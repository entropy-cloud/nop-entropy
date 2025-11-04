/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.selector;

import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathValueSelector;

import java.util.Collections;
import java.util.List;

public class FixedValueSelector<E> implements IXPathValueSelector<E, Object> {
    private static final long serialVersionUID = -7313489009097643942L;
    private final Object value;

    public FixedValueSelector(Object value) {
        this.value = value;
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<Object> collector) {
        return collector.collect(value);
    }

    @Override
    public Object select(E source, IXPathContext<E> context) {
        return value;
    }

    @Override
    public List<Object> selectAll(E source, IXPathContext<E> context) {
        return Collections.singletonList(value);
    }
}
