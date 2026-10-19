package io.nop.pdf.census;

import io.nop.pdf.core.PdfDoc;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI17：图片普查——多页嵌图/跨页同图/小图阈值/无图页/registry YAML 输出/端到端落盘回读。
 */
public class TestImageCensus {

    private byte[] tinyPng(int w, int h) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private void drawImage(PDDocument doc, int pageIdx, byte[] png) throws Exception {
        PDPage page = doc.getPage(pageIdx - 1);
        var img = org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(
                doc, png, null);
        try (var cs = new PDPageContentStream(doc, page,
                PDPageContentStream.AppendMode.APPEND, true)) {
            cs.drawImage(img, 10, 10, img.getWidth(), img.getHeight());
        }
    }

    @Test
    public void testMultiPageImageCensus() throws Exception {
        byte[] png1 = tinyPng(100, 50);
        byte[] png2 = tinyPng(80, 60);
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 4; i++) doc.addPage(new PDPage());
            drawImage(doc, 1, png1);
            drawImage(doc, 3, png2);
            // page 4 无图

            ImageCensus.Options opts = new ImageCensus.Options().setMinAreaPx(1);
            ImageCensus.Result result = ImageCensus.census(doc, opts);

            assertEquals(2, result.getEntries().size());
            assertEquals(1, result.getEntries().get(0).getPage());
            assertEquals(3, result.getEntries().get(1).getPage());
            assertTrue(result.getEntries().get(0).isKeep());
            assertEquals(2, result.getDocStats().getImageCount());
        }
    }

    @Test
    public void testMinAreaFilter() throws Exception {
        byte[] small = tinyPng(5, 5);
        byte[] big = tinyPng(200, 100);
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            drawImage(doc, 1, small);
            drawImage(doc, 1, big);

            ImageCensus.Options opts = new ImageCensus.Options().setMinAreaPx(1000);
            ImageCensus.Result result = ImageCensus.census(doc, opts);
            assertEquals(2, result.getEntries().size());
            assertFalse(result.getEntries().get(0).isKeep(), "small image: area 25 < 1000");
            assertTrue(result.getEntries().get(1).isKeep(), "big image: area 20000 >= 1000");
        }
    }

    @Test
    public void testRegistryYaml() throws Exception {
        byte[] png = tinyPng(100, 50);
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            drawImage(doc, 1, png);
            ImageCensus.Options opts = new ImageCensus.Options().setMinAreaPx(1);
            ImageCensus.Result result = ImageCensus.census(doc, opts);
            String yaml = ImageCensus.toRegistryYaml(result.getEntries());

            Map<String, Object> root = new org.yaml.snakeyaml.Yaml().load(yaml);
            List<Map<String, Object>> images = (List<Map<String, Object>>) root.get("images");
            assertEquals(1, images.size());
            assertEquals(1, images.get(0).get("seq"));
            assertEquals(1, images.get(0).get("page"));
            assertEquals(100, images.get(0).get("width"));
            assertEquals(50, images.get(0).get("height"));
        }
    }

    @Test
    public void testEndToEndFileOutput() throws Exception {
        byte[] png = tinyPng(100, 50);
        Path tmpDir = Files.createTempDirectory("wi17-census");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            drawImage(doc, 1, png);
            ImageCensus.Options opts = new ImageCensus.Options()
                    .setMinAreaPx(1).setOutputDir(tmpDir.toFile());
            ImageCensus.Result result = ImageCensus.census(doc, opts);

            assertEquals(1, result.getEntries().size());
            String fileName = result.getEntries().get(0).getFile();
            File imgFile = new File(tmpDir.toFile(), fileName);
            assertTrue(imgFile.exists(), "image file must be written to disk");
            assertTrue(imgFile.length() > 0);
        }
    }

    private static void assertFalse(boolean cond, String msg) {
        org.junit.jupiter.api.Assertions.assertFalse(cond, msg);
    }
}
