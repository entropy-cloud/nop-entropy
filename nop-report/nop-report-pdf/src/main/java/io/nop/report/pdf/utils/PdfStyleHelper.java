package io.nop.report.pdf.utils;

import io.nop.excel.model.ExcelStyle;
import io.nop.excel.model.color.ColorHelper;
import io.nop.office.model.OfficeBorderStyle;
import io.nop.office.model.constants.OfficeHorizontalAlignment;
import io.nop.office.model.constants.OfficeLineStyle;
import io.nop.office.model.constants.OfficeVerticalAlignment;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.util.Matrix;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public class PdfStyleHelper {

    // 将Excel颜色代码转换为PDFBox颜色
    public static PDColor convertColor(String excelColor) {
        float[] components = ColorHelper.toNormalizedRgb(excelColor);
        return new PDColor(components, PDDeviceRGB.INSTANCE);
    }

    // WI9：当前渲染页（PdfSheetRenderer 每页设置/清理），用于 alpha 时注册 ExtGState 到页面 Resources
    private static final ThreadLocal<PDPage> CURRENT_PAGE = new ThreadLocal<>();

    public static void setCurrentPage(PDPage page) {
        CURRENT_PAGE.set(page);
    }

    public static void clearCurrentPage() {
        CURRENT_PAGE.remove();
    }

    /** 解析颜色的 alpha 分量（0-255；解析失败视为不透明 255）。 */
    private static int parseAlpha(String colorStr) {
        int[] rgba = ColorHelper.parseRgba(colorStr);
        return rgba == null ? 255 : rgba[3];
    }

    /**
     * WI9：alpha<255 且当前页已知时，注入 ExtGState（ca/CA）到页面 Resources。
     * 调用方须已在 saveGraphicsState 之后调用本方法，并在绘制后 restoreGraphicsState
     * 收口 gs 作用域（gs 注入先于 save 会修改当前态导致泄漏）。
     *
     * @return true 表示已注入
     */
    private static boolean applyAlpha(PDPageContentStream contentStream, int alpha) {
        if (alpha >= 255)
            return false;
        PDPage page = CURRENT_PAGE.get();
        if (page == null || page.getResources() == null)
            return false;
        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(alpha / 255f);
        gs.setStrokingAlphaConstant(alpha / 255f);
        page.getResources().add(gs);
        try {
            contentStream.setGraphicsStateParameters(gs);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to set ExtGState for alpha", e);
        }
        return true;
    }

    // 设置单元格背景
    public static void setCellBackground(PDPageContentStream contentStream,
                                         ExcelStyle style,
                                         float x, float y,
                                         float width, float height) throws IOException {
        if (style != null && style.getFillFgColor() != null && !style.getFillFgColor().isEmpty()) {
            // 使用fgColor而不是bgColor
            PDColor fgColor = convertColor(style.getFillFgColor());
            int alpha = parseAlpha(style.getFillFgColor());
            boolean alphaApplied = alpha < 255;
            if (alphaApplied)
                contentStream.saveGraphicsState();
            applyAlpha(contentStream, alpha);
            contentStream.setNonStrokingColor(fgColor);
            contentStream.addRect(x, y, width, height);
            contentStream.fill();
            if (alphaApplied)
                contentStream.restoreGraphicsState();
        }
    }

    // 计算文本位置（修正版）
    public static float[] calculateTextPosition(String text, PDFont font, float fontSize,
                                                float cellX, float cellY, float cellWidth, float cellHeight,
                                                OfficeHorizontalAlignment hAlign, OfficeVerticalAlignment vAlign) {
        try {
            float textWidth = font.getStringWidth(text) / 1000 * fontSize;
            float textHeight = font.getFontDescriptor().getCapHeight() / 1000 * fontSize;

            float x = cellX + 2; // 默认左对齐，左边距2pt
            if (hAlign == OfficeHorizontalAlignment.CENTER) {
                x = cellX + (cellWidth - textWidth) / 2;
            } else if (hAlign == OfficeHorizontalAlignment.RIGHT) {
                x = cellX + cellWidth - textWidth - 2; // 右边距2pt
            }

            float y = cellY + cellHeight - textHeight - 2; // 默认顶部对齐，上边距2pt
            if (vAlign == OfficeVerticalAlignment.CENTER) {
                y = cellY + (cellHeight - textHeight) / 2;
            } else if (vAlign == OfficeVerticalAlignment.BOTTOM) {
                y = cellY + textHeight + 2; // 下边距2pt
            }

            return new float[]{x, y};
        } catch (IOException e) {
            return new float[]{cellX + 2, cellY + cellHeight - 10}; // 默认位置
        }
    }

    public static void drawText(PDPageContentStream contentStream, String text, PDFont font,
                                float fontSize, PDRectangle cellRect, ExcelStyle style) throws IOException {
        drawText(contentStream, text, font, false, false, fontSize, cellRect, style);
    }

    /**
     * WI10：带合成标志的文本绘制——syntheticBold 走 FILL_STROKE 伪粗体（描边色随字体色、
     * dash 重置），syntheticItalic 走 shear 文本矩阵伪斜体。
     */
    public static void drawText(PDPageContentStream contentStream, String text, PDFont font,
                                boolean syntheticBold, boolean syntheticItalic,
                                float fontSize, PDRectangle cellRect, ExcelStyle style) throws IOException {
        // 注意：此处不做控制字符归一化——wrapText路径的splitTextIntoLines依赖\n拆分显式换行，
        // 控制字符由两个子方法在各自绘制粒度上归一化（孤立即\r在分段后仍留在行内，需行级处理）
        if (style != null && style.isWrapText()) {
            drawWrappedText(contentStream, text, font, syntheticBold, syntheticItalic, fontSize, cellRect, style);
        } else {
            drawUnwrappedText(contentStream, text, font, syntheticBold, syntheticItalic, fontSize, cellRect, style);
        }
    }

    /**
     * 字体无法编码的字符（例如回退到Helvetica时遇到中文）会导致PDFBox抛出IllegalArgumentException，
     * 使整个导出失败。这里将无法编码的字符替换为?，保证导出可以完成。
     */
    public static String sanitizeTextForFont(String text, PDFont font) {
        if (text == null || text.isEmpty())
            return text;
        try {
            font.encode(text);
            return text;
        } catch (Exception e) {
            // ignore: 逐字符过滤
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            try {
                font.encode(ch);
                sb.append(ch);
            } catch (Exception e) {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    // 绘制不折行的文本（修正版）
    public static void drawUnwrappedText(PDPageContentStream contentStream,
                                         String text,
                                         PDFont font,
                                         boolean syntheticBold, boolean syntheticItalic,
                                         float fontSize,
                                         PDRectangle cellRect,
                                         ExcelStyle style) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }

        text = sanitizeTextForFont(text, font);

        OfficeHorizontalAlignment hAlign = style != null ? style.getHorizontalAlign() : null;
        OfficeVerticalAlignment vAlign = style != null ? style.getVerticalAlign() : null;

        text = normalizeControlChars(text);

        float[] position = calculateTextPosition(text, font, fontSize,
                cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                cellRect.getWidth(), cellRect.getHeight(),
                hAlign, vAlign);

        // 裁剪到单元格矩形：长文本不得溢出到相邻单元格（审计02-p2乱块机理）
        contentStream.saveGraphicsState();
        contentStream.addRect(cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                cellRect.getWidth(), cellRect.getHeight());
        contentStream.clip();

        contentStream.setFont(font, fontSize);
        if (style != null && style.getFont() != null) {
            PDColor textColor = convertColor(style.getFont().getFontColor());
            contentStream.setNonStrokingColor(textColor);
            // WI10：伪粗体描边色随字体色 + dash 重置（防继承边框虚线/异色）
            if (syntheticBold) {
                contentStream.setStrokingColor(textColor);
                contentStream.setLineDashPattern(new float[]{}, 0);
                contentStream.setLineWidth(Math.max(0.3f, fontSize / 30));
                contentStream.setRenderingMode(RenderingMode.FILL_STROKE);
            }
            // WI9：字体色 alpha（该方法外层已有 save/clip/restore 包裹，gs 作用域随 restore 收口）
            applyAlpha(contentStream, parseAlpha(style.getFont().getFontColor()));
        }

        contentStream.beginText();
        if (syntheticItalic) {
            // WI10：伪斜体 shear（y-up 右倾）
            contentStream.setTextMatrix(new Matrix(
                    1, 0, 0.25f, 1, position[0], position[1]));
        } else {
            contentStream.newLineAtOffset(position[0], position[1]);
        }
        contentStream.showText(text);
        contentStream.endText();

        // WI10：伪粗体 renderingMode 还原（防后续绘制继承 FILL_STROKE）
        if (syntheticBold) {
            contentStream.setRenderingMode(RenderingMode.FILL);
        }

        contentStream.restoreGraphicsState();
    }

    // 绘制自动折行的文本（修正版）
    public static void drawWrappedText(PDPageContentStream contentStream,
                                       String text,
                                       PDFont font,
                                       boolean syntheticBold, boolean syntheticItalic,
                                       float fontSize,
                                       PDRectangle cellRect,
                                       ExcelStyle style) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }

        text = sanitizeTextForFont(text, font);

        OfficeHorizontalAlignment hAlign = style != null ? style.getHorizontalAlign() : null;
        OfficeVerticalAlignment vAlign = style != null ? style.getVerticalAlign() : null;

        float availableWidth = cellRect.getWidth() - 4; // 2pt边距*2
        List<String> lines = TextWrapHelper.splitTextIntoLines(text, font, fontSize, availableWidth, 2);

        float lineSpacing = 1.3f;
        float textBlockHeight = TextWrapHelper.calculateTextBlockHeight(lines, font, fontSize, lineSpacing);
        float lineHeight = font.getFontDescriptor().getCapHeight() / 1000 * fontSize * lineSpacing;

        // 修正的起始Y位置计算
        float startY = cellRect.getLowerLeftY() + cellRect.getHeight() - 2; // 从顶部开始，留2pt边距
        if (vAlign == OfficeVerticalAlignment.CENTER) {
            startY = cellRect.getLowerLeftY() + (cellRect.getHeight() + textBlockHeight) / 2;
        } else if (vAlign == OfficeVerticalAlignment.BOTTOM) {
            startY = cellRect.getLowerLeftY() + textBlockHeight + 2;
        }

        // 裁剪到单元格矩形：多行文本超出单元格高度的部分被裁掉，
        // 不得溢出覆盖下方单元格（审计02-p2/末页说明块乱块机理）
        contentStream.saveGraphicsState();
        contentStream.addRect(cellRect.getLowerLeftX(), cellRect.getLowerLeftY(),
                cellRect.getWidth(), cellRect.getHeight());
        contentStream.clip();

        contentStream.setFont(font, fontSize);
        if (style != null && style.getFont() != null) {
            PDColor textColor = convertColor(style.getFont().getFontColor());
            if (textColor != null) {
                contentStream.setNonStrokingColor(textColor);
                // WI10：伪粗体描边色随字体色 + dash 重置（同 unwrapped 路径，防继承边框虚线/异色）
                if (syntheticBold) {
                    contentStream.setStrokingColor(textColor);
                    contentStream.setLineDashPattern(new float[]{}, 0);
                    contentStream.setLineWidth(Math.max(0.3f, fontSize / 30));
                    contentStream.setRenderingMode(RenderingMode.FILL_STROKE);
                }
            }
        }

        for (String line : lines) {
            if (line.isEmpty()) {
                startY -= lineHeight;
                continue;
            }

            float lineWidth = font.getStringWidth(line) / 1000 * fontSize;
            float x = cellRect.getLowerLeftX() + 2; // 左边距

            if (hAlign == OfficeHorizontalAlignment.CENTER) {
                x = cellRect.getLowerLeftX() + (cellRect.getWidth() - lineWidth) / 2;
            } else if (hAlign == OfficeHorizontalAlignment.RIGHT) {
                x = cellRect.getLowerLeftX() + cellRect.getWidth() - lineWidth - 2;
            }

            contentStream.beginText();
            if (syntheticItalic) {
                // WI10：伪斜体 shear（y-up 右倾），逐行设置行矩阵
                contentStream.setTextMatrix(new Matrix(
                        1, 0, 0.25f, 1, x, startY - fontSize));
            } else {
                contentStream.newLineAtOffset(x, startY - fontSize);
            }
            contentStream.showText(normalizeControlChars(line));
            contentStream.endText();

            startY -= lineHeight;
        }

        // WI10：伪粗体 renderingMode 还原（防后续绘制继承 FILL_STROKE）
        if (syntheticBold) {
            contentStream.setRenderingMode(RenderingMode.FILL);
        }

        contentStream.restoreGraphicsState();
    }

    /**
     * 控制字符无字形（字体encode对\r\n等直接抛异常），绘制前归一化为空格。
     * 不换行空格（U+00A0/U+202F/U+2007）同样无独立字形需求且多数CJK回退字体
     * 不收录该码点，统一归一化为普通空格，避免 font-missing-glyph 误报。
     */
    public static String normalizeControlChars(String text) {
        if (text == null || text.isEmpty())
            return text;
        if (text.indexOf('\r') < 0 && text.indexOf('\n') < 0 && text.indexOf('\t') < 0
                && text.indexOf('\u00A0') < 0 && text.indexOf('\u202F') < 0 && text.indexOf('\u2007') < 0)
            return text;
        return text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
                .replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2007', ' ');
    }

    /**
     * 缺省字号（pt）定版口径：字号缺失或 <=0 时回退 10。扫描与渲染必须同源使用。
     */
    public static final float DEFAULT_FONT_SIZE = 10;

    // 获取字体大小
    public static float getFontSize(io.nop.excel.model.ExcelFont excelFont) {
        return normalizeFontSize(excelFont != null ? excelFont.getFontSize() : null);
    }

    /**
     * 字号口径唯一定版：null 或 <=0 回退 {@link #DEFAULT_FONT_SIZE}。
     */
    public static float normalizeFontSize(Float fontSize) {
        return fontSize != null && fontSize > 0 ? fontSize : DEFAULT_FONT_SIZE;
    }

    // 绘制边框（修正版）
    public static void drawBorder(PDPageContentStream contentStream,
                                  float x, float y, float width, float height,
                                  ExcelStyle style) throws IOException {
        if (style == null) {
            return;
        }

        // 绘制上边框
        if (style.getTopBorder() != null) {
            drawBorderLine(contentStream, x, y + height, x + width, y + height, style.getTopBorder());
        }
        // 绘制右边框
        if (style.getRightBorder() != null) {
            drawBorderLine(contentStream, x + width, y + height, x + width, y, style.getRightBorder());
        }
        // 绘制下边框
        if (style.getBottomBorder() != null) {
            drawBorderLine(contentStream, x, y, x + width, y, style.getBottomBorder());
        }
        // 绘制左边框
        if (style.getLeftBorder() != null) {
            drawBorderLine(contentStream, x, y + height, x, y, style.getLeftBorder());
        }

        // 绘制对角线（左上到右下）
        if (style.getDiagonalLeftBorder() != null) {
            drawBorderLine(contentStream, x, y + height, x + width, y, style.getDiagonalLeftBorder());
        }

        // 绘制对角线（右上到左下）
        if (style.getDiagonalRightBorder() != null) {
            drawBorderLine(contentStream, x + width, y + height, x, y, style.getDiagonalRightBorder());
        }
    }

    private static void drawBorderLine(PDPageContentStream contentStream,
                                       float x1, float y1,
                                       float x2, float y2,
                                       OfficeBorderStyle borderStyle) throws IOException {
        if (borderStyle == null || borderStyle.getType() == OfficeLineStyle.NONE) {
            return;
        }

        Color color = parseColor(borderStyle.getColor());
        if (color == null) {
            color = Color.BLACK;
        }

        float lineWidth = getLineWidth(borderStyle);
        int alpha = parseAlpha(borderStyle.getColor());
        boolean alphaApplied = alpha < 255;
        if (alphaApplied)
            contentStream.saveGraphicsState();
        applyAlpha(contentStream, alpha);
        contentStream.setStrokingColor(color);
        contentStream.setLineWidth(lineWidth);

        // WI8：虚线节距随线宽比例化（单位=线宽 w），下限 0.5 避免 hair 线糊死
        float dw = Math.max(0.5f, lineWidth);
        switch (borderStyle.getType()) {
            case DASHED:
                contentStream.setLineDashPattern(new float[]{3 * dw}, 0);
                break;
            case DOTTED:
            case HAIR:
                contentStream.setLineDashPattern(new float[]{dw}, 0);
                break;
            case DASH_DOT:
                contentStream.setLineDashPattern(new float[]{3 * dw, dw, dw, dw}, 0);
                break;
            case DASH_DOT_DOT:
                contentStream.setLineDashPattern(new float[]{3 * dw, dw, dw, dw, dw, dw}, 0);
                break;
            case DOUBLE:
                contentStream.setLineDashPattern(new float[]{}, 0);
                drawDoubleLine(contentStream, x1, y1, x2, y2, lineWidth);
                if (alphaApplied)
                    contentStream.restoreGraphicsState();
                return;
            default:
                contentStream.setLineDashPattern(new float[]{}, 0);
        }

        contentStream.moveTo(x1, y1);
        contentStream.lineTo(x2, y2);
        contentStream.stroke();
        if (alphaApplied)
            contentStream.restoreGraphicsState();
    }

    private static void drawDoubleLine(PDPageContentStream contentStream,
                                       float x1, float y1, float x2, float y2,
                                       float lineWidth) throws IOException {
        float dx = x2 - x1;
        float dy = y2 - y1;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length == 0) return;

        float offsetX = (dy / (float) length) * lineWidth;
        float offsetY = (-dx / (float) length) * lineWidth;

        contentStream.moveTo(x1 + offsetX, y1 + offsetY);
        contentStream.lineTo(x2 + offsetX, y2 + offsetY);
        contentStream.stroke();

        contentStream.moveTo(x1 - offsetX, y1 - offsetY);
        contentStream.lineTo(x2 - offsetX, y2 - offsetY);
        contentStream.stroke();
    }

    // 将Excel颜色字符串转换为AWT Color（WI9：委托 ColorHelper.parseRgba 统一解析，消除双解析器分歧）
    public static Color parseColor(String colorStr) {
        int[] rgba = ColorHelper.parseRgba(colorStr);
        if (rgba == null)
            return null;
        return new Color(rgba[0], rgba[1], rgba[2], rgba[3]);
    }

    private static float getLineWidth(OfficeBorderStyle borderStyle) {
        // WI8：模型真实权重优先（解析期自 OfficeLineStyle 规范表写入，Excel 语义 thin=1/medium=2/thick=3）；
        // 程序化样式 weight=0 时回退类型规范权重；HAIR 权重 1（Excel 0.25pt 校准留后续）。
        if (borderStyle.getWeight() > 0)
            return borderStyle.getWeight();
        OfficeLineStyle type = borderStyle.getType();
        if (type == null)
            return 1f;
        return Math.max(1, type.getWeight());
    }
}
