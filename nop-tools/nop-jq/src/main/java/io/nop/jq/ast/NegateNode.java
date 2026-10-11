package io.nop.jq.ast;

public final class NegateNode implements JqAstNode {
    private final JqAstNode operand;

    public NegateNode(JqAstNode operand) { this.operand = operand; }
    public JqAstNode operand() { return operand; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitNegate(this); }
    @Override public String toString() { return "-" + operand; }
}
