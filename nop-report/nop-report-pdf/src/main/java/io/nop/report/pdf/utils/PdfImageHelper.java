package io.nop.report.pdf.utils;

import io.nop.commons.bytes.ByteString;
import io.nop.excel.model.ExcelImage;
import io.nop.report.pdf.renderer.PdfRenderer;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;

public class PdfImageHelper {
    static final Logger LOG = LoggerFactory.getLogger(PdfImageHelper.class);

    /**
     * 渲染单元格中的图片。绘制裁剪到clipRect（页面）范围内，越界部分不绘制
     */
    public static void drawImage(PdfRenderer renderer, PDPageContentStream contentStream,
                                 ExcelImage picture, double x, double y, double width, double height) {
        drawImage(renderer, contentStream, picture, x, y, width, height, null);
    }

    /**
     * @param clipRect 当前用户空间坐标的裁剪矩形（调用方已把设备页矩形换算到
     *                 内容变换空间），越界部分不绘制
     */
    public static void drawImage(PdfRenderer renderer, PDPageContentStream contentStream,
                                 ExcelImage picture, double x, double y, double width, double height,
                                 java.awt.Rectangle clipRect) {
        try {
            // 获取图片数据；缺图（无数据）与损坏图统一绘制占位（WI6：不再静默跳过）
            ByteString imageData = picture.getData();
            if (imageData == null || imageData.isEmpty()) {
                LOG.warn("nop.report.pdf.image-missing-placeholder:x={},y={},w={},h={}", x, y, width, height);
                drawPlaceholder(contentStream, x, y, width, height);
                return;
            }

            // 根据图片类型创建PDImageXObject
            PDImageXObject pdImage = renderer.getImage(imageData);

            if (clipRect != null) {
                contentStream.saveGraphicsState();
                contentStream.addRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);
                contentStream.clip();
            }

            Number rotateNumber = picture.getRotateDegree();
            double rotate = rotateNumber == null ? 0 : rotateNumber.doubleValue();
            if (Math.abs(rotate) < 1e-9) {
                contentStream.drawImage(pdImage, (float) x, (float) y, (float) width, (float) height);
            } else {
                // WI6：绕图片中心旋转。PDFBox 为行向量约定（p' = p·M，左侧先应用），
                // 期望顺序 Tl(-.5,-.5)→S(w,h)→R(θ)→T(cx,cy)，合并后各项直接显式构造：
                //   a=w·cosθ, b=w·sinθ, c=-h·sinθ, d=h·cosθ,
                //   e=cx-0.5(w·cosθ-h·sinθ), f=cy-0.5(w·sinθ+h·cosθ)
                // PDF y-up 空间正角逆时针，OOXML rotateDegree 顺时针为正，故 θ 取负。
                double theta = Math.toRadians(-rotate);
                double cosT = Math.cos(theta);
                double sinT = Math.sin(theta);
                float w = (float) width;
                float h = (float) height;
                float cx = (float) (x + width / 2);
                float cy = (float) (y + height / 2);
                Matrix matrix = new Matrix(
                        (float) (w * cosT), (float) (w * sinT),
                        (float) (-h * sinT), (float) (h * cosT),
                        (float) (cx - 0.5 * (w * cosT - h * sinT)),
                        (float) (cy - 0.5 * (w * sinT + h * cosT)));
                contentStream.drawImage(pdImage, matrix);
            }

            if (clipRect != null) {
                contentStream.restoreGraphicsState();
            }
        } catch (Exception e) {
            LOG.error("Failed to render image at [{}, {}]", x, y, e);
            try {
                // 绘制错误占位符
                drawPlaceholder(contentStream, x, y, width, height);
            } catch (IOException ex) {
                LOG.error("Failed to render error placeholder", ex);
            }
        }
    }

    private static void drawPlaceholder(PDPageContentStream contentStream, double x, double y,
                                        double width, double height) throws IOException {
        contentStream.setNonStrokingColor(Color.LIGHT_GRAY);
        contentStream.addRect((float) x, (float) y, (float) width, (float) height);
        contentStream.fill();
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
