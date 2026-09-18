package io.nop.jq.jq.ast;

public final class NumberLiteralNode implements JqAstNode {
    private final Number value;
    public NumberLiteralNode(Number value) { this.value = value; }
    public Number value() { return value; }
    public int intValue() { return value.intValue(); }
    public long longValue() { return value.longValue(); }
    public double doubleValue() { return value.doubleValue(); }
    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitNumber(this); }
    @Override public String toString() { return value.toString(); }
}
