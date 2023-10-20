/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.Guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class IntRangeSet implements IJsonString {
    private final List<IntRangeBean> ranges;
    private final boolean sorted;

    public IntRangeSet(List<IntRangeBean> ranges, boolean sorted) {
        this.ranges = Guard.notEmpty(ranges, "ranges");
        this.sorted = ranges.size() == 1 || sorted;
    }

    public static IntRangeSet rangeSet(List<IntRangeBean> ranges, boolean sorted) {
        return new IntRangeSet(ranges, sorted);
    }

    public static IntRangeSet rangeSet(List<IntRangeBean> ranges) {
        return rangeSet(ranges, false);
    }

    public List<IntRangeBean> getRanges() {
        return ranges;
    }

    @StaticFactoryMethod
    public static IntRangeSet parse(String text) {
        if (ApiStringHelper.isEmpty(text)) return null;

        List<String> parts = ApiStringHelper.split(text, '|');
        List<IntRangeBean> ranges = new ArrayList<>(parts.size());
        for (String part : parts) {
            IntRangeBean range = IntRangeBean.parse(part);
            ranges.add(range);
        }
        return new IntRangeSet(ranges, false);
    }

    public String toString() {
        return ApiStringHelper.join(ranges, "|");
    }

    public int getFirstBegin() {
        return ranges.get(0).getBegin();
    }

    public int getLastEnd() {
        return ranges.get(ranges.size() - 1).getEnd();
    }

    public int getTotalSize() {
        int n = 0;
        for (IntRangeBean range : ranges) {
            n += range.getLimit();
        }
        return n;
    }

    public int size() {
        return ranges.size();
    }

    public IntRangeSet sort() {
        if (sorted) return this;

        IntRangeBean[] array = toSortedArray();
        return rangeSet(Arrays.asList(array));
    }

    private IntRangeBean[] toSortedArray() {
        IntRangeBean[] array = ranges.toArray(new IntRangeBean[size()]);
        if (!sorted) Arrays.sort(array);
        return array;
    }

    /**
     * 合并所有相互连接的区间
     */
    public IntRangeSet compact() {
        if (size() == 1) return this;

        IntRangeBean[] array = toSortedArray();
        List<IntRangeBean> list = new ArrayList<>(array.length);
        IntRangeBean prev = array[0];

        for (int i = 1, n = array.length; i < n; i++) {
            IntRangeBean next = array[i];
            if (next.isEmpty())
                continue;

            if (prev.overlaps(next) || prev.getEnd() == next.getOffset()) {
                prev = prev.union(next);
            } else {
                list.add(prev);
                prev = next;
            }
        }
        list.add(prev);
        return rangeSet(list, true);
    }

    /**
     * 将整个区间切分为n份
     */
    public List<IntRangeSet> split(int n) {
        if (n <= 1) return Collections.singletonList(this);

        int total = getTotalSize();

        if (total <= n) {
            List<IntRangeSet> ret = new ArrayList<>(total);
            for (IntRangeBean range : ranges) {
                for (int index : range) {
                    ret.add(rangeSet(Collections.singletonList(IntRangeBean.intRange(index, 1))));
                }
            }
            return ret;
        } else {
            RangeSplitter splitter = new RangeSplitter(total, n);
            return splitter.split();
        }
    }

    class RangeSplitter implements Function<IntRangeBean, IntRangeBean> {
        List<IntRangeSet> ret = new ArrayList<>(size());

        int step;
        int remainder;

        int subSize = 0;
        int subIndex = 0;
        List<IntRangeBean> list = null;

        public RangeSplitter(int total, int n) {
            this.step = total / n;
            this.remainder = total % n;
        }

        public List<IntRangeSet> split() {
            forEach(this);
            if (list != null) ret.add(rangeSet(list, true));
            return ret;
        }

        @Override
        public IntRangeBean apply(IntRangeBean range) {
            if (subSize == 0) {
                if (subIndex < remainder) {
                    subSize = step + 1;
                } else {
                    subSize = step;
                }
                list = new ArrayList<>(step + 1);
                subIndex++;
            }

            if (range.getLimit() < subSize) {
                subSize -= range.getLimit();
                list.add(range);
                return null;
            } else {
                list.add(range.first(subSize));
                ret.add(rangeSet(list, true));
                list = null;
                IntRangeBean left = subSize == range.getLimit() ? null : range.last(range.getLimit() - subSize);
                subSize = 0;
                return left;
            }
        }
    }

    void forEach(Function<IntRangeBean, IntRangeBean> consumer) {
        IntRangeBean[] array = toSortedArray();
        for (IntRangeBean range : array) {
            do {
                IntRangeBean left = consumer.apply(range);
                if (left != null) {
                    range = left;
                }else{
                    break;
                }
            } while (true);
        }
    }
}