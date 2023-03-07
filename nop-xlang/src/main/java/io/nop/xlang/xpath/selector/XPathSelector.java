/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.selector;

import io.nop.api.core.util.Guard;
import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;
import io.nop.xlang.xpath.IXPathOperator;
import io.nop.xlang.xpath.IXPathValueSelector;

import java.io.Serializable;

public class XPathSelector<E> implements IXPathValueSelector<E, Object>, Serializable {
    private static final long serialVersionUID = -8438075642579424103L;
    private final IXPathElementSelector<E> nodeSelector;
    private final IXPathOperator<E> operator;

    private XPathSelector(IXPathElementSelector<E> nodeSelector, IXPathOperator<E> operator) {
        this.nodeSelector = nodeSelector;
        this.operator = Guard.notNull(operator, "valueOperator");
    }

    public static <E> IXPathValueSelector<E, Object> of(IXPathElementSelector<E> nodeSelector) {
        return of(nodeSelector, null);
    }

    public static <E> IXPathValueSelector<E, Object> of(IXPathElementSelector<E> nodeSelector,
                                                        IXPathOperator<E> operator) {
        if (operator == null)
            return (IXPathValueSelector) nodeSelector;
        if (nodeSelector == CurrentNodeSelector.INSTANCE)
            nodeSelector = null;
        return new XPathSelector<>(nodeSelector, operator);
    }

    public String toString() {
        if (nodeSelector == null)
            return operator.toString();
        if (operator == null)
            return nodeSelector.toString();
        if (nodeSelector == RootSelector.INSTANCE)
            return "/" + operator;
        return nodeSelector + "/" + operator;
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<Object> collector) {
        if (nodeSelector == null) {
            return collector.collect(operator.apply(source, context));
        }

        SelectResult result = nodeSelector.select(source, context, node -> {
            Object value = operator.apply(node, context);
            return collector.collect(value);
        });
        return result;
    }
}