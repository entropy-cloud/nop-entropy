package io.nop.jq.ast;

import java.util.List;

public final class FuncDefNode implements JqAstNode {
    private final String name;
    private final List<String> params;
    private final JqAstNode body;

    public FuncDefNode(String name, List<String> params, JqAstNode body) {
        this.name = name;
        this.params = params;
        this.body = body;
    }

    public String name() { return name; }
    public List<String> params() { return params; }
    public JqAstNode body() { return body; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitFuncDef(this); }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder("def ").append(name);
        if (!params.isEmpty()) {
            sb.append("(");
            for (int i = 0; i < params.size(); i++) {
                if (i > 0) sb.append("; ");
                sb.append(params.get(i));
            }
            sb.append(")");
        }
        sb.append(": ").append(body).append(";");
        return sb.toString();
    }
}
