#!/bin/bash
export LANG=en_US.UTF-8
export LC_ALL=en_US.UTF-8

# 定义要合并的目录路径
source_dir="."
# 定义输出文件的路径
output_file="merged.md"
# 定义分隔行的内容
separator="   "

cat << EOF > "$output_file"
---
CJKmainfont: KaiTi
---

EOF

# 递归遍历目录并合并Markdown文件
function merge_markdowns() {
    # 打开输出文件

    # 遍历目录
    for file in $(find "$source_dir" -type f -name "*.md"); do
        # 读取Markdown文件内容并添加到输出文件
        cat "$file" >> "$output_file"
        # 添加分隔行
        echo "$separator" >> "$output_file"
    done
}
# 调用函数执行合并操作
merge_markdowns

#iconv -f gbk -t utf-8 merged.md > merged-utf8.md

#pandoc --pdf-engine=xelatex merged.md -o nop.docx  -s

