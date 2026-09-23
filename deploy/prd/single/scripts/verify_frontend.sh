#!/usr/bin/env bash
#
# 前端发布后只读验收：webprd 容器运行、静态产物一致性、assets 缓存与 404 隔离、
# SPA 路由回退、同源 /api/ 到网关的反向代理。
#
# 只读约束（硬性）：本脚本只调用 docker ps / inspect 与只读 HTTP 请求；
# 不 up、不 stop、不 restart、不 exec、不 build、不 down，绝不改动任何容器或文件。
#
# 用法:
#   verify_frontend.sh --deploy-root /home/ubuntu/framework_java/prd/single
#   verify_frontend.sh --deploy-root ... --expected-sha256 HASH --external-host HOST
#
# 退出码：0 全部通过；1 有检查项失败；2 用法或环境错误。
#
set -euo pipefail

COMPOSE_PROJECT="${COMPOSE_PROJECT:-frameworkjava-prd}"
WEB_PORT_DEFAULT="8666"

DEPLOY_ROOT=""
EXPECTED_SHA256=""
EXTERNAL_HOST=""
WEB_PORT=""
FAILED=0

usage() {
  cat <<'EOF'
用法: verify_frontend.sh --deploy-root DIR [选项]

  --deploy-root DIR        服务器 single 部署根目录（必填，内含 .env 与 app/）
  --expected-sha256 HASH   期望的 index.html SHA-256 校验和（可选）
  --external-host HOST     从外部观测点验证的主机或 IP（可选）
  --web-port PORT          Nginx 宿主端口（默认读取 .env 中 WEB_PORT，回落 8666）
  -h, --help               显示帮助
EOF
}

die() { echo "[FAIL] $*" >&2; exit 2; }
info() { echo "$*"; }
ok() { echo "[ OK ] $*"; }
fail() { echo "[FAIL] $*" >&2; FAILED=1; }

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

sha256_str() {
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s' "$1" | sha256sum | awk '{print $1}'
  else
    printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --deploy-root) DEPLOY_ROOT="${2:-}"; shift 2 ;;
    --expected-sha256) EXPECTED_SHA256="${2:-}"; shift 2 ;;
    --external-host) EXTERNAL_HOST="${2:-}"; shift 2 ;;
    --web-port) WEB_PORT="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) die "未知参数: $1（见 --help）" ;;
  esac
done

[[ -n "$DEPLOY_ROOT" ]] || die "缺少 --deploy-root"
[[ -d "$DEPLOY_ROOT" ]] || die "部署根目录不存在: $DEPLOY_ROOT"

APP_DIR="$DEPLOY_ROOT/app"
ENV_FILE="$DEPLOY_ROOT/.env"

if [[ -z "$WEB_PORT" && -f "$ENV_FILE" ]]; then
  WEB_PORT="$(grep -E '^WEB_PORT=' "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r"' || true)"
fi
WEB_PORT="${WEB_PORT:-$WEB_PORT_DEFAULT}"

info "==> 开始前端只读验收 (Web Port: $WEB_PORT)"

# 1. 检查 webprd 容器状态
info "[1/6] 检查 webprd 容器运行状态..."
WEBPRD_ID="$(docker ps -a -q \
  --filter "label=com.docker.compose.project=$COMPOSE_PROJECT" \
  --filter "label=com.docker.compose.service=frameworkjava-webprd" | head -1)"

if [[ -z "$WEBPRD_ID" ]]; then
  # 降级：通过容器名直接找
  WEBPRD_ID="$(docker ps -a -q --filter "name=${COMPOSE_PROJECT}.*webprd" | head -1)"
fi

if [[ -n "$WEBPRD_ID" ]]; then
  STATUS="$(docker inspect -f '{{.State.Status}}' "$WEBPRD_ID" 2>/dev/null || true)"
  if [[ "$STATUS" == "running" ]]; then
    ok "webprd 容器处于运行状态 (id: $WEBPRD_ID)"
  else
    fail "webprd 容器状态异常: $STATUS"
  fi
else
  fail "未找到 webprd 容器 (project: $COMPOSE_PROJECT)"
fi

# 2. 检查宿主机 dist/index.html
info "[2/6] 检查宿主机静态产物..."
DIST_INDEX="$APP_DIR/nginx/web/dist/index.html"
if [[ -f "$DIST_INDEX" && -s "$DIST_INDEX" ]]; then
  LOCAL_SHA="$(sha256_of "$DIST_INDEX")"
  ok "宿主机 index.html 存在 (sha256: $LOCAL_SHA)"
  if [[ -n "$EXPECTED_SHA256" ]]; then
    if [[ "$LOCAL_SHA" == "$EXPECTED_SHA256" ]]; then
      ok "宿主机 index.html 与期望 SHA-256 一致"
    else
      fail "宿主机 index.html SHA-256 不匹配: 实际 $LOCAL_SHA != 期望 $EXPECTED_SHA256"
    fi
  fi
else
  fail "宿主机 index.html 不存在或为空: $DIST_INDEX"
fi

# 3. 本机 HTTP 首页验收与缓存头校验
info "[3/6] 验收本机 HTTP GET / (127.0.0.1:$WEB_PORT)..."
INDEX_RESP_HEADERS="$(curl -s -D - -o /tmp/frontend_verify_index.html "http://127.0.0.1:${WEB_PORT}/" || true)"
INDEX_HTTP_CODE="$(printf '%s' "$INDEX_RESP_HEADERS" | awk '/^HTTP/{code=$2} END{print code}')"

