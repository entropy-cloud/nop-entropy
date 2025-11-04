/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table.html;

import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.model.table.ICellView;
import io.nop.core.model.table.IColumnConfig;
import io.nop.core.model.table.IRowView;
import io.nop.core.model.table.ITableView;
import io.nop.core.resource.tpl.ITextTemplateOutput;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;

public class HtmlTableOutput implements ITextTemplateOutput {
    static final int POS_THEAD = 1;
    static final int POS_TBODY = 2;
    static final int POS_TFOOT = 3;

    private final ITableView table;
    private String tableClass;
    private String rowClass;
    private String cellClass;
    private String sideCellClass;
    private String cornerCellClass;
    private String scopeCssPrefix = "cls-";
    private String hiddenClass;
    private String colClass;

    private Double defaultHeight;
    private Double defaultWidth;
    private boolean disableStyle; // 某些安全限制下不允许设置style，只能使用class
    private boolean[] colHidden;

    public HtmlTableOutput(ITableView table, String themeCssPrefix) {
        this.table = table;
        this.setThemeCssPrefix(themeCssPrefix);
    }

    public void setDisableStyle(boolean disableStyle) {
        this.disableStyle = disableStyle;
    }

    public HtmlTableOutput disableStyle(boolean disableStyle) {
        setDisableStyle(disableStyle);
        return this;
    }

    public Double getDefaultHeight() {
        return defaultHeight;
    }

    public void setDefaultHeight(Double defaultHeight) {
        this.defaultHeight = defaultHeight;
    }

    public Double getDefaultWidth() {
        return defaultWidth;
    }

    public void setDefaultWidth(Double defaultWidth) {
        this.defaultWidth = defaultWidth;
    }

    public HtmlTableOutput(ITableView table) {
        this(table, "xui-");
    }

    public void setThemeCssPrefix(String cssPrefix) {
        this.tableClass = cssPrefix + "table";
        rowClass = cssPrefix + "row";
        cellClass = cssPrefix + "cell";
        sideCellClass = cellClass + " side";
        cornerCellClass = cellClass + " corner";
        this.hiddenClass = cssPrefix + "hidden";
        this.colClass = cssPrefix + "col";
    }

    public void setScopeCssPrefix(String scopeCssPrefix) {
        this.scopeCssPrefix = scopeCssPrefix;
    }


    public static String toHtml(ITableView table) {
        return new HtmlTableOutput(table).generateText(DisabledEvalScope.INSTANCE);
    }

    String styleClass(String baseClass, String styleId) {
        if (styleId == null)
            return baseClass;

        return baseClass + " " + scopeCssPrefix + styleId;
    }

    String rowClass(String styleId, boolean hidden) {
        if (hidden)
            return styleClass(rowClass, styleId) + " " + hiddenClass;
        return styleClass(rowClass, styleId);
    }

    void writeCell(Writer out, String cellClass, ICellView cell, int colIndex, IEvalContext context) throws IOException {
        if (colHidden[colIndex]) {
            cellClass += " " + hiddenClass;
        }
        if (cell == null) {
            out.write("<td ");
            if (!disableStyle)
                writeAttr(out, "class", cellClass);
            out.write("></td>");
            return;
        }
        if (cell.isProxyCell())
            return;

        out.write("<td ");
        writeAttr(out, "id", cell.getId());

        if (!disableStyle)
            writeAttr(out, "class", cellClass(styleClass(cellClass, cell.getStyleId()), cell));

        if (cell.getMergeAcross() > 0) {
            writeAttr(out, "colspan", cell.getColSpan());
        }

        if (cell.getMergeDown() > 0) {
            writeAttr(out, "rowspan", cell.getRowSpan());
        }

        out.write(">");

        String linkUrl = cell.getLinkUrl();
        if (StringHelper.isEmpty(linkUrl)) {
            String text = cell.getText();
            if (text != null)
                out.write(StringHelper.escapeHtml(text));
        } else {
            out.write("<a href=\"");
            StringHelper.escapeXmlAttrTo(linkUrl, out);
            out.write("\">");
            String text = cell.getText();
            if (text != null)
                out.write(StringHelper.escapeHtml(text));
            out.write("</a>");
        }
        out.write("</td>");
    }

    private String cellClass(String className, ICellView cell) {
        Object value = cell.getValue();
        if (value instanceof Number) {
            if (!StringHelper.isEmpty(className)) {
                return "xpt-cell-num " + className;
            } else {
                return "xpt-cell-num";
            }
        }
        return className;
    }

    void writeProps(Writer out, Map<String, Object> props) throws IOException {
        if (props == null)
            return;

        for (Map.Entry<String, Object> entry : props.entrySet()) {
            writeAttr(out, "data-" + entry.getKey(), entry.getValue());
        }
    }

    void writeAttr(Writer out, String name, Object value) throws IOException {
        if (name != null && value != null) {
            out.write(" ");
            out.write(name);
            out.write("=\"");
            out.write(StringHelper.escapeXmlAttr(value.toString()));
            out.write("\"");
        }
    }

    void writeAttr(Writer out, String name, int value) throws IOException {
        if (name != null) {
            out.write(" ");
            out.write(name);
            out.write("=\"");
            out.write(String.valueOf(value));
            out.write("\"");
        }
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {
        out.write("<table ");
        String tableId = table.getId();
        writeAttr(out, "id", tableId);

        if (!disableStyle)
            writeAttr(out, "class", styleClass(tableClass, table.getStyleId()));

        if (table.getSideCount() > 0)
            writeAttr(out, "data-side-count", table.getSideCount());

        out.write(">\n");
        out.write("<colgroup>");
        writeCols(out);
        out.write("</colgroup>");
        out.write("\n<thead>");
        writeThead(out, context);
        out.write("\n</thead>");
        out.write("\n<tbody>");
        writeTbody(out, context);
        out.write("\n</tbody>");
        out.write("\n<tfoot>");
        writeTfoot(out, context);
        out.write("</tfoot>");
        out.write("\n</table>\n");
    }

    void writeCols(Writer out) throws IOException {
        this.colHidden = new boolean[table.getColCount()];

        for (int index = 0, n = table.getColCount(); index < n; index++) {
            IColumnConfig colType = table.getCol(index);
            out.write("<col ");

            if (colType != null && colType.isHidden()) {
                writeAttr(out, "hidden", "true");
                colHidden[index] = true;
            }
            Double width = null;
            if (colType != null) {
                width = colType.getWidth();
            }

            if (width == null)
                width = defaultWidth;

            if (width != null && !disableStyle) {
                writeAttr(out, "style", "width:" + ptValue(width));
            }

            out.write(" />");
        }
    }

    String ptValue(Double value) {
        if (value == null)
            return null;
        return value + "pt";
    }

    void writeThead(Writer out, IEvalContext context) throws IOException {
        for (int i = 0, n = table.getHeaderCount(); i < n; i++) {
            IRowView row = table.getRow(i);
            writeRow(out, row, POS_THEAD, context);
        }
    }

    void writeTbody(Writer out, IEvalContext context) throws IOException {
        for (int i = table.getHeaderCount(), n = table.getRowCount() - table.getFooterCount(); i < n; i++) {
            IRowView row = table.getRow(i);
            writeRow(out, row, POS_TBODY, context);
        }
    }

    void writeTfoot(Writer out, IEvalContext context) throws IOException {
        for (int i = table.getRowCount() - table.getFooterCount(), n = table.getRowCount(); i < n; i++) {
            IRowView row = table.getRow(i);
            writeRow(out, row, POS_TFOOT, context);
        }
    }

    void writeRow(Writer out, IRowView row, int pos, IEvalContext context) throws IOException {
        out.write("\n<tr ");
        writeAttr(out, "id", row.getId());

        if(!disableStyle)
            writeAttr(out, "class", rowClass(row.getStyleId(), row.isHidden()));

        Double height = row.getHeight();
        if (height == null)
            height = defaultHeight;
        if (height != null && !disableStyle) {
            out.write(" style=\"height:" + ptValue(height) + "\"");
        }

        out.write(">");

        int sideCount = table.getSideCount();

        int i = 0;
        Iterator<? extends ICellView> it = row.iterator();
        while (it.hasNext()) {
            ICellView cell = it.next();
            String cellClass = getCellClass(i < sideCount, pos);
            writeCell(out, cellClass, cell, i, context);
            i++;
        }

        out.write("</tr>");
    }

    private String getCellClass(boolean side, int pos) {
        if (side) {
            if (pos == POS_THEAD) {
                return cornerCellClass;
            } else {
                return sideCellClass;
            }
        }
        return cellClass;
    }
}