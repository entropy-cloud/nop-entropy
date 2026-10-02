package io.nop.integration.api.qrcode;

import io.nop.integration.api.sms.SmsMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WI12 small-module coverage: the QRCode render options must pin their
 * documented defaults (png / UTF-8 / medium error correction) — connectors
 * rely on these when the caller sends a bare options bean — and the SMS
 * message type codes must keep their dictionary values.
 */
public class TestQrcodeAndSmsOptions {

    @Test
    public void testQrcodeOptionsDefaults() {
        QrcodeOptions options = new QrcodeOptions();
        assertEquals("png", options.getImgType(), "default image type is png");
        assertEquals("UTF-8", options.getEncoding(), "default content encoding is UTF-8");
        assertEquals(QrcodeOptions.ERROR_CORRECTION_M, options.getErrorCorrection(),
                "default error correction is medium");
        assertEquals(0, options.getMargin(), "default margin is zero");
    }

    @Test
    public void testQrcodeErrorCorrectionConstantsAreDistinct() {
        assertEquals(0, QrcodeOptions.ERROR_CORRECTION_M);
        assertEquals(1, QrcodeOptions.ERROR_CORRECTION_L);
        assertEquals(2, QrcodeOptions.ERROR_CORRECTION_H);
        assertEquals(3, QrcodeOptions.ERROR_CORRECTION_Q);
    }

    @Test
    public void testQrcodeOptionsSettersRoundTrip() {
        QrcodeOptions options = new QrcodeOptions();
        options.setImgType("svg");
        options.setContent("https://example.com/qr");
        options.setWidth(300.5);
        options.setHeight(300.5);
        options.setErrorCorrection(QrcodeOptions.ERROR_CORRECTION_H);
        assertEquals("svg", options.getImgType());
        assertEquals("https://example.com/qr", options.getContent());
        assertEquals(300.5, options.getWidth());
        assertEquals(300.5, options.getHeight());
        assertEquals(QrcodeOptions.ERROR_CORRECTION_H, options.getErrorCorrection());
    }

    @Test
    public void testSmsMessageTypeCodesAndAccessors() {
        assertEquals(0, SmsMessage.TYPE_DEFAULT, "default SMS type code is 0");
        assertEquals(1, SmsMessage.TYPE_PROMOTION, "promotion SMS type code is 1");

        SmsMessage sms = new SmsMessage();
        sms.setType(SmsMessage.TYPE_PROMOTION);
        sms.setAreaCode("+86");
        sms.setMobile("13800000000");
        sms.setText("your code is 1234");
        sms.setTemplateCode("SMS_1001");
        sms.setParams(java.util.List.of("1234"));
        assertEquals(SmsMessage.TYPE_PROMOTION, sms.getType());
        assertEquals("+86", sms.getAreaCode());
        assertEquals("13800000000", sms.getMobile());
        assertEquals("your code is 1234", sms.getText());
        assertEquals("SMS_1001", sms.getTemplateCode());
        assertEquals(java.util.List.of("1234"), sms.getParams());
    }
}
