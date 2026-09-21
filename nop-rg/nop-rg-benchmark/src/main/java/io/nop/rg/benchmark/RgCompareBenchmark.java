package io.nop.rg.benchmark;

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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 与系统 rg 的端到端对比基准（plan 2265 JMH-05）。
 *
 * <p>对比口径（plan 钉死）：双方 count 等价——rg 以 {@code -c} 输出行计数，
 * 子进程 spawn 开销计入并在结果中注明占比。
 * rg 不可用时的行为：JMH 无 skip 机制，@Setup 抛出 IllegalStateException 终止该基准
 * （fail-fast 是 JMH 的官方模式，错误信息含可用动作）；本基准不参与时用 include 正则排除。
 * 该基准的数字只作收尾档吞吐比裁定（迭代档子进程噪音大，不作依据）。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
public class RgCompareBenchmark {

    @Param({"1MB", "64MB", "512MB"})
    private String size;

    private Path corpusDir;
    private boolean rgAvailable;

    @Setup(Level.Trial)
    public void setup() throws IOException, InterruptedException {
        Path base = Path.of(System.getProperty("java.io.tmpdir"), "nop-rg-bench-corpus")
                .resolve(size.toLowerCase());
        long bytes = "1MB".equals(size) ? 1L << 20 : "64MB".equals(size) ? 64L << 20 : 512L << 20;
        corpusDir = base;
        long perFile = bytes / 16;
        for (int i = 0; i < 16; i++) {
            CorpusUtil.ensureFile(corpusDir, "part" + i + ".txt", perFile, 42L + i);
        }
        Process p = new ProcessBuilder("rg", "--version").start();
        rgAvailable = p.waitFor() == 0;
        if (!rgAvailable) {
            // JMH 无 skip 机制：fail-fast 并给出排除指令（-Dexcluded 或 include 正则不匹配本类）
            throw new IllegalStateException(
                    "system rg not found on PATH: exclude RgCompareBenchmark via"
                            + " '.*RgCompare.*' negative regex or install ripgrep");
        }
    }

    @Benchmark
    public void rgCount(Blackhole bh) {
        try {
            Process p = new ProcessBuilder("rg", "-c", "needle", ".")
                    .directory(corpusDir.toFile()).start();
            byte[] out = p.getInputStream().readAllBytes();
            p.waitFor();
            bh.consume(out.length);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