if [[ "$INDEX_HTTP_CODE" == "200" ]]; then
  RESP_SHA="$(sha256_of "/tmp/frontend_verify_index.html")"
  if [[ -f "$DIST_INDEX" ]]; then
    EXPECT_SHA="$(sha256_of "$DIST_INDEX")"
    if [[ "$RESP_SHA" == "$EXPECT_SHA" ]]; then
      ok "HTTP GET / 返回 200，且响应内容与磁盘 index.html SHA-256 完全一致"
    else
      fail "HTTP GET / 返回内容与 index.html SHA-256 不一致 ($RESP_SHA != $EXPECT_SHA)"
    fi
  else
    ok "HTTP GET / 返回 200"
  fi
  if printf '%s' "$INDEX_RESP_HEADERS" | grep -iq "Cache-Control:.*no-cache"; then
    ok "HTTP GET / 包含正确的 no-cache 缓存控制头"
  else
    fail "HTTP GET / 缺少 Cache-Control: no-cache 头"
  fi
else
  fail "HTTP GET / 失败，HTTP 状态码: $INDEX_HTTP_CODE"
fi
rm -f /tmp/frontend_verify_index.html

# 4. 静态资源 404 与强缓存验收
info "[4/6] 验收静态资源缓存与 404 隔离..."
ASSET_PATH=""
if [[ -f "$DIST_INDEX" ]]; then
  ASSET_PATH="$(grep -oE '/assets/[a-zA-Z0-9_-]+\.(js|css)' "$DIST_INDEX" | head -1 || true)"
fi

if [[ -n "$ASSET_PATH" ]]; then
  ASSET_HEADERS="$(curl -s -D - -o /dev/null "http://127.0.0.1:${WEB_PORT}${ASSET_PATH}" || true)"
  ASSET_CODE="$(printf '%s' "$ASSET_HEADERS" | awk '/^HTTP/{code=$2} END{print code}')"
  if [[ "$ASSET_CODE" == "200" ]]; then
    ok "真实静态资源可访问: $ASSET_PATH (HTTP 200)"
    if printf '%s' "$ASSET_HEADERS" | grep -iq "immutable"; then
      ok "静态资源包含 immutable 长期强缓存策略"
    else
      fail "静态资源缺少 immutable 强缓存头"
    fi
  else
    fail "静态资源请求失败: $ASSET_PATH (HTTP $ASSET_CODE)"
  fi
else
  info "index.html 中未提取到 /assets/ 链接，跳过真实资源头检测"
fi

# 缺失静态资源严禁回退 index.html，必须 404
MISSING_ASSET_CODE="$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:${WEB_PORT}/assets/missing_asset_probe_404.js" || true)"
if [[ "$MISSING_ASSET_CODE" == "404" ]]; then
  ok "不存在的 /assets/ 请求正确返回 404（未发生 index.html 回退）"
else
  fail "不存在的 /assets/ 请求未返回 404: 实际返回 $MISSING_ASSET_CODE"
fi

# 5. SPA 深层路由回退验收 (/accounts)
info "[5/6] 验收 SPA 路由回退 (/accounts)..."
SPA_RESP_HEADERS="$(curl -s -D - -o /tmp/frontend_verify_spa.html "http://127.0.0.1:${WEB_PORT}/accounts" || true)"
SPA_CODE="$(printf '%s' "$SPA_RESP_HEADERS" | awk '/^HTTP/{code=$2} END{print code}')"
if [[ "$SPA_CODE" == "200" ]]; then
  SPA_SHA="$(sha256_of "/tmp/frontend_verify_spa.html")"
  if [[ -f "$DIST_INDEX" && "$SPA_SHA" == "$(sha256_of "$DIST_INDEX")" ]]; then
    ok "深层路由 /accounts 返回 200 且正确回退到 index.html"
  else
    ok "深层路由 /accounts 返回 200"
  fi
else
  fail "深层路由 /accounts 请求失败: HTTP $SPA_CODE"
fi
rm -f /tmp/frontend_verify_spa.html

# 6. API 反向代理链路验收 (/api/sys_user/login/password)
info "[6/6] 验收同源 /api/ 到微服务网关的反向代理链路..."
API_RESP="$(curl -s -X POST "http://127.0.0.1:${WEB_PORT}/api/sys_user/login/password" \
  -H "Content-Type: application/json" -d "{}" || true)"

if printf '%s' "$API_RESP" | grep -q '"code"'; then
  ok "/api/ 反向代理成功连通后端网关，返回标准化业务信封 (含 \"code\")"
else
  fail "/api/ 反向代理响应未包含业务信封: $API_RESP"
fi

# 可选：外部观测点检测
if [[ -n "$EXTERNAL_HOST" ]]; then
  info "==> 探测外部观测点 (http://${EXTERNAL_HOST}:${WEB_PORT}/)..."
  EXT_CODE="$(curl -s -m 10 -o /dev/null -w "%{http_code}" "http://${EXTERNAL_HOST}:${WEB_PORT}/" || true)"
  if [[ "$EXT_CODE" == "200" ]]; then
    ok "外部观测点访问成功 (HTTP 200，云安全组放通正常)"
  else
    fail "外部观测点无法访问 http://${EXTERNAL_HOST}:${WEB_PORT}/ (HTTP $EXT_CODE)"
  fi
fi

if [[ $FAILED -ne 0 ]]; then
  die "前端验收未通过，详见上方失败项"
fi

ok "前端只读验收全部通过"
exit 0
