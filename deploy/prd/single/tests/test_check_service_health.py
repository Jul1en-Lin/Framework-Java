#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check_service_health.py 的用例。

用本机假 Nacos（登录 + 实例列表）、假服务端口与假 Nginx 端口驱动真实脚本，
覆盖注册缺失、实例不健康、实例端口关闭、凭据错误、Nginx 502 等失败场景：

    python3 deploy/prd/single/tests/test_check_service_health.py -v
"""

import os
import subprocess
import sys
import unittest
from pathlib import Path

from fake_services import closed_port, make_nacos_handler, make_status_handler, start_server

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
HEALTH = SCRIPTS / "check_service_health.py"

SERVICES = ["lien-gateway", "lien-admin", "lien-file", "lien-portal"]
USERNAME = "nacos-client"
PASSWORD = "s3cret-should-never-be-printed"


class CheckServiceHealthTest(unittest.TestCase):
    def add_server_cleanup(self, server):
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)

    def setUp(self):
        # 假服务端口：任何 HTTP 响应都算存活
        service_server, self.service_port = start_server(make_status_handler(404))
        self.add_server_cleanup(service_server)

        # 假 Nginx：默认返回 401（网关可达但需鉴权）
        nginx_server, self.web_port = start_server(make_status_handler(401))
        self.add_server_cleanup(nginx_server)

        self.nacos_state = {
            "username": USERNAME,
            "password": PASSWORD,
            "token": "fake-access-token",
            "services": {
                service: [
                    {
                        "ip": "127.0.0.1",
                        "port": self.service_port,
                        "healthy": True,
                        "enabled": True,
                    }
                ]
                for service in SERVICES
            },
        }
        nacos_server, self.nacos_port = start_server(make_nacos_handler(self.nacos_state))
        self.add_server_cleanup(nacos_server)

    def run_health(self, services=None, api_prefix=True, env=None, extra=()):
        args = [
            sys.executable,
            str(HEALTH),
            "--nacos-url",
            "http://127.0.0.1:{}/nacos".format(self.nacos_port),
            "--namespace",
            "frameworkjava-prd",
            "--web-port",
            str(self.web_port),
            "--timeout",
            "5",
        ]
        for service in services or SERVICES:
            args += ["--service", service]
        if api_prefix:
            for prefix in ("admin", "file", "portal"):
                args += ["--api-prefix", prefix]
        args += list(extra)

        environment = dict(os.environ)
        environment.update(
            env
            if env is not None
            else {"NACOS_USERNAME": USERNAME, "NACOS_PASSWORD": PASSWORD}
        )
        return subprocess.run(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True,
            env=environment,
        )

    def assert_passes(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("健康校验通过", result.stdout)

    def assert_fails(self, result, *fragments):
        self.assertNotEqual(result.returncode, 0, "校验应当失败但返回 0")
        output = result.stdout + result.stderr
        for fragment in fragments:
            self.assertIn(fragment, output, "失败信息里缺少 {!r}：\n{}".format(fragment, output))

    def test_all_services_healthy_passes(self):
        self.assert_passes(self.run_health())

    def test_credentials_never_appear_in_output(self):
        result = self.run_health()
        self.assertNotIn(PASSWORD, result.stdout + result.stderr)
        self.assertNotIn("fake-access-token", result.stdout + result.stderr)

    def test_missing_credentials_exit_2(self):
        result = self.run_health(env={"NACOS_USERNAME": "", "NACOS_PASSWORD": ""})
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("NACOS_USERNAME", result.stderr)

    def test_rejected_login_fails(self):
        result = self.run_health(env={"NACOS_USERNAME": USERNAME, "NACOS_PASSWORD": "wrong"})
        self.assert_fails(result, "登录被拒绝", "HTTP 403")

    def test_unregistered_service_fails(self):
        self.nacos_state["services"]["lien-file"] = None
        self.assert_fails(self.run_health(), "lien-file 未在 frameworkjava-prd/DEFAULT_GROUP 注册")

    def test_unhealthy_instance_fails(self):
        self.nacos_state["services"]["lien-admin"] = [
            {"ip": "127.0.0.1", "port": self.service_port, "healthy": False, "enabled": True}
        ]
        self.assert_fails(self.run_health(), "lien-admin 已注册但没有健康实例")

    def test_instance_port_closed_fails(self):
        port = closed_port()
        self.nacos_state["services"]["lien-portal"] = [
            {"ip": "127.0.0.1", "port": port, "healthy": True, "enabled": True}
        ]
        self.assert_fails(self.run_health(), "lien-portal 实例 127.0.0.1:{} 端口无响应".format(port))

    def test_broken_gateway_chain_fails(self):
        # 把假 Nginx 换成 502：网关不可达
        nginx_server, web_port = start_server(make_status_handler(502))
        self.add_server_cleanup(nginx_server)
        self.web_port = web_port
        self.assert_fails(self.run_health(), "HTTP 502", "reload")

    def test_prefix_only_run_skips_api_chain(self):
        nginx_server, web_port = start_server(make_status_handler(502))
        self.add_server_cleanup(nginx_server)
        self.web_port = web_port
        self.assert_passes(self.run_health(api_prefix=False))


if __name__ == "__main__":
    unittest.main(verbosity=2)
