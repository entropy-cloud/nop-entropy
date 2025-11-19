package io.nop.markdown.table;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.model.table.ITableView;
import io.nop.core.model.table.impl.BaseCell;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.markdown.utils.MarkdownHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown表格类
 * 用于描述和操作Markdown表格结构
 */
@DataBean
public class MarkdownTable {

    /**
     * 对齐方式枚举
     */
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }

    private List<String> headers;          // 表头文本
    private List<Alignment> alignments;    // 列对齐方式
    private List<List<String>> rows;       // 表格数据行

    // 构造函数
    public MarkdownTable() {
        this.headers = new ArrayList<>();
        this.alignments = new ArrayList<>();
        this.rows = new ArrayList<>();
    }

    /**
     * 使用表头和对齐方式构造表格
     */
    public MarkdownTable(List<String> headers, List<Alignment> alignments) {
        this();
        this.headers = headers;
        this.alignments = alignments;
    }

    public static MarkdownTable fromTableView(ITableView tableView) {
        return TableViewToMarkdownTableConverter.INSTANCE.convertToMarkdownTable(tableView);
    }

    /**
     * 设置指定列的对齐方式
     *
     * @param columnIndex 列索引（0-based）
     * @param alignment   对齐方式
     * @return 当前对象，支持链式调用
     */
    public MarkdownTable colAlign(int columnIndex, Alignment alignment) {
        if (this.alignments == null)
            this.alignments = new ArrayList<>();
        CollectionHelper.set(this.alignments, columnIndex, alignment);
        return this;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public void setAlignments(List<Alignment> alignments) {
        this.alignments = alignments;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    /**
     * 添加一行数据
     *
     * @param data 数据列表
     * @return 当前对象，支持链式调用
     */
    public MarkdownTable addRow(List<String> data) {
        if (this.rows == null) {
            this.rows = new ArrayList<>();
        }
        this.rows.add(data);
        return this;
    }

    /**
     * 生成Markdown表格字符串
     *
     * @return Markdown格式的表格字符串
     */
    public String toMarkdown() {
        if (getColumnCount() == 0)
            return "\n| (empty) |\n|------|\n";

        StringBuilder sb = new StringBuilder();

        generateHeaderRow(sb);
        sb.append("\n");

        // 分隔线
        generateSeparatorRow(sb);
        sb.append("\n");

        // 数据行
        for (int i = 0; i < rows.size(); i++) {
            generateDataRow(rows.get(i), sb);
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 生成表头行
     */
    private void generateHeaderRow(StringBuilder sb) {
        sb.append("| ");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(escapeCell(headers.get(i)));
        }
        sb.append(" |");
    }

    /**
     * 生成分隔线
     */
    private void generateSeparatorRow(StringBuilder sb) {
        sb.append("|");
        for (int i = 0; i < headers.size(); i++) {
            Alignment alignment = i < alignments.size() ? alignments.get(i) : Alignment.LEFT;
            if (i > 0) {
                sb.append("|");
            }
            sb.append(generateAlignmentSeparator(alignment));
        }
        sb.append("|");
    }

    /**
     * 生成对齐方式分隔符
     */
    private String generateAlignmentSeparator(Alignment alignment) {
        switch (alignment) {
            case LEFT:
                return ":---";
            case CENTER:
                return ":---:";
            case RIGHT:
                return "---:";
            default:
                return "---";
        }
    }

    /**
     * 生成数据行
     */
    private void generateDataRow(List<String> row, StringBuilder sb) {
        sb.append("| ");
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            String cell = i < row.size() ? row.get(i) : "";
            if (cell == null) cell = "";
            sb.append(escapeCell(cell));
        }
        sb.append(" |");
    }

    /**
     * 转义单元格内容
     * 对Markdown特殊字符进行转义，避免破坏表格结构
     */
    private String escapeCell(String cell) {
        return MarkdownHelper.escapeCell(cell);
    }

    // ========== Getter和Setter方法 ==========

    /**
     * 获取列数
     */
    @JsonIgnore
    public int getColumnCount() {
        return headers != null ? headers.size() : 0;
    }

    /**
     * 获取行数（不包括表头）
     */
    @JsonIgnore
    public int getRowCount() {
        return rows != null ? rows.size() : 0;
    }

    /**
     * 获取表头列表
     */
    public List<String> getHeaders() {
        return headers;
    }

    /**
     * 获取对齐方式列表
     */
    public List<Alignment> getAlignments() {
        return alignments;
    }

    /**
     * 获取指定列的表头
     *
     * @param columnIndex 列索引
     */
    public String getHeader(int columnIndex) {
        return CollectionHelper.get(this.headers, columnIndex);
    }

    /**
     * 获取指定列的对齐方式
     *
     * @param columnIndex 列索引
     */
    public Alignment getAlignment(int columnIndex) {
        return CollectionHelper.get(this.alignments, columnIndex);
    }

    /**
     * 获取所有数据行
     */
    public List<List<String>> getRows() {
        return rows;
    }

    /**
     * 获取指定单元格的值
     *
     * @param row 行索引（0-based）
     * @param col 列索引（0-based）
     */
    public String getCell(int row, int col) {
        List<String> data = CollectionHelper.get(this.rows, row);
        return CollectionHelper.get(data, col);
    }

    /**
     * 设置指定单元格的值
     *
     * @param row   行索引
     * @param col   列索引
     * @param value 新值
     */
    public void setCell(int row, int col, String value) {
        if (row < 0 || row >= rows.size() || col < 0 || col >= headers.size()) {
            throw new IndexOutOfBoundsException();
        }
        rows.get(row).set(col, value);
    }

    @Override
    public String toString() {
        return toMarkdown();
    }

    /**
     * 转换为BaseTable对象
     *
     * @return BaseTable实例
     */
    public BaseTable toBaseTable() {
        BaseTable table = new BaseTable();

        // 添加表头行
        if (headers != null && !headers.isEmpty()) {
            BaseRow headerRow = new BaseRow();
            for (int i = 0; i < headers.size(); i++) {
                BaseCell cell = new BaseCell();
                cell.setValue(headers.get(i));
                headerRow.internalAddCell(cell);
            }
            table.addRow(headerRow);
        }

        // 添加数据行
        if (rows != null && !rows.isEmpty()) {
            for (List<String> rowData : rows) {
                BaseRow row = new BaseRow();
                for (int i = 0, n = Math.max(rowData.size(), headers.size()); i < n; i++) {
                    BaseCell cell = new BaseCell();
                    if (i < rowData.size()) {
                        String value = rowData.get(i);
                        cell.setValue(value);
                    }
                }
                table.addRow(row);
            }
        }

        return table;
    }
}