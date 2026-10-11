package io.nop.jq.ast;

public final class FieldAccessNode implements JqAstNode {
    private final String fieldName;
    private final JqAstNode object;

    public FieldAccessNode(String fieldName, JqAstNode object) {
        this.fieldName = fieldName;
        this.object = object;
    }

    public String fieldName() { return fieldName; }
    public JqAstNode object() { return object; }
    public boolean hasExplicitObject() { return object != null; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitFieldAccess(this); }
    @Override public String toString() {
        return (object != null ? object + "." : ".") + fieldName;
    }
}
