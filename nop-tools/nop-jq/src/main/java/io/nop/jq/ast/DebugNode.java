package io.nop.jq.ast;

import java.util.List;

public final class DebugNode implements JqAstNode {
    private final JqAstNode expr;
    private final List<JqAstNode> args;

    public DebugNode(JqAstNode expr, List<JqAstNode> args) {
        this.expr = expr;
        this.args = args != null ? args : List.of();
    }

    public JqAstNode expr() { return expr; }
    public List<JqAstNode> args() { return args; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitDebug(this); }
    @Override public String toString() {
        if (args.isEmpty()) return "debug";
        StringBuilder sb = new StringBuilder("debug(");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(args.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
