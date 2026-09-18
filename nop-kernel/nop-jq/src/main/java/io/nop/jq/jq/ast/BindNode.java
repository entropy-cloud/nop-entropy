package io.nop.jq.jq.ast;

public final class BindNode implements JqAstNode {
    private final JqAstNode expr;
    private final String varName;
    private final JqAstNode body;

    public BindNode(JqAstNode expr, String varName, JqAstNode body) {
        this.expr = expr;
        this.varName = varName;
        this.body = body;
    }

    public JqAstNode expr() { return expr; }
    public String varName() { return varName; }
    public JqAstNode body() { return body; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitBind(this); }
    @Override public String toString() { return expr + " as $" + varName + " | " + body; }
}
