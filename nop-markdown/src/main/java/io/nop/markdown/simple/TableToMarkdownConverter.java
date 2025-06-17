package io.nop.markdown.simple;


import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;

/**
 * Markdown表格转换器
 */
public class TableToMarkdownConverter {

    /**
     * 将ITableView对象转换为Markdown表格字符串
     *
     * @param tableView 表格数据对象
     * @return Markdown格式的表格字符串
     */
    public String convertToMarkdown(ITableView tableView) {
        if (tableView == null) {
            throw new IllegalArgumentException("TableView cannot be null");
        }


        StringBuilder md = new StringBuilder();

        convertToMarkdown(tableView, md);

        return md.toString();
    }

    public void convertToMarkdown(ITableView tableView, StringBuilder md) {
        int cols = tableView.getColCount();
        int rows = tableView.getRowCount();

        // 空表格处理
        if (cols == 0) {
            md.append("\n| (empty) |\n|------|");
            return;
        }

        // 1. 构建表头
        appendHeader(md, tableView, cols);

        // 2. 构建分隔线
        appendSeparator(md, cols);

        // 3. 构建数据行
        appendDataRows(md, tableView, rows, cols);
    }

    // 假定第一行是表头
    private void appendHeader(StringBuilder md, ITableView table, int cols) {
        md.append("\n|");
        IRowView row = table.getRow(0);
        row.forEachCell(0, (cell, rowIndex, colIndex) -> {
            String cellText = cell.getRealCell().getText();
            md.append(escapeCell(cellText)).append("|");
            return ProcessResult.CONTINUE;
        });
        md.append("\n");
    }

    private void appendSeparator(StringBuilder md, int cols) {
        md.append("|");
        for (int i = 0; i < cols; i++) {
            md.append("-----|");  // 默认左对齐（:---）
        }
        md.append("\n");
    }

    private void appendDataRows(StringBuilder md, ITableView table, int rows, int cols) {
        for (int r = 1; r < rows; r++) {
            md.append("|");
            IRowView row = table.getRow(r);
            row.forEachCell(r, (cell, rowIndex, colIndex) -> {
                String cellText = cell.getText();
                md.append(escapeCell(cellText)).append("|");
                return ProcessResult.CONTINUE;
            });
            md.append("\n");
        }
    }

    /**
     * 转义单元格特殊字符
     * - 管道符 | 转换为 \|
     * - 换行符转换为空格
     */
    private String escapeCell(String content) {
        if (content == null || content.isEmpty()) return " ";
        return content
                .replace("|", "\\|")  // 转义管道符
                .replace("\n", " ")   // 替换换行为空格
                .replace("\r", "");    // 移除回车符
    }
}
