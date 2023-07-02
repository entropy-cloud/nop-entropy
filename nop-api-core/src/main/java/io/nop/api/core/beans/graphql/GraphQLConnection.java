/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans.graphql;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;

import java.util.List;

@DataBean
@GraphQLObject
public class GraphQLConnection<T> {

    private long total;
    private List<GraphQLEdgeBean> edges;

    private List<T> items;

    private GraphQLPageInfo pageInfo;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<GraphQLEdgeBean> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphQLEdgeBean> edges) {
        this.edges = edges;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public GraphQLPageInfo getPageInfo() {
        return pageInfo;
    }

    public void setPageInfo(GraphQLPageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }
}