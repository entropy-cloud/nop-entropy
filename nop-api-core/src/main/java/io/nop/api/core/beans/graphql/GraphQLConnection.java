/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans.graphql;

import io.nop.api.core.annotations.data.DataBean;

import java.util.List;

@DataBean
public class GraphQLConnection<T> {
    private long totalCount;
    private List<GraphQLEdgeBean> edges;

    private List<T> data;

    private GraphQLPageInfo pageInfo;

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }

    public List<GraphQLEdgeBean> getEdges() {
        return edges;
    }

    public void setEdges(List<GraphQLEdgeBean> edges) {
        this.edges = edges;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public GraphQLPageInfo getPageInfo() {
        return pageInfo;
    }

    public void setPageInfo(GraphQLPageInfo pageInfo) {
        this.pageInfo = pageInfo;
    }
}