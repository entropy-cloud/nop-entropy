package io.nop.find.cli;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.find.core.coordinator.FindCoordinator;
import io.nop.find.core.model.FindCommand;
import io.nop.find.core.model.FindMatch;
import picocli.CommandLine;

import java.io.BufferedOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * nop-find 命令行入口（plan 2379 Phase 3）。
 *
 * <p>用法：{@code nop-find QUERY [PATH] [-n limit] [-s] [-g glob]... [--json] [--no-ignore] [--threads N]}
 *
 * <p>退出码对齐 rg / nop-rg：命中 0 / 未命中 1 / 错误 2。
 * 空 query、非法 PATH、遍历错误均显式报错退出 2，不静默返回空结果。
 */
@CommandLine.Command(
        name = "nop-find",
        mixinStandardHelpOptions = true,
        version = "nop-find 0.1.0",
        description = "Pure-JVM fuzzy file finder (query -> top-K ranked paths).")
public class NopFindMain implements Callable<Integer> {

    private static final int OUTPUT_BUFFER_BYTES = 64 * 1024;
    private static final int DEFAULT_LIMIT = 100;

    @CommandLine.Parameters(index = "0", arity = "1", paramLabel = "QUERY",
            description = "Fuzzy query (non-empty)")
    private String query;

    @CommandLine.Parameters(index = "1", defaultValue = ".", paramLabel = "PATH",
            description = "Root directory to search")
    private String path;

    @CommandLine.Option(names = {"-n", "--limit"}, defaultValue = "" + DEFAULT_LIMIT,
            description = "Maximum number of results (default: " + DEFAULT_LIMIT + ")")
    private int limit;

    @CommandLine.Option(names = {"-s", "--case-sensitive"},
            description = "Case-sensitive matching (default: case-insensitive)")
    private boolean caseSensitive;

    @CommandLine.Option(names = {"-g", "--glob"},
            description = "Include/exclude glob (repeatable, '!' prefix excludes)")
    private List<String> globs = new ArrayList<>();

    @CommandLine.Option(names = "--json", description = "Output JSON instead of SCORE<TAB>RELATIVE_PATH lines")
    private boolean json;

    @CommandLine.Option(names = "--no-ignore", description = "Disable .gitignore filtering (hidden files stay hidden)")
    private boolean noIgnore;

    @CommandLine.Option(names = "--threads", defaultValue = "0", description = "Worker thread count (0 = CPU cores)")
    private int threads;

    public static void main(String[] args) {
        int code = new CommandLine(new NopFindMain()).execute(args);
        System.exit(code);
    }

    @Override
    public Integer call() {
        try {
            if (query == null || query.isBlank()) {
                System.err.println("nop-find: query must not be empty");
                return 2;
            }
            if (limit <= 0) {
                System.err.println("nop-find: limit must be positive, got " + limit);
                return 2;
            }
            if (threads < 0) {
                System.err.println("nop-find: threads must not be negative, got " + threads);
                return 2;
            }
            Path root = Path.of(path).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                System.err.println("nop-find: not a directory: " + path);
                return 2;
            }

            if (!noIgnore) {
                CoreInitialization.initialize();
            }
            try {
                List<FindMatch> matches = search(root);
                PrintWriter out = new PrintWriter(
                        new BufferedOutputStream(System.out, OUTPUT_BUFFER_BYTES), false);
                if (json) {
                    ResultPrinter.printJson(out, matches);
                } else {
                    ResultPrinter.printText(out, matches);
                }
                out.flush();
                return matches.isEmpty() ? 1 : 0;
            } finally {
                if (!noIgnore) {
                    CoreInitialization.destroy();
                }
            }
        } catch (NopException | IllegalArgumentException e) {
            // NopException 覆盖 NopFindException / NopRgException（walker）/ VFS 资源层异常，
            // 全部归位错误退出码 2，不落入 picocli 默认处理的 exit 1（与未命中混淆）
            System.err.println("nop-find: " + e);
            return 2;
        }
    }

    /**
     * 组装命令并执行查找（包私有供测试覆写）。
     */
    List<FindMatch> search(Path root) {
        FindCommand command = FindCommand.builder(root, query)
                .limit(limit)
                .caseSensitive(caseSensitive)
                .globs(globs)
                .build();
        FindCoordinator coordinator = new FindCoordinator(
                threads > 0 ? threads : Runtime.getRuntime().availableProcessors(),
                !noIgnore, false);
        return coordinator.find(command);
    }
}