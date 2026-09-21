#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""scripts/verify_service_artifacts.py 的用例。

覆盖 issue #4 要求的四类场景：正常、制品缺失、同名候选多于一份、JAR 结构无效；
另外覆盖校验和不一致与明文凭据这两条硬性边界。

用例使用「结构上合法的假 Boot JAR」构造 fixture，不依赖 maven 构建，因此可以随时执行：

    python3 scripts/tests/test_verify_service_artifacts.py -v

若仓库里已有真实发布包（release/），最后一个用例会额外校验它。
"""

import hashlib
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
ROOT = SCRIPTS.parent
VERIFIER = SCRIPTS / "verify_service_artifacts.py"

ARTIFACTS = {
    "gateway": "lien-gateway",
    "admin": "lien-admin-service",
    "file": "lien-file-service",
    "portal": "lien-portal-service",
}


def bootstrap_yml(service, username="${NACOS_USERNAME}", password="${NACOS_PASSWORD}"):
    return (
        "spring:\n"
        "  application:\n"
        "    name: lien-{service}\n"
        "  cloud:\n"
        "    nacos:\n"
        "      discovery:\n"
        "        username: {username}\n"
        "        password: {password}\n"
    ).format(service=service, username=username, password=password)


def write_boot_jar(
    path, start_class="com.example.App", class_entry=None, bootstrap=None, boot_inf=True
):
    """写一份能通过结构校验的 Boot 可执行 JAR（内容为占位字节）。

    class_entry 用于模拟「manifest 的 Start-Class 在制品里不存在」的损坏制品，
    默认与 start_class 一致。
    """
    manifest = "\r\n".join(
        [
            "Manifest-Version: 1.0",
            "Main-Class: org.springframework.boot.loader.JarLauncher",
            "Start-Class: {}".format(start_class),
            "Spring-Boot-Version: 3.0.2",
            "Spring-Boot-Classes: BOOT-INF/classes/",
            "Spring-Boot-Lib: BOOT-INF/lib/",
            "",
            "",
        ]
    )
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", manifest)
        if boot_inf:
            archive.writestr("BOOT-INF/classes/", "")
            archive.writestr("BOOT-INF/lib/", "")
            archive.writestr("BOOT-INF/lib/spring-core-6.0.4.jar", b"placeholder")
            archive.writestr(
                "BOOT-INF/classes/{}.class".format(
                    (class_entry or start_class).replace(".", "/")
                ),
                b"\xca\xfe\xba\xbe",
            )
            if bootstrap is not None:
                archive.writestr("BOOT-INF/classes/bootstrap.yml", bootstrap)


def write_plain_jar(path):
    """普通（非 Boot）JAR：是合法 zip，但没有可执行结构。"""
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n")
        archive.writestr("com/example/App.class", b"\xca\xfe\xba\xbe")


def stage_service(release_dir, service, jar_path=None, with_checksum=True):
    artifact = ARTIFACTS[service]
    service_dir = release_dir / service
    service_dir.mkdir(parents=True, exist_ok=True)
    target = service_dir / "{}.jar".format(artifact)
    if jar_path is None:
        write_boot_jar(
            target,
            start_class="com.lien.{}.App".format(service),
            bootstrap=bootstrap_yml(service),
        )
    else:
        shutil.copy(jar_path, target)
    if with_checksum:
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        (service_dir / "{}.jar.sha256".format(artifact)).write_text(
            "{}  {}.jar\n".format(digest, artifact), encoding="utf-8"
        )
    return target


def stage_full_release(release_dir):
    for service in ARTIFACTS:
        stage_service(release_dir, service)
    return release_dir


def run_verifier(release_dir, *extra):
    return subprocess.run(
        [sys.executable, str(VERIFIER), "--release-dir", str(release_dir), *extra],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
    )


class VerifyServiceArtifactsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.mkdtemp(prefix="verify-artifacts-")
        self.release_dir = Path(self._tmp) / "release"
        self.addCleanup(shutil.rmtree, self._tmp, ignore_errors=True)

    def assert_fails(self, result, *expected_fragments):
        self.assertNotEqual(result.returncode, 0, "校验应当失败但返回 0")
        output = result.stdout + result.stderr
        for fragment in expected_fragments:
            self.assertIn(fragment, output, "失败信息里缺少 {!r}：\n{}".format(fragment, output))

    # ---- 场景 1：正常 ----
    def test_complete_release_passes(self):
        stage_full_release(self.release_dir)
        result = run_verifier(self.release_dir)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("校验通过：4/4", result.stdout)

    def test_single_service_can_be_selected(self):
        stage_full_release(self.release_dir)
        result = run_verifier(self.release_dir, "--service", "admin")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("校验通过：1/1", result.stdout)

    # ---- 场景 2：制品缺失 ----
    def test_missing_jar_fails(self):
        stage_full_release(self.release_dir)
        (self.release_dir / "file" / "lien-file-service.jar").unlink()
        (self.release_dir / "file" / "lien-file-service.jar.sha256").unlink()
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "file", "没有任何 .jar")

    def test_missing_service_dir_fails(self):
        stage_full_release(self.release_dir)
        shutil.rmtree(self.release_dir / "portal")
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "portal", "服务目录不存在")

    def test_missing_checksum_fails(self):
        stage_full_release(self.release_dir)
        (self.release_dir / "gateway" / "lien-gateway.jar.sha256").unlink()
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "gateway", "缺少校验和文件")

    # ---- 场景 3：同名候选多于一份 ----
    def test_duplicate_candidate_fails(self):
        stage_full_release(self.release_dir)
        duplicate = self.release_dir / "admin" / "lien-admin-service-0.0.1-SNAPSHOT.jar"
        shutil.copy(self.release_dir / "admin" / "lien-admin-service.jar", duplicate)
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "admin", "同名候选多于一份")
        self.assertIn("lien-admin-service-0.0.1-SNAPSHOT.jar", result.stdout + result.stderr)

    def test_jar_original_residue_fails(self):
        stage_full_release(self.release_dir)
        (self.release_dir / "portal" / "lien-portal-service.jar.original").write_bytes(b"residue")
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "portal", "非发布制品", ".jar.original")

    # ---- 场景 4：JAR 结构无效 ----
    def test_plain_jar_without_boot_structure_fails(self):
        stage_full_release(self.release_dir)
        plain = Path(self._tmp) / "plain.jar"
        write_plain_jar(plain)
        target = self.release_dir / "admin" / "lien-admin-service.jar"
        shutil.copy(plain, target)
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        (self.release_dir / "admin" / "lien-admin-service.jar.sha256").write_text(
            "{}  lien-admin-service.jar\n".format(digest), encoding="utf-8"
        )
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "admin", "Main-Class", "BOOT-INF")

    def test_corrupt_zip_fails(self):
        stage_full_release(self.release_dir)
        target = self.release_dir / "gateway" / "lien-gateway.jar"
        target.write_bytes(b"not a zip file at all")
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "gateway", "不是合法 zip")

    def test_truncated_jar_fails(self):
        stage_full_release(self.release_dir)
        target = self.release_dir / "file" / "lien-file-service.jar"
        data = target.read_bytes()
        target.write_bytes(data[: len(data) // 3])
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        (self.release_dir / "file" / "lien-file-service.jar.sha256").write_text(
            "{}  lien-file-service.jar\n".format(digest), encoding="utf-8"
        )
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "file")

    def test_empty_file_fails(self):
        stage_full_release(self.release_dir)
        target = self.release_dir / "portal" / "lien-portal-service.jar"
        target.write_bytes(b"")
        digest = hashlib.sha256(b"").hexdigest()
        (self.release_dir / "portal" / "lien-portal-service.jar.sha256").write_text(
            "{}  lien-portal-service.jar\n".format(digest), encoding="utf-8"
        )
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "portal", "空文件")

    def test_start_class_missing_from_jar_fails(self):
        stage_full_release(self.release_dir)
        target = self.release_dir / "admin" / "lien-admin-service.jar"
        write_boot_jar(
            target,
            start_class="com.lien.admin.NotThere",
            class_entry="com.lien.admin.App",
            bootstrap=bootstrap_yml("admin"),
        )
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        (self.release_dir / "admin" / "lien-admin-service.jar.sha256").write_text(
            "{}  lien-admin-service.jar\n".format(digest), encoding="utf-8"
        )
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "admin", "Start-Class", "不存在")

    def test_wrong_service_artifact_fails(self):
        stage_full_release(self.release_dir)
        target = self.release_dir / "file" / "lien-file-service.jar"
        write_boot_jar(
            target,
            start_class="com.lien.portal.App",
            bootstrap=bootstrap_yml("portal"),  # portal 的 bootstrap.yml 放进了 file 目录
        )
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        (self.release_dir / "file" / "lien-file-service.jar.sha256").write_text(
            "{}  lien-file-service.jar\n".format(digest), encoding="utf-8"
        )
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "file", "spring.application.name")

    # ---- 边界：校验和与明文凭据 ----
    def test_checksum_mismatch_fails(self):
        stage_full_release(self.release_dir)
        (self.release_dir / "gateway" / "lien-gateway.jar.sha256").write_text(
            "{}  lien-gateway.jar\n".format("0" * 64), encoding="utf-8"
        )
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "gateway", "校验和不一致")

    def test_plaintext_credential_fails(self):
        stage_full_release(self.release_dir)
        target = self.release_dir / "portal" / "lien-portal-service.jar"
        write_boot_jar(
            target,
            start_class="com.lien.portal.App",
            bootstrap=bootstrap_yml("portal", password="Nacos123456"),
        )
        digest = hashlib.sha256(target.read_bytes()).hexdigest()
        (self.release_dir / "portal" / "lien-portal-service.jar.sha256").write_text(
            "{}  lien-portal-service.jar\n".format(digest), encoding="utf-8"
        )
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "portal", "明文凭据")

    def test_all_services_fail_when_release_dir_missing(self):
        result = run_verifier(self.release_dir)
        self.assert_fails(result, "服务目录不存在")
        self.assertIn("校验失败：4/4", result.stderr)

    # ---- 可选：校验真实发布包 ----
    @unittest.skipUnless((ROOT / "release").is_dir(), "尚无 release/ 发布包，跳过真实制品校验")
    def test_real_release_package(self):
        result = run_verifier(ROOT / "release")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("校验通过：4/4", result.stdout)


if __name__ == "__main__":
    unittest.main(verbosity=2)
