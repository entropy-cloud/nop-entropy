package io.nop.report.pdf.doc;

import io.nop.excel.model.ExcelBorderStyle;
import io.nop.office.model.constants.OfficeLineStyle;
import io.nop.office.model.constants.OfficeVerticalAlignment;
import io.nop.office.model.OfficeBorderStyle;
import io.nop.office.model.WordBorderStyle;
import io.nop.report.pdf.utils.PdfImageHelper;
import io.nop.report.pdf.utils.PdfStyleHelper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Paints laid-out {@link DocPage}s onto PDF pages. All placement values are pt with a
 * top-left origin and are converted to PDF user space when drawn.
 *
 * <p>Run decorations drawn here (new capability, no Excel-render path equivalent):
 * underline / strikeout as stroked line segments below / across the baseline, text
 * highlight as a filled rectangle behind the glyphs, superscript / subscript as font
 * size scaling plus baseline shift, synthetic bold via FILL_STROKE and synthetic
 * italic via shear — the same technique as {@code PdfStyleHelper}.</p>
 */
public class DocPagePainter {

    /**
     * OOXML w:highlight value names not covered by {@code ColorHelper}'s named colors
     * (its "green" is 008000 while the Word highlight green is 00FF00).
     */
    private static final Map<String, String> HIGHLIGHT_COLORS = Map.ofEntries(
            Map.entry("yellow", "FFFF00"),
            Map.entry("green", "00FF00"),
            Map.entry("cyan", "00FFFF"),
            Map.entry("magenta", "FF00FF"),
            Map.entry("blue", "0000FF"),
            Map.entry("red", "FF0000"),
            Map.entry("darkblue", "000080"),
            Map.entry("darkcyan", "008080"),
            Map.entry("darkgreen", "008000"),
            Map.entry("darkmagenta", "800080"),
            Map.entry("darkred", "800000"),
            Map.entry("darkyellow", "808000"),
            Map.entry("darkgray", "808080"),
            Map.entry("darkgrey", "808080"),
            Map.entry("lightgray", "C0C0C0"),
            Map.entry("lightgrey", "C0C0C0"),
            Map.entry("black", "000000"),
            Map.entry("white", "FFFFFF"));

    /** Thickness of underline / strikeout lines, as a fraction of the font size. */
    static final float DECORATION_THICKNESS_RATIO = 1f / 14f;
    static final float MIN_DECORATION_THICKNESS = 0.5f;
    /** Underline distance below the baseline, as a fraction of the font size. */
    static final float UNDERLINE_OFFSET = 0.12f;
    /** Strikeout position above the baseline, as a fraction of the font size. */
    static final float STRIKEOUT_OFFSET = 0.3f;
    /** Highlight rectangle: how far the box extends above the baseline / below it. */
    static final float HIGHLIGHT_ABOVE = 0.85f;
    static final float HIGHLIGHT_BELOW = 0.25f;

    private final DocRenderSession session;

    public DocPagePainter(DocRenderSession session) {
        this.session = session;
    }

    /** Create the PDF page for a laid-out page and paint everything on it. */
    public void paintPage(DocPage page) throws IOException {
        PDDocument document = session.getDocument();
        DocPageGeometry geometry = page.getGeometry();
        PDPage pdPage = new PDPage(new PDRectangle(geometry.getPageWidth(), geometry.getPageHeight()));
        document.addPage(pdPage);
        List<LinkTarget> links = new ArrayList<>();

        try (PDPageContentStream cs = new PDPageContentStream(document, pdPage)) {
            paintBackground(cs, geometry);
            for (DocBox.Placed placed : page.getHeader())
                paintBox(cs, placed, geometry, links);
            for (DocBox.Placed placed : page.getFooter())
                paintBox(cs, placed, geometry, links);
            for (DocBox.Placed placed : page.getBody())
                paintBox(cs, placed, geometry, links);
        }
        for (LinkTarget target : links)
            addLinkAnnotation(pdPage, target);
    }

