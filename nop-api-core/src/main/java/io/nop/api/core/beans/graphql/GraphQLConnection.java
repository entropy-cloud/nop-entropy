/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.graphql;

import io.nop.api.core.annotations.core.LazyLoad;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.graphql.GraphQLObject;
import io.nop.api.core.annotations.meta.PropMeta;

import java.util.List;

@DataBean
@GraphQLObject
public class GraphQLConnection<T> {

    private long total;
    private List<GraphQLEdgeBean> edges;

    private List<T> items;

    private GraphQLPageInfo pageInfo;

    @PropMeta(propId = 1)
    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    @PropMeta(propId = 2)
    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    @LazyLoad
    @PropMeta(propId = 3)
    public List<GraphQLEdgeBean> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphQLEdgeBean> edges) {
        this.edges = edges;
    }

    @LazyLoad
    @PropMeta(propId = 4)
    public GraphQLPageInfo getPageInfo() {
        return pageInfo;
    }

    public void setPageInfo(GraphQLPageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }
}