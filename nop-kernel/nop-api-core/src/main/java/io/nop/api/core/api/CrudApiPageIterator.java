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
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CancellationException;

public class CrudApiPageIterator<O> implements Iterator<List<O>> {

    private static final String FRAGMENT_DEFAULTS = "...F_defaults";
    private static final String FIELD_NEXT_CURSOR = "nextCursor";
    private static final String FIELD_HAS_NEXT = "hasNext";

    public static final int DEFAULT_PAGE_SIZE = 100;

    private final ICrudApi<?, O> api;
    private final QueryBean query;
    private final FieldSelectionBean selection;
    private final ICancelToken cancelToken;
    private final int pageSize;

    private String cursor;
    private List<O> currentBatch;
    private boolean eof;

    public CrudApiPageIterator(ICrudApi<?, O> api, QueryBean query,
                               FieldSelectionBean selection, ICancelToken cancelToken,
                               int pageSize) {
        this.api = Objects.requireNonNull(api);
        this.query = query;
        this.selection = buildSelectionWithCursor(selection);
        this.cancelToken = cancelToken;
        this.pageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        this.cursor = query != null ? query.getCursor() : null;
    }

    public CrudApiPageIterator(ICrudApi<?, O> api, QueryBean query,
                               FieldSelectionBean selection, ICancelToken cancelToken) {
        this(api, query, selection, cancelToken, DEFAULT_PAGE_SIZE);
    }

    public static <O> CrudApiPageIterator<O> of(ICrudApi<?, O> api, QueryBean query,
                                                FieldSelectionBean selection, ICancelToken cancelToken) {
        return new CrudApiPageIterator<>(api, query, selection, cancelToken);
    }

    public static <O> CrudApiPageIterator<O> of(ICrudApi<?, O> api, QueryBean query,
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
        // 已拉取且未消费的批次优先返回，不能被eof标志拦截，否则next()内部再次调用hasNext()会拿不到待消费批次
        if (currentBatch != null && !currentBatch.isEmpty())
            return true;

        if (eof)
            return false;

        if (cancelToken != null && cancelToken.isCancelled())
            throw new CancellationException("nop.cancelled:" + cancelToken.getCancelReason());

        String prevCursor = cursor;
        QueryBean pageQuery = buildPageQuery();
        PageBean<O> page = api.findPage(pageQuery, selection, cancelToken);

        if (page == null || page.getItems() == null || page.getItems().isEmpty()) {
            eof = true;
            return false;
        }

        currentBatch = page.getItems();
        cursor = page.getNextCursor();

        // 已取到的数据必须交付（hasNext=FALSE只表示这是最后一页，不能连数据一起丢弃）；
        // 后端未返回游标、或游标未推进时，无法构造出不同的下一页查询，
        // 交付本页后终止，避免对下游API的无限重复拉取
        if (Boolean.FALSE.equals(page.getHasNext())
                || currentBatch.size() < pageSize
                || ApiStringHelper.isEmpty(cursor)
                || Objects.equals(cursor, prevCursor)) {
            eof = true;
        }

        return true;
    }

    @Override
    public List<O> next() {
        if (!hasNext())
            throw new NoSuchElementException();

        List<O> result = currentBatch;
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
