/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.api;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.ICancelToken;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class CrudApiItemIterator<T> implements Iterator<T> {

    private final CrudApiPageIterator<T> batchIterator;
    private Iterator<T> currentBatchIterator;

    public CrudApiItemIterator(CrudApiPageIterator<T> batchIterator) {
        this.batchIterator = batchIterator;
    }

    public CrudApiItemIterator(ICrudApi<T> api, QueryBean query,
                               FieldSelectionBean selection, ICancelToken cancelToken,
                               int pageSize) {
        this(new CrudApiPageIterator<>(api, query, selection, cancelToken, pageSize));
    }

    public CrudApiItemIterator(ICrudApi<T> api, QueryBean query,
                               FieldSelectionBean selection, ICancelToken cancelToken) {
        this(new CrudApiPageIterator<>(api, query, selection, cancelToken));
    }

    public static <T> CrudApiItemIterator<T> of(ICrudApi<T> api, QueryBean query,
                                                FieldSelectionBean selection, ICancelToken cancelToken) {
        return new CrudApiItemIterator<>(api, query, selection, cancelToken);
    }

    public static <T> CrudApiItemIterator<T> of(ICrudApi<T> api, QueryBean query,
                                                FieldSelectionBean selection, ICancelToken cancelToken,
                                                int pageSize) {
        return new CrudApiItemIterator<>(api, query, selection, cancelToken, pageSize);
    }

    @Override
    public boolean hasNext() {
        if (currentBatchIterator != null && currentBatchIterator.hasNext())
            return true;

        if (batchIterator.hasNext()) {
            List<T> batch = batchIterator.next();
            currentBatchIterator = batch.iterator();
            return currentBatchIterator.hasNext();
        }

        return false;
    }

    @Override
    public T next() {
        if (!hasNext())
            throw new NoSuchElementException();
        return currentBatchIterator.next();
    }

    public String getCursor() {
        return batchIterator.getCursor();
    }

    public boolean isEof() {
        return batchIterator.isEof();
    }
}
