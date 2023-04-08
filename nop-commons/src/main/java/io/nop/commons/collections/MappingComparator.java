/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import java.util.Comparator;
import java.util.function.Function;

public class MappingComparator<T> implements Comparator<T> {
    private final boolean desc;
    private final Boolean nullsFirst;
    private final Function<T, Object> propGetter;

    public MappingComparator(boolean desc,
                             Boolean nullsFirst, Function<T, Object> propGetter) {
        this.desc = desc;
        this.nullsFirst = nullsFirst;
        this.propGetter = propGetter;
    }

    public boolean isAsc() {
        return !desc;
    }

    @Override
    public int compare(T o1, T o2) {
        boolean b = nullsFirst != null ? nullsFirst : isAsc();
        Object v1 = o1 == null ? null : propGetter.apply(o1);
        Object v2 = o2 == null ? null : propGetter.apply(o2);
        int cmp = b ? SafeOrderedComparator.NULLS_FIRST.compare(v1, v2)
                : SafeOrderedComparator.NULLS_LAST.compare(v1, v2);
        return cmp;
    }
}