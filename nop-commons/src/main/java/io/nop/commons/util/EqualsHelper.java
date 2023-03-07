/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class EqualsHelper {
    public static boolean deepEquals(Object o1, Object o2) {
        if (o1 == o2)
            return true;
        if (o1 == null || o2 == null)
            return false;

        if (o1 instanceof Collection && o2 instanceof Collection) {
            return collectionDeepEquals((Collection<?>) o1, (Collection<?>) o2);
        } else if (o1 instanceof Map && o2 instanceof Map) {
            return mapDeepEquals((Map<?, ?>) o1, (Map<?, ?>) o2);
        }
        return o1.equals(o2);
    }

    public static boolean collectionDeepEquals(Collection<?> c1, Collection<?> c2) {
        if (c1.size() != c2.size())
            return false;

        Iterator<?> it1 = c1.iterator();
        Iterator<?> it2 = c2.iterator();
        while (it1.hasNext()) {
            Object o1 = it1.next();
            Object o2 = it2.next();
            if (!deepEquals(o1, o2))
                return false;
        }

        return true;
    }

    public static boolean mapDeepEquals(Map<?, ?> m1, Map<?, ?> m2) {
        if (m1.size() != m2.size())
            return false;

        for (Map.Entry<?, ?> entry : m1.entrySet()) {
            Object o1 = entry.getValue();
            Object o2 = m2.get(entry.getKey());

            if (!deepEquals(o1, o2))
                return false;
        }
        return true;
    }
}
