package io.nop.excel.renderer;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.unittest.BaseTestCase;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelWorkbook;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SimpleHtmlReportRenderer 导出输出 golden 快照（按 testing.md「XPL Tag 输出 Golden JSON 快照」惯例）：
 * 正式测试 = 1-2 个关键字段断言 + 全结构 golden 比对；regenerateSnapshots 为 @Disabled 录入方法。
 */
public class TestSimpleHtmlReportRendererGolden extends BaseTestCase {

    static ExcelCell cell(Object value) {
        ExcelCell cell = new ExcelCell();
        cell.setValue(value);
        return cell;
    }

    static ExcelSheet buildSheet(String name) {
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName(name);
        // 标题行横向合并两列，第二行为数据
        ExcelCell title = cell("Title");
        title.setMergeAcross(1);
        sheet.getTable().setCell(0, 0, title);
        sheet.getTable().setCell(1, 0, cell("a"));
        sheet.getTable().setCell(1, 1, cell(3));
        return sheet;
    }

    static ExcelWorkbook buildWorkbook() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        workbook.addSheet(buildSheet("s1"));
        return workbook;
    }

    private String render(ExcelWorkbook workbook, IExcelSheetGenerator sheetGenerator, IEvalScope scope)
            throws Exception {
        StringWriter out = new StringWriter();
        new SimpleHtmlReportRendererFactory().buildRenderer(workbook, sheetGenerator)
                .generateToWriter(out, scope);
        return out.toString();
    }

    // 关键字段：默认 reportId 与 sheet 名；全结构：golden HTML 快照比对
    @Test
    public void testBasicWorkbookGolden() throws Exception {
        String html = render(buildWorkbook(), null, EvalExprProvider.newEvalScope());

        assertTrue(html.contains("<div id=\"xpt-report\">"), "default report id expected");
        assertTrue(html.contains("data-sheet-name=\"s1\""), "sheet name expected");

        assertEquals(attachmentText("simple-report-basic.html"), html);
    }

    // sheetGenerator 动态出多个 sheet 且 reportId 取自 scope 变量
    @Test
    public void testSheetGeneratorAndCustomReportIdGolden() throws Exception {
        IEvalScope scope = EvalExprProvider.newEvalScope();
        scope.setLocalValue(ExcelConstants.VAR_XPT_REPORT_ID, "my-report");

        ExcelWorkbook workbook = new ExcelWorkbook();
        IExcelSheetGenerator generator = (ctx, consumer) -> {
            consumer.accept(buildSheet("gen1"), ctx);
            consumer.accept(buildSheet("gen2"), ctx);
        };

        String html = render(workbook, generator, scope);

        assertTrue(html.contains("<div id=\"my-report\">"), "custom report id expected");
        assertTrue(html.contains("data-sheet-name=\"gen2\""), "second generated sheet expected");

        assertEquals(attachmentText("simple-report-generator.html"), html);
    }

    /**
     * 录入方法：本地解除 @Disabled 运行一次，确认输出正确后，
     * 将 target/ 下的 golden 文件复制到 src/test/resources/io/nop/excel/renderer/。
     */
    @Disabled("Run to regenerate golden snapshots, copy target output to test resources")
    @Test
    public void regenerateSnapshots() throws Exception {        String basic = render(buildWorkbook(), null, EvalExprProvider.newEvalScope());

        IEvalScope scope = EvalExprProvider.newEvalScope();
        scope.setLocalValue(ExcelConstants.VAR_XPT_REPORT_ID, "my-report");
        ExcelWorkbook emptyWorkbook = new ExcelWorkbook();
        IExcelSheetGenerator generator = (ctx, consumer) -> {
            consumer.accept(buildSheet("gen1"), ctx);
            consumer.accept(buildSheet("gen2"), ctx);
        };
        String generated = render(emptyWorkbook, generator, scope);

        Path targetDir = Path.of("target", "golden");
        Files.createDirectories(targetDir);
        Files.write(targetDir.resolve("simple-report-basic.html"), basic.getBytes(StandardCharsets.UTF_8));
        Files.write(targetDir.resolve("simple-report-generator.html"), generated.getBytes(StandardCharsets.UTF_8));
        System.out.println("golden files written to " + targetDir.toAbsolutePath());
    }
}
