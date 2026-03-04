package io.nop.orm.eql.compile;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.orm.eql.ast.EqlASTKind;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.EqlASTVisitor;
import io.nop.orm.eql.ast.SqlAndExpr;
import io.nop.orm.eql.ast.SqlBinaryExpr;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExistsExpr;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.ast.SqlExprProjection;
import io.nop.orm.eql.ast.SqlFrom;
import io.nop.orm.eql.ast.SqlNumberLiteral;
import io.nop.orm.eql.ast.SqlOrExpr;
import io.nop.orm.eql.ast.SqlProjection;
import io.nop.orm.eql.ast.SqlQuerySelect;
import io.nop.orm.eql.ast.SqlSingleTableSource;
import io.nop.orm.eql.ast.SqlSubQueryExpr;
import io.nop.orm.eql.ast.SqlTableName;
import io.nop.orm.eql.ast.SqlTableSource;
import io.nop.orm.eql.ast.SqlWhere;
import io.nop.orm.eql.enums.SqlCollectionOperator;
import io.nop.orm.eql.enums.SqlOperator;
import io.nop.orm.eql.sql.IAliasGenerator;
import io.nop.orm.eql.utils.EqlASTBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

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
        List<CollectionScope> collectionScopes = new ArrayList<>();
        collectCollectionScopes(where, collectionScopes);

        if (collectionScopes.isEmpty())
            return;

        buildLogicNodes(collectionScopes);

        if (collectionScopes.size() == 1) {
            transformToExistsClauses(collectionScopes);
            return;
        }

        List<CollectionScope> removedScopes = new ArrayList<>();
        mergeConditions(collectionScopes, removedScopes);

        collectionScopes.removeAll(removedScopes);

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
        EqlASTNode node = fromNode;
        EqlASTNode parent = node.getASTParent();
        while (parent != null) {
            EqlASTKind kind = parent.getASTKind();
            if (kind == EqlASTKind.SqlAndExpr || kind == EqlASTKind.SqlOrExpr ||
                    kind == EqlASTKind.SqlWhere) {
                return node;
            }
            node = parent;
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
        Set<CollectionScope> alreadyMerged = new HashSet<>();
        
        do {
            CollectionScope scope = processing.poll();
            if (scope == null)
                break;

            Iterator<CollectionScope> it = processing.iterator();
            while (it.hasNext()) {
                CollectionScope scope2 = it.next();
                
                // Skip if comparing with itself
                if (scope == scope2) {
                    continue;
                }
                
                // 同一个分支
                if (canMergeSameBranch(scope, scope2)) {
                    removedScopes.add(scope2);
                    it.remove();
                } else if (canMergeScope(scope, scope2)) {
                    removedScopes.add(scope2);
                    it.remove();
                    scope.setLogicUnitNode(findLogicUnitNode(scope2.getLogicUnitNode().getASTParent()));

                    // Only re-add if not already merged in this round
                    if (!alreadyMerged.contains(scope)) {
                        processing.add(scope);
                        alreadyMerged.add(scope);
                    }
                }
            }
        } while (true);
    }

    private boolean canMergeScope(CollectionScope scope1, CollectionScope scope2) {
        if (!scope1.getCollectionPrefix().equals(scope2.getCollectionPrefix())) {
            return false;
        }

        if (scope1.getOperator() != scope2.getOperator()) {
            return false;
        }

        EqlASTNode logic1 = scope1.getLogicUnitNode();
        EqlASTNode logic2 = scope2.getLogicUnitNode();

        if (scope1.getOperator() == SqlCollectionOperator.SOME) {
            return canMergeSomeScopes(scope1, scope2, logic1, logic2);
        } else if (scope1.getOperator() == SqlCollectionOperator.ALL) {
            return canMergeAllScopes(scope1, scope2, logic1, logic2);
        }

        return false;
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

    private boolean canMergeSomeScopes(CollectionScope scope1, CollectionScope scope2,
                                              EqlASTNode logic1, EqlASTNode logic2) {
        EqlASTNode parent1 = logic1.getASTParent();
        EqlASTNode parent2 = logic2.getASTParent();

        if (parent1 == parent2) {
                if (parent1.getASTKind() == EqlASTKind.SqlAndExpr ||
                    parent1.getASTKind() == EqlASTKind.SqlOrExpr) {
                    scope1.merge(scope2);
                    return true;
                }
                return false;
            }

        SqlAndExpr commonAnd = findCommonAndAncestor(logic1, logic2);
        if (commonAnd != null) {
            scope1.merge(scope2);
            scope1.setLogicUnitNode(commonAnd);
            return true;
        }

        return false;
    }

    private boolean canMergeAllScopes(CollectionScope scope1, CollectionScope scope2,
                                             EqlASTNode logic1, EqlASTNode logic2) {
        EqlASTNode parent1 = logic1.getASTParent();
        EqlASTNode parent2 = logic2.getASTParent();

        if (parent1 != parent2) {
            return false;
            }

        if (parent1.getASTKind() != EqlASTKind.SqlAndExpr) {
                return false;
            }

        scope1.merge(scope2);
        return true;
    }

    private SqlAndExpr findCommonAndAncestor(EqlASTNode node1, EqlASTNode node2) {
        Set<EqlASTNode> ancestors = new HashSet<>();
        EqlASTNode current = node1;
        while (current != null) {
            if (current.getASTKind() == EqlASTKind.SqlAndExpr) {
                ancestors.add(current);
            }
            current = current.getASTParent();
        }

        current = node2;
        while (current != null) {
            if (current.getASTKind() == EqlASTKind.SqlAndExpr && ancestors.contains(current)) {
                return (SqlAndExpr) current;
            }
            current = current.getASTParent();
        }

        return null;
    }

    private void transformToExistsClauses(List<CollectionScope> scopes) {
        for (CollectionScope scope : scopes) {
            transformCollectionScope(scope);
        }
    }

    private void transformCollectionScope(CollectionScope scope) {
        
        makeAlias(scope);
        
        if (scope.hasChild()) {
            transformNestedScopes(scope);
        }

        SqlExistsExpr existsExpr = createExistsExpr(scope);
        replaceLogicNode(scope, existsExpr);

        if (scope.getMergedColNames() != null) {
            List<SqlColumnName> colNames = scope.getMergedColNames();
            for (SqlColumnName colName : colNames) {
                colName.setOwner(EqlASTBuilder.qualifier(scope.getAlias()));
            }
        }

        if (!scope.hasChild()) {
            SqlColumnName colName = scope.getColNameNode();
            colName.setOwner(EqlASTBuilder.qualifier(scope.getAlias()));
        }
    }

    private void processNestedScopesInSubquery(CollectionScope parentScope, SqlExistsExpr parentExistsExpr) {
        SqlQuerySelect subquery = (SqlQuerySelect) parentExistsExpr.getQuery().getSelect();
        SqlWhere subqueryWhere = subquery.getWhere();
        if (subqueryWhere == null || subqueryWhere.getExpr() == null)
            return;

        SqlExpr condition = subqueryWhere.getExpr();

        for (CollectionScope childScope : parentScope.getChildren().values()) {
            String fullPrefix = getFullCollectionPrefix(childScope);
            EqlASTNode nestedLogicNode = findNestedLogicNode(condition, fullPrefix);
            if (nestedLogicNode != null) {
                childScope.setLogicUnitNode(nestedLogicNode);
                childScope.setAlias(makeAlias(childScope));
                transformCollectionScope(childScope);
            }
        }
    }

    private EqlASTNode findNestedLogicNode(SqlExpr condition, String collectionPrefix) {
        final List<EqlASTNode> logicNodes = new ArrayList<>();
        EqlASTVisitor visitor = new EqlASTVisitor() {
            @Override
            public void visitSqlColumnName(SqlColumnName colName) {
                String fullName = colName.getFullName();
                if (fullName != null && fullName.startsWith(collectionPrefix)) {
                    EqlASTNode logicNode = findLogicUnitNode(colName);
                    if (logicNode != null && !logicNodes.contains(logicNode)) {
                        logicNodes.add(logicNode);
                    }
                }
            }
        };
        visitor.visit(condition);
        
        if (logicNodes.isEmpty()) {
            return null;
        }
        
        if (logicNodes.size() == 1) {
            return logicNodes.get(0);
        }
        
        return findCommonAncestor(logicNodes);
    }

    private EqlASTNode findCommonAncestor(List<EqlASTNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        
        EqlASTNode common = nodes.get(0);
        for (int i = 1; i < nodes.size(); i++) {
            common = findLowestCommonAncestor(common, nodes.get(i));
            if (common == null) {
                return null;
            }
        }
        return common;
    }

    private EqlASTNode findLowestCommonAncestor(EqlASTNode node1, EqlASTNode node2) {
        Set<EqlASTNode> ancestors = new HashSet<>();
        EqlASTNode current = node1;
        while (current != null) {
            ancestors.add(current);
            current = current.getASTParent();
        }
        
        current = node2;
        while (current != null) {
            if (ancestors.contains(current)) {
                return current;
            }
            current = current.getASTParent();
        }
        return null;
    }

    private void transformNestedScopes(CollectionScope scope) {
        SqlExpr conditionExpr;
        boolean isNestedScope = scope.getParent() != null && scope.getParent().getClonedCondition() != null;
        
        if (isNestedScope) {
            conditionExpr = scope.getParent().getClonedCondition();
            scope.setClonedCondition(conditionExpr);
        } else if (scope.getClonedCondition() != null) {
            conditionExpr = scope.getClonedCondition();
        } else {
            SqlExpr originalCondition = (SqlExpr) scope.getLogicUnitNode();
            conditionExpr = (SqlExpr) originalCondition.deepClone();
            scope.setClonedCondition(conditionExpr);
        }

        for (CollectionScope child : scope.getChildren().values()) {
            String fullPrefix = getFullCollectionPrefix(child);
            EqlASTNode nestedLogicNode = findNestedLogicNode(conditionExpr, fullPrefix);
            if (nestedLogicNode != null) {
                child.setLogicUnitNode(nestedLogicNode);
                transformCollectionScope(child);
            }
        }

        if (!isNestedScope) {
            SqlExpr finalCondition = scope.getClonedCondition();
            EqlASTNode parent = scope.getLogicUnitNode().getASTParent();
            if (parent != null) {
                parent.replaceChild(scope.getLogicUnitNode(), finalCondition);
            }
            scope.setLogicUnitNode(finalCondition);
        }
    }

    private List<EqlASTNode> findAllNestedLogicNodes(SqlExpr condition, CollectionScope parentScope) {
        List<EqlASTNode> result = new ArrayList<>();
        for (CollectionScope child : parentScope.getChildren().values()) {
            String fullPrefix = getFullCollectionPrefix(child);
            EqlASTNode nestedLogicNode = findNestedLogicNode(condition, fullPrefix);
            if (nestedLogicNode != null) {
                result.add(nestedLogicNode);
            }
        }
        return result;
    }

//    private EqlASTNode findCorrespondingNode(SqlExpr clonedCondition, EqlASTNode originalNode) {
//        if (originalNode == null || clonedCondition == null)
//            return null;
//
//        String originalSql = originalNode.toSqlString();
//        return findNodeBySqlString(clonedCondition, originalSql);
//    }

//    private EqlASTNode findNodeBySqlString(EqlASTNode node, String targetSql) {
//        if (node == null)
//            return null;
//
//        String normalizedTarget = normalizeSql(targetSql);
//        if (normalizeSql(node.toSqlString()).equals(normalizedTarget)) {
//            return node;
//        }
//
//        if (node instanceof SqlBinaryExpr) {
//            SqlBinaryExpr binaryExpr = (SqlBinaryExpr) node;
//            EqlASTNode leftResult = findNodeBySqlString(binaryExpr.getLeft(), targetSql);
//            if (leftResult != null)
//                return leftResult;
//            return findNodeBySqlString(binaryExpr.getRight(), targetSql);
//        }
//
//        if (node instanceof SqlAndExpr) {
//            SqlAndExpr andExpr = (SqlAndExpr) node;
//            EqlASTNode leftResult = findNodeBySqlString(andExpr.getLeft(), targetSql);
//            if (leftResult != null)
//                return leftResult;
//            return findNodeBySqlString(andExpr.getRight(), targetSql);
//        }
//
//        if (node instanceof SqlOrExpr) {
//            SqlOrExpr orExpr = (SqlOrExpr) node;
//            EqlASTNode leftResult = findNodeBySqlString(orExpr.getLeft(), targetSql);
//            if (leftResult != null)
//                return leftResult;
//            return findNodeBySqlString(orExpr.getRight(), targetSql);
//        }
//
//        return null;
//    }

//    private String normalizeSql(String sql) {
//        return sql.replaceAll("\\s+", " ").trim();
//    }
//
//    private void buildLogicNodesForChildren(CollectionScope scope) {
//        if (scope.getChildren() != null) {
//            for (CollectionScope child : scope.getChildren().values()) {
//                SqlColumnName colName = child.getColNameNode();
//                if (colName != null) {
//                    EqlASTNode logicNode = findLogicUnitNode(colName);
//                    child.setLogicUnitNode(logicNode);
//                }
//                buildLogicNodesForChildren(child);
//            }
//        }
//    }

    private SqlExistsExpr createExistsExpr(CollectionScope scope) {
        SqlCollectionOperator operator = scope.getOperator();
        String tableCollection = scope.getCollectionProp();
        String alias = makeAlias(scope);

        if (scope.getParent() != null)
            tableCollection = scope.getParent().getAlias() + '.' + tableCollection;

        SourceLocation loc = scope.getLocation();

        SqlQuerySelect subquery = createSubquery(loc, tableCollection, alias);
        SqlSubQueryExpr subqueryExpr = new SqlSubQueryExpr();
        subqueryExpr.setSelect(subquery);

        SqlExistsExpr existsExpr = new SqlExistsExpr();
        existsExpr.setQuery(subqueryExpr);

        boolean shouldNegate = shouldNegateExists(scope);
        if (shouldNegate) {
            existsExpr.setNot(true);
        }

        return existsExpr;
    }

    private boolean shouldNegateExists(CollectionScope scope) {
        if (scope.hasChild()) {
            return false;
        }
        int allCount = countAllAncestors(scope);
        return (allCount % 2) == 1;
    }

    private int countAllAncestors(CollectionScope scope) {
        int count = 0;
        CollectionScope current = scope;
        while (current != null) {
            if (current.getOperator() == SqlCollectionOperator.ALL) {
                count++;
            }
            current = current.getParent();
        }
        return count;
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

        SqlExpr conditionExpr;
        if (scope.getClonedCondition() != null) {
            conditionExpr = scope.getClonedCondition();
            conditionExpr.setASTParent(null);
        } else {
            conditionExpr = (SqlExpr) oldLogicNode.deepClone();
            conditionExpr.setASTParent(null);
        }


        String alias = scope.getAlias();
        String collectionPrefix = getFullCollectionPrefix(scope);
        updateColNameOwners(conditionExpr, alias, collectionPrefix);

        if (!scope.hasChild()) {
            int allCount = countAllAncestors(scope);
            if (allCount % 2 == 1) {
                conditionExpr = negateConditionForAll(conditionExpr);
            }
        }

        SqlQuerySelect subquery = (SqlQuerySelect) existsExpr.getQuery().getSelect();
        subquery.makeWhere().setExpr(conditionExpr);

        if (parent == null) {
            if (scope.getParent() != null && scope.getParent().getClonedCondition() == oldLogicNode) {
                scope.getParent().setClonedCondition(existsExpr);
            }
            scope.setLogicUnitNode(existsExpr);
            return;
        }

        if (parent instanceof SqlAndExpr) {
            SqlAndExpr andExpr = (SqlAndExpr) parent;
            if (andExpr.getLeft() == oldLogicNode) {
                andExpr.setLeft(existsExpr);
            } else if (andExpr.getRight() == oldLogicNode) {
                andExpr.setRight(existsExpr);
            }
        } else if (parent instanceof SqlOrExpr) {
            SqlOrExpr orExpr = (SqlOrExpr) parent;
            if (orExpr.getLeft() == oldLogicNode) {
                orExpr.setLeft(existsExpr);
            } else if (orExpr.getRight() == oldLogicNode) {
                orExpr.setRight(existsExpr);
            }
        } else if (parent instanceof SqlBinaryExpr) {
            SqlBinaryExpr binaryExpr = (SqlBinaryExpr) parent;
            if (binaryExpr.getLeft() == oldLogicNode) {
                binaryExpr.setLeft(existsExpr);
            } else if (binaryExpr.getRight() == oldLogicNode) {
                binaryExpr.setRight(existsExpr);
            }
        } else if (parent instanceof SqlWhere) {
            SqlWhere where = (SqlWhere) parent;
            where.setExpr(existsExpr);
        }
        
        scope.setLogicUnitNode(existsExpr);
    }

    private String getFullCollectionPrefix(CollectionScope scope) {
        if (scope.getParent() == null) {
            return scope.getCollectionPrefix();
        }
        return getFullCollectionPrefix(scope.getParent()) + scope.getCollectionPrefix();
    }

    private void updateColNameOwners(SqlExpr expr, String alias, String collectionPrefix) {
        EqlASTVisitor visitor = new EqlASTVisitor() {
            @Override
            public void visitSqlColumnName(SqlColumnName colName) {
                String fullName = colName.getFullName();
                if (fullName != null && fullName.startsWith(collectionPrefix)) {
                    String remaining = fullName.substring(collectionPrefix.length());
                    if (!containsCollectionOperator(remaining)) {
                        colName.setOwner(EqlASTBuilder.qualifier(alias));
                    }
                }
            }
        };
        visitor.visit(expr);
    }

    private boolean containsCollectionOperator(String path) {
        return path.contains(SqlCollectionOperator.SOME.getPathPattern()) ||
               path.contains(SqlCollectionOperator.ALL.getPathPattern());
    }

    private SqlExpr negateConditionForAll(SqlExpr expr) {
        if (expr instanceof SqlAndExpr) {
            SqlAndExpr andExpr = (SqlAndExpr) expr;
            SqlExpr negatedLeft = negateConditionForAll(andExpr.getLeft());
            SqlExpr negatedRight = negateConditionForAll(andExpr.getRight());

            SqlOrExpr orExpr = new SqlOrExpr();
            orExpr.setLocation(expr.getLocation());
            orExpr.setLeft(negatedLeft.deepClone());
            orExpr.setRight(negatedRight.deepClone());
            return orExpr;
        }

        if (expr instanceof SqlOrExpr) {
            SqlOrExpr orExpr = (SqlOrExpr) expr;
            SqlExpr negatedLeft = negateConditionForAll(orExpr.getLeft());
            SqlExpr negatedRight = negateConditionForAll(orExpr.getRight());

            SqlAndExpr andExpr = new SqlAndExpr();
            andExpr.setLocation(expr.getLocation());
            andExpr.setLeft(negatedLeft.deepClone());
            andExpr.setRight(negatedRight.deepClone());
            return andExpr;
        }

        if (expr instanceof SqlBinaryExpr) {
            SqlBinaryExpr binaryExpr = (SqlBinaryExpr) expr;
            SqlOperator op = binaryExpr.getOperator();

            if (op == SqlOperator.AND) {
                SqlExpr negatedLeft = negateConditionForAll(binaryExpr.getLeft());
                SqlExpr negatedRight = negateConditionForAll(binaryExpr.getRight());
                binaryExpr.setLeft(negatedLeft);
                binaryExpr.setRight(negatedRight);
                binaryExpr.setOperator(SqlOperator.OR);
                return expr;
            }

            if (op == SqlOperator.OR) {
                SqlExpr negatedLeft = negateConditionForAll(binaryExpr.getLeft());
                SqlExpr negatedRight = negateConditionForAll(binaryExpr.getRight());
                binaryExpr.setLeft(negatedLeft);
                binaryExpr.setRight(negatedRight);
                binaryExpr.setOperator(SqlOperator.AND);
                return expr;
            }

            SqlOperator negatedOp = negateComparisonOperator(op);
            if (negatedOp != null) {
                binaryExpr.setOperator(negatedOp);
            }
        }
        return expr;
    }

    private SqlOperator negateComparisonOperator(SqlOperator op) {
        if (op == null)
            return null;
        switch (op) {
            case EQ:
                return SqlOperator.NE;
            case NE:
                return SqlOperator.EQ;
            case GT:
                return SqlOperator.LE;
            case GE:
                return SqlOperator.LT;
            case LT:
                return SqlOperator.GE;
            case LE:
                return SqlOperator.GT;
            default:
                return null;
        }
    }
}
