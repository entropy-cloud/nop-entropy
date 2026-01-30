package io.nop.ai.shell.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 大括号分组 - 在当前shell中执行命令组
 */
public final class GroupExpr implements CommandExpression {

    private final List<CommandExpression> commands;
    private final List<Redirect> redirects;

    public GroupExpr(List<CommandExpression> commands, List<Redirect> redirects) {
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("Group must have at least one command");
        }
        this.commands = List.copyOf(commands);
        this.redirects = List.copyOf(redirects);
    }

    public static GroupExpr of(CommandExpression... commands) {
        return new GroupExpr(List.of(commands), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<CommandExpression> commands() {
        return commands;
    }

    public List<Redirect> redirects() {
        return redirects;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");

        for (int i = 0; i < commands.size(); i++) {
            sb.append(commands.get(i));
            if (i < commands.size() - 1) {
                sb.append("; ");
            } else {
                sb.append(";");
            }
        }

        sb.append(" }");

        for (Redirect redirect : redirects) {
            sb.append(" ").append(redirect);
        }

        return sb.toString().trim();
    }

    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static class Builder {
        private final List<CommandExpression> commands = new ArrayList<>();
        private final List<Redirect> redirects = new ArrayList<>();

        public Builder command(CommandExpression cmd) {
            commands.add(cmd);
            return this;
        }

        public Builder commands(List<CommandExpression> cmds) {
            this.commands.addAll(cmds);
            return this;
        }

        public Builder redirect(Redirect redirect) {
            redirects.add(redirect);
            return this;
        }

        public Builder redirects(List<Redirect> redirects) {
            this.redirects.addAll(redirects);
            return this;
        }

        public GroupExpr build() {
            return new GroupExpr(commands, redirects);
        }
    }
}
