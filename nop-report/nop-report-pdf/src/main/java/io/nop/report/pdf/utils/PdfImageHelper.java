package io.nop.report.pdf.utils;

import io.nop.commons.bytes.ByteString;
import io.nop.excel.model.ExcelImage;
import io.nop.report.pdf.renderer.PdfRenderer;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;

public class PdfImageHelper {
    static final Logger LOG = LoggerFactory.getLogger(PdfImageHelper.class);

    /**
     * 渲染单元格中的图片
     */
    public static void drawImage(PdfRenderer renderer, PDPageContentStream contentStream,
                                 ExcelImage picture, double x, double y, double width, double height) {
        try {
            // 获取图片数据
            ByteString imageData = picture.getData();
            if (imageData == null || imageData.isEmpty()) {
                return;
            }

            // 根据图片类型创建PDImageXObject
            PDImageXObject pdImage = renderer.getImage(imageData);

            // 绘制图片
            contentStream.drawImage(pdImage, (float) x, (float) y, (float) width, (float) height);
        } catch (Exception e) {
            LOG.error("Failed to render image at [{}, {}]", x, y, e);
            try {
                // 绘制错误占位符
                contentStream.setNonStrokingColor(Color.LIGHT_GRAY);
                contentStream.addRect((float) x, (float) y, (float) width, (float) width * 0.75f);
                contentStream.fill();
            } catch (IOException ex) {
                LOG.error("Failed to render error placeholder", ex);
            }
        }
    }

    /**
     * 自动检测图片类型
     */
    public static String detectImageType(byte[] imageData) {
        // 检查JPEG
        if (imageData.length >= 2 &&
                imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) {
            return "image/jpeg";
        }

        // 检查PNG
        if (imageData.length >= 8 &&
                imageData[0] == (byte) 0x89 && imageData[1] == (byte) 0x50 &&
                imageData[2] == (byte) 0x4E && imageData[3] == (byte) 0x47) {
            return "image/png";
        }

        // 默认返回PNG
        return "image/png";
    }
}
