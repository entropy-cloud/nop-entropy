/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.pdf.renderer;

import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
//import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.OutputStream;

public class TestPdfReportRenderer extends JunitBaseTestCase {
    @Inject
    IReportEngine reportEngine;

    @Test
    public void logAllFontNames() {
        String[] fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String fontName : fontNames) {
            System.out.println(fontName);
        }
    }

    @Test
    public void testHtmlToPdf() throws Exception {
        ITextTemplateOutput out = reportEngine.getHtmlRenderer("/test/test-pdf-simple.xpt.xlsx");
        String html = out.generateText(XLang.newEvalScope());

        //加载html文件
        org.jsoup.nodes.Document document = Jsoup.parse(html, "UTF-8");
        document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.html);

        IResource resource = getTargetResource("gen/test.pdf");
        OutputStream os = resource.getOutputStream();
//        try {
//            PdfRendererBuilder builder = new PdfRendererBuilder();
//            builder.withUri("javaOpenSource\\src\\main\\resources\\testOpenLeagueoflegends1.pdf");
//            builder.toStream(os);
//            builder.withW3cDocument(new W3CDom().fromJsoup(document), "/");
//            addFont(builder, "C:\\Windows\\Fonts");
//          //  builder.run();
//        } finally {
//            IoHelper.safeCloseObject(os);
//        }
    }
//
//    private static void addFont(PdfRendererBuilder builder, String dir) {
//        File f = new File(dir);
//        if (f.isDirectory()) {
//            File[] files = f.listFiles(new FilenameFilter() {
//                public boolean accept(File dir, String name) {
//                    String lower = name.toLowerCase();
////                    lower.endsWith(".otf") ||  对otf库的字体支持有问题，暂时屏蔽
//                    return lower.endsWith(".ttf") || lower.endsWith(".ttc");
//                }
//            });
//            for (File subFile : files) {
//                String fontFamily = subFile.getName().substring(0, subFile.getName().lastIndexOf("."));
//                builder.useFont(subFile, fontFamily);
//            }
//        }
//    }

    @Test
    public void testPdfSimple() {
        ITemplateOutput out = reportEngine.getRenderer("/test/test-pdf-simple.xpt.xlsx", "pdf");
        IResource resource = getTargetResource("gen/test-simple.pdf");
        out.generateToResource(resource, XLang.newEvalScope());
    }

}
