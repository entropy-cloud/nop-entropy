package io.nop.ai.shell.model;

import java.util.List;
import java.util.Map;

/**
 * 统一的命令行表达式工厂类
 * 支持静态导入，提供流畅的API
 */
public final class CommandFactory {

    private CommandFactory() {}

    public static SimpleCommand cmd(String command, String... args) {
        return SimpleCommand.builder(command)
                .args(args)
                .build();
    }

    public static SimpleCommand cmdWithEnvVars(String command, Map<String, String> envVars, String... args) {
        SimpleCommand.Builder builder = SimpleCommand.builder(command);
        envVars.forEach((key, value) -> builder.envVar(EnvVar.local(key, value)));
        return builder.args(args).build();
    }

    public static SimpleCommand cmdWithRedirects(String command, List<Redirect> redirects, String... args) {
        return SimpleCommand.builder(command)
                .args(args)
                .redirects(redirects)
                .build();
    }

    public static SimpleCommand cmdFull(String command, List<EnvVar> envVars, List<Redirect> redirects, String... args) {
        return SimpleCommand.builder(command)
                .envVars(envVars)
                .args(args)
                .redirects(redirects)
                .build();
    }

    public static EnvVar env(String name, String value) {
        return EnvVar.local(name, value);
    }

    public static EnvVar export(String name, String value) {
        return EnvVar.export(name, value);
    }

    public static EnvVar expand(String name, String value) {
        return EnvVar.expand(name, value);
    }

    public static LogicalExpr and(CommandExpression left, CommandExpression right) {
        return LogicalExpr.Operator.and(left, right);
    }

    public static LogicalExpr or(CommandExpression left, CommandExpression right) {
        return LogicalExpr.Operator.or(left, right);
    }

    public static LogicalExpr sequence(CommandExpression left, CommandExpression right) {
        return LogicalExpr.Operator.sequence(left, right);
    }

    public static PipelineExpr pipeline(CommandExpression... commands) {
        PipelineExpr.Builder builder = PipelineExpr.builder();
        for (CommandExpression cmd : commands) {
            builder.command(cmd);
        }
        return builder.build();
    }

    public static GroupExpr group(CommandExpression... commands) {
        return GroupExpr.of(commands);
    }

    public static GroupExpr group(List<CommandExpression> commands, List<Redirect> redirects) {
        return new GroupExpr(commands, redirects);
    }

    public static SubshellExpr subshell(CommandExpression inner) {
        return SubshellExpr.of(inner);
    }

    public static SubshellExpr subshell(CommandExpression inner, List<Redirect> redirects) {
        return new SubshellExpr(inner, redirects);
    }

    public static BackgroundExpr background(CommandExpression inner) {
        return BackgroundExpr.of(inner);
    }

    public static Redirect stdoutToFile(String file) {
        return new Redirect(null, Redirect.Type.OUTPUT, file);
    }

    public static Redirect stdoutAppend(String file) {
        return new Redirect(null, Redirect.Type.APPEND, file);
    }

    public static Redirect stdinFromFile(String file) {
        return new Redirect(null, Redirect.Type.INPUT, file);
    }

    public static Redirect stderrToFile(String file) {
        return new Redirect(2, Redirect.Type.OUTPUT, file);
    }

    public static Redirect stderrAppend(String file) {
        return new Redirect(2, Redirect.Type.APPEND, file);
    }

    public static Redirect stderrToStdout() {
        return new Redirect(2, Redirect.Type.FD_OUTPUT, "1");
    }

    public static Redirect mergeToFile(String file) {
        return new Redirect(null, Redirect.Type.MERGE, file);
    }

    public static Redirect mergeAppend(String file) {
        return new Redirect(null, Redirect.Type.MERGE_APPEND, file);
    }

    public static Redirect fdOutput(int sourceFd, int targetFd) {
        return new Redirect(sourceFd, Redirect.Type.FD_OUTPUT, String.valueOf(targetFd));
    }

    public static Redirect fdInput(int sourceFd, int targetFd) {
        return new Redirect(sourceFd, Redirect.Type.FD_INPUT, String.valueOf(targetFd));
    }

    public static Redirect hereDoc(String delimiter) {
        return new Redirect(null, Redirect.Type.HERE_DOC, delimiter);
    }

    public static Redirect hereString(String string) {
        return new Redirect(null, Redirect.Type.HERE_STRING, string);
    }
}
