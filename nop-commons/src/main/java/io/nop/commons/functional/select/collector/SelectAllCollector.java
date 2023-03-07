/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.functional.select.collector;

import io.nop.commons.functional.select.ISelectionCollector;
import io.nop.commons.functional.select.SelectResult;

import java.util.ArrayList;
import java.util.List;

public class SelectAllCollector<E> implements ISelectionCollector<E> {
    private List<E> elements = new ArrayList<>();

    @Override
    public SelectResult collect(E e) {
        elements.add(e);
        return SelectResult.FOUND;
    }

    public List<E> getElements() {
        return elements;
    }
}
