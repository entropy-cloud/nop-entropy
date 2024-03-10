/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.graphql.core.ast._gen._GraphQLUnionTypeDefinition;

public class GraphQLUnionTypeDefinition extends _GraphQLUnionTypeDefinition {
    private GraphQLObjectDefinition mergedDefinition;

    public String getTypesSource() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = getTypes().size(); i < n; i++) {
            sb.append(getTypes().get(i).getName());
            if (i != n - 1)
                sb.append('|');
        }
        return sb.toString();
    }

    /**
     * 将union类型所有子类型的属性合并到一起形成一个对象类型
     */
    public GraphQLObjectDefinition getMergedDefinition() {
        return mergedDefinition;
    }

    public void setMergedDefinition(GraphQLObjectDefinition mergedDefinition) {
        this.mergedDefinition = mergedDefinition;
    }
}
