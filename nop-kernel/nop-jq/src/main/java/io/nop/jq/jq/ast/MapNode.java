package io.nop.jq.jq.ast;

public final class MapNode implements JqAstNode {
    private final JqAstNode function;
    private final JqAstNode object;
    private final boolean mapValues;

    public MapNode(JqAstNode function, JqAstNode object, boolean mapValues) {
        this.function = function;
        this.object = object;
        this.mapValues = mapValues;
    }

    public JqAstNode function() { return function; }
    public JqAstNode object() { return object; }
    public boolean isMapValues() { return mapValues; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitMap(this); }
    @Override public String toString() {
        String name = mapValues ? "map_values" : "map";
        return (object != null ? object + " | " : "") + name + "(" + function + ")";
    }
}
