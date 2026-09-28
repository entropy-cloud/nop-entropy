package io.nop.jq.jq.ast;

/**
 * Assignment / update operators: =, |=, +=, -=, *=, /=, %=, //=.
 * The left operand is a path expression; the right operand supplies values.
 */
public final class UpdateAssignNode implements JqAstNode {
    public enum Op {
        ASSIGN("="), UPDATE("|="), ADD("+="), SUB("-="),
        MUL("*="), DIV("/="), MOD("%="), ALTERNATIVE("//=");

        private final String text;

        Op(String text) { this.text = text; }

        public String text() { return text; }
    }

    private final Op op;
    private final JqAstNode path;
    private final JqAstNode value;

    public UpdateAssignNode(Op op, JqAstNode path, JqAstNode value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public Op op() { return op; }
    public JqAstNode path() { return path; }
    public JqAstNode value() { return value; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitUpdateAssign(this); }
    @Override public String toString() { return "(" + path + " " + op.text() + " " + value + ")"; }
}