    private void paintBackground(PDPageContentStream cs, DocPageGeometry geometry) throws IOException {
        String background = session.getDoc().getBackground();
        if (background == null || background.isEmpty())
            return;
        Color color = PdfStyleHelper.parseColor(background);
        if (color == null)
            return;
        cs.saveGraphicsState();
        cs.setNonStrokingColor(color);
        cs.addRect(0, 0, geometry.getPageWidth(), geometry.getPageHeight());
        cs.fill();
        cs.restoreGraphicsState();
    }

    private void paintBox(PDPageContentStream cs, DocBox.Placed placed, DocPageGeometry geometry,
                          List<LinkTarget> links) throws IOException {
        if (placed.getBox() instanceof DocBox.Paragraph paragraph)
            paintParagraph(cs, paragraph, placed.getX(), placed.getY(), geometry, links);
        else if (placed.getBox() instanceof DocBox.Table table)
            paintTable(cs, table, placed.getX(), placed.getY(), geometry, links);
        else if (placed.getBox() instanceof DocBox.Image image)
            paintImage(cs, image, placed.getX(), placed.getY(), geometry);
        else if (placed.getBox() instanceof DocBox.Separator separator)
            paintSeparator(cs, separator, placed.getX(), placed.getY(), geometry);
        // DocBox.Break has no visual
    }

    private void paintImage(PDPageContentStream cs, DocBox.Image image, float x, float y,
                            DocPageGeometry geometry) throws IOException {
        PdfImageHelper.drawImage(session.getPdfRenderer(), cs, image.getData(),
                x, geometry.toPdfY(y + image.getHeight()), image.getWidth(), image.getHeight(), null);
    }

    private void paintSeparator(PDPageContentStream cs, DocBox.Separator separator, float x, float y,
                                DocPageGeometry geometry) throws IOException {
        float lineY = y + separator.getHeight() / 2;
        cs.saveGraphicsState();
        cs.setStrokingColor(Color.GRAY);
        cs.setLineWidth(1f);
        cs.setLineDashPattern(new float[]{}, 0);
        float pdfY = geometry.toPdfY(lineY);
        cs.moveTo(x, pdfY);
        cs.lineTo(x + separator.getWidth(), pdfY);
        cs.stroke();
        cs.restoreGraphicsState();
    }

    private void paintTable(PDPageContentStream cs, DocBox.Table table, float x, float y,
                            DocPageGeometry geometry, List<LinkTarget> links) throws IOException {
        float tableWidth = 0;
        for (float w : table.getColWidth())
            tableWidth += w;
        float tableHeight = table.getHeight();

        // clip the whole fragment: a truncated over-tall row must not spill past the page
        cs.saveGraphicsState();
        cs.addRect(x, geometry.toPdfY(y + tableHeight), tableWidth, tableHeight);
        cs.clip();

        float rowY = y;
        int rowIndex = 0;
        List<DocBox.Table.TableRow> rows = table.getRows();
        for (DocBox.Table.TableRow row : rows) {
            for (DocBox.Table.TableCell cell : row.getCells()) {
                float cx = x + table.getColX()[cell.getCol()];
                // vertical span clipped to the remaining rows of this fragment
                int spanRows = Math.max(1, Math.min(cell.getRowSpan(), rows.size() - rowIndex));
                float cy = rowY;
                float height = 0;
                for (int i = rowIndex; i < rowIndex + spanRows && i < rows.size(); i++)
                    height += rows.get(i).getHeight();
                paintCell(cs, table, cell, cx, cy, height, geometry, links);
            }
            rowY += row.getHeight();
            rowIndex++;
        }
        cs.restoreGraphicsState();
    }

