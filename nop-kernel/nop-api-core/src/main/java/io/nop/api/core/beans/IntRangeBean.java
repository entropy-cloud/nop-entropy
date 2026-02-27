/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.ApiErrors;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;

import java.io.Serializable;
import java.util.List;
import java.util.PrimitiveIterator;

@DataBean
public class IntRangeBean implements Serializable, Comparable<IntRangeBean>, Iterable<Integer> {
    private static final long serialVersionUID = 3846253782985184968L;
    private static final char SEPARATOR = ',';

    public static IntRangeBean EMPTY = new IntRangeBean(0, 0);

    private final int offset;
    private final int limit;

    @JsonCreator
    public IntRangeBean(@JsonProperty("offset") int offset, @JsonProperty("limit") int limit) {
        this.offset = offset;
        this.limit = Guard.nonNegativeInt(limit, "limit");
    }

    public static IntRangeBean build(int begin, int end) {
        return of(begin, end - begin);
    }

    public static IntRangeBean shortRange() {
        return intRange(0, Short.MAX_VALUE);
    }

    public static IntRangeBean intRange(int offset, int limit) {
        return of(offset, limit);
    }

    public static IntRangeBean of(int offset, int limit) {
        if (offset == 0 && limit == 0)
            return EMPTY;
        return new IntRangeBean(offset, limit);
    }

    public static IntRangeBean parse(String str) {
        if (str == null || str.length() <= 0)
            return null;

        int pos = str.indexOf(SEPARATOR);
        if (pos < 0) {
            Integer start = ConvertHelper.stringToInt(str,
                    err -> new NopException(ApiErrors.ERR_INVALID_OFFSET_LIMIT_STRING).param(ApiErrors.ARG_VALUE, str));
            return of(start, 1);
        }

        Integer start = ConvertHelper.stringToInt(str.substring(0, pos),
                err -> new NopException(ApiErrors.ERR_INVALID_OFFSET_LIMIT_STRING).param(ApiErrors.ARG_VALUE, str));
        Integer limit = ConvertHelper.stringToInt(str.substring(pos + 1),
                err -> new NopException(ApiErrors.ERR_INVALID_OFFSET_LIMIT_STRING).param(ApiErrors.ARG_VALUE, str));
        return of(start, limit);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(offset);
        sb.append(SEPARATOR);
        sb.append(limit);
        return sb.toString();
    }

    public PrimitiveIterator.OfInt iterator() {
        return new PrimitiveIterator.OfInt() {
            private int index = offset;

            @Override
            public int nextInt() {
                return index++;
            }

            @Override
            public boolean hasNext() {
                return index < getEnd();
            }
        };
    }

    @JsonIgnore
    public String getBeginEndString() {
        return "[" + getBegin() + "," + getEnd() + ")";
    }

    @JsonIgnore
    public String getFirstLastString() {
        return "[" + getBegin() + "," + getLast() + "]";
    }

    @JsonIgnore
    public int getLast() {
        if (limit < 0)
            return Integer.MAX_VALUE - 1;
        return offset + limit - 1;
    }

    @JsonIgnore
    public int getStart() {
        return offset;
    }

    @PropMeta(propId = 1)
    public int getOffset() {
        return offset;
    }

    @PropMeta(propId = 2)
    public int getLimit() {
        return limit;
    }

    public boolean hasLimit() {
        return limit >= 0;
    }

    public int hashCode() {
        return Integer.hashCode(offset * 37 + limit);
    }

    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;

        if (getClass() != o.getClass())
            return false;

        IntRangeBean other = (IntRangeBean) o;
        return offset == other.offset && limit == other.limit;
    }

    @JsonIgnore
    public int getEnd() {
        if (limit < 0)
            return Integer.MAX_VALUE;
        return offset + limit;
    }

    @JsonIgnore
    public int getBegin() {
        return offset;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return limit == 0;
    }

    @JsonIgnore
    public boolean isSingle() {
        return limit == 1;
    }

    @JsonIgnore
    public boolean isAllowSplit() {
        return limit > 1;
    }

    /**
     * 将当前区间转换为 IntRangeSet
     */
    public IntRangeSet toRangeSet() {
        return new IntRangeSet(java.util.Collections.singletonList(this), true);
    }

    public IntRangeBean move(int offset) {
        if (offset == 0)
            return this;
        return of(this.offset + offset, limit);
    }

    public IntRangeBean first(int n) {
        if (this.limit <= n)
            return this;

        return of(offset, n);
    }

    public IntRangeBean last(int n) {
        if (this.limit <= n)
            return this;
        return of(offset + limit - n, n);
    }

    /**
     * 将当前区间拆成subCount份，返回subIndex对应的区间
     *
     * @param subIndex 子区间下标，从0开始
     * @param subCount 子区间总个数
     * @return 子区间范围
     */
    public IntRangeBean partitionRange(int subIndex, int subCount) {
        return calcPartitionRange(limit, offset, subIndex, subCount);
    }

    public static IntRangeBean calcPartitionRange(int limit, int offset, int subIndex, int subCount) {
        int step = limit / subCount;
        int remainder = limit % subCount;
        int begin = offset + step * subIndex;
        if (remainder > 0) {
            if (subIndex < remainder) {
                begin += subIndex;
                step++;
            } else {
                begin += remainder;
            }
        }
        return of(begin, step);
    }

    public boolean containsValue(int value) {
        return offset <= value && value < getEnd();
    }

    public boolean contains(IntRangeBean range) {
        if (range == null) {
            return false;
        }
        return offset <= range.getOffset() && range.getEnd() <= getEnd();
    }

    /**
     * 判断区间是否重叠
     *
     * @param range
     * @return
     */
    public boolean overlaps(IntRangeBean range) {
        if (range == null) {
            return false;
        }

        if (range.isEmpty() || this.isEmpty())
            return false;

        return range.containsValue(getOffset())
                || range.containsValue(getEnd() - 1)
                || containsValue(range.getOffset());
    }

    public IntRangeBean intersect(IntRangeBean range) {
        if (range == null)
            return this;

        int lowerCmp = Integer.compare(offset, range.getOffset());
        int upperCmp = Integer.compare(getEnd(), range.getEnd());

        if (lowerCmp >= 0 && upperCmp <= 0) {
            return this;
        } else if (lowerCmp <= 0 && upperCmp >= 0) {
            return range;
        } else {
            int start = lowerCmp >= 0 ? this.offset : range.getOffset();
            int end = upperCmp <= 0 ? getEnd() : range.getEnd();

            if (start >= end)
                return of(Math.min(start, end), 0);

            if (end == Integer.MAX_VALUE)
                return of(start, -1);

            return of(start, end - start);
        }
    }

    @Override
    public int compareTo(IntRangeBean o) {
        int cmp = Integer.compare(offset, o.offset);
        if (cmp != 0)
            return cmp;

        return Integer.compare(getEnd(), o.getEnd());
    }

    public IntRangeBean union(IntRangeBean range) {
        int offset = Math.min(range.getOffset(), this.offset);
        int end = Math.max(range.getEnd(), this.getEnd());
        return of(offset, end - offset);
    }

    public static IntRangeBean unionAll(List<IntRangeBean> ranges) {
        if (ranges.isEmpty())
            return IntRangeBean.of(0, 0);

        int offset = Integer.MAX_VALUE;
        int end = Integer.MIN_VALUE;
        for (IntRangeBean range : ranges) {
            offset = Math.min(offset, range.getOffset());
            end = Math.max(end, range.getEnd());
        }
        return of(offset, end - offset);
    }
}