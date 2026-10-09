package io.nop.rg.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试侧的系统 rg 对比辅助：把 nop-rg 参数原样透传给系统 ripgrep 可执行文件。
 *
 * <p>该能力只服务于 A/B 对比与基准，属于测试设施而非产品能力，因此不进入
 * {@link NopRgMain} 的命令行面——把它做成生产 CLI 的一个开关会让 nop-rg
 * 出现两条语义迥异的执行路径（纯 JVM 与外部二进制），并让工具依赖宿主机预装
 * ripgrep。需要对比时由测试代码直接调用本辅助类（见 {@code NopRgMainTest}
 * 与 {@code RgComparisonTest}）。
 */
final class SystemRgDelegate {

    private SystemRgDelegate() {
    }

    /**
     * 构造系统 rg 命令行：{@code =path} 指定可执行文件路径，否则用 PATH 中的 {@code rg}；
     * 其余参数原样透传。
     */
    static List<String> buildRgArgs(String[] args) {
        List<String> rgArgs = new ArrayList<>();
        rgArgs.add("rg");
        rgArgs.addAll(List.of(args));
        return rgArgs;
    }

    static List<String> buildRgArgs(String executable, String[] args) {
        List<String> rgArgs = new ArrayList<>();
        rgArgs.add(executable);
        rgArgs.addAll(List.of(args));
        return rgArgs;
    }

    static int invokeRg(List<String> rgArgs) {
        try {
            Process process = new ProcessBuilder(rgArgs).inheritIO().start();
            return process.waitFor();
        } catch (IOException e) {
            System.err.println("nop-rg: failed to launch rg (" + rgArgs.get(0) + "): " + e);
            return 2;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 2;
        }
    }
}