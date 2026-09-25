package io.nop.jq.jq.ast;

public final class PipeNode implements JqAstNode {
    private final JqAstNode left;
    private final JqAstNode right;

    public PipeNode(JqAstNode left, JqAstNode right) {
        this.left = left;
        this.right = right;
    }

    public JqAstNode left() { return left; }
    public JqAstNode right() { return right; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitPipe(this); }
    @Override public String toString() { return left + " | " + right; }
}
