/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.text.marker;

import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.util.Guard;

import java.io.Serializable;

/**
 * 对文本区间增加附加标记
 */
@ImmutableBean
public abstract class Marker implements Serializable, Comparable<Marker> {

    private static final long serialVersionUID = -1153465649000496861L;

    protected final int textBegin;
    protected final int textEnd;

    public Marker(int textStart, int textEnd) {
        Guard.checkArgument(textStart <= textEnd, "marker start should be less than end", textStart, textEnd);
        Guard.checkArgument(textStart >= 0, "marker start should be positive", textStart);

        this.textBegin = textStart;
        this.textEnd = textEnd;
    }

    public boolean within(int begin, int end) {
        return textBegin >= begin && textEnd <= end;
    }

    /**
     * 返回标记部分的文本
     *
     * @param text 文本整体
     * @return marker所标记部分的文本
     */
    public String getMarkedText(CharSequence text) {
        return text.subSequence(textBegin, textEnd).toString();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        return appendPos(sb).toString();
    }

    protected StringBuilder appendPos(StringBuilder sb) {
        sb.append(textBegin);
        if (textEnd != textBegin + 1)
            sb.append('-').append(textEnd);
        return sb;
    }

    @Override
    public int compareTo(Marker o) {
        return Integer.compare(textBegin, o.textBegin);
    }

    public int countOccur(String text, char c) {
        int count = 0;
        for (int i = textBegin; i < textEnd; i++) {
            if (text.charAt(i) == c)
                count++;
        }
        return count;
    }

    public boolean isEmpty() {
        return textBegin == textEnd;
    }

    /**
     * marker所标记的区间的长度
     *
     * @return 区间长度
     */
    public int length() {
        return textEnd - textBegin;
    }

    public boolean intersect(Marker o) {
        if (textBegin <= o.textBegin && textEnd > o.textBegin)
            return true;
        if (o.textBegin <= textBegin && o.textEnd > textBegin)
            return true;
        return false;
    }

    /**
     * 标记区间的起始位置，从0开始
     */
    public int getBegin() {
        return textBegin;
    }

    /**
     * 标记区间的结束位置。实际区间不包含此位置
     */
    public int getEnd() {
        return textEnd;
    }

    /**
     * marker的名称，一般用于显示
     */
    public String getName() {
        return null;
    }

    /**
     * 移动整个标记区间的位置。例如标记区间[1,5)移动offset=1之后得到新的区间[2,6)
     *
     * @param offset 移动的步长。
     * @return 新生成的标记对象
     */
    public abstract Marker offset(int offset);
}
