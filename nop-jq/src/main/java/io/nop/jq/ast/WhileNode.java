package io.nop.jq.ast;

/**
 * while(cond; update): repeatedly applies update while cond holds, emitting
 * each intermediate value. Starts from the input itself.
 */
public final class WhileNode implements JqAstNode {
    private final JqAstNode condition;
    private final JqAstNode update;

    public WhileNode(JqAstNode condition, JqAstNode update) {
        this.condition = condition;
        this.update = update;
    }

    public JqAstNode condition() { return condition; }
    public JqAstNode update() { return update; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitWhile(this); }
    @Override public String toString() { return "while(" + condition + "; " + update + ")"; }
}
