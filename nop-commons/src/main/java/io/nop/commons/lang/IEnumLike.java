/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.lang;

public interface IEnumLike<E extends IEnumLike<E>> extends Comparable<E> {
    String label();

    default String value() {
        return label();
    }

    default String code() {
        return null;
    }

    int ordinal();

    int hashCode();

    boolean equals(Object o);
}