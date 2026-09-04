#!/usr/bin/env bash
#
# nop-stream 快速起步脚手架 —— 脚本化验证
#
# 端到端路径：生成工程 → 编译 → 3 个入门拓扑测试全部通过。
#
# 前置条件（脚本会自动检测并补齐）：
#   本地 maven 仓库已安装 nop-stream 构件。缺失时自动执行
#   ./mvnw install -pl nop-stream -am -DskipTests
#   （约 1—2 分钟；也可预先手动执行以跳过）。
#
# 任何步骤失败即非零退出（set -euo pipefail），无静默跳过。
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MVNW="$REPO_ROOT/mvnw"

NOP_STREAM_VERSION="${NOP_STREAM_VERSION:-2.0.0-SNAPSHOT}"
DEST="$REPO_ROOT/_tmp/quickstart-verify"

echo "[verify] repo root: $REPO_ROOT"

# 1. 本地仓库构件检测 / 补齐
#    FORCE_REBUILD=1 时强制重新安装（默认：存在即跳过。AR-28/AR-1：陈旧 jar 会掩盖
#    模板/依赖变更，使「存在即跳过」的 freshness 判定无法验证最新代码）。
FLOW_JAR_DIR="$HOME/.m2/repository/io/github/entropy-cloud/nop-stream-flow/$NOP_STREAM_VERSION"
if [ "${FORCE_REBUILD:-0}" = "1" ]; then
    echo "[verify] FORCE_REBUILD=1 — 强制重新安装 nop-stream 构件（-DskipTests）..."
    "$MVNW" -f "$REPO_ROOT/pom.xml" install -pl nop-stream -am -DskipTests -T 1C
elif ! ls "$FLOW_JAR_DIR"/nop-stream-flow-*.jar >/dev/null 2>&1; then
    echo "[verify] 本地仓库缺少 nop-stream $NOP_STREAM_VERSION 构件，执行安装（-DskipTests）..."
    "$MVNW" -f "$REPO_ROOT/pom.xml" install -pl nop-stream -am -DskipTests -T 1C
else
    echo "[verify] 本地仓库已有 nop-stream $NOP_STREAM_VERSION 构件（FORCE_REBUILD=1 可强制重建）"
fi

# 2. 生成工程（目标已存在则清理重建，保证可重复验证）
if [ -e "$DEST" ]; then
    echo "[verify] 清理旧验证目录: $DEST"
    rm -rf "$DEST"
fi
"$SCRIPT_DIR/generate.sh" "$DEST" \
    --group-id com.example \
    --artifact-id quickstart-verify \
    --package com.example.quickstartverify \
    --nop-stream-version "$NOP_STREAM_VERSION"

# 3. 生成的工程 mvn test（3 拓扑测试全绿才算通过）
echo "[verify] 在生成的工程上执行 mvn test ..."
"$MVNW" -f "$DEST/pom.xml" test

echo "[verify] OK — 脚手架端到端验证通过：生成 → mvn test → 3 拓扑全绿"
echo "[verify] 生成工程保留在: $DEST"
