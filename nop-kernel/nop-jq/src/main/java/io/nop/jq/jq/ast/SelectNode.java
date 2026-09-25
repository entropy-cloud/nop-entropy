package io.nop.jq.jq.ast;

public final class SelectNode implements JqAstNode {
    private final JqAstNode condition;
    private final JqAstNode object;

    public SelectNode(JqAstNode condition, JqAstNode object) {
        this.condition = condition;
        this.object = object;
    }

    public JqAstNode condition() { return condition; }
    public JqAstNode object() { return object; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitSelect(this); }
    @Override public String toString() {
        return (object != null ? object + " | " : "") + "select(" + condition + ")";
    }
}
