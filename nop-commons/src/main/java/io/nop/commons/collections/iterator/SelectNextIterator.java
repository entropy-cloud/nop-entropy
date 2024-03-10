/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.collections.iterator;

import io.nop.api.core.exceptions.NopException;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import static io.nop.commons.CommonErrors.ERR_COLLECTIONS_ITERATOR_EOF;

public class SelectNextIterator<T> implements Iterator<T> {
    private final Function<T, List<T>> provider;

    private List<T> entityList = Collections.emptyList();
    private int currentIndex;
    private T lastEntity;
    private boolean eof;

    public SelectNextIterator(Function<T, List<T>> provider) {
        this.provider = provider;
    }

    @Override
    public boolean hasNext() {
        if (eof)
            return false;

        if (!entityList.isEmpty()) {
            return true;
        }

        return fetchNext();
    }

    private boolean fetchNext() {
        currentIndex = 0;
        entityList = provider.apply(lastEntity);
        if (entityList.isEmpty()) {
            eof = true;
            return false;
        }
        lastEntity = entityList.get(entityList.size() - 1);
        return true;
    }

    @Override
    public T next() {
        if (!hasNext())
            throw new NoSuchElementException();

        T ret = entityList.get(currentIndex++);
        if (currentIndex >= entityList.size())
            entityList = Collections.emptyList();
        return ret;
    }
}