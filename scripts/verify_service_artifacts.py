#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""校验四服务（gateway/admin/file/portal）发布包中的 Spring Boot 可执行 JAR。

只依赖 Python 3 标准库（zipfile / hashlib），可离线、可重复执行。

校验的发布包布局（由 scripts/build_release.sh 生成）::

    <release-dir>/
    ├── gateway/lien-gateway.jar + lien-gateway.jar.sha256
    ├── admin/lien-admin-service.jar + lien-admin-service.jar.sha256
    ├── file/lien-file-service.jar + lien-file-service.jar.sha256
    └── portal/lien-portal-service.jar + lien-portal-service.jar.sha256

每个服务目录必须恰好一份 JAR、一份与之一致的校验和，且不得残留 .jar.original
等非发布制品。以下任一情况都会导致非零退出码：

* 制品缺失（服务目录不存在、没有 JAR）；
* 同名候选多于一份（或目录里混入未预期的文件）；
* JAR 结构无效（不是 zip、损坏、缺少 Boot 启动结构/Start-Class/bootstrap.yml）；
* 校验和缺失或不匹配；
* bootstrap.yml 里出现明文凭据（必须使用 ${NACOS_USERNAME} / ${NACOS_PASSWORD} 占位符）。

用法::

    scripts/verify_service_artifacts.py [--release-dir release] [--service admin]...

退出码：0 全部通过，1 有检查项失败，2 用法/环境错误。
"""

import argparse
import hashlib
import re
import sys
import zipfile
from pathlib import Path

# 服务名 -> Maven artifactId（发布包中的 JAR 基名）
SERVICES = {
    "gateway": "lien-gateway",
    "admin": "lien-admin-service",
    "file": "lien-file-service",
    "portal": "lien-portal-service",
}

EXPECTED_MAIN_CLASS_SUFFIX = "JarLauncher"
MAIN_CLASS_PREFIX = "org.springframework.boot.loader"
USERNAME_PLACEHOLDER = "${NACOS_USERNAME}"
PASSWORD_PLACEHOLDER = "${NACOS_PASSWORD}"
CREDENTIAL_KEY_RE = re.compile(r"^\s*(username|password):\s*(\S+)\s*$", re.MULTILINE)
APP_NAME_RE = re.compile(r"^\s{2,}name:\s*(\S+)\s*$", re.MULTILINE)


class Checks(object):
    """收集某个服务的检查结果。"""

    def __init__(self, service):
        self.service = service
        self.failures = []

    def fail(self, message):
        self.failures.append(message)

    def require(self, condition, message):
        if not condition:
            self.fail(message)
        return bool(condition)


def parse_manifest(raw):
    """解析 MANIFEST.MF（处理 72 字节折行）。"""
    unfolded = []
    for line in raw.splitlines():
        if line.startswith(" ") and unfolded:
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    manifest = {}
    for line in unfolded:
        if ":" in line:
            key, _, value = line.partition(":")
            manifest[key.strip()] = value.strip()
    return manifest


def read_checksum(checksum_file, checks):
    if not checksum_file.is_file():
        checks.fail("缺少校验和文件: {}".format(checksum_file.name))
        return None
    text = checksum_file.read_text(encoding="utf-8", errors="replace").strip()
    token = text.split()[0] if text.split() else ""
    if not re.fullmatch(r"[0-9a-fA-F]{64}", token):
        checks.fail("校验和文件格式无效（期望 64 位十六进制 SHA-256）: {}".format(checksum_file.name))
        return None
    return token.lower()


def file_sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_jar(service, jar_path, checks):
    """校验单个 JAR 的可执行结构；返回摘要字符串（失败时返回 None）。"""
    if not jar_path.is_file():
        checks.fail("未找到制品: {}".format(jar_path))
        return None
    size = jar_path.stat().st_size
    if size == 0:
        checks.fail("制品为空文件（0 字节）: {}".format(jar_path))
        return None

    try:
        with zipfile.ZipFile(jar_path) as archive:
            bad_member = archive.testzip()
            if bad_member is not None:
                checks.fail("JAR 结构无效：成员 {} 损坏".format(bad_member))
                return None
            names = archive.namelist()

            if "META-INF/MANIFEST.MF" not in names:
                checks.fail("JAR 结构无效：缺少 META-INF/MANIFEST.MF")
                return None
            manifest = parse_manifest(
                archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
            )

            main_class = manifest.get("Main-Class", "")
            if not main_class:
                checks.fail("JAR 结构无效：manifest 缺少 Main-Class（不是 Spring Boot 可执行 JAR）")
            elif not (
                main_class.startswith(MAIN_CLASS_PREFIX)
                and main_class.rsplit(".", 1)[-1].endswith(EXPECTED_MAIN_CLASS_SUFFIX)
            ):
                checks.fail(
                    "JAR 结构无效：Main-Class={} 不是 Spring Boot launcher".format(main_class)
                )

            start_class = manifest.get("Start-Class", "")
            if not start_class or not re.fullmatch(r"[\w.$]+", start_class):
                checks.fail("JAR 结构无效：manifest 缺少有效的 Start-Class（无主类入口）")
                start_class = ""

            if not any(name.startswith("BOOT-INF/classes/") for name in names):
                checks.fail("JAR 结构无效：缺少 BOOT-INF/classes/")
            if not any(name.startswith("BOOT-INF/lib/") for name in names):
                checks.fail("JAR 结构无效：缺少 BOOT-INF/lib/")

            lib_jars = [
                name
                for name in names
                if name.startswith("BOOT-INF/lib/")
                and name.endswith(".jar")
                and name.count("/") == 2
            ]
            if not lib_jars:
                checks.fail("JAR 结构无效：BOOT-INF/lib/ 下没有任何依赖 JAR")

            if start_class:
                entry = "BOOT-INF/classes/{}.class".format(start_class.replace(".", "/"))
                checks.require(
                    entry in names,
                    "JAR 结构无效：manifest 声明的 Start-Class {} 在制品中不存在（可能对应错误的模块）".format(
                        start_class
                    ),
                )

            bootstrap_name = "BOOT-INF/classes/bootstrap.yml"
            if bootstrap_name not in names:
                checks.fail("JAR 结构无效：缺少 {}，无法确认配置来源".format(bootstrap_name))
                bootstrap = None
            else:
                try:
                    bootstrap = archive.read(bootstrap_name).decode("utf-8")
                except UnicodeDecodeError:
                    bootstrap = None
                    checks.fail("{} 不是合法 UTF-8 文本".format(bootstrap_name))
    except zipfile.BadZipFile as error:
        checks.fail("JAR 结构无效：不是合法 zip（{}）".format(error))
        return None

    if bootstrap is not None:
        for key, placeholder in (
            ("username", USERNAME_PLACEHOLDER),
            ("password", PASSWORD_PLACEHOLDER),
        ):
            values = CREDENTIAL_KEY_RE.findall(bootstrap)
            literal = [value for name, value in values if name == key and not value.startswith("${")]
            if literal:
                checks.fail(
                    "bootstrap.yml 出现明文凭据：{}: {}（必须使用 {} 占位符）".format(
                        key, literal[0], placeholder
                    )
                )
        for key, placeholder in (
            ("username", USERNAME_PLACEHOLDER),
            ("password", PASSWORD_PLACEHOLDER),
        ):
            checks.require(
                placeholder in bootstrap,
                "bootstrap.yml 缺少 {} 占位符，无法确认制品新鲜度".format(placeholder),
            )
        app_names = APP_NAME_RE.findall(bootstrap)
        checks.require(
            app_names == ["lien-{}".format(service)],
            "bootstrap.yml 的 spring.application.name 为 {}，期望 lien-{}（制品与目录不对应）".format(
                app_names if app_names else "<缺失>", service
            ),
        )

    return "Main-Class={}, Start-Class={}, libs={}, {} bytes".format(
        main_class or "<缺失>", start_class or "<缺失>", len(lib_jars), size
    )


def verify_service(release_dir, service):
    checks = Checks(service)
    artifact_id = SERVICES[service]
    service_dir = release_dir / service
    expected_jar = service_dir / "{}.jar".format(artifact_id)
    expected_checksum = service_dir / "{}.jar.sha256".format(artifact_id)

    if not service_dir.is_dir():
        checks.fail("制品缺失：服务目录不存在 {}".format(service_dir))
        return checks, None

    entries = sorted(entry.name for entry in service_dir.iterdir())
    candidates = [entry for entry in service_dir.iterdir() if entry.name.endswith(".jar")]

    if len(candidates) > 1:
        checks.fail(
            "同名候选多于一份（{} 个）：{}".format(
                len(candidates), ", ".join(sorted(entry.name for entry in candidates))
            )
        )
    elif not candidates:
        checks.fail("制品缺失：{} 下没有任何 .jar".format(service_dir))

    unexpected = [
        name
        for name in entries
        if name not in (expected_jar.name, expected_checksum.name)
    ]
    if unexpected:
        checks.fail(
            "服务目录含非发布制品（期望恰好 {} + {}）：{}".format(
                expected_jar.name, expected_checksum.name, ", ".join(unexpected)
            )
        )

    if candidates and expected_jar not in candidates:
        checks.fail(
            "制品命名不符合约定：期望 {}，实际 {}".format(
                expected_jar.name, ", ".join(sorted(entry.name for entry in candidates))
            )
        )

    summary = verify_jar(service, expected_jar, checks)

    expected_hash = read_checksum(expected_checksum, checks)
    if expected_hash is not None and summary is not None:
        actual_hash = file_sha256(expected_jar)
        if actual_hash != expected_hash:
            checks.fail(
                "校验和不一致：{} 记录 {}，实际 {}".format(
                    expected_checksum.name, expected_hash, actual_hash
                )
            )
        else:
            summary += ", sha256={}…".format(actual_hash[:12])

    return checks, summary


def main(argv=None):
    parser = argparse.ArgumentParser(
        description="校验四服务的 Spring Boot 可执行制品发布包",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--release-dir",
        default="release",
        help="发布包目录（默认: release，即 <repo>/release）",
    )
    parser.add_argument(
        "--service",
        action="append",
        choices=sorted(SERVICES),
        help="只校验指定服务（可重复；默认校验全部四个）",
    )
    args = parser.parse_args(argv)

    release_dir = Path(args.release_dir).expanduser().resolve()
    services = args.service or ["gateway", "admin", "file", "portal"]

    print("制品校验：{}（{}）".format(release_dir, "、".join(services)), flush=True)
    failed_services = 0
    failed_checks = 0
    for service in services:
        checks, summary = verify_service(release_dir, service)
        if checks.failures:
            failed_services += 1
            failed_checks += len(checks.failures)
            for message in checks.failures:
                print("[FAIL] {:8s} {}".format(service, message), file=sys.stderr, flush=True)
        else:
            print(
                "[ OK ] {:8s} {} ({})".format(service, SERVICES[service] + ".jar", summary),
                flush=True,
            )

    if failed_services:
        print(
            "校验失败：{}/{} 个服务未通过，共 {} 项失败".format(
                failed_services, len(services), failed_checks
            ),
            file=sys.stderr,
            flush=True,
        )
        return 1

    print("校验通过：{}/{} 个服务制品可用".format(len(services), len(services)), flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        sys.exit(2)
