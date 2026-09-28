package io.nop.jq.jq.ast;

public final class LabelNode implements JqAstNode {
    private final String name;
    private final JqAstNode body;

    public LabelNode(String name, JqAstNode body) {
        this.name = name;
        this.body = body;
    }

    public String name() { return name; }
    public JqAstNode body() { return body; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitLabel(this); }
    @Override public String toString() { return "label $" + name + " | " + body; }
}
