package io.nop.jq.jq;

import io.nop.jq.jq.ast.JqAstNode;

import java.util.List;

/**
 * Legacy compiled jq query that translates to XLang.
 * Kept for backward compatibility but deprecated in favor of JqDirectQuery.
 */
public class JqCompiledQuery implements IJsonQuery {
    private final String expression;
    private final String xlangExpression;

    JqCompiledQuery(String expression, String xlangExpression) {
        this.expression = expression;
        this.xlangExpression = xlangExpression;
    }

    @Override
    public List<Object> apply(Object root) {
        throw new UnsupportedOperationException("not yet implemented: apply (requires XLang engine integration)");
    }

    @Override
    public Object applyOne(Object root) {
        throw new UnsupportedOperationException("not yet implemented: applyOne (requires XLang engine integration)");
    }

    @Override
    public String getExpression() {
        return expression;
    }

    @Override
    public JqAstNode getAst() {
        return null;
    }

    @Override
    public boolean isDirectExecution() {
        return false;
    }

    @Override
    public String toString() {
        return expression + " → " + xlangExpression;
    }
}
