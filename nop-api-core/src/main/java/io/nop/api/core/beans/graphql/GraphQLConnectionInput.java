/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans.graphql;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLInput;
import io.nop.api.core.annotations.meta.PropMeta;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.OrderFieldBean;

import java.util.List;

/**
 * 针对关联子表的查询条件，属性命名与GraphQL标准的Connection保持一致
 */
@DataBean
@GraphQLInput
public class GraphQLConnectionInput {
    /**
     * first表示从afterCursor开始向后取n条数据
     */
    private int first;
    private int last;
    private String after;
    private String before;

    /**
     * 如果没有设置cursor，则也可以使用offset/limit机制进行分页
     */
    private long offset;
    private TreeBean filter;
    private List<OrderFieldBean> orderBy;

    @PropMeta(propId = 1)
    public int getFirst() {
        return first;
    }

    public void setFirst(int first) {
        this.first = first;
    }

    @PropMeta(propId = 2)
    public int getLast() {
        return last;
    }

    public void setLast(int last) {
        this.last = last;
    }

    @PropMeta(propId = 3)
    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    @PropMeta(propId = 4)
    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    @PropMeta(propId = 5)
    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    @PropMeta(propId = 6)
    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    @PropMeta(propId = 7)
    public List<OrderFieldBean> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<OrderFieldBean> orderBy) {
        this.orderBy = orderBy;
    }
}