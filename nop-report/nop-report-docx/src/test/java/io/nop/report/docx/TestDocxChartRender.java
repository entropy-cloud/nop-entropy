package io.nop.report.docx;

import io.nop.chart.report.ExcelChartImageProvider;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.excel.chart.constants.ChartType;
import io.nop.excel.chart.model.ChartPlotAreaModel;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.chart.model.ChartSeriesModel;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.renderer.ChartImageProviderRegistry;
import io.nop.ooxml.common.OfficeConstants;
import io.nop.ooxml.docx.WordTemplate;
import io.nop.ooxml.docx.model.WordOfficePackage;
import io.nop.office.doc.model.WordTable;
import io.nop.report.docx.parse.XptWordTemplateParser;
import io.nop.report.docx.support.WordTableToExcelTableTransformer;
import io.nop.report.core.build.ExcelToXptModelTransformer;
import io.nop.report.core.build.XptModelInitializer;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * docx chart rendering (plan 2366 Phase 2 / D49-D50): charts injected
 * programmatically into the xpt model render as a drawing paragraph after the
 * w:tbl, with media parts registered on the generation-scope ofcPkg copy
 * (WordTemplate.generateToDir serializes exactly that copy). Zero-change
 * regression: the chart-less template emits no drawing regardless of provider
 * presence.
 */
public class TestDocxChartRender extends JunitBaseTestCase {

    @AfterEach
    void cleanup() {
        ChartImageProviderRegistry.unregister();
        if (detachedContainer != null)
            BeanContainer.registerInstance(detachedContainer);
        detachedContainer = null;
    }

    private io.nop.api.core.ioc.IBeanContainer detachedContainer;

    /**
     * Detach the IoC container so the registry's BeanContainer fallback misses
     * (simulating provider absence); restored in cleanup.
     */
    private void detachContainer() {
        detachedContainer = BeanContainer.isInitialized() ? BeanContainer.instance() : null;
        BeanContainer.registerInstance(null);
    }

    private WordTemplate parseTemplate() {
        return new XptWordTemplateParser().parseFromResource(getResource("/test/test-word-report.docx"));
    }

    @Test
    public void testTemplateWithoutChartsHasNoDrawing() {
        WordTemplate tpl = parseTemplate();
        WordOfficePackage pkg = new WordOfficePackage();
        java.io.File file = generate(tpl, pkg);

        String docXml = zipEntry(file, "word/document.xml");
        assertFalse(docXml.contains("w:drawing"), "chart-less template emits no drawing");
        assertTrue(docXml.contains("w:tbl"), "table pipeline still rendered");
    }

    @Test
    public void testProviderAbsentSkipsSilently() {
        ChartImageProviderRegistry.unregister();
        detachContainer(); // BeanContainer fallback must also miss (D42 double-null)
        WordTemplate tpl = parseTemplate();
        WordOfficePackage pkg = new WordOfficePackage();
        java.io.File file = generate(tpl, pkg);
        assertFalse(zipEntry(file, "word/document.xml").contains("w:drawing"),
                "provider absent -> no drawing (D43)");
    }

