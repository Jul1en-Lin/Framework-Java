#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""发布相关用例共用的本机假服务。

不是测试模块（文件名不以 test_ 开头），只提供：

* ``make_nacos_handler``：假 Nacos，支持 /v1/auth/login 与 /v1/ns/instance/list
* ``make_status_handler``：固定状态码的假业务服务 / 假 Nginx
* ``start_server`` / ``closed_port``：启停辅助
"""

import json
import socket
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, HTTPServer


def make_nacos_handler(state):
    """state 至少包含 username / password / token / services{service: hosts|None}。"""

    class NacosHandler(BaseHTTPRequestHandler):
        def log_message(self, *args):  # 静默，避免污染测试输出
            pass

        def _respond(self, status, body):
            payload = body.encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def do_POST(self):
            if not self.path.endswith("/v1/auth/login"):
                self._respond(404, "{}")
                return
            length = int(self.headers.get("Content-Length", 0))
            params = urllib.parse.parse_qs(self.rfile.read(length).decode("utf-8"))
            if (
                params.get("username", [""])[0] == state["username"]
                and params.get("password", [""])[0] == state["password"]
            ):
                self._respond(200, json.dumps({"accessToken": state["token"]}))
            else:
                self._respond(403, json.dumps({"status": 403, "message": "user not found!"}))

        def do_GET(self):
            if "/v1/ns/instance/list" not in self.path:
                self._respond(404, "{}")
                return
            query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
            if query.get("accessToken", [""])[0] != state["token"]:
                self._respond(403, json.dumps({"status": 403}))
                return
            service = query.get("serviceName", [""])[0]
            self._respond(200, json.dumps({"hosts": state["services"].get(service)}))

    return NacosHandler


def make_status_handler(status_code):
    class StatusHandler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def do_GET(self):
            self.send_response(status_code)
            self.send_header("Content-Length", "2")
            self.end_headers()
            self.wfile.write(b"{}")

    return StatusHandler


def start_server(handler_class):
    server = HTTPServer(("127.0.0.1", 0), handler_class)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server, server.server_address[1]


def closed_port():
    """占用一个端口再释放，返回一个确定没有服务监听的端口号。"""
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    return port
