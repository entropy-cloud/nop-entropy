package io.nop.rg.benchmark;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.cli.ResultPrinter;
import io.nop.rg.core.coordinator.FileMatches;
import io.nop.rg.core.coordinator.SearchCommand;
import io.nop.rg.core.coordinator.SearchCoordinator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * coordinator 端到端吞吐（plan 2265 JMH-04）：glob 过滤 → walker → 映射搜索 → 行聚合。
 * corpus 为固定种子真实文本，构建一次复用（不进 target，避免 mvn clean 漂移）。
 *
 * <p>plan 2267 Phase 1 场景矩阵化：{@code scenario} 选择命中词/密度（corpus 目录随场景隔离——
 * ensureFile 复用键只有 path+size，同 size 不同场景必须分目录防静默污染）；
 * {@code strategy} 支持 LITERAL/VECTOR 对比（VECTOR 需 --add-modules jdk.incubator.vector）。
 *
 * <p>plan 2273 Phase 2 口径扩展：{@code mode} 选择 count（includeLineText=false 纯行计数，
 * 2267 判定口径）或 text（includeLineText=true + {@link ResultPrinter} 输出至 CLI 等价
 * sink——形态镜像 NopRgMain 的 stdout writer，Phase 2 为 autoflush 逐行 flush，
 * R1 起随 CLI 改为 64KB 缓冲 + 收尾 flush，保证两侧口径始终等价）。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
public class CoordinatorEndToEndBenchmark {

    @Param({"1MB", "64MB", "512MB"})
    private String size;

    @Param({"needle-6B"})
    private String scenario;

    @Param({"LITERAL"})
    private String strategy;

    @Param({"count", "text"})
    private String mode;

    private Path corpusDir;
    private boolean initialized;
    private PrintWriter textSink;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        if ("many-small".equals(scenario)) {
            // plan 2273 Phase 2 多小文件口径：512 × 128KB（=64MB 总量），corpus 参数完全由
            // scenario 决定（size 维度对该场景无效，运行时以 -p size=64MB 钉定消除重复 trial）
            corpusDir = Path.of(System.getProperty("java.io.tmpdir"), "nop-rg-bench-corpus").resolve("many-small");
            long perFile = 128L * 1024;
            for (int i = 0; i < 512; i++) {
                CorpusUtil.ensureFile(corpusDir, "part" + i + ".txt", perFile, 42L + i);
            }
        } else {
            long bytes = "1MB".equals(size) ? 1L << 20 : "64MB".equals(size) ? 64L << 20 : 512L << 20;
            String dirName = switch (scenario) {
                case "needle-6B" -> size.toLowerCase();
                case "long-32B" -> size.toLowerCase() + "-long";
                default -> throw new IllegalArgumentException("unknown scenario: " + scenario);
            };
            corpusDir = Path.of(System.getProperty("java.io.tmpdir"), "nop-rg-bench-corpus").resolve(dirName);
            // 16 个分片文件（模拟多文件目录树）
            long perFile = bytes / 16;
            for (int i = 0; i < 16; i++) {
                if ("long-32B".equals(scenario)) {
                    CorpusUtil.ensureFile(corpusDir, "part" + i + ".txt", perFile, 42L + i,
                            VectorCompareBenchmark.LONG_HIT, 6);
                } else {
                    CorpusUtil.ensureFile(corpusDir, "part" + i + ".txt", perFile, 42L + i);
                }
            }
        }
        // CLI 等价输出 sink（plan 2273 R1 起 = 缓冲形态，镜像 NopRgMain 的 64KB BufferedOutputStream
        // + 收尾 flush；/dev/null 的 write 系统调用代价与真实 stdout 等价）
        textSink = new PrintWriter(new BufferedOutputStream(
                new FileOutputStream(System.getProperty("os.name").toLowerCase().contains("win")
                        ? "NUL" : "/dev/null"), 64 * 1024), false);
        initialized = true;
        CoreInitialization.initialize();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (textSink != null) {
            textSink.close();
        }
        if (initialized) {
            CoreInitialization.destroy();
        }
    }

    @Benchmark
    public void endToEndSearch(Blackhole bh) throws IOException {
        SearchCoordinator coordinator = new SearchCoordinator(
                Runtime.getRuntime().availableProcessors(), false, false);
        boolean includeLineText = "text".equals(mode);
        Map<String, FileMatches> results = coordinator.search(
                new SearchCommand(corpusDir, pattern(), SearchCoordinator.Strategy.valueOf(strategy),
                        false, List.of(), 0, includeLineText));
        if (includeLineText) {
            ResultPrinter.print(textSink, results, ResultPrinter.OutputMode.TEXT);
            textSink.flush();
        }
        int lines = 0;
        for (FileMatches matches : results.values()) {
            lines += matches.lineCount();
        }
        bh.consume(lines);
    }

    private String pattern() {
        return switch (scenario) {
            case "needle-6B", "many-small" -> "needle";
            case "long-32B" -> VectorCompareBenchmark.LONG_HIT;
            default -> throw new IllegalArgumentException("unknown scenario: " + scenario);
        };
    }
}
