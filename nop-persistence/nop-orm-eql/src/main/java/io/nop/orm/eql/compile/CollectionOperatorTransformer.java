package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTVisitor;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExistsExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlSubQueryExpr;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlWhere;
import io.nop.orm.eql.enums.SqlCollectionOperator;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.utils.EqlASTBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

import static io.nop.orm.eql.OrmEqlErrors.ARG_COLLECTION_PREFIX1;
import static io.nop.orm.eql.OrmEqlErrors.ARG_COLLECTION_PREFIX2;
import static io.nop.orm.eql.OrmEqlErrors.ARG_OPERATOR1;
import static io.nop.orm.eql.OrmEqlErrors.ARG_OPERATOR2;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_CANNOT_MERGE_COLLECTION_SCOPES;
import static io.nop.orm.eql.OrmEqlErrors.ERR_EQL_COLLECTION_PREFIX_MISMATCH;

/**
 * EQL中的collection operator支持，文档参见eql-collection-operator.md
 * 实现逻辑：
 * 1. 先遍历所有的SqlColName，根据每个SqlColName创建一个CollectionScope
 * 2. 根据每个SqlColName向上查找parent获取到and/or/where等逻辑节点
 * 3. 检查逻辑节点两个分支是否可以合并。合并条件必须满足eql-collection-operator.md中的条件，而且两个分支都必须对应于某个同样的collectionPrefix.
 * 4. 如果能够合并，则继续向上尝试是否可以合并。如果不能合并，则替换为exists语句，原先的SqlColName的owner则被改变。
 */
public class CollectionOperatorTransformer {
    private final IAliasGenerator aliasGenerator;

    public CollectionOperatorTransformer(IAliasGenerator aliasGenerator) {
        this.aliasGenerator = aliasGenerator;
    }

    public void transform(SqlWhere where) {
        // 第一步：收集所有包含集合操作符的SqlColumnName
        List<CollectionScope> collectionScopes = new ArrayList<>();
        collectCollectionScopes(where, collectionScopes);

        if (collectionScopes.isEmpty())
            return;

        // 第二步：为每个CollectionScope构建逻辑节点
        buildLogicNodes(collectionScopes);

        if (collectionScopes.size() == 1) {
            transformToExistsClauses(collectionScopes);
            return;
        }

        // 第三步：合并相同collectionPrefix的条件
        // removedScopes 对应于scope合并后，已经不作为独立scope存在，被合并到已有scope中的哪些scope
        List<CollectionScope> removedScopes = new ArrayList<>();
        mergeConditions(collectionScopes, removedScopes);

        // 第四步：将合并后的条件转换为EXISTS/NOT EXISTS子句
        transformToExistsClauses(collectionScopes);
    }

    private void collectCollectionScopes(SqlWhere where, List<CollectionScope> collectionScopes) {
        EqlASTVisitor visitor = new EqlASTVisitor() {
            @Override
            public void visitSqlColumnName(SqlColumnName colName) {
                CollectionScope scope = CollectionScope.build(colName);
                if (scope != null) {
                    collectionScopes.add(scope);
                }
            }
        };

        // 使用visit方法来遍历整个程序
        visitor.visit(where);
    }

    private void buildLogicNodes(List<CollectionScope> collectionScopes) {
        for (CollectionScope scope : collectionScopes) {
            SqlColumnName colName = scope.getColNameNode();
            EqlASTNode parentNode = findLogicUnitNode(colName);
            scope.setLogicUnitNode(parentNode);
        }
    }

    private EqlASTNode findLogicUnitNode(EqlASTNode fromNode) {
        // 向上查找逻辑节点（AND/OR/WHERE等）
        EqlASTNode node = fromNode;
        EqlASTNode parent = node.getASTParent();
        while (parent != null) {
            EqlASTKind kind = parent.getASTKind();
            if (kind == EqlASTKind.SqlAndExpr || kind == EqlASTKind.SqlOrExpr ||
                    kind == EqlASTKind.SqlWhere) {
                return node;
            }
            parent = parent.getASTParent();
        }
        return node;
    }

