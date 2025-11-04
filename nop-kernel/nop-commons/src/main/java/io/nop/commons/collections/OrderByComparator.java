/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import io.nop.api.core.beans.query.OrderFieldBean;

import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;

public class OrderByComparator<T> implements Comparator<T> {
    private final List<OrderFieldBean> orderBy;
    private final BiFunction<T, String, Object> propGetter;

    public OrderByComparator(List<OrderFieldBean> orderBy, BiFunction<T, String, Object> propGetter) {
        this.orderBy = orderBy;
        this.propGetter = propGetter;
    }

    @Override
    public int compare(T o1, T o2) {
        for (OrderFieldBean field : orderBy) {
            String name = field.getName();
            boolean nullsFirst = field.shouldNullsFirst();

            Object v1 = o1 == null ? null : propGetter.apply(o1, name);
            Object v2 = o2 == null ? null : propGetter.apply(o2, name);
            int cmp = nullsFirst ? SafeOrderedComparator.NULLS_FIRST.compare(v1, v2)
                    : SafeOrderedComparator.NULLS_LAST.compare(v1, v2);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }
}