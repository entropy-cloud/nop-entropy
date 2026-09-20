package io.nop.rg.cli;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.core.NopRgException;
import io.nop.rg.core.coordinator.FileMatches;
import io.nop.rg.core.coordinator.LineMatch;
import io.nop.rg.core.coordinator.SearchCommand;
import io.nop.rg.core.coordinator.SearchCoordinator;
import picocli.CommandLine;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * nop-rg 命令行入口（plan 2264 CLI-01..03）。
 *
 * <p>用法：{@code nop-rg PATTERN [PATH] [-g glob]... [-i] [-c] [-l] [--json] [--no-ignore] [--threads N]
 * [--delegate-rg[=path]]}
 *
 * <p>退出码对齐 rg：命中 0 / 未命中 1 / 错误 2。
 */
@CommandLine.Command(
        name = "nop-rg",
        mixinStandardHelpOptions = true,
        version = "nop-rg 0.1.0",
        description = "Pure-JVM file search tool (ripgrep-compatible subset).")
public class NopRgMain implements Callable<Integer> {

    private static final int OUTPUT_BUFFER_BYTES = 64 * 1024;

    @CommandLine.Parameters(index = "0", description = "Search pattern (literal or regex with --regex)")
    private String pattern;

    @CommandLine.Parameters(index = "1", defaultValue = ".", description = "Root directory to search")
    private String path;

    @CommandLine.Option(names = {"-g", "--glob"}, description = "Include/exclude glob (repeatable, '!' prefix excludes)")
    private List<String> globs = new ArrayList<>();

    @CommandLine.Option(names = {"-i", "--ignore-case"}, description = "Case-insensitive (ASCII folding)")
    private boolean ignoreCase;

    @CommandLine.Option(names = {"-c", "--count"}, description = "Print matching line count per file")
    private boolean count;

    @CommandLine.Option(names = {"-l", "--files-with-matches"}, description = "Print matching file paths only")
    private boolean filesWithMatches;

    @CommandLine.Option(names = "--json", description = "Output rg --json compatible messages")
    private boolean json;

    @CommandLine.Option(names = "--no-ignore", description = "Disable .gitignore filtering (hidden files stay hidden)")
    private boolean noIgnore;

    @CommandLine.Option(names = "--threads", defaultValue = "0", description = "Worker thread count (0 = CPU cores)")
    private int threads;

    @CommandLine.Option(names = {"-r", "--regex"}, description = "Treat pattern as regular expression")
    private boolean regex;

    @CommandLine.Option(names = "--vector", description = "Use Vector API (SIMD) search when available; requires nop-rg-vector"
            + " on classpath and --add-modules jdk.incubator.vector (falls back to scalar silently if module present"
            + " but incubator module missing; errors out if module absent)")
    private boolean vector;

    // 说明：--delegate-rg 由 main() 在 picocli 解析前拦截处理，本字段仅用于 help 展示，运行时不可达
    @CommandLine.Option(names = "--delegate-rg", arity = "0..1", fallbackValue = "rg",
            description = "Delegate execution to system rg (optionally specify executable path via =path)")
    private String delegateRg;

    @CommandLine.Option(names = "--jfr", arity = "1",
            description = "Record JFR events (CPU/alloc/lock) to the given .jfr file during the search")
    private String jfrOutput;

    public static void main(String[] args) {
        // --delegate-rg 在 picocli 解析前拦截：原样透传全部参数给系统 rg
        List<String> rgArgs = buildRgArgs(args);
        if (rgArgs != null) {
            System.exit(invokeRg(rgArgs));
            return;
        }
        int code = new CommandLine(new NopRgMain()).execute(args);
        System.exit(code);
    }

    /**
     * 预解析 --delegate-rg：委托时返回 rg 命令行（rg 可执行路径 + 其余参数原样透传），否则返回 null。
     * 包可见供测试直接验证桥接语义。
     */
    static List<String> buildRgArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--delegate-rg") || arg.startsWith("--delegate-rg=")) {
                String rgPath = arg.startsWith("--delegate-rg=")
                        ? arg.substring("--delegate-rg=".length())
                        : "rg";
                List<String> rgArgs = new ArrayList<>();
                rgArgs.add(rgPath);
                for (int k = 0; k < args.length; k++) {
                    if (k == i) continue;
                    rgArgs.add(args[k]);
                }
                return rgArgs;
            }
        }
        return null;
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

    @Override
    public Integer call() {
        try {
            Path root = Path.of(path).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                System.err.println("nop-rg: not a directory: " + path);
                return 2;
            }

            if (!noIgnore) {
                CoreInitialization.initialize();
            }
            try {
                // 录制启动失败（--jfr 路径非法等）同样经外层 finally 销毁初始化（plan 2273 A2 对称清理）
                AutoCloseable recording = jfrOutput == null ? null : JfrSupport.startRecording(Path.of(jfrOutput));
                try {
                    Map<String, FileMatches> results = search(root);
                    // plan 2273 R1：64KB 缓冲 + 收尾一次性 flush，替代 autoflush 逐行 flush（字节序列不变）
                    PrintWriter out = new PrintWriter(
                            new BufferedOutputStream(System.out, OUTPUT_BUFFER_BYTES), false);
                    ResultPrinter.print(out, results, ResultPrinter.resolveMode(json, filesWithMatches, count));
                    out.flush();
                    return results.isEmpty() ? 1 : 0;
                } finally {
                    if (recording != null) {
                        try {
                            recording.close();
                        } catch (Exception e) {
                            System.err.println("nop-rg: warning: JFR dump failed: " + e);
                        }
                    }
                }
            } finally {
                if (!noIgnore) {
                    CoreInitialization.destroy();
                }
            }
        } catch (NopRgException | IllegalArgumentException e) {
            System.err.println("nop-rg: " + e);
            return 2;
        }
    }

    /**
     * 组装搜索命令并执行（plan 2268 Phase 3：自 call() 提取的搜索编排段，逻辑不变）。
     */
    private Map<String, FileMatches> search(Path root) {
        SearchCoordinator coordinator = new SearchCoordinator(
                threads > 0 ? threads : Runtime.getRuntime().availableProcessors(),
                !noIgnore, false);
        // count 模式不需要行文本（rg -c 等价口径，优化迭代 Round 4/5：纯行计数快速路径）
        boolean includeLineText = !count;
        // 纯文本输出不消费命中文本（plan 2273 R5）；--json 的 submatch.text 需要全量解码
        boolean includeSubmatchText = json;
        // --regex 优先于 --vector（vector 仅加速字面量）
        SearchCoordinator.Strategy strategy = regex ? SearchCoordinator.Strategy.REGEX
                : vector ? SearchCoordinator.Strategy.VECTOR
                : SearchCoordinator.Strategy.LITERAL;
        SearchCommand command = new SearchCommand(root, pattern, strategy,
                ignoreCase, globs, 0, includeLineText, includeSubmatchText);
        return coordinator.search(command);
    }
}