    private void mergeConditions(List<CollectionScope> collectionScopes,
                                 List<CollectionScope> removedScopes) {
        if (collectionScopes.size() <= 1) {
            return;
        }

        Queue<CollectionScope> processing = new ArrayDeque<>(collectionScopes);
        do {
            CollectionScope scope = processing.poll();
            if (scope == null)
                break;

            Iterator<CollectionScope> it = processing.iterator();
            while (it.hasNext()) {
                CollectionScope scope2 = it.next();
                // 同一个分支
                if (canMergeSameBranch(scope, scope2)) {
                    removedScopes.add(scope2);
                } else if (canMergeScope(scope, scope2)) {
                    removedScopes.add(scope2);
                    scope.setLogicUnitNode(findLogicUnitNode(scope2.getLogicUnitNode().getASTParent()));

                    // 合并scope之后重新检查
                    processing.add(scope);
                }
            }
        } while (true);
    }

    /**
     * 判断两个CollectionScope是否在同一逻辑分支中，可以合并。
     * 根据eql-collection-operator.md文档，同一集合的_some条件自动合并到同一个EXISTS子句
     *
     * @throws NopException 当collectionPrefix不匹配或同一个逻辑分支不支持多个集合表达式时
     */
    private boolean canMergeSameBranch(CollectionScope scope1, CollectionScope scope2) {

        // 检查逻辑单元节点是否在同一分支中
        EqlASTNode logic1 = scope1.getLogicUnitNode();
        EqlASTNode logic2 = scope2.getLogicUnitNode();

        // 如果两个scope的逻辑单元节点相同，说明它们在同一表达式中
        if (logic1 != logic2) {
            return false;
        }


        // 检查操作符类型是否相同
        if (scope1.getOperator() != scope2.getOperator()) {
            throw new NopException(ERR_EQL_CANNOT_MERGE_COLLECTION_SCOPES)
                    .param(ARG_OPERATOR1, scope1.getOperator())
                    .param(ARG_OPERATOR2, scope2.getOperator())
                    .source(scope2.getColNameNode());
        }

        // 检查集合前缀是否相同（同一集合）
        if (!scope1.getCollectionPrefix().equals(scope2.getCollectionPrefix())) {
            throw new NopException(ERR_EQL_COLLECTION_PREFIX_MISMATCH)
                    .param(ARG_COLLECTION_PREFIX1, scope1.getCollectionPrefix())
                    .param(ARG_COLLECTION_PREFIX2, scope2.getCollectionPrefix())
                    .source(scope2.getColNameNode());
        }

        scope1.merge(scope2);
        return true;
    }

    /**
     * 判断两个CollectionScope是否可以合并。
     * 根据eql-collection-operator.md文档：
     * - _some条件：同一集合的AND条件可以合并，OR条件也可以合并到同一个EXISTS子句
     * - _all条件：AND条件可以合并，但OR条件不合并
     *
     * @throws NopException 当collectionPrefix不匹配或无法合并时
     */
    private boolean canMergeScope(CollectionScope scope1, CollectionScope scope2) {
        // 检查逻辑结构是否可以合并
        EqlASTNode logic1 = scope1.getLogicUnitNode();
        EqlASTNode logic2 = scope2.getLogicUnitNode();

        // 找到共同的父节点
        EqlASTNode parent1 = logic1.getASTParent();
        EqlASTNode parent2 = logic2.getASTParent();

        if (parent1 != parent2) {
            return false;
        }

        // 必须具有相同的集合前缀（同一集合）
        if (!scope1.getCollectionPrefix().equals(scope2.getCollectionPrefix())) {
            return false;
        }

        // 操作符类型必须相同
        if (scope1.getOperator() != scope2.getOperator()) {
            return false;
        }

        // 如果父节点相同
        // 对于_some操作符，AND和OR条件都可以合并
        if (scope1.getOperator() == SqlCollectionOperator.SOME) {
            if (parent1.getASTKind() != EqlASTKind.SqlAndExpr &&
                    parent1.getASTKind() != EqlASTKind.SqlOrExpr) {
                return false;
            }
        } else if (scope1.getOperator() == SqlCollectionOperator.ALL) {
            // 对于_all操作符，只有AND条件可以合并，OR条件不合并
            if (parent1.getASTKind() != EqlASTKind.SqlAndExpr) {
                return false;
            }
        }

        scope1.merge(scope2);
        return true;
    }

    private void transformToExistsClauses(List<CollectionScope> scopes) {
        for (CollectionScope scope : scopes) {
            transformCollectionScope(scope);
        }
    }

