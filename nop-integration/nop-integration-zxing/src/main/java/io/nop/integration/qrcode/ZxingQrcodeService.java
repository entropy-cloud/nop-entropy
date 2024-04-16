package io.nop.integration.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.integration.api.qrcode.IQrcodeService;
import io.nop.integration.api.qrcode.QrcodeOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ZxingQrcodeService implements IQrcodeService {
    @Override
    public byte[] createQrcodeBytes(QrcodeOptions options) {
        try {
            // 创建二维码内容
            String content = options.getContent();
            // 设置二维码的参数
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, options.getEncoding());
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.forBits(options.getErrorCorrection()));
            if (options.getMargin() >= 0) {
                hints.put(EncodeHintType.MARGIN, options.getMargin()); // 设置边距
            }

            double width = options.getWidth();
            double height = options.getHeight();
            if (height <= 0)
                height = options.getWidth();

            // 生成二维码矩阵
            MultiFormatWriter multiFormatWriter = new MultiFormatWriter();
            BarcodeFormat format = getFormat(options.getBarcodeFormat());
            BitMatrix bitMatrix = multiFormatWriter.encode(content, format,
                    (int) width, (int) height, hints);
            // 将二维码矩阵转换为图片
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, options.getImgType(), outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw NopException.adapt(e);
        }
    }

    private BarcodeFormat getFormat(String name) {
        if (ApiStringHelper.isEmpty(name))
            return BarcodeFormat.QR_CODE;
        for (BarcodeFormat format : BarcodeFormat.values()) {
            if (format.name().equalsIgnoreCase(name))
                return format;
        }
        throw new IllegalArgumentException("nop.qrcode.unsupported-barcode-format:" + name);
    }
}