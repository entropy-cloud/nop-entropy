package io.nop.jq.jq.runtime;

import java.util.*;

/**
 * Runtime environment for jq execution. Manages variable scoping and function definitions.
 */
public class JqEnvironment {
    private final Deque<Map<String, JqValue>> scopes = new ArrayDeque<>();
    private final Map<String, Object> functionDefs = new LinkedHashMap<>();
    private final Map<String, Object> labels = new LinkedHashMap<>();

    public JqEnvironment() {
        scopes.push(new HashMap<>());
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
        return env;
    }
}
