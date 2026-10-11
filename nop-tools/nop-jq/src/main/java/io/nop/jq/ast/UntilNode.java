package io.nop.jq.ast;

/**
 * until(cond; update): applies update until cond holds, emitting only the
 * final value. Starts from the input itself.
 */
public final class UntilNode implements JqAstNode {
    private final JqAstNode condition;
    private final JqAstNode update;

    public UntilNode(JqAstNode condition, JqAstNode update) {
        this.condition = condition;
        this.update = update;
    }

    public JqAstNode condition() { return condition; }
    public JqAstNode update() { return update; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitUntil(this); }
    @Override public String toString() { return "until(" + condition + "; " + update + ")"; }
}
