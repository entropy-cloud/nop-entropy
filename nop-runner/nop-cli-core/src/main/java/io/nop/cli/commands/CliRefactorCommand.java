package io.nop.cli.commands;

import io.nop.refactor.core.cli.NopRefactorCli;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code nop-cli refactor} —— 将参数原样转交 {@link NopRefactorCli} 这一命令面。
 *
 * <p>命令面为 {@code preview|apply --rules <vfs-prefix> [--json] <file|dir>...}，
 * 由 {@code RefactorOptions.parse} fail-closed 解析（缺 {@code --rules} 即抛错并附 usage）。
 * 本处不复述选项，只整体转发并透传退出码（0=OK / 1=non-applied / 2=internal error），
 * 避免命令面在此处与上游产生第二份定义。
 */
@CommandLine.Command(
        name = "refactor",
        description = "Batch refactoring with AST-based pattern matching."
)
public class CliRefactorCommand implements Callable<Integer> {

    @CommandLine.Unmatched
    List<String> args;

    @Override
    public Integer call() {
        return NopRefactorCli.run(toArgv(), System.out, System.err);
    }

    String[] toArgv() {
        return args == null ? new String[0] : args.toArray(new String[0]);
    }
}