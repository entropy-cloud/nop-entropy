package io.nop.jq.ast;

public final class BooleanOpNode implements JqAstNode {
    public enum Op { AND, OR, NOT }

    private final Op op;
    private final JqAstNode left;
    private final JqAstNode right;

    public BooleanOpNode(Op op, JqAstNode left, JqAstNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    public Op op() { return op; }
    public JqAstNode left() { return left; }
    public JqAstNode right() { return right; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitBooleanOp(this); }
    @Override public String toString() {
        return switch (op) {
            case NOT -> "not " + right;
            case AND -> "(" + left + " and " + right + ")";
            case OR -> "(" + left + " or " + right + ")";
        };
    }
}
