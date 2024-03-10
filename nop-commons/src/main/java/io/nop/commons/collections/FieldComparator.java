/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import java.util.Comparator;
import java.util.function.BiFunction;

public class FieldComparator<T> implements Comparator<T> {
    private final String field;
    private final boolean desc;
    private final Boolean nullsFirst;
    private final BiFunction<T, String, Object> propGetter;

    public FieldComparator(String field, boolean desc,
                           Boolean nullsFirst, BiFunction<T, String, Object> propGetter) {
        this.field = field;
        this.desc = desc;
        this.nullsFirst = nullsFirst;
        this.propGetter = propGetter;
    }

    public String getField() {
        return field;
    }

    public boolean isAsc() {
        return !desc;
    }

    @Override
    public int compare(T o1, T o2) {
        boolean b = nullsFirst != null ? nullsFirst : isAsc();
        Object v1 = o1 == null ? null : propGetter.apply(o1, field);
        Object v2 = o2 == null ? null : propGetter.apply(o2, field);
        int cmp = b ? SafeOrderedComparator.NULLS_FIRST.compare(v1, v2)
                : SafeOrderedComparator.NULLS_LAST.compare(v1, v2);
        return cmp;
    }
}