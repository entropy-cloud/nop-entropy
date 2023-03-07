/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.select.selector;

import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.ISelector;
import io.nop.commons.functional.select.SelectResult;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;
import java.util.List;

public class CompositeSelector<E, C> implements ISelector<E, C, E>, Serializable {
    private static final long serialVersionUID = 5374473912602139760L;
    private final List<? extends ISelector<E, C, E>> selectors;

    public CompositeSelector(List<? extends ISelector<E, C, E>> selectors) {
        this.selectors = selectors;
    }

    public String toString() {
        return StringHelper.join(selectors, "/");
    }

    public List<? extends ISelector<E, C, E>> getSelectors() {
        return selectors;
    }

    @Override
    public SelectResult select(E element, C context, ISelectionCollector<E> consumer) {
        return runSelect(element, selectors, 0, context, consumer);
    }

    SelectResult runSelect(E element, List<? extends ISelector<E, C, E>> selectors, final int index, C context,
                           ISelectionCollector<E> consumer) {
        if (index >= selectors.size())
            return SelectResult.NOT_FOUND;

        ISelector<E, C, E> selector = selectors.get(index);

        SelectResult result = selector.select(element, context, elm -> {
            // if all selector are matched
            if (index + 1 >= selectors.size()) {
                return consumer.collect(elm);
            }

            // try to match the next selector
            return runSelect(elm, selectors, index + 1, context, consumer);
        });

        return result;
    }
}