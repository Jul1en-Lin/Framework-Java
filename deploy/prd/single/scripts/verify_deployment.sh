#!/usr/bin/env bash
#
# 发布后只读验收：四个应用容器真实就绪、Nacos 注册、启动日志、经 Nginx 的后端 API。
#
# 只读约束（硬性）：本脚本只调用 `docker ps -a` / `docker inspect` / `docker compose logs`
# 与只读 HTTP 请求；不 up、不 stop、不 restart、不 exec、不 build、不 down，绝不改动
# 任何容器或文件。因此可以在任何时刻重复执行，也可以在发布之外独立复核。
#
# 「compose up 返回成功」不算通过：容器在跑、启动日志没有致命错误、Nacos 里注册健康、
# 注册的 ip:port 真的在监听、Nginx 转发到网关不是 502/503/504，才算通过。
#
# 用法:
#   verify_deployment.sh --deploy-root /home/ubuntu/framework_java/deploy/prd/single
#   verify_deployment.sh --deploy-root ... --sample-seconds 600   # 首次发布按 #3 阈值观察 10 分钟
#
# 退出码：0 全部通过；1 有检查项失败；2 用法或环境错误。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HEALTH_CHECK="$SCRIPT_DIR/check_service_health.py"

# 固定的应用服务范围（与 docker-compose-app.yml 一致），不随发布可选。
SERVICES="gateway admin file portal"
API_PREFIXES="admin file portal"

COMPOSE_PROJECT="${COMPOSE_PROJECT:-frameworkjava-prd}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose-app.yml}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-frameworkjava-prd}"
NACOS_HOST_PORT="${NACOS_HOST_PORT:-8866}"
WEB_PORT="${WEB_PORT:-}"

# issue #3 的验收阈值：四服务启动后 available ≥ 300 MiB，且 swap 不再明显增长。
MIN_AVAIL_MB="${MIN_AVAIL_MB:-300}"
MAX_SWAP_GROWTH_MB="${MAX_SWAP_GROWTH_MB:-128}"
MAX_SERVICE_RSS_MB="${MAX_SERVICE_RSS_MB:-500}"
SAMPLE_SECONDS="${SAMPLE_SECONDS:-60}"
READY_TIMEOUT="${READY_TIMEOUT:-300}"
READY_INTERVAL="${READY_INTERVAL:-10}"

DEPLOY_ROOT=""
FATAL=0
FAILED=0

usage() {
  cat <<'EOF'
用法: verify_deployment.sh --deploy-root DIR [选项]

  --deploy-root DIR        服务器 single 部署根目录（必填，内含 .env 与 app/）
  --sample-seconds N       资源观察窗口秒数（默认 60；首次发布建议 600）
  --ready-timeout N        等待四服务就绪的上限秒数（默认 300）
  --ready-interval N       两次校验之间的间隔秒数（默认 10）
  --min-avail-mb N         观察窗口结束时 available 下限 MiB（默认 300）
  --max-swap-growth-mb N   观察窗口内 swap 增长上限 MiB（默认 128）
  --max-service-rss-mb N   单个应用容器内存占用上限 MiB（默认 500，issue #3）
  -h, --help               显示帮助
EOF
}

die() { echo "[FAIL] $*" >&2; exit 2; }
info() { echo "$*"; }
ok() { echo "[ OK ] $*"; }
fail() { echo "[FAIL] $*" >&2; FAILED=1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-root) DEPLOY_ROOT="${2:-}"; shift 2 ;;
    --sample-seconds) SAMPLE_SECONDS="${2:-}"; shift 2 ;;
    --ready-timeout) READY_TIMEOUT="${2:-}"; shift 2 ;;
    --ready-interval) READY_INTERVAL="${2:-}"; shift 2 ;;
    --min-avail-mb) MIN_AVAIL_MB="${2:-}"; shift 2 ;;
    --max-swap-growth-mb) MAX_SWAP_GROWTH_MB="${2:-}"; shift 2 ;;
    --max-service-rss-mb) MAX_SERVICE_RSS_MB="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（见 --help）" ;;
  esac
done

[[ -n "$DEPLOY_ROOT" ]] || die "缺少 --deploy-root"
APP_DIR="$DEPLOY_ROOT/app"
ENV_FILE="$DEPLOY_ROOT/.env"

command -v docker >/dev/null 2>&1 || die "服务器上没有 docker 命令"
command -v python3 >/dev/null 2>&1 || die "服务器上没有 python3 命令"
[[ -d "$APP_DIR" ]] || die "缺少应用目录 $APP_DIR"
[[ -f "$APP_DIR/$COMPOSE_FILE" ]] || die "缺少 Compose 文件 $APP_DIR/$COMPOSE_FILE"
[[ -f "$ENV_FILE" ]] || die "缺少 env 文件 $ENV_FILE"

