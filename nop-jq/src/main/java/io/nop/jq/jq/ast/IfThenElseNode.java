package io.nop.jq.jq.ast;

import java.util.List;

public final class IfThenElseNode implements JqAstNode {
    private final JqAstNode condition;
    private final JqAstNode thenBranch;
    private final JqAstNode elseBranch;
    private final List<ElifClause> elifClauses;

    public record ElifClause(JqAstNode condition, JqAstNode branch) {}

    public IfThenElseNode(JqAstNode condition, JqAstNode thenBranch,
                          List<ElifClause> elifClauses, JqAstNode elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elifClauses = elifClauses;
        this.elseBranch = elseBranch;
    }

    public JqAstNode condition() { return condition; }
    public JqAstNode thenBranch() { return thenBranch; }
    public JqAstNode elseBranch() { return elseBranch; }
    public List<ElifClause> elifClauses() { return elifClauses; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitIfThenElse(this); }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder("if ");
        sb.append(condition).append(" then ").append(thenBranch);
        for (var elif : elifClauses) {
            sb.append(" elif ").append(elif.condition()).append(" then ").append(elif.branch());
        }
        if (elseBranch != null) sb.append(" else ").append(elseBranch);
        sb.append(" end");
        return sb.toString();
    }
}
