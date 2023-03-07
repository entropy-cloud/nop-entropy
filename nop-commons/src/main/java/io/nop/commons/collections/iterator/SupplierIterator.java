/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.collections.iterator;

import io.nop.api.core.exceptions.NopException;

import java.util.Iterator;
import java.util.function.Supplier;

import static io.nop.commons.CommonErrors.ERR_COLLECTIONS_ITERATOR_EOF;

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
        if (hasNext()) {
            T result = (T) item;
            item = supplier.get();
            if (item == null)
                end = true;
            return result;
        } else {
            throw new NopException(ERR_COLLECTIONS_ITERATOR_EOF);
        }
    }

    @Override
    public boolean hasNext() {
        return !end;
    }
}