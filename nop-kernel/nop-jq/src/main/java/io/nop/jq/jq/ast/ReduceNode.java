package io.nop.jq.jq.ast;

public final class ReduceNode implements JqAstNode {
    private final JqAstNode expr;
    private final String varName;
    private final JqAstNode init;
    private final JqAstNode body;

    public ReduceNode(JqAstNode expr, String varName, JqAstNode init, JqAstNode body) {
        this.expr = expr;
        this.varName = varName;
        this.init = init;
        this.body = body;
    }

    public JqAstNode expr() { return expr; }
    public String varName() { return varName; }
    public JqAstNode init() { return init; }
    public JqAstNode body() { return body; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitReduce(this); }
    @Override public String toString() {
        return "reduce " + expr + " as $" + varName + " (" + init + "; " + body + ")";
    }
}
