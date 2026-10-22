package io.nop.integration.qrcode;

import io.nop.api.core.exceptions.NopException;
import io.nop.integration.api.qrcode.IQrcodeService;
import io.nop.integration.api.qrcode.QrcodeOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12：码制扩展验证——Zxing MultiFormatWriter 支持的全部码制按名解析；
 * errorCorrection 整数 0/1/2/3（forBits：M=0/L=1/H=2/Q=3）。
 */
public class TestBarcodeFormats {
    IQrcodeService qrcodeService = new ZxingQrcodeService();

    private byte[] encode(String format, String content, int width, int height, int errorCorrection) {
        QrcodeOptions options = new QrcodeOptions();
        options.setBarcodeFormat(format);
        options.setContent(content);
        options.setWidth(width);
        options.setHeight(height);
        options.setErrorCorrection(errorCorrection);
        return qrcodeService.createQrcodeBytes(options);
    }

    @Test
    public void testCode128() {
        assertTrue(encode("CODE_128", "ABC-123", 200, 60, 1).length > 0);
    }

    @Test
    public void testEan13() {
        assertTrue(encode("EAN_13", "690123456789", 200, 80, 1).length > 0);
    }

    @Test
    public void testPdf417() {
        assertTrue(encode("PDF_417", "PDF417-CONTENT-123", 200, 60, 1).length > 0);
    }

    @Test
    public void testErrorCorrectionLevels() {
        for (int level : new int[]{0, 1, 2, 3}) {
            assertTrue(encode("QR_CODE", "hello", 100, 100, level).length > 0, "level=" + level);
        }
    }

    @Test
    public void testUnknownFormatThrows() {
        assertThrows(Exception.class, () -> encode("NO_SUCH_FORMAT", "x", 100, 100, 0));
    }
}
