/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core.engine.renderer;

import io.nop.core.context.IEvalContext;
import io.nop.core.model.table.ICellView;
import io.nop.excel.model.IExcelTable;

import java.util.List;

import java.io.IOException;
import java.io.Writer;

/**
 * CSV 渲染器（RFC4180：含逗号/引号/换行的字段加引号，内部引号翻倍）。
 * 单元格取显示文本 getText()；合并区域被覆盖单元格输出空。
 */
public class CsvReportRendererFactory extends SingleSheetTableRenderer {

    @Override
    protected void generateSheet(IExcelTable table, Writer out, IEvalContext context) throws IOException {
        for (int r = 0, rn = table.getRowCount(); r < rn; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0, cn = table.getColCount(); c < cn; c++) {
                if (c > 0)
                    sb.append(',');
                sb.append(escape(csvValue(table, r, c)));
            }
            sb.append("\r\n");
            out.write(sb.toString());
        }
    }

    static String csvValue(IExcelTable table, int row, int col) {
        if (row >= table.getRowCount())
            return "";
        List<? extends ICellView> cells = table.getRow(row).getCells();
        if (col >= cells.size())
            return "";
        ICellView cell = cells.get(col);
        if (cell == null)
            return "";
        // 合并区域被覆盖单元格输出空（仅保留 top-left 值）；CSV 无 span 语义
        return text(cell);
    }

    private static String text(ICellView cell) {
        String text = cell.getText();
        return text == null ? "" : text;
    }

    static String escape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0)
            return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
