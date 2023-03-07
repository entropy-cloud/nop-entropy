/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.bit;

import java.util.Collection;

/**
 * 对BloomFilter的封装。可以判断集合中是否包含某个元素，用于去重
 *
 * @param <T>
 */
public interface IFilterSet<T> {

    boolean isEmpty();

    boolean contains(T elm);

    void add(T elm);

    void clear();

    default boolean containsAll(Collection<? extends T> c) {
        if (c == null)
            return true;

        for (T elm : c) {
            if (!contains(elm))
                return false;
        }
        return true;
    }

    default void addAll(Collection<? extends T> c) {
        if (c != null) {
            for (T elm : c) {
                add(elm);
            }
        }
    }
}