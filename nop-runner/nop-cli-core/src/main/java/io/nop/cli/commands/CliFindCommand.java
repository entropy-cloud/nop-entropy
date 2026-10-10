package io.nop.cli.commands;

import io.nop.find.cli.NopFindMain;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code nop-cli find} —— 将参数原样转交 {@link NopFindMain} 这一命令面。
 *
 * <p>本命令刻意不声明任何 {@code @Option}/{@code @Parameters}：nop-find 已拥有完整的模糊查找
 * 命令面（{@code QUERY/PATH/-n/-s/-g/--json/--no-ignore/--threads} …），在此处复述一份必然与
 * 上游漂移。改为 {@code unmatchedArgumentsAllowed} + {@code @Unmatched} 收集 {@code find} 之后的
 * 全部参数并交给 NopFindMain 自己的 picocli 解析器，命令面因此只有一份定义；
 * {@code --help} 也随之由 NopFindMain 渲染。与 {@link CliRgCommand} 同一模式。
 */
@CommandLine.Command(
        name = "find",
        description = "Pure-JVM fuzzy file finder (query -> top-K ranked paths)."
)
public class CliFindCommand implements Callable<Integer> {

    @CommandLine.Unmatched
    List<String> args;

    @Override
    public Integer call() {
        return new CommandLine(new NopFindMain()).execute(toArgv());
    }

    String[] toArgv() {
        return args == null ? new String[0] : args.toArray(new String[0]);
    }
}