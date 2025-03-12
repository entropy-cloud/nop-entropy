/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

import java.io.Serializable;
import java.util.List;

@DataBean
public class PageBean<T> implements Serializable {

    private static final long serialVersionUID = 511496891406307402L;
    private List<T> items;

    private long total;
    private long offset;
    private int limit;
    private Boolean hasPrev;
    private Boolean hasNext;
    private String prevCursor;
    private String nextCursor;

    @PropMeta(propId = 1)
    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    @PropMeta(propId = 2)
    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    @PropMeta(propId = 3)
    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    @PropMeta(propId = 4)
    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    @PropMeta(propId = 5)
    public long getPageCount() {
        if (total <= 0)
            return 1;
        if (limit <= 0)
            return 0;

        return (total + limit - 1) / limit;
    }

    @PropMeta(propId = 6)
    public long getPage() {
        if (offset <= 0)
            return 1;
        if (limit <= 0)
            return 1;

        return (offset + limit - 1) / limit + 1;
    }

    @PropMeta(propId = 7)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(String nextCursor) {
        this.nextCursor = nextCursor;
    }

    @PropMeta(propId = 8)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getHasPrev() {
        return hasPrev;
    }

    public void setHasPrev(Boolean hasPrev) {
        this.hasPrev = hasPrev;
    }

    @PropMeta(propId = 9)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getHasNext() {
        return hasNext;
    }

    public void setHasNext(Boolean hasNext) {
        this.hasNext = hasNext;
    }

    @PropMeta(propId = 10)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPrevCursor() {
        return prevCursor;
    }

    public void setPrevCursor(String prevCursor) {
        this.prevCursor = prevCursor;
    }

}