package io.nop.jq.jq.ast;

public final class RecursiveDescentNode implements JqAstNode {
    private final JqAstNode object;
    private final String fieldName;

    public RecursiveDescentNode(JqAstNode object, String fieldName) {
        this.object = object;
        this.fieldName = fieldName;
    }

    public JqAstNode object() { return object; }
    public String fieldName() { return fieldName; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitRecursiveDescent(this); }
    @Override public String toString() {
        String base = object != null ? object.toString() : "";
        return base + ".." + (fieldName != null ? fieldName : "");
    }
}
