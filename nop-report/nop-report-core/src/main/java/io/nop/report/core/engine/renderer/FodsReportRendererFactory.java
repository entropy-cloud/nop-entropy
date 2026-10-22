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
 * Flat ODF 渲染器（renderType=fods，.fods 单文件 XML）。
 * LibreOffice de facto 兼容——非 ODF 1.2 规范性打包（zip ODS），已知限制。
 * 单元格取显示文本；合并单元格保留 table:number-columns/rows-spanned。
 */
public class FodsReportRendererFactory extends SingleSheetTableRenderer {

    @Override
    protected void generateSheet(IExcelTable table, Writer out, IEvalContext context) throws IOException {
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        out.write("<office:document xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\"");
        out.write(" xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\"");
        out.write(" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\"");
        out.write(" xmlns:of=\"urn:oasis:names:tc:opendocument:xmlns:of:1.2\"");
        out.write(" office:version=\"1.2\" office:mimetype=\"application/vnd.oasis.opendocument.spreadsheet\">\n");
        out.write("<office:body><office:spreadsheet>\n");
        out.write("<table:table table:name=\"Report\">\n");
        for (int r = 0, rn = table.getRowCount(); r < rn; r++) {
            out.write("<table:table-row>\n");
            List<? extends ICellView> cells = table.getRow(r).getCells();
            for (int c = 0, cn = table.getColCount(); c < cn; c++) {
                ICellView cell = c < cells.size() ? cells.get(c) : null;
                String text = cell == null ? "" : safeText(cell);
                StringBuilder attrs = new StringBuilder();
                if (cell != null && cell.getMergeAcross() > 0)
                    attrs.append(" table:number-columns-spanned=\"").append(cell.getMergeAcross() + 1).append('"');
                if (cell != null && cell.getMergeDown() > 0)
                    attrs.append(" table:number-rows-spanned=\"").append(cell.getMergeDown() + 1).append('"');
                out.write("<table:table-cell" + attrs + " office:value-type=\"string\"><text:p>");
                escapeXml(text, out);
                out.write("</text:p></table:table-cell>\n");
            }
            out.write("</table:table-row>\n");
        }
        out.write("</table:table>\n</office:spreadsheet></office:body>\n</office:document>\n");
    }

    static String safeText(ICellView cell) {
        String text = cell.getText();
        return text == null ? "" : text;
    }

    static void escapeXml(String text, Writer out) throws IOException {
        for (int i = 0, n = text.length(); i < n; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '<':
                    out.write("&lt;");
                    break;
                case '>':
                    out.write("&gt;");
                    break;
                case '&':
                    out.write("&amp;");
                    break;
                default:
                    out.write(c);
            }
        }
    }
}