    private void paintCell(PDPageContentStream cs, DocBox.Table table, DocBox.Table.TableCell cell,
                           float x, float y, float height, DocPageGeometry geometry,
                           List<LinkTarget> links) throws IOException {
        io.nop.office.model.WordCellStyle style = cell.getStyle();

        // shading
        if (style != null && style.getBackgroundColor() != null && !style.getBackgroundColor().isEmpty()) {
            io.nop.excel.model.ExcelStyle fill = new io.nop.excel.model.ExcelStyle();
            fill.setFillFgColor(style.getBackgroundColor());
            PdfStyleHelper.setCellBackground(cs, fill, x, geometry.toPdfY(y + height),
                    cell.getWidth(), height);
        }

        // text with vertical alignment
        float contentHeight = cell.getContentHeight();
        float textY = y + DocLayoutEngine.CELL_PADDING;
        if (cell.getVerticalAlign() == OfficeVerticalAlignment.CENTER)
            textY = y + Math.max(0, (height - contentHeight) / 2);
        else if (cell.getVerticalAlign() == OfficeVerticalAlignment.BOTTOM)
            textY = y + Math.max(DocLayoutEngine.CELL_PADDING, height - contentHeight - DocLayoutEngine.CELL_PADDING);

        cs.saveGraphicsState();
        cs.addRect(x, geometry.toPdfY(y + height), cell.getWidth(), height);
        cs.clip();
        float lineY = textY;
        for (DocLine line : cell.getLines()) {
            paintLine(cs, line, x + line.getXOffset(), lineY, geometry, links);
            lineY += line.getHeight();
        }
        cs.restoreGraphicsState();

        // borders around the (merged) cell rectangle
        io.nop.excel.model.ExcelStyle borderStyle = new io.nop.excel.model.ExcelStyle();
        boolean hasBorder = false;
        if (style != null) {
            ExcelBorderStyle top = toExcelBorder(style.getTopBorder());
            ExcelBorderStyle bottom = toExcelBorder(style.getBottomBorder());
            ExcelBorderStyle left = toExcelBorder(style.getLeftBorder());
            ExcelBorderStyle right = toExcelBorder(style.getRightBorder());
            if (top != null || bottom != null || left != null || right != null) {
                borderStyle.setTopBorder(top);
                borderStyle.setBottomBorder(bottom);
                borderStyle.setLeftBorder(left);
                borderStyle.setRightBorder(right);
                hasBorder = true;
            }
        }
        if (hasBorder)
            PdfStyleHelper.drawBorder(cs, x, geometry.toPdfY(y + height), cell.getWidth(), height, borderStyle);
    }

    private static ExcelBorderStyle toExcelBorder(WordBorderStyle border) {
        if (border == null || border.getType() == null || border.getType() == OfficeLineStyle.NONE)
            return null;
        ExcelBorderStyle ret = new ExcelBorderStyle();
        ret.setType(border.getType());
        ret.setWeight(border.getWeight());
        ret.setColor(border.getColor());
        return ret;
    }

    private void paintParagraph(PDPageContentStream cs, DocBox.Paragraph paragraph,
                                float x, float y, DocPageGeometry geometry, List<LinkTarget> links) throws IOException {
        float lineY = y;
        for (DocLine line : paragraph.getLines()) {
            paintLine(cs, line, x + line.getXOffset(), lineY, geometry, links);
            lineY += line.getHeight();
        }
    }

