package io.nop.ai.shell.model;

import java.util.List;
import java.util.Objects;

/**
 * 子shell表达式 - 在子shell中执行命令
 */
public final class SubshellExpr implements CommandExpression {

    private final CommandExpression inner;
    private final List<Redirect> redirects;

    public SubshellExpr(CommandExpression inner, List<Redirect> redirects) {
        this.inner = Objects.requireNonNull(inner, "Inner expression cannot be null");
        this.redirects = List.copyOf(redirects);
    }

    public static SubshellExpr of(CommandExpression inner) {
        return new SubshellExpr(inner, List.of());
    }

    public CommandExpression inner() {
        return inner;
    }

    public List<Redirect> redirects() {
        return redirects;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(inner).append(")");

        for (Redirect redirect : redirects) {
            sb.append(" ").append(redirect);
        }

        return sb.toString();
    }

    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
