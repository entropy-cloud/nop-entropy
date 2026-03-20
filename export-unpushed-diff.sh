#!/bin/bash

set -euo pipefail

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

if [ "$CURRENT_BRANCH" = "HEAD" ]; then
    echo "错误: 当前处于 detached HEAD 状态，无法判断分支名"
    exit 1
fi

if ! UPSTREAM_BRANCH=$(git rev-parse --abbrev-ref --symbolic-full-name @{upstream} 2>/dev/null); then
    echo "错误: 当前分支 ($CURRENT_BRANCH) 没有配置上游分支"
    echo "提示: 先执行 git push -u <remote> $CURRENT_BRANCH 或 git branch --set-upstream-to <remote>/<branch>"
    exit 1
fi

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
SAFE_BRANCH_NAME=${CURRENT_BRANCH//\//_}
DIFF_FILE="unpushed_diff_${SAFE_BRANCH_NAME}_${TIMESTAMP}.diff"

echo "正在导出当前分支尚未推送到远程的差异..."
echo "  当前分支: $CURRENT_BRANCH"
echo "  上游分支: $UPSTREAM_BRANCH"

git diff @{upstream}..HEAD > "$DIFF_FILE"

if [ -s "$DIFF_FILE" ]; then
    FILE_SIZE=$(wc -c < "$DIFF_FILE")
    echo "✓ 差异文件已成功导出: $DIFF_FILE"
    echo "  文件大小: ${FILE_SIZE} bytes"
else
    echo "✓ 当前分支没有尚未推送到远程的已提交差异"
    rm -f "$DIFF_FILE"
fi
