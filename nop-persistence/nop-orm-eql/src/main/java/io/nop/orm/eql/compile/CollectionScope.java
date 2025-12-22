package io.nop.orm.eql.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.ast.SqlColumnName;
import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.enums.SqlCollectionOperator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollectionScope {
    private final SqlCollectionOperator operator;
    private final String collectionProp;
    private final String collectionPrefix;
    private String alias;
    private SqlColumnName colNameNode;
    private boolean singleCondition;

    // 向上提升，直到parent为and/or/where。
    // 比如 o.roles._some.status > 3 and o.status =1 ， loginUnitNode为 o.roles._some.status>3解析得到的SqlBinaryExpr
    private EqlASTNode logicUnitNode;

    private SourceLocation location;
    private CollectionScope parent;

    private List<SqlColumnName> mergedColNames;

    private Map<String, CollectionScope> children;

    public CollectionScope(SqlCollectionOperator operator, String collectionProp) {
        this.operator = operator;
        this.collectionProp = collectionProp;
        this.collectionPrefix = collectionProp + operator.getPathPattern();
    }

    public static CollectionScope build(SqlColumnName colNameNode) {
        String propPath = colNameNode.getFullName();

        // 查找所有集合操作符的位置
        int allPos = propPath.indexOf(SqlCollectionOperator.ALL.getPathPattern());
        int somePos = propPath.indexOf(SqlCollectionOperator.SOME.getPathPattern());

        // 如果没有找到集合操作符，返回null
        if (allPos < 0 && somePos < 0) {
            return null;
        }

        // 找到第一个出现的集合操作符
        int firstPos = Integer.MAX_VALUE;
        SqlCollectionOperator firstOperator = null;

        if (allPos >= 0) {
            firstPos = allPos;
            firstOperator = SqlCollectionOperator.ALL;
        }

        if (somePos >= 0 && somePos < firstPos) {
            firstPos = somePos;
            firstOperator = SqlCollectionOperator.SOME;
        }

        // 提取第一个操作符之前的路径作为前缀
        String prefix = propPath.substring(0, firstPos);
        String remainingPath = propPath.substring(firstPos + firstOperator.getPathPattern().length());

        // 如果前缀为空，说明操作符在路径开头，这是不合法的
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Collection operator cannot be at the beginning of property path: " + propPath);
        }

        // 创建根scope
        CollectionScope rootScope = new CollectionScope(firstOperator, prefix);
        rootScope.setColNameNode(colNameNode);
        // 递归处理剩余的路径
        buildNestedScopes(rootScope, remainingPath);

        return rootScope;
    }

    private static void buildNestedScopes(CollectionScope parentScope, String remainingPath) {
        // 检查剩余路径中是否还有集合操作符
        int allPos = remainingPath.indexOf(SqlCollectionOperator.ALL.getPathPattern());
        int somePos = remainingPath.indexOf(SqlCollectionOperator.SOME.getPathPattern());

        // 如果没有更多操作符，则设置finalProperty并返回
        if (allPos < 0 && somePos < 0) {
            return;
        }

        // 找到下一个操作符
        int nextPos = Integer.MAX_VALUE;
        SqlCollectionOperator nextOperator = null;

        if (allPos >= 0) {
            nextPos = allPos;
            nextOperator = SqlCollectionOperator.ALL;
        }

        if (somePos >= 0 && somePos < nextPos) {
            nextPos = somePos;
            nextOperator = SqlCollectionOperator.SOME;
        }

        // 提取当前操作符之前的路径作为前缀
        String prefix = remainingPath.substring(0, nextPos);
        String newRemainingPath = remainingPath.substring(nextPos + nextOperator.getPathPattern().length());

        // 如果前缀为空，说明有连续的操作符，这是不合法的
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Consecutive collection operators are not allowed: " + remainingPath);
        }

        // 创建子scope并添加到父scope
        CollectionScope childScope = new CollectionScope(nextOperator, prefix);
        childScope.setColNameNode(parentScope.getColNameNode());
        parentScope.addChildScope(childScope);

        // 递归处理剩余路径
        buildNestedScopes(childScope, newRemainingPath);
    }

    public SourceLocation getLocation() {
        return location;
    }

    public void setLocation(SourceLocation location) {
        this.location = location;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public boolean isSingleCondition() {
        return singleCondition;
    }

    public void setSingleCondition(boolean singleCondition) {
        this.singleCondition = singleCondition;
    }

    public String getFinalProperty() {
        return colNameNode.getName();
    }

    public CollectionScope getParent() {
        return parent;
    }

    public void setParent(CollectionScope parent) {
        this.parent = parent;
    }

    public SqlCollectionOperator getOperator() {
        return operator;
    }

    public String getCollectionProp() {
        return collectionProp;
    }

    public String getCollectionPrefix() {
        return collectionPrefix;
    }

    public CollectionScope getChildScope(String propPath) {
        return children == null ? null : children.get(propPath);
    }

    public Map<String, CollectionScope> getChildren() {
        return children == null ? Collections.emptyMap() : children;
    }

    public boolean hasChild() {
        return children != null && !children.isEmpty();
    }

    public void addChildScope(CollectionScope scope) {
        if (children == null)
            children = new HashMap<>();
        // 使用完整的collectionPrefix作为key
        children.put(scope.getCollectionPrefix(), scope);
        scope.setParent(this);
    }

    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    public SqlColumnName getColNameNode() {
        return colNameNode;
    }

    public void setColNameNode(SqlColumnName colNameNode) {
        this.colNameNode = colNameNode;
    }

    public EqlASTNode getLogicUnitNode() {
        return logicUnitNode;
    }

    public void setLogicUnitNode(EqlASTNode logicUnitNode) {
        this.logicUnitNode = logicUnitNode;
    }

    public List<SqlColumnName> getMergedColNames() {
        return mergedColNames;
    }

    public void addMergedColName(SqlColumnName colName) {
        if (this.mergedColNames == null)
            this.mergedColNames = new ArrayList<>();
        this.mergedColNames.add(colName);
    }

    public void merge(CollectionScope other) {
        if (other == null)
            return;
        
        // 合并 mergedColNames
        if (other.mergedColNames != null) {
            if (this.mergedColNames == null)
                this.mergedColNames = new ArrayList<>();
            this.mergedColNames.addAll(other.mergedColNames);
        }
        
        // 合并 children
        if (other.children != null && !other.children.isEmpty()) {
            if (this.children == null)
                this.children = new HashMap<>();
            
            for (Map.Entry<String, CollectionScope> entry : other.children.entrySet()) {
                String key = entry.getKey();
                CollectionScope childScope = entry.getValue();
                
                // 如果当前scope已经存在相同key的子scope，则递归合并
                CollectionScope existingChild = this.children.get(key);
                if (existingChild != null) {
                    existingChild.merge(childScope);
                } else {
                    // 否则直接添加子scope，并设置父关系
                    this.children.put(key, childScope);
                    childScope.setParent(this);
                }
            }
        }
    }
}