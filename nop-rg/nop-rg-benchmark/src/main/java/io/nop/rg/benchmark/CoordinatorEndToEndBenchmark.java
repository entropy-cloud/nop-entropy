package io.nop.rg.benchmark;

import io.nop.core.initialize.CoreInitialization;
import io.nop.rg.core.coordinator.SearchCommand;
import io.nop.rg.core.coordinator.SearchCoordinator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * coordinator 端到端吞吐（plan 2265 JMH-04）：glob 过滤 → walker → 整文件映射搜索 → 行聚合。
 * corpus 为固定种子真实文本，构建一次复用（不进 target，避免 mvn clean 漂移）。
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

    private Path corpusDir;
    private boolean initialized;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        Path base = Path.of(System.getProperty("java.io.tmpdir"), "nop-rg-bench-corpus")
                .resolve(size.toLowerCase());
        long bytes = "1MB".equals(size) ? 1L << 20 : "64MB".equals(size) ? 64L << 20 : 512L << 20;
        corpusDir = base;
        // 16 个分片文件（模拟多文件目录树）
        long perFile = bytes / 16;
        for (int i = 0; i < 16; i++) {
            CorpusUtil.ensureFile(corpusDir, "part" + i + ".txt", perFile, 42L + i);
        }
        initialized = true;
        CoreInitialization.initialize();
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (initialized) {
            CoreInitialization.destroy();
        }
    }

    @Benchmark
    public void endToEndSearch(Blackhole bh) throws IOException {
        SearchCoordinator coordinator = new SearchCoordinator(
                Runtime.getRuntime().availableProcessors(), false, false);
        Map<String, SearchCoordinator.FileMatches> results = coordinator.search(
                new SearchCommand(corpusDir, "needle", SearchCoordinator.Strategy.LITERAL,
                        false, java.util.List.of(), 0, false));
        int lines = 0;
        for (SearchCoordinator.FileMatches matches : results.values()) {
            lines += matches.lineCount();
        }
        bh.consume(lines);
    }
}
