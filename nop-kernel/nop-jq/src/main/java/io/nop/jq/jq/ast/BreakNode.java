package io.nop.jq.jq.ast;

public final class BreakNode implements JqAstNode {
    private final String labelName;
    public BreakNode(String labelName) { this.labelName = labelName; }
    public String labelName() { return labelName; }
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitBreak(this); }
    @Override public String toString() { return "break $" + labelName; }
}
