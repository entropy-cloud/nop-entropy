package io.nop.report.pdf.utils;

import io.nop.api.core.beans.geometry.RectangleBean;
import io.nop.api.core.beans.geometry.SizeBean;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public class PdfPrintHelper {
    public static PDRectangle toRectangle(SizeBean size) {
        return new PDRectangle(0, 0, (float) size.getWidth(), (float) size.getHeight());
    }

    public static PDRectangle toRectangle(RectangleBean rect) {
        return new PDRectangle((float) rect.getX(), (float) rect.getY(), (float) rect.getWidth(), (float) rect.getHeight());
    }
}
