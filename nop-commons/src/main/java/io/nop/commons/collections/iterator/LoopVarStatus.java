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

import static io.nop.commons.CommonErrors.ERR_COLLECTIONS_ITERATOR_EOF;

/**
 * @author canonical_entropy@163.com
 */
public class LoopVarStatus<T> implements Iterator<T> {
    private final Iterator<T> it;

    private T current;
    private int index = -1;
    private boolean last;
    private final boolean readOnly;

    public LoopVarStatus(Iterator<T> it, boolean readOnly) {
        this.it = it;
        this.last = !it.hasNext();
        this.readOnly = readOnly;
    }

    public LoopVarStatus(Iterable<T> iterable, boolean readOnly) {
        this(iterable.iterator(), readOnly);
    }

    public LoopVarStatus(Iterable<T> iterable) {
        this(iterable.iterator(), true);
    }

    @Override
    public T next() {
        if (last)
            throw new NopException(ERR_COLLECTIONS_ITERATOR_EOF);

        current = it.next();
        index++;
        last = !it.hasNext();
        return current;
    }

    public T getValue() {
        return current;
    }

    public int getCount() {
        return index + 1;
    }

    public int getIndex() {
        return index;
    }

    public boolean isFirst() {
        return index == 0;
    }

    public boolean isLast() {
        return last;
    }

    @Override
    public boolean hasNext() {
        return !last;
    }

    @Override
    public void remove() {
        if (readOnly) {
            throw new UnsupportedOperationException("remove");
        } else {
            it.remove();
        }
    }
}