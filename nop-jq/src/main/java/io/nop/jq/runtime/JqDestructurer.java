package io.nop.jq.runtime;

import io.nop.jq.ast.BindPattern;
import io.nop.jq.ast.JqAstNode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates destructuring patterns: binds pattern variables to parts of a value.
 * Optional patterns ({@code ?}) swallow errors; {@code ?//} alternatives try
 * each pattern in order and use the first that succeeds.
 */
public final class JqDestructurer {

    @FunctionalInterface
    public interface ExprEvaluator {
        List<JqValue> evaluate(JqAstNode expr, JqValue input, JqEnvironment env);
    }

    private final ExprEvaluator evaluator;

    public JqDestructurer(ExprEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Destructure value against pattern, returning variable bindings.
     * Throws JqRuntimeException on mismatch (callers handle optional/?//).
     */
    public Map<String, JqValue> destructure(BindPattern pattern, JqValue value, JqEnvironment env) {
        Map<String, JqValue> bindings = new LinkedHashMap<>();
        destructureInto(pattern, value, env, bindings);
        return bindings;
    }

    /**
     * Top-level entry honouring optional and ?// alternatives.
     * Returns null when the pattern (optionally) does not match.
     */
    public Map<String, JqValue> destructureTop(BindPattern pattern, JqValue value, JqEnvironment env) {
        if (pattern instanceof BindPattern.Alt alt) {
            // jq binds the union of all alternatives' variables; variables from
            // alternatives that did not match are bound to null
            Set<String> allVars = new LinkedHashSet<>();
            for (BindPattern.Alt.Alternative alternative : alt.alternatives()) {
                collectVars(alternative.pattern(), allVars);
            }
            JqRuntimeException firstError = null;
            for (BindPattern.Alt.Alternative alternative : alt.alternatives()) {
                try {
                    Map<String, JqValue> matched = destructure(alternative.pattern(), value, env);
                    Map<String, JqValue> union = new LinkedHashMap<>();
                    for (String var : allVars) {
                        union.put(var, matched.getOrDefault(var, JqValue.NULL));
                    }
                    return union;
                } catch (JqRuntimeException e) {
                    if (firstError == null)
                        firstError = e;
                    // failed alternatives simply do not match
                }
            }
            if (firstError == null)
                return null;
            // jq re-raises the underlying mismatch when no alternative matched
            throw firstError;
        }
        try {
            return destructure(pattern, value, env);
        } catch (JqRuntimeException e) {
            if (pattern.optional())
                return null;
            throw e;
        }
    }

    /** Collect every variable name a pattern may bind. */
    private void collectVars(BindPattern pattern, Set<String> vars) {
        if (pattern instanceof BindPattern.Var var) {
            vars.add(var.name());
        } else if (pattern instanceof BindPattern.Array array) {
            for (BindPattern.Array.Element element : array.elements()) {
                collectVars(element.pattern(), vars);
            }
        } else if (pattern instanceof BindPattern.Object object) {
            for (BindPattern.Object.Entry entry : object.entries()) {
                if (entry.variable() != null)
                    vars.add(entry.variable());
                if (entry.value() != null)
                    collectVars(entry.value(), vars);
            }
        } else if (pattern instanceof BindPattern.Alt alt) {
            for (BindPattern.Alt.Alternative alternative : alt.alternatives()) {
                collectVars(alternative.pattern(), vars);
            }
        }
    }

    private void destructureInto(BindPattern pattern, JqValue value, JqEnvironment env,
                                 Map<String, JqValue> bindings) {
        if (pattern instanceof BindPattern.Var var) {
            bindings.put(var.name(), value);
            return;
        }
        if (pattern instanceof BindPattern.Array array) {
            if (!(value instanceof JqArray arr)) {
                throw new JqRuntimeException(
                        "Cannot index " + value.typeName() + " with number");
            }
            for (int i = 0; i < array.elements().size(); i++) {
                BindPattern.Array.Element element = array.elements().get(i);
                JqValue item = i < arr.size() ? arr.get(i) : JqValue.NULL;
                try {
                    destructureInto(element.pattern(), item, env, bindings);
                } catch (JqRuntimeException e) {
                    if (!element.optional())
                        throw e;
                }
            }
            return;
        }
        if (pattern instanceof BindPattern.Object object) {
            if (!(value instanceof JqObject obj)) {
                String firstKey = object.entries().isEmpty() ? ""
                        : java.util.Objects.requireNonNullElse(
                                object.entries().get(0).key(),
                                object.entries().get(0).variable() != null
                                        ? object.entries().get(0).variable().substring(1) : "");
                throw new JqRuntimeException("Cannot index " + value.typeName()
                        + " with string \"" + firstKey + "\"");
            }
            for (BindPattern.Object.Entry entry : object.entries()) {
                try {
                    destructureEntry(entry, obj, env, bindings);
                } catch (JqRuntimeException e) {
                    if (!entry.optional())
                        throw e;
                }
            }
            return;
        }
        throw new JqRuntimeException("Unsupported destructuring pattern: " + pattern);
    }

    private void destructureEntry(BindPattern.Object.Entry entry, JqObject obj,
                                  JqEnvironment env, Map<String, JqValue> bindings) {
        String key;
        if (entry.keyExpr() != null) {
            List<JqValue> keyVals = evaluator.evaluate(entry.keyExpr(), obj, env);
            if (keyVals.isEmpty())
                return;
            key = JqPrinter.tostring(keyVals.get(0));
        } else {
            key = entry.key();
        }
        JqValue fieldValue = obj.get(key);
        if (fieldValue == null)
            fieldValue = JqValue.NULL;
        if (entry.variable() != null) {
            // {$foo} shorthand and {$b: pattern}: $b always binds the field value
            bindings.put(entry.variable(), fieldValue);
        }
        if (entry.value() != null) {
            destructureInto(entry.value(), fieldValue, env, bindings);
        }
    }
}
