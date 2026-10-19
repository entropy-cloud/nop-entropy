package io.nop.pdf.text;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.util.Matrix;
import org.apache.pdfbox.util.Vector;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;

/**
 * WI16：strip 模式 stripper——pdfio pageText 直译前置（行分隔固定 \n；乱码双路计数）。
 * 空白归一（[ \t]+→单空格）与 trim 由抽取器在拿到全文后统一做（对齐 pdfio 时序）。
 */
public class StripTextStripper extends PDFTextStripper {

    private int unmappedCount;
    private int fffdCount;

    public StripTextStripper() throws IOException {
        setSortByPosition(true);
        setLineSeparator("\n");
    }

    @Override
    protected void showGlyph(Matrix textRenderingMatrix, PDFont font, int code,
                             Vector displacement) throws IOException {
        super.showGlyph(textRenderingMatrix, font, code, displacement);
        if (font.toUnicode(code) == null)
            unmappedCount++;
    }

    @Override
    protected void writeString(String text) throws IOException {
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\uFFFD')
                fffdCount++;
        super.writeString(text);
    }

    public int getUnmappedCount() {
        return unmappedCount;
    }

    public int getFffdCount() {
        return fffdCount;
    }
}
