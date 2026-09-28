package io.nop.jq.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime environment for jq execution: variable scopes and function
 * definitions, plus resource limits that protect against runaway programs.
 */
public class JqEnvironment {
    /** Maximum user-function call depth. */
    public static final int MAX_CALL_DEPTH = 2000;
    /** Safety net for programs that would produce unbounded outputs. */
    public static final int MAX_OUTPUT_COUNT = 1_000_000;
    public static final int MAX_SPLIT_LIMIT = 100_000;

    private final Deque<Map<String, JqValue>> scopes = new ArrayDeque<>();
    private Map<String, Object> functionDefs = new LinkedHashMap<>();
    /** True while functionDefs is shared with a forked parent and must be copied before writes. */
    private boolean defsShared = false;
    private int callDepth = 0;
    private int outputCount = 0;
    /** Extra input documents available to input/inputs (empty by default). */
    private Deque<JqValue> pendingInputs = new ArrayDeque<>();

    public JqEnvironment() {
        scopes.push(new HashMap<>());
    }

    private JqEnvironment(Deque<Map<String, JqValue>> scopes, Map<String, Object> functionDefs,
                          boolean defsShared, int callDepth, int outputCount,
                          Deque<JqValue> pendingInputs) {
        this.scopes.addAll(scopes);
        this.functionDefs = functionDefs;
        this.defsShared = defsShared;
        this.callDepth = callDepth;
        this.outputCount = outputCount;
        this.pendingInputs = pendingInputs;
    }

    // ---- variable scopes ----

    public void pushScope() {
        scopes.push(new HashMap<>());
    }

    public void popScope() {
        scopes.pop();
    }

    public void bind(String name, JqValue value) {
        scopes.peek().put(name, value);
    }


    public JqValue lookup(String name) {
        for (Map<String, JqValue> scope : scopes) {
            JqValue value = scope.get(name);
            if (value != null)
                return value;
        }
        throw new JqRuntimeException("$" + name + " is not defined");
    }

    public boolean hasVariable(String name) {
        for (Map<String, JqValue> scope : scopes) {
            if (scope.containsKey(name))
                return true;
        }
        return false;
    }

    // ---- function definitions (closures and user functions) ----

    public void defineFunction(String name, Object def) {
        if (defsShared) {
            functionDefs = new LinkedHashMap<>(functionDefs);
            defsShared = false;
        }
        functionDefs.put(name, def);
    }

    public Object getFunction(String name) {
        return functionDefs.get(name);
    }

    public boolean hasFunction(String name) {
        return functionDefs.containsKey(name);
    }

    // ---- call depth ----

    public void enterCall() {
        if (++callDepth > MAX_CALL_DEPTH) {
            throw new JqRuntimeException("Call depth limit exceeded (max=" + MAX_CALL_DEPTH + ")");
        }
    }

    public void exitCall() {
        callDepth--;
    }

    // ---- output budget ----

    public void checkOutputLimit() {
        if (++outputCount > MAX_OUTPUT_COUNT) {
            throw new JqRuntimeException(
                    "Output count limit exceeded (max=" + MAX_OUTPUT_COUNT + ")");
        }
    }

    // ---- input stream for input/inputs ----

    public boolean hasMoreInputs() {
        return !pendingInputs.isEmpty();
    }

    public JqValue nextInput() {
        if (pendingInputs.isEmpty()) {
            throw new JqRuntimeException("break", JqString.of("break"));
        }
        return pendingInputs.pop();
    }

    public void addInput(JqValue input) {
        pendingInputs.add(input);
    }

    // ---- fork for user function calls ----

    /**
     * Create an environment for a function body. Scope maps and function
     * definitions are shared copy-on-write: the fork cannot leak bindings into
     * the caller because every binding path pushes a fresh scope first, and
     * defineFunction clones the shared definition map before writing.
     */
    public JqEnvironment fork() {
        JqEnvironment env = new JqEnvironment(scopes, functionDefs, true, callDepth,
                outputCount, pendingInputs);
        return env;
    }
}
