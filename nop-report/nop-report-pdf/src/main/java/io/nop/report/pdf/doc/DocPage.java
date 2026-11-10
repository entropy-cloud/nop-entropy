package io.nop.report.pdf.doc;

import java.util.ArrayList;
import java.util.List;

/**
 * One laid-out page: section geometry plus the placed header / body / footer boxes.
 */
public final class DocPage {
    private final int sectionIndex;
    private final DocPageGeometry geometry;
    private final List<DocBox.Placed> header;
    private final List<DocBox.Placed> footer;
    private final List<DocBox.Placed> body = new ArrayList<>();

    public DocPage(int sectionIndex, DocPageGeometry geometry, List<DocBox.Placed> header,
                   List<DocBox.Placed> footer) {
        this.sectionIndex = sectionIndex;
        this.geometry = geometry;
        this.header = header;
        this.footer = footer;
    }

    public int getSectionIndex() {
        return sectionIndex;
    }

    public DocPageGeometry getGeometry() {
        return geometry;
    }

    public List<DocBox.Placed> getHeader() {
        return header;
    }

    public List<DocBox.Placed> getFooter() {
        return footer;
    }

    public List<DocBox.Placed> getBody() {
        return body;
    }

    /** Texts of the body paragraph lines on this page, in reading order. */
    public List<String> getBodyLineTexts() {
        List<String> ret = new ArrayList<>();
        for (DocBox.Placed placed : body) {
            if (placed.getBox() instanceof DocBox.Paragraph)
                ret.addAll(((DocBox.Paragraph) placed.getBox()).getLineTexts());
        }
        return ret;
    }

    public int getBodyLineCount() {
        return getBodyLineTexts().size();
    }
}
