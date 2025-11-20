package io.nop.markdown.utils;

import io.nop.api.core.util.ProcessResult;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.markdown.table.MarkdownTableParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.commons.util.StringHelper.escapeMarkdown;

class MarkdownTableHelper {

    /**
     * 查找第一个GFM表格的开始位置
     *
     * @return 表格开始的字符位置，如果没找到返回-1
     */
    public static int findTable(String text) {
        if (text == null || text.isEmpty()) return -1;

        int len = text.length();
        int i = 0;
        int stage = 0; // 0=找表头行 1=找分隔行 2=找数据行
        int tableStartPos = -1; // 记录表格开始的字符位置

        while (i < len) {
            // 记录当前行的开始位置
            int lineStart = i;

            // 扫描到行尾
            int pipes = 0;
            boolean lineHasContent = false;
            while (i < len && text.charAt(i) != '\n') {
                char c = text.charAt(i);
                if (c == '|') pipes++;
                if (c != ' ' && c != '\t') lineHasContent = true;
                i++;
            }

            // 跳过换行符
            if (i < len && text.charAt(i) == '\n') i++;

            if (stage == 0) {
                // 找表头行：必须有 | 且非空行
                if (pipes > 0 && lineHasContent) {
                    tableStartPos = lineStart; // 记录表格开始字符位置
                    stage = 1;
                }
            } else if (stage == 1) {
                // 找分隔行：必须有 | 且是合法分隔行
                if (pipes > 0 && isSeparatorLine(text, lineStart, i)) {
                    stage = 2;
                } else {
                    // 重置状态，但当前行可能是一个新的表头行
                    if (pipes > 0 && lineHasContent) {
                        tableStartPos = lineStart; // 重新记录表格开始位置
                        stage = 1;
                    } else {
                        tableStartPos = -1;
                        stage = 0;
                    }
                }
            } else { // stage == 2
                // 找到完整的三连，返回表格开始位置
                return tableStartPos;
            }
        }

        return -1;
    }

    private static boolean isSeparatorLine(String text, int start, int end) {
        boolean hasDash = false;
        for (int j = start; j < end; j++) {
            char c = text.charAt(j);
            switch (c) {
                case '|':
                case ' ':
                case '\t':
                case ':':
                    break;
                case '-':
                case '=':
                    hasDash = true;
                    break;
                default:
                    return false;
            }
        }
        return hasDash;
    }

    public static String buildMappingTable(Collection<String> list, String sourceField, String targetField) {
        StringBuilder sb = new StringBuilder();
        sb.append('|').append(escapeMarkdown(sourceField)).append('|').append(escapeMarkdown(targetField)).append('|');
        sb.append("\n|-----|-----|\n");
        for (String item : list) {
            sb.append("\n|").append(escapeMarkdown(item)).append("| |\n");
        }
        return sb.toString();
    }

    public static Map<String, String> parseMappingTable(SourceLocation loc, String text) {
        BaseTable table = MarkdownTableParser.parseTable(loc, text);
        Map<String, String> map = new LinkedHashMap<>();
        String sourceField = table.getCellText(0, 0);
        String targetField = table.getCellText(0, 1);

        for (int i = 1, n = table.getRowCount(); i < n; i++) {
            IRowView row = table.getRow(i);
            String source = row.getCellText(0);
            String target = row.getCellText(1);
            map.put(sourceField, source);
            map.put(targetField, target);
        }

        return map;
    }

    public static List<Map<String, Object>> toRecordList(ITableView table) {
        if (table == null)
            return null;

        int rowCount = table.getRowCount();
        if (rowCount <= 1)
            return new ArrayList<>();

        List<String> headers = table.getRow(0).getCellTexts();

        List<Map<String, Object>> ret = new ArrayList<>();
        for (int i = 1; i < rowCount; i++) {
            IRowView row = table.getRow(i);
            Map<String, Object> data = new LinkedHashMap<>();
            row.forEachCell(i, (cell, rowIndex, colIndex) -> {
                String header = CollectionHelper.get(headers, colIndex);
                data.put(header, cell.getValue());
                return ProcessResult.CONTINUE;
            });
            ret.add(data);
        }

        return ret;
    }
}