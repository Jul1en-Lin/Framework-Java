#!/usr/bin/env bash
#
# 构建并组织四服务（gateway/admin/file/portal）的 Spring Boot 可执行发布包。
#
# 产出布局（每个服务恰好一份 JAR + 一份校验和）：
#
#   release/
#   ├── build-info.txt
#   ├── gateway/lien-gateway.jar(+.sha256)
#   ├── admin/lien-admin-service.jar(+.sha256)
#   ├── file/lien-file-service.jar(+.sha256)
#   └── portal/lien-portal-service.jar(+.sha256)
#
# 约定：
#   * JAR 从「明确的模块」的 target/ 收集，按 *.jar 匹配（不会匹配 .jar.original），
#     因此不硬编码 maven 版本号；候选不是唯一一份时立即失败。
#   * 发布包内统一改名为 <artifactId>.jar，把模块级 finalName 差异收敛在一处。
#   * 暂存后自动执行 scripts/verify_service_artifacts.py 校验；校验失败则非零退出。
#   * release/ 是构建产物目录，已被 .gitignore 忽略。
#
# 兼容 macOS 自带的 bash 3.2（不使用关联数组 / mapfile）。
#
set -euo pipefail

usage() {
  cat <<'EOF'
用法: scripts/build_release.sh [选项]

  --release-dir DIR   发布包目录（默认 <repo>/release）
  --service NAME      只处理指定服务（gateway|admin|file|portal，可重复，默认四个全做）
  --with-tests        不传 -DskipTests（默认跳过；集成测试需要真实 MySQL/Redis/RabbitMQ）
  --skip-build        跳过 maven，直接暂存现有 target/ 制品
  --maven-args "ARGS" 追加给 maven 的参数（例如 "-o" 走离线构建）
  -h, --help          显示帮助
EOF
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_DIR="$ROOT/release"
MVN="${MVN:-mvn}"
RUN_TESTS=0
SKIP_BUILD=0
EXTRA_MAVEN_ARGS=""

module_of() {
  case "$1" in
    gateway) echo "lien-gateway" ;;
    admin) echo "lien-admin/lien-admin-service" ;;
    file) echo "lien-file/lien-file-service" ;;
    portal) echo "lien-portal/lien-portal-service" ;;
    *) return 1 ;;
  esac
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

contains() {
  local needle="$1" item
  shift
  for item in "$@"; do
    if [[ "$item" == "$needle" ]]; then return 0; fi
  done
  return 1
}

REQUESTED=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-dir) RELEASE_DIR="$2"; shift 2 ;;
    --service) REQUESTED+=("$2"); shift 2 ;;
    --with-tests) RUN_TESTS=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
    --maven-args) EXTRA_MAVEN_ARGS="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 2 ;;
  esac
done

# 稳定顺序：始终按 gateway admin file portal 处理
SELECTED=(gateway admin file portal)
if [[ "${#REQUESTED[@]}" -gt 0 ]]; then
  SELECTED=()
  for known in gateway admin file portal; do
    if contains "$known" "${REQUESTED[@]}"; then SELECTED+=("$known"); fi
  done
  for requested in "${REQUESTED[@]}"; do
    if ! contains "$requested" gateway admin file portal; then
      echo "[FAIL] 未知服务: $requested（可选 gateway admin file portal）" >&2
      exit 2
    fi
  done
fi

PL_LIST=""
for service in "${SELECTED[@]}"; do
  PL_LIST="${PL_LIST:+$PL_LIST,}$(module_of "$service")"
done

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

echo "== 发布包构建 =="
echo "仓库:      $ROOT"
echo "服务:      ${SELECTED[*]}"
echo "输出目录:  $RELEASE_DIR"
echo "模块:      $PL_LIST"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
  MAVEN_CMD=("$MVN")
  if [[ -n "$EXTRA_MAVEN_ARGS" ]]; then
    # shellcheck disable=SC2206  # 允许按空格拆分附加参数
    MAVEN_CMD+=($EXTRA_MAVEN_ARGS)
  fi
  MAVEN_CMD+=(-pl "$PL_LIST" -am clean package)
  if [[ "$RUN_TESTS" -eq 0 ]]; then
    MAVEN_CMD+=(-DskipTests)
    echo "注意: 本次构建带 -DskipTests，单元测试与集成测试都未执行；"
    echo "      lien-admin-service 的 *IntegrationTest 需要真实 MySQL/Redis/RabbitMQ，"
    echo "      CI 中如何提供这些依赖（service container / 单独 job）尚未决定。"
  else
    echo "注意: 已启用测试执行；集成测试需要真实 MySQL/Redis/RabbitMQ。"
  fi
  echo "命令:      ${MAVEN_CMD[*]}"
  (cd "$ROOT" && "${MAVEN_CMD[@]}")
else
  echo "注意: --skip-build，直接使用现有 target/ 制品；新鲜度由校验脚本的 bootstrap.yml 检查兜底。"
fi

# ---- 收集制品：每个模块恰好一份 .jar（转义 .jar.original，与版本号无关） ----
STAGED=()
for service in "${SELECTED[@]}"; do
  module="$(module_of "$service")"
  target_dir="$ROOT/$module/target"
  if [[ ! -d "$target_dir" ]]; then
    echo "[FAIL] $service: 模块未构建，缺少目录 $module/target" >&2
    exit 1
  fi
  candidates=()
  while IFS= read -r line; do
    candidates+=("$line")
  done < <(find "$target_dir" -maxdepth 1 -type f -name '*.jar' | LC_ALL=C sort)
  if [[ "${#candidates[@]}" -eq 0 ]]; then
    echo "[FAIL] $service: $module/target 下没有 .jar 制品" >&2
    exit 1
  fi
  if [[ "${#candidates[@]}" -gt 1 ]]; then
    echo "[FAIL] $service: $module/target 下候选 JAR 多于一份，无法确定发布制品：" >&2
    printf '       %s\n' "${candidates[@]}" >&2
    exit 1
  fi
  STAGED+=("$service|$module|$(artifact_of "$service")|${candidates[0]}")
done

# ---- 暂存发布包 ----
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"
JAVA_VERSION="$(java -version 2>&1 | head -1)"
MAVEN_VERSION="$("$MVN" -v 2>/dev/null | head -1 || echo '未知')"
GIT_REV="$(cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null || echo '未知')"
GIT_DIRTY="未知"
if (cd "$ROOT" && git rev-parse --git-dir >/dev/null 2>&1); then
  if [[ -n "$(cd "$ROOT" && git status --porcelain)" ]]; then GIT_DIRTY="yes"; else GIT_DIRTY="no"; fi
fi
if [[ "$RUN_TESTS" -eq 1 ]]; then TESTS_RUN="yes"; else TESTS_RUN="no(-DskipTests)"; fi

{
  echo "built_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "git_rev=$GIT_REV"
  echo "git_dirty=$GIT_DIRTY"
  echo "java=$JAVA_VERSION"
  echo "maven=$MAVEN_VERSION"
  echo "tests_run=$TESTS_RUN"
  echo "modules=$PL_LIST"
} > "$RELEASE_DIR/build-info.txt"

for entry in "${STAGED[@]}"; do
  IFS='|' read -r service module artifact_id source_jar <<< "$entry"
  service_dir="$RELEASE_DIR/$service"
  mkdir -p "$service_dir"
  cp "$source_jar" "$service_dir/$artifact_id.jar"
  hash="$(sha256_of "$service_dir/$artifact_id.jar")"
  echo "$hash  $artifact_id.jar" > "$service_dir/$artifact_id.jar.sha256"
  printf 'service=%s module=%s artifact=%s source=%s sha256=%s\n' \
    "$service" "$module" "$artifact_id.jar" "${source_jar#"$ROOT"/}" "$hash" >> "$RELEASE_DIR/build-info.txt"
  echo "已暂存 $service -> $service/$artifact_id.jar"
done

# ---- 校验 ----
echo
python3 "$ROOT/scripts/verify_service_artifacts.py" --release-dir "$RELEASE_DIR"
echo
echo "发布包就绪: $RELEASE_DIR"
