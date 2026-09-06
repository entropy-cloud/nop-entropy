package io.nop.report.pdf.renderer;

import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.pdf.font.FontManager;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2260 Phase 2：字体体系回归。
 *
 * 环境前提（设计决策，见ai-dev/design/nop-report/pdf-font-strategy.md）：
 * 运行/CI环境需存在系统CJK字体（macOS自带；Linux CI需安装fonts-wqy-microhei等），
 * 或在VFS的/fonts/default.ttf提供字体文件。
 *
 * 注意：本类不注入VFS字体（区别于TestPdfRenderDefects），验证系统字体目录
 * 自动发现与字形回退链路。
 */
public class TestPdfFontResolution extends JunitBaseTestCase {

    /**
     * FontManager是JVM级单例，同fork中先行测试类（如TestPdfRenderDefects）注入的
     * VFS字体会被init永久缓存。此处强制重置，保证本类验证的是"系统字体目录发现"链路
     */
    @BeforeAll
    static void resetFontManager() {
        FontManager.instance().resetForTesting();
    }

    private static boolean systemCjkAvailable() {
        try (PDDocument doc = new PDDocument()) {
            PDFont fallback = FontManager.instance().loadFallbackFont(doc);
            return fallback != null && FontManager.instance().canEncode(fallback, "中");
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 字形编码判定 ==========

    @Test
    public void testCanEncode() throws Exception {
        PDFont helvetica = new PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA);
        assertFalse(FontManager.instance().canEncode(helvetica, "中文"),
                "base-14 font cannot encode CJK");
        assertTrue(FontManager.instance().canEncode(helvetica, "english123"),
                "base-14 font encodes latin");

        Assumptions.assumeTrue(systemCjkAvailable(),
                "环境前提（pdf-font-strategy.md D3）：需要系统CJK字体或VFS字体");

        try (PDDocument doc = new PDDocument()) {
            java.io.File cjkFile = FontManager.instance().findDiscoveredFont("Arial Unicode");
            if (cjkFile == null) {
                // 找不到具名字体时通过回退解析拿到任一CJK字体
                PDFont fallback = FontManager.instance().loadFallbackFont(doc);
                assertTrue(fallback != null, "system should provide a CJK fallback font");
                assertTrue(FontManager.instance().canEncode(fallback, "中文"),
                        "fallback font should encode CJK");
            } else {
                PDFont font = PDType0Font.load(doc, cjkFile);
                assertTrue(FontManager.instance().canEncode(font, "中文"));
            }
        }
    }

    // ========== 系统字体目录发现 ==========

    @Test
    public void testSystemFontDirsDiscovered() {
        Assumptions.assumeTrue(systemCjkAvailable(),
                "环境前提（pdf-font-strategy.md D3）：需要系统CJK字体或VFS字体");
        assertTrue(!FontManager.instance().discoveredFontFiles().isEmpty(),
                "system font dirs should yield font files");
    }

    // ========== F1：中文报表无需手工配置即可导出 ==========

    /**
     * 具名CJK字体（宋体）：文件名未命中时经字形回退链路渲染中文
     */
    @Test
    public void testNamedCjkFontRendersChinese() throws Exception {
        Assumptions.assumeTrue(systemCjkAvailable(),
                "环境前提（pdf-font-strategy.md D3）：需要系统CJK字体或VFS字体");
        String text = renderChinese(newFontStyle("宋体"));
        // 系统字体的ToUnicode映射可能把"文"映射为康熙部首形(U+2F8C)，断言避开该字
        assertTrue(text.contains("测试") && text.contains("Chinese"),
                "Chinese text should be extracted: " + text);
    }

    /**
     * base-14字体名+中文文本：绘制时按字符回退到CJK字体，不再抛.notdef异常
     */
    @Test
    public void testBase14FontWithChineseFallsBack() throws Exception {
        Assumptions.assumeTrue(systemCjkAvailable(),
                "环境前提（pdf-font-strategy.md D3）：需要系统CJK字体或VFS字体");
        String text = renderChinese(newFontStyle("Helvetica"));
        assertTrue(text.contains("测试") && text.contains("Chinese"),
                "Chinese text should render via fallback: " + text);
    }

    /**
     * 无字体名（F8）：默认字体解析后经字形回退渲染中文
     */
    @Test
    public void testNullFontNameRendersChinese() throws Exception {
        Assumptions.assumeTrue(systemCjkAvailable(),
                "环境前提（pdf-font-strategy.md D3）：需要系统CJK字体或VFS字体");
        ExcelStyle style = new ExcelStyle();
        style.setId("nofont");
        String text = renderChinese(style);
        assertTrue(text.contains("测试") && text.contains("Chinese"),
                "Chinese text should render with null font name: " + text);
    }

    // ========== 夹具 ==========

    private ExcelStyle newFontStyle(String fontName) {
        ExcelStyle style = new ExcelStyle();
        style.setId("st");
        io.nop.excel.model.ExcelFont font = new io.nop.excel.model.ExcelFont();
        font.setFontName(fontName);
        font.setFontSize(10.0f);
        style.setFont(font);
        return style;
    }

    private String renderChinese(ExcelStyle style) throws Exception {
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = TestPdfRenderDefects.newSheet(wb, 400, 400);
        wb.addStyle(style);
        ExcelCell cell = new ExcelCell();
        cell.setValue("中文测试Chinese");
        cell.setStyleId(style.getId());
        sheet.getTable().setCell(0, 0, cell);

        byte[] pdf = TestPdfRenderDefects.render(wb);
        String text = TestPdfRenderDefects.normalize(TestPdfRenderDefects.allText(pdf));
        assertNotEquals(0, text.length(), "text should be extracted");
        return text;
    }
}
