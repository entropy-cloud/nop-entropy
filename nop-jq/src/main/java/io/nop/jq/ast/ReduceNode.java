package io.nop.jq.ast;

public final class ReduceNode implements JqAstNode {
    private final JqAstNode expr;
    private final BindPattern pattern;
    private final JqAstNode init;
    private final JqAstNode body;

    public ReduceNode(JqAstNode expr, BindPattern pattern, JqAstNode init, JqAstNode body) {
        this.expr = expr;
        this.pattern = pattern;
        this.init = init;
        this.body = body;
    }

    public JqAstNode expr() { return expr; }
    public BindPattern pattern() { return pattern; }
    public JqAstNode init() { return init; }
    public JqAstNode body() { return body; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitReduce(this); }
    @Override public String toString() {
        return "reduce " + expr + " as " + pattern + " (" + init + "; " + body + ")";
    }
}
