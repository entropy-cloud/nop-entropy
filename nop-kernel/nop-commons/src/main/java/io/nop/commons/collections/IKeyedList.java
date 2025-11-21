/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.IFreezable;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 按照唯一键管理的列表。如果重复加入具有同样键值的元素，则列表对应位置被更新。
 *
 * @param <T>
 */
public interface IKeyedList<T> extends List<T>, IFreezable {
    boolean containsKey(String key);

    String getKey(T obj);

    T getByKey(String key);

    T removeByKey(String key);

    Set<String> keySet();

    void addUnique(T obj, Function<String, NopException> errorFactory);

    /**
     * 按照IOrdered接口进行排序
     */
    void sort();
}