    private void transformCollectionScope(CollectionScope scope) {
        // 根据操作符类型创建对应的EXISTS子句
        SqlExistsExpr existsExpr = createExistsExpr(scope);

        // 替换原来的逻辑节点
        replaceLogicNode(scope, existsExpr);

        if (scope.getMergedColNames() != null) {
            List<SqlColumnName> colNames = scope.getMergedColNames();
            for (SqlColumnName colName : colNames) {
                colName.setOwner(EqlASTBuilder.qualifier(scope.getAlias()));
            }
        }

        if (scope.hasChild()) {
            scope.getChildren().values().forEach(this::transformCollectionScope);
        } else {
            SqlColumnName colName = scope.getColNameNode();
            colName.setOwner(EqlASTBuilder.qualifier(scope.getAlias()));
        }
    }

    private SqlExistsExpr createExistsExpr(CollectionScope scope) {
        SqlCollectionOperator operator = scope.getOperator();
        String tableCollection = scope.getCollectionProp();
        String alias = makeAlias(scope);

        if (scope.getParent() != null)
            tableCollection = scope.getParent().getAlias() + '.' + tableCollection;

        SourceLocation loc = scope.getLocation();

        // 创建子查询
        SqlQuerySelect subquery = createSubquery(loc, tableCollection, alias);
        SqlSubQueryExpr subqueryExpr = new SqlSubQueryExpr();
        subqueryExpr.setSelect(subquery);

        SqlExistsExpr existsExpr = new SqlExistsExpr();
        existsExpr.setQuery(subqueryExpr);

        // 根据操作符类型设置NOT EXISTS
        if (operator == SqlCollectionOperator.ALL) {
            existsExpr.setNot(true);
        }

        return existsExpr;
    }

    private String makeAlias(CollectionScope scope) {
        String alias = scope.getAlias();
        if (alias == null) {
            alias = aliasGenerator.genTableAlias();
            scope.setAlias(alias);
        }
        return alias;
    }

    private SqlQuerySelect createSubquery(SourceLocation loc, String collectionProp, String alias) {
        // 创建子查询
        SqlQuerySelect subquery = new SqlQuerySelect();
        subquery.setLocation(loc);

        // 创建投影
        SqlNumberLiteral literalOne = new SqlNumberLiteral();
        literalOne.setLocation(loc);
        literalOne.setValue("1");
        SqlExprProjection projection = new SqlExprProjection();
        projection.setExpr(literalOne);

        List<SqlProjection> projections = new ArrayList<>();
        projections.add(projection);
        subquery.setProjections(projections);

        // 创建FROM子句
        SqlSingleTableSource tableSource = createTableSource(collectionProp);
        tableSource.setAlias(EqlASTBuilder.alias(alias, true));
        SqlFrom from = new SqlFrom();
        List<SqlTableSource> tableSources = new ArrayList<>();
        tableSources.add(tableSource);
        from.setTableSources(tableSources);
        subquery.setFrom(from);

        // 创建WHERE子句，包含关联条件和用户条件
        SqlWhere where = new SqlWhere();
        subquery.setWhere(where);

        return subquery;
    }

    private SqlSingleTableSource createTableSource(String relationProp) {
        SqlTableName tableName = EqlASTBuilder.tableName(relationProp);

        SqlSingleTableSource tableSource = new SqlSingleTableSource();
        tableSource.setTableName(tableName);

        return tableSource;
    }

    private void replaceLogicNode(CollectionScope scope, SqlExistsExpr existsExpr) {
        EqlASTNode oldLogicNode = scope.getLogicUnitNode();

        EqlASTNode parent = oldLogicNode.getASTParent();
        oldLogicNode.setASTParent(null);
        oldLogicNode.setASTParent(existsExpr.getQuery().getWhere());

        if (parent instanceof SqlBinaryExpr) {
            SqlBinaryExpr binaryExpr = (SqlBinaryExpr) parent;
            // 替换二元表达式中的对应分支
            if (binaryExpr.getLeft() == oldLogicNode) {
                binaryExpr.setLeft(existsExpr);
            } else if (binaryExpr.getRight() == oldLogicNode) {
                binaryExpr.setRight(existsExpr);
            }
        } else if (parent instanceof SqlWhere) {
            SqlWhere where = (SqlWhere) parent;
            where.setExpr(existsExpr);
        }
    }
}
