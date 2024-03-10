/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.model.table;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.IJsonString;
import io.nop.core.model.table.utils.CellReferenceHelper;

import java.io.Serializable;

import static io.nop.core.CoreErrors.ARG_COL_INDEX;
import static io.nop.core.CoreErrors.ARG_ROW_INDEX;
import static io.nop.core.CoreErrors.ERR_TABLE_INVALID_CELL_POSITION;
import static io.nop.core.model.table.utils.CellReferenceHelper.convertNumToColString;

/**
 * 对应一个单元格的位置
 */
@DataBean
public class CellPosition implements Serializable, Comparable<CellPosition>, IJsonString {
    public static String NONE_NAME = "A0";
    public static String NONE_RC_NAME = "R0C0";

    public static CellPosition NONE = new CellPosition();

    private static final long serialVersionUID = 6640189410792108663L;

    public final static int MAX_ROWS = 1024 * 1024;
    public final static int MAX_COLS = 65536;

    private final int rowIndex;
    private final int colIndex;

    public CellPosition(@JsonProperty("rowIndex") int rowIndex, @JsonProperty("colIndex") int colIndex) {
        if (rowIndex < 0 && colIndex < 0) {
            throw new NopException(ERR_TABLE_INVALID_CELL_POSITION).param(ARG_ROW_INDEX, rowIndex).param(ARG_COL_INDEX,
                    colIndex);
        }

        if (rowIndex >= MAX_ROWS || colIndex >= MAX_COLS)
            throw new NopException(ERR_TABLE_INVALID_CELL_POSITION).param(ARG_ROW_INDEX, rowIndex).param(ARG_COL_INDEX,
                    colIndex);

        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
    }

    private CellPosition() {
        this.rowIndex = -1;
        this.colIndex = -1;
    }

    @StaticFactoryMethod
    public static CellPosition fromRCString(String str) {
        if (NONE_RC_NAME.equals(str))
            return NONE;
        return CellReferenceHelper.parsePositionRCString(str);
    }

    public static CellPosition fromABString(String str) {
        return CellReferenceHelper.parsePositionABString(str);
    }

    public static CellPosition of(int rowIndex, int colIndex) {
        return new CellPosition(rowIndex, colIndex);
    }

    public CellPosition offset(int x, int y) {
        return new CellPosition(rowIndex < 0 ? rowIndex : rowIndex + x, colIndex < 0 ? colIndex : colIndex + y);
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public String toRCString() {
        return toRCString(rowIndex, colIndex);
    }

    public boolean isNone() {
        return this == NONE;
    }

    public boolean isWholeRow() {
        return colIndex < 0;
    }

    public boolean isWholeCol() {
        return rowIndex < 0;
    }

    public String toString() {
        return toABString();
    }

    public String toABString() {
        return toABString(false, false);
    }

    public String toABString(boolean rowAbs, boolean colAbs) {
        return toABString(rowIndex, colIndex, rowAbs, colAbs);
    }

    public static String toRCString(int r, int c) {
        if (r < 0) {
            if (r == -1 && c == -1)
                return NONE_RC_NAME;

            return "C" + (c + 1);
        }
        if (c < 0)
            return "R" + (r + 1);
        return "R" + (r + 1) + "C" + (c + 1);
    }

    public static String toABString(int rowIndex, int colIndex) {
        return toABString(rowIndex, colIndex, false, false);
    }

    public static String toABString(int rowIndex, int colIndex, boolean rowAbs, boolean colAbs) {
        if (rowIndex == -1 && colIndex == -1)
            return NONE_NAME;

        StringBuilder sb = new StringBuilder(10);
        if (colIndex >= 0) {
            if (colAbs) {
                sb.append('$');
            }
            sb.append(convertNumToColString(colIndex));
        }
        if (rowIndex >= 0) {
            if (rowAbs) {
                sb.append('$');
            }
            sb.append(rowIndex + 1);
        }
        String pos = sb.toString();

        // 单元格下标字符串可以被复用
        if (!rowAbs && !colAbs && rowIndex < 100 && colIndex < 50) {
            pos = pos.intern();
        }
        return pos;
    }

    public String toRelativeRCString(int r, int c) {
        int dr = this.rowIndex - r;
        int dc = this.colIndex - c;
        StringBuilder sb = new StringBuilder(10);
        if (!isWholeCol()) {
            sb.append("R");
            if (dr != 0) {
                sb.append('[');
                sb.append(dr);
                sb.append(']');
            }
        }

        if (!isWholeRow()) {
            sb.append("C");
            if (dc != 0) {
                sb.append('[');
                sb.append(dc);
                sb.append(']');
            }
        }
        return sb.toString();
    }

    public int hashCode() {
        return rowIndex * 31 + colIndex;
    }

    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof CellPosition))
            return false;
        CellPosition pos = (CellPosition) o;
        return rowIndex == pos.rowIndex && colIndex == pos.colIndex;
    }

    @Override
    public int compareTo(CellPosition o) {
        if (rowIndex > o.rowIndex)
            return 1;
        if (rowIndex < o.rowIndex)
            return -1;
        if (colIndex > o.colIndex)
            return 1;
        if (colIndex < o.colIndex)
            return -1;
        return 0;
    }
}