package io.nop.jq.jq.ast;

public final class ErrorNode implements JqAstNode {
    private final JqAstNode message;

    public ErrorNode(JqAstNode message) { this.message = message; }
    public JqAstNode message() { return message; }
    public boolean hasMessage() { return message != null; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitError(this); }
    @Override public String toString() {
        return message != null ? "error(" + message + ")" : "error";
    }
}
