package io.nop.report.pdf.utils;

import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import io.nop.excel.model.constants.ExcelHorizontalAlignment;
import io.nop.excel.model.constants.ExcelPaperSize;
import io.nop.excel.model.constants.ExcelVerticalAlignment;

public class ExcelPdfHelper {
    public static Rectangle getPageSize(ExcelPaperSize paperSize, boolean horizontal) {
        if (paperSize == null)
            paperSize = ExcelPaperSize.A4_PAPER;

        if (horizontal) {
            Rectangle rect = new Rectangle(paperSize.getHeight(), paperSize.getWidth());
            rect.rotate();
            return rect;
        } else {
            Rectangle rect = new Rectangle(paperSize.getWidth(), paperSize.getHeight());
            return rect;
        }
    }

    public static int getVerticalAlign(ExcelVerticalAlignment align) {
        switch (align) {

            case TOP:
                return Element.ALIGN_TOP;
            case CENTER:
                return Element.ALIGN_MIDDLE;
            case BOTTOM:
                return Element.ALIGN_BOTTOM;
            case JUSTIFY:
                return Element.ALIGN_JUSTIFIED;
            case DISTRIBUTED:
                return Element.ALIGN_JUSTIFIED_ALL;
            default:
                return Element.ALIGN_UNDEFINED;
        }
    }

    public static int getHorizontalAlign(ExcelHorizontalAlignment align) {
        switch (align) {
            case LEFT:
                return Element.ALIGN_LEFT;
            case CENTER:
                return Element.ALIGN_CENTER;
            case RIGHT:
                return Element.ALIGN_RIGHT;
            case JUSTIFY:
                return Element.ALIGN_JUSTIFIED;
            default:
                return Element.ALIGN_LEFT;
        }
    }
}
