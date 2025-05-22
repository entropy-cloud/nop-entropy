package io.nop.report.pdf.utils;

import io.nop.excel.model.ExcelBorderStyle;
import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.color.ColorHelper;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelLineStyle;
import io.nop.excel.model.constants.ExcelVerticalAlignment;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.awt.*;
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

    public static void drawText(PDPageContentStream contentStream, String text, PDFont font,
                                float fontSize, PDRectangle cellRect, ExcelStyle style) throws IOException {
        if (style != null && style.isWrapText()) {
            drawWrappedText(contentStream, text, font, fontSize, cellRect, style);
        } else {
            drawUnwrappedText(contentStream, text, font, fontSize, cellRect, style);
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
    public static void drawUnwrappedText(PDPageContentStream contentStream,
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

    // 获取字体大小（保持不变）
    public static float getFontSize(io.nop.excel.model.ExcelFont excelFont) {
        return excelFont != null && excelFont.getFontSize() > 0 ? excelFont.getFontSize() : 10;
    }

    // 绘制边框
    public static void drawBorder(PDPageContentStream contentStream,
                                  PDRectangle cellRect,
                                  ExcelStyle style) throws IOException {
        if (style == null) return;

        // 绘制上边框
        drawBorderLine(contentStream,
                cellRect.getLowerLeftX(), cellRect.getUpperRightY(),
                cellRect.getUpperRightX(), cellRect.getUpperRightY(),
                style.getTopBorder());

        // 绘制右边框
        drawBorderLine(contentStream,
                cellRect.getUpperRightX(), cellRect.getUpperRightY(),
                cellRect.getUpperRightX(), cellRect.getLowerLeftY(),
                style.getRightBorder());

        // 绘制下边框
        drawBorderLine(contentStream,
                cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                cellRect.getUpperRightX(), cellRect.getLowerLeftY(),
                style.getBottomBorder());

        // 绘制左边框
        drawBorderLine(contentStream,
                cellRect.getLowerLeftX(), cellRect.getUpperRightY(),
                cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                style.getLeftBorder());

        // 绘制对角线（如果有）
        if (style.getDiagonalLeftBorder() != null) {
            drawBorderLine(contentStream,
                    cellRect.getLowerLeftX(), cellRect.getUpperRightY(),
                    cellRect.getUpperRightX(), cellRect.getLowerLeftY(),
                    style.getDiagonalLeftBorder());
        }

        if (style.getDiagonalRightBorder() != null) {
            drawBorderLine(contentStream,
                    cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                    cellRect.getUpperRightX(), cellRect.getUpperRightY(),
                    style.getDiagonalRightBorder());
        }
    }


    private static void drawBorderLine(PDPageContentStream contentStream,
                                       float x1, float y1,
                                       float x2, float y2,
                                       ExcelBorderStyle borderStyle) throws IOException {
        if (borderStyle == null || borderStyle.getType() == ExcelLineStyle.NONE) {
            return;
        }

        Color color = parseColor(borderStyle.getColor());
        if (color == null) {
            color = Color.BLACK;
        }

        float lineWidth = getLineWidth(borderStyle);
        contentStream.setStrokingColor(color);
        contentStream.setLineWidth(lineWidth);

        // 处理不同的线型
        switch (borderStyle.getType()) {
            case DASHED:
                contentStream.setLineDashPattern(new float[]{3}, 0);
                break;
            case DOTTED:
                contentStream.setLineDashPattern(new float[]{1}, 0);
                break;
            case DASH_DOT:
                contentStream.setLineDashPattern(new float[]{3, 1, 1, 1}, 0);
                break;
            case DASH_DOT_DOT:
                contentStream.setLineDashPattern(new float[]{3, 1, 1, 1, 1, 1}, 0);
                break;
            case DOUBLE:
                // 双线需要绘制两次
                drawDoubleLine(contentStream, x1, y1, x2, y2, lineWidth);
                return;
            default:
                contentStream.setLineDashPattern(new float[]{}, 0);
        }

        contentStream.moveTo(x1, y1);
        contentStream.lineTo(x2, y2);
        contentStream.stroke();
    }

    private static void drawDoubleLine(PDPageContentStream contentStream,
                                       float x1, float y1, float x2, float y2,
                                       float lineWidth) throws IOException {
        // 计算线的方向向量
        float dx = x2 - x1;
        float dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;

        // 计算垂直方向的偏移量
        float offsetX = (dy / (float) length) * lineWidth;
        float offsetY = (-dx / (float) length) * lineWidth;

        // 绘制第一条线
        contentStream.moveTo(x1 + offsetX, y1 + offsetY);
        contentStream.lineTo(x2 + offsetX, y2 + offsetY);
        contentStream.stroke();

        // 绘制第二条线
        contentStream.moveTo(x1 - offsetX, y1 - offsetY);
        contentStream.lineTo(x2 - offsetX, y2 - offsetY);
        contentStream.stroke();
    }

    // 将Excel颜色字符串转换为AWT Color
    public static Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) {
            return null;
        }

        try {
            colorStr = colorStr.trim().toLowerCase();

            // 处理命名颜色
            switch (colorStr) {
                case "black":
                    return Color.BLACK;
                case "white":
                    return Color.WHITE;
                case "red":
                    return Color.RED;
                case "green":
                    return Color.GREEN;
                case "blue":
                    return Color.BLUE;
                case "yellow":
                    return Color.YELLOW;
                case "gray":
                    return Color.GRAY;
            }

            if (colorStr.startsWith("#")) {
                return Color.decode(colorStr);
            } else if (colorStr.startsWith("rgb(")) {
                String[] parts = colorStr.substring(4, colorStr.length() - 1).split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Color(r, g, b);
            } else if (colorStr.startsWith("rgba(")) {
                String[] parts = colorStr.substring(5, colorStr.length() - 1).split(",");
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                float a = Float.parseFloat(parts[3].trim());
                return new Color(r, g, b, (int) (a * 255));
            }
        } catch (Exception e) {
            // 如果解析失败，返回null
        }
        return null;
    }

    private static float getLineWidth(ExcelBorderStyle borderStyle) {
        switch (borderStyle.getType()) {
            case HAIR:
                return 0.5f;
            case SINGLE:
                return 1f;
            case MEDIUM:
                return 1.5f;
            case THICK:
                return 2f;
            case DOUBLE:
                return 1.5f; // 双线需要特殊处理
            default:
                return 1f;
        }
    }
}