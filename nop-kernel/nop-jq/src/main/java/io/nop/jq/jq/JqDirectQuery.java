package io.nop.jq.jq;

import io.nop.jq.jq.ast.JqAstNode;
import io.nop.jq.jq.runtime.JqEnvironment;
import io.nop.jq.jq.runtime.JqExecutor;
import io.nop.jq.jq.runtime.JqValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiled jq query that executes AST directly.
 */
public class JqDirectQuery implements IJsonQuery {
    private final String expression;
    private final JqAstNode ast;
    private final JqExecutor executor;

    public JqDirectQuery(String expression, JqAstNode ast) {
        this.expression = expression;
        this.ast = ast;
        this.executor = new JqExecutor();
    }

    @Override
    public List<Object> apply(Object root) {
        JqValue input = JqValue.of(root);
        JqEnvironment env = new JqEnvironment();
        List<JqValue> results = executor.execute(ast, input, env);
        List<Object> output = new ArrayList<>(results.size());
        for (JqValue v : results) {
            output.add(v.toJava());
        }
        return output;
    }

    @Override
    public Object applyOne(Object root) {
        List<Object> results = apply(root);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public String getExpression() {
        return expression;
    }

    @Override
    public JqAstNode getAst() {
        return ast;
    }

    @Override
    public boolean isDirectExecution() {
        return true;
    }

    @Override
    public String toString() {
        return "jq: " + expression;
    }
}
