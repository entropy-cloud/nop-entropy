package io.nop.treesitter.compat;

import java.util.Objects;

/**
 * Zero-based row/column position ({@code org.treesitter.TSPoint} shape).
 */
public final class TSPoint {
    private final int row;
    private final int column;

    public TSPoint(int row, int column) {
        this.row = row;
        this.column = column;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TSPoint)) {
            return false;
        }
        TSPoint tsPoint = (TSPoint) o;
        return row == tsPoint.row && column == tsPoint.column;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, column);
    }

    @Override
    public String toString() {
        return "(" + row + ", " + column + ")";
    }
}
