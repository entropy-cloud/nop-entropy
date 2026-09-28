package io.nop.jq.ast;

/**
 * jq alternative operator: a // b produces all outputs of a that are not
 * false/null; if a produces no truthy outputs, the outputs of b are produced.
 */
public final class AlternativeNode implements JqAstNode {
    private final JqAstNode left;
    private final JqAstNode right;

    public AlternativeNode(JqAstNode left, JqAstNode right) {
        this.left = left;
        this.right = right;
    }

    public JqAstNode left() { return left; }
    public JqAstNode right() { return right; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitAlternative(this); }
    @Override public String toString() { return "(" + left + " // " + right + ")"; }
}
