package io.nop.pdf.core;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI13：文档元数据（六字段 roundtrip）与加密（AES-256 算法钉、权限位、错密码
 * fail-loud）——全部经 save→reload 持久验证（非仅内存态）。
 */
public class TestPdfMetadataProtection {

    private byte[] page200x100() throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                200, 100, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, 200, 100);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    @Test
    public void testMetadataRoundtrip() throws Exception {
        Path dir = Files.createTempDirectory("wi13-meta");
        File out = dir.resolve("meta.pdf").toFile();
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.setInfo(new PdfDocInfo()
                    .setTitle("测试标题")
                    .setAuthor("Nop")
                    .setSubject("WI13")
                    .setKeywords("pdf,meta")
                    .setCreator("nop-pdf")
                    .setProducer("nop-entropy"));
            doc.save(out);
        }
        try (PdfDoc reloaded = PdfDoc.loadFromFile(out)) {
            PdfDocInfo info = reloaded.getInfo();
            assertEquals("测试标题", info.getTitle());
            assertEquals("Nop", info.getAuthor());
            assertEquals("WI13", info.getSubject());
            assertEquals("pdf,meta", info.getKeywords());
            assertEquals("nop-pdf", info.getCreator());
            assertEquals("nop-entropy", info.getProducer());
        }
    }

    @Test
    public void testNullFieldsNotOverwritten() throws Exception {
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.setInfo(new PdfDocInfo().setTitle("only-title"));
            // 局部补充：null 字段不覆盖既有值
            doc.setInfo(new PdfDocInfo().setAuthor("late-author"));
            assertEquals("only-title", doc.getInfo().getTitle());
            assertEquals("late-author", doc.getInfo().getAuthor());
        }
    }

    /**
     * 加密 roundtrip：AES-256 算法钉（Filter/V/Length——防 RC4-40 缺省静默回归）、
     * user 密码受限打开、权限位持久。
     */
    @Test
    public void testEncryptRoundtripAes256() throws Exception {
        Path dir = Files.createTempDirectory("wi13-enc");
        File out = dir.resolve("enc.pdf").toFile();
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.setInfo(new PdfDocInfo().setTitle("secret"));
            doc.encrypt("owner-pass", "user-pass", true, false);
            // 注意：isEncrypted() 仅反映加载态（protect 只在 save 时写出加密字典），
            // 加密态断言以 reload 后为准
            doc.save(out);
        }

        try (PDDocument reloaded = Loader.loadPDF(out, "user-pass")) {
            assertTrue(reloaded.isEncrypted());
            // 加密字典算法断言（R1-Major-2：防缺省 RC4-40）
            PDEncryption enc = reloaded.getEncryption();
            assertEquals("Standard", enc.getFilter());
            assertEquals(5, enc.getVersion(), "AES-256 → V=5");
            assertEquals(256, enc.getLength(), "AES-256 → Length=256");
            // 权限位：允许打印、禁止复制
            assertTrue(reloaded.getCurrentAccessPermission().canPrint());
            assertFalse(reloaded.getCurrentAccessPermission().canExtractContent());
            // 元数据在加密文档上仍可读（user 可打开）
            assertEquals("secret", new PdfDoc(reloaded).getInfo().getTitle());
        }

        // owner 密码全权
        try (PDDocument ownerDoc = Loader.loadPDF(out, "owner-pass")) {
            assertTrue(ownerDoc.getCurrentAccessPermission().canExtractContent(),
                    "owner password must have full access");
        }
    }

    @Test
    public void testWrongPasswordFailsLoud() throws Exception {
        Path dir = Files.createTempDirectory("wi13-wrongpw");
        File out = dir.resolve("enc.pdf").toFile();
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            doc.encrypt("owner-pass", "user-pass", true, true);
            doc.save(out);
        }
        // 错密码：InvalidPasswordException → NopException.adapt（不新增错误码，plan 2482 裁定）
        Exception wrongPw = assertThrows(Exception.class, () -> PdfDoc.loadFromFile(out, "wrong-password"));
        assertTrue(hasCauseOfType(wrongPw, org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException.class),
                "wrong password must carry InvalidPasswordException cause: " + wrongPw);
        // 无密码打开带 user 密码文档同样失败
        Exception noPw = assertThrows(Exception.class, () -> PdfDoc.loadFromFile(out));
        assertTrue(hasCauseOfType(noPw, org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException.class),
                "missing password must carry InvalidPasswordException cause: " + noPw);
    }

    @Test
    public void testUnencryptedDocUnaffected() throws Exception {
        Path dir = Files.createTempDirectory("wi13-plain");
        File out = dir.resolve("plain.pdf").toFile();
        try (PdfDoc doc = PdfDoc.fromImage(page200x100())) {
            assertFalse(doc.isEncrypted());
            doc.save(out);
        }
        try (PDDocument reloaded = Loader.loadPDF(out)) {
            assertFalse(reloaded.isEncrypted());
        }
    }

    private boolean hasCauseOfType(Throwable t, Class<? extends Throwable> type) {
        while (t != null) {
            if (type.isInstance(t))
                return true;
            t = t.getCause();
        }
        return false;
    }
}
