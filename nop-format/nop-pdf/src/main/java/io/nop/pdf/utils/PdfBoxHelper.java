package io.nop.pdf.utils;

import io.nop.api.core.beans.geometry.RectangleBean;
import io.nop.api.core.beans.geometry.SizeBean;
import org.apache.fontbox.util.BoundingBox;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;
import org.apache.pdfbox.pdmodel.font.PDType3Font;

import java.io.IOException;

public class PdfBoxHelper {

    public static PDRectangle toRectangle(SizeBean size) {
        return new PDRectangle(0, 0, (float) size.getWidth(), (float) size.getHeight());
    }

    public static PDRectangle toRectangle(RectangleBean rect) {
        return new PDRectangle((float) rect.getX(), (float) rect.getY(), (float) rect.getWidth(), (float) rect.getHeight());
    }

    public static float computeFontHeight(PDFont font) throws IOException {
        BoundingBox bbox = font.getBoundingBox();
        if (bbox.getLowerLeftY() < Short.MIN_VALUE) {
            // PDFBOX-2158 and PDFBOX-3130
            // files by Salmat eSolutions / ClibPDF Library
            bbox.setLowerLeftY(-(bbox.getLowerLeftY() + 65536));
        }
        // 1/2 the bbox is used as the height todo: why?
        float glyphHeight = bbox.getHeight() / 2;

        // sometimes the bbox has very high values, but CapHeight is OK
        PDFontDescriptor fontDescriptor = font.getFontDescriptor();
        if (fontDescriptor != null) {
            float capHeight = fontDescriptor.getCapHeight();
            if (Float.compare(capHeight, 0) != 0 &&
                    (capHeight < glyphHeight || Float.compare(glyphHeight, 0) == 0)) {
                glyphHeight = capHeight;
            }
            // PDFBOX-3464, PDFBOX-448:
            // sometimes even CapHeight has very high value, but Ascent and Descent are ok
            float ascent = fontDescriptor.getAscent();
            float descent = fontDescriptor.getDescent();
            if (ascent > 0 && descent < 0 &&
                    ((ascent - descent) / 2 < glyphHeight || Float.compare(glyphHeight, 0) == 0)) {
                glyphHeight = (ascent - descent) / 2;
            }
        }

        // transformPoint from glyph space -> text space
        float height;
        if (font instanceof PDType3Font) {
            height = font.getFontMatrix().transformPoint(0, glyphHeight).y;
        } else {
            height = glyphHeight / 1000;
        }

        return height;
    }
}