compose() { ( cd "$APP_DIR" && docker compose --env-file ../.env -p "$COMPOSE_PROJECT" -f "$COMPOSE_FILE" "$@" ); }

# .env 里的值可能带 $ / # / 空格，用单引号包裹；这里只取值、不 eval、不 source。
env_get() {
  local key="$1" value
  value="$(sed -n "s/^${key}=//p" "$ENV_FILE" | head -1)"
  case "$value" in
    \'*\') value="${value#\'}"; value="${value%\'}" ;;
    \"*\") value="${value#\"}"; value="${value%\"}" ;;
  esac
  printf '%s' "$value"
}

if [[ -z "$WEB_PORT" ]]; then
  WEB_PORT="$(env_get WEB_PORT)"
  WEB_PORT="${WEB_PORT:-8666}"
fi
NACOS_USERNAME_VALUE="${NACOS_USERNAME:-$(env_get NACOS_USERNAME)}"
NACOS_PASSWORD_VALUE="${NACOS_PASSWORD:-$(env_get NACOS_PASSWORD)}"

container_of() {
  docker ps -a -q \
    --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
    --filter "label=com.docker.compose.service=frameworkjava-$1" | head -1
}

# 输出 "running restart_count status"；容器不存在时为 "false 0 missing"。
inspect_state() {
  local id="$1"
  docker inspect -f '{{.State.Running}} {{.RestartCount}} {{.State.Status}}' "$id" 2>/dev/null \
    || echo "false 0 missing"
}

check_containers() {
  local service id state running restarts status
  for service in $SERVICES; do
    id="$(container_of "$service")"
    if [[ -z "$id" ]]; then
      fail "frameworkjava-$service 容器不存在"
      continue
    fi
    state="$(inspect_state "$id")"
    running="$(echo "$state" | awk '{print $1}')"
    restarts="$(echo "$state" | awk '{print $2}')"
    status="$(echo "$state" | awk '{print $3}')"
    if [[ "$running" != "true" ]]; then
      fail "frameworkjava-$service 未运行（status=${status}，restarts=${restarts}）"
    elif [[ "$restarts" != "0" ]]; then
      fail "frameworkjava-$service 已重启 ${restarts} 次（不是稳定就绪，查日志）"
    else
      ok "frameworkjava-$service 运行中（restarts=0）"
    fi
  done
  [[ "$FAILED" -eq 0 ]]
}

check_logs() {
  local service logs target
  for service in $SERVICES; do
    target="frameworkjava-$service"
    if ! logs="$(compose logs --tail=400 --no-color "$target" 2>&1)"; then
      fail "$target 日志读取失败"
      continue
    fi
    if echo "$logs" | grep -Eq "APPLICATION FAILED TO START|Error starting ApplicationContext|OutOfMemoryError|BeanCreationException"; then
      FATAL=1
      fail "$target 启动日志出现致命错误（最后 40 行）："
      echo "$logs" | tail -40 >&2
      continue
    fi
    if ! echo "$logs" | grep -Eq "Started .* in [0-9.]+ seconds"; then
      fail "$target 日志里还没有启动完成记录（Started ... in ... seconds）"
      continue
    fi
    ok "$target 启动完成"
  done
  [[ "$FAILED" -eq 0 ]]
}

check_health() {
  if [[ -z "$NACOS_USERNAME_VALUE" || -z "$NACOS_PASSWORD_VALUE" ]]; then
    fail "服务器 .env 缺少 NACOS_USERNAME / NACOS_PASSWORD，无法校验 Nacos 注册"
    return 1
  fi
  local args=(
    --nacos-url "http://127.0.0.1:${NACOS_HOST_PORT}/nacos"
    --namespace "$NACOS_NAMESPACE"
    --web-port "$WEB_PORT"
  )
  local service prefix
  for service in $SERVICES; do args+=(--service "lien-$service"); done
  for prefix in $API_PREFIXES; do args+=(--api-prefix "$prefix"); done
  if NACOS_USERNAME="$NACOS_USERNAME_VALUE" NACOS_PASSWORD="$NACOS_PASSWORD_VALUE" \
      python3 "$HEALTH_CHECK" "${args[@]}"; then
    return 0
  fi
  fail "服务健康校验未通过（Nacos 注册 / 实例端口 / 经 Nginx 的后端 API）"
  return 1
}

mem_available_mb() { free -m | awk '/^Mem:/{print (($7 + 0) > 0) ? $7 : $4}'; }
swap_used_mb() { free -m | awk '/^Swap:/{print $3 + 0}'; }

# issue #3 的验收阈值：任一服务 RSS > 500 MiB 判定余量不足。
check_rss() {
  local ids="" service id line name usage mb
  for service in $SERVICES; do
    id="$(container_of "$service")"
    [[ -n "$id" ]] && ids="$ids $id"
  done
  if [[ -z "$ids" ]]; then
    fail "无法定位四个应用容器，跳过 RSS 检查"
    return 1
  fi
  # shellcheck disable=SC2086  # 需要按空格拆分成多个容器 ID
  while IFS= read -r line; do
    name="$(echo "$line" | awk '{print $1}')"
    usage="$(echo "$line" | awk '{print $2}')"
    mb="$(echo "$usage" | awk '{
      value = $0 + 0
      if ($0 ~ /GiB/) printf "%d", value * 1024
      else if ($0 ~ /MiB/) printf "%d", value
      else if ($0 ~ /KiB/) printf "%d", value / 1024
      else printf "%d", value / 1048576
    }')"
    if [[ -z "$mb" ]]; then
      fail "无法解析 $name 的内存占用：$usage"
    elif [[ "$mb" -gt "$MAX_SERVICE_RSS_MB" ]]; then
      fail "$name 内存占用 ${mb} MiB 超过阈值 ${MAX_SERVICE_RSS_MB} MiB（issue #3）"
    else
      ok "$name 内存占用 ${mb} MiB（阈值 ${MAX_SERVICE_RSS_MB} MiB）"
    fi
  done < <(docker stats --no-stream --format '{{.Name}} {{.MemUsage}}' $ids)
  [[ "$FAILED" -eq 0 ]]
}

