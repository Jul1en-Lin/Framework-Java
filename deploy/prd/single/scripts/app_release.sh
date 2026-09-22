#!/usr/bin/env bash
#
# 服务器端发布编排：把已验证的四服务发布包落到 Docker 构建目录、重建四个应用容器、
# reload Nginx、只读验收，并保留可回滚的前一版本制品。
#
# 由 GitHub Actions 通过 SSH 调用（.github/workflows/release-prd.yml），也可以在
# 服务器上手动执行（例如排障、授权回滚、预演）。
#
# 硬性约束（改动此脚本时必须保持）：
#   * 只操作四个应用服务 frameworkjava-{gateway,admin,file,portal}；绝不 up/stop/down
#     中间件，绝不使用 docker-compose-mid.yml，绝不 compose down，绝不删数据卷。
#   * 每次都把改动限制在四个 service/<name>/ 目录与 releases/ 下本次发布的暂存目录；
#     不批量删除部署根目录。
#   * 每次发布在 releases/staging/<release_id>/ 内隔离暂存；制品在校验通过前不动应用。
#   * 用 releases/lock 互斥，防止并发发布互相覆盖状态。
#
# 用法:
#   app_release.sh preflight      --deploy-root DIR
#   app_release.sh fetch-package  --deploy-root DIR --release-id ID --parts N --sha256 HASH
#   app_release.sh deploy         --deploy-root DIR --package DIR --release-id ID [--dry-run]
#   app_release.sh rollback       --deploy-root DIR --to RELEASE_ID
#   app_release.sh status         --deploy-root DIR
#
# 退出码：0 成功；1 发布/校验失败；2 用法或环境错误。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERIFY_SCRIPT="$SCRIPT_DIR/verify_deployment.sh"

# 固定的应用服务范围：发布与回滚始终是这四个，不接受子集（避免半套版本）。
SERVICES="gateway admin file portal"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-frameworkjava-prd}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose-app.yml}"

# 生产环境固定事实（见 deploy/prd/single/README.md）：用于挡住「指错部署根目录」。
WEB_PORT_EXPECTED="${WEB_PORT_EXPECTED:-8666}"
NACOS_HOST_PORT_EXPECTED="${NACOS_HOST_PORT_EXPECTED:-8866}"
NACOS_IN_CONTAINER_ADDR="${NACOS_IN_CONTAINER_ADDR:-frameworkjava-nacos:8848}"

# 发布前资源余量（issue #3：预算 1.6–2.0 GiB，实测 available 1807 MiB）。
MIN_AVAIL_MEM_MB="${MIN_AVAIL_MEM_MB:-1400}"
MIN_FREE_DISK_MB="${MIN_FREE_DISK_MB:-3072}"
READY_TIMEOUT="${READY_TIMEOUT:-300}"
SAMPLE_SECONDS="${SAMPLE_SECONDS:-60}"
# 网关首次上线前 Nginx 会因为 upstream 解析不到网关而一直重启；等它自己恢复的上限。
NGINX_WAIT_SECONDS="${NGINX_WAIT_SECONDS:-120}"

COMMAND=""
DEPLOY_ROOT=""
PACKAGE=""
RELEASE_ID=""
ROLLBACK_TO=""
STAGING_OPT=""
PART_COUNT=""
PACKAGE_SHA256=""
VERIFIER=""
OPERATOR="${USER:-unknown}"
DRY_RUN=0

PHASE="preflight"
LOCK_HELD=0
LOCK_TOKEN=""
PREVIOUS_RELEASE=""
STAGING=""
LOG_DIR=""
FAILED=0

usage() {
  cat <<'EOF'
用法: app_release.sh <命令> --deploy-root DIR [选项]

命令:
  preflight      只读预检：目录、.env、Compose、服务目录、资源余量、并发锁
  fetch-package  从国内 OSS 取回分片发布包，核对 SHA-256 后解包并校验（国际直传不可用）
  deploy         发布：校验发布包 → 备份当前 JAR → 替换 → 重建四服务 → reload Nginx → 验收
  rollback       回滚到 releases/ 中已保留的 release_id（同一套校验与验收）
  status         查看当前生效版本、已保留版本与最近操作记录（只读）

选项:
  --deploy-root DIR   服务器 single 部署根目录（必填，内含 .env 与 app/）
  --package DIR       deploy 的发布包目录，必须在 <deploy-root>/releases/ 下
  --release-id ID     deploy 的发布标识（如 12-1a2b3c4）
  --staging DIR       可选：本次发布的暂存目录（默认取发布包所在目录）
  --verifier PATH     可选：scripts/verify_service_artifacts.py，对发布包再做结构校验
  --to RELEASE_ID     rollback 的目标版本
  --parts N           fetch-package：OSS 上的分片数量
  --sha256 HASH       fetch-package：发布包 tar 的 SHA-256
  --operator NAME     记录在历史与 manifest 里的操作人
  --dry-run           只预检并打印将执行的动作，不做任何变更
  -h, --help          显示帮助
EOF
}

