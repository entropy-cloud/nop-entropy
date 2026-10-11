package io.nop.jq.runtime;

import io.nop.jq.ast.FuncDefNode;

/**
 * A user function definition paired with the environment where it was defined,
 * giving jq's lexical scoping: the body resolves helpers from the definition
 * site, while filter/value parameters come from the call site.
 */
public final class JqFunctionDef {
    private final FuncDefNode def;
    private final JqEnvironment definitionEnv;

    public JqFunctionDef(FuncDefNode def, JqEnvironment definitionEnv) {
        this.def = def;
        this.definitionEnv = definitionEnv;
    }

    public FuncDefNode def() {
        return def;
    }

    public JqEnvironment definitionEnv() {
        return definitionEnv;
    }
}