check_resources() {
  local avail_before swap_before avail_after swap_after growth disk_mb
  avail_before="$(mem_available_mb)"
  swap_before="$(swap_used_mb)"
  info "== 资源观察 ${SAMPLE_SECONDS}s（可用 ${avail_before} MiB，swap 已用 ${swap_before} MiB）=="
  sleep "$SAMPLE_SECONDS"
  avail_after="$(mem_available_mb)"
  swap_after="$(swap_used_mb)"
  growth=$((swap_after - swap_before))
  [[ "$growth" -lt 0 ]] && growth=0
  disk_mb="$(df -Pk "$DEPLOY_ROOT" | awk 'NR==2{print int($4 / 1024)}')"

  info "   可用内存: ${avail_before} MiB -> ${avail_after} MiB（下限 ${MIN_AVAIL_MB} MiB）"
  info "   swap 已用: ${swap_before} MiB -> ${swap_after} MiB（增长 ${growth} MiB，上限 ${MAX_SWAP_GROWTH_MB} MiB）"
  info "   部署根目录可用磁盘: ${disk_mb} MiB"
  if [[ "$avail_after" -lt "$MIN_AVAIL_MB" ]]; then
    fail "四服务启动后 available=${avail_after} MiB，低于 ${MIN_AVAIL_MB} MiB（issue #3 验收阈值）"
  else
    ok "内存余量满足阈值（available=${avail_after} MiB）"
  fi
  if [[ "$growth" -gt "$MAX_SWAP_GROWTH_MB" ]]; then
    fail "观察窗口内 swap 增长 ${growth} MiB，超过 ${MAX_SWAP_GROWTH_MB} MiB（疑似内存不足）"
  else
    ok "swap 在观察窗口内稳定（增长 ${growth} MiB）"
  fi
  check_rss
  [[ "$FAILED" -eq 0 ]]
}

info "部署验收（只读）：$DEPLOY_ROOT"
info "   服务: frameworkjava-{$(echo "$SERVICES" | tr ' ' ',')}；Nacos: 127.0.0.1:$NACOS_HOST_PORT/${NACOS_NAMESPACE}；Nginx: 127.0.0.1:$WEB_PORT"

deadline=$(( $(date +%s) + READY_TIMEOUT ))
attempt=0
ready=0
while :; do
  attempt=$((attempt + 1))
  FAILED=0
  info "== 就绪校验第 ${attempt} 次 =="
  check_containers || true
  check_logs || true
  check_health || true
  if [[ "$FAILED" -eq 0 ]]; then
    ready=1
    break
  fi
  if [[ "$FATAL" -eq 1 ]]; then
    echo "[FAIL] 启动日志已出现致命错误，不再等待" >&2
    exit 1
  fi
  if [[ "$(date +%s)" -ge "$deadline" ]]; then
    break
  fi
  info "   未就绪，${READY_INTERVAL}s 后重试（上限 ${READY_TIMEOUT}s）"
  sleep "$READY_INTERVAL"
done

if [[ "$ready" -ne 1 ]]; then
  echo "[FAIL] 四服务未在 ${READY_TIMEOUT}s 内就绪" >&2
  exit 1
fi

FAILED=0
check_resources || true
if [[ "$FAILED" -ne 0 ]]; then
  echo "[FAIL] 部署验收未通过" >&2
  exit 1
fi
echo "部署验收通过：四服务容器就绪、Nacos 注册健康、经 Nginx 后端 API 可达"
