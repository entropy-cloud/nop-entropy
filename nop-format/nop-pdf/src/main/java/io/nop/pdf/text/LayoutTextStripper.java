package io.nop.pdf.text;

import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.List;

/**
 * WI16：-layout 等价 stripper——writeString 内按相邻 TextPosition 的 x 间隙重建
 * 多空格（保留 run，不做空白归一——R1-M1）。y 聚类由 PDFTextStripper 内建
 * sortByPosition + 行分隔处理。
 *
 * <p>同时统计 toUnicode==null 字形事件（乱码可观测点，R1-M2）与 U+FFFD 字面计数。
 */
public class LayoutTextStripper extends PDFTextStripper {

    private int unmappedCount;
    private float lastEndX = -1;

    public LayoutTextStripper() throws IOException {
        setSortByPosition(true);
        setLineSeparator("\n");
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
        if (!positions.isEmpty()) {
            float startX = positions.get(0).getXDirAdj();
            float gap = startX - lastEndX;
            // 跨 writeString 调用跟踪 x 间隙（定位间隙→多空格重建，R2-Major-2 契约）
            if (lastEndX >= 0 && gap > 12) {
                int spaces = Math.min(48, Math.max(2, (int) (gap / 6)));
                output.write(" ".repeat(spaces));
            }
            TextPosition last = positions.get(positions.size() - 1);
            lastEndX = last.getXDirAdj() + last.getWidthDirAdj();
        }
        super.writeString(text, positions);
    }

    @Override
    protected void writeLineSeparator() throws IOException {
        lastEndX = -1; // 行分隔重置 x 追踪
        super.writeLineSeparator();
    }

    @Override
    protected void showGlyph(org.apache.pdfbox.util.Matrix textRenderingMatrix,
                             org.apache.pdfbox.pdmodel.font.PDFont font, int code,
                             org.apache.pdfbox.util.Vector displacement) throws IOException {
        super.showGlyph(textRenderingMatrix, font, code, displacement);
        if (font.toUnicode(code) == null)
            unmappedCount++;
    }

    public int getUnmappedCount() {
        return unmappedCount;
    }
}
