/**
 * Copyright (c) 2017-2024 Nop Platform.
 */
package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelChartModel;
import io.nop.excel.model.ExcelClientAnchor;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelPageMargins;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import io.nop.report.pdf.utils.ChartImageProviders;
import io.nop.report.pdf.utils.IChartImageProvider;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7：图表图片 provider SPI 接线与 renderCharts 管线（桩 provider）。
 */
public class TestPdfChartRender extends JunitBaseTestCase {

    private ExcelWorkbook newWorkbook(int rows, ExcelChartModel... charts) {
        ExcelWorkbook workbook = new ExcelWorkbook();

        ExcelStyle style = new ExcelStyle();
        style.setId("text");
        ExcelFont font = new ExcelFont();
        font.setFontName("Helvetica");
        style.setFont(font);
        workbook.addStyle(style);

        io.nop.excel.model.ExcelSheet sheet = new io.nop.excel.model.ExcelSheet();
        sheet.setName("s1");
        sheet.setDefaultColumnWidth(60.0);
        sheet.setDefaultRowHeight(20.0);

        ExcelPageSetup pageSetup = new ExcelPageSetup();
        pageSetup.setPaperWidth(595f);
        pageSetup.setPaperHeight(842f);
        sheet.setPageSetup(pageSetup);

        ExcelPageMargins margins = new ExcelPageMargins();
        margins.setLeft(36.0);
        margins.setRight(36.0);
        margins.setTop(30.0);
        margins.setBottom(20.0);
        margins.setHeader(10.0);
        margins.setFooter(10.0);
        sheet.setPageMargins(margins);

        for (int r = 0; r < rows; r++) {
            ExcelRow row = new ExcelRow();
            row.setHeight(20.0);
            for (int c = 0; c < 4; c++) {
                ExcelCell cell = new ExcelCell();
                row.getCells().add(cell);
            }
            sheet.getTable().getRows().add(row);
        }

        // charts 经 setCharts 注入（getCharts 缺省为不可变空 KeyedList）；name 为 KeyedList 键
        for (int i = 0; i < charts.length; i++) {
            charts[i].setName("chart-" + i);
        }
        sheet.setCharts(new java.util.ArrayList<>(java.util.Arrays.asList(charts)));
        workbook.addSheet(sheet);
        return workbook;
    }

