package io.nop.jq.ast;

public final class ComparisonNode implements JqAstNode {
    public enum Op { EQ, NE, GT, GE, LT, LE }

    private final Op op;
    private final JqAstNode left;
    private final JqAstNode right;

    public ComparisonNode(Op op, JqAstNode left, JqAstNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    public Op op() { return op; }
    public JqAstNode left() { return left; }
    public JqAstNode right() { return right; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitComparison(this); }
    @Override public String toString() {
        String opStr = switch (op) {
            case EQ -> " == ";
            case NE -> " != ";
            case GT -> " > ";
            case GE -> " >= ";
            case LT -> " < ";
            case LE -> " <= ";
        };
        return "(" + left + opStr + right + ")";
    }
}
