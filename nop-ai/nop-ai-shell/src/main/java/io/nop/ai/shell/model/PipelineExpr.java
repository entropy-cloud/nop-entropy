package io.nop.ai.shell.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管道表达式 - 连接多个命令
 */
public final class PipelineExpr implements CommandExpression {

    private final List<CommandExpression> commands;

    private PipelineExpr(List<CommandExpression> commands) {
        if (commands.size() < 2) {
            throw new IllegalArgumentException("Pipeline must have at least 2 commands");
        }
        this.commands = List.copyOf(commands);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<CommandExpression> commands() {
        return commands;
    }

    @Override
    public String toString() {
        return commands.stream()
                .map(CommandExpression::toString)
                .collect(Collectors.joining(" | "));
    }

    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static class Builder {
        private final List<CommandExpression> commands = new ArrayList<>();

        public Builder command(CommandExpression cmd) {
            commands.add(cmd);
            return this;
        }

        public Builder commands(List<CommandExpression> cmds) {
            this.commands.addAll(cmds);
            return this;
        }

        public PipelineExpr build() {
            return new PipelineExpr(commands);
        }
    }
}
