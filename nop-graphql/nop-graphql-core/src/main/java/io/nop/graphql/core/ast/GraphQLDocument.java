/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core.ast;

import io.nop.api.core.util.INeedInit;
import io.nop.commons.mutable.MutableInt;
import io.nop.graphql.core.ast._gen._GraphQLDocument;
import io.nop.graphql.core.schema.utils.GraphQLSourcePrinter;

import java.util.ArrayList;

public class GraphQLDocument extends _GraphQLDocument implements INeedInit {
    private boolean resolved;

    public GraphQLDocument() {
        setDefinitions(new ArrayList<>());
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String toSource() {
        return new GraphQLSourcePrinter().print(this);
    }

    public int getAllDirectiveCount() {
        MutableInt count = new MutableInt();
        new GraphQLASTVisitor() {
            @Override
            public void visitGraphQLDirective(GraphQLDirective node) {
                count.incrementAndGet();
            }
        }.visit(this);
        return count.get();
    }

    public void init() {
        for (GraphQLDefinition def : this.makeDefinitions()) {
            def.init();
        }
    }

    public GraphQLDefinition getDefinitionByName(String name) {
        for (GraphQLDefinition def : this.getDefinitions()) {
            if (def.getName().equals(name))
                return def;
        }
        return null;
    }

    public GraphQLObjectDefinition getObjectDefinitionByName(String name) {
        return (GraphQLObjectDefinition) this.getDefinitionByName(name);
    }

    public boolean isOperationQuery() {
        int count = 0;
        for (GraphQLDefinition def : this.getDefinitions()) {
            if (def instanceof GraphQLOperation) {
                count++;
                continue;
            } else if (def instanceof GraphQLFragment) {
                continue;
            } else {
                // 前端提交的查询只允许operation和Fragment两个部分
                return false;
            }
        }
        return count == 1;
    }

    public GraphQLOperation getOperation() {
        for (GraphQLDefinition def : this.getDefinitions()) {
            if (def instanceof GraphQLOperation)
                return (GraphQLOperation) def;
        }
        return null;
    }

    public GraphQLFragment getFragment(String name) {
        for (GraphQLDefinition def : this.getDefinitions()) {
            if (def instanceof GraphQLFragment) {
                GraphQLFragment fragment = (GraphQLFragment) def;
                if (name.equals(fragment.getName()))
                    return fragment;
            }
        }
        return null;
    }

    public GraphQLOperation getOperationByType(GraphQLOperationType opType) {
        for (GraphQLDefinition def : this.getDefinitions()) {
            if (def instanceof GraphQLOperation) {
                GraphQLOperation op = (GraphQLOperation) def;
                if (op.getOperationType().equals(opType)) {
                    return op;
                }
            }
        }
        return null;
    }

    public GraphQLOperation makeOperationByType(GraphQLOperationType opType) {
        GraphQLOperation op = getOperationByType(opType);
        if (op == null) {
            op = new GraphQLOperation();
            op.setOperationType(opType);
        }
        this.definitions.add(op);
        return op;
    }

    public GraphQLFieldSelection addOperation(GraphQLOperationType opType, String actionName, Object request) {
        GraphQLOperation op = makeOperationByType(opType);
        return op.addBizAction(actionName, request);
    }
}