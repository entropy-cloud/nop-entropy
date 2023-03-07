/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

import java.util.Comparator;

public class OrderedComparator<T extends IOrdered> implements Comparator<T> {
    static OrderedComparator<IOrdered> DEFAULT = new OrderedComparator<>();

    public static <T extends IOrdered> OrderedComparator<T> instance() {
        return (OrderedComparator<T>) DEFAULT;
    }

    @Override
    public int compare(T o1, T o2) {
        return Integer.compare(o1.order(), o2.order());
    }
}
