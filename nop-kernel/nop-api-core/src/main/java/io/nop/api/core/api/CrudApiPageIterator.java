/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.api;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.PageBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.ICancelToken;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CancellationException;

public class CrudApiPageIterator<T> implements Iterator<List<T>> {

    private static final String FRAGMENT_DEFAULTS = "...F_defaults";
    private static final String FIELD_NEXT_CURSOR = "nextCursor";
    private static final String FIELD_HAS_NEXT = "hasNext";

    public static final int DEFAULT_PAGE_SIZE = 100;

    private final ICrudApi<T> api;
    private final QueryBean query;
    private final FieldSelectionBean selection;
    private final ICancelToken cancelToken;
    private final int pageSize;

    private String cursor;
    private List<T> currentBatch;
    private boolean eof;

    public CrudApiPageIterator(ICrudApi<T> api, QueryBean query,
                               FieldSelectionBean selection, ICancelToken cancelToken,
                               int pageSize) {
        this.api = Objects.requireNonNull(api);
        this.query = query;
        this.selection = buildSelectionWithCursor(selection);
        this.cancelToken = cancelToken;
        this.pageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        this.cursor = query != null ? query.getCursor() : null;
    }

    public CrudApiPageIterator(ICrudApi<T> api, QueryBean query,
                               FieldSelectionBean selection, ICancelToken cancelToken) {
        this(api, query, selection, cancelToken, DEFAULT_PAGE_SIZE);
    }

    public static <T> CrudApiPageIterator<T> of(ICrudApi<T> api, QueryBean query,
                                                FieldSelectionBean selection, ICancelToken cancelToken) {
        return new CrudApiPageIterator<>(api, query, selection, cancelToken);
    }

    public static <T> CrudApiPageIterator<T> of(ICrudApi<T> api, QueryBean query,
                                                FieldSelectionBean selection, ICancelToken cancelToken,
                                                int pageSize) {
        return new CrudApiPageIterator<>(api, query, selection, cancelToken, pageSize);
    }

    private static FieldSelectionBean buildSelectionWithCursor(FieldSelectionBean selection) {
        if (selection == null || !selection.hasField()) {
            FieldSelectionBean newSelection = new FieldSelectionBean();
            newSelection.addCompositeField(FRAGMENT_DEFAULTS, false);
            newSelection.addField(FIELD_NEXT_CURSOR);
            newSelection.addField(FIELD_HAS_NEXT);
            return newSelection;
        }

        FieldSelectionBean cloned = selection.deepClone();
        cloned.addField(FIELD_NEXT_CURSOR);
        cloned.addField(FIELD_HAS_NEXT);
        return cloned;
    }

    @Override
    public boolean hasNext() {
        if (eof)
            return false;

        if (currentBatch != null && !currentBatch.isEmpty())
            return true;

        if (cancelToken != null && cancelToken.isCancelled())
            throw new CancellationException("nop.cancelled:" + cancelToken.getCancelReason());

        QueryBean pageQuery = buildPageQuery();
        PageBean<T> page = api.findPage(pageQuery, selection, cancelToken);

        if (page == null || page.getItems() == null || page.getItems().isEmpty() || Boolean.FALSE.equals(page.getHasNext())) {
            eof = true;
            return false;
        }

        currentBatch = page.getItems();
        cursor = page.getNextCursor();

        if (currentBatch.size() < pageSize) {
            eof = true;
        }

        return true;
    }

    @Override
    public List<T> next() {
        if (!hasNext())
            throw new NoSuchElementException();

        List<T> result = currentBatch;
        currentBatch = null;
        return result;
    }

    private QueryBean buildPageQuery() {
        QueryBean pageQuery;
        if (query == null) {
            pageQuery = new QueryBean();
        } else {
            pageQuery = query.cloneInstance();
        }

        pageQuery.setCursor(cursor);
        pageQuery.setLimit(pageSize);
        pageQuery.setOffset(0);

        return pageQuery;
    }

    public String getCursor() {
        return cursor;
    }

    public boolean isEof() {
        return eof;
    }
}
