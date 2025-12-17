package io.nop.orm.eql.compile;

import io.nop.api.core.util.ProcessResult;
import io.nop.commons.util.StringHelper;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTProcessor;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlNotExpr;
import io.nop.orm.eql.ast.SqlOrExpr;
import io.nop.orm.eql.ast.SqlRegularFunction;

class CollectionScopeCollector extends EqlASTProcessor<CollectionScope, CollectionScope> {

    @Override
    public CollectionScope defaultProcess(EqlASTNode node, CollectionScope context) {
        node.processChild(child -> {
            processAST(child, context);
            return ProcessResult.CONTINUE;
        });
        return context;
    }

    @Override
    public CollectionScope processSqlNotExpr(SqlNotExpr node, CollectionScope context) {
        return super.processSqlNotExpr(node, context);
    }

    @Override
    public CollectionScope processSqlAndExpr(SqlAndExpr node, CollectionScope context) {
        return super.processSqlAndExpr(node, context);
    }

    @Override
    public CollectionScope processSqlOrExpr(SqlOrExpr node, CollectionScope context) {
        return super.processSqlOrExpr(node, context);
    }

    @Override
    public CollectionScope processSqlColumnName(SqlColumnName node, CollectionScope context) {
        if (node.getOwner() == null)
            return context;

        if (node.getOwner().containsCollectorOperator()) {
            String path = node.getOwner().getFullName();
            if (context == null) {

            } else if (StringHelper.startsWithNamespace(path, context.getCollectionPrefix())) {
                return context;
            }
        }

        return context;
    }

    @Override
    public CollectionScope processSqlRegularFunction(SqlRegularFunction node, CollectionScope context) {
        return super.processSqlRegularFunction(node, context);
    }
}