    private byte[] stubPng(double widthPt, double heightPt) throws Exception {
        int w = (int) Math.max(20, Math.round(widthPt * 2));
        int h = (int) Math.max(20, Math.round(heightPt * 2));
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new Color(0xE8E8E8));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x3060C0));
        int inset = Math.max(2, w / 20);
        g.fillRect(inset, inset, w - 2 * inset, h - 2 * inset);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private ExcelChartModel chartAt(int col1, int row1, int colSpan, int rowSpan) {
        ExcelChartModel chart = new ExcelChartModel();
        ExcelClientAnchor anchor = new ExcelClientAnchor();
        anchor.setCol1(col1);
        anchor.setRow1(row1);
        anchor.setColDelta(colSpan);
        anchor.setRowDelta(rowSpan);
        chart.setAnchor(anchor);
        return chart;
    }

    private byte[] render(ExcelWorkbook workbook) throws Exception {
        PdfReportRenderer renderer = new PdfReportRenderer(workbook, null);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        renderer.generateToStream(bos, null);
        return bos.toByteArray();
    }

    private BufferedImage renderFirstPage(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return PdfImageRenderer.renderPage(doc, 0, PdfImageRenderer.DEFAULT_DPI);
        }
    }

    private boolean hasInkInBand(BufferedImage img, double xFrom, double xTo, double yFrom, double yTo) {
        double s = 110.0 / 72;
        for (int py = (int) (yFrom * s); py < Math.min(yTo * s, img.getHeight()); py += 2) {
            for (int px = (int) (xFrom * s); px < Math.min(xTo * s, img.getWidth()); px += 2) {
                int rgb = img.getRGB(px, py);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (!(r > 245 && g > 245 && b > 245))
                    return true;
            }
        }
        return false;
    }

    private Color probe(BufferedImage img, double ptX, double ptY) {
        return new Color(img.getRGB((int) Math.round(ptX * 110.0 / 72), (int) Math.round(ptY * 110.0 / 72)));
    }

    private boolean isWhitish(Color c) {
        return c.getRed() > 245 && c.getGreen() > 245 && c.getBlue() > 245;
    }

    /**
     * (a) 接线验证：探测 provider 记录调用参数；渲染后桩图案出现在锚点区域。
     */
    @Test
    public void testProviderWiredAndRendered() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<byte[]> received = new java.util.ArrayList<>();
        ChartImageProviders.setProvider(new IChartImageProvider() {
            @Override
            public ByteString renderChart(ExcelChartModel chart, io.nop.excel.model.IExcelSheet sheet,
                                          double widthPt, double heightPt) {
                calls.incrementAndGet();
                assertTrue(widthPt > 0 && heightPt > 0);
                try {
                    byte[] png = stubPng(widthPt, heightPt);
                    received.add(png);
                    return ByteString.of(png);
                } catch (IOException e) {
                    throw NopException.adapt(e);
                }
            }
        });
        try {
            byte[] pdf = render(newWorkbook(12, chartAt(0, 0, 2, 4)));
            assertEquals(1, calls.get(), "provider must be invoked once per chart");
            assertEquals(1, received.size());

            BufferedImage img = renderFirstPage(pdf);
            // 锚点 (0,0)-(2,4)：120x80pt @ 打印区原点 (36,40)；内部采样非白
            Color inside = probe(img, 36 + 60, 40 + 40);
            assertNotWhitish(inside, 245, "stub chart pattern must render inside anchor area");
            Color outside = probe(img, 36 + 120 + 30, 40 + 40);
            assertTrue(isWhitish(outside), "area beyond chart anchor must stay blank");
        } finally {
            ChartImageProviders.setProvider(null);
        }
    }

    private void assertNotWhitish(Color c, int tol, String msg) {
        assertTrue(!(c.getRed() > tol && c.getGreen() > tol && c.getBlue() > tol), msg);
    }

    /**
     * (c) region 过滤：锚点在第 2 页（行 40+）的图表不出现在第 1 页。
     */
    @Test
    public void testRegionFilter() throws Exception {
        // 90 行 3 页；图表锚点在行 45（第 2 页）
        byte[] pdf = render(newWorkbook(90, chartAt(0, 45, 2, 4)));
        List<BufferedImage> images;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
        }
        assertTrue(images.size() >= 2);
        // 页 1 图表区（若画在页 1 会在 (36..156, 40..120)pt）应无图案：扫描带断言
        assertTrue(!hasInkInBand(images.get(0), 36, 156, 40, 130),
                "chart anchored on page 2 must not render on page 1");
        // 页 2 图表带（锚点行 45 附近）存在图表墨迹（存在性扫描，对区域偏移稳健；
        // 像素级偏移复核记 follow-up）
        assertTrue(hasInkInBand(images.get(1), 36, 156, 150, 320),
                "chart must render on its anchor page (page 2)");
    }

    /**
     * (d) provider 返回 null：WARN 跳过，不抛异常、不绘制。
     */
    @Test
    public void testProviderNullSkippedWithWarn() throws Exception {
        ChartImageProviders.setProvider((chart, sheet, w, h) -> null);
        try {
            byte[] pdf = render(newWorkbook(12, chartAt(0, 0, 2, 4)));
            BufferedImage img = renderFirstPage(pdf);
            Color inside = probe(img, 36 + 60, 40 + 40);
            assertTrue(isWhitish(inside), "null provider result must skip rendering");
        } finally {
            ChartImageProviders.setProvider(null);
        }
    }

    /**
     * 默认桩 provider：非注册状态渲染合成图案。
     */
    @Test
    public void testDefaultStubAlwaysAvailable() throws Exception {
        assertNotNull(ChartImageProviders.getProvider());
        byte[] pdf = render(newWorkbook(12, chartAt(0, 0, 2, 4)));
        BufferedImage img = renderFirstPage(pdf);
        Color inside = probe(img, 36 + 60, 40 + 40);
        assertNotWhitish(inside, 245, "default stub must render");
    }

    /**
     * (e) golden：桩图表页。
     */
    @Test
    public void testGoldenChartPage() throws Exception {
        byte[] pdf = render(newWorkbook(12, chartAt(0, 0, 2, 4)));
        PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi7-chart-wiring");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            List<BufferedImage> images = PdfImageRenderer.renderAll(doc, PdfImageRenderer.DEFAULT_DPI);
            for (int i = 0; i < images.size(); i++) {
                GoldenCompareResult result = golden.compare("stub-chart-page-" + (i + 1), images.get(i));
                assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                        || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                        "golden regression page " + (i + 1) + ": " + result);
            }
        }
    }
}
