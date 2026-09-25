package io.nop.jq.jq.ast;

public final class ForEachNode implements JqAstNode {
    private final JqAstNode expr;
    private final BindPattern pattern;
    private final JqAstNode init;
    private final JqAstNode update;
    private final JqAstNode extract;

    public ForEachNode(JqAstNode expr, BindPattern pattern, JqAstNode init, JqAstNode update, JqAstNode extract) {
        this.expr = expr;
        this.pattern = pattern;
        this.init = init;
        this.update = update;
        this.extract = extract;
    }

    public JqAstNode expr() { return expr; }
    public BindPattern pattern() { return pattern; }
    public JqAstNode init() { return init; }
    public JqAstNode update() { return update; }
    public JqAstNode extract() { return extract; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitForEach(this); }
    @Override public String toString() {
        return "foreach " + expr + " as " + pattern + " (" + init + "; " + update + "; " + extract + ")";
    }
}
