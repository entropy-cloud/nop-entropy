package io.nop.markdown.table;

import io.nop.api.core.util.ProcessResult;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;
import io.nop.markdown.utils.MarkdownHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * 将ITableView转换为MarkdownTable支持的结构
 */
class TableViewToMarkdownTableConverter {
    public static final TableViewToMarkdownTableConverter INSTANCE = new TableViewToMarkdownTableConverter();

    /**
     * 将ITableView对象转换为MarkdownTable
     *
     * @param tableView 表格数据对象
     * @return MarkdownTable对象
     */
    public MarkdownTable convertToMarkdownTable(ITableView tableView) {
        if (tableView == null) {
            throw new IllegalArgumentException("TableView cannot be null");
        }

        int cols = tableView.getColCount();
        int rows = tableView.getRowCount();

        // 空表格处理
        if (cols == 0 || rows == 0) {
            return new MarkdownTable();
        }

        // 提取表头（假设第一行是表头）
        List<String> headers = extractHeaders(tableView, cols);

        // 创建MarkdownTable对象
        MarkdownTable markdownTable = new MarkdownTable();
        markdownTable.setHeaders(headers);

        // 提取数据行（从第二行开始）
        extractDataRows(markdownTable, tableView, rows, cols);

        return markdownTable;
    }

    /**
     * 提取表头（第一行）
     */
    private List<String> extractHeaders(ITableView tableView, int cols) {
        List<String> headers = new ArrayList<>();
        IRowView headerRow = tableView.getRow(0);

        headerRow.forEachCell(0, (cell, rowIndex, colIndex) -> {
            String cellText = "";
            if (cell != null) {
                cellText = cell.getRealCell().getText();
            }
            headers.add(escapeCell(cellText));
            return ProcessResult.CONTINUE;
        });

        // 仅补齐稀疏行不足的列。getColCount 是全表最大列数，规则表格下 headers
        // 已有 cols 个元素，CollectionHelper.set(..., null) 会覆盖最后一个表头
        padToColumns(headers, cols);

        return headers;
    }

    /**
     * 提取数据行（从第二行开始）
     */
    private void extractDataRows(MarkdownTable markdownTable, ITableView tableView, int rows, int cols) {
        for (int r = 1; r < rows; r++) {
            List<String> rowData = new ArrayList<>();
            IRowView dataRow = tableView.getRow(r);

            dataRow.forEachCell(r, (cell, rowIndex, colIndex) -> {
                String cellText = "";
                if (cell != null) {
                    cellText = cell.getText();
                }
                rowData.add(escapeCell(cellText));
                return ProcessResult.CONTINUE;
            });

            padToColumns(rowData, cols);

            markdownTable.addRow(rowData);
        }
    }

    /**
     * 将稀疏行补齐到全表列数，不覆盖已有单元格
     */
    private void padToColumns(List<String> cells, int cols) {
        for (int i = cells.size(); i < cols; i++) {
            cells.add(null);
        }
    }

    /**
     * 转义单元格特殊字符
     * - 管道符 | 转换为 \|
     * - 换行符转换为空格
     */
    private String escapeCell(String content) {
        return MarkdownHelper.escapeCell(content);
    }
}