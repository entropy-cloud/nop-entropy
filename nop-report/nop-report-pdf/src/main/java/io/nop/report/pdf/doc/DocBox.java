package io.nop.report.pdf.doc;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout boxes produced by {@link DocLayoutEngine}. Boxes are pure data in pt; the
 * paginator places them (or fragments of them) on pages and {@link DocPagePainter}
 * draws them.
 */
public abstract class DocBox {

    public abstract float getHeight();

    /** Space above the box, collapsed at the top of a page. */
    public float getSpaceBefore() {
        return 0;
    }

    public float getSpaceAfter() {
        return 0;
    }

    /** A paragraph-like block: stacked lines. Splittable between lines. */
    public static final class Paragraph extends DocBox {
        private final List<DocLine> lines;
        private final float spaceBefore;
        private final float spaceAfter;

        public Paragraph(List<DocLine> lines, float spaceBefore, float spaceAfter) {
            this.lines = lines;
            this.spaceBefore = spaceBefore;
            this.spaceAfter = spaceAfter;
        }

        public List<DocLine> getLines() {
            return lines;
        }

        @Override
        public float getHeight() {
            float height = 0;
            for (DocLine line : lines)
                height += line.getHeight();
            return height;
        }

        @Override
        public float getSpaceBefore() {
            return spaceBefore;
        }

        @Override
        public float getSpaceAfter() {
            return spaceAfter;
        }

        public List<String> getLineTexts() {
            List<String> ret = new ArrayList<>(lines.size());
            for (DocLine line : lines)
                ret.add(line.getText());
            return ret;
        }
    }

    /** A box (or box fragment) positioned on a page; x/y are the top-left corner, top-origin. */
    public static final class Placed {
        private final DocBox box;
        private final float x;
        private final float y;

        public Placed(DocBox box, float x, float y) {
            this.box = box;
            this.x = x;
            this.y = y;
        }

        public DocBox getBox() {
            return box;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }
    }

    /** An image block, already scaled to fit the content area. Never split across pages. */
    public static final class Image extends DocBox {
        private final byte[] data;
        private final float width;
        private final float height;

        public Image(byte[] data, float width, float height) {
            this.data = data;
            this.width = width;
            this.height = height;
        }

        public byte[] getData() {
            return data;
        }

        @Override
        public float getHeight() {
            return height;
        }

        public float getWidth() {
            return width;
        }
    }

    /** A horizontal separator line. Never split. */
    public static final class Separator extends DocBox {
        private final float width;
        public static final float HEIGHT = 14f;

        public Separator(float width) {
            this.width = width;
        }

        public float getWidth() {
            return width;
        }

        @Override
        public float getHeight() {
            return HEIGHT;
        }
    }

    /** An explicit page break directive (height 0; no-op at the top of an empty page). */
    public static final class Break extends DocBox {
        @Override
        public float getHeight() {
            return 0;
        }
    }

    /** A word table, splittable between rows (a single row is never split). */
    public static final class Table extends DocBox {
        private final float[] colX;
        private final float[] colWidth;
        private final List<TableRow> rows;

        public Table(float[] colX, float[] colWidth, List<TableRow> rows) {
            this.colX = colX;
            this.colWidth = colWidth;
            this.rows = rows;
        }

        public float[] getColX() {
            return colX;
        }

        public float[] getColWidth() {
            return colWidth;
        }

        public List<TableRow> getRows() {
            return rows;
        }

        @Override
        public float getHeight() {
            float height = 0;
            for (TableRow row : rows)
                height += row.getHeight();
            return height;
        }

        /** A fragment covering rows [from, to) — used for splitting across pages. */
        public Table fragment(int from, int to) {
            return new Table(colX, colWidth, rows.subList(from, to));
        }

        public int getRowCount() {
            return rows.size();
        }

        public static final class TableRow {
            private final float height;
            private final List<TableCell> cells;

            public TableRow(float height, List<TableCell> cells) {
                this.height = height;
                this.cells = cells;
            }

            public float getHeight() {
                return height;
            }

            public List<TableCell> getCells() {
                return cells;
            }
        }

        public static final class TableCell {
            private final int col;
            private final int colSpan;
            private final int rowSpan;
            private final float width;
            private final List<DocLine> lines;
            private final float contentHeight;
            private final io.nop.office.model.WordCellStyle style;
            private final io.nop.office.model.constants.OfficeVerticalAlignment verticalAlign;

            public TableCell(int col, int colSpan, int rowSpan, float width, List<DocLine> lines,
                             float contentHeight, io.nop.office.model.WordCellStyle style,
                             io.nop.office.model.constants.OfficeVerticalAlignment verticalAlign) {
                this.col = col;
                this.colSpan = colSpan;
                this.rowSpan = rowSpan;
                this.width = width;
                this.lines = lines;
                this.contentHeight = contentHeight;
                this.style = style;
                this.verticalAlign = verticalAlign;
            }

            public int getCol() {
                return col;
            }

            public int getColSpan() {
                return colSpan;
            }

            public int getRowSpan() {
                return rowSpan;
            }

            public float getWidth() {
                return width;
            }

            public List<DocLine> getLines() {
                return lines;
            }

            public float getContentHeight() {
                return contentHeight;
            }

            public io.nop.office.model.WordCellStyle getStyle() {
                return style;
            }

            public io.nop.office.model.constants.OfficeVerticalAlignment getVerticalAlign() {
                return verticalAlign;
            }
        }
    }
}
