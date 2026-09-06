package io.nop.report.demo;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.pdf.font.FontManager;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2260 Phase 6端到端验收：不注入VFS字体，依赖Phase 2的系统字体目录
 * 自动发现+字形回退链路，全部demo模板必须导出成功。
 *
 * 环境前提（ai-dev/design/nop-report/pdf-font-strategy.md D3）：
 * 运行环境需存在系统CJK字体，或CI镜像安装CJK字体包（如fonts-wqy-microhei）。
 */
@NopTestConfig(localDb = true)
public class TestPdfExportAuditNoFontInjection extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    /**
     * FontManager是JVM级单例：同fork中先行测试类（TestPdfRenderDefects/TestPdfExportAudit）
     * 注入的VFS字体会被init永久缓存，使本测试的"无注入"前提失真。强制重置后，
     * 若环境无系统CJK字体，本测试会如实失败而非被污染缓存掩盖
     */
    @BeforeAll
    static void resetFontManager() {
        FontManager.instance().resetForTesting();
    }

    @Test
    public void exportAllWithoutFontInjection() throws Exception {
        File dir = new File("../../_tmp/report-pdf-audit-noinject");
        dir.mkdirs();

        Map<String, Throwable> failures = new LinkedHashMap<>();
        for (String name : TestPdfExportAudit.TEMPLATES) {
            String path = "/nop/report/demo" + name;
            String base = TestPdfExportAudit.fileName(name);
            try {
                ITemplateOutput output = reportEngine.getRenderer(path, "pdf");
                IEvalScope scope = XLang.newEvalScope();
                if (name.contains("report-with-params"))
                    scope.setLocalValue("title", "审计参数标题");
                output.generateToFile(new File(dir, base + ".pdf"), scope);
            } catch (Throwable e) {
                failures.put(name, e);
            }
        }

        failures.forEach((k, v) -> System.out.println("FAIL " + k + " -> " + v));
        assertTrue(failures.isEmpty(), "templates failed without font injection: " + failures.keySet());

        // 中文必须真的画出来（防字形静默丢失）：对中文模板做PDFTextStripper提取断言
        File chinesePdf = new File(dir, TestPdfExportAudit.fileName("/base/03-复杂多源报表.xpt.xlsx") + ".pdf");
        assertTrue(chinesePdf.exists(), "chinese template pdf should exist");
        String rawText = extractText(chinesePdf);
        String text = rawText.replaceAll("\\s+", "");
        assertTrue(text.contains("项目名称") || text.contains("资金"),
                "Chinese text should be extractable from exported pdf, got: "
                        + text.substring(0, Math.min(200, text.length())));
    }

    private String extractText(File pdf) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }
}
