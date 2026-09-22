#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发布后只读校验：Nacos 注册、实例端口可用性、经 Nginx 的后端 API 链路。

只发 HTTP 只读请求；不注册实例、不发布配置、不重启或停止任何容器。因此可以在
任何时刻安全地重复执行，既用于发布后的验收，也可用于独立的状态复核。

    NACOS_USERNAME=... NACOS_PASSWORD=... \\
      check_service_health.py --nacos-url http://127.0.0.1:8866/nacos \\
        --namespace frameworkjava-prd \\
        --service lien-gateway --service lien-admin \\
        --service lien-file --service lien-portal \\
        --web-port 8666 --api-prefix admin --api-prefix file --api-prefix portal

登录凭据只从环境变量读取，不通过命令行传递（命令行会出现在 ps 输出里），也从不打印。
退出码：0 全部通过，1 有检查项失败，2 用法或环境错误。
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

DEFAULT_GROUP = "DEFAULT_GROUP"
LOGIN_PATH = "/v1/auth/login"
INSTANCE_LIST_PATH = "/v1/ns/instance/list"
# Nginx 已就绪但上游不可达时的状态码：这些说明链路真的断了。
GATEWAY_BROKEN_STATUS = (502, 503, 504)
OK = "[ OK ]"
FAIL = "[FAIL]"


class CheckError(Exception):
    """单项检查无法完成（连接失败、响应不可解析等）。"""


def http_call(url, data=None, timeout=10):
    """发起请求并返回 (状态码, 响应体)；HTTP 错误码不抛异常，连接错误抛 CheckError。"""
    request = urllib.request.Request(url, data=data)
    if data is not None:
        request.add_header("Content-Type", "application/x-www-form-urlencoded")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", "replace")
    except urllib.error.URLError as error:
        raise CheckError("无法连接 {}（{}）".format(url.split("?")[0], error.reason))
    except OSError as error:  # 超时、连接被重置等
        raise CheckError("请求失败 {}（{}）".format(url.split("?")[0], error))


def nacos_login(base_url, username, password, timeout):
    """用 Nacos 客户端账号登录，返回 accessToken。"""
    payload = urllib.parse.urlencode({"username": username, "password": password}).encode("utf-8")
    status, body = http_call(base_url + LOGIN_PATH, payload, timeout)
    if status != 200:
        raise CheckError(
            "Nacos 登录被拒绝（HTTP {}）；请确认 NACOS_USERNAME / NACOS_PASSWORD 与服务器 .env 一致".format(
                status
            )
        )
    try:
        token = json.loads(body).get("accessToken")
    except ValueError:
        raise CheckError("Nacos 登录响应不是合法 JSON")
    if not token:
        raise CheckError("Nacos 登录响应缺少 accessToken")
    return token


def nacos_instances(base_url, token, namespace, group, service, timeout):
    """查询一个服务在指定命名空间/分组下的实例列表。"""
    query = urllib.parse.urlencode(
        {
            "serviceName": service,
            "groupName": group,
            "namespaceId": namespace,
            "accessToken": token,
            "healthyOnly": "false",
        }
    )
    status, body = http_call("{}?{}".format(base_url + INSTANCE_LIST_PATH, query), None, timeout)
    if status != 200:
        raise CheckError("查询 {} 实例列表失败（HTTP {}）".format(service, status))
    try:
        hosts = json.loads(body).get("hosts")
    except ValueError:
        raise CheckError("{} 实例列表响应不是合法 JSON".format(service))
    return hosts or []


def probe_service_port(ip, port, timeout):
    """访问实例注册的 ip:port，返回 HTTP 状态码；连接失败返回 None。

    只要求「端口在监听并给出 HTTP 响应」：根路径返回 401/403/404 都算通过，
    这里验证的是进程与端口，不是接口语义。
    """
    try:
        status, _ = http_call("http://{}:{}/".format(ip, port), None, timeout)
        return status
    except CheckError:
        return None


def probe_gateway(path_url, timeout):
    """经 Nginx 访问网关，返回状态码；连接失败返回 None。"""
    try:
        status, _ = http_call(path_url, None, timeout)
        return status
    except CheckError:
        return None