    void paintLine(PDPageContentStream cs, DocLine line, float x, float lineTopY,
                   DocPageGeometry geometry, List<LinkTarget> links) throws IOException {
        float baselineY = lineTopY + line.getBaseline(); // top-origin

        for (DocLine.Segment segment : line.getSegments()) {
            DocTextStyle style = segment.getStyle();
            float fontSize = style.getEffectiveFontSize();
            float textBaseline = baselineY + style.getBaselineRise();
            PDColor textColor = PdfStyleHelper.convertColor(style.getColor());
            PDFont font = segment.getFont().getFont();
            float segmentX = x + segment.getX();

            if (style.getHighlightColor() != null) {
                Color highlight = highlightColor(style.getHighlightColor());
                if (highlight != null) {
                    float top = baselineY - fontSize * HIGHLIGHT_ABOVE;
                    float bottom = baselineY + fontSize * HIGHLIGHT_BELOW;
                    cs.saveGraphicsState();
                    cs.setNonStrokingColor(highlight);
                    cs.addRect(segmentX, geometry.toPdfY(bottom),
                            segment.getWidth(), bottom - top);
                    cs.fill();
                    cs.restoreGraphicsState();
                }
            }

            cs.saveGraphicsState();
            cs.setFont(font, fontSize);
            cs.setNonStrokingColor(textColor);
            boolean syntheticBold = segment.getFont().isSyntheticBold();
            if (syntheticBold) {
                cs.setStrokingColor(textColor);
                cs.setLineDashPattern(new float[]{}, 0);
                cs.setLineWidth(Math.max(0.3f, fontSize / 30));
                cs.setRenderingMode(RenderingMode.FILL_STROKE);
            }

            cs.beginText();
            float pdfY = geometry.toPdfY(textBaseline);
            if (segment.getFont().isSyntheticItalic()) {
                cs.setTextMatrix(new Matrix(1, 0, 0.25f, 1, segmentX, pdfY));
            } else {
                cs.newLineAtOffset(segmentX, pdfY);
            }
            cs.showText(PdfStyleHelper.normalizeControlChars(segment.getText()));
            cs.endText();
            if (syntheticBold)
                cs.setRenderingMode(RenderingMode.FILL);
            cs.restoreGraphicsState();

            float thickness = Math.max(MIN_DECORATION_THICKNESS, fontSize * DECORATION_THICKNESS_RATIO);
            if (style.isUnderline()) {
                drawDecoration(cs, segmentX, segment.getWidth(),
                        baselineY + fontSize * UNDERLINE_OFFSET, thickness, textColor, geometry);
            }
            if (style.isStrikeout()) {
                drawDecoration(cs, segmentX, segment.getWidth(),
                        baselineY - fontSize * STRIKEOUT_OFFSET, thickness, textColor, geometry);
            }
            if (style.getHref() != null && !style.getHref().isEmpty()) {
                links.add(new LinkTarget(style.getHref(),
                        new PDRectangle(segmentX, geometry.toPdfY(baselineY + fontSize * HIGHLIGHT_BELOW),
                                segment.getWidth(),
                                fontSize * HIGHLIGHT_ABOVE + fontSize * HIGHLIGHT_BELOW)));
            }
        }
    }

    /** A hyperlink target rectangle collected while painting, turned into annotations afterwards. */
    record LinkTarget(String href, PDRectangle rect) {
    }

    private void addLinkAnnotation(PDPage page, LinkTarget target) throws IOException {
        PDAnnotationLink annotation = new PDAnnotationLink();
        PDActionURI action = new PDActionURI();
        action.setURI(target.href());
        annotation.setAction(action);
        annotation.setRectangle(target.rect());
        PDBorderStyleDictionary border = new PDBorderStyleDictionary();
        border.setWidth(0); // the text itself is underlined; no visible annotation border
        annotation.setBorderStyle(border);
        page.getAnnotations().add(annotation);
    }

    private void drawDecoration(PDPageContentStream cs, float x, float width, float lineYTopOrigin,
                                float thickness, PDColor color, DocPageGeometry geometry) throws IOException {
        if (width <= 0)
            return;
        cs.saveGraphicsState();
        cs.setStrokingColor(color);
        cs.setLineWidth(thickness);
        cs.setLineDashPattern(new float[]{}, 0);
        float pdfY = geometry.toPdfY(lineYTopOrigin);
        cs.moveTo(x, pdfY);
        cs.lineTo(x + width, pdfY);
        cs.stroke();
        cs.restoreGraphicsState();
    }

    /** Resolve an OOXML highlight name or a raw color string; null when unresolvable. */
    public static Color highlightColor(String value) {
        if (value == null || value.isEmpty() || "none".equalsIgnoreCase(value))
            return null;
        String named = HIGHLIGHT_COLORS.get(value.toLowerCase(java.util.Locale.ROOT));
        Color color = PdfStyleHelper.parseColor(named != null ? named : value);
        if (color == null)
            color = PdfStyleHelper.parseColor(named);
        return color;
    }

    /** Convert a model {@link WordBorderStyle} for reuse of border drawing helpers. */
    static OfficeBorderStyle toOfficeBorder(WordBorderStyle border) {
        if (border == null)
            return null;
        OfficeBorderStyle ret = new OfficeBorderStyle();
        ret.setType(border.getType() == null ? OfficeLineStyle.NONE : border.getType());
        ret.setWeight(border.getWeight());
        ret.setColor(border.getColor());
        return ret;
    }

    public void paintPages(List<DocPage> pages) throws IOException {
        for (DocPage page : pages)
            paintPage(page);
    }
}
