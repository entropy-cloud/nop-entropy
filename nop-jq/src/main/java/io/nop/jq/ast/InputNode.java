package io.nop.jq.ast;

/**
 * input (read the next input document) or inputs (read all remaining documents).
 */
public final class InputNode implements JqAstNode {
    private final boolean all;

    public InputNode(boolean all) {
        this.all = all;
    }

    /** True for `inputs` (all remaining), false for `input` (next one). */
    public boolean all() { return all; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitInput(this); }
    @Override public String toString() { return all ? "inputs" : "input"; }
}
