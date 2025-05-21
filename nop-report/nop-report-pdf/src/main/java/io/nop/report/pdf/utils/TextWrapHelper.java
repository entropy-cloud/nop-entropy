package io.nop.report.pdf.utils;

import org.apache.pdfbox.pdmodel.font.PDFont;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TextWrapHelper {
    
    /**
     * 将文本分割成多行以适应指定宽度
     * @param text 要分割的文本
     * @param font PDF字体对象
     * @param fontSize 字体大小
     * @param maxWidth 最大允许宽度
     * @param wrapMode 折行模式：0=按单词折行，1=按字符折行，2=强制折行(允许单词中断)
     * @return 分割后的行列表
     * @throws IOException 如果计算字符串宽度时出错
     */
    public static List<String> splitTextIntoLines(String text, PDFont font, 
                                               float fontSize, float maxWidth, 
                                               int wrapMode) throws IOException {
        List<String> lines = new ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        
        // 处理换行符，先按自然换行分割
        String[] paragraphs = text.split("\\r?\\n");
        
        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            
            switch (wrapMode) {
                case 0:
                    lines.addAll(wrapByWord(paragraph, font, fontSize, maxWidth));
                    break;
                case 1:
                    lines.addAll(wrapByCharacter(paragraph, font, fontSize, maxWidth));
                    break;
                case 2:
                    lines.addAll(wrapForced(paragraph, font, fontSize, maxWidth));
                    break;
                default:
                    lines.addAll(wrapByWord(paragraph, font, fontSize, maxWidth));
            }
        }
        
        return lines;
    }
    
    // 按单词折行（优先在空格处折行）
    private static List<String> wrapByWord(String text, PDFont font,
                                         float fontSize, float maxWidth) 
            throws IOException {
        List<String> lines = new ArrayList<>();
        int lastSpace = -1;
        float currentWidth = 0;
        int lineStart = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charWidth = font.getStringWidth(String.valueOf(c)) / 1000 * fontSize;
            
            // 记录最后一个空格位置
            if (Character.isWhitespace(c)) {
                lastSpace = i;
            }
            
            // 如果当前宽度超过最大宽度
            if (currentWidth + charWidth > maxWidth) {
                if (lastSpace >= lineStart) {
                    // 在最后一个空格处折行
                    lines.add(text.substring(lineStart, lastSpace).trim());
                    lineStart = lastSpace + 1;
                    i = lineStart - 1; // 重置i到新行开始位置
                } else {
                    // 没有空格，只能强制在当前位置折行
                    lines.add(text.substring(lineStart, i));
                    lineStart = i;
                    i--; // 重新处理当前字符
                }
                currentWidth = 0;
                lastSpace = -1;
                continue;
            }
            
            currentWidth += charWidth;
        }
        
        // 添加最后一行
        if (lineStart < text.length()) {
            lines.add(text.substring(lineStart).trim());
        }
        
        return lines;
    }
    
    // 按字符折行（不考虑单词边界）
    private static List<String> wrapByCharacter(String text, PDFont font,
                                              float fontSize, float maxWidth) 
            throws IOException {
        List<String> lines = new ArrayList<>();
        float currentWidth = 0;
        int lineStart = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charWidth = font.getStringWidth(String.valueOf(c)) / 1000 * fontSize;
            
            if (currentWidth + charWidth > maxWidth) {
                lines.add(text.substring(lineStart, i));
                lineStart = i;
                currentWidth = 0;
            }
            
            currentWidth += charWidth;
        }
        
        if (lineStart < text.length()) {
            lines.add(text.substring(lineStart));
        }
        
        return lines;
    }
    
    // 强制折行（允许在单词中间断开）
    private static List<String> wrapForced(String text, PDFont font,
                                        float fontSize, float maxWidth) 
            throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        float currentWidth = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float charWidth = font.getStringWidth(String.valueOf(c)) / 1000 * fontSize;
            
            if (currentWidth + charWidth > maxWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
                currentWidth = 0;
            }
            
            currentLine.append(c);
            currentWidth += charWidth;
        }
        
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        
        return lines;
    }
    
    /**
     * 计算文本块的总高度（包括行间距）
     * @param lines 文本行列表
     * @param font PDF字体对象
     * @param fontSize 字体大小
     * @param lineSpacing 行间距（倍数，1.0表示单倍行距）
     * @return 总高度
     */
    public static float calculateTextBlockHeight(List<String> lines, PDFont font,
                                              float fontSize, float lineSpacing) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }
        
        float lineHeight = font.getFontDescriptor().getCapHeight() / 1000 * fontSize;
        return lineHeight * lines.size() * lineSpacing;
    }
}