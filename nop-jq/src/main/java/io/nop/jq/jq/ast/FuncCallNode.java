package io.nop.jq.jq.ast;

import java.util.List;

public final class FuncCallNode implements JqAstNode {
    private final String name;
    private final List<JqAstNode> args;

    public FuncCallNode(String name, List<JqAstNode> args) {
        this.name = name;
        this.args = args;
    }

    public String name() { return name; }
    public List<JqAstNode> args() { return args; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitFuncCall(this); }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder(name).append("(");
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(args.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
