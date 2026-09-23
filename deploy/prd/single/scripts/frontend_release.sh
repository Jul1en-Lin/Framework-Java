#!/usr/bin/env bash
#
# 前端发布编排（frameworkjava-prd：orbit-admin）
#
# 由 GitHub Actions 通过 SSH 调用（.github/workflows/release-web-prd.yml），
# 也可以在服务器上手动执行。
#
# 约束：
#   * 只更新宿主机 app/nginx/web/dist/ 内静态文件并 reload Nginx，不重启容器、不触碰中间件与后端应用；
#   * 使用 releases/lock 互斥锁，与后端发布共享排他保护，杜绝并发状态覆盖；
#   * 前端静态包经过 OSS 预签名中转并在服务器校验 SHA-256 后方可解包与替换；
#   * 替换必须保持 dist/ 目录 Inode 不变（使用 rsync 或目录内清空复制），避免 Docker 绑定挂载失效。
#
# 用法:
#   frontend_release.sh preflight      --deploy-root DIR
#   frontend_release.sh fetch-package  --deploy-root DIR --release-id ID --sha256 HASH
#   frontend_release.sh deploy         --deploy-root DIR --release-id ID [--staging DIR] [--dry-run] [--index-sha256 HASH]
#   frontend_release.sh status         --deploy-root DIR
#
# 退出码：0 成功；1 发布/校验失败；2 用法或环境错误。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OSS_PRESIGN="$SCRIPT_DIR/oss_presign.py"
VERIFY_SCRIPT="$SCRIPT_DIR/verify_frontend.sh"

COMPOSE_PROJECT="${COMPOSE_PROJECT:-frameworkjava-prd}"
WEB_PORT_EXPECTED="${WEB_PORT_EXPECTED:-8666}"
MIN_FREE_DISK_MB="${MIN_FREE_DISK_MB:-500}"

COMMAND=""
DEPLOY_ROOT=""
RELEASE_ID=""
PACKAGE_SHA256=""
INDEX_SHA256=""
STAGING_OPT=""
OPERATOR="${USER:-unknown}"
DRY_RUN=0

LOCK_HELD=0
LOCK_TOKEN=""
STAGING=""
FAILED=0

usage() {
  cat <<'EOF'
用法: frontend_release.sh <命令> --deploy-root DIR [选项]

命令:
  preflight      只读预检：目录、.env、Compose、webprd 状态、磁盘空间、并发锁
  fetch-package  从国内 OSS 取回前端发布包，核对 SHA-256 并解包校验
  deploy         发布：校验产物 → 原地原子替换 dist → reload Nginx → 只读验收
  status         查看当前前端生效版本与历史操作记录（只读）

选项:
  --deploy-root DIR    服务器 single 部署根目录（必填，内含 .env 与 app/）
  --release-id ID      发布标识（形如 web-1-1a2b3c4）
  --sha256 HASH        fetch-package：发布包 tar.gz 的 SHA-256
  --index-sha256 HASH  deploy 验收：期望的 index.html SHA-256
  --staging DIR        可选：本次发布的暂存目录
  --operator NAME      记录在历史中的操作人
  --dry-run            只打印计划，不做任何实际文件替换与容器操作
  -h, --help           显示帮助
EOF
}

die() { echo "[FAIL] $*" >&2; exit 2; }
info() { echo "$*"; }
warn() { echo "[WARN] $*" >&2; }
ok() { echo "[ OK ] $*"; }
fail() { echo "[FAIL] $*" >&2; FAILED=1; }

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

utc_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

assert_under_releases_dir() {
  case "$1" in
    *..*) die "内部保护：拒绝含 .. 的路径 $1" ;;
    "$RELEASES_DIR"/?*) ;;
    *) die "内部保护：拒绝操作 $1（不在 $RELEASES_DIR 内）" ;;
  esac
}

safe_rm_rf() {
  assert_under_releases_dir "$1"
  rm -rf "$1"
}

