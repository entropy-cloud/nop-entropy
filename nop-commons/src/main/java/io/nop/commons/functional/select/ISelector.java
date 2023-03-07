/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.select;

import io.nop.commons.functional.select.collector.SelectAllCollector;
import io.nop.commons.functional.select.collector.SelectOneCollector;

import java.util.List;

public interface ISelector<E, C, T> {

    default T select(E source, C context) {
        SelectOneCollector<T> collector = new SelectOneCollector<>();
        select(source, context, collector);
        return collector.getElement();
    }

    default List<T> selectAll(E source, C context) {
        SelectAllCollector<T> collector = new SelectAllCollector<>();
        select(source, context, collector);
        return collector.getElements();
    }

    SelectResult select(E source, C context, ISelectionCollector<T> collector);
}