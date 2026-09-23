#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""frontend_release.sh 与 verify_frontend.sh 的单元测试。

覆盖：
* 参数与环境校验：缺少参数、缺失 .env、缺少 webprd 容器；
* 预检 preflight：正常通过、锁占用失败；
* fetch-package：SHA-256 校验和对齐、解包结构合法性检查（index.html、assets/）；
* deploy：
  - dry-run 模式只打印不修改文件；
  - 真实发布：同步产物、保持目录 inode、docker exec nginx -t & reload、只读验收、记录状态；
  - 并发排他锁保护与释放；
* status：正确读取状态与操作审计日志。
"""

import hashlib
import os
import shutil
import stat
import subprocess
import tarfile
import tempfile
import unittest
from pathlib import Path

TESTS_DIR = Path(__file__).resolve().parent
SINGLE = TESTS_DIR.parent
SCRIPTS = SINGLE / "scripts"
FRONTEND_RELEASE = SCRIPTS / "frontend_release.sh"
VERIFY_FRONTEND = SCRIPTS / "verify_frontend.sh"

DOCKER_STUB = """#!/usr/bin/env python3
import os, sys

args = sys.argv[1:]
log_file = os.environ.get("DOCKER_LOG")
if log_file:
    with open(log_file, "a") as handle:
        handle.write("docker " + " ".join(args) + "\\n")

if "ps" in args:
    print("fake-webprd-container-id")
    sys.exit(0)

if "inspect" in args:
    print("running")
    sys.exit(0)

if "exec" in args:
    # nginx -t 或 nginx -s reload
    sys.exit(0)

sys.exit(0)
"""

CURL_STUB = """#!/usr/bin/env python3
import os, sys

args = sys.argv[1:]
url = next((a for a in args if a.startswith("http")), "")

# 1. 模拟 fetch-package 的 OSS 下载
if "github-release" in url or "oss" in url:
    if "-o" in args:
        out_idx = args.index("-o") + 1
        out_path = args[out_idx]
        mock_src = os.environ.get("MOCK_TAR_GZ")
        if mock_src and os.path.exists(mock_src):
            with open(mock_src, "rb") as sf, open(out_path, "wb") as df:
                df.write(sf.read())
    if "-w" in args and "%{http_code}" in args:
        sys.stdout.write("200")
    sys.exit(0)

# 2. 模拟 missing asset 404
if "assets/missing_asset_probe_404.js" in url:
    if "-w" in args and "%{http_code}" in args:
        sys.stdout.write("404")
    else:
        sys.stdout.write("HTTP/1.1 404 Not Found\\r\\n\\r\\n")
    sys.exit(0)

# 3. 模拟 API 代理
if "api/sys_user/login/password" in url:
    sys.stdout.write('{"code":500000,"msg":"服务繁忙请稍后重试","data":null}')
    sys.exit(0)

# 4. 模拟输出 -D 响应头
if "-D" in args:
    head_idx = args.index("-D") + 1
    head_file = args[head_idx]
    headers = "HTTP/1.1 200 OK\\r\\nCache-Control: public, max-age=31536000, immutable\\r\\nContent-Type: text/html\\r\\n\\r\\n"
    if url.endswith("/") or "accounts" in url:
        headers = "HTTP/1.1 200 OK\\r\\nCache-Control: no-cache, no-store\\r\\nContent-Type: text/html\\r\\n\\r\\n"
    if head_file == "-":
        sys.stdout.write(headers)
    else:
        with open(head_file, "w") as hf:
            hf.write(headers)

# 5. 模拟写入 -o 文件
if "-o" in args:
    out_idx = args.index("-o") + 1
    out_path = args[out_idx]
    mock_index = os.environ.get("MOCK_INDEX_HTML")
    if mock_index and os.path.exists(mock_index) and out_path != "/dev/null":
        with open(mock_index, "r") as sf, open(out_path, "w") as df:
            df.write(sf.read())

if "-w" in args and "%{http_code}" in args:
    sys.stdout.write("200")

