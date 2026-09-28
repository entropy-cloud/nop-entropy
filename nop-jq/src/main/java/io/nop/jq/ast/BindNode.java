package io.nop.jq.ast;

public final class BindNode implements JqAstNode {
    private final JqAstNode expr;
    private final BindPattern pattern;
    private final JqAstNode body;

    public BindNode(JqAstNode expr, BindPattern pattern, JqAstNode body) {
        this.expr = expr;
        this.pattern = pattern;
        this.body = body;
    }

    public JqAstNode expr() { return expr; }
    public BindPattern pattern() { return pattern; }
    public JqAstNode body() { return body; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitBind(this); }
    @Override public String toString() { return expr + " as " + pattern + " | " + body; }
}
