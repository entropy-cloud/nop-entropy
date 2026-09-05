package io.nop.excel.renderer;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.excel.ExcelConstants;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.excel.model.ExcelCell;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTML报表样式输出必须是合法CSS：
 * 修复前 .xpt-row 规则后多输出一个 }，且默认字体色"0x0"输出非法的 color:#0
 */
public class TestHtmlReportRenderer {

    private static ExcelWorkbook buildWorkbook() {
        ExcelWorkbook workbook = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("s1");
        ExcelCell cell = new ExcelCell();
        cell.setValue("v");
        sheet.getTable().setCell(0, 0, cell);
        workbook.addSheet(sheet);

        ExcelStyle style = new ExcelStyle();
        style.setId("st1");
        ExcelFont font = new ExcelFont();
        font.setFontColor(ExcelConstants.DEFAULT_FONT_COLOR);
        style.setFont(font);
        workbook.setStyles(Collections.singletonList(style));
        return workbook;
    }

    @Test
    public void testRenderStylesProducesValidCss() throws Exception {
        ExcelWorkbook workbook = buildWorkbook();
        StringWriter out = new StringWriter();
        new HtmlReportRendererFactory().buildRenderer(workbook, null)
                .generateToWriter(out, newContext());

        String html = out.toString();

        // 多余的 } 会让样式块非法
        assertFalse(html.contains("}}"), "stray closing brace in CSS: " + html);
        // "0x0" 必须展开为合法6位hex，而不是被浏览器丢弃的 #0
        assertFalse(html.contains("color:#0;"), "invalid CSS color #0: " + html);
        assertTrue(html.contains("color:#000000"), "default font color should be #000000: " + html);
    }

    private static IEvalContext newContext() {
        // IEvalScope 本身实现了 IEvalContext
        return EvalExprProvider.newEvalScope();
    }
}
