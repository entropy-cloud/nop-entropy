/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

public class SupplierIterator<T> implements Iterator<T> {

    private final Supplier<T> supplier;

    private Object item;
    private boolean end;

    public SupplierIterator(Supplier<T> supplier) {
        this.supplier = supplier;
        this.item = supplier.get();
        if (item == null)
            end = true;
    }

    @Override
    public T next() {
        if (!hasNext())
            throw new NoSuchElementException();
        T result = (T) item;
        item = supplier.get();
        if (item == null)
            end = true;
        return result;
    }

    @Override
    public boolean hasNext() {
        return !end;
    }
}