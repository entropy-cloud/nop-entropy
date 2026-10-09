package io.nop.cli.commands;

import io.nop.rg.cli.NopRgMain;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code nop-cli rg} —— 将参数原样转交 {@link NopRgMain} 这一命令面。
 *
 * <p>本命令刻意不声明任何 {@code @Option}/{@code @Parameters}：nop-rg 已拥有完整的
 * ripgrep 兼容命令面（{@code -g/-i/-c/-l/--json/--threads/--regex/--vector/--jfr} …），
 * 在此处复述一份必然与上游漂移。改为 {@code unmatchedArgumentsAllowed} +
 * {@code @Unmatched} 收集 {@code rg} 之后的全部参数并交给 NopRgMain 自己的 picocli
 * 解析器，命令面因此只有一份定义；{@code --help} 也随之由 NopRgMain 渲染。
 *
 * <p>不再支持 {@code --delegate-rg}：该开关的用途是调起系统外部 ripgrep 做 A/B 对比与
 * benchmark（见 {@code ai-dev/backlog/nop-rg-roadmap.md}），属于测试设施而非产品能力。
 * 它已从 nop-rg 的命令面整体移除，对比能力改由测试代码直接调用
 * {@code SystemRgDelegate}（nop-rg-cli test scope）承担——留在生产命令面会让
 * {@code nop-rg} 出现两条语义迥异的执行路径，并让工具依赖宿主机预装 ripgrep。
 */
@CommandLine.Command(
        name = "rg",
        description = "Pure-JVM file search tool (ripgrep-compatible subset)."
)
public class CliRgCommand implements Callable<Integer> {

    @CommandLine.Unmatched
    List<String> args;

    @Override
    public Integer call() {
        return new CommandLine(new NopRgMain()).execute(toArgv());
    }

    String[] toArgv() {
        return args == null ? new String[0] : args.toArray(new String[0]);
    }
}