die() { echo "[FAIL] $*" >&2; exit 2; }
info() { echo "$*"; }
warn() { echo "[WARN] $*" >&2; }
ok() { echo "[ OK ] $*"; }
fail() { echo "[FAIL] $*" >&2; FAILED=1; }

# ---------------------------------------------------------------- 基础工具

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

artifact_of() {
  case "$1" in
    gateway) echo "lien-gateway" ;;
    admin) echo "lien-admin-service" ;;
    file) echo "lien-file-service" ;;
    portal) echo "lien-portal-service" ;;
    *) return 1 ;;
  esac
}

utc_now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

# 只允许操作 releases/ 下的路径，避免任何变量拼错或路径穿越导致删错目录。
assert_under_releases_dir() {
  case "$1" in
    *..*) die "内部保护：拒绝含 .. 的路径 $1" ;;
    "$RELEASES_DIR"/?*) ;;
    *) die "内部保护：拒绝操作 $1（不在 $RELEASES_DIR 内）" ;;
  esac
  # 父目录存在时用物理路径再确认一次，防符号链接逃逸（TMPDIR 等路径本身可能是符号链接）。
  local parent real_releases
  parent="$(dirname "$1")"
  [[ -d "$parent" ]] || return 0
  parent="$(cd -P "$parent" && pwd)"
  real_releases="$(cd -P "$RELEASES_DIR" 2>/dev/null && pwd || printf '%s' "$RELEASES_DIR")"
  case "$parent" in
    "$real_releases"|"$real_releases"/*) ;;
    *) die "内部保护：拒绝操作 $1（物理路径 $parent 不在 $real_releases 内）" ;;
  esac
}

# releases/<id> 是否是一次成功发布（失败发布的残留不应被当作回滚目标推荐）。
release_result() {
  local manifest="$1/manifest.txt"
  if [[ -f "$manifest" ]] && grep -q '^result=success$' "$manifest"; then
    echo "success"
  else
    echo "unsuccessful"
  fi
}

safe_rm_rf() {
  assert_under_releases_dir "$1"
  rm -rf "$1"
}

# 本次发布的暂存目录必须落在 <deploy-root>/releases/staging/ 内，与其他发布隔离。
set_staging() {
  if [[ -n "$STAGING_OPT" ]]; then STAGING="$STAGING_OPT"; else STAGING="$1"; fi
  assert_under_releases_dir "$STAGING"
  case "$STAGING" in
    "$RELEASES_DIR"/staging/?*) ;;
    *) die "暂存目录必须在 $RELEASES_DIR/staging/ 下：$STAGING" ;;
  esac
}

compose() { ( cd "$APP_DIR" && docker compose --env-file ../.env -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" "$@" ); }

container_of() {
  docker ps -a -q \
    --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
    --filter "label=com.docker.compose.service=frameworkjava-$1" | head -1
}

mem_available_mb() { free -m | awk '/^Mem:/{print (($7 + 0) > 0) ? $7 : $4}'; }
swap_used_mb() { free -m | awk '/^Swap:/{print $3 + 0}'; }

env_get() {
  local key="$1" value
  value="$(sed -n "s/^${key}=//p" "$ENV_FILE" | head -1)"
  case "$value" in
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
  esac
  printf '%s' "$value"
}

check_jars_in_app() {
  local found=0
  local service artifact dir entries jars
  for service in $SERVICES; do
    artifact="$(artifact_of "$service")"
    dir="$APP_DIR/service/$service"
    if [[ ! -d "$dir" ]]; then
      fail "缺少构建目录 $dir"
      found=1
      continue
    fi
    [[ -f "$dir/Dockerfile" ]] || { fail "$dir 缺少 Dockerfile"; found=1; }
    if [[ -e "$dir/$artifact.jar.original" ]]; then
      fail "$dir 存在 .jar.original 残留，会让构建目录出现多份制品"
      found=1
    fi
    jars="$(find "$dir" -maxdepth 1 -type f -name '*.jar' | LC_ALL=C sort)"
    entries="$(echo "$jars" | grep -c . || true)"
    if [[ "$entries" -gt 1 ]]; then
      fail "$dir 下有 ${entries} 份 JAR，无法确定构建输入：$(echo "$jars" | tr '\n' ' ')"
      found=1
    fi
  done
  return "$found"
}

check_compose_static() {
  local file="$APP_DIR/$COMPOSE_FILE" text count
  text="$(cat "$file")"
  local found=0 service
  for service in $SERVICES; do
    if ! echo "$text" | grep -q "^  frameworkjava-$service:"; then
      fail "$COMPOSE_FILE 里没有 frameworkjava-$service 服务"
      found=1
    fi
  done
  for var in NACOS_USERNAME NACOS_PASSWORD; do
    count="$(echo "$text" | grep -c "${var}: \${${var}:?${var} is required}" || true)"
    if [[ "$count" -ne 4 ]]; then
      fail "$COMPOSE_FILE 里 ${var} 的必填注入出现 ${count} 次，期望 4 次"
      found=1
    fi
  done
  count="$(echo "$text" | grep -c "NACOS_ADDR: $NACOS_IN_CONTAINER_ADDR" || true)"
  if [[ "$count" -ne 4 ]]; then
    fail "$COMPOSE_FILE 里 NACOS_ADDR: ${NACOS_IN_CONTAINER_ADDR} 出现 ${count} 次，期望 4 次（容器内必须用服务名与 8848）"
    found=1
  fi
  return "$found"
}

check_prod_markers() {
  local mid="$APP_DIR/docker-compose-mid.yml" web_port found=0
  web_port="$(env_get WEB_PORT)"
  if [[ "$web_port" != "$WEB_PORT_EXPECTED" ]]; then
    fail ".env 的 WEB_PORT=${web_port:-<缺失>}，期望 ${WEB_PORT_EXPECTED}（疑似指错部署根目录）"
    found=1
  fi
  if [[ ! -f "$mid" ]]; then
    fail "缺少中间件 Compose ${mid}，无法确认这是生产部署根目录"
    found=1
  elif ! grep -q "\"${NACOS_HOST_PORT_EXPECTED}:8848\"" "$mid"; then
    fail "$mid 未把 Nacos 映射到宿主 ${NACOS_HOST_PORT_EXPECTED}（生产标识；疑似指错部署根目录）"
    found=1
  fi
  if [[ "$(env_get NACOS_USERNAME)" == "" || "$(env_get NACOS_PASSWORD)" == "" ]]; then
    fail ".env 缺少 NACOS_USERNAME / NACOS_PASSWORD，四个应用无法登录生产 Nacos"
    found=1
  fi
  return "$found"
}

check_resources() {
  local avail disk found=0
  avail="$(mem_available_mb)"
  disk="$(df -Pk "$DEPLOY_ROOT" | awk 'NR==2{print int($4 / 1024)}')"
  info "资源余量：available ${avail} MiB（下限 ${MIN_AVAIL_MEM_MB}），磁盘可用 ${disk} MiB（下限 ${MIN_FREE_DISK_MB}），swap 已用 $(swap_used_mb) MiB"
  if [[ "$avail" -lt "$MIN_AVAIL_MEM_MB" ]]; then
    fail "available=${avail} MiB 低于发布所需的 ${MIN_AVAIL_MEM_MB} MiB（build + 四个 JVM 启动峰值）"
    found=1
  fi
  if [[ "$disk" -lt "$MIN_FREE_DISK_MB" ]]; then
    fail "磁盘可用 ${disk} MiB 低于 ${MIN_FREE_DISK_MB} MiB（需要空间放发布包与重建镜像）"
    found=1
  fi
  if ! docker image inspect eclipse-temurin:17-jdk >/dev/null 2>&1; then
    warn "本地没有 eclipse-temurin:17-jdk 镜像，重建时会从网络拉取（占用额外磁盘与时间）"
  fi
  return "$found"
}

# ---------------------------------------------------------------- 预检

preflight_environment() {
  [[ -n "$DEPLOY_ROOT" ]] || die "缺少 --deploy-root"
  [[ "$DEPLOY_ROOT" = /* ]] || die "--deploy-root 必须是绝对路径：$DEPLOY_ROOT"
  [[ -d "$DEPLOY_ROOT" ]] || die "部署根目录不存在：$DEPLOY_ROOT"
  APP_DIR="$DEPLOY_ROOT/app"
  ENV_FILE="$DEPLOY_ROOT/.env"
  RELEASES_DIR="$DEPLOY_ROOT/releases"

  command -v docker >/dev/null 2>&1 || die "服务器上没有 docker 命令"
  command -v python3 >/dev/null 2>&1 || die "服务器上没有 python3 命令"
  docker info --format '{{.ServerVersion}}' >/dev/null 2>&1 \
    || die "当前用户无法访问 docker daemon（检查是否在 docker 组）"
  [[ -d "$APP_DIR" ]] || die "缺少应用目录 $APP_DIR"
  [[ -f "$APP_DIR/$COMPOSE_FILE" ]] || die "缺少 Compose 文件 $APP_DIR/$COMPOSE_FILE"
  [[ -f "$ENV_FILE" ]] || die "缺少 env 文件 $ENV_FILE"
  [[ -x "$VERIFY_SCRIPT" || -f "$VERIFY_SCRIPT" ]] || die "缺少验收脚本 $VERIFY_SCRIPT"

  local found=0
  check_prod_markers || found=1
  check_compose_static || found=1
  check_jars_in_app || found=1
  check_resources || found=1
  [[ "$found" -eq 0 ]] || die "预检未通过，未做任何改动"

  if ! compose config --quiet; then
    die "docker compose config 校验失败（不输出解析结果，避免泄露凭据）"
  fi
  ok "预检通过：${DEPLOY_ROOT}（Compose 校验通过，构建目录未出现多份 JAR）"
}

preflight_package() {
  local package="$1" found=0 service artifact
  assert_under_releases_dir "$package"
  [[ -d "$package" ]] || die "发布包目录不存在：$package"
  [[ -f "$package/build-info.txt" ]] || die "发布包缺少 build-info.txt"

  for service in $SERVICES; do
    artifact="$(artifact_of "$service")"
    if [[ ! -f "$package/$service/$artifact.jar" ]]; then
      fail "发布包缺少 $service/$artifact.jar"
      found=1
      continue
    fi
    if [[ ! -f "$package/$service/$artifact.jar.sha256" ]]; then
      fail "发布包缺少 $service/$artifact.jar.sha256"
      found=1
      continue
    fi
  done
  [[ "$found" -eq 0 ]] || die "发布包不完整，未做任何改动"

  if [[ -n "$VERIFIER" ]]; then
    [[ -f "$VERIFIER" ]] || die "校验脚本不存在：$VERIFIER"
    python3 "$VERIFIER" --release-dir "$package" || die "制品结构校验未通过（见上面的失败项）"
  else
    warn "未提供 --verifier，跳过制品结构校验，只核对 SHA-256"
  fi

  # 逐份复核校验和：即使结构校验通过，也要防止传输过程损坏。
  for service in $SERVICES; do
    artifact="$(artifact_of "$service")"
    local expected actual
    expected="$(awk 'NR==1{print $1}' "$package/$service/$artifact.jar.sha256")"
    actual="$(sha256_of "$package/$service/$artifact.jar")"
    if [[ "$expected" != "$actual" ]]; then
      fail "$service 校验和不一致：记录 ${expected}，实际 ${actual}"
      found=1
    fi
  done
  [[ "$found" -eq 0 ]] || die "发布包制品校验未通过，未做任何改动"
  ok "发布包校验通过：四份制品完整且 SHA-256 一致"
}

# ---------------------------------------------------------------- 互斥锁

acquire_lock() {
  LOCK_DIR="$RELEASES_DIR/lock"
  LOCK_TOKEN="$$-$(utc_now)"
  mkdir -p "$RELEASES_DIR/state" "$RELEASES_DIR/staging"
  if mkdir "$LOCK_DIR" 2>/dev/null; then
    {
      echo "token=$LOCK_TOKEN"
      echo "host=$(hostname)"
      echo "pid=$$"
      echo "release=$RELEASE_ID$ROLLBACK_TO"
      echo "started_at=$(utc_now)"
    } > "$LOCK_DIR/owner"
    LOCK_HELD=1
    ok "已获取发布锁 $LOCK_DIR"
    return 0
  fi
  warn "锁被占用："
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

# ---------------------------------------------------------------- 应用变更

apply_package() {
  local package="$1" backup_dir="$2" service artifact src dir jars count before after
  mkdir -p "$backup_dir"
  for service in $SERVICES; do
    artifact="$(artifact_of "$service")"
    src="$package/$service/$artifact.jar"
    dir="$APP_DIR/service/$service"
    mkdir -p "$backup_dir/$service"

    jars="$(find "$dir" -maxdepth 1 -type f -name '*.jar' | LC_ALL=C sort)"
    count="$(echo "$jars" | grep -c . || true)"
    if [[ "$count" -gt 1 ]]; then
      fail "$dir 下有 ${count} 份 JAR，拒绝替换：$(echo "$jars" | tr '\n' ' ')"
      return 1
    fi
    before="none"
    if [[ "$count" -eq 1 ]]; then
      before="$(sha256_of "$jars")"
      # 备份当前制品（不同名的旧制品一并移走，保证构建目录只剩一份 JAR）。
      mv "$jars" "$backup_dir/$service/$(basename "$jars")"
    fi
    install -m 644 "$src" "$dir/$artifact.jar"

    jars="$(find "$dir" -maxdepth 1 -type f -name '*.jar' | LC_ALL=C sort)"
    if [[ "$jars" != "$dir/$artifact.jar" ]]; then
      fail "$dir 替换后不是恰好一份 $artifact.jar：$(echo "$jars" | tr '\n' ' ')"
      return 1
    fi
    after="$(sha256_of "$dir/$artifact.jar")"
    printf 'service=%s artifact=%s before=%s after=%s\n' \
      "$service" "$artifact.jar" "$before" "$after" >> "$MANIFEST"
    ok "$service: $artifact.jar 已就位（before=$before after=${after:0:12}…）"
  done
}

start_services() {
  local targets="" service
  for service in $SERVICES; do targets="$targets frameworkjava-$service"; done
  PHASE="starting"
  info "重建并启动这四个应用服务（中间件、数据卷、Nginx 容器都不动）："
  info "   docker compose -p $COMPOSE_PROJECT -f $COMPOSE_FILE up -d --build$targets"
  # shellcheck disable=SC2086  # 需要按空格拆分成四个服务名
  compose up -d --build $targets
  PHASE="started"
}

stop_services() {
  local targets="" service
  for service in $SERVICES; do targets="$targets frameworkjava-$service"; done
  # shellcheck disable=SC2086
  compose stop $targets
}

reload_nginx() {
  local id waited=0
  # 包含未运行/重启中的容器：网关首次上线前 webprd 会因为 upstream 解析不到
  # frameworkjava-gateway 而一直重启（现网实测重启过数千次），这时它还不在 docker ps 里。
  id="$(docker ps -a -q \
    --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
    --filter "label=com.docker.compose.service=frameworkjava-webprd" | head -1)"
  if [[ -z "$id" ]]; then
    warn "未找到 frameworkjava-webprd 容器；跳过 Nginx 处理（若经 Nginx 的验收失败，请人工确认）"
    return 0
  fi

  if ! nginx_container_running "$id"; then
    info "frameworkjava-webprd 未运行：首次上线前 upstream 解析不到网关会一直重启，属预期；等待其自动恢复…"
    while ! nginx_container_running "$id"; do
      if [[ "$waited" -ge "$NGINX_WAIT_SECONDS" ]]; then
        warn "等待 ${NGINX_WAIT_SECONDS}s 后仍未运行，单独启动该容器（不动中间件、不动其它容器）"
        docker start "$id" >/dev/null 2>&1 || true
        sleep 5
        if ! nginx_container_running "$id"; then
          fail "frameworkjava-webprd 仍无法启动，最后 20 行日志："
          docker logs --tail=20 "$id" >&2 2>&1 || true
          return 1
        fi
        break
      fi
      sleep 5
      waited=$((waited + 5))
    done
    ok "frameworkjava-webprd 已运行"
  fi

  # 网关容器重建后 IP 会变，而 Nginx 只在加载配置时解析一次 upstream。
  docker exec "$id" nginx -t
  docker exec "$id" nginx -s reload
  ok "Nginx 配置校验通过并已 reload（只处理该容器，不重启中间件）"
}

nginx_container_running() {
  [[ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null || echo false)" == "true" ]]
}

run_verification() {
  PHASE="verifying"
  if ! bash "$VERIFY_SCRIPT" --deploy-root "$DEPLOY_ROOT" \
      --ready-timeout "$READY_TIMEOUT" --sample-seconds "$SAMPLE_SECONDS"; then
    fail "发布后验收未通过"
    return 1
  fi
}

record_manifest() {
  local package="$1" action="$2" result="$3" git_rev="未知"
  if [[ -f "$package/build-info.txt" ]]; then
    git_rev="$(sed -n 's/^git_rev=//p' "$package/build-info.txt" | head -1)"
    git_rev="${git_rev:-未知}"
  fi
  {
    echo "release_id=$RELEASE_ID"
    echo "action=$action"
    echo "result=$result"
    echo "operator=$OPERATOR"
    echo "git_rev=$git_rev"
    echo "package=$package"
    echo "previous_release=${PREVIOUS_RELEASE:-none}"
    echo "started_at=$STARTED_AT"
    echo "finished_at=$(utc_now)"
  } >> "$MANIFEST"
}

write_history() {
  local result="$1" action="$2"
  if [[ "$DRY_RUN" -eq 1 || -z "${RELEASES_DIR:-}" ]]; then
    return 0
  fi
  mkdir -p "$RELEASES_DIR/state"
  printf 'ts=%s action=%s release=%s previous=%s result=%s phase=%s operator=%s\n' \
    "$(utc_now)" "$action" "${RELEASE_ID}${ROLLBACK_TO}" "${PREVIOUS_RELEASE:-none}" \
    "$result" "$PHASE" "$OPERATOR" >> "$RELEASES_DIR/state/history.log"
}

capture_logs() {
  [[ -n "$LOG_DIR" ]] || return 0
  mkdir -p "$LOG_DIR" 2>/dev/null || return 0
  local service
  for service in $SERVICES; do
    compose logs --tail=400 --no-color "frameworkjava-$service" > "$LOG_DIR/$service.log" 2>&1 || true
  done
  warn "四服务日志已保存到 ${LOG_DIR}（仅供排障，含运行时输出，勿外传）"
}

handle_failure() {
  local status="$1"
  case "$PHASE" in
    preflight|staging)
      warn "失败发生在改动之前，应用制品与已保留版本都未被改动"
      write_history failed "$ACTION" || true
      return 0
      ;;
  esac
  capture_logs
  if [[ "$PHASE" == "applying" ]]; then
    warn "失败发生在替换 JAR 的过程中：容器尚未重建，仍在运行上一版；构建目录可能处于半更新状态"
    warn "替换前的制品备份在 $STAGING/previous/（目录为空说明还没开始替换）"
    if [[ -n "$PREVIOUS_RELEASE" ]]; then
      warn "用上一版重新覆盖构建目录：bash $SCRIPT_DIR/app_release.sh rollback --deploy-root $DEPLOY_ROOT --to $PREVIOUS_RELEASE"
    fi
    write_history failed "$ACTION" || true
    return 0
  fi
  if [[ -n "$PREVIOUS_RELEASE" ]]; then
    warn "失败发生在四个应用已重建之后；按发布流程不自动回滚（回滚同样要重建镜像），由授权人执行："
    warn "   bash $SCRIPT_DIR/app_release.sh rollback --deploy-root $DEPLOY_ROOT --to $PREVIOUS_RELEASE"
  else
    warn "首次发布失败且没有可回滚的历史版本；停掉本次启动的四个应用服务，保留现场与日志："
    stop_services || warn "停止应用服务失败，请人工确认容器状态"
    warn "确认服务本身没问题时可重新启动：docker compose -p $COMPOSE_PROJECT -f $COMPOSE_FILE up -d"
  fi
  write_history failed "$ACTION" || true
}

# 退出时统一收尾：失败时给出处置，成功时释放锁。
on_exit() {
  local status=$?
  trap - EXIT
  if [[ "$status" -ne 0 ]]; then
    handle_failure "$status" || true
  fi
  release_lock || true
  exit "$status"
}

# ---------------------------------------------------------------- 命令实现

cmd_preflight() {
  preflight_environment
  info "预检完成，未做任何改动"
}

# ---- OSS 取回：这台服务器的国际线路只有十几 KB/s，发布包改走国内 OSS 中转 ----
oss_presign_url() {
  local method="$1" key="$2" expires="${3:-3600}"
  python3 "$SCRIPT_DIR/oss_presign.py" --env-file "$ENV_FILE" \
    --method "$method" --key "$key" --expires "$expires"
}

# 下载一个对象到文件（对象不存在或校验失败时非零退出）。
oss_get() {
  local key="$1" out="$2" url
  url="$(oss_presign_url GET "$key")" || return 1
  curl -sS --fail --retry 3 --retry-delay 2 --max-time 300 -o "$out" "$url"
}

check_oss_config() {
  command -v curl >/dev/null 2>&1 || die "服务器上没有 curl，无法从 OSS 取回发布包"
  [[ -f "$SCRIPT_DIR/oss_presign.py" ]] || die "缺少 $SCRIPT_DIR/oss_presign.py"
  local key
  for key in OSS_BUCKET OSS_ENDPOINT OSS_ACCESS_KEY_ID OSS_ACCESS_KEY_SECRET; do
    [[ -n "$(env_get "$key")" ]] || die ".env 缺少 ${key}，无法从 OSS 取回发布包（值不打印）"
  done
}

cmd_fetch_package() {
  [[ -n "$RELEASE_ID" ]] || die "fetch-package 需要 --release-id"
  [[ -n "$PART_COUNT" ]] || die "fetch-package 需要 --parts"
  [[ -n "$PACKAGE_SHA256" ]] || die "fetch-package 需要 --sha256"
  [[ "$RELEASE_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] || die "release-id 非法：${RELEASE_ID}"
  [[ "$PART_COUNT" =~ ^[0-9]{1,3}$ ]] || die "--parts 必须是数字（实际：${PART_COUNT}）"
  [[ "$PART_COUNT" -ge 1 ]] || die "--parts 至少为 1"
  [[ "$PACKAGE_SHA256" =~ ^[0-9a-f]{64}$ ]] || die "--sha256 必须是 64 位十六进制"

  preflight_environment
  check_oss_config
  PHASE="staging"

  STAGING="$RELEASES_DIR/staging/$RELEASE_ID"
  set_staging "$STAGING"
  mkdir -p "$STAGING"
  local prefix="github-release/$RELEASE_ID"
  local tar="$STAGING/package.tar.gz" dir="$STAGING/package" i part actual
  safe_rm_rf "$dir"
  rm -f "$tar"
  mkdir -p "$dir"

  info "从 OSS 取回发布包（$PART_COUNT 个分片）…"
  for i in $(seq 0 $((PART_COUNT - 1))); do
    part="$(printf 'part-%02d' "$i")"
    oss_get "$prefix/package.tar.gz.$part" "$STAGING/$part" \
      || die "从 OSS 下载分片 $part 失败"
  done

  : > "$tar"
  for i in $(seq 0 $((PART_COUNT - 1))); do
    part="$(printf 'part-%02d' "$i")"
    cat "$STAGING/$part" >> "$tar"
    rm -f "$STAGING/$part"
  done
  actual="$(sha256_of "$tar")"
  if [[ "$actual" != "$PACKAGE_SHA256" ]]; then
    fail "发布包校验和不一致：期望 ${PACKAGE_SHA256}，实际 ${actual}"
    die "发布包在传输中损坏或上传不完整，未做任何改动"
  fi
  ok "发布包已取回并通过 SHA-256 校验（${PACKAGE_SHA256:0:12}…）"

  tar -xzf "$tar" -C "$dir" || die "解压发布包失败"
  rm -f "$tar"
  preflight_package "$dir"
  info "发布包就绪：${dir}（deploy 时用 --package '$dir'）"
}

cmd_deploy() {
  [[ -n "$PACKAGE" ]] || die "deploy 需要 --package"
  [[ -n "$RELEASE_ID" ]] || die "deploy 需要 --release-id"
  [[ "$RELEASE_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
    || die "release-id 只允许字母数字与 . _ -（实际：${RELEASE_ID}）"

  preflight_environment
  PHASE="staging"
  preflight_package "$PACKAGE"

  STAGING="$(cd "$PACKAGE" && cd .. && pwd)"
  set_staging "$STAGING"
  LOG_DIR="$STAGING/logs"
  PREVIOUS_RELEASE=""
  if [[ -f "$RELEASES_DIR/state/current" ]]; then
    PREVIOUS_RELEASE="$(head -1 "$RELEASES_DIR/state/current")"
  fi

  info "本次发布：release=$RELEASE_ID 上一版=${PREVIOUS_RELEASE:-无（首次发布）}"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    info "== dry-run：以下动作不会真的执行 =="
    info "1) 保留本次发布包到 $RELEASES_DIR/$RELEASE_ID"
    info "2) 备份四个 service/<name>/*.jar 到 $STAGING/previous，再替换为新制品"
    info "3) compose up -d --build frameworkjava-{gateway,admin,file,portal}"
    info "4) docker exec <webprd> nginx -t && nginx -s reload"
    info "5) 只读验收（容器、日志、Nacos 注册、实例端口、经 Nginx 的后端 API）"
    info "dry-run 结束，未做任何改动"
    return 0
  fi

  acquire_lock

  if [[ -d "$RELEASES_DIR/$RELEASE_ID" ]]; then
    die "已存在同名保留版本 $RELEASES_DIR/${RELEASE_ID}，换一个 release-id（不覆盖已保留版本）"
  fi
  mkdir -p "$RELEASES_DIR/$RELEASE_ID"
  assert_under_releases_dir "$RELEASES_DIR/$RELEASE_ID"
  cp -R "$PACKAGE/." "$RELEASES_DIR/$RELEASE_ID/"
  MANIFEST="$RELEASES_DIR/$RELEASE_ID/manifest.txt"
  : > "$MANIFEST"

  PHASE="applying"
  apply_package "$PACKAGE" "$STAGING/previous"

  start_services
  reload_nginx
  run_verification

  PHASE="done"
  printf '%s\n' "$RELEASE_ID" > "$RELEASES_DIR/state/current"
  record_manifest "$PACKAGE" "deploy" "success"
  write_history success "deploy"
  safe_rm_rf "$STAGING"
  ok "发布完成：release=${RELEASE_ID}（回滚目标：${PREVIOUS_RELEASE:-无}）"
}

cmd_rollback() {
  [[ -n "$ROLLBACK_TO" ]] || die "rollback 需要 --to"
  [[ "$ROLLBACK_TO" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
    || die "release-id 只允许字母数字与 . _ -（实际：${ROLLBACK_TO}）"

  preflight_environment
  local source="$RELEASES_DIR/$ROLLBACK_TO"
  [[ -d "$source" ]] || die "没有保留的发布版本 ${ROLLBACK_TO}（用 status 查看可用版本）"
  if [[ "$(release_result "$source")" != "success" ]]; then
    warn "${ROLLBACK_TO} 的 manifest 不是成功发布（可能是失败发布的残留），确认这是有意的回滚目标"
  fi
  PHASE="staging"
  preflight_package "$source"

  RELEASE_ID="$ROLLBACK_TO"
  PREVIOUS_RELEASE=""
  if [[ -f "$RELEASES_DIR/state/current" ]]; then
    PREVIOUS_RELEASE="$(head -1 "$RELEASES_DIR/state/current")"
  fi
  STAGING="$RELEASES_DIR/staging/rollback-$ROLLBACK_TO-$(date -u +%Y%m%dT%H%M%SZ)"
  set_staging "$STAGING"
  LOG_DIR="$STAGING/logs"

  info "本次回滚：目标=$ROLLBACK_TO 当前=${PREVIOUS_RELEASE:-无}"

  acquire_lock
  mkdir -p "$STAGING"
  MANIFEST="$STAGING/manifest.txt"
  : > "$MANIFEST"

  PHASE="applying"
  apply_package "$source" "$STAGING/previous"
  start_services
  reload_nginx
  run_verification

  PHASE="done"
  printf '%s\n' "$ROLLBACK_TO" > "$RELEASES_DIR/state/current"
  record_manifest "$source" "rollback" "success"
  cp "$MANIFEST" "$source/rollback-$(date -u +%Y%m%dT%H%M%SZ).txt"
  write_history success "rollback"
  safe_rm_rf "$STAGING"
  ok "回滚完成：release=$ROLLBACK_TO"
}

cmd_status() {
  [[ -n "$DEPLOY_ROOT" ]] || die "缺少 --deploy-root"
  DEPLOY_ROOT="$(cd "$DEPLOY_ROOT" 2>/dev/null && pwd)" || die "部署根目录不存在：$DEPLOY_ROOT"
  RELEASES_DIR="$DEPLOY_ROOT/releases"
  APP_DIR="$DEPLOY_ROOT/app"
  ENV_FILE="$DEPLOY_ROOT/.env"

  if [[ -f "$RELEASES_DIR/state/current" ]]; then
    info "当前生效版本: $(head -1 "$RELEASES_DIR/state/current")"
  else
    info "当前生效版本: 无（尚未通过本脚本发布）"
  fi
  info "已保留版本（可作为回滚目标）:"
  if [[ -d "$RELEASES_DIR" ]]; then
    local dir
    while IFS= read -r dir; do
      info "   $(basename "$dir")  result=$(release_result "$dir")"
    done < <(find "$RELEASES_DIR" -mindepth 1 -maxdepth 1 -type d ! -name state ! -name staging ! -name lock | LC_ALL=C sort)
  fi
  if [[ -f "$RELEASES_DIR/lock/owner" ]]; then
    info "发布锁: 已占用"; cat "$RELEASES_DIR/lock/owner" | sed 's/^/   /'
  else
    info "发布锁: 空闲"
  fi
  if [[ -f "$RELEASES_DIR/state/history.log" ]]; then
    info "最近操作记录:"
    tail -5 "$RELEASES_DIR/state/history.log" | sed 's/^/   /'
  fi
}

# ---------------------------------------------------------------- 入口

[[ $# -gt 0 ]] || { usage; exit 2; }
COMMAND="$1"
shift
case "$COMMAND" in
  preflight|fetch-package|deploy|rollback|status) ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; die "未知命令: $COMMAND" ;;
esac

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-root) DEPLOY_ROOT="${2:-}"; shift 2 ;;
    --package) PACKAGE="${2:-}"; shift 2 ;;
    --release-id) RELEASE_ID="${2:-}"; shift 2 ;;
    --to) ROLLBACK_TO="${2:-}"; shift 2 ;;
    --parts) PART_COUNT="${2:-}"; shift 2 ;;
    --sha256) PACKAGE_SHA256="${2:-}"; shift 2 ;;
    --staging) STAGING_OPT="${2:-}"; shift 2 ;;
    --verifier) VERIFIER="${2:-}"; shift 2 ;;
    --operator) OPERATOR="${2:-}"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; die "未知参数: $1" ;;
  esac
done

STARTED_AT="$(utc_now)"
ACTION="$COMMAND"
RELEASES_DIR=""
APP_DIR=""
ENV_FILE=""
MANIFEST=""

trap on_exit EXIT

case "$COMMAND" in
  preflight) cmd_preflight ;;
  fetch-package) cmd_fetch_package ;;
  deploy) cmd_deploy ;;
  rollback) cmd_rollback ;;
  status) cmd_status ;;
esac
