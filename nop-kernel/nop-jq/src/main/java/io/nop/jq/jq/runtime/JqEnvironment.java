package io.nop.jq.jq.runtime;

import java.util.*;

/**
 * Runtime environment for jq execution. Manages variable scoping and function definitions.
 * Includes security limits to prevent resource exhaustion from untrusted jq expressions.
 */
public class JqEnvironment {
    public static final int MAX_RECURSION_DEPTH = 100;
    public static final int MAX_OUTPUT_COUNT = 10000;
    public static final int MAX_SPLIT_LIMIT = 100000;
    public static final long REGEX_TIMEOUT_NANOS = 100_000_000L; // 100ms

    private final Deque<Map<String, JqValue>> scopes = new ArrayDeque<>();
    private final Map<String, Object> functionDefs = new LinkedHashMap<>();
    private final Map<String, Object> labels = new LinkedHashMap<>();
    private int recursionDepth = 0;
    private int outputCount = 0;
    private String breakLabel = null;
    private JqValue breakValue = null;

    public JqEnvironment() {
        scopes.push(new HashMap<>());
    }

    public void checkRecursionDepth() {
        if (recursionDepth > MAX_RECURSION_DEPTH) {
            throw new JqRuntimeException("Recursion depth limit exceeded (max=" + MAX_RECURSION_DEPTH + ")");
        }
    }

    public void enterRecursion() {
        recursionDepth++;
        checkRecursionDepth();
    }

    public void exitRecursion() {
        recursionDepth--;
    }

    public void checkOutputLimit() {
        outputCount++;
        if (outputCount > MAX_OUTPUT_COUNT) {
            throw new JqRuntimeException("Output count limit exceeded (max=" + MAX_OUTPUT_COUNT + "). Expression produces too many results.");
        }
    }

    public boolean isBreak() {
        return breakLabel != null;
    }

    public String breakLabel() {
        return breakLabel;
    }

    public JqValue breakValue() {
        return breakValue;
    }

    public void setBreak(String label, JqValue value) {
        this.breakLabel = label;
        this.breakValue = value;
    }

    public void clearBreak() {
        this.breakLabel = null;
        this.breakValue = null;
    }

    public int getRecursionDepth() {
        return recursionDepth;
    }

    public void pushScope() {
        scopes.push(new HashMap<>());
    }

    public void popScope() {
        if (scopes.size() > 1) {
            scopes.pop();
        }
    }

    public void bind(String name, JqValue value) {
        scopes.peek().put(name, value);
    }

    public JqValue lookup(String name) {
        for (Map<String, JqValue> scope : scopes) {
            JqValue val = scope.get(name);
            if (val != null) return val;
        }
        throw new JqRuntimeException("Undefined variable: $" + name);
    }

    public boolean hasVariable(String name) {
        for (Map<String, JqValue> scope : scopes) {
            if (scope.containsKey(name)) return true;
        }
        return false;
    }

    public void defineFunction(String name, Object def) {
        functionDefs.put(name, def);
    }

    public Object getFunction(String name) {
        return functionDefs.get(name);
    }

    public boolean hasFunction(String name) {
        return functionDefs.containsKey(name);
    }

    public Set<String> getFunctionNames() {
        return functionDefs.keySet();
    }

    public void pushLabel(String name, Object position) {
        labels.put(name, position);
    }

    public void popLabel(String name) {
        labels.remove(name);
    }

    public JqEnvironment copy() {
        JqEnvironment env = new JqEnvironment();
        env.scopes.clear();
        for (Map<String, JqValue> scope : scopes) {
            env.scopes.push(new HashMap<>(scope));
        }
        env.functionDefs.putAll(this.functionDefs);
        env.labels.putAll(this.labels);
        env.recursionDepth = this.recursionDepth;
        env.outputCount = this.outputCount;
        return env;
    }
}
