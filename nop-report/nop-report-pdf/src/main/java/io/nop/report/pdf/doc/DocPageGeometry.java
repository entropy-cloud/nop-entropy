package io.nop.report.pdf.doc;

import io.nop.api.core.exceptions.NopException;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficePageMargins;
import io.nop.report.pdf.ReportPdfErrors;

/**
 * Page geometry of one section in pt. The layout coordinate system has its origin at
 * the top-left page corner with y growing downwards; {@link #toPdfY(float)} converts
 * to PDF user space (origin bottom-left).
 */
public final class DocPageGeometry {
    /** A4 portrait, used when neither the section nor the document declares a size. */
    public static final float DEFAULT_PAGE_WIDTH = 595.3f;
    public static final float DEFAULT_PAGE_HEIGHT = 841.9f;
    /** Word defaults: 1 inch body margins, 0.5 inch header / footer distance. */
    public static final float DEFAULT_MARGIN = 72f;
    public static final float DEFAULT_HEADER_FOOTER_OFFSET = 36f;

    public static final String ORIENTATION_LANDSCAPE = "landscape";
    public static final String ORIENTATION_PORTRAIT = "portrait";

    private final float pageWidth;
    private final float pageHeight;
    private final float marginTop;
    private final float marginBottom;
    private final float marginLeft;
    private final float marginRight;
    private final float headerOffset;
    private final float footerOffset;

    public DocPageGeometry(float pageWidth, float pageHeight, float marginTop, float marginBottom,
                           float marginLeft, float marginRight, float headerOffset, float footerOffset) {
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.marginTop = marginTop;
        this.marginBottom = marginBottom;
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;
        this.headerOffset = headerOffset;
        this.footerOffset = footerOffset;
    }

    /**
     * Section geometry: section width/height fall back to the document values, then to
     * A4; the orientation swaps the two sides when they disagree with it; a missing
     * margins node falls back to the Word defaults; the gutter widens the left margin.
     */
    public static DocPageGeometry of(OfficeDocModel doc, OfficeDocPageModel page, int sectionIndex) {
        float width = pick(page == null ? null : page.getWidth(), doc.getWidth(), DEFAULT_PAGE_WIDTH);
        float height = pick(page == null ? null : page.getHeight(), doc.getHeight(), DEFAULT_PAGE_HEIGHT);

        String orientation = page == null ? null : page.getOrientation();
        if (ORIENTATION_LANDSCAPE.equalsIgnoreCase(orientation) && width < height
                || ORIENTATION_PORTRAIT.equalsIgnoreCase(orientation) && width > height) {
            float tmp = width;
            width = height;
            height = tmp;
        }

        OfficePageMargins margins = page == null ? null : page.getMargins();
        DocPageGeometry geometry;
        if (margins == null) {
            geometry = new DocPageGeometry(width, height, DEFAULT_MARGIN, DEFAULT_MARGIN, DEFAULT_MARGIN,
                    DEFAULT_MARGIN, DEFAULT_HEADER_FOOTER_OFFSET, DEFAULT_HEADER_FOOTER_OFFSET);
        } else {
            geometry = new DocPageGeometry(width, height, (float) margins.getTop(), (float) margins.getBottom(),
                    (float) (margins.getLeft() + margins.getGutter()), (float) margins.getRight(),
                    (float) margins.getHeader(), (float) margins.getFooter());
        }
        if (geometry.getContentWidth() <= 0 || geometry.getContentHeight() <= 0)
            throw new NopException(ReportPdfErrors.ERR_PDF_DOC_INVALID_PAGE_GEOMETRY)
                    .param(ReportPdfErrors.ARG_BLOCK_TYPE, String.valueOf(sectionIndex));
        return geometry;
    }

    private static float pick(Double sectionValue, double docValue, float fallback) {
        if (sectionValue != null && sectionValue > 0)
            return sectionValue.floatValue();
        if (docValue > 0)
            return (float) docValue;
        return fallback;
    }

    public float getPageWidth() {
        return pageWidth;
    }

    public float getPageHeight() {
        return pageHeight;
    }

    public float getMarginTop() {
        return marginTop;
    }

    public float getMarginBottom() {
        return marginBottom;
    }

    public float getMarginLeft() {
        return marginLeft;
    }

    public float getMarginRight() {
        return marginRight;
    }

    public float getHeaderOffset() {
        return headerOffset;
    }

    public float getFooterOffset() {
        return footerOffset;
    }

    public float getContentWidth() {
        return pageWidth - marginLeft - marginRight;
    }

    public float getContentHeight() {
        return pageHeight - marginTop - marginBottom;
    }

    /** Lower limit (top-origin y) of the body area. */
    public float getContentBottom() {
        return pageHeight - marginBottom;
    }

    /** Convert a top-origin y to PDF user space. */
    public float toPdfY(float y) {
        return pageHeight - y;
    }
}
