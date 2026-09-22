#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""app_release.sh 与 verify_deployment.sh 的端到端用例（用桩替换 docker）。

真实脚本 + 本机假 Nacos/假服务/假 Nginx + PATH 上的 docker 桩，覆盖：

* 成功发布：制品替换、构建目录恰好一份 JAR、Nginx reload、状态与 manifest 记录；
* 发布包缺失 / 校验和不符 / 部署根目录指错 / 并发锁被占 / 服务目录多份 JAR：改动前失败；
* 首次发布失败停应用服务、后续发布失败不自动回滚并给出回滚命令；
* 回滚到已保留版本；
* 只读预检与 status 不改动任何东西；
* 桩记录的 docker 调用里绝不出现 down / 中间件 Compose / prune。

    python3 deploy/prd/single/tests/test_app_release.py -v
"""

import hashlib
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from fake_services import make_nacos_handler, make_status_handler, start_server

TESTS_DIR = Path(__file__).resolve().parent
SINGLE = TESTS_DIR.parent
ROOT = SINGLE.parents[2]
SCRIPTS = SINGLE / "scripts"
APP_RELEASE = SCRIPTS / "app_release.sh"

# 复用 #4 的制品构造器，让「服务器上重跑真实校验脚本」这一步能在本地真实验证
sys.path.insert(0, str(ROOT / "scripts" / "tests"))
from test_verify_service_artifacts import bootstrap_yml, write_boot_jar  # noqa: E402

ARTIFACTS = {
    "gateway": "lien-gateway",
    "admin": "lien-admin-service",
    "file": "lien-file-service",
    "portal": "lien-portal-service",
}
SERVICES = ["gateway", "admin", "file", "portal"]
USERNAME = "nacos-client"
PASSWORD = "s3cret-should-never-be-printed"

DOCKER_STUB = """#!/usr/bin/env python3
import os, sys

args = sys.argv[1:]
with open(os.environ["DOCKER_LOG"], "a") as handle:
    handle.write("docker " + " ".join(args) + "\\n")

KNOWN_SERVICES = {
    "frameworkjava-gateway",
    "frameworkjava-admin",
    "frameworkjava-file",
    "frameworkjava-portal",
}
STARTED_MARKER = os.environ["DOCKER_LOG"] + ".webprd-started"

service = ""
for arg in args:
    if "com.docker.compose.service=" in arg:
        service = arg.split("com.docker.compose.service=", 1)[1]

if args and args[0] == "info":
    sys.exit(0)
if args[:2] == ["image", "inspect"]:
    sys.exit(0)
if args and args[0] == "ps":
    if os.environ.get("DOCKER_NO_WEBPRD") == "1" and service == "frameworkjava-webprd":
        sys.exit(0)
    print("cid-" + service.replace("frameworkjava-", ""))
    sys.exit(0)
if args and args[0] == "inspect":
    target = args[-1]
    if target == "cid-webprd":
        # 模拟「网关上线前 nginx 因 upstream 解析不到网关而重启循环」
        if os.environ.get("DOCKER_WEBPRD_RUNNING") == "0" and not os.path.exists(STARTED_MARKER):
            print("false")
            sys.exit(0)
        print("true")
        sys.exit(0)
    print("true 0 running")
    sys.exit(0)
if args and args[0] == "start":
    if args[-1] == "cid-webprd":
        open(STARTED_MARKER, "w").close()
    sys.exit(0)
if args and args[0] == "exec":
    sys.stderr.write("nginx: configuration file test is successful\\n")
    sys.exit(0)
if args and args[0] == "stats":
    rss = os.environ.get("DOCKER_RSS", "300MiB")
    for arg in args:
        if arg.startswith("cid-"):
            print("{} {} / 1.7GiB".format(arg, rss))
    sys.exit(0)
if args and args[0] == "compose":
    if "--quiet" in args:
        sys.exit(0)
    if "logs" in args:
        target = args[-1]
        if target not in KNOWN_SERVICES:
            sys.stderr.write("stub: no such service: {}\\n".format(target))
            sys.exit(1)
        if os.environ.get("DOCKER_FATAL_LOG") == "1":
            print("2026-01-01 00:00:00 ERROR APPLICATION FAILED TO START")
            sys.exit(0)
        print("2026-01-01 00:00:00 INFO Started DemoApplication in 12.34 seconds")
        sys.exit(0)
    for verb in ("up", "stop"):
        if verb in args:
            for arg in args[args.index(verb) + 1:]:
                if arg.startswith("-"):
                    continue
                if arg not in KNOWN_SERVICES:
                    sys.stderr.write("stub: unexpected compose service: {}\\n".format(arg))
                    sys.exit(1)
    if "up" in args and os.environ.get("DOCKER_FAIL_UP") == "1":
        sys.stderr.write("stub: failed to rebuild images\\n")
        sys.exit(1)
    sys.exit(0)
