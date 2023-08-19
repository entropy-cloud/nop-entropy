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

    public int getFirst() {
        return first;
    }

    public void setFirst(int first) {
        this.first = first;
    }

    public int getLast() {
        return last;
    }

    public void setLast(int last) {
        this.last = last;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public TreeBean getFilter() {
        return filter;
    }

    public void setFilter(TreeBean filter) {
        this.filter = filter;
    }

    public List<OrderFieldBean> getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(List<OrderFieldBean> orderBy) {
        this.orderBy = orderBy;
    }
}