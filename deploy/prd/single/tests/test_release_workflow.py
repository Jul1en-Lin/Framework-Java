#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生产发布工作流与服务器脚本的静态约束用例（issue #5 的验收条件）。

这些断言不启动 Docker、不连服务器，只检查「结构性安全属性」——它们正是发布这件事
最容易在日后被改坏的地方（触发方式、凭据来源、主机校验、破坏性命令、并发与回滚）。

    python3 deploy/prd/single/tests/test_release_workflow.py -v
"""

import os
import re
import subprocess
import unittest
from pathlib import Path

SINGLE = Path(__file__).resolve().parents[1]
ROOT = SINGLE.parents[2]
WORKFLOW = ROOT / ".github" / "workflows" / "release-prd.yml"
SCRIPTS = SINGLE / "scripts"
APP_RELEASE = SCRIPTS / "app_release.sh"
VERIFY = SCRIPTS / "verify_deployment.sh"
HEALTH = SCRIPTS / "check_service_health.py"


def code_only(text):
    """去掉整行注释：安全断言要看真正会执行的内容，而不是注释里的说明。"""
    return "\n".join(
        line for line in text.splitlines() if not line.lstrip().startswith("#")
    )


class ReleaseWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(WORKFLOW.is_file(), "缺少 {}".format(WORKFLOW))
        self.workflow = WORKFLOW.read_text(encoding="utf-8")
        self.app_release = APP_RELEASE.read_text(encoding="utf-8")
        self.verify = VERIFY.read_text(encoding="utf-8")

    # ---- 触发方式 ----

    def test_workflow_is_manual_only(self):
        triggers = self.workflow.split("on:", 1)[1].split("permissions:", 1)[0]
        self.assertIn("workflow_dispatch", triggers)
        for forbidden in ("push:", "pull_request:", "schedule:", "workflow_run:", "repository_dispatch:"):
            self.assertNotIn(forbidden, code_only(triggers), "不应存在自动触发：{}".format(forbidden))

    def test_workflow_has_concurrency_without_cancelling_running_release(self):
        self.assertIn("concurrency:", self.workflow)
        self.assertIn("group: prd-release", self.workflow)
        self.assertIn("cancel-in-progress: false", self.workflow)
        # 工作流级 concurrency：整个发布/回滚串行执行
        self.assertLess(
            self.workflow.index("concurrency:"), self.workflow.index("jobs:")
        )

    def test_inputs_require_explicit_confirmation(self):
        self.assertIn("confirm:", self.workflow)
        self.assertIn('!= "frameworkjava-prd"', self.workflow)
        self.assertIn("options:", self.workflow)
        self.assertIn("rollback", self.workflow)

    def test_deploy_offers_dry_run_before_first_real_release(self):
        self.assertIn("dry_run:", self.workflow)
        self.assertIn('extra="--dry-run"', self.workflow)
        self.assertIn("dry_run 只适用于 deploy", self.workflow)

    # ---- 凭据与主机校验 ----

    def test_uses_production_environment_for_credentials(self):
        self.assertIn("environment: production", self.workflow)
        for required in ("PRD_SSH_PRIVATE_KEY", "PRD_SSH_KNOWN_HOSTS", "PRD_SSH_HOST", "PRD_SSH_USER"):
            self.assertIn(required, self.workflow)
        self.assertIn("production Environment 缺少必填项", self.workflow)

    def test_server_details_are_secrets_not_public_variables(self):
        # 公开仓库的 Variables 任何人可读，会把服务器地址/用户/路径暴露出去
        for name in ("PRD_SSH_HOST", "PRD_SSH_USER", "PRD_DEPLOY_ROOT"):
            self.assertIn("secrets.{}".format(name), self.workflow)
            self.assertNotIn("vars.{}".format(name), self.workflow)

    def test_ssh_uses_strict_host_verification_and_ignores_runner_config(self):
        self.assertIn("StrictHostKeyChecking=yes", self.workflow)
        self.assertIn("UserKnownHostsFile=", self.workflow)
        self.assertIn("IdentitiesOnly=yes", self.workflow)
        self.assertIn("-F /dev/null", self.workflow)
        self.assertIn("BatchMode=yes", self.workflow)

    def test_known_hosts_is_trusted_input_not_scanned_at_deploy_time(self):
        for text, name in ((self.workflow, "workflow"), (self.app_release, "app_release.sh")):
            self.assertNotIn("ssh-keyscan", code_only(text), "{} 不应在部署时扫描主机密钥".format(name))
            self.assertNotIn("StrictHostKeyChecking=no", code_only(text))
        self.assertIn("awk 'NF && $1 !~ /^#/ { if (NF < 3) bad = 1 } END { exit bad }'", self.workflow)

    def test_private_key_and_secrets_are_never_printed(self):
        self.assertNotIn('echo "$PRIVATE_KEY"', self.workflow)
        self.assertNotIn("cat \"$key_file\"", self.workflow)
        self.assertIn("清理 runner 上的临时密钥", self.workflow)
        self.assertIn('rm -rf "$RUNNER_TEMP/sshrun"', self.workflow)

    def test_reachability_is_checked_before_uploading(self):
        # 最后一个「上传发布包」属于 release job（前面 build job 里可能也有同名步骤名）
        self.assertIn("确认 runner 到服务器的 SSH 可达性", self.workflow)
        self.assertLess(
            self.workflow.index("确认 runner 到服务器的 SSH 可达性"),
            self.workflow.rindex("上传发布包"),
        )
        # 三次都失败时必须让这个步骤真的失败，而不是只打个 ::error:: 就继续
        reachability = self.workflow.split("确认 runner 到服务器的 SSH 可达性", 1)[1].split("下载发布包 artifact", 1)[0]
        self.assertIn("exit 1", reachability)

    # ---- 构建与制品传递 ----

    def test_reuses_java17_build_and_artifact_verification(self):
        self.assertIn('JAVA_VERSION: "17"', self.workflow)
        self.assertIn("actions/setup-java@v4", self.workflow)
        self.assertIn("scripts/build_release.sh", self.workflow)
        self.assertIn("python3 scripts/verify_service_artifacts.py --release-dir release", self.workflow)
        self.assertIn("verify_service_artifacts.py", self.workflow)

    def test_release_package_is_passed_through_artifact(self):
        self.assertIn("actions/upload-artifact@v4", self.workflow)
        self.assertIn("actions/download-artifact@v4", self.workflow)
        self.assertIn("needs: [build]", self.workflow)
        self.assertIn("prd-release-package", self.workflow)

    def test_release_tooling_is_pinned_to_the_built_commit(self):
        # 部署使用的工具/脚本必须来自 build job 实际构建的那个 commit，而不是可能已移动的分支名
        self.assertIn("commit_sha: ${{ steps.release_id.outputs.commit_sha }}", self.workflow)
        self.assertIn("ref: ${{ inputs.action == 'deploy' && needs.build.outputs.commit_sha", self.workflow)

    def test_rollback_reuses_the_same_artifact_verification(self):
        rollback_step = self.workflow.split("回滚四个应用服务", 1)[1].split("写入结果摘要", 1)[0]
        self.assertIn("--verifier '$STAGING/verify/verify_service_artifacts.py'", rollback_step)

    def test_deploy_root_is_an_explicit_required_setting(self):
        self.assertIn('missing="$missing PRD_DEPLOY_ROOT"', self.workflow)
        self.assertIn('deploy_root="$DEPLOY_ROOT"', self.workflow)
        self.assertNotIn("/home/ubuntu/framework_java/deploy/prd/single}", self.workflow)

    def test_first_release_observation_window_is_wired(self):
        self.assertIn("post_release_sample_seconds:", self.workflow)
        self.assertIn('echo "SAMPLE_SECONDS=$SAMPLE_SECONDS_INPUT"', self.workflow)

    def test_deploy_uses_staging_dir_and_server_side_verifier(self):
        self.assertIn("releases/staging/$RELEASE_ID", self.workflow)
        self.assertIn("--package '$STAGING/package'", self.workflow)
        self.assertIn("--verifier '$STAGING/verify/verify_service_artifacts.py'", self.workflow)

    # ---- 破坏性操作边界 ----

    def test_no_destructive_docker_commands_anywhere(self):
        for text, name in (
            (self.workflow, "workflow"),
            (self.app_release, "app_release.sh"),
            (self.verify, "verify_deployment.sh"),
        ):
            code = code_only(text)
            for forbidden in (
                "compose down",
                "compose rm",
                "docker system prune",
                "docker volume rm",
                "docker rmi",
            ):
                self.assertNotIn(forbidden, code, "{} 出现破坏性命令：{}".format(name, forbidden))

    def test_middleware_compose_is_never_driven(self):
        self.assertNotRegex(
            code_only(self.app_release), r"compose[^\n]*-f[^\n]*docker-compose-mid\.yml"
        )
        self.assertNotIn("-f docker-compose-mid.yml", code_only(self.workflow))

    def test_app_service_scope_is_pinned(self):
        self.assertIn('SERVICES="gateway admin file portal"', self.app_release)
        self.assertIn("COMPOSE_PROJECT=\"${COMPOSE_PROJECT:-frameworkjava-prd}\"", self.app_release)
        self.assertIn('COMPOSE_FILE="${COMPOSE_FILE:-docker-compose-app.yml}"', self.app_release)

    def test_only_the_four_app_services_are_started(self):
        self.assertIn("up -d --build $targets", self.app_release)
        self.assertIn('for service in $SERVICES; do targets="$targets frameworkjava-$service"; done', self.app_release)
        self.assertNotIn("compose up -d\n", self.app_release)

    def test_nginx_is_reloaded_not_restarted(self):
        self.assertIn("nginx -t", self.app_release)
        self.assertIn("nginx -s reload", self.app_release)
        self.assertNotIn("restart frameworkjava-webprd", self.app_release)

    def test_verification_script_is_read_only(self):
        for forbidden in (" up ", " stop ", " restart ", " exec ", " down ", " rm "):
            self.assertNotIn(
                "docker {}".format(forbidden.strip()),
                self.verify,
                "只读验收脚本不应调用 docker {}".format(forbidden.strip()),
            )
        self.assertIn("compose logs", self.verify)
        self.assertIn("docker inspect", self.verify)
        self.assertIn("docker stats --no-stream", self.verify)

    def test_log_checks_use_the_real_compose_service_names(self):
        for text, name in ((self.verify, "verify_deployment.sh"), (self.app_release, "app_release.sh")):
            for line in code_only(text).splitlines():
                if "compose logs" in line:
                    self.assertNotIn(
                        '--no-color "$service"',
                        line,
                        "{} 不能把裸服务名传给 compose logs（真 compose 会报 no such service）：{}".format(
                            name, line.strip()
                        ),
                    )
                    self.assertTrue(
                        "frameworkjava-" in line or '"$target"' in line,
                        "{} 读日志时必须用 frameworkjava-<name>：{}".format(name, line.strip()),
                    )

    def test_failure_paths_are_defined(self):
        self.assertIn("首次发布失败且没有可回滚的历史版本", self.app_release)
        self.assertIn("stop_services", self.app_release)
        self.assertIn("按发布流程不自动回滚", self.app_release)
        self.assertIn("rollback --deploy-root", self.app_release)

    def test_rollback_targets_are_retained_releases(self):
        self.assertIn("已保留版本（可作为回滚目标）", self.app_release)
        self.assertIn("state/current", self.app_release)
        self.assertIn("$RELEASES_DIR/$RELEASE_ID", self.app_release)

    # ---- 仓库卫生 ----

    def test_scripts_are_executable(self):
        for path in (APP_RELEASE, VERIFY, HEALTH):
            self.assertTrue(os.access(str(path), os.X_OK), "{} 应有可执行位".format(path))

    def test_release_state_directories_are_ignored(self):
        result = subprocess.run(
            ["git", "check-ignore", "--quiet", str(SINGLE / "releases" / "state" / "current")],
            cwd=str(ROOT),
        )
        self.assertEqual(result.returncode, 0, "服务器发布状态目录不应进入版本控制")


if __name__ == "__main__":
    unittest.main(verbosity=2)
