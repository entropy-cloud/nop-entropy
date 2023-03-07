/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import java.util.Comparator;
import java.util.function.BiFunction;

public class FieldComparator<T> implements Comparator<T> {
    private final String field;
    private final boolean asc;
    private final Boolean nullsFirst;
    private final BiFunction<T, String, Object> propGetter;

    public FieldComparator(String field, boolean asc,
                           Boolean nullsFirst, BiFunction<T, String, Object> propGetter) {
        this.field = field;
        this.asc = asc;
        this.nullsFirst = nullsFirst;
        this.propGetter = propGetter;
    }

    @Override
    public int compare(T o1, T o2) {
        boolean b = nullsFirst != null ? nullsFirst : asc;
        Object v1 = o1 == null ? null : propGetter.apply(o1, field);
        Object v2 = o2 == null ? null : propGetter.apply(o2, field);
        int cmp = b ? SafeOrderedComparator.NULLS_FIRST.compare(v1, v2)
                : SafeOrderedComparator.NULLS_LAST.compare(v1, v2);
        return cmp;
    }
}