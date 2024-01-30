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
import io.nop.api.core.annotations.meta.PropMeta;

@DataBean
@GraphQLObject
public class GraphQLEdgeBean {
    private GraphQLNode node;
    private String cursor;

    @PropMeta(propId = 1)
    public GraphQLNode getNode() {
        return node;
    }

    public void setNode(GraphQLNode node) {
        this.node = node;
    }

    @PropMeta(propId = 2)
    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }
}