set_staging() {
  if [[ -n "$STAGING_OPT" ]]; then STAGING="$STAGING_OPT"; else STAGING="$1"; fi
  assert_under_releases_dir "$STAGING"
}

acquire_lock() {
  LOCK_DIR="$RELEASES_DIR/lock"
  LOCK_TOKEN="$$-$(utc_now)"
  mkdir -p "$RELEASES_DIR/state" "$RELEASES_DIR/staging"
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    {
      echo "token=$LOCK_TOKEN"
      echo "host=$(hostname)"
      echo "pid=$$"
      echo "release=$RELEASE_ID"
      echo "started_at=$(utc_now)"
      echo "type=frontend"
    } > "$LOCK_DIR/owner"
    LOCK_HELD=1
    ok "已获取发布排他锁 $LOCK_DIR"
    return 0
  fi
  warn "发布锁被占用："
  cat "$LOCK_DIR/owner" >&2 || true
  die "已有发布在执行或被中断。确认没有发布进程后，人工删除 $LOCK_DIR 再重试"
}

release_lock() {
  [[ "$LOCK_HELD" -eq 1 ]] || return 0
  if grep -q "^token=$LOCK_TOKEN$" "$LOCK_DIR/owner" 2>/dev/null; then
    safe_rm_rf "$LOCK_DIR"
  fi
  LOCK_HELD=0
}

cleanup_on_exit() {
  local exit_code=$?
  if [[ "$LOCK_HELD" -eq 1 ]]; then
    release_lock
  fi
  if [[ $exit_code -ne 0 && -n "$STAGING" && -d "$STAGING" && "$FAILED" -ne 0 ]]; then
    warn "发布失败，保留暂存现场: $STAGING"
  fi
  exit "$exit_code"
}
trap cleanup_on_exit EXIT

# ---------------------------------------------------------------- 命令分发

[[ $# -ge 1 ]] || { usage; exit 2; }
COMMAND="$1"; shift

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-root) DEPLOY_ROOT="${2:-}"; shift 2 ;;
    --release-id) RELEASE_ID="${2:-}"; shift 2 ;;
    --sha256) PACKAGE_SHA256="${2:-}"; shift 2 ;;
    --index-sha256) INDEX_SHA256="${2:-}"; shift 2 ;;
    --staging) STAGING_OPT="${2:-}"; shift 2 ;;
    --operator) OPERATOR="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（见 --help）" ;;
  esac
done

[[ -n "$DEPLOY_ROOT" ]] || die "缺少 --deploy-root"
[[ -d "$DEPLOY_ROOT" ]] || die "部署根目录不存在: $DEPLOY_ROOT"

APP_DIR="$DEPLOY_ROOT/app"
ENV_FILE="$DEPLOY_ROOT/.env"
RELEASES_DIR="$DEPLOY_ROOT/releases"
STATE_DIR="$RELEASES_DIR/state"

[[ -f "$ENV_FILE" ]] || die "缺少生产环境配置文件: $ENV_FILE"
[[ -d "$APP_DIR" ]] || die "缺少应用目录: $APP_DIR"

case "$COMMAND" in
  preflight)
    info "==> 前端发布只读预检: $DEPLOY_ROOT"
    
    # 检查 webprd 容器
    WEBPRD_ID="$(docker ps -a -q \
      --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
      --filter "label=com.docker.compose.service=frameworkjava-webprd" | head -1)"
    if [[ -z "$WEBPRD_ID" ]]; then
      WEBPRD_ID="$(docker ps -a -q --filter "name=${COMPOSE_PROJECT}.*webprd" | head -1)"
    fi
    [[ -n "$WEBPRD_ID" ]] || die "未找到 webprd 容器 (project: $COMPOSE_PROJECT)"
    STATUS="$(docker inspect -f '{{.State.Status}}' "$WEBPRD_ID" 2>/dev/null || true)"
    [[ "$STATUS" == "running" ]] || die "webprd 容器未运行 (当前状态: $STATUS)"
    ok "webprd 容器正常运行 (id: $WEBPRD_ID)"

    # 检查磁盘空间
    FREE_DISK_MB="$(df -m "$DEPLOY_ROOT" | awk 'NR==2{print $4}')"
    if [[ "$FREE_DISK_MB" -lt "$MIN_FREE_DISK_MB" ]]; then
      die "磁盘可用空间不足: 剩余 ${FREE_DISK_MB} MB，要求至少 ${MIN_FREE_DISK_MB} MB"
    fi
    ok "磁盘空间充裕 (可用 ${FREE_DISK_MB} MB)"

    # 检查锁
    if [[ -d "$RELEASES_DIR/lock" ]]; then
      die "发布锁目前被占用: $RELEASES_DIR/lock"
    fi
    ok "发布排他锁空闲"
    ok "前端发布预检全部通过"
    ;;

  fetch-package)
    [[ -n "$RELEASE_ID" ]] || die "fetch-package 必须指定 --release-id"
    [[ -n "$PACKAGE_SHA256" ]] || die "fetch-package 必须指定 --sha256"
    
    set_staging "$RELEASES_DIR/staging/$RELEASE_ID"
    mkdir -p "$STAGING"
    
    BUNDLE_FILE="$STAGING/frontend-dist.tar.gz"
    info "==> 从 OSS 取回前端发布包 (release_id: $RELEASE_ID)..."
    
    OSS_KEY="github-release-web/$RELEASE_ID/frontend-dist.tar.gz"
    DOWNLOAD_URL="$(python3 "$OSS_PRESIGN" --env-file "$ENV_FILE" --method GET --key "$OSS_KEY" --expires 3600)"
    
    HTTP_CODE="$(curl -s -f -w "%{http_code}" -o "$BUNDLE_FILE" "$DOWNLOAD_URL" || true)"
    if [[ "$HTTP_CODE" != "200" || ! -s "$BUNDLE_FILE" ]]; then
      fail "从 OSS 下载发布包失败 (HTTP $HTTP_CODE): $OSS_KEY"
      safe_rm_rf "$STAGING"
      exit 1
    fi
    ok "发布包下载完成: $BUNDLE_FILE ($(wc -c < "$BUNDLE_FILE") bytes)"

    # 核对 SHA-256
    ACTUAL_SHA="$(sha256_of "$BUNDLE_FILE")"
    if [[ "$ACTUAL_SHA" != "$PACKAGE_SHA256" ]]; then
      fail "发布包 SHA-256 校验失败: 实际 $ACTUAL_SHA != 期望 $PACKAGE_SHA256"
      safe_rm_rf "$STAGING"
      exit 1
    fi
    ok "发布包 SHA-256 校验一致 ($ACTUAL_SHA)"

    # 解包并验证关键结构
    mkdir -p "$STAGING/dist"
    tar -xzf "$BUNDLE_FILE" -C "$STAGING/dist"
    
    [[ -f "$STAGING/dist/index.html" && -s "$STAGING/dist/index.html" ]] || {
      fail "发布包缺少有效的 index.html"
      safe_rm_rf "$STAGING"
      exit 1
    }
    [[ -d "$STAGING/dist/assets" ]] || {
      fail "发布包缺少 assets/ 资源目录"
      safe_rm_rf "$STAGING"
      exit 1
    }
    ok "解包验证成功：包含 index.html 与 assets/ 目录"
    ;;

  deploy)
    [[ -n "$RELEASE_ID" ]] || die "deploy 必须指定 --release-id"
    set_staging "$RELEASES_DIR/staging/$RELEASE_ID"
    
    DIST_SRC="$STAGING/dist"
    [[ -d "$DIST_SRC" && -f "$DIST_SRC/index.html" ]] || die "暂存产物不存在或损坏: ${DIST_SRC}（请先执行 fetch-package）"

    acquire_lock
    
    TARGET_DIST="$APP_DIR/nginx/web/dist"
    info "==> 开始前端部署 (release_id: $RELEASE_ID)..."

    if [[ "$DRY_RUN" -eq 1 ]]; then
      ok "[DRY-RUN] 将执行: rsync -a --delete $DIST_SRC/ $TARGET_DIST/"
      ok "[DRY-RUN] 将执行: docker exec <webprd> nginx -t && nginx -s reload"
      ok "[DRY-RUN] 将执行: verify_frontend.sh --deploy-root $DEPLOY_ROOT"
      safe_rm_rf "$STAGING"
      release_lock
      ok "[DRY-RUN] 预演通过，未对生产做实质变更"
      exit 0
    fi

    # 保持目录 Inode 不变的原地安全同步
    mkdir -p "$TARGET_DIST"
    if command -v rsync >/dev/null 2>&1; then
      rsync -a --delete "$DIST_SRC/" "$TARGET_DIST/"
    else
      # 回落：清空内部子项再复制，保持 TARGET_DIST 目录本身 Inode
      find "$TARGET_DIST" -mindepth 1 -delete
      cp -a "$DIST_SRC/"* "$TARGET_DIST/"
    fi
    ok "静态文件已原地原子更新至 $TARGET_DIST"

    # 重载 Nginx
    WEBPRD_ID="$(docker ps -a -q \
      --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
      --filter "label=com.docker.compose.service=frameworkjava-webprd" | head -1)"
    if [[ -z "$WEBPRD_ID" ]]; then
      WEBPRD_ID="$(docker ps -a -q --filter "name=${COMPOSE_PROJECT}.*webprd" | head -1)"
    fi
    [[ -n "$WEBPRD_ID" ]] || die "未找到 webprd 容器"

    docker exec "$WEBPRD_ID" nginx -t
    docker exec "$WEBPRD_ID" nginx -s reload
    ok "Nginx 平滑重载成功"

    # 执行只读验收
    VERIFY_ARGS=(--deploy-root "$DEPLOY_ROOT")
    if [[ -n "$INDEX_SHA256" ]]; then
      VERIFY_ARGS+=(--expected-sha256 "$INDEX_SHA256")
    fi
    bash "$VERIFY_SCRIPT" "${VERIFY_ARGS[@]}"

    # 归档发布状态与历史
    mkdir -p "$STATE_DIR"
    {
      echo "release_id=$RELEASE_ID"
      echo "deployed_at=$(utc_now)"
      echo "operator=$OPERATOR"
      echo "index_sha256=$(sha256_of "$TARGET_DIST/index.html")"
    } > "$STATE_DIR/frontend_current"

    printf "%s\t%s\tsuccess\t%s\n" "$(utc_now)" "$RELEASE_ID" "$OPERATOR" >> "$STATE_DIR/frontend_history.log"
    ok "发布状态与审计日志已更新: $STATE_DIR/frontend_current"

    # 清理暂存目录
    safe_rm_rf "$STAGING"
    release_lock
    ok "前端发布部署成功 (release_id: $RELEASE_ID)"
    ;;

  status)
    info "==> 前端发布状态 ($DEPLOY_ROOT)"
    CURRENT_FILE="$STATE_DIR/frontend_current"
    HISTORY_FILE="$STATE_DIR/frontend_history.log"
    if [[ -f "$CURRENT_FILE" ]]; then
      echo "--- 当前生效版本 ---"
      cat "$CURRENT_FILE"
    else
      echo "当前无已记录的前端生效版本"
    fi
    if [[ -f "$HISTORY_FILE" ]]; then
      echo ""
      echo "--- 最近发布历史 (最后 5 条) ---"
      tail -5 "$HISTORY_FILE"
    fi
    ;;

  *)
    die "未知命令: ${COMMAND}（支持 preflight | fetch-package | deploy | status）"
    ;;
esac

exit 0
