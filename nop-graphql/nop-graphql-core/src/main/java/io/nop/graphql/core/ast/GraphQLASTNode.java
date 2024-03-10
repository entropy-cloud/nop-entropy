/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.ApiConstants;
import io.nop.core.lang.ast.ASTNode;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;

public abstract class GraphQLASTNode extends ASTNode<GraphQLASTNode> {
    public abstract GraphQLASTKind getASTKind();

    @JsonProperty(ApiConstants.TREE_BEAN_PROP_TYPE)
    public String getASTType() {
        return getASTKind().toString();
    }

    public abstract GraphQLASTNode deepClone();

    public String toSource() {
        GraphQLSourcePrinter printer = new GraphQLSourcePrinter();
        return printer.print(this);
    }
}