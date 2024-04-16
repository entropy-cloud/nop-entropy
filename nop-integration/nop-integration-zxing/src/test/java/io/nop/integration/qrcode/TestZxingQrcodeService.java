package io.nop.integration.qrcode;

import io.nop.commons.util.FileHelper;
import io.nop.core.unittest.BaseTestCase;
import io.nop.integration.api.qrcode.IQrcodeService;
import io.nop.integration.api.qrcode.QrcodeOptions;
import org.junit.jupiter.api.Test;

public class TestZxingQrcodeService extends BaseTestCase {
    IQrcodeService qrcodeService = new ZxingQrcodeService();

    @Test
    public void testBarcode() {
        QrcodeOptions options = new QrcodeOptions();
        options.setBarcodeFormat("CODE_128");
        options.setContent("12345678");
        options.setWidth(100);
        options.setHeight(20);
        byte[] bytes = qrcodeService.createQrcodeBytes(options);
        FileHelper.writeBytes(getTargetFile("barcode.png"), bytes);
    }

    @Test
    public void testQrcode() {
        QrcodeOptions options = new QrcodeOptions();
        options.setContent("12345678");
        options.setWidth(100);
        byte[] bytes = qrcodeService.createQrcodeBytes(options);
        FileHelper.writeBytes(getTargetFile("qrcode.png"), bytes);
    }
}
