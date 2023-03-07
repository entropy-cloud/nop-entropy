/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections;

import io.nop.api.core.util.IOrdered;

import java.util.Comparator;

public class SafeOrderedComparator implements Comparator<Object> {
    public static final SafeOrderedComparator DEFAULT = new SafeOrderedComparator(true);

    public static final SafeOrderedComparator NULLS_FIRST = new SafeOrderedComparator(true);

    public static final SafeOrderedComparator NULLS_LAST = new SafeOrderedComparator(false);

    private final boolean nullsFirst;

    public SafeOrderedComparator(boolean nullsFirst) {
        this.nullsFirst = nullsFirst;
    }

    @Override
    public int compare(Object o1, Object o2) {
        if (o1 == o2)
            return 0;

        if (nullsFirst) {
            // null小于所有非null的值
            if (o1 == null) {
                return -1;
            }

            if (o2 == null)
                return 1;
        } else {
            // null大于所有非null的值
            if (o1 == null) {
                return 1;
            }

            if (o2 == null)
                return -1;
        }

        if (o1.getClass() == o2.getClass()) {
            if (o1 instanceof Comparable) {
                return ((Comparable) o1).compareTo(o2);
            }
        }

        if (o1 instanceof IOrdered && o2 instanceof IOrdered) {
            return Integer.compare(getOrder(o1), getOrder(o2));
        }
        return 0;
    }

    int getOrder(Object o) {
        if (o instanceof IOrdered)
            return ((IOrdered) o).order();
        return IOrdered.NORMAL_PRIORITY;
    }
}
