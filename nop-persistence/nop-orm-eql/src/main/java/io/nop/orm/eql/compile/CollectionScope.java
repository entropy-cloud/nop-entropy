package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.SqlExpr;
import io.nop.orm.eql.enums.SqlCollectionOperator;

import java.util.HashMap;
import java.util.Map;

public class CollectionScope {
    private final SqlCollectionOperator operator;
    private final String collectionPrefix;
    private SqlExpr node;

    private CollectionScope parent;

    private Map<String, CollectionScope> children;

    public CollectionScope(SqlCollectionOperator operator, String collectionPrefix) {
        this.operator = operator;
        this.collectionPrefix = collectionPrefix;
    }

    public CollectionScope of(SqlCollectionOperator operator, String propPath) {
        return new CollectionScope(operator, propPath);
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

    public String getCollectionPrefix() {
        return collectionPrefix;
    }

    public CollectionScope getChildScope(String propPath) {
        return children == null ? null : children.get(propPath);
    }

    public void addChildScope(CollectionScope scope) {
        if (children == null)
            children = new HashMap<>();
        children.put(scope.getCollectionPrefix(), scope);
    }


}