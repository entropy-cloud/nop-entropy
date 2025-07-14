package io.nop.report.docx.renderer;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectXmlHandler;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.util.UnitsHelper;
import io.nop.report.core.engine.ExpandedSheetGenerator;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.engine.XptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import static io.nop.commons.util.objects.ValueWithLocation.vl;
import static io.nop.report.docx.ReportDocxConstants.VAR_XPT_NODE;
import static io.nop.report.docx.parse.XptWordTableParser.DEFAULT_WIDTH;

public class XptWordTableRenderer implements ITextTemplateOutput {
    private final ExcelSheet xptModel;

    public XptWordTableRenderer(ExcelSheet xptModel) {
        this.xptModel = xptModel;
    }

    @Override
    public void generateToWriter(Writer out, IEvalContext context) throws IOException {
        XptRuntime xptRt = new XptRuntime(context.getEvalScope());
        ExcelWorkbook wk = new ExcelWorkbook();
        xptRt.setWorkbook(wk);

        ExpandedSheet sheet = new ExpandedSheetGenerator(wk).generateSheet(xptModel, xptRt, new HashMap<>());
        renderExpandedSheet(out, sheet, xptRt);
    }

    protected void renderExpandedSheet(Writer out, ExpandedSheet sheet, IXptRuntime xptRt) throws IOException {

        CollectXmlHandler handler = new CollectXmlHandler(out).indentRoot(false).indent(true);

        ExpandedTable table = sheet.getTable();
        handler.beginNode("w:tbl");
        XNode node = (XNode) sheet.getModel().prop_get(VAR_XPT_NODE);
        XNode tblPr = node.childByTag("w:tblPr");
        if (tblPr != null) {
            tblPr.process(handler);
        }
        handler.beginNode("w:tblGrid");
        for (int i = 0, n = table.getColCount(); i < n; i++) {
            int width = UnitsHelper.pointsToTwips(table.getColWidth(i, DEFAULT_WIDTH));
            handler.simpleNode(null, "w:gridCol", Map.of("w:w", vl(null, width)));
        }
        handler.endNode("w:tblGrid");

        for (int i = 0, n = table.getRowCount(); i < n; i++) {
            ExpandedRow row = table.getRow(i);
            XNode tr = (XNode) row.getModel().prop_get(VAR_XPT_NODE);
            XNode trPr = tr.childByTag("w:trPr");
            handler.beginNode(tr.getLocation(), tr.getTagName(), tr.attrValueLocs());
            if (trPr != null)
                trPr.process(handler);
            renderCells(row, handler, xptRt);
            handler.endNode(tr.getTagName());
        }
        handler.endNode("w:tbl");
    }

    protected void renderCells(ExpandedRow row, IXNodeHandler handler, IXptRuntime xptRt) {
        for (ExpandedCell cell : row) {
            if (cell.isProxyCell()) {
                renderProxyCell(cell, handler);
            } else {
                renderCell(cell, handler, xptRt);
            }
        }
    }

    protected void renderProxyCell(ExpandedCell cell, IXNodeHandler handler) {
        if (cell.getColOffset() > 0)
            return;

        ExpandedCell realCell = cell.getRealCell();
        XNode tc = (XNode) realCell.getModel().prop_get(VAR_XPT_NODE);
        XNode tcPr = tc.childByTag("w:tcPr");
        XNode p = tc.childByTag("w:p");

        handler.beginNode("w:tc");
        handler.beginNode("w:tcPr");

        if (realCell.getMergeAcross() > 0) {
            handler.simpleNode(null, "w:gridSpan", Map.of("w:val", ValueWithLocation.of(null, realCell.getMergeAcross() + 1)));
        }

        if (cell.getRowOffset() > 0)
            handler.simpleNode("w:vMerge");


        int width = realCell.getCellWidthTwips(DEFAULT_WIDTH);
        Map<String, ValueWithLocation> attrs = Map.of("w:w", ValueWithLocation.of(null, width),
                "w:type", ValueWithLocation.of(null, "dxa"));
        handler.simpleNode(null, "w:tcW", attrs);

        if (tcPr != null) {
            for (XNode child : tcPr.getChildren()) {
                String tagName = child.getTagName();
                if (tagName.equals("w:gridSpan"))
                    continue;
                if (tagName.equals("w:vMerge"))
                    continue;
                if (tagName.equals("w:tcW"))
                    continue;
                child.process(handler);
            }
        }

        handler.endNode("w:tcPr");
        handler.beginNode("w:p");
        handler.beginNode("w:pPr");
        handler.endNode("w:pPr");
        handler.endNode("w:p");
        handler.endNode("w:tc");
    }

    protected void renderCell(ExpandedCell cell, IXNodeHandler handler, IXptRuntime xptRt) {
        XNode tc = (XNode) cell.getModel().prop_get(VAR_XPT_NODE);
        XNode tcPr = tc.childByTag("w:tcPr");

        XNode p = tc.childByTag("w:p");
        XNode pPr = p.childByTag("w:pPr");
        XNode r = p.childByTag("w:r");
        XNode rPr = r == null ? null : r.childByTag("w:rPr");

        handler.beginNode("w:tc");
        handler.beginNode("w:tcPr");

        if (cell.getMergeAcross() > 0) {
            handler.simpleNode(null, "w:gridSpan", Map.of("w:val", ValueWithLocation.of(null, cell.getMergeAcross() + 1)));
        }

        if (cell.getMergeDown() > 0)
            handler.simpleNode(null, "w:vMerge", Map.of("w:val", ValueWithLocation.of(null, "restart")));


        int width = cell.getCellWidthTwips(DEFAULT_WIDTH);
        Map<String, ValueWithLocation> attrs = Map.of("w:w", ValueWithLocation.of(null, width),
                "w:type", ValueWithLocation.of(null, "dxa"));
        handler.simpleNode(null, "w:tcW", attrs);

        if (tcPr != null) {
            for (XNode child : tcPr.getChildren()) {
                String tagName = child.getTagName();
                if (tagName.equals("w:gridSpan"))
                    continue;
                if (tagName.equals("w:vMerge"))
                    continue;
                if (tagName.equals("w:tcW"))
                    continue;
                child.process(handler);
            }
        }


        handler.endNode("w:tcPr");
        handler.beginNode("w:p");
        if (pPr != null)
            pPr.process(handler);
        handler.beginNode("w:r");
        if (rPr != null) {
            rPr.process(handler);
        }
        handler.beginNode("w:t");
        String text = cell.getText();
        handler.value(null, text);
        handler.endNode("w:t");
        handler.endNode("w:r");
        handler.endNode("w:p");
        handler.endNode("w:tc");
    }
}
