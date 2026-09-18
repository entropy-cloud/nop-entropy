package io.nop.jq.jq.ast;

public final class LimitNode implements JqAstNode {
    private final JqAstNode count;
    private final JqAstNode expr;

    public LimitNode(JqAstNode count, JqAstNode expr) {
        this.count = count;
        this.expr = expr;
    }

    public JqAstNode count() { return count; }
    public JqAstNode expr() { return expr; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitLimit(this); }
    @Override public String toString() { return "limit(" + count + "; " + expr + ")"; }
}
