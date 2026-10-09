package io.nop.cli.commands;

import io.nop.lint.core.cli.NopLintCli;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code nop-cli lint} —— 将参数原样转交 {@link NopLintCli} 这一命令面。
 *
 * <p>与 {@link CliRgCommand} 同理：lint 的 {@code check|match|test} 子命令与全部选项由
 * {@code NopLintCli}/{@code CliOptions} 自己定义（且其参数解析是 fail-closed 的），本处
 * 不复述任何选项，只把 {@code lint} 之后的参数整体转发，返回其退出码
 * （0=OK / 1=violations / 2=internal error）。
 *
 * <p>语言绑定与规则库（nop-lint-java / nop-lint-nop）随 uber-jar 分发，
 * {@code JavaLanguage} 经 ServiceLoader 自动发现，无需在此注册。
 */
@CommandLine.Command(
        name = "lint",
        description = "Lint source files with AST-based pattern matching."
)
public class CliLintCommand implements Callable<Integer> {

    @CommandLine.Unmatched
    List<String> args;

    @Override
    public Integer call() {
        return NopLintCli.run(toArgv(), System.out, System.err);
    }

    String[] toArgv() {
        return args == null ? new String[0] : args.toArray(new String[0]);
    }
}