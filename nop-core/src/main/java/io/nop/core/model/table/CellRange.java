/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.table;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.IJsonString;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;

import static io.nop.core.CoreErrors.ARG_COL_INDEX;
import static io.nop.core.CoreErrors.ARG_ROW_INDEX;
import static io.nop.core.CoreErrors.ERR_TABLE_INVALID_CELL_POSITION;

/**
 * 描述一个连续的区域。包含firstIndex和lastIndex
 */
@DataBean
public class CellRange implements Serializable, Comparable<CellRange>, IJsonString {

    private static final long serialVersionUID = -5723999944891774654L;

    private final int firstColIndex;
    private final int firstRowIndex;
    private final int lastColIndex;
    private final int lastRowIndex;

    public CellRange(@JsonProperty("firstRowIndex") int firstRowIndex, @JsonProperty("firstColIndex") int firstColIndex,
                     @JsonProperty("lastRowIndex") int lastRowIndex, @JsonProperty("lastColIndex") int lastColIndex) {
        // 确保小的标号在前
        if (firstRowIndex > lastRowIndex) {
            int t = firstRowIndex;
            firstRowIndex = lastRowIndex;
            lastRowIndex = t;
        }
        if (firstColIndex > lastColIndex) {
            int t = firstColIndex;
            firstColIndex = lastColIndex;
            lastColIndex = t;
        }

        if (firstRowIndex < 0 && lastRowIndex >= 0)
            firstRowIndex = 0;

        if (firstColIndex < 0 && lastRowIndex >= 0)
            firstColIndex = 0;

        if (lastRowIndex < 0 && lastColIndex < 0) {
            throw new NopException(ERR_TABLE_INVALID_CELL_POSITION).param(ARG_ROW_INDEX, lastRowIndex)
                    .param(ARG_COL_INDEX, lastColIndex);
        }

        this.firstRowIndex = firstRowIndex;
        this.firstColIndex = firstColIndex;
        this.lastRowIndex = lastRowIndex;
        this.lastColIndex = lastColIndex;
    }

    public static CellRange fromPosition(CellPosition first, CellPosition last) {
        return new CellRange(first.getRowIndex(), first.getColIndex(), last.getRowIndex(), last.getColIndex());
    }

    public static CellRange fromABString(String str) {
        if (StringHelper.isEmpty(str))
            return null;

        int pos = str.indexOf(':');
        if (pos < 0) {
            CellPosition ref = CellPosition.fromABString(str);
            return new CellRange(ref.getRowIndex(), ref.getColIndex(), ref.getRowIndex(), ref.getColIndex());
        } else {
            CellPosition first = CellPosition.fromABString(str.substring(0, pos));
            CellPosition last = CellPosition.fromABString(str.substring(pos + 1));
            return new CellRange(first.getRowIndex(), first.getColIndex(), last.getRowIndex(), last.getColIndex());
        }
    }

    public String toABString() {
        return toABString(false, false);
    }

    public String toABString(boolean rowAbs, boolean colAbs) {
        return CellPosition.toABString(firstRowIndex, firstColIndex, rowAbs, colAbs) + ':'
                + CellPosition.toABString(lastRowIndex, lastColIndex, rowAbs, colAbs);
    }

    public String toRCString() {
        if (isWholeRow()) {
            return "R" + (firstRowIndex + 1) + ":R" + (lastRowIndex + 1);
        } else if (isWholeCol()) {
            return "C" + (firstColIndex + 1) + ":C" + (lastColIndex + 1);
        }

        return CellPosition.toRCString(firstRowIndex, firstColIndex) + ":"
                + CellPosition.toRCString(lastRowIndex, lastColIndex);
    }

    private static void _appendIndex(StringBuilder sb, int index) {
        if (index != 0) {
            sb.append('[').append(index).append(']');
        }
    }

    public String toRelativeRCString(int r, int c) {
        StringBuilder sb = new StringBuilder(16);
        boolean wholeRow = isWholeRow();
        boolean wholeCol = isWholeCol();

        if (!wholeCol) {
            sb.append('R');
            _appendIndex(sb, firstRowIndex - r);
        }
        if (!wholeRow) {
            sb.append('C');
            _appendIndex(sb, firstColIndex - c);
        }
        sb.append(':');

        if (!wholeCol) {
            sb.append('R');
            _appendIndex(sb, lastRowIndex - r);
        }

        if (!wholeRow) {
            sb.append('C');
            _appendIndex(sb, lastColIndex - c);
        }

        return sb.toString();
    }

    public String toString() {
        return toABString();
    }

    public boolean isWholeCol() {
        return lastRowIndex < 0;
    }

    public boolean isWholeRow() {
        return lastColIndex < 0;
    }

    public int getFirstRowIndex() {
        return firstRowIndex;
    }

    public int getFirstColIndex() {
        return firstColIndex;
    }

    public int getLastRowIndex() {
        return lastRowIndex;
    }

    public int getLastColIndex() {
        return lastColIndex;
    }

    public int getEndRowIndex() {
        if (lastRowIndex < 0)
            return -1;
        return lastRowIndex + 1;
    }

    public int getEndColIndex() {
        if (lastColIndex < 0)
            return -1;
        return lastColIndex + 1;
    }

    public int getRowCount() {
        if (isWholeCol())
            return Integer.MAX_VALUE;
        return this.getLastRowIndex() - this.getFirstRowIndex() + 1;
    }

    public int getColCount() {
        if (isWholeRow())
            return Integer.MAX_VALUE;
        return this.getLastColIndex() - this.getFirstColIndex() + 1;
    }

    public boolean isSingleCell() {
        if (isWholeRow() || isWholeCol())
            return false;
        return firstRowIndex == lastRowIndex && firstColIndex == lastColIndex;
    }

    public boolean intersectWith(CellRange range) {
        int minr = Math.max(this.firstRowIndex, range.getFirstRowIndex());
        int maxr = Math.min(this.lastRowIndex, range.getLastRowIndex());
        if (minr > maxr)
            return false;

        int minc = Math.max(this.firstColIndex, range.getFirstColIndex());
        int maxc = Math.min(this.lastColIndex, range.getLastColIndex());
        return minc <= maxc;
    }

    public CellRange intersect(CellRange range) {
        if (contains(range))
            return range;

        if (range.contains(this))
            return this;

        int minr = Math.max(this.firstRowIndex, range.getFirstRowIndex());
        int maxr = Math.min(this.lastRowIndex, range.getLastRowIndex());
        if (minr > maxr)
            return null;

        int minc = Math.max(this.firstColIndex, range.getFirstColIndex());
        int maxc = Math.min(this.lastColIndex, range.getLastColIndex());
        if (minc > maxc)
            return null;

        return new CellRange(minr, minc, maxr, maxc);
    }

    public boolean contains(CellRange range) {
        return firstRowIndex <= range.getFirstRowIndex() && firstColIndex <= range.getFirstColIndex()
                && lastRowIndex >= range.getLastRowIndex() && lastColIndex >= range.getLastColIndex();
    }

    public CellRange offset(int rowOffset, int colOffset) {
        if (isWholeRow()) {
            return new CellRange(this.getFirstRowIndex() + rowOffset, 0, this.getLastRowIndex() + rowOffset, -1);
        }

        if (isWholeCol()) {
            return new CellRange(0, this.getFirstColIndex() + colOffset, -1, this.getLastColIndex() + colOffset);
        }

        return new CellRange(this.getFirstRowIndex() + rowOffset, this.getFirstColIndex() + colOffset,
                this.getLastRowIndex() + rowOffset, this.getLastColIndex() + colOffset);
    }

    public boolean containsCell(int rowIndex, int colIndex) {
        return this.getFirstRowIndex() <= rowIndex && this.getLastRowIndex() >= rowIndex
                && this.getFirstColIndex() <= colIndex && this.getLastColIndex() >= colIndex;
    }

    @Override
    public int compareTo(CellRange o) {
        if (firstRowIndex > o.firstRowIndex)
            return 1;
        if (firstRowIndex < o.firstRowIndex)
            return -1;
        if (firstColIndex > o.firstColIndex)
            return 1;
        if (firstColIndex < o.firstColIndex)
            return -1;

        if (lastRowIndex > o.lastRowIndex)
            return 1;
        if (lastRowIndex < o.lastRowIndex)
            return -1;
        if (lastColIndex > o.lastColIndex)
            return 1;
        if (lastColIndex < o.lastColIndex)
            return -1;
        return 0;
    }
}