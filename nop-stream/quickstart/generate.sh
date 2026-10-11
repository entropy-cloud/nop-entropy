#!/usr/bin/env bash
#
# nop-stream 快速起步脚手架生成脚本
#
# 用法:
#   ./generate.sh <targetDir> [--group-id <g>] [--artifact-id <a>] [--package <p>] [--nop-stream-version <v>]
#
# 将 template/ 复制到 <targetDir> 并替换占位符（@groupId@/@artifactId@/@package@/@packagePath@/@nop-stream.version@）。
# 任何失败步骤非零退出；生成后残留占位符视为失败。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMPLATE_DIR="$SCRIPT_DIR/template"

GROUP_ID="com.example"
ARTIFACT_ID="my-stream-job"
NOP_STREAM_VERSION="2.0.0-SNAPSHOT"
TARGET_DIR=""

usage() {
    cat <<'USAGE'
Usage: generate.sh <targetDir> [options]

Options:
  --group-id <g>           Maven groupId        (default: com.example)
  --artifact-id <a>        Maven artifactId     (default: my-stream-job)
  --package <p>            Java base package    (default: derived from groupId/artifactId)
  --nop-stream-version <v> nop-stream version   (default: 2.0.0-SNAPSHOT)
  -h, --help               Show this help
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --group-id) GROUP_ID="${2:?--group-id requires a value}"; shift 2 ;;
        --artifact-id) ARTIFACT_ID="${2:?--artifact-id requires a value}"; shift 2 ;;
        --package) PACKAGE="${2:?--package requires a value}"; shift 2 ;;
        --nop-stream-version) NOP_STREAM_VERSION="${2:?--nop-stream-version requires a value}"; shift 2 ;;
        -h|--help) usage; exit 0 ;;
        -*) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
        *) if [ -n "$TARGET_DIR" ]; then echo "ERROR: unexpected extra argument: $1" >&2; usage >&2; exit 2; fi
           TARGET_DIR="$1"; shift ;;
    esac
done

if [ -z "$TARGET_DIR" ]; then
    echo "ERROR: <targetDir> is required" >&2
    usage >&2
    exit 2
fi

PACKAGE="${PACKAGE:-$(echo "$GROUP_ID.$ARTIFACT_ID" | tr -d '-' | tr '[:upper:]' '[:lower:]')}"
PACKAGE_PATH="$(echo "$PACKAGE" | tr '.' '/')"

if [ ! -d "$TEMPLATE_DIR" ]; then
    echo "ERROR: template directory not found: $TEMPLATE_DIR" >&2
    exit 1
fi

if [ -e "$TARGET_DIR" ]; then
    echo "ERROR: target already exists: $TARGET_DIR (remove it first)" >&2
    exit 1
fi

echo "[generate] template   : $TEMPLATE_DIR"
echo "[generate] target     : $TARGET_DIR"
echo "[generate] groupId    : $GROUP_ID"
echo "[generate] artifactId : $ARTIFACT_ID"
echo "[generate] package    : $PACKAGE"

mkdir -p "$TARGET_DIR"
cp -R "$TEMPLATE_DIR/." "$TARGET_DIR/"

# 展开包目录占位符（main + test 两棵树）
for tree in src/main/java src/test/java; do
    if [ -d "$TARGET_DIR/$tree/@packagePath@" ]; then
        mkdir -p "$TARGET_DIR/$tree/$PACKAGE_PATH"
        mv "$TARGET_DIR/$tree/@packagePath@"/* "$TARGET_DIR/$tree/$PACKAGE_PATH/"
        rmdir "$TARGET_DIR/$tree/@packagePath@"
    else
        echo "ERROR: expected placeholder directory missing: $TARGET_DIR/$tree/@packagePath@" >&2
        exit 1
    fi
done

# 替换文件内容中的占位符
# 可移植写法：逐文件临时文件替换（BSD sed 的 `-i ''` 后缀语义与 GNU sed 不兼容，
# GNU 会把独立词 '' 当文件操作数；sed 无 -i 的用法两平台一致）。
# 注意 -e 顺序：@packagePath@ 必须先于 @package@ 替换（前者含后者字面前缀，反序会碾碎占位符）。
# fail-fast：`sed && mv` 中 sed 的失败不触发 set -e（AND-OR 列表非末命令的失败被忽略），
# 必须用 `|| { rm -f; exit 1; }` 显式传播——探针实测：缺此子句时循环跑完全部文件并残留 .tmp。
find "$TARGET_DIR" -type f \( -name '*.java' -o -name '*.xml' \) -print0 |
    while IFS= read -r -d '' f; do
        sed \
            -e "s|@packagePath@|$PACKAGE_PATH|g" \
            -e "s|@package@|$PACKAGE|g" \
            -e "s|@groupId@|$GROUP_ID|g" \
            -e "s|@artifactId@|$ARTIFACT_ID|g" \
            -e "s|@nop-stream.version@|$NOP_STREAM_VERSION|g" \
            "$f" > "$f.tmp" || { rm -f "$f.tmp"; exit 1; }
        mv "$f.tmp" "$f"
    done

# 无残留占位符校验（任何失败非零退出）
if grep -rqE '@(package|packagePath|groupId|artifactId|nop-stream\.version)@' "$TARGET_DIR"; then
    echo "ERROR: unresolved placeholders remain in $TARGET_DIR:" >&2
    grep -rlE '@(package|packagePath|groupId|artifactId|nop-stream\.version)@' "$TARGET_DIR" >&2
    exit 1
fi

echo "[generate] OK — 生成的工程：$TARGET_DIR"
echo "[generate] 下一步："
echo "  cd $TARGET_DIR && mvn test          # 前置：本地仓库已安装 nop-stream 构件（见 README）"
