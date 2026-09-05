package io.nop.report.demo;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.store.InMemoryResourceStore;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.excel.model.ExcelCell;
import io.nop.excel.model.ExcelFont;
import io.nop.excel.model.ExcelPageSetup;
import io.nop.excel.model.constants.ExcelPaperSize;
import io.nop.excel.model.ExcelRow;
import io.nop.excel.model.ExcelSheet;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 报表PDF导出质量审计：批量渲染demo目录全部报表模板为PDF，并逐页转PNG供视觉检查。
 * 产出目录：项目根/_tmp/report-pdf-audit/
 *
 * 字体注入：仓库未自带CJK字体（/fonts/default.ttf缺失），模板中的中文具名字体（宋体/等线等）
 * 会回退到defaultFontResource。审计时把系统CJK字体注入VFS内存层，使审计聚焦在
 * 布局/分页/样式问题上；null字体名走Helvetica的缺陷另行记录，不在此掩盖。
 */
@NopTestConfig(localDb = true)
public class TestPdfExportAudit extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    static final String[] CJK_FONT_CANDIDATES = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf", // macOS
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",       // Linux
            "C:/Windows/Fonts/simsun.ttc",                          // Windows
    };

    @BeforeAll
    static void injectCjkFont() {
        File fontFile = null;
        for (String path : CJK_FONT_CANDIDATES) {
            File f = new File(path);
            if (f.exists()) {
                fontFile = f;
                break;
            }
        }
        if (fontFile == null)
            return;

        InMemoryResourceStore store = new InMemoryResourceStore();
        store.addResource(new FileResource("/fonts/default.ttf", fontFile));
        VirtualFileSystem.instance().updateInMemoryLayer(store);
    }

    static final String[] TEMPLATES = {
            "/base/01-档案式报表.xpt.xlsx",
            "/base/02-段落明细表.xpt.xlsx",
            "/base/03-复杂多源报表.xpt.xlsx",
            "/base/04-交叉报表—数据双向扩展.xpt.xlsx",
            "/base/05-同比环比等财务统计表.xpt.xlsx",
            "/base/06-Sheet循环.xpt.xlsx",
            "/base/07-数据钻取主报表.xpt.xlsx",
            "/base/08-动态展开列.xpt.xlsx",
            "/base/09-套打.xpt.xlsx",
            "/base/10-导出Excel公式.xpt.xlsx",
            "/base/11-打印条码和二维码.xpt.xlsx",
            "/base/12-动态Sheet和动态列.xpt.xlsx",
            "/base/13-复杂结构列展开.xpt.xlsx",
            "/base/14-复杂结构行展开.xpt.xlsx",
            "/base/15-兄弟节点同时展开.xpt.xlsx",
            "/base/16-多个数据源展开.xpt.xlsx",
            "/base/17-动态Sheet和动态列.xpt.xlsx",
            "/base/18-兄弟节点不自动展开.xpt.xlsx",
            "/ext/report-with-params.xpt.xlsx",
            "/performance/测试同比环比.xpt.xlsx",
    };

    static File outDir() {
        File dir = new File("../../_tmp/report-pdf-audit");
        dir.mkdirs();
        return dir;
    }

    @Test
    public void exportAll() throws Exception {
        File dir = outDir();
        Map<String, Throwable> failures = new LinkedHashMap<>();
        List<String> ok = new ArrayList<>();

        for (String name : TEMPLATES) {
            String path = "/nop/report/demo" + name;
            String base = fileName(name);
            File pdf = new File(dir, base + ".pdf");
            try {
                ITemplateOutput output = reportEngine.getRenderer(path, "pdf");
                IEvalScope scope = XLang.newEvalScope();
                if (name.contains("report-with-params"))
                    scope.setLocalValue("title", "审计参数标题");
                output.generateToFile(pdf, scope);
                int pages = renderPages(pdf, new File(dir, base));
                ok.add(name + " (" + pages + "p)");
            } catch (Throwable e) {
                failures.put(name, e);
            }
        }

        // 合成分页用例：200行中文长表 + A4页面设置，专测自动分页（纯表格无xpt语义，走ForExcel路径）
        try {
            File pdf = new File(dir, "synthetic-long-table.pdf");
            reportEngine.getRendererForExcel(buildLongTableWorkbook(), "pdf").generateToFile(pdf, XLang.newEvalScope());
            int pages = renderPages(pdf, new File(dir, "synthetic-long-table"));
            ok.add("synthetic-long-table (" + pages + "p)");
        } catch (Throwable e) {
            failures.put("synthetic-long-table", e);
        }

        System.out.println("==== PDF EXPORT AUDIT ====");
        ok.forEach(s -> System.out.println("OK   " + s));
        failures.forEach((k, v) -> {
            System.out.println("FAIL " + k + " -> " + v);
            v.printStackTrace(System.out);
        });
        // plan 2260 Phase 1断言基线：注入CJK字体后全部模板必须导出成功
        assertTrue(failures.isEmpty(), "templates failed to export: " + failures.keySet());
    }

    static String fileName(String name) {
        String s = name.substring(name.lastIndexOf('/') + 1);
        return s.substring(0, s.length() - ".xpt.xlsx".length());
    }

    static int renderPages(File pdf, File pngBase) throws Exception {
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            PDFRenderer r = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                BufferedImage img = r.renderImageWithDPI(i, 110);
                ImageIO.write(img, "png", new File(pngBase + "-p" + (i + 1) + ".png"));
            }
            return doc.getNumberOfPages();
        }
    }

    ExcelWorkbook buildLongTableWorkbook() {
        ExcelWorkbook wb = new ExcelWorkbook();
        ExcelSheet sheet = new ExcelSheet();
        sheet.setName("LongTable");

        ExcelPageSetup pageSetup = new ExcelPageSetup();
        pageSetup.setPaperSize(ExcelPaperSize.A4_PAPER.ordinal());
        sheet.setPageSetup(pageSetup);

        // 无style/无font的单元格会使PdfTableRenderer.getStyleFont返回null并NPE（缺陷已记录），这里显式设置style+font
        ExcelStyle headerStyle = new ExcelStyle();
        headerStyle.setId("h1");
        ExcelFont font = new ExcelFont();
        font.setFontName("宋体");
        font.setFontSize(10.0f);
        headerStyle.setFont(font);
        wb.addStyle(headerStyle);

        String[] headers = {"编号", "名称", "数量", "金额"};
        for (int c = 0; c < headers.length; c++) {
            ExcelCell cell = new ExcelCell();
            cell.setValue(headers[c]);
            cell.setStyleId("h1");
            sheet.getTable().setCell(0, c, cell);
        }
        for (int r = 1; r <= 200; r++) {
            Object[] row = {"P" + r, "测试项目名称第" + r + "号", r * 3, r * 12.5};
            for (int c = 0; c < row.length; c++) {
                ExcelCell cell = new ExcelCell();
                cell.setValue(row[c]);
                cell.setStyleId("h1");
                sheet.getTable().setCell(r, c, cell);
            }
        }
        wb.addSheet(sheet);
        return wb;
    }
}