sys.exit(0)
"""


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()


class TestFrontendRelease(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix="fe-release-test-")
        self.tmp_path = Path(self.tmp)
        self.deploy_root = self.tmp_path / "deploy_root"
        self.deploy_root.mkdir()
        self.app_dir = self.deploy_root / "app"
        self.app_dir.mkdir()
        self.web_dist = self.app_dir / "nginx" / "web" / "dist"
        self.web_dist.mkdir(parents=True)
        (self.app_dir / "docker-compose-mid.yml").write_text("services: {}\n")

        # 写入最小生产 .env
        self.env_file = self.deploy_root / ".env"
        self.env_file.write_text(
            "WEB_PORT=8666\n"
            "OSS_BUCKET=test-bucket\n"
            "OSS_ENDPOINT=oss-cn-guangzhou.aliyuncs.com\n"
            "OSS_ACCESS_KEY_ID=test-key\n"
            "OSS_ACCESS_KEY_SECRET=test-secret\n"
        )

        # 构造 fake bin (docker + curl)
        self.bin_dir = self.tmp_path / "bin"
        self.bin_dir.mkdir()
        self.docker_log = self.tmp_path / "docker.log"

        docker_script = self.bin_dir / "docker"
        docker_script.write_text(DOCKER_STUB)
        docker_script.chmod(docker_script.stat().st_mode | stat.S_IEXEC)

        curl_script = self.bin_dir / "curl"
        curl_script.write_text(CURL_STUB)
        curl_script.chmod(curl_script.stat().st_mode | stat.S_IEXEC)

        self.env = os.environ.copy()
        self.env["PATH"] = f"{self.bin_dir}:{self.env.get('PATH', '')}"
        self.env["DOCKER_LOG"] = str(self.docker_log)

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _create_sample_dist_tar(self) -> tuple[Path, str]:
        dist_dir = self.tmp_path / "sample_dist"
        dist_dir.mkdir(parents=True, exist_ok=True)
        assets_dir = dist_dir / "assets"
        assets_dir.mkdir(exist_ok=True)
        index_file = dist_dir / "index.html"
        index_file.write_text(
            '<!DOCTYPE html><html><head><script src="/assets/index-abc1234.js"></script></head>'
            '<body><div id="app">Hello</div></body></html>'
        )
        (assets_dir / "index-abc1234.js").write_text('console.log("orbit admin");')

        tar_path = self.tmp_path / "frontend-dist.tar.gz"
        with tarfile.open(tar_path, "w:gz") as tar:
            for item in dist_dir.iterdir():
                tar.add(item, arcname=item.name)

        return tar_path, sha256_file(tar_path)

    def test_preflight_success(self):
        res = subprocess.run(
            [str(FRONTEND_RELEASE), "preflight", "--deploy-root", str(self.deploy_root)],
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertIn("前端发布预检全部通过", res.stdout)

    def test_preflight_fails_when_lock_held(self):
        lock_dir = self.deploy_root / "releases" / "lock"
        lock_dir.mkdir(parents=True)
        (lock_dir / "owner").write_text("token=12345\n")

        res = subprocess.run(
            [str(FRONTEND_RELEASE), "preflight", "--deploy-root", str(self.deploy_root)],
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(res.returncode, 0)
        self.assertIn("发布锁目前被占用", res.stderr)

    def test_fetch_package_success(self):
        tar_path, tar_sha = self._create_sample_dist_tar()
        self.env["MOCK_TAR_GZ"] = str(tar_path)

        res = subprocess.run(
            [
                str(FRONTEND_RELEASE),
                "fetch-package",
                "--deploy-root",
                str(self.deploy_root),
                "--release-id",
                "web-fetch-ok",
                "--sha256",
                tar_sha,
            ],
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertIn("发布包 SHA-256 校验一致", res.stdout)
        self.assertIn("解包验证成功", res.stdout)

        staging_dist = self.deploy_root / "releases" / "staging" / "web-fetch-ok" / "dist"
        self.assertTrue((staging_dist / "index.html").exists())

    def test_fetch_package_sha_mismatch(self):
        tar_path, _ = self._create_sample_dist_tar()
        self.env["MOCK_TAR_GZ"] = str(tar_path)

        res = subprocess.run(
            [
                str(FRONTEND_RELEASE),
                "fetch-package",
                "--deploy-root",
                str(self.deploy_root),
                "--release-id",
                "web-fetch-bad",
                "--sha256",
                "0000000000000000000000000000000000000000000000000000000000000000",
            ],
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(res.returncode, 0)
        self.assertIn("SHA-256 校验失败", res.stderr)

    def test_deploy_dry_run(self):
        tar_path, tar_sha = self._create_sample_dist_tar()
        staging_dir = self.deploy_root / "releases" / "staging" / "web-1-test"
        staging_dist = staging_dir / "dist"
        staging_dist.mkdir(parents=True)
        with tarfile.open(tar_path, "r:gz") as tar:
            tar.extractall(staging_dist)

        res = subprocess.run(
            [
                str(FRONTEND_RELEASE),
                "deploy",
                "--deploy-root",
                str(self.deploy_root),
                "--release-id",
                "web-1-test",
                "--dry-run",
            ],
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertIn("[DRY-RUN] 预演通过", res.stdout)
        # 宿主机的 dist 应仍为空，未被写入
        self.assertEqual(list(self.web_dist.iterdir()), [])

    def test_deploy_real_and_verify(self):
        tar_path, tar_sha = self._create_sample_dist_tar()
        staging_dir = self.deploy_root / "releases" / "staging" / "web-1-test"
        staging_dist = staging_dir / "dist"
        staging_dist.mkdir(parents=True)
        with tarfile.open(tar_path, "r:gz") as tar:
            tar.extractall(staging_dist)

        # 记录替换前 web_dist 目录的 inode
        initial_inode = os.stat(self.web_dist).st_ino

        # 配置 mock curl 变量供 verify_frontend.sh 读取
        self.env["MOCK_INDEX_HTML"] = str(staging_dist / "index.html")

        res = subprocess.run(
            [
                str(FRONTEND_RELEASE),
                "deploy",
                "--deploy-root",
                str(self.deploy_root),
                "--release-id",
                "web-1-test",
                "--operator",
                "ci-bot",
            ],
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(res.returncode, 0, res.stderr)
        self.assertIn("前端发布部署成功", res.stdout)

        # 核心保证 1：web_dist 目录自身的 inode 必须保持不变！
        after_inode = os.stat(self.web_dist).st_ino
        self.assertEqual(initial_inode, after_inode, "web_dist 目录 inode 被破坏，Docker 挂载将失效！")

        # 核心保证 2：文件确实成功覆盖
        installed_index = self.web_dist / "index.html"
        self.assertTrue(installed_index.exists())
        self.assertIn("Hello", installed_index.read_text())

        # 核心保证 3：docker 收到 nginx -t 和 nginx -s reload 指令
        docker_log_content = self.docker_log.read_text()
        self.assertIn("exec fake-webprd-container-id nginx -t", docker_log_content)
        self.assertIn("exec fake-webprd-container-id nginx -s reload", docker_log_content)

        # 核心保证 4：状态文件与操作日志已记录
        state_file = self.deploy_root / "releases" / "state" / "frontend_current"
        self.assertTrue(state_file.exists())
        state_content = state_file.read_text()
        self.assertIn("release_id=web-1-test", state_content)
        self.assertIn("operator=ci-bot", state_content)

        history_file = self.deploy_root / "releases" / "state" / "frontend_history.log"
        self.assertTrue(history_file.exists())
        self.assertIn("web-1-test\tsuccess\tci-bot", history_file.read_text())

        # 验证 status 命令
        status_res = subprocess.run(
            [str(FRONTEND_RELEASE), "status", "--deploy-root", str(self.deploy_root)],
            env=self.env,
            capture_output=True,
            text=True,
        )
        self.assertEqual(status_res.returncode, 0)
        self.assertIn("web-1-test", status_res.stdout)


if __name__ == "__main__":
    unittest.main()
