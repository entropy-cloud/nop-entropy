package io.nop.report.pdf.doc;

import io.nop.api.core.exceptions.NopException;
import io.nop.office.doc.model.OfficeBlock;
import io.nop.office.doc.model.OfficeBreakModel;
import io.nop.office.doc.model.OfficeDocModel;
import io.nop.office.doc.model.OfficeDocPageModel;
import io.nop.office.doc.model.OfficeHyperlinkModel;
import io.nop.office.doc.model.OfficeImageAsset;
import io.nop.office.doc.model.OfficeImageModel;
import io.nop.office.doc.model.OfficeLinkModel;
import io.nop.office.doc.model.OfficeListModel;
import io.nop.office.doc.model.OfficeListItemModel;
import io.nop.office.doc.model.OfficePageBreakModel;
import io.nop.office.doc.model.OfficeParagraphModel;
import io.nop.office.doc.model.OfficeRun;
import io.nop.office.doc.model.OfficeRunModel;
import io.nop.office.doc.model.OfficeSeparatorModel;
import io.nop.office.doc.model.OfficeTabModel;
import io.nop.office.doc.model.OfficeTitleModel;
import io.nop.office.doc.model.WordTable;
import io.nop.office.doc.model.WordTableCell;
import io.nop.office.doc.model.WordTableColumnConfig;
import io.nop.office.doc.model.WordTableRow;
import io.nop.office.model.WordCellStyle;
import io.nop.office.model.WordListStyle;
import io.nop.office.model.WordParagraphStyle;
import io.nop.office.model.WordTableStyle;
import io.nop.office.model.constants.OfficeVerticalAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.nop.report.pdf.ReportPdfErrors.ARG_ASSET_REF;
import static io.nop.report.pdf.ReportPdfErrors.ARG_BLOCK_TYPE;
import static io.nop.report.pdf.ReportPdfErrors.ERR_PDF_DOC_IMAGE_ASSET_NOT_FOUND;
import static io.nop.report.pdf.ReportPdfErrors.ERR_PDF_DOC_UNKNOWN_BLOCK;
import static io.nop.report.pdf.ReportPdfErrors.ERR_PDF_DOC_UNKNOWN_RUN;

/**
 * Turns an {@link OfficeDocModel} into laid-out pages: blocks become {@link DocBox}
 * instances, the paginator distributes them over {@link DocPage}s. Pure computation
 * over the fonts of the shared {@link DocRenderSession} — no drawing happens here.
 */
public class DocLayoutEngine {

    /** Title font sizes by title level (level 1..9), all bold. */
    static final float[] TITLE_FONT_SIZES = {18f, 16f, 14f, 12f, 12f, 12f, 11f, 11f, 11f};
    static final float TITLE_SPACE_BEFORE = 12f;
    static final float TITLE_SPACE_AFTER = 6f;
    static final float MIN_TEXT_WIDTH = 10f;

    private static final float EPSILON = 0.01f;

    private final DocRenderSession session;
    private final DocLineBreaker breaker;

    public DocLayoutEngine(DocRenderSession session) {
        this.session = session;
        this.breaker = new DocLineBreaker(session);
    }

    public DocRenderSession getSession() {
        return session;
    }

    public List<DocPage> layout() {
        OfficeDocModel doc = session.getDoc();
        List<OfficeDocPageModel> sections = doc.getPages();
        List<DocPage> pages = new ArrayList<>();
        int sectionCount = sections == null || sections.isEmpty() ? 1 : sections.size();
        for (int i = 0; i < sectionCount; i++) {
            OfficeDocPageModel page = sections == null || sections.isEmpty() ? null : sections.get(i);
            DocPageGeometry geometry = DocPageGeometry.of(doc, page, i);
            float contentWidth = geometry.getContentWidth();

            List<DocBox> headerBoxes = page == null ? List.of() : layoutBlocks(page.getHeader(), contentWidth, true);
            List<DocBox> footerBoxes = page == null ? List.of() : layoutBlocks(page.getFooter(), contentWidth, true);
            List<DocBox.Placed> header = stack(headerBoxes, geometry.getMarginLeft(), geometry.getHeaderOffset());
            float footerHeight = stackHeight(footerBoxes);
            List<DocBox.Placed> footer = stack(footerBoxes, geometry.getMarginLeft(),
                    geometry.getPageHeight() - geometry.getFooterOffset() - footerHeight);

            List<DocBox> bodyBoxes = page == null ? List.of() : layoutBlocks(page.getBody(), contentWidth, false);
            paginate(pages, geometry, i, header, footer, bodyBoxes);
        }
        return pages;
    }

    /** Stack boxes top-down from {@code y}, returning the placed copies. */
    private List<DocBox.Placed> stack(List<DocBox> boxes, float x, float y) {
        List<DocBox.Placed> placed = new ArrayList<>(boxes.size());
        for (DocBox box : boxes) {
            placed.add(new DocBox.Placed(box, x, y));
            y += box.getHeight();
        }
        return placed;
    }

    private float stackHeight(List<DocBox> boxes) {
        float height = 0;
        for (DocBox box : boxes)
            height += box.getHeight();
        return height;
    }

    private void paginate(List<DocPage> pages, DocPageGeometry geometry, int sectionIndex,
                          List<DocBox.Placed> header, List<DocBox.Placed> footer, List<DocBox> boxes) {
        DocPage current = new DocPage(sectionIndex, geometry, header, footer);
        pages.add(current);
        float y = geometry.getMarginTop();
        boolean empty = true;
        float bottom = geometry.getContentBottom();

        for (DocBox box : boxes) {
            if (box instanceof DocBox.Paragraph paragraph) {
                if (!empty)
                    y += paragraph.getSpaceBefore();
                float fragStart = y;
                List<DocLine> fragment = new ArrayList<>();
                for (DocLine line : paragraph.getLines()) {
                    if (!empty && y + line.getHeight() > bottom + EPSILON) {
                        flushFragment(current, geometry, fragment, fragStart);
                        fragment = new ArrayList<>();
                        current = newPage(pages, sectionIndex, geometry, header, footer);
                        y = geometry.getMarginTop();
                        empty = true;
                        fragStart = y;
                    }
                    fragment.add(line);
                    y += line.getHeight();
                    empty = false;
                    if (line.isPageBreakAfter()) {
                        flushFragment(current, geometry, fragment, fragStart);
                        fragment = new ArrayList<>();
                        current = newPage(pages, sectionIndex, geometry, header, footer);
                        y = geometry.getMarginTop();
                        empty = true;
                        fragStart = y;
                    }
                }
                if (!fragment.isEmpty())
                    flushFragment(current, geometry, fragment, fragStart);
                if (!empty)
                    y += paragraph.getSpaceAfter();
            } else if (box instanceof DocBox.Break) {
                if (!empty) {
                    current = newPage(pages, sectionIndex, geometry, header, footer);
                    y = geometry.getMarginTop();
                    empty = true;
                }
            } else if (box instanceof DocBox.Table table) {
                if (!empty)
                    y += box.getSpaceBefore();
                int from = 0;
                while (from < table.getRowCount()) {
                    float avail = Math.max(EPSILON, bottom - y);
                    int to = from;
                    float used = 0;
                    boolean truncated = false;
                    while (to < table.getRowCount()) {
                        float rowHeight = table.getRows().get(to).getHeight();
                        if (used + rowHeight > avail + EPSILON && to > from)
                            break;
                        if (used + rowHeight > avail + EPSILON) {
                            // single row taller than a page: placed clipped, named warning (plan R2)
                            truncated = true;
                            used = avail;
                            to++;
                            break;
                        }
                        used += rowHeight;
                        to++;
                    }
                    if (truncated)
                        session.warn("doc-table-row-truncated",
                                "table row taller than the page content height, clipped at row " + to);
                    current.getBody().add(new DocBox.Placed(table.fragment(from, to),
                            geometry.getMarginLeft(), y));
                    y += used;
                    empty = false;
                    from = to;
                    if (from < table.getRowCount()) {
                        current = newPage(pages, sectionIndex, geometry, header, footer);
                        y = geometry.getMarginTop();
                        empty = true;
                    }
                }
                if (!empty)
                    y += box.getSpaceAfter();
            } else {
                if (!empty) {
                    if (y + box.getSpaceBefore() + box.getHeight() > bottom + EPSILON) {
                        current = newPage(pages, sectionIndex, geometry, header, footer);
                        y = geometry.getMarginTop();
                        empty = true;
                    }
                    y += box.getSpaceBefore();
                }
                current.getBody().add(new DocBox.Placed(box, geometry.getMarginLeft(), y));
                y += box.getHeight() + box.getSpaceAfter();
                empty = false;
            }
        }
    }

    private void flushFragment(DocPage page, DocPageGeometry geometry, List<DocLine> fragment, float y) {
        page.getBody().add(new DocBox.Placed(new DocBox.Paragraph(fragment, 0, 0), geometry.getMarginLeft(), y));
    }

    private DocPage newPage(List<DocPage> pages, int sectionIndex, DocPageGeometry geometry,
                            List<DocBox.Placed> header, List<DocBox.Placed> footer) {
        DocPage page = new DocPage(sectionIndex, geometry, header, footer);
        pages.add(page);
        return page;
    }

    // ---- block dispatch ------------------------------------------------------

    public List<DocBox> layoutBlocks(List<OfficeBlock> blocks, float width, boolean headerFooter) {
        List<DocBox> boxes = new ArrayList<>();
        if (blocks == null)
            return boxes;
        for (OfficeBlock block : blocks) {
            if (block instanceof OfficeParagraphModel paragraph) {
                boxes.add(layoutParagraph(paragraph.getRuns(), paragraph.getStyle(), width, false, null));
            } else if (block instanceof OfficeTitleModel title) {
                boxes.add(layoutTitle(title, width));
            } else if (block instanceof OfficeHyperlinkModel hyperlink) {
                boxes.add(layoutHyperlinkBlock(hyperlink, width));
            } else if (block instanceof WordTable table) {
                boxes.add(layoutTable(table, width));
            } else if (block instanceof OfficeImageModel image) {
                boxes.add(layoutImage(image, width));
            } else if (block instanceof OfficeListModel list) {
                boxes.add(layoutList(list, width));
            } else if (block instanceof OfficeSeparatorModel) {
                boxes.add(new DocBox.Separator(width));
            } else if (block instanceof OfficePageBreakModel) {
                boxes.add(new DocBox.Break());
            } else {
                throw new NopException(ERR_PDF_DOC_UNKNOWN_BLOCK)
                        .param(ARG_BLOCK_TYPE, block == null ? "null"
                                : block.getClass().getName());
            }
        }
        return boxes;
    }

    public DocBox.Paragraph layoutTitle(OfficeTitleModel title, float width) {
        WordParagraphStyle style = session.paragraphStyle(title.getStyle());
        int level = style.getTitleLevel() == null ? 1 : Math.max(1, Math.min(9, style.getTitleLevel()));
        float fontSize = TITLE_FONT_SIZES[level - 1];

        DocTextStyle base = session.baseTextStyle();
        base.setFontSize(fontSize);
        base.setBold(true);

        List<DocInline> inlines = flattenRuns(title.getRuns(), base);
        return buildParagraph(inlines, style, width, base.getFontSize(), TITLE_SPACE_BEFORE, TITLE_SPACE_AFTER);
    }

    public DocBox.Paragraph layoutParagraph(List<OfficeRun> runs, WordParagraphStyle inlineStyle,
                                            float width, boolean headerFooter, String href) {
        WordParagraphStyle style = session.paragraphStyle(inlineStyle);
        DocTextStyle base = session.baseTextStyle();
        if (href != null)
            base.setHref(href);
        List<DocInline> inlines = flattenRuns(runs, base);
        return buildParagraph(inlines, style, width, base.getFontSize(), 0, 0);
    }

    private DocBox.Paragraph layoutHyperlinkBlock(OfficeHyperlinkModel hyperlink, float width) {
        String href = hyperlink.getHref();
        if (href == null && hyperlink.getAnchor() != null)
            href = "#" + hyperlink.getAnchor();
        return layoutParagraph(hyperlink.getRuns(), null, width, false, href);
    }

    // ---- Phase 2b: table / image / list / separator / page break --------------

    /** Cell padding left/right and top/bottom inside a table cell. */
    static final float CELL_PADDING = 2f;
    /** Default height of a table row with empty content. */
    static final float MIN_ROW_HEIGHT = 14f;
    /** List indentation per nesting level. */
    static final float LIST_INDENT_PER_LEVEL = 18f;
    /** Default bullet glyphs per level when no list style is declared. */
    static final String[] DEFAULT_BULLETS = {"•", "◦", "▪"};

    public DocBox.Table layoutTable(WordTable table, float width) {
        List<WordTableRow> rows = table.getRows();
        int rowCount = rows == null ? 0 : rows.size();

        // pass 1: grid occupancy from mergeAcross / mergeDown
        List<List<int[]>> spans = new ArrayList<>(rowCount); // per row: {col, colSpan, rowSpan}
        int colCount = 0;
        List<List<Boolean>> occ = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            List<Boolean> rowOcc = new ArrayList<>();
            occ.add(rowOcc);
            spans.add(new ArrayList<>());
            int c = 0;
            for (WordTableCell cell : rows.get(r).getCells()) {
                while (c < rowOcc.size() && Boolean.TRUE.equals(rowOcc.get(c)))
                    c++;
                int colSpan = Math.max(1, 1 + cell.getMergeAcross());
                int rowSpan = Math.max(1, 1 + cell.getMergeDown());
                for (int rr = r; rr < Math.min(rowCount, r + rowSpan); rr++) {
                    while (occ.get(rr).size() < c + colSpan)
                        occ.get(rr).add(Boolean.FALSE);
                    for (int cc = c; cc < c + colSpan; cc++)
                        occ.get(rr).set(cc, rr == r && cc == c ? Boolean.FALSE : Boolean.TRUE);
                }
                spans.get(r).add(new int[]{c, colSpan, rowSpan});
                c += colSpan;
            }
            colCount = Math.max(colCount, c);
            colCount = Math.max(colCount, rowOcc.size());
        }
        if (colCount == 0)
            colCount = 1;

        // column widths: model column widths, equal split fallback; normalized to the
        // content width when they disagree
        float[] colWidth = new float[colCount];
        List<WordTableColumnConfig> cols = table.getCols();
        boolean hasWidths = cols != null && !cols.isEmpty();
        float modelTotal = 0;
        for (int c = 0; c < colCount; c++) {
            Double w = hasWidths && c < cols.size() ? cols.get(c).getWidth() : null;
            colWidth[c] = w != null && w > 0 ? w.floatValue() : 0;
            modelTotal += colWidth[c];
        }
        if (modelTotal <= 0) {
            float each = width / colCount;
            for (int c = 0; c < colCount; c++)
                colWidth[c] = each;
        } else {
            float scale = width / modelTotal;
            for (int c = 0; c < colCount; c++)
                if (colWidth[c] == 0)
                    colWidth[c] = width / colCount * scale;
                else
                    colWidth[c] *= scale;
        }
        float[] colX = new float[colCount];
        for (int c = 1; c < colCount; c++)
            colX[c] = colX[c - 1] + colWidth[c - 1];

        // default cell style from the table style
        WordCellStyle tableCellStyle = null;
        WordTableStyle tableStyle = table.getStyleId() == null ? null : table.getStyleId().isEmpty() ? null
                : session.getStyleResolver().resolveTableStyle(table.getStyleId());
        if (tableStyle != null)
            tableCellStyle = tableStyle.getCellStyle();

        // pass 2: cell content
        List<DocBox.Table.TableRow> tableRows = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            WordTableRow modelRow = rows.get(r);
            float modelHeight = modelRow.getHeight() != null ? modelRow.getHeight().floatValue() : 0;
            List<DocBox.Table.TableCell> cells = new ArrayList<>();
            int spanIdx = 0;
            for (WordTableCell cell : rows.get(r).getCells()) {
                int[] span = spans.get(r).get(spanIdx++);
                int c = span[0];
                float cellWidth = 0;
                for (int cc = c; cc < Math.min(colCount, c + span[1]); cc++)
                    cellWidth += colWidth[cc];

                WordCellStyle style = tableCellStyle;
                if (cell.getStyleId() != null && session.cellStyle(cell.getStyleId()) != null)
                    style = session.cellStyle(cell.getStyleId());

                DocTextStyle textStyle = session.baseTextStyle();
                if (style != null && style.getFont() != null)
                    textStyle.applyFont(style.getFont());

                float avail = Math.max(MIN_TEXT_WIDTH, cellWidth - 2 * CELL_PADDING);
                List<DocInline> inlines = new ArrayList<>();
                String text = cell.getValue();
                if (text != null && !text.isEmpty())
                    inlines.add(DocInline.text(text, textStyle));
                List<DocLine> lines = breaker.breakLines(inlines, avail, avail,
                        textStyle.getFontSize(), DocLineBreaker.DEFAULT_LINE_SPACING);
                for (DocLine line : lines)
                    line.setXOffset(CELL_PADDING);
                float contentHeight = 0;
                for (DocLine line : lines)
                    contentHeight += line.getHeight();

                OfficeVerticalAlignment vAlign = style != null ? style.getVerticalAlign() : null;
                cells.add(new DocBox.Table.TableCell(c, span[1], span[2], cellWidth, lines, contentHeight,
                        style, vAlign));
            }
            float rowHeight = Math.max(Math.max(modelHeight, MIN_ROW_HEIGHT), maxContentBottom(cells));
            tableRows.add(new DocBox.Table.TableRow(rowHeight, cells));
        }
        return new DocBox.Table(colX, colWidth, tableRows);
    }

    private static float maxContentBottom(List<DocBox.Table.TableCell> cells) {
        float max = 0;
        for (DocBox.Table.TableCell cell : cells)
            max = Math.max(max, cell.getContentHeight() + 2 * CELL_PADDING);
        return max;
    }

    public DocBox.Image layoutImage(OfficeImageModel image, float width) {
        OfficeImageAsset asset = findAsset(session.getDoc(), image.getAssetRef());
        if (asset == null)
            throw new NopException(ERR_PDF_DOC_IMAGE_ASSET_NOT_FOUND)
                    .param(ARG_ASSET_REF, image.getAssetRef());
        byte[] data = asset.getData() == null ? new byte[0] : asset.getData().toByteArray();

        float w = image.getWidth() != null && image.getWidth() > 0 ? image.getWidth().floatValue() : width;
        float h = image.getHeight() != null && image.getHeight() > 0 ? image.getHeight().floatValue() : width;
        // scale down to fit the content area, keep the aspect ratio, never upscale
        DocPageGeometry geometry = DocPageGeometry.of(session.getDoc(), null, 0);
        float scale = Math.min(1f, Math.min(width / w, geometry.getContentHeight() / h));
        return new DocBox.Image(data, w * scale, h * scale);
    }

    private static OfficeImageAsset findAsset(OfficeDocModel doc, String assetRef) {
        if (assetRef == null || doc.getAssets() == null)
            return null;
        for (OfficeImageAsset asset : doc.getAssets()) {
            if (assetRef.equals(asset.getName()))
                return asset;
        }
        return null;
    }

    /**
     * List block: indent per level + a glyph prefix. With a named {@link WordListStyle}
     * the numFmt/lvlText/start tuple drives the glyph (level and numbering counters per
     * level); without one the fallback is a bullet glyph ("•" / "◦" / "▪") at level 0+.
     */
    public DocBox.Paragraph layoutList(OfficeListModel list, float width) {
        WordListStyle style = session.listStyle(list.getStyleId());
        boolean numbered = list.getNumId() != null && !list.getNumId().isEmpty();
        List<DocInline> inlines = new ArrayList<>();
        int[] counters = new int[10];
        for (OfficeListItemModel item : list.getItems()) {
            int level = Math.max(0, Math.min(9, item.getLevel()));
            String glyph;
            if (style != null) {
                counters[level]++;
                glyph = listGlyph(style, level, counters[level]);
            } else if (numbered) {
                // numbering definition not modeled: plain decimal sequence per list (R5 fallback)
                counters[level]++;
                glyph = (counters[level]) + ".";
            } else {
                glyph = DEFAULT_BULLETS[Math.min(level, DEFAULT_BULLETS.length - 1)];
            }

            DocTextStyle glyphStyle = session.baseTextStyle();
            inlines.add(DocInline.text(glyph + " ", glyphStyle));
            DocTextStyle base = session.baseTextStyle();
            inlines.addAll(flattenRuns(item.getRuns(), base));
            inlines.add(DocInline.lineBreak(glyphStyle));
        }
        inlines.remove(inlines.size() - 1); // no trailing break after the last item

        WordParagraphStyle paragraphStyle = new WordParagraphStyle();
        float indent = LIST_INDENT_PER_LEVEL;
        paragraphStyle.setLeftIndent((double) indent);
        return buildParagraph(inlines, paragraphStyle, width, session.baseTextStyle().getFontSize(), 0, 0);
    }

    private String listGlyph(WordListStyle style, int level, int counter) {
        String fmt = style.getNumFmt() == null ? "bullet" : style.getNumFmt().toLowerCase(Locale.ROOT);
        String lvlText = style.getLvlText() == null || style.getLvlText().isEmpty() ? "%1" : style.getLvlText();
        int start = style.getStart() > 0 ? style.getStart() : 1;
        String value;
        switch (fmt) {
            case "bullet":
                value = lvlText;
                break;
            case "lowerletter":
                value = String.valueOf((char) ('a' + Math.min(25, counter - 1)));
                break;
            case "upperletter":
                value = String.valueOf((char) ('A' + Math.min(25, counter - 1)));
                break;
            case "lowerroman":
                value = roman(start + counter - 1).toLowerCase(Locale.ROOT);
                break;
            case "upperroman":
                value = roman(start + counter - 1);
                break;
            default:
                value = String.valueOf(start + counter - 1);
        }
        // substitute %1..%9 with the level counters (only level+1 is maintained here)
        return lvlText.replace("%" + (level + 1), value).replaceAll("%[1-9]", value);
    }

    private static String roman(int n) {
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                sb.append(symbols[i]);
                n -= values[i];
            }
        }
        return sb.toString();
    }

    /**
     * Model runs -> inline atoms. Link runs inherit a link presentation (color +
     * underline) and carry the href for the link annotation.
     */
    public List<DocInline> flattenRuns(List<OfficeRun> runs, DocTextStyle base) {
        List<DocInline> inlines = new ArrayList<>();
        if (runs == null)
            return inlines;
        for (OfficeRun run : runs) {
            if (run instanceof OfficeRunModel textRun) {
                DocTextStyle style = session.textStyle(base, textRun.getStyle());
                String text = textRun.getT();
                if (text != null && !text.isEmpty())
                    inlines.add(DocInline.text(text, style));
            } else if (run instanceof OfficeTabModel) {
                inlines.add(DocInline.tab(base));
            } else if (run instanceof OfficeBreakModel br) {
                boolean page = "page".equalsIgnoreCase(br.getBrType());
                inlines.add(page ? DocInline.pageBreak(base) : DocInline.lineBreak(base));
            } else if (run instanceof OfficeLinkModel link) {
                DocTextStyle linkBase = base.copy();
                String href = link.getHref();
                if (href == null && link.getAnchor() != null)
                    href = "#" + link.getAnchor();
                linkBase.setHref(href);
                applyLinkLook(linkBase);
                inlines.addAll(flattenRuns(link.getRuns(), linkBase));
            } else {
                throw new NopException(ERR_PDF_DOC_UNKNOWN_RUN)
                        .param(ARG_BLOCK_TYPE, run == null ? "null" : run.getClass().getName());
            }
        }
        return inlines;
    }

    private static void applyLinkLook(DocTextStyle style) {
        if (style.getColor() == null)
            style.setColor("0563C1");
        style.setUnderline(true);
    }

    private DocBox.Paragraph buildParagraph(List<DocInline> inlines, WordParagraphStyle style,
                                            float width, float defaultFontSize,
                                            float defaultSpaceBefore, float defaultSpaceAfter) {
        float left = orZero(style.getLeftIndent());
        float right = orZero(style.getRightIndent());
        float first = orZero(style.getFirstLineIndent());
        if (left + first < 0)
            first = -left;
        float firstWidth = Math.max(MIN_TEXT_WIDTH, width - left - right - first);
        float otherWidth = Math.max(MIN_TEXT_WIDTH, width - left - right);

        float baseFontSize = baseFontSizeOf(inlines, defaultFontSize);
        float lineSpacing = style.getLineSpacing() != null && style.getLineSpacing() > 0
                ? style.getLineSpacing().floatValue() : DocLineBreaker.DEFAULT_LINE_SPACING;
        List<DocLine> lines = breaker.breakLines(inlines, firstWidth, otherWidth, baseFontSize, lineSpacing);

        // alignment: JUSTIFY is drawn as left (first version, recorded in the impl report)
        io.nop.office.model.constants.OfficeHorizontalAlignment align = style.getAlign();
        boolean center = align == io.nop.office.model.constants.OfficeHorizontalAlignment.CENTER
                || align == io.nop.office.model.constants.OfficeHorizontalAlignment.CENTER_SELECTION;
        boolean rightAlign = align == io.nop.office.model.constants.OfficeHorizontalAlignment.RIGHT;

        for (int i = 0; i < lines.size(); i++) {
            DocLine line = lines.get(i);
            float indent = i == 0 ? first : 0;
            float avail = (i == 0 ? firstWidth : otherWidth);
            float slack = avail - line.getWidth();
            float offset = left + indent;
            if (center)
                offset += Math.max(0, slack) / 2;
            else if (rightAlign)
                offset += Math.max(0, slack);
            line.setXOffset(offset);
        }

        float spaceBefore = style.getSpaceBefore() != null ? style.getSpaceBefore().floatValue() : defaultSpaceBefore;
        float spaceAfter = style.getSpaceAfter() != null ? style.getSpaceAfter().floatValue() : defaultSpaceAfter;
        return new DocBox.Paragraph(lines, spaceBefore, spaceAfter);
    }

    private static float baseFontSizeOf(List<DocInline> inlines, float fallback) {
        float size = 0;
        for (DocInline inline : inlines) {
            if (inline.getStyle() != null)
                size = Math.max(size, inline.getStyle().getFontSize());
        }
        return size > 0 ? size : fallback;
    }

    private static float orZero(Double value) {
        return value == null ? 0 : value.floatValue();
    }
}
