package io.nop.jq.ast;

import java.util.List;

public final class ObjectConstructNode implements JqAstNode {
    public record Field(JqAstNode key, JqAstNode value) {}
    private final List<Field> fields;

    public ObjectConstructNode(List<Field> fields) { this.fields = fields; }
    public List<Field> fields() { return fields; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitObjectConstruct(this); }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(", ");
            Field f = fields.get(i);
            sb.append(f.key()).append(": ").append(f.value());
        }
        sb.append("}");
        return sb.toString();
    }
}
