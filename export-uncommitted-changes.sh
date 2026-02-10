#!/bin/bash

# 导出所有未提交的更改到diff文件
# 用法: ./export-uncommitted-changes.sh [输出文件名]
# 如果不指定文件名，默认使用: uncommitted-changes-YYYY-MM-DD-HHMMSS.diff

OUTPUT_FILE=${1:-"uncommitted-changes-$(date +%Y-%m-%d-%H%M%S).diff"}

echo "正在导出未提交的更改到: $OUTPUT_FILE"
echo "========================================"

TEMP_FILE=$(mktemp)

echo "[未暂存的更改]" >> "$TEMP_FILE"
git diff >> "$TEMP_FILE" 2>&1

echo -e "\n[已暂存的更改]" >> "$TEMP_FILE"
git diff --cached >> "$TEMP_FILE" 2>&1

echo -e "\n[未跟踪的文件]" >> "$TEMP_FILE"
git ls-files --others --exclude-standard >> "$TEMP_FILE" 2>&1

if [ -s "$TEMP_FILE" ]; then
    mv "$TEMP_FILE" "$OUTPUT_FILE"
    echo "✓ 导出成功: $OUTPUT_FILE"

    SIZE=$(du -h "$OUTPUT_FILE" | cut -f1)
    echo "  文件大小: $SIZE"

    echo ""
    echo "统计信息:"
    echo "--------"
    if grep -q "^diff --git" "$OUTPUT_FILE"; then
        NUM_FILES=$(grep -c "^diff --git" "$OUTPUT_FILE")
        echo "  修改的文件数: $NUM_FILES"
    fi
    if grep -q "^[+-]" "$OUTPUT_FILE"; then
        ADD_LINES=$(grep -c "^+" "$OUTPUT_FILE" | xargs)
        DEL_LINES=$(grep -c "^-" "$OUTPUT_FILE" | xargs)
        echo "  新增行数: ~$ADD_LINES"
        echo "  删除行数: ~$DEL_LINES"
    fi
else
    rm "$TEMP_FILE"
    echo "✓ 没有未提交的更改"
fi
