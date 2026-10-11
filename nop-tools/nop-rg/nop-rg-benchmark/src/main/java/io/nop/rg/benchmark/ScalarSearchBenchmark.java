package io.nop.rg.benchmark;

import io.nop.rg.core.search.ScalarByteSearcher;
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
import java.util.concurrent.TimeUnit;

/**
 * 标量 BMH 搜索吞吐（plan 2265 JMH-02）。
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
public class ScalarSearchBenchmark {

    @Param({"1MB", "64MB"})
    private String size;

    private MemorySegment segment;
    private long length;
    private byte[] hitPattern;
    private byte[] missPattern;

    @Setup
    public void setup() {
        long bytes = "1MB".equals(size) ? 1L << 20 : 64L << 20;
        byte[] data = CorpusUtil.textBytes(bytes, 42L);
        segment = Arena.global().allocateFrom(ValueLayout.JAVA_BYTE, data);
        length = data.length;
        hitPattern = "needle".getBytes(StandardCharsets.UTF_8);
        missPattern = "zzzzzq".getBytes(StandardCharsets.UTF_8);
    }

    @Benchmark
    public void scanHit(Blackhole bh) {
        long from = 0;
        int count = 0;
        while (true) {
            long pos = ScalarByteSearcher.INSTANCE.findPattern(segment, from, length, hitPattern);
            if (pos < 0) {
                break;
            }
            count++;
            from = pos + hitPattern.length;
        }
        bh.consume(count);
    }

    @Benchmark
    public void scanMiss(Blackhole bh) {
        bh.consume(ScalarByteSearcher.INSTANCE.findPattern(segment, 0, length, missPattern));
    }
}
