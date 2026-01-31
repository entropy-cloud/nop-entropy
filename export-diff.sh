#!/bin/bash

# 导出当前分支与主分支之间的diff差异文件
# 文件名格式: diff_主分支_当前分支_时间戳.diff

CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)

MAIN_BRANCH="main"
if ! git show-ref --verify --quiet refs/heads/main; then
    MAIN_BRANCH="master"
fi

if [ "$CURRENT_BRANCH" = "$MAIN_BRANCH" ]; then
    echo "警告: 当前分支就是主分支 ($MAIN_BRANCH)，没有差异需要导出"
    exit 0
fi

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DIFF_FILE="diff_${TIMESTAMP}.diff"

echo "正在导出 $MAIN_BRANCH 和 $CURRENT_BRANCH 之间的差异..."
git diff ${MAIN_BRANCH}...${CURRENT_BRANCH} > "$DIFF_FILE"

if [ $? -eq 0 ]; then
    if [ -s "$DIFF_FILE" ]; then
        FILE_SIZE=$(ls -lh "$DIFF_FILE" | awk '{print $5}')
        echo "✓ 差异文件已成功导出: $DIFF_FILE"
        echo "  文件大小: $FILE_SIZE"
    else
        echo "✓ 差异文件已创建，但没有检测到差异: $DIFF_FILE"
        rm "$DIFF_FILE"
    fi
else
    echo "✗ 导出失败"
    exit 1
fi
