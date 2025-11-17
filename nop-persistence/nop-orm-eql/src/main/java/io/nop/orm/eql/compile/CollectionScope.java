package io.nop.orm.eql.compile;

import io.nop.orm.eql.ast.EqlASTNode;
import io.nop.orm.eql.enums.SqlCollectionOperator;

import java.util.HashMap;
import java.util.Map;

public class CollectionScope {
    private final SqlCollectionOperator operator;
    private final String propPath;
    private EqlASTNode node;

    private Map<String, CollectionScope> children;

    public CollectionScope(SqlCollectionOperator operator, String propPath) {
        this.operator = operator;
        this.propPath = propPath;
    }

    public CollectionScope of(SqlCollectionOperator operator, String propPath) {
        return new CollectionScope(operator, propPath);
    }

    public SqlCollectionOperator getOperator() {
        return operator;
    }

    public String getPropPath() {
        return propPath;
    }

    public CollectionScope getChildScope(String propPath) {
        return children == null ? null : children.get(propPath);
    }

    public void addChildScope(CollectionScope scope) {
        if (children == null)
            children = new HashMap<>();
        children.put(scope.getPropPath(), scope);
    }
}