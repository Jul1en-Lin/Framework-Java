#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成阿里云 OSS 预签名 URL（V1 签名，只用 Python 标准库）。

用途：把发布包从外网（GitHub runner）送到国内 OSS，再由服务器从国内地址下载——这台
腾讯云服务器的国际线路只有十几 KB/s，262 MB 的发布包无法从 runner 直传，而 OSS 双向往返
都是国内链路。

凭据只从服务器环境变量读取，不通过命令行传递、也不打印：

    OSS_BUCKET             桶名
    OSS_ENDPOINT           地域域名，例如 oss-cn-guangzhou.aliyuncs.com
                           （开了传输加速则为 oss-accelerate.aliyuncs.com）
    OSS_ACCESS_KEY_ID      RAM 用户的 AccessKeyId
    OSS_ACCESS_KEY_SECRET  RAM 用户的 AccessKeySecret

用法::

    oss_presign.py --method PUT --key github-release/12-1a2b3c4/package.tar.gz
    oss_presign.py --method GET --key github-release/12-1a2b3c4/package.tar.gz --expires 3600

输出：一行预签名 URL（stdout）。签名算法见阿里云 OSS 文档「在 URL 中包含签名」：

    StringToSign = METHOD + "\\n" + Content-MD5 + "\\n" + Content-Type + "\\n"
                 + Expires + "\\n" + CanonicalizedOSSHeaders + CanonicalizedResource
    Signature    = base64(hmac-sha1(AccessKeySecret, StringToSign))

预签名 URL 的 Content-MD5 与 Content-Type 为空串（客户端 PUT 时不要带 Content-Type），
CanonicalizedResource 为 ``/<bucket>/<key>``。

退出码：0 成功；2 用法或环境错误。
"""

import argparse
import base64
import hashlib
import hmac
import os
import sys
from urllib.parse import quote

ALLOWED_METHODS = ("PUT", "GET", "HEAD")
MAX_EXPIRES = 7 * 24 * 3600


def canonicalized_resource(bucket, key):
    return "/{}/{}".format(bucket, key)


def sign(method, bucket, key, expires, secret):
    string_to_sign = "{}\n\n\n{}\n{}{}".format(
        method, expires, "", canonicalized_resource(bucket, key)
    )
    digest = hmac.new(secret.encode("utf-8"), string_to_sign.encode("utf-8"), hashlib.sha1)
    return base64.b64encode(digest.digest()).decode("ascii")


def presign(method, bucket, endpoint, key, expires, access_key_id, access_key_secret):
    signature = sign(method, bucket, key, expires, access_key_secret)
    quoted_key = "/".join(quote(part, safe="") for part in key.split("/"))
    return "https://{}.{}/{}?OSSAccessKeyId={}&Expires={}&Signature={}".format(
        bucket,
        endpoint,
        quoted_key,
        quote(access_key_id, safe=""),
        expires,
        quote(signature, safe=""),
    )


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="生成阿里云 OSS 预签名 URL（凭据只从环境变量或 --env-file 读取）")
    parser.add_argument("--method", default="PUT", choices=ALLOWED_METHODS, help="HTTP 方法（默认 PUT）")
    parser.add_argument("--key", required=True, help="对象键，例如 github-release/<release_id>/package.tar.gz")
    parser.add_argument("--expires", type=int, default=3600, help="有效期秒数（默认 3600）")
    parser.add_argument("--env-file", default=None, help="从这里读 OSS_* 键（不 eval、不 source）；真正的环境变量优先")
    parser.add_argument("--now", type=int, default=None, help="用于测试：指定当前时间戳")
    return parser.parse_args(argv)


CREDENTIAL_KEYS = ("OSS_BUCKET", "OSS_ENDPOINT", "OSS_ACCESS_KEY_ID", "OSS_ACCESS_KEY_SECRET")


def read_env_file(path):
    """从 dotenv 文件里取需要的键；不 eval、不 source，只按 KEY= 前缀取值并去掉成对引号。"""
    values = {}
    try:
        with open(path, "r", encoding="utf-8") as handle:
            for line in handle:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                name, _, value = line.partition("=")
                name = name.strip()
                if name not in CREDENTIAL_KEYS:
                    continue
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                    value = value[1:-1]
                values[name] = value
    except OSError as error:
        print("无法读取 {}：{}".format(path, error), file=sys.stderr)
        raise SystemExit(2)
    return values


def resolve_credentials(args):
    values = read_env_file(args.env_file) if args.env_file else {}
    for name in CREDENTIAL_KEYS:
        if os.environ.get(name):
            values[name] = os.environ[name]
    return values


def main(argv=None):
    args = parse_args(argv)
    values = resolve_credentials(args)
    missing = [name for name in CREDENTIAL_KEYS if not values.get(name)]
    if missing:
        print("缺少 OSS 凭据：{}（用 --env-file 或环境变量提供）".format("、".join(missing)), file=sys.stderr)
        return 2
    if not args.key or args.key.startswith("/") or ".." in args.key.split("/"):
        print("非法的对象键：{}".format(args.key), file=sys.stderr)
        return 2
    if not 0 < args.expires <= MAX_EXPIRES:
        print("--expires 必须在 1..{} 秒之间".format(MAX_EXPIRES), file=sys.stderr)
        return 2

    import time

    expires = (args.now if args.now is not None else int(time.time())) + args.expires
    url = presign(
        args.method,
        values["OSS_BUCKET"],
        values["OSS_ENDPOINT"],
        args.key,
        expires,
        values["OSS_ACCESS_KEY_ID"],
        values["OSS_ACCESS_KEY_SECRET"],
    )
    print(url)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(2)