sys.exit(0)
"""

FREE_STUB = """#!/usr/bin/env bash
cat <<'OUT'
              total        used        free      shared  buff/cache   available
Mem:           1807        1000         300          10         500        1800
Swap:          2047         100       1947
OUT
"""

VERIFIER_STUB = """#!/usr/bin/env python3
import os, sys
with open(os.environ["VERIFIER_LOG"], "a") as handle:
    handle.write(" ".join(sys.argv[1:]) + "\\n")
sys.exit(int(os.environ.get("VERIFIER_EXIT", "0")))
"""

# 假 curl：把预签名 URL 里的对象键映射到 FAKE_OSS_DIR 下的本地文件（模拟 OSS GET）。
CURL_STUB = '''#!/usr/bin/env python3
import os, shutil, sys
from urllib.parse import urlparse

args = sys.argv[1:]
with open(os.environ["CURL_LOG"], "a") as handle:
    handle.write(" ".join(args) + "\\n")

out = url = None
index = 0
while index < len(args):
    if args[index] == "-o":
        out = args[index + 1]
        index += 2
        continue
    if args[index].startswith("http"):
        url = args[index]
    index += 1
if url is None or out is None:
    sys.stderr.write("stub curl: unexpected args: {}\\n".format(args))
    sys.exit(2)
key = urlparse(url).path.lstrip("/")
source = os.path.join(os.environ["FAKE_OSS_DIR"], key)
if not os.path.isfile(source):
    sys.stderr.write("stub curl: 404 {}\\n".format(key))
    sys.exit(22)
shutil.copyfile(source, out)
'''


def sha256_of(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


class AppReleaseTest(unittest.TestCase):
    def add_server_cleanup(self, server):
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)

    def setUp(self):
        self._tmp = tempfile.mkdtemp(prefix="app-release-")
        self.addCleanup(shutil.rmtree, self._tmp, ignore_errors=True)
        self.root = Path(self._tmp) / "single"

        # docker / free / verifier 桩
        self.bin_dir = Path(self._tmp) / "bin"
        self.bin_dir.mkdir()
        self.write_stub("docker", DOCKER_STUB)
        self.write_stub("free", FREE_STUB)
        self.write_stub("curl", CURL_STUB)
        self.verifier = Path(self._tmp) / "verifier.py"
        self.verifier.write_text(VERIFIER_STUB, encoding="utf-8")
        self.docker_log = Path(self._tmp) / "docker.log"
        self.verifier_log = Path(self._tmp) / "verifier.log"
        self.curl_log = Path(self._tmp) / "curl.log"
        self.fake_oss = Path(self._tmp) / "oss"
        self.fake_oss.mkdir()

        # 假服务端口与假 Nginx
        service_server, self.service_port = start_server(make_status_handler(404))
        self.add_server_cleanup(service_server)
        nginx_server, self.web_port = start_server(make_status_handler(401))
        self.add_server_cleanup(nginx_server)

        self.nacos_state = {
            "username": USERNAME,
            "password": PASSWORD,
            "token": "fake-access-token",
            "services": {
                "lien-{}".format(service): [
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

        self.build_deploy_root()

    # ---------------------------------------------------------------- fixture

    def write_stub(self, name, content):
        path = self.bin_dir / name
        path.write_text(content, encoding="utf-8")
        path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    def build_deploy_root(self):
        app = self.root / "app"
        (app / "service").mkdir(parents=True)
        # Web 端口保持生产值 8666（脚本用它挡住指错目录），实际校验走环境变量 WEB_PORT
        (self.root / ".env").write_text(
            "WEB_PORT=8666\n"
            "NACOS_USERNAME={}\n"
            "NACOS_PASSWORD={}\n"
            "MYSQL_ROOT_PASSWORD=dummy\n"
            "OSS_BUCKET=frameworkjava-release\n"
            "OSS_ENDPOINT=oss-cn-guangzhou.aliyuncs.com\n"
            "OSS_ACCESS_KEY_ID=AKIDEXAMPLE1234567890\n"
            "OSS_ACCESS_KEY_SECRET=SECRETEXAMPLE1234567890abcdefghij\n".format(USERNAME, PASSWORD),
            encoding="utf-8",
        )
        for name in ("docker-compose-app.yml", "docker-compose-mid.yml"):
            shutil.copy(SINGLE / "app" / name, app / name)
        for service, artifact in ARTIFACTS.items():
            service_dir = app / "service" / service
            service_dir.mkdir()
            (service_dir / "Dockerfile").write_text(
                (SINGLE / "app" / "service" / "gateway" / "Dockerfile").read_text(),
                encoding="utf-8",
            )
            (service_dir / "{}.jar".format(artifact)).write_bytes(
                "old-{}-jar".format(service).encode()
            )

    def package_dir(self, release_id, suffix="", dirname="release"):
        return self.root / "releases" / "staging" / release_id / dirname

    def build_package(self, release_id, marker, suffix="", dirname="release"):
        """在 releases/staging/<id>/<dirname> 下造一份四服务发布包。"""
        package = self.package_dir(release_id, suffix, dirname)
        for service, artifact in ARTIFACTS.items():
            service_dir = package / service
            service_dir.mkdir(parents=True, exist_ok=True)
            jar = service_dir / "{}.jar".format(artifact)
            jar.write_bytes("{}-{}{}".format(marker, service, suffix).encode())
            (service_dir / "{}.jar.sha256".format(artifact)).write_text(
                "{}  {}.jar\n".format(sha256_of(jar), artifact), encoding="utf-8"
            )
        (package / "build-info.txt").write_text(
            "git_rev=deadbee\ntests_run=no(-DskipTests)\n", encoding="utf-8"
        )
        return package

    def build_boot_package(self, release_id, dirname="package"):
        """造一份能通过真实 verify_service_artifacts.py 的发布包（结构合法的 Boot JAR）。"""
        package = self.package_dir(release_id, dirname=dirname)
        for service, artifact in ARTIFACTS.items():
            service_dir = package / service
            service_dir.mkdir(parents=True, exist_ok=True)
            jar = service_dir / "{}.jar".format(artifact)
            write_boot_jar(
                jar,
                start_class="com.lien.{}.App".format(service),
                bootstrap=bootstrap_yml(service),
            )
            (service_dir / "{}.jar.sha256".format(artifact)).write_text(
                "{}  {}.jar\n".format(sha256_of(jar), artifact), encoding="utf-8"
            )
        (package / "build-info.txt").write_text("git_rev=deadbee\n", encoding="utf-8")
        return package

    # ---------------------------------------------------------------- runner

    def run_release(self, *args, **kwargs):
        environment = dict(os.environ)
        environment.update(
            {
                "PATH": "{}:{}".format(self.bin_dir, environment["PATH"]),
                "DOCKER_LOG": str(self.docker_log),
                "VERIFIER_LOG": str(self.verifier_log),
                "CURL_LOG": str(self.curl_log),
                "FAKE_OSS_DIR": str(self.fake_oss),
                "NACOS_HOST_PORT": str(self.nacos_port),
                "WEB_PORT": str(self.web_port),
                "SAMPLE_SECONDS": "0",
                "READY_TIMEOUT": "3",
                "READY_INTERVAL": "1",
                "MIN_AVAIL_MEM_MB": "100",
                "MIN_FREE_DISK_MB": "1",
                "MIN_AVAIL_MB": "100",
            }
        )
        environment.update(kwargs.pop("env", {}))
        return subprocess.run(
            ["bash", str(APP_RELEASE)] + list(args),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True,
            cwd=str(ROOT),
            env=environment,
        )

    def deploy(self, release_id, marker, extra=(), env=None):
        package = self.build_package(release_id, marker)
        return self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            release_id,
            "--verifier",
            str(self.verifier),
            "--operator",
            "tester",
            *extra,
            env=env or {},
        )

    def docker_calls(self):
        if not self.docker_log.exists():
            return []
        return [line for line in self.docker_log.read_text().splitlines() if line]

    def service_jar(self, service):
        return (self.root / "app" / "service" / service / "{}.jar".format(ARTIFACTS[service]))

    def current_release(self):
        path = self.root / "releases" / "state" / "current"
        return path.read_text().strip() if path.exists() else None

    def assert_no_mutations(self):
        """所有服务目录仍是初始内容，且没有 compose up / stop / exec 调用。"""
        for service in SERVICES:
            self.assertEqual(
                self.service_jar(service).read_bytes(),
                "old-{}-jar".format(service).encode(),
                "{} 的 JAR 被改动了".format(service),
            )
        joined = "\n".join(self.docker_calls())
        self.assertNotIn(" up ", joined)
        self.assertNotIn(" stop ", joined)
        self.assertNotIn("exec", joined)

    def assert_never_destructive(self):
        joined = "\n".join(self.docker_calls())
        for forbidden in ("down", "prune", "rmi", "volume rm", "docker-compose-mid"):
            self.assertNotIn(forbidden, joined, "docker 桩记录里出现了破坏性调用：{}".format(forbidden))

    def assert_succeeds(self, result):
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_deploy_success_replaces_all_four_services(self):
        result = self.deploy("1-abcdefg", "new")
        self.assert_succeeds(result)
        for service in SERVICES:
            self.assertEqual(
                self.service_jar(service).read_bytes(), "new-{}".format(service).encode()
            )
        self.assertEqual(self.current_release(), "1-abcdefg")

        manifest = (self.root / "releases" / "1-abcdefg" / "manifest.txt").read_text()
        self.assertIn("result=success", manifest)
        self.assertIn("operator=tester", manifest)
        self.assertIn("git_rev=deadbee", manifest)
        for service in SERVICES:
            self.assertIn("service={}".format(service), manifest)
            self.assertIn("before=", manifest)

        calls = "\n".join(self.docker_calls())
        self.assertIn(
            "compose --env-file ../.env -p frameworkjava-prd -f docker-compose-app.yml"
            " up -d --build frameworkjava-gateway frameworkjava-admin frameworkjava-file frameworkjava-portal",
            calls,
        )
        self.assertIn("nginx -s reload", calls)
        self.assertNotIn("down", calls)
        self.assert_never_destructive()

        # 暂存目录已清理，历史与状态落盘
        self.assertFalse(self.package_dir("1-abcdefg").exists())
        history = (self.root / "releases" / "state" / "history.log").read_text()
        self.assertIn("action=deploy", history)
        self.assertIn("result=success", history)
        self.assertFalse((self.root / "releases" / "lock").exists())

    def test_deploy_reruns_artifact_verifier(self):
        self.assert_succeeds(self.deploy("1-abcdefg", "new"))
        self.assertIn(str(self.package_dir("1-abcdefg")), self.verifier_log.read_text())

    def test_deploy_fails_when_verifier_rejects_package(self):
        package = self.build_package("2-abcdefg", "new")
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            "2-abcdefg",
            "--verifier",
            str(self.verifier),
            env={"VERIFIER_EXIT": "1"},
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("制品结构校验未通过", result.stderr)
        self.assert_no_mutations()

    def test_deploy_rejects_checksum_mismatch(self):
        package = self.build_package("3-abcdefg", "new")
        (package / "admin" / "lien-admin-service.jar").write_bytes(b"tampered")
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            "3-abcdefg",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("校验和不一致", result.stderr)
        self.assert_no_mutations()

    def test_deploy_rejects_missing_artifact(self):
        package = self.build_package("4-abcdefg", "new")
        (package / "file" / "lien-file-service.jar").unlink()
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            "4-abcdefg",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("发布包缺少 file/lien-file-service.jar", result.stderr)
        self.assert_no_mutations()

    def test_deploy_rejects_package_outside_releases_dir(self):
        outside = Path(self._tmp) / "outside"
        outside.mkdir()
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(outside),
            "--release-id",
            "9-abcdefg",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("拒绝操作", result.stderr)
        self.assert_no_mutations()

    def test_deploy_rejects_wrong_deploy_root(self):
        (self.root / ".env").write_text(
            "WEB_PORT=8848\nNACOS_USERNAME={}\nNACOS_PASSWORD={}\n".format(USERNAME, PASSWORD),
            encoding="utf-8",
        )
        package = self.build_package("5-abcdefg", "new")
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            "5-abcdefg",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("疑似指错部署根目录", result.stderr)
        self.assert_no_mutations()

    def test_deploy_rejects_two_jars_in_service_dir(self):
        extra = self.root / "app" / "service" / "admin" / "lien-admin-service-0.0.1-SNAPSHOT.jar"
        extra.write_bytes(b"leftover")
        result = self.deploy("6-abcdefg", "new")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("2 份 JAR", result.stderr)
        self.assert_no_mutations()

    def test_deploy_rejects_overwriting_retained_release(self):
        self.assert_succeeds(self.deploy("7-abcdefg", "first"))
        result = self.deploy("7-abcdefg", "second")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("已存在同名保留版本", result.stderr)
        self.assertEqual(self.service_jar("admin").read_bytes(), b"first-admin")

    def test_deploy_refuses_when_lock_is_held(self):
        lock = self.root / "releases" / "lock"
        lock.mkdir(parents=True)
        (lock / "owner").write_text("token=other\npid=999999\n", encoding="utf-8")
        result = self.deploy("8-abcdefg", "new")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("已有发布在执行或被中断", result.stderr)
        self.assert_no_mutations()

    def test_dry_run_makes_no_changes(self):
        result = self.deploy("10-abcdefg", "new", extra=("--dry-run",))
        self.assert_succeeds(result)
        self.assertIn("dry-run 结束，未做任何改动", result.stdout)
        self.assert_no_mutations()
        self.assertIsNone(self.current_release())
        self.assertFalse((self.root / "releases" / "10-abcdefg").exists())

    def test_first_publish_failure_stops_app_services(self):
        self.nacos_state["services"]["lien-portal"] = None
        result = self.deploy("11-abcdefg", "new")
        self.assertNotEqual(result.returncode, 0)
        calls = "\n".join(self.docker_calls())
        self.assertIn(
            "stop frameworkjava-gateway frameworkjava-admin frameworkjava-file frameworkjava-portal",
            calls,
        )
        self.assertNotIn("down", calls)
        self.assert_never_destructive()
        self.assertIsNone(self.current_release())
        # 现场与日志保留在暂存目录里
        self.assertTrue((self.root / "releases" / "staging" / "11-abcdefg" / "logs").is_dir())
        self.assertTrue((self.root / "releases" / "lock").exists() is False)

    def test_subsequent_publish_failure_does_not_roll_back_automatically(self):
        self.assert_succeeds(self.deploy("12-abcdefg", "first"))
        self.docker_log.unlink()
        self.nacos_state["services"]["lien-file"] = None
        result = self.deploy("13-abcdefg", "second")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("rollback --deploy-root", result.stderr)
        self.assertIn("--to 12-abcdefg", result.stderr)
        calls = "\n".join(self.docker_calls())
        self.assertNotIn(" stop ", calls)
        self.assert_never_destructive()
        # 失败时不写入 current，回滚目标仍是上一版
        self.assertEqual(self.current_release(), "12-abcdefg")

    def test_failed_compose_up_reports_and_keeps_previous_release(self):
        self.assert_succeeds(self.deploy("14-abcdefg", "first"))
        result = self.deploy("15-abcdefg", "second", env={"DOCKER_FAIL_UP": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("rollback --deploy-root", result.stderr)
        self.assertEqual(self.current_release(), "14-abcdefg")
        self.assert_never_destructive()

    def test_rollback_restores_retained_release(self):
        self.assert_succeeds(self.deploy("16-abcdefg", "first"))
        self.assert_succeeds(self.deploy("17-abcdefg", "second"))
        self.assertEqual(self.service_jar("gateway").read_bytes(), b"second-gateway")

        self.docker_log.unlink()
        result = self.run_release(
            "rollback",
            "--deploy-root",
            str(self.root),
            "--to",
            "16-abcdefg",
            "--operator",
            "tester",
        )
        self.assert_succeeds(result)
        for service in SERVICES:
            self.assertEqual(
                self.service_jar(service).read_bytes(), "first-{}".format(service).encode()
            )
        self.assertEqual(self.current_release(), "16-abcdefg")
        calls = "\n".join(self.docker_calls())
        self.assertIn("up -d --build", calls)
        self.assertIn("nginx -s reload", calls)
        self.assert_never_destructive()
        self.assertTrue(list((self.root / "releases" / "16-abcdefg").glob("rollback-*.txt")))

    def test_rollback_to_unknown_release_fails_before_changes(self):
        self.assert_succeeds(self.deploy("18-abcdefg", "first"))
        result = self.run_release(
            "rollback", "--deploy-root", str(self.root), "--to", "does-not-exist"
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("没有保留的发布版本", result.stderr)
        self.assertEqual(self.service_jar("admin").read_bytes(), b"first-admin")

    def test_preflight_is_read_only(self):
        result = self.run_release("preflight", "--deploy-root", str(self.root))
        self.assert_succeeds(result)
        self.assertIn("预检通过", result.stdout)
        self.assert_no_mutations()
        joined = "\n".join(self.docker_calls())
        self.assertIn("config --quiet", joined)
        for forbidden in (" up ", " stop ", "exec"):
            self.assertNotIn(forbidden, joined)

    def test_status_lists_retained_releases(self):
        self.assert_succeeds(self.deploy("19-abcdefg", "first"))
        result = self.run_release("status", "--deploy-root", str(self.root))
        self.assert_succeeds(result)
        self.assertIn("当前生效版本: 19-abcdefg", result.stdout)
        self.assertIn("19-abcdefg", result.stdout)
        self.assertIn("发布锁: 空闲", result.stdout)

    def test_compose_static_check_rejects_missing_service(self):
        compose = self.root / "app" / "docker-compose-app.yml"
        text = compose.read_text().replace(
            "  frameworkjava-portal:\n    build:", "  frameworkjava-portal-renamed:\n    build:"
        )
        compose.write_text(text, encoding="utf-8")
        result = self.deploy("20-abcdefg", "new")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("没有 frameworkjava-portal 服务", result.stderr)
        self.assert_no_mutations()

    def test_rollback_with_explicit_staging_cleans_it(self):
        self.assert_succeeds(self.deploy("21-abcdefg", "first"))
        staging = self.root / "releases" / "staging" / "rollback-21-abcdefg-1-1"
        (staging / "prd-scripts").mkdir(parents=True)
        (staging / "prd-scripts" / "app_release.sh").write_text("stub")

        result = self.run_release(
            "rollback",
            "--deploy-root",
            str(self.root),
            "--to",
            "21-abcdefg",
            "--staging",
            str(staging),
        )
        self.assert_succeeds(result)
        self.assertEqual(self.service_jar("admin").read_bytes(), b"first-admin")
        # 成功回滚后本次暂存目录（含上传的工具）整目录清理
        self.assertFalse(staging.exists())

    def test_deploy_rejects_staging_outside_releases(self):
        package = self.build_package("22-abcdefg", "new")
        outside = Path(self._tmp) / "elsewhere"
        outside.mkdir()
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            "22-abcdefg",
            "--staging",
            str(outside),
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("拒绝操作", result.stderr)
        self.assert_no_mutations()

    def test_compose_static_check_rejects_broken_nacos_addr(self):
        compose = self.root / "app" / "docker-compose-app.yml"
        compose.write_text(
            compose.read_text().replace(
                "NACOS_ADDR: frameworkjava-nacos:8848", "NACOS_ADDR: frameworkjava-nacos:8849"
            ),
            encoding="utf-8",
        )
        result = self.deploy("23-abcdefg", "new")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("NACOS_ADDR", result.stderr)
        self.assert_no_mutations()

    def test_deploy_with_real_verifier_uses_workflow_layout(self):
        """工作流的真实布局：staging/<id>/package + staging/<id>/verify/verify_service_artifacts.py。"""
        release_id = "24-abcdefg"
        package = self.build_boot_package(release_id)
        verify_dir = self.root / "releases" / "staging" / release_id / "verify"
        verify_dir.mkdir(parents=True)
        shutil.copy(ROOT / "scripts" / "verify_service_artifacts.py", verify_dir)

        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            release_id,
            "--verifier",
            str(verify_dir / "verify_service_artifacts.py"),
        )
        self.assert_succeeds(result)
        self.assertIn("校验通过：4/4", result.stdout)
        self.assertEqual(self.current_release(), release_id)

    def test_real_verifier_rejects_wrong_service_artifacts(self):
        release_id = "25-abcdefg"
        package = self.build_boot_package(release_id)
        # 把 portal 的 bootstrap.yml（application.name=lien-portal）装进 file 目录：串位制品
        target = package / "file" / "lien-file-service.jar"
        write_boot_jar(
            target,
            start_class="com.lien.file.App",
            bootstrap=bootstrap_yml("portal"),
        )
        (package / "file" / "lien-file-service.jar.sha256").write_text(
            "{}  lien-file-service.jar\n".format(sha256_of(target)), encoding="utf-8"
        )
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            release_id,
            "--verifier",
            str(ROOT / "scripts" / "verify_service_artifacts.py"),
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("制品结构校验未通过", result.stderr)
        self.assert_no_mutations()

    def test_logs_are_read_by_compose_service_name(self):
        """compose 服务名是 frameworkjava-<name>；传裸服务名会被真 compose 拒绝（桁里也会拒绝）。"""
        self.assert_succeeds(self.deploy("26-abcdefg", "new"))
        calls = [call for call in self.docker_calls() if " logs " in call]
        self.assertTrue(calls, "验收阶段应该读了四服务日志")
        for service in SERVICES:
            expected = "logs --tail=400 --no-color frameworkjava-{}".format(service)
            self.assertTrue(
                any(call.endswith(expected) for call in calls),
                "缺少日志调用：{}（完整记录：{}）".format(expected, calls),
            )

    def test_verification_fails_when_service_rss_exceeds_budget(self):
        result = self.deploy("27-abcdefg", "new", env={"DOCKER_RSS": "600MiB"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("超过阈值 500 MiB", result.stderr)

    def test_path_traversal_in_staging_is_rejected(self):
        package = self.build_package("31-abcdefg", "new")
        outside = "{}/releases/staging/../../outside-{}".format(self.root, os.getpid())
        result = self.run_release(
            "deploy",
            "--deploy-root",
            str(self.root),
            "--package",
            str(package),
            "--release-id",
            "31-abcdefg",
            "--staging",
            outside,
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("拒绝含 ..", result.stderr)
        self.assert_no_mutations()

    def test_status_marks_unsuccessful_release_residue(self):
        healthy = self.nacos_state["services"]["lien-portal"]
        self.nacos_state["services"]["lien-portal"] = None
        self.assertNotEqual(self.deploy("29-abcdefg", "new").returncode, 0)
        self.nacos_state["services"]["lien-portal"] = healthy
        self.assert_succeeds(self.deploy("30-abcdefg", "new"))

        result = self.run_release("status", "--deploy-root", str(self.root))
        self.assert_succeeds(result)
        self.assertIn("30-abcdefg  result=success", result.stdout)
        self.assertIn("29-abcdefg  result=unsuccessful", result.stdout)

    def test_rollback_verifies_the_retained_package(self):
        self.assert_succeeds(self.deploy("28-abcdefg", "first"))
        self.verifier_log.unlink()
        result = self.run_release(
            "rollback",
            "--deploy-root",
            str(self.root),
            "--to",
            "28-abcdefg",
            "--verifier",
            str(self.verifier),
        )
        self.assert_succeeds(result)
        self.assertIn("28-abcdefg", self.verifier_log.read_text())

    def test_nginx_crash_loop_is_waited_for_and_started(self):
        """网关首次上线前 webprd 因 upstream 解析不到网关而重启循环：必须等它恢复或单独启动它。"""
        result = self.deploy(
            "32-abcdefg",
            "new",
            env={"DOCKER_WEBPRD_RUNNING": "0", "NGINX_WAIT_SECONDS": "0"},
        )
        self.assert_succeeds(result)
        self.assertIn("等待其自动恢复", result.stdout)
        calls = "\n".join(self.docker_calls())
        self.assertIn("docker start cid-webprd", calls)
        self.assertIn("nginx -s reload", calls)
        self.assertNotIn("restart", calls)
        self.assertNotIn("down", calls)
        self.assert_never_destructive()

    def test_missing_nginx_container_skips_nginx_step(self):
        result = self.deploy("33-abcdefg", "new", env={"DOCKER_NO_WEBPRD": "1"})
        self.assert_succeeds(result)
        self.assertIn("未找到 frameworkjava-webprd 容器", result.stderr)
        calls = "\n".join(self.docker_calls())
        self.assertNotIn("nginx", calls)
        self.assertNotIn("docker start", calls)

    def stage_oss_package(self, release_id, package, parts=3):
        """把发布包打成 tar.gz 后分片，写到假 OSS 目录，返回 (tar 路径, 总 sha256, 分片数)。"""
        tar_path = Path(self._tmp) / "{}.tar.gz".format(release_id)
        subprocess.run(
            ["tar", "-czf", str(tar_path), "-C", str(package), "."], check=True
        )
        digest = sha256_of(tar_path)
        data = tar_path.read_bytes()
        chunk = (len(data) + parts - 1) // parts
        for index in range(parts):
            target = (
                self.fake_oss / "github-release" / release_id
                / "package.tar.gz.part-{:03d}".format(index)
            )
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data[index * chunk:(index + 1) * chunk])
        return tar_path, digest, parts

    def run_fetch(self, release_id, parts, digest, extra=(), env=None):
        return self.run_release(
            "fetch-package",
            "--deploy-root",
            str(self.root),
            "--release-id",
            release_id,
            "--parts",
            str(parts),
            "--sha256",
            digest,
            "--verifier",
            str(self.verifier),
            *extra,
            env=env or {},
        )

    def fetched_package(self, release_id):
        return self.root / "releases" / "staging" / release_id / "package"

    def test_fetch_package_reassembles_and_verifies(self):
        package = self.build_package("40-abcdefg", "new")
        _, digest, parts = self.stage_oss_package("40-abcdefg", package)
        result = self.run_fetch("40-abcdefg", parts, digest)
        self.assert_succeeds(result)
        self.assertIn("已取回并通过 SHA-256 校验", result.stdout)

        fetched = self.fetched_package("40-abcdefg")
        for service, artifact in ARTIFACTS.items():
            self.assertEqual(
                (fetched / service / "{}.jar".format(artifact)).read_bytes(),
                (package / service / "{}.jar".format(artifact)).read_bytes(),
            )
        # 分片与 tar 用完即删
        staging = self.root / "releases" / "staging" / "40-abcdefg"
        self.assertEqual(list(staging.glob("part-*")), [])
        self.assertFalse((staging / "package.tar.gz").exists())
        # 制品结构校验在服务器上又跑了一遍
        self.assertIn(str(fetched), self.verifier_log.read_text())
        # 预签名 URL 带签名参数，且不泄露密钥
        curl_calls = self.curl_log.read_text()
        self.assertIn("OSSAccessKeyId=AKIDEXAMPLE1234567890", curl_calls)
        self.assertIn("Signature=", curl_calls)
        self.assertNotIn("SECRETEXAMPLE", curl_calls + result.stdout + result.stderr)

    def test_fetch_package_rejects_checksum_mismatch(self):
        package = self.build_package("41-abcdefg", "new")
        _, digest, parts = self.stage_oss_package("41-abcdefg", package)
        result = self.run_fetch("41-abcdefg", parts, "0" * 64)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("校验和不一致", result.stderr)
        self.assertFalse(self.fetched_package("41-abcdefg").joinpath("gateway").exists())
        self.assert_no_mutations()

    def test_fetch_package_fails_on_missing_part(self):
        package = self.build_package("42-abcdefg", "new")
        _, digest, parts = self.stage_oss_package("42-abcdefg", package)
        (self.fake_oss / "github-release" / "42-abcdefg" / "package.tar.gz.part-001").unlink()
        result = self.run_fetch("42-abcdefg", parts, digest)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("下载分片 part-001 失败", result.stderr)
        self.assert_no_mutations()

    def test_fetch_package_requires_oss_config(self):
        package = self.build_package("43-abcdefg", "new")
        _, digest, parts = self.stage_oss_package("43-abcdefg", package)
        env_file = self.root / ".env"
        env_file.write_text(
            "\n".join(
                line for line in env_file.read_text().splitlines()
                if not line.startswith("OSS_")
            ) + "\n",
            encoding="utf-8",
        )
        result = self.run_fetch("43-abcdefg", parts, digest)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("缺少 OSS_BUCKET", result.stderr)
        self.assert_no_mutations()

    def test_fetch_package_rejects_bad_arguments(self):
        for args in (["--parts", "0"], ["--parts", "x"], ["--sha256", "zz"]):
            with self.subTest(args=args):
                result = self.run_release(
                    "fetch-package", "--deploy-root", str(self.root),
                    "--release-id", "44-abcdefg", "--parts", "1", "--sha256", "0" * 64,
                    *args,
                )
                self.assertNotEqual(result.returncode, 0)

    def test_deploy_requires_release_id_and_package(self):
        result = self.run_release("deploy", "--deploy-root", str(self.root))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("deploy 需要 --package", result.stderr)

    def test_unknown_command_exits_2(self):
        result = self.run_release("destroy", "--deploy-root", str(self.root))
        self.assertEqual(result.returncode, 2)
        self.assertIn("未知命令", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
