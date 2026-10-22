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

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * RTF 渲染器（基础表格：全表统一细边框、单元格文本 unicode 转义；富样式为已知限制）。
 * 合并区域被覆盖单元格输出空。
 */
public class RtfReportRendererFactory extends SingleSheetTableRenderer {

    @Override
    protected void generateSheet(IExcelTable table, Writer out, IEvalContext context) throws IOException {
        out.write("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Song;}}\n");
        int cn = table.getColCount();
        // cellx 定义（每列边界，1440 twips 等宽）
        StringBuilder cellx = new StringBuilder();
        for (int c = 0; c <= cn; c++)
            cellx.append("\\cellx").append(c * 1440);
        for (int r = 0, rn = table.getRowCount(); r < rn; r++) {
            out.write("\\trowd\\trgaph108" + cellx + "\n");
            List<? extends ICellView> cells = table.getRow(r).getCells();
            for (int c = 0; c < cn; c++) {
                ICellView cell = c < cells.size() ? cells.get(c) : null;
                out.write("\\clbrdrt\\brdrs\\brdrw10\\clbrdrl\\brdrs\\brdrw10\\clbrdrb\\brdrs\\brdrw10\\clbrdrr\\brdrs\\brdrw10\\cell");
                out.write(escapeRtf(cell == null ? "" : safeText(cell)));
                out.write("\\cell\n");
            }
            out.write("\\row\n");
        }
        out.write("}\n");
    }

    static String safeText(ICellView cell) {
        String text = cell.getText();
        return text == null ? "" : text;
    }

    static String escapeRtf(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '{' || c == '}')
                sb.append('\\').append(c);
            else if (c > 127)
                sb.append("\\u").append((int) c).append('?');
            else
                sb.append(c);
        }
        return sb.toString();
    }
}
