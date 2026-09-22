#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""oss_presign.py 的用例。

签名正确性用「官方 oss2 SDK 生成的固定签名向量」锁住（离线比对，不依赖 oss2 安装）：
生成这些向量的方式是 `oss2.Bucket(Auth(AK,SK), endpoint, bucket).sign_url(method, key, 3600)`，
把返回 URL 里的 Expires 与 Signature 取出来作为期望值。这保证我们自己手算的 V1 签名
与官方实现逐字节一致，而不是「看起来像」。

    python3 deploy/prd/single/tests/test_oss_presign.py -v
"""

import importlib.util
import os
import subprocess
import sys
import unittest
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse

SCRIPTS = Path(__file__).resolve().parents[1] / "scripts"
PRESIGN = SCRIPTS / "oss_presign.py"

BUCKET = "frameworkjava-release"
ENDPOINT = "oss-cn-guangzhou.aliyuncs.com"
ACCESS_KEY_ID = "AKIDEXAMPLE1234567890"
ACCESS_KEY_SECRET = "SECRETEXAMPLE1234567890abcdefghij"

# 由官方 oss2 SDK 生成，锁住签名算法（见模块 docstring）
VECTORS = [
    ("PUT", "oss.jpg", 1790100153, "L/HdCwpQ24fTjTExy5t0YkNE9I4="),
    ("GET", "oss.jpg", 1790100153, "PT4JR/PAirPro7fk5cp5qsAffTE="),
    ("PUT", "github-release/12-1a2b3c4/package.tar.gz", 1790100153, "eZ0khBl+sApUT3uuru+kwAGpYJg="),
    ("GET", "github-release/12-1a2b3c4/package.tar.gz", 1790100153, "yXQbPe7/VKzl4yVwnonAdwCPmAE="),
    ("PUT", "github-release/a b/中文.bin", 1790100153, "7RVYFau/eU3Dsw2RGy1hocGKElA="),
]


def load_module():
    spec = importlib.util.spec_from_file_location("oss_presign", str(PRESIGN))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


PRESIGN_MODULE = load_module()


class OssPresignTest(unittest.TestCase):
    def run_cli(self, args, env=None):
        environment = dict(os.environ)
        environment.update(
            env
            if env is not None
            else {
                "OSS_BUCKET": BUCKET,
                "OSS_ENDPOINT": ENDPOINT,
                "OSS_ACCESS_KEY_ID": ACCESS_KEY_ID,
                "OSS_ACCESS_KEY_SECRET": ACCESS_KEY_SECRET,
            }
        )
        return subprocess.run(
            [sys.executable, str(PRESIGN)] + list(args),
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            universal_newlines=True,
            env=environment,
        )

    def test_signature_matches_official_sdk_vectors(self):
        for method, key, expires, expected in VECTORS:
            with self.subTest(key=key, method=method):
                actual = PRESIGN_MODULE.sign(
                    method, BUCKET, key, expires, ACCESS_KEY_SECRET
                )
                self.assertEqual(actual, expected)

    def test_signature_covers_method_key_bucket_and_expiry(self):
        base = PRESIGN_MODULE.sign("PUT", BUCKET, "a/b.bin", 1790100153, ACCESS_KEY_SECRET)
        self.assertNotEqual(
            base, PRESIGN_MODULE.sign("GET", BUCKET, "a/b.bin", 1790100153, ACCESS_KEY_SECRET)
        )
        self.assertNotEqual(
            base, PRESIGN_MODULE.sign("PUT", BUCKET, "a/c.bin", 1790100153, ACCESS_KEY_SECRET)
        )
        self.assertNotEqual(
            base, PRESIGN_MODULE.sign("PUT", "other-bucket", "a/b.bin", 1790100153, ACCESS_KEY_SECRET)
        )
        self.assertNotEqual(
            base, PRESIGN_MODULE.sign("PUT", BUCKET, "a/b.bin", 1790100154, ACCESS_KEY_SECRET)
        )
        self.assertNotEqual(
            base, PRESIGN_MODULE.sign("PUT", BUCKET, "a/b.bin", 1790100153, ACCESS_KEY_SECRET + "x")
        )

    def test_url_shape(self):
        url = PRESIGN_MODULE.presign(
            "PUT", BUCKET, ENDPOINT, "github-release/1-abc/package.tar.gz",
            1790100153, ACCESS_KEY_ID, ACCESS_KEY_SECRET,
        )
        parsed = urlparse(url)
        self.assertEqual(parsed.scheme, "https")
        self.assertEqual(parsed.netloc, "{}.{}".format(BUCKET, ENDPOINT))
        self.assertEqual(parsed.path, "/github-release/1-abc/package.tar.gz")
        query = parse_qs(parsed.query)
        self.assertEqual(query["OSSAccessKeyId"], [ACCESS_KEY_ID])
        self.assertEqual(query["Expires"], ["1790100153"])
        self.assertEqual(
            unquote(query["Signature"][0]),
            PRESIGN_MODULE.sign("PUT", BUCKET, "github-release/1-abc/package.tar.gz",
                                1790100153, ACCESS_KEY_SECRET),
        )

    def test_key_is_url_encoded_per_segment(self):
        url = PRESIGN_MODULE.presign(
            "PUT", BUCKET, ENDPOINT, "github-release/a b/中文.bin",
            1790100153, ACCESS_KEY_ID, ACCESS_KEY_SECRET,
        )
        self.assertIn("/github-release/a%20b/%E4%B8%AD%E6%96%87.bin?", url)
        self.assertNotIn(" ", url)

    def test_cli_prints_one_url_without_leaking_secret(self):
        result = self.run_cli(
            ["--method", "PUT", "--key", "github-release/1-abc/package.tar.gz", "--now", "1790096553"]
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = [line for line in result.stdout.splitlines() if line]
        self.assertEqual(len(lines), 1, result.stdout)
        self.assertTrue(lines[0].startswith("https://{}.{}/".format(BUCKET, ENDPOINT)))
        self.assertIn("Expires=1790100153", lines[0])
        self.assertNotIn(ACCESS_KEY_SECRET, result.stdout + result.stderr)

    def test_cli_rejects_missing_credentials(self):
        result = self.run_cli(["--key", "a/b.bin"], env={})
        self.assertEqual(result.returncode, 2)
        self.assertIn("OSS_BUCKET", result.stderr)

    def test_env_file_supplies_credentials_without_environment(self):
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text(
                "# 注释\n"
                "MYSQL_ROOT_PASSWORD=irrelevant\n"
                "OSS_BUCKET={}\n"
                "OSS_ENDPOINT={}\n"
                "OSS_ACCESS_KEY_ID='{}'\n"
                "OSS_ACCESS_KEY_SECRET=\"{}\"\n".format(
                    BUCKET, ENDPOINT, ACCESS_KEY_ID, ACCESS_KEY_SECRET
                ),
                encoding="utf-8",
            )
            result = self.run_cli(
                ["--key", "github-release/1-a/b.bin", "--env-file", str(path)], env={}
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(result.stdout.startswith("https://{}.{}/".format(BUCKET, ENDPOINT)))
        self.assertNotIn(ACCESS_KEY_SECRET, result.stdout + result.stderr)

    def test_environment_overrides_env_file(self):
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".env"
            path.write_text("OSS_BUCKET=from-file\nOSS_ENDPOINT={}\n".format(ENDPOINT), encoding="utf-8")
            result = self.run_cli(
                ["--key", "a/b.bin", "--env-file", str(path)],
                env={
                    "OSS_BUCKET": BUCKET,
                    "OSS_ACCESS_KEY_ID": ACCESS_KEY_ID,
                    "OSS_ACCESS_KEY_SECRET": ACCESS_KEY_SECRET,
                },
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(result.stdout.startswith("https://{}.{}/".format(BUCKET, ENDPOINT)))

    def test_cli_rejects_bad_key_and_expiry(self):
        for args in (["--key", "/absolute"], ["--key", "a/../b"], ["--key", ""],
                     ["--key", "a/b", "--expires", "0"], ["--key", "a/b", "--expires", "999999999"]):
            with self.subTest(args=args):
                result = self.run_cli(args)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)

    def test_accelerate_endpoint_is_supported(self):
        result = self.run_cli(
            ["--key", "github-release/1-abc/package.tar.gz"],
            env={
                "OSS_BUCKET": BUCKET,
                "OSS_ENDPOINT": "oss-accelerate.aliyuncs.com",
                "OSS_ACCESS_KEY_ID": ACCESS_KEY_ID,
                "OSS_ACCESS_KEY_SECRET": ACCESS_KEY_SECRET,
            },
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("https://{}.oss-accelerate.aliyuncs.com/".format(BUCKET), result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
