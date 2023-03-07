/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.selector;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;

import java.io.ObjectStreamException;

import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR;

public class ParentSelector<E> implements IXPathElementSelector<E> {
    public static final ParentSelector INSTANCE = new ParentSelector();
    private static final long serialVersionUID = 7710690741547150659L;

    public String toString() {
        return "..";
    }

    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }

    @Override
    public SelectResult select(E source, IXPathContext<E> context, ISelectionCollector<E> collector) {
        if (source == context.root())
            throw new NopException(ERR_XPATH_ROOT_NOT_ALLOW_PARENT_SELECTOR).param(ARG_NODE, source);

        E parent = context.adapter().getParent(source);
        return collector.collect(parent);
    }
}