package io.nop.rg.benchmark;

import io.nop.rg.core.search.LiteralFinderProvider;
import io.nop.rg.core.search.PreparedFinder;
import io.nop.rg.core.search.PreparedLiteral;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

/**
 * 标量 vs Vector 吞吐对比（plan 2266 follow-up；plan 2267 Phase 1 场景矩阵化）。
 *
 * <p>场景 = 命中模式长度（6B needle / 32B token）× 命中密度（~1/6 稀疏 / ~1/2 密集），
 * corpus 为内存生成（固定种子逐字节可再生，无文件复用污染面）。
 *
 * <p>运行前置：{@code java --add-modules jdk.incubator.vector -cp ...}（与 README 运行方式一致）。
 * 孵化模块缺失时 provider 自动降级标量——两组基准数值将相同，setup 会打印警示（不静默）。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
public class VectorCompareBenchmark {

    // 32 字节命中 token（稀疏锚点随机分布，不与常规词表重叠）
    public static final String LONG_HIT = "QzWxEcRvTbYnUmIkOlPjHgFdSaGdJfKd";

    // 16 字节命中 token（SIMD/标量 crossover 探测点，plan 2267 R1 阈值取证）
    public static final String MID_HIT_16B = "QzWxEcRvTbYnUmIk";

    @Param({"sparse-short-6B", "dense-short-6B", "sparse-mid-16B", "dense-mid-16B",
            "sparse-long-32B", "dense-long-32B"})
    private String scenario;

    private MemorySegment segment;
    private long length;
    private byte[] pattern;
    private PreparedFinder scalarFinder;
    private PreparedFinder vectorFinder;
    private boolean vectorAvailable;

    @Setup
    public void setup() {
        String hitWord;
        int hitEveryN;
        switch (scenario) {
            case "sparse-short-6B" -> {
                hitWord = "needle";
                hitEveryN = 6;
            }
            case "dense-short-6B" -> {
                hitWord = "needle";
                hitEveryN = 2;
            }
            case "sparse-mid-16B" -> {
                hitWord = MID_HIT_16B;
                hitEveryN = 6;
            }
            case "dense-mid-16B" -> {
                hitWord = MID_HIT_16B;
                hitEveryN = 2;
            }
            case "sparse-long-32B" -> {
                hitWord = LONG_HIT;
                hitEveryN = 6;
            }
            case "dense-long-32B" -> {
                hitWord = LONG_HIT;
                hitEveryN = 2;
            }
            default -> throw new IllegalArgumentException("unknown scenario: " + scenario);
        }
        byte[] data = CorpusUtil.textBytes(1L << 20, 42L, hitWord, hitEveryN);
        segment = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
        length = data.length;
        pattern = hitWord.getBytes(StandardCharsets.UTF_8);
        scalarFinder = PreparedLiteral.compile(pattern, false);
        LiteralFinderProvider provider = ServiceLoader.load(LiteralFinderProvider.class)
                .findFirst().orElse(null);
        if (provider == null) {
            System.out.println("WARNING: nop-rg-vector not on classpath — vector benchmark measures scalar fallback");
            vectorFinder = scalarFinder;
            vectorAvailable = false;
        } else {
            vectorFinder = provider.compile(pattern, false);
            vectorAvailable = provider.available();
            if (!vectorAvailable) {
                System.out.println("WARNING: --add-modules jdk.incubator.vector missing — vector benchmark measures scalar fallback");
            }
        }
    }

    @Benchmark
    public void scalarScan(Blackhole bh) {
        long from = 0;
        int count = 0;
        while (true) {
            long pos = scalarFinder.find(segment, from, length);
            if (pos < 0) {
                break;
            }
            count++;
            from = pos + scalarFinder.patternLength();
        }
        bh.consume(count);
    }

    @Benchmark
    public void vectorScan(Blackhole bh) {
        long from = 0;
        int count = 0;
        while (true) {
            long pos = vectorFinder.find(segment, from, length);
            if (pos < 0) {
                break;
            }
            count++;
            from = pos + vectorFinder.patternLength();
        }
        bh.consume(count);
    }

    public boolean isVectorAvailable() {
        return vectorAvailable;
    }
}
