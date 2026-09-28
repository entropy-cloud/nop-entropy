package io.nop.jq.ast;

/**
 * env / $ENV: the process environment as an object.
 */
public final class EnvNode implements JqAstNode {
    public static final EnvNode INSTANCE = new EnvNode();

    private EnvNode() {
    }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitEnv(this); }
    @Override public String toString() { return "env"; }
}
