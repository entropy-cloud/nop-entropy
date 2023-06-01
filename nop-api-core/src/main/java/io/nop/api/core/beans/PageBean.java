/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.List;

@DataBean
public final class PageBean<T> implements Serializable {

    private static final long serialVersionUID = 511496891406307402L;
    private List<T> items;

    private long total;
    private long offset;
    private int limit;
    private Boolean hasPrev;
    private Boolean hasNext;
    private String prevCursor;
    private String nextCursor;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getHasPrev() {
        return hasPrev;
    }

    public void setHasPrev(Boolean hasPrev) {
        this.hasPrev = hasPrev;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getHasNext() {
        return hasNext;
    }

    public void setHasNext(Boolean hasNext) {
        this.hasNext = hasNext;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPrevCursor() {
        return prevCursor;
    }

    public void setPrevCursor(String prevCursor) {
        this.prevCursor = prevCursor;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPageCount() {
        if (total <= 0)
            return 1;
        if (limit <= 0)
            return 0;

        return (total + limit - 1) / limit;
    }

    public long getPage() {
        if (offset <= 0)
            return 1;
        if (limit <= 0)
            return 1;

        return (offset + limit - 1) / limit + 1;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }
}