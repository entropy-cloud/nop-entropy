package io.nop.report.docx.renderer;

import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.handler.CollectXmlHandler;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelImage;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.renderer.ChartImageProviderRegistry;
import io.nop.excel.renderer.IChartImageProvider;
import io.nop.excel.util.UnitsHelper;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.ooxml.common.model.OfficeRelationship;
import io.nop.report.core.engine.ExpandedSheetGenerator;
import io.nop.report.core.engine.IXptRuntime;
import io.nop.report.core.engine.XptRuntime;
import io.nop.report.core.model.ExpandedCell;
import io.nop.report.core.model.ExpandedRow;
import io.nop.report.core.model.ExpandedSheet;
import io.nop.report.core.model.ExpandedTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.commons.util.objects.ValueWithLocation.vl;
import static io.nop.report.docx.ReportDocxConstants.VAR_XPT_NODE;
import static io.nop.report.docx.parse.XptWordTableParser.DEFAULT_WIDTH;

public class XptWordTableRenderer implements ITextTemplateOutput {
    private static final Logger LOG = LoggerFactory.getLogger(XptWordTableRenderer.class);
    private static final String WP_NS = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    private static final String A_NS = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final String PIC_NS = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    private static final String R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String PIC_URI = PIC_NS;

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
        renderExpandedSheet(out, sheet, xptRt, context);
    }

    protected void renderExpandedSheet(Writer out, ExpandedSheet sheet, IXptRuntime xptRt,
                                       IEvalContext context) throws IOException {

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
            handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "w:gridCol", Map.of("w:w", vl(null, width)));
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
        renderCharts(sheet, context, handler);
    }

    /**
     * plan 2366 D49/D50：表格后追加图表段落。生成期 package 从 eval scope 的
     * ofcPkg 取（WordTemplate.generateToDir 放入的副本，序列化的正是它）——直接
     * 单元调用 scope 无 ofcPkg 时 debug log 跳过（D43 同族显式裁定）。provider
     * 缺席或 sheet 无图表同样静默跳过；provider 异常上抛。
     */
    protected void renderCharts(ExpandedSheet sheet, IEvalContext context, CollectXmlHandler handler)
            throws IOException {
        IEvalScope scope = context.getEvalScope();
        Object pkgObj = scope == null ? null : scope.getValue(OfficeConstants.VAR_OFC_PKG);
        if (!(pkgObj instanceof WordOfficePackage)) {
            LOG.debug("nop.chart-report.skip-docx-chart: no ofcPkg in generation scope");
            return;
        }
        WordOfficePackage pkg = (WordOfficePackage) pkgObj;

        IChartImageProvider provider = ChartImageProviderRegistry.getProvider();
        if (provider == null || !sheet.hasChart())
            return;

        List<ExcelImage> charts = provider.renderCharts(sheet);
        if (charts == null || charts.isEmpty())
            return;

        int index = 1;
        for (ExcelImage chart : charts) {
            if (chart.getData() == null || chart.getData().isEmpty())
                continue;
            ByteArrayResource resource = new ByteArrayResource("/nop/chart/docx-chart-" + index + ".png",
                    chart.getData().toByteArray(), System.currentTimeMillis());
            OfficeRelationship rel = pkg.addImage("png", resource);
            emitChartParagraph(handler, chart, rel.getId(), index);
            index++;
        }
    }

    /**
     * D50：表后内联图表段落（w:p > w:r > w:drawing > wp:inline），extent 单位
     * EMU（1pt = 12700 EMU）。wp/a/pic/r 命名空间在片段节点上内联声明——模板
     * document.xml 根不声明 a/pic 前缀，片段必须自含（closure audit 实证）。
     */
    private void emitChartParagraph(CollectXmlHandler handler, ExcelImage chart, String relId, int index) {
        long cx = Math.max(1, Math.round(chart.getWidth() * 12700));
        long cy = Math.max(1, Math.round(chart.getHeight() * 12700));

        handler.beginNode("w:p");
        handler.beginNode("w:r");
        handler.beginNode("w:drawing");

        Map<String, ValueWithLocation> attrs = new HashMap<>();
        attrs.put("distT", vl(null, 0));
        attrs.put("distB", vl(null, 0));
        attrs.put("distL", vl(null, 0));
        attrs.put("distR", vl(null, 0));
        attrs.put("xmlns:wp", vl(null, WP_NS));
        attrs.put("xmlns:r", vl(null, R_NS));
        handler.beginNode((io.nop.api.core.util.SourceLocation) null, "wp:inline", attrs);

        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "wp:extent", Map.of(
                "cx", vl(null, cx),
                "cy", vl(null, cy)));
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "wp:effectExtent", Map.of(
                "l", vl(null, 0), "t", vl(null, 0), "r", vl(null, 0), "b", vl(null, 0)));
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "wp:docPr", Map.of(
                "id", vl(null, index),
                "name", vl(null, "Chart" + index)));

        handler.beginNode((io.nop.api.core.util.SourceLocation) null, "a:graphic",
                Map.of("xmlns:a", vl(null, A_NS)));
        handler.beginNode((io.nop.api.core.util.SourceLocation) null, "a:graphicData",
                Map.of("uri", vl(null, PIC_URI)));
        handler.beginNode((io.nop.api.core.util.SourceLocation) null, "pic:pic",
                Map.of("xmlns:pic", vl(null, PIC_NS)));
        handler.beginNode("pic:nvPicPr");
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "pic:cNvPr", Map.of(
                "id", vl(null, index),
                "name", vl(null, "Chart" + index)));
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "pic:cNvPicPr", new HashMap<>());
        handler.endNode("pic:nvPicPr");
        handler.beginNode("pic:blipFill");
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "a:blip", Map.of("r:embed", vl(null, relId)));
        handler.beginNode("a:stretch");
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "a:fillRect", new HashMap<>());
        handler.endNode("a:stretch");
        handler.endNode("pic:blipFill");
        handler.beginNode("pic:spPr");
        handler.beginNode("a:xfrm");
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "a:off", Map.of("x", vl(null, 0), "y", vl(null, 0)));
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "a:ext", Map.of("cx", vl(null, cx), "cy", vl(null, cy)));
        handler.endNode("a:xfrm");
        handler.beginNode((io.nop.api.core.util.SourceLocation) null, "a:prstGeom", Map.of("prst", vl(null, "rect")));
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "a:avLst", new HashMap<>());
        handler.endNode("a:prstGeom");
        handler.endNode("pic:spPr");
        handler.endNode("pic:pic");
        handler.endNode("a:graphicData");
        handler.endNode("a:graphic");
        handler.endNode("wp:inline");
        handler.endNode("w:drawing");
        handler.endNode("w:r");
        handler.endNode("w:p");
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
            handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "w:gridSpan", Map.of("w:val", ValueWithLocation.of(null, realCell.getMergeAcross() + 1)));
        }

        if (cell.getRowOffset() > 0)
            handler.simpleNode("w:vMerge");


        int width = realCell.getCellWidthTwips(DEFAULT_WIDTH);
        Map<String, ValueWithLocation> attrs = Map.of("w:w", ValueWithLocation.of(null, width),
                "w:type", ValueWithLocation.of(null, "dxa"));
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "w:tcW", attrs);

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
        // 手工构造或损坏的docx模板中tc节点可能缺失w:p子节点，需要判空兜底
        XNode pPr = p == null ? null : p.childByTag("w:pPr");
        XNode r = p == null ? null : p.childByTag("w:r");
        XNode rPr = r == null ? null : r.childByTag("w:rPr");

        handler.beginNode("w:tc");
        handler.beginNode("w:tcPr");

        if (cell.getMergeAcross() > 0) {
            handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "w:gridSpan", Map.of("w:val", ValueWithLocation.of(null, cell.getMergeAcross() + 1)));
        }

        if (cell.getMergeDown() > 0)
            handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "w:vMerge", Map.of("w:val", ValueWithLocation.of(null, "restart")));


        int width = cell.getCellWidthTwips(DEFAULT_WIDTH);
        Map<String, ValueWithLocation> attrs = Map.of("w:w", ValueWithLocation.of(null, width),
                "w:type", ValueWithLocation.of(null, "dxa"));
        handler.simpleNode((io.nop.api.core.util.SourceLocation) null, "w:tcW", attrs);

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
