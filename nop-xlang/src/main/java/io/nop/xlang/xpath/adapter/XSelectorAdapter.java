/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.adapter;

import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.adapter.IXNodeAdapter;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xpath.DefaultXPathContext;
import io.nop.xlang.xpath.IXPathValueSelector;

import java.util.Collection;

public class XSelectorAdapter<E> implements IXSelector<E> {
    private final IXNodeAdapter<E> adapter;
    private final IXPathValueSelector<E, Object> valueSelector;

    public XSelectorAdapter(IXNodeAdapter<E> adapter, IXPathValueSelector<E, Object> valueSelector) {
        this.adapter = adapter;
        this.valueSelector = valueSelector;
    }

    @Override
    public Object select(E node) {
        DefaultXPathContext<E> ctx = new DefaultXPathContext<>(node, adapter, XLang.newEvalScope());
        return valueSelector.select(node, ctx);
    }

    @Override
    public void updateSelected(E node, Object value) {

    }

    @Override
    public Collection<?> selectAll(E node) {
        DefaultXPathContext<E> ctx = new DefaultXPathContext<>(node, adapter, XLang.newEvalScope());
        return valueSelector.selectAll(node, ctx);
    }
}
