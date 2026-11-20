package io.nop.rg.cli;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.core.coordinator.FileError;
import io.nop.rg.core.coordinator.SearchCommand;
import io.nop.rg.core.coordinator.SearchCoordinator;
import io.nop.rg.core.coordinator.SearchOutcome;
import picocli.CommandLine;

import java.io.BufferedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * nop-rg 命令行入口（plan 2264 CLI-01..03）。
 *
 * <p>用法：{@code nop-rg PATTERN [PATH] [-g glob]... [-i] [-c] [-l] [--json] [--no-ignore] [--threads N]
 * [--encoding <charset>] [--jfr <file>]
 *
 * <p>退出码：命中 0 / 未命中 1 / 错误 2 / 非法 --encoding 值 3（plan 2575 Phase E2 R4 裁定：
 * 有意偏离 rg 的 0/1/2 契约——非法编码是参数用法错误，与搜索执行错误区分；roadmap 与
 * 设计文档 03 验收表钉 exit 3）。
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
            + " on classpath and --add-modules jdk.incubator.vector (falls back to scalar with a stderr warning"
            + " if module present but incubator module missing; errors out if module absent)")
    private boolean vector;

    @CommandLine.Option(names = "--jfr", arity = "1",
            description = "Record JFR events (CPU/alloc/lock) to the given .jfr file during the search")
    private String jfrOutput;

    /**
     * 显式编码（plan 2575 Phase E2）：同时作用于模式编码 / regex 解码 / 输出解码三处
     * （设计文档 03 决策 3）；{@code none} 别名 = 默认字节保真（决策 2 显式形式）。
     * 非法值显式报错 exit 3，不回退 UTF-8（R4：catch-all 之前前置校验）。
     */
    @CommandLine.Option(names = "--encoding", arity = "1",
            description = "Text encoding for pattern matching, regex decoding and output text"
                    + " (e.g. gbk, iso-8859-1, shift_jis; 'none' = byte-fidelity default)."
                    + " Invalid value exits with code 3")
    private String encoding;

    // --encoding 解析结果（plan 2575 Phase E2）：call() 前置校验后写入，search() 消费；
    // 包内字段便于测试覆写/断言
    Charset searchCharset = StandardCharsets.UTF_8;

    public static void main(String[] args) {
        int code = new CommandLine(new NopRgMain()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() {
        // --encoding 校验前置（plan 2575 R4）：IllegalCharsetNameException/UnsupportedCharsetException
        // 均为 IllegalArgumentException 子类，若落入下方 catch-all 会被归位 exit 2——显式前置返回 exit 3
        Charset charset;
        if (encoding == null || "none".equals(encoding)) {
            charset = StandardCharsets.UTF_8;
        } else {
            try {
                charset = Charset.forName(encoding);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                // stderr 直出（CLI 无 logger；消息面向用户，无需堆栈——非法值属用法错误）
                System.err.println("nop-rg: invalid --encoding value '" + encoding + "' ("
                        + e.getClass().getSimpleName() + "): not a supported charset name");
                return 3;
            }
        }
        this.searchCharset = charset;
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
                    SearchOutcome outcome = search(root);
                    // plan 2273 R1：64KB 缓冲 + 收尾一次性 flush，替代 autoflush 逐行 flush（字节序列不变）
                    PrintWriter out = new PrintWriter(
                            new BufferedOutputStream(System.out, OUTPUT_BUFFER_BYTES), false);
                    ResultPrinter.print(out, outcome.results(), ResultPrinter.resolveMode(json, filesWithMatches, count));
                    out.flush();
                    // plan 2564 W-05：部分成功语义——错误项 stderr 逐条 + exit 2（消费 plan 2276 G2
                    // 既有契约），部分结果照常输出；无错误维持 0/1 语义
                    if (!outcome.errors().isEmpty()) {
                        for (FileError error : outcome.errors()) {
                            System.err.println("nop-rg: error scanning " + error.relativePath()
                                    + ": " + error.message());
                        }
                        return 2;
                    }
                    return outcome.results().isEmpty() ? 1 : 0;
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
        } catch (NopException | IllegalArgumentException e) {
            // NopException 覆盖 NopRgException 与 CoreInitialization/VFS/GitIgnoreFile 资源层异常——
            // 全部归位错误退出码 2（plan 2276 G2），不落入 picocli 默认处理的 exit 1（与未命中混淆）
            System.err.println("nop-rg: " + e);
            return 2;
        }
    }

    /**
     * 组装搜索命令并执行（plan 2268 Phase 3：自 call() 提取的搜索编排段）。
     * 包私有供测试覆写（plan 2276 G2：裸 NopException → exit 2 契约）；
     * plan 2564 W-05 起返回 {@link SearchOutcome} 部分成功联合结果。
     */
    SearchOutcome search(Path root) {
        SearchCoordinator coordinator = new SearchCoordinator(
                threads > 0 ? threads : Runtime.getRuntime().availableProcessors(),
                !noIgnore, false);
        // count 模式不需要行文本（rg -c 等价口径，优化迭代 Round 4/5：纯行计数快速路径）；
        // --json 需要 LineMatch 构建 match 消息，数据完整性优先于 count 快速路径（plan 2275 G1）
        boolean includeLineText = !count || json;
        // 纯文本输出不消费命中文本（plan 2273 R5）；--json 的 submatch.text 需要全量解码
        boolean includeSubmatchText = json;
        // --regex 优先于 --vector（vector 仅加速字面量）
        SearchCoordinator.Strategy strategy = regex ? SearchCoordinator.Strategy.REGEX
                : vector ? SearchCoordinator.Strategy.VECTOR
                : SearchCoordinator.Strategy.LITERAL;
        SearchCommand command = new SearchCommand(root, pattern, strategy,
                ignoreCase, globs, 0, includeLineText, includeSubmatchText, searchCharset);
        return coordinator.search(command);
    }
}
