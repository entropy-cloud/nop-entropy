package io.nop.report.pdf.utils;

import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelVerticalAlignment;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.io.IOException;
import java.util.List;

public class PdfStyleHelper {


    // 将Excel颜色代码转换为PDFBox颜色
    public static PDColor convertColor(String excelColor) {
        float[] components = ColorHelper.toNormalizedRgb(excelColor);
        return new PDColor(components, PDDeviceRGB.INSTANCE);
    }

    // 设置单元格背景
    public static void setCellBackground(PDPageContentStream contentStream,
                                         ExcelStyle style,
                                         float x, float y,
                                         float width, float height) throws IOException {
        if (style != null && style.getFillBgColor() != null && !style.getFillBgColor().isEmpty()) {
            PDColor bgColor = convertColor(style.getFillBgColor());
            contentStream.setNonStrokingColor(bgColor);
            contentStream.addRect(x, y - height, width, height);
            contentStream.fill();
        }
    }

    // 绘制单元格边框
    public static void drawCellBorders(PDPageContentStream contentStream,
                                       ExcelStyle style,
                                       float x, float y,
                                       float width, float height) throws IOException {
        if (style == null) return;

        // 设置边框颜色和宽度
        float lineWidth = 0.5f; // 默认线宽
        PDColor borderColor = new PDColor(new float[]{0, 0, 0}, PDDeviceRGB.INSTANCE);

        contentStream.setLineWidth(lineWidth);
        contentStream.setStrokingColor(borderColor);

        // 绘制各边边框
        if (style.getTopBorder() != null) {
            contentStream.moveTo(x, y);
            contentStream.lineTo(x + width, y);
            contentStream.stroke();
        }

        if (style.getBottomBorder() != null) {
            contentStream.moveTo(x, y - height);
            contentStream.lineTo(x + width, y - height);
            contentStream.stroke();
        }

        if (style.getLeftBorder() != null) {
            contentStream.moveTo(x, y);
            contentStream.lineTo(x, y - height);
            contentStream.stroke();
        }

        if (style.getRightBorder() != null) {
            contentStream.moveTo(x + width, y);
            contentStream.lineTo(x + width, y - height);
            contentStream.stroke();
        }
    }

    // 计算文本位置
    public static float[] calculateTextPosition(String text, PDFont font, float fontSize,
                                                float cellX, float cellY, float cellWidth, float cellHeight,
                                                ExcelHorizontalAlignment hAlign, ExcelVerticalAlignment vAlign) {
        try {
            float textWidth = font.getStringWidth(text) / 1000 * fontSize;
            float textHeight = font.getFontDescriptor().getCapHeight() / 1000 * fontSize;

            float x = cellX;
            if (hAlign == ExcelHorizontalAlignment.CENTER) {
                x = cellX + (cellWidth - textWidth) / 2;
            } else if (hAlign == ExcelHorizontalAlignment.RIGHT) {
                x = cellX + cellWidth - textWidth - 2; // 右边留2pt边距
            } else {
                x = cellX + 2; // 左边留2pt边距
            }

            float y = cellY;
            if (vAlign == ExcelVerticalAlignment.CENTER) {
                y = cellY - (cellHeight - textHeight) / 2 - textHeight * 0.3f;
            } else if (vAlign == ExcelVerticalAlignment.BOTTOM) {
                y = cellY - cellHeight + textHeight + 2; // 底部留2pt边距
            } else {
                y = cellY - textHeight * 1.3f; // 顶部对齐
            }

            return new float[]{x, y};
        } catch (IOException e) {
            return new float[]{cellX + 2, cellY - 10}; // 默认位置
        }
    }

