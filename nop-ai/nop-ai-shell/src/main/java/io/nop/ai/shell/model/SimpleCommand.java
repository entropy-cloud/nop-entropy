package io.nop.ai.shell.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 殀单命令 - 原子执行单元
 * 存储原始字符串，不进行变量展开或通配符扩展
 */
public final class SimpleCommand implements CommandExpression {

    private final String command;
    private final List<String> args;
    private final List<EnvVar> envVars;
    private final List<Redirect> redirects;

    private SimpleCommand(String command, List<String> args, List<EnvVar> envVars, List<Redirect> redirects) {
        this.command = command;
        this.args = List.copyOf(args);
        this.envVars = List.copyOf(envVars);
        this.redirects = List.copyOf(redirects);
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArgs() {
        return args;
    }

    public List<EnvVar> getEnvVars() {
        return envVars;
    }

    public List<Redirect> getRedirects() {
        return redirects;
    }

    public String command() {
        return command;
    }

    public List<String> args() {
        return args;
    }

    public List<EnvVar> envVars() {
        return envVars;
    }

    public List<Redirect> redirects() {
        return redirects;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (EnvVar envVar : envVars) {
            if (envVar.type() == EnvVar.Type.EXPORT) {
                sb.append("export ");
            }
            sb.append(envVar.name()).append("=").append(envVar.value()).append(" ");
        }

        sb.append(command);

        for (String arg : args) {
            sb.append(" ").append(arg);
        }

        for (Redirect redirect : redirects) {
            sb.append(" ").append(redirect);
        }

        return sb.toString();
    }

    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static Builder builder(String command) {
        return new Builder(command);
    }

    public static class Builder {
        private final String command;
        private final List<String> args = new ArrayList<>();
        private final List< EnvVar> envVars = new ArrayList<>();
        private final List< Redirect> redirects = new ArrayList<>();

        private Builder(String command) {
            this.command = Objects.requireNonNull(command, "Command cannot be null");
        }

        public Builder arg(String arg) {
            args.add(arg);
            return this;
        }

        public Builder args(String... args) {
            Collections.addAll(this.args, args);
            return this;
        }

        public Builder envVar(String key, String value) {
            envVars.add(EnvVar.local(key, value));
            return this;
        }

        public Builder envVar(EnvVar envVar) {
            envVars.add(envVar);
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

        public Builder envVars(List<EnvVar> envVars) {
            this.envVars.addAll(envVars);
            return this;
        }

        public SimpleCommand build() {
            return new SimpleCommand(command, args, envVars, redirects);
        }
    }
}
