package io.nop.core.lang.eval.visitor;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

public class ScopeVariableVisitor implements IExecutableExpressionVisitor {
    private final Set<IExecutableExpression> visited = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<String> scopeVariables = new HashSet<>();

    @Override
    public boolean onVisitExpr(IExecutableExpression expr) {
        return visited.add(expr);
    }

    @Override
    public void visitScopeVariable(SourceLocation loc, String name) {
        scopeVariables.add(name);
    }

    public Set<String> getScopeVariables() {
        return scopeVariables;
    }
}