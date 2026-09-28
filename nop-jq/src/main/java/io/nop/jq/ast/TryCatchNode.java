package io.nop.jq.ast;

public final class TryCatchNode implements JqAstNode {
    private final JqAstNode tryExpr;
    private final JqAstNode catchExpr;

    public TryCatchNode(JqAstNode tryExpr, JqAstNode catchExpr) {
        this.tryExpr = tryExpr;
        this.catchExpr = catchExpr;
    }

    public JqAstNode tryExpr() { return tryExpr; }
    public JqAstNode catchExpr() { return catchExpr; }
    public boolean hasCatch() { return catchExpr != null; }

    @Override public <T> T accept(JqAstVisitor<T> v) { return v.visitTryCatch(this); }
    @Override public String toString() {
        return "try " + tryExpr + (catchExpr != null ? " catch " + catchExpr : "");
    }
}