def check_service(args, token, service):
    """校验单个服务的注册与实例端口；返回失败信息列表。"""
    failures = []
    try:
        hosts = nacos_instances(
            args.nacos_url, token, args.namespace, args.group, service, args.timeout
        )
    except CheckError as error:
        return ["{} 注册检查失败：{}".format(service, error)]

    healthy = [
        host
        for host in hosts
        if host.get("healthy") is True and host.get("enabled") is not False
    ]
    if not healthy:
        if hosts:
            detail = "、".join(
                "{}:{} healthy={} enabled={}".format(
                    host.get("ip"), host.get("port"), host.get("healthy"), host.get("enabled")
                )
                for host in hosts
            )
            failures.append("{} 已注册但没有健康实例（{}）".format(service, detail))
        else:
            failures.append(
                "{} 未在 {}/{} 注册".format(service, args.namespace, args.group)
            )
        return failures

    print(
        "{} {:20s} 注册健康：{} 个实例（{}）".format(
            OK,
            service,
            len(healthy),
            "、".join("{}:{}".format(h.get("ip"), h.get("port")) for h in healthy),
        ),
        flush=True,
    )

    for host in healthy:
        ip, port = host.get("ip"), host.get("port")
        if not ip or not port:
            failures.append("{} 实例缺少 ip/port：{}".format(service, host))
            continue
        status = probe_service_port(ip, port, args.timeout)
        if status is None:
            failures.append("{} 实例 {}:{} 端口无响应".format(service, ip, port))
        else:
            print(
                "{} {:20s} 实例 {}:{} 端口存活（HTTP {}）".format(OK, service, ip, port, status),
                flush=True,
            )
    return failures


def check_gateway_chain(args):
    """经 Nginx 的后端 API 链路：只判定「Nginx → 网关」是否可达。"""
    failures = []
    for prefix in args.api_prefix:
        url = "http://127.0.0.1:{}/{}/".format(args.web_port, prefix.strip("/"))
        status = probe_gateway(url, args.timeout)
        if status is None:
            failures.append("经 Nginx 访问 /{}/ 无响应（Nginx 或端口 {} 不可达）".format(prefix, args.web_port))
        elif status in GATEWAY_BROKEN_STATUS:
            failures.append(
                "经 Nginx 访问 /{}/ 返回 HTTP {}（网关不可达；重建网关后需确认 Nginx 已 reload）".format(
                    prefix, status
                )
            )
        else:
            print(
                "{} {:20s} 经 Nginx 可达（HTTP {}；401/403/404 属正常鉴权或路由响应）".format(
                    OK, "/{}/".format(prefix), status
                ),
                flush=True,
            )
    return failures


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="发布后只读校验：Nacos 注册、实例端口、经 Nginx 的后端 API",
    )
    parser.add_argument("--nacos-url", required=True, help="Nacos 入口，例如 http://127.0.0.1:8866/nacos")
    parser.add_argument("--namespace", required=True, help="Nacos 命名空间 ID，例如 frameworkjava-prd")
    parser.add_argument("--group", default=DEFAULT_GROUP, help="Nacos 分组（默认 DEFAULT_GROUP）")
    parser.add_argument("--service", action="append", default=[], required=True, help="要校验的服务名（可重复）")
    parser.add_argument("--api-prefix", action="append", default=[], help="经 Nginx 校验的路径前缀（可重复）")
    parser.add_argument("--web-port", type=int, default=8666, help="Nginx 宿主机端口（默认 8666）")
    parser.add_argument("--timeout", type=float, default=10.0, help="单个 HTTP 请求超时秒数（默认 10）")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    args.nacos_url = args.nacos_url.rstrip("/")

    username = os.environ.get("NACOS_USERNAME", "")
    password = os.environ.get("NACOS_PASSWORD", "")
    if not username or not password:
        print(
            "NACOS_USERNAME / NACOS_PASSWORD 未设置，无法登录生产 Nacos",
            file=sys.stderr,
            flush=True,
        )
        return 2

    print(
        "服务健康校验：{}（服务 {}）".format(args.nacos_url, "、".join(args.service)),
        flush=True,
    )
    try:
        token = nacos_login(args.nacos_url, username, password, args.timeout)
    except CheckError as error:
        print("{} Nacos 登录：{}".format(FAIL, error), file=sys.stderr, flush=True)
        return 1

    failures = []
    for service in args.service:
        failures.extend(check_service(args, token, service))
    if args.api_prefix:
        failures.extend(check_gateway_chain(args))

    if failures:
        for message in failures:
            print("{} {}".format(FAIL, message), file=sys.stderr, flush=True)
        print(
            "健康校验失败：{} 项未通过".format(len(failures)), file=sys.stderr, flush=True
        )
        return 1

    print("健康校验通过：{} 个服务".format(len(args.service)), flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(2)
