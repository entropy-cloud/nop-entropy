/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dataset.record;

import java.util.Collection;
import java.util.function.BiConsumer;

/**
 * 按照规则将一个记录拆分成多个记录
 *
 * @param <T>
 */
public interface IRecordSplitter<T, R,C> {

    default void splitMulti(Collection<? extends T> data, BiConsumer<String, R> collector,C context) {
        for (T record : data) {
            split(record, collector,context);
        }
    }

    void split(T record, BiConsumer<String, R> collector, C context);
}