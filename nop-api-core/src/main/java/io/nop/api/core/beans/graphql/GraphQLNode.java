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
public class GraphQLNode {
    private String id;

    @PropMeta(propId = 1)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}