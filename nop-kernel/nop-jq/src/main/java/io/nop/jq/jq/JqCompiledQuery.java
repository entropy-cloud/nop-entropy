package io.nop.jq.jq;

import java.util.Collections;
import java.util.List;

/**
 * Compiled jq query. Translates jq expression to XLang and caches the result.
 * Execution is delegated to the XLang expression engine.
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
    public String getXLangExpression() {
        return xlangExpression;
    }

    @Override
    public String toString() {
        return expression + " → " + xlangExpression;
    }
}
