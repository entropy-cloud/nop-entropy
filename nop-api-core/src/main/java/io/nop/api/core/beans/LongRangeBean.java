/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.ApiErrors;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;

import java.io.Serializable;
import java.util.List;

@DataBean
public class LongRangeBean implements Serializable, Comparable<LongRangeBean> {
    private static final long serialVersionUID = -5743883059551504946L;

    //public static final LongRangeBean FIRST = longRange(0, 1);

    private static final char SEPARATOR = ',';

    private final long offset;
    private final long limit;

    @JsonCreator
    public LongRangeBean(@JsonProperty("offset") long offset, @JsonProperty("limit") long limit) {
        this.offset = offset;
        this.limit = Guard.nonNegativeLong(limit, "limit");
    }

    public static LongRangeBean longRange(long offset, long limit) {
        return of(offset, limit);
    }

    public static LongRangeBean of(long offset, long limit) {
        return new LongRangeBean(offset, limit);
    }

    @StaticFactoryMethod
    public static LongRangeBean parse(String str) {
        if (str == null || str.length() <= 0)
            return null;

        int pos = str.indexOf(SEPARATOR);
        if (pos < 0) {
            Long start = ConvertHelper.stringToLong(str,
                    err -> new NopException(ApiErrors.ERR_INVALID_OFFSET_LIMIT_STRING).param(ApiErrors.ARG_VALUE, str));
            return of(start, 1);
        }

        Long start = ConvertHelper.stringToLong(str.substring(0, pos),
                err -> new NopException(ApiErrors.ERR_INVALID_OFFSET_LIMIT_STRING).param(ApiErrors.ARG_VALUE, str));
        Long limit = ConvertHelper.stringToLong(str.substring(pos + 1),
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

    @PropMeta(propId = 1)
    public long getOffset() {
        return offset;
    }

    @PropMeta(propId = 2)
    public long getLimit() {
        return limit;
    }

    public boolean hasLimit() {
        return limit >= 0;
    }

    public int hashCode() {
        return Long.hashCode(offset * 37 + limit);
    }

    public boolean equals(Object o) {
        if (this == o)
            return true;

        if (!(o instanceof LongRangeBean))
            return false;

        LongRangeBean other = (LongRangeBean) o;
        return offset == other.offset && limit == other.limit;
    }

    @JsonIgnore
    public long getBegin() {
        return offset;
    }

    @JsonIgnore
    public long getEnd() {
        if (limit < 0)
            return Long.MAX_VALUE;
        return offset + limit;
    }

    @JsonIgnore
    public long getFirst() {
        return offset;
    }

    @JsonIgnore
    public long getLast() {
        if (limit < 0)
            return Long.MAX_VALUE;
        if (limit == 0)
            return offset;
        return offset + limit - 1;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return limit == 0;
    }

    @JsonIgnore
    public boolean isSingle() {
        return limit == 1;
    }

    public LongRangeBean first(int n) {
        if (this.limit <= n)
            return this;

        return of(offset, n);
    }

    public LongRangeBean last(int n) {
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
    public LongRangeBean partitionRange(int subIndex, int subCount) {
        return calcPartitionRange(limit, offset, subIndex, subCount);
    }

    public static LongRangeBean calcPartitionRange(long limit, long offset, int subIndex, int subCount) {
        long step = limit / subCount;
        long remainder = limit % subCount;
        long begin = offset + step * subIndex;
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

    public boolean containsValue(long value) {
        return offset <= value && value < getEnd();
    }

    public boolean contains(LongRangeBean range) {
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
    public boolean overlaps(LongRangeBean range) {
        if (range == null) {
            return false;
        }

        if (range.isEmpty() || this.isEmpty())
            return false;

        return range.containsValue(getOffset())
                || range.containsValue(getEnd() - 1)
                || containsValue(range.getOffset());
    }

    public LongRangeBean intersect(LongRangeBean range) {
        if (range == null)
            return this;

        int lowerCmp = Long.compare(offset, range.getOffset());
        int upperCmp = Long.compare(getEnd(), range.getEnd());

        if (lowerCmp >= 0 && upperCmp <= 0) {
            return this;
        } else if (lowerCmp <= 0 && upperCmp >= 0) {
            return range;
        } else {
            long start = lowerCmp >= 0 ? this.offset : range.getOffset();
            long end = upperCmp <= 0 ? getEnd() : range.getEnd();

            if (start >= end)
                return of(Math.min(start, end), 0);

            if (end == Long.MAX_VALUE)
                return of(start, -1L);

            return of(start, end - start);
        }
    }

    @Override
    public int compareTo(LongRangeBean o) {
        int cmp = Long.compare(offset, o.offset);
        if (cmp != 0)
            return cmp;

        return Long.compare(getEnd(), o.getEnd());
    }

    public LongRangeBean union(LongRangeBean range) {
        long offset = Math.min(range.getOffset(), this.offset);
        long end = Math.max(range.getEnd(), this.getEnd());
        return of(offset, end - offset);
    }

    public static LongRangeBean unionAll(List<LongRangeBean> ranges) {
        if (ranges.isEmpty())
            return of(0, 0);

        long offset = Long.MAX_VALUE;
        long end = Long.MIN_VALUE;
        for (LongRangeBean range : ranges) {
            offset = Math.min(offset, range.getOffset());
            end = Math.max(end, range.getEnd());
        }
        return of(offset, end - offset);
    }
}