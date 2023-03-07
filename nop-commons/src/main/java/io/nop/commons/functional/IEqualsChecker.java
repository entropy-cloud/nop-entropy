/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional;

import java.util.Objects;

@FunctionalInterface
public interface IEqualsChecker<T> {
    // 对象的toString结果值相等
    IEqualsChecker<Object> STRING_EQUALS = new IEqualsChecker<Object>() {
        @Override
        public boolean isEquals(Object o1, Object o2) {
            if (Objects.equals(o1, o2))
                return true;

            boolean s1 = o1 instanceof String;
            boolean s2 = o2 instanceof String;
            if (s1 && s2)
                return false;

            if (s1 || s2) {
                if (o1 == null || o2 == null)
                    return false;

                return o1.toString().equals(o2.toString());
            }

            return false;
        }
    };

    boolean isEquals(T o1, T o2);
}