    /**
     * 绘制不折行的文本
     *
     * @param contentStream PDF内容流
     * @param text          要绘制的文本
     * @param font          字体
     * @param fontSize      字体大小
     * @param cellRect      单元格矩形区域
     * @param style         Excel样式
     * @throws IOException 如果绘制过程中出错
     */
    public static void drawText(PDPageContentStream contentStream,
                                String text,
                                PDFont font,
                                float fontSize,
                                PDRectangle cellRect,
                                ExcelStyle style) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }

        // 获取对齐方式
        ExcelHorizontalAlignment hAlign = style != null ? style.getHorizontalAlign() : null;
        ExcelVerticalAlignment vAlign = style != null ? style.getVerticalAlign() : null;

        // 计算文本位置
        float[] position = calculateTextPosition(text, font, fontSize,
                cellRect.getLowerLeftX(), cellRect.getUpperRightY(),
                cellRect.getWidth(), cellRect.getHeight(),
                hAlign, vAlign);

        // 设置字体和颜色
        contentStream.setFont(font, fontSize);
        if (style != null && style.getFont() != null) {
            PDColor textColor = convertColor(style.getFont().getFontColor());
            if (textColor != null) {
                contentStream.setNonStrokingColor(textColor);
            }
        }

        // 绘制文本
        contentStream.beginText();
        contentStream.newLineAtOffset(position[0], position[1]);
        contentStream.showText(text);
        contentStream.endText();
    }

    /**
     * 绘制自动折行的文本
     *
     * @param contentStream PDF内容流
     * @param text          要绘制的文本
     * @param font          字体
     * @param fontSize      字体大小
     * @param cellRect      单元格矩形区域
     * @param style         Excel样式
     * @throws IOException 如果绘制过程中出错
     */
    public static void drawWrappedText(PDPageContentStream contentStream,
                                       String text,
                                       PDFont font,
                                       float fontSize,
                                       PDRectangle cellRect,
                                       ExcelStyle style) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }

        // 获取对齐方式和折行模式
        ExcelHorizontalAlignment hAlign = style != null ? style.getHorizontalAlign() : null;
        ExcelVerticalAlignment vAlign = style != null ? style.getVerticalAlign() : null;
        int wrapMode = style != null && style.isWrapText() ? 2 : 0;

        // 计算可用宽度（减去左右边距）
        float availableWidth = cellRect.getWidth() - 4; // 2pt边距*2

        // 分割文本为多行
        List<String> lines = TextWrapHelper.splitTextIntoLines(text, font, fontSize, availableWidth, wrapMode);

        // 计算文本块总高度
        float lineSpacing = 1.2f; // 1.2倍行距
        float textBlockHeight = TextWrapHelper.calculateTextBlockHeight(lines, font, fontSize, lineSpacing);
        float lineHeight = font.getFontDescriptor().getCapHeight() / 1000 * fontSize * lineSpacing;

        // 计算起始Y位置
        float startY = cellRect.getUpperRightY();
        if (vAlign == ExcelVerticalAlignment.CENTER) {
            startY = cellRect.getUpperRightY() - (cellRect.getHeight() - textBlockHeight) / 2;
        } else if (vAlign == ExcelVerticalAlignment.BOTTOM) {
            startY = cellRect.getLowerLeftY() + textBlockHeight;
        } else {
            startY = cellRect.getUpperRightY() - 2; // 顶部对齐，留2pt边距
        }

        // 设置字体和颜色
        contentStream.setFont(font, fontSize);
        if (style != null && style.getFont() != null) {
            PDColor textColor = convertColor(style.getFont().getFontColor());
            if (textColor != null) {
                contentStream.setNonStrokingColor(textColor);
            }
        }

        // 绘制每一行文本
        for (String line : lines) {
            if (line.isEmpty()) {
                startY -= lineHeight;
                continue;
            }

            float lineWidth = font.getStringWidth(line) / 1000 * fontSize;
            float x = cellRect.getLowerLeftX() + 2; // 左边距

            // 水平对齐
            if (hAlign == ExcelHorizontalAlignment.CENTER) {
                x = cellRect.getLowerLeftX() + (cellRect.getWidth() - lineWidth) / 2;
            } else if (hAlign == ExcelHorizontalAlignment.RIGHT) {
                x = cellRect.getLowerLeftX() + cellRect.getWidth() - lineWidth - 2;
            }

            contentStream.beginText();
            contentStream.newLineAtOffset(x, startY - fontSize);
            contentStream.showText(line);
            contentStream.endText();

            startY -= lineHeight;
        }
    }


}