    @Test
    public void testInjectedChartRendersDrawingAndMedia() {
        ChartImageProviderRegistry.register(new ExcelChartImageProvider());

        ExcelSheet sheet = buildSheetWithChart();
        io.nop.report.docx.renderer.XptWordTableRenderer renderer =
                new io.nop.report.docx.renderer.XptWordTableRenderer(sheet);

        WordOfficePackage pkg = new WordOfficePackage();
        // a parsed docx template always carries the rels part; a fresh package must
        // create it explicitly before addImage
        pkg.makeRels("word/_rels/document.xml.rels");
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue(null, OfficeConstants.VAR_OFC_PKG, pkg);

        java.io.StringWriter out = new java.io.StringWriter();
        try {
            renderer.generateToWriter(out, scope);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        String xml = out.toString();

        assertTrue(xml.contains("w:drawing"), "chart drawing paragraph emitted: " + xml);
        assertTrue(xml.contains("r:embed"), "drawing references the image relationship");
        assertTrue(xml.contains("wp:inline"), "inline drawing after the table");
        // namespace self-containment (closure audit Major): the fragment must carry
        // its own xmlns declarations — template document.xml roots lack a/pic
        assertTrue(xml.contains("xmlns:pic=") && xml.contains("xmlns:a=") && xml.contains("xmlns:wp="),
                "drawing fragment declares wp/a/pic namespaces inline");
        // the fragment (plus a w-rooted wrapper) must parse as well-formed XML
        XNode.parse("<w:p xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + xml + "</w:p>");

        int[] media = {0};
        pkg.forEachImage(r -> media[0]++);
        assertTrue(media[0] >= 1, "png registered as media part");

        String embedMarker = "r:embed=\"";
        int at = xml.indexOf(embedMarker);
        assertTrue(at > 0, "r:embed attribute present");
        String rId = xml.substring(at + embedMarker.length(), xml.indexOf('\"', at + embedMarker.length()));
        assertNotNull(pkg.getRels("word/_rels/document.xml.rels").getRelationship(rId),
                "relationship " + rId + " registered in document rels");
    }

    private String zipEntry(java.io.File docx, String path) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(docx)) {
            java.util.zip.ZipEntry entry = zip.getEntry(path);
            assertNotNull(entry, path + " present in docx");
            java.io.InputStream in = zip.getInputStream(entry);
            byte[] bytes = in.readAllBytes();
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("failed to read " + path + " from " + docx, e);
        }
    }

    private java.io.File generate(WordTemplate tpl, WordOfficePackage pkg) {
        IEvalScope scope = XLang.newEvalScope();
        scope.setLocalValue("data", java.util.Arrays.asList(
                java.util.Map.of("name", "a", "amount", 100),
                java.util.Map.of("name", "b", "amount", 200)));
        if (pkg != null)
            scope.setLocalValue(null, OfficeConstants.VAR_OFC_PKG, pkg);
        java.io.File file = new java.io.File("target/docx-chart-" + System.nanoTime() + ".docx");
        tpl.generateToFile(file, scope);
        return file;
    }

    /**
     * Programmatic xpt model with a complete bar chart (type+plotArea+series:
     * ExpandedSheetChartGenerator silently drops incomplete charts); data refs point
     * at the table's own numeric cells. Wiring mirrors compileTable's public
     * transformation chain (R2 F2/F3).
     */
    private ExcelSheet buildSheetWithChart() {
        String tbl = "<w:tbl xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:tr><w:tc><w:p><w:r><w:t>Item</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>Amount</w:t></w:r></w:p></w:tc></w:tr>"
                + "<w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>10</w:t></w:r></w:p></w:tc></w:tr>"
                + "<w:tr><w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc>"
                + "<w:tc><w:p><w:r><w:t>20</w:t></w:r></w:p></w:tc></w:tr>"
                + "</w:tbl>";
        XNode tblNode = XNode.parse(tbl);

        // XptWordTableParser (not the base parser) attaches VAR_XPT_NODE to rows/cells
        io.nop.report.docx.parse.XptWordTableParser parser =
                new io.nop.report.docx.parse.XptWordTableParser(XLang.newCompileTool());
        WordTable wordTable = parser.parseTable(tblNode);
        io.nop.excel.model.ExcelTable table = WordTableToExcelTableTransformer.transform(wordTable);
        ExcelToXptModelTransformer.INSTANCE.transformTable(table);

        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("WordTable");
        sheet.setTable(table);
        sheet.makeModel().prop_set(ReportDocxConstants.VAR_XPT_NODE, tblNode);
        new XptModelInitializer(XLang.newCompileTool()).buildSheetModel(sheet);

        sheet.setCharts(new ArrayList<>(List.of(barChart())));
        return sheet;
    }

    private ExcelChartModel barChart() {
        ExcelChartModel model = new ExcelChartModel();
        model.setType(ChartType.BAR);
        ChartPlotAreaModel plotArea = new ChartPlotAreaModel();
        ChartSeriesModel series = new ChartSeriesModel();
        series.setName("s1");
        series.setCatCellRef("A2:A3");
        series.setDataCellRef("B2:B3");
        plotArea.setSeriesList(io.nop.commons.collections.KeyedList.fromList(
                new ArrayList<>(List.of(series)), s -> String.valueOf(s.getIndex())));
        model.setPlotArea(plotArea);
        model.setWidth(400.0);
        model.setHeight(300.0);
        return model;
    }
}
