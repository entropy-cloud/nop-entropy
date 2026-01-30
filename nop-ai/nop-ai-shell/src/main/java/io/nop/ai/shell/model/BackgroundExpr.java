package io.nop.ai.shell.model;

import java.util.Objects;

/**
 * 后台运行表达式
 */
public final class BackgroundExpr implements CommandExpression {

    private final CommandExpression inner;

    public BackgroundExpr(CommandExpression inner) {
        this.inner = Objects.requireNonNull(inner, "Inner expression cannot be null");
    }

    public static BackgroundExpr of(CommandExpression inner) {
        return new BackgroundExpr(inner);
    }

    public CommandExpression inner() {
        return inner;
    }

    @Override
    public String toString() {
        String innerStr = inner.toString();
        if (inner instanceof LogicalExpr) {
            return "(" + innerStr + ") &";
        }
        return innerStr + " &";
    }

    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
