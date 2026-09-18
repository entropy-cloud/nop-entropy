package io.nop.jq.jq.ast;

public final class MathOpNode implements JqAstNode {
    public enum Op { ADD, SUB, MUL, DIV, MOD }

    private final Op op;
    private final JqAstNode left;
    private final JqAstNode right;

    public MathOpNode(Op op, JqAstNode left, JqAstNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    public Op op() { return op; }
    public JqAstNode left() { return left; }
    public JqAstNode right() { return right; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitMathOp(this); }
    @Override public String toString() {
        String opStr = switch (op) {
            case ADD -> " + ";
            case SUB -> " - ";
            case MUL -> " * ";
            case DIV -> " / ";
            case MOD -> " % ";
        };
        return "(" + left + opStr + right + ")";
    }
}
