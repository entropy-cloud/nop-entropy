package io.nop.pdf.render;

import io.nop.core.unittest.BaseTestCase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TestPdfTable extends BaseTestCase {

    @Test
    public void testGenTable() throws IOException {
        // 创建新文档
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            // 定义表格数据
            List<List<String>> data = new ArrayList<>();
            data.add(List.of("A", "B", "C", "D")); // 表头
            data.add(List.of("1", "x", "28", "f"));
            data.add(List.of("2", "f", "32", "g"));
            data.add(List.of("3", "z", "25", "s"));

            // 创建表格
            float margin = 50;
            float yStart = page.getMediaBox().getHeight() - margin;
            float tableWidth = page.getMediaBox().getWidth() - 2 * margin;
            float rowHeight = 30f;
            int rowsPerPage = (int) ((yStart - margin) / rowHeight);

            // 绘制表格
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                drawTable(contentStream, yStart, tableWidth, margin, rowHeight, data, rowsPerPage);
            }

            // 保存文档
            document.save(getTargetFile("styled_table.pdf"));
        }
    }

    private static void drawTable(PDPageContentStream contentStream, float yStart, float tableWidth,
                                  float margin, float rowHeight, List<List<String>> data, int rowsPerPage) throws IOException {
        final int cols = data.get(0).size();
        final float colWidth = tableWidth / cols;

        // 设置字体样式
        PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
        PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.COURIER);
        float fontSize = 12;

        // 绘制表格行
        float y = yStart;
        for (int i = 0; i < data.size(); i++) {
            List<String> row = data.get(i);
            float x = margin;

            // 设置行背景色
            if (i % 2 == 1) {
                contentStream.setNonStrokingColor(240 / 255f, 240 / 255f, 240 / 255f); // 浅灰色背景
                contentStream.addRect(x, y - rowHeight, tableWidth, rowHeight);
                contentStream.fill();
            }

            // 绘制单元格内容和边框
            for (int j = 0; j < row.size(); j++) {
                String text = row.get(j);

                // 设置字体和颜色
                if (i == 0) { // 表头样式
                    contentStream.setFont(headerFont, fontSize);
                    contentStream.setNonStrokingColor(0, 0, 0); // 黑色
                } else { // 内容样式
                    contentStream.setFont(bodyFont, fontSize);
                    if (j == 2) { // 年龄列特殊样式
                        contentStream.setNonStrokingColor(255 / 255f, 0, 0); // 红色
                    } else {
                        contentStream.setNonStrokingColor(0, 0, 0); // 黑色
                    }
                }

                // 绘制文本（居中）
                float textWidth = headerFont.getStringWidth(text) / 1000 * fontSize;
                float textX = x + (colWidth - textWidth) / 2;
                float textY = y - 15; // 垂直居中调整
                contentStream.beginText();
                contentStream.newLineAtOffset(textX, textY);
                contentStream.showText(text);
                contentStream.endText();

                // 绘制单元格边框
                contentStream.setStrokingColor(150 / 255f, 150 / 255f, 150 / 255f); // 灰色边框
                contentStream.setLineWidth(0.5f);
                contentStream.addRect(x, y - rowHeight, colWidth, rowHeight);
                contentStream.stroke();

                x += colWidth;
            }

            y -= rowHeight;
        }
    }
}