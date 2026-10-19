package io.nop.pdf.core;

import io.nop.pdf.verify.GoldenCompareResult;
import io.nop.pdf.verify.PdfGoldenManager;
import io.nop.pdf.verify.PdfImageRenderer;
import io.nop.pdf.verify.RegionProbe;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12：盖章/水印——位置墨迹采样、opacity 混色、旋转 Tm 系数、缺省参数定版
 * （45°/0.25/36pt 居中）、null Resources 页 alpha 注入不静默、golden。
 */
public class TestPdfStamp {

    private byte[] page200x100() throws Exception {
        BufferedImage img = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    /** 渲染为 110dpi 图（与 PdfImageRenderer.DEFAULT_DPI 同源） */
    private BufferedImage renderFirst(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return PdfImageRenderer.renderPage(doc, 0, PdfImageRenderer.DEFAULT_DPI);
        }
    }

    /**
     * stampText 定位：左下原点 (20, 60) 的黑色文本在 110dpi 渲染图的对应区域留墨，
     * 远端对照区域无墨。
     */
    @Test
    public void testStampTextPositionInk() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.stampText(1, "IN", new PdfStampOptions().setX(20f).setY(60f).setFontSize(16f));
            byte[] pdf = toPdf(doc);

            BufferedImage img = renderFirst(pdf);
            double s = PdfImageRenderer.DEFAULT_DPI / 72.0;
            // 文本绘制基线 (20,60)，字形在基线上方 16pt 高的带内
            Color ink = RegionProbe.averageColor(img,
                    (int) (20 * s), (int) ((100 - 70) * s), (int) (24 * s), (int) (12 * s));
            Color blank = RegionProbe.averageColor(img,
                    (int) (120 * s), (int) ((100 - 70) * s), (int) (40 * s), (int) (12 * s));
            assertTrue(ink.getRed() < 250, "text region must have ink: " + ink);
            assertTrue(blank.getRed() > 250, "control region must be blank: " + blank);
        }
    }

    /**
     * opacity 混色：0.25 透明度的墨迹显著浅于 1.0（同为黑字白底）。
     */
    @Test
    public void testOpacityBlend() throws Exception {
        byte[] light;
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.stampText(1, "OP", new PdfStampOptions().setX(20f).setY(60f)
                    .setFontSize(16f).setOpacity(0.25f));
            light = toPdf(doc);
        }
        byte[] solid;
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.stampText(1, "OP", new PdfStampOptions().setX(20f).setY(60f)
                    .setFontSize(16f).setOpacity(1f));
            solid = toPdf(doc);
        }
        double s = PdfImageRenderer.DEFAULT_DPI / 72.0;
        int[] region = {(int) (20 * s), (int) ((100 - 70) * s), (int) (24 * s), (int) (12 * s)};
        Color cLight = RegionProbe.averageColor(renderFirst(light), region[0], region[1], region[2], region[3]);
        Color cSolid = RegionProbe.averageColor(renderFirst(solid), region[0], region[1], region[2], region[3]);
        assertTrue(cLight.getRed() > cSolid.getRed(),
                "0.25 opacity must be lighter than solid: " + cLight + " vs " + cSolid);
        assertTrue(cSolid.getRed() < 250, "solid must have ink: " + cSolid);
    }

    /**
     * 旋转 Tm 系数：30° → (cos30, sin30, -sin30, cos30)；水印缺省 45°。
     */
    @Test
    public void testRotationMatricesAndWatermarkDefaults() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.stampText(1, "ROT", new PdfStampOptions().setDegrees(30f).setX(50f).setY(50f));
            doc.watermarkText("CONF", null);
            byte[] pdf = toPdf(doc);

            List<float[]> tms = textMatrices(pdf);
            boolean found30 = false, found45 = false;
            for (float[] tm : tms) {
                if (near(tm[0], 0.866f) && near(tm[1], 0.5f) && near(tm[2], -0.5f) && near(tm[3], 0.866f))
                    found30 = true;
                if (near(tm[0], 0.7071f) && near(tm[1], 0.7071f)
                        && near(tm[2], -0.7071f) && near(tm[3], 0.7071f))
                    found45 = true;
            }
            assertTrue(found30, "degrees=30 must emit Tm(cos30,sin30,-sin30,cos30), tms=" + tms);
            assertTrue(found45, "watermark default must be 45°, tms=" + tms);
            assertTrue(docAllText(pdf).contains("CONF"));
        }
    }

    /**
     * null Resources 页（fromImage 产物）注 alpha 不 NPE、不静默：水印后页面有淡墨。
     */
    @Test
    public void testWatermarkImageOnNullResourcesPage() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            // 红色 40x40 png
            BufferedImage img = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, 40, 40);
            g.dispose();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            doc.watermarkImage(bos.toByteArray(), new PdfStampOptions().setOpacity(0.3f));
            byte[] pdf = toPdf(doc);

            BufferedImage rendered = renderFirst(pdf);
            double s = PdfImageRenderer.DEFAULT_DPI / 72.0;
            // 页中心（水印居中）应有红色淡墨（R 显著高于 B）
            // 水印缺省 45°（旋转后为菱形）——取中心 10x10pt（在菱形内切圆内）
            Color c = RegionProbe.averageColor(rendered,
                    (int) (95 * s), (int) (45 * s), (int) (10 * s), (int) (10 * s));
            assertTrue(c.getRed() > c.getBlue() + 20, "watermark image must blend red at center: " + c);
            // 纯红混白不改变 R 通道（255），混色体现在 G/B：0.3*0+0.7*255 = 178.5
            assertTrue(c.getBlue() < 250 && c.getBlue() > 150,
                    "0.3 opacity must blend with white bg: " + c);
        }
    }

    @Test
    public void testGoldenWatermarkPage() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.stampText(1, "Stamp-12", new PdfStampOptions().setX(20f).setY(50f));
            doc.watermarkText("CONF", null);
            byte[] pdf = toPdf(doc);

            PdfGoldenManager golden = PdfGoldenManager.forDefaultDir("wi12-assembly");
            BufferedImage img = renderFirst(pdf);
            GoldenCompareResult result = golden.compare("watermark-page-1", img);
            assertTrue(result.getStatus() == GoldenCompareResult.Status.PASS
                    || result.getStatus() == GoldenCompareResult.Status.BASELINE_CREATED,
                    "golden regression: " + result);
        }
    }

    // ========== 辅助 ==========

    private byte[] toPdf(PdfDoc doc) {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        doc.save(bos);
        return bos.toByteArray();
    }

    private String docAllText(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private List<float[]> textMatrices(byte[] pdf) throws Exception {
        List<float[]> ret = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (var page : doc.getPages()) {
                PDFStreamParser parser = new PDFStreamParser(page);
                List<Object> operands = new ArrayList<>();
                for (Object token : parser.parse()) {
                    if (token instanceof Operator op) {
                        if ("Tm".equals(op.getName()) && operands.size() >= 4)
                            ret.add(new float[]{((COSNumber) operands.get(0)).floatValue(),
                                    ((COSNumber) operands.get(1)).floatValue(),
                                    ((COSNumber) operands.get(2)).floatValue(),
                                    ((COSNumber) operands.get(3)).floatValue()});
                        operands = new ArrayList<>();
                    } else {
                        operands.add(token);
                    }
                }
            }
        }
        return ret;
    }

    private boolean near(float a, float b) {
        return Math.abs(a - b) < 0.01f;
    }
}
