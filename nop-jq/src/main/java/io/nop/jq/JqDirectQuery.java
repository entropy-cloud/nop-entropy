package io.nop.jq;

import io.nop.jq.ast.JqAstNode;
import io.nop.jq.runtime.JqEnvironment;
import io.nop.jq.runtime.JqExecutor;
import io.nop.jq.runtime.JqValue;

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

    /**
     * Like {@link #apply(Object)}, but a runtime error only ends the stream
     * (jq keeps the outputs produced before the error). Used by the official
     * test harness, whose runner semantics tolerate a trailing error.
     *
     * @param errored set to true when the stream ended with an error
     */
    public List<Object> applyPartial(Object root, java.util.concurrent.atomic.AtomicBoolean errored) {
        JqValue input = JqValue.of(root);
        JqEnvironment env = new JqEnvironment();
        List<JqValue> results = new ArrayList<>();
        try {
            executor.executeInto(ast, input, env, results);
        } catch (OutOfMemoryError | StackOverflowError e) {
            throw e;
        } catch (RuntimeException e) {
            errored.set(true);
        }
        List<Object> output = new ArrayList<>(results.size());
        for (JqValue v : results) {
            output.add(v.toJava());
        }
        return output;
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
