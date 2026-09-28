/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.benchmark.stream;

import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.stream.connector.file.FileSourceReader;
import io.nop.stream.connector.file.FileSplit;
import io.nop.stream.connector.file.FileTwoPhaseCommitSink;
import io.nop.stream.connector.jdbc.JdbcTwoPhaseCommitSink;
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

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Connector 数据面逐记录路径基准（plan 366 Phase 1，R4 审计基准缺口补建）。
 *
 * <p>被测生产入口（照实生产类型）：
 * <ul>
 *   <li>{@code fileSourceReadLine}：{@link FileSourceReader#pollNext()}（file source
 *       逐行读取路径，含 split 打开/游标推进——R4-P2 逐字节读候选的口径）；</li>
 *   <li>{@code fileSinkInvokeMap}：{@link FileTwoPhaseCommitSink#invoke}（每记录
 *       {@code value.toString()} 物化——R4-P1 候选的口径，Map/Bean 负载下 toString
 *       为序列化级成本）；</li>
 *   <li>{@code jdbcSinkInvokeMap}：{@link JdbcTwoPhaseCommitSink#invoke}（每记录防御性
 *       {@code new LinkedHashMap<>(row)} 拷贝——R4-P3 候选的口径；{@code invoke} 仅做
 *       内存缓冲不触 JDBC，{@link IJdbcTemplate} 用反射桩，永不命中）。</li>
 * </ul>
 *
 * <p>负载：source 行长与 sink Map 条目数由 {@code @Param} 控制；source 文件在 trial
 * setup 一次性生成（约 32MB），单次 reset 覆盖数十万次调用，摊销后不影响稳态。
 * 临时文件写在模块 {@code target/bench-connector/} 下（不入库），trial 结束清理。
 */
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ConnectorInvokeBench {

    private static final String VALUE_TEXT = "0123456789abcdef";

    @State(Scope.Benchmark)
    public static class SourceState {
        @Param({"64", "512"})
        public int lineChars;

        Path sourceFile;
        long fileSize;
        FileSourceReader reader;
        String filePath;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Files.createDirectories(Path.of("target", "bench-connector"));
            Path dir = Files.createTempDirectory(Path.of("target", "bench-connector"), "src-");
            sourceFile = dir.resolve("bench-source.txt");
            long lineBytes = lineChars + 1L;
            long lines = Math.max(1, 32L * 1024 * 1024 / lineBytes);
            StringBuilder sb = new StringBuilder(lineChars + 2);
            try (var writer = Files.newBufferedWriter(sourceFile, StandardCharsets.UTF_8)) {
                for (long i = 0; i < lines; i++) {
                    sb.setLength(0);
                    while (sb.length() < lineChars) {
                        sb.append(VALUE_TEXT);
                    }
                    sb.setLength(lineChars);
                    sb.append('\n');
                    writer.write(sb.toString());
                }
            }
            fileSize = Files.size(sourceFile);
            filePath = sourceFile.toAbsolutePath().toString();
            reader = freshReader();
        }

        FileSourceReader freshReader() {
            FileSourceReader r = new FileSourceReader(null);
            r.addSplits(List.of(new FileSplit(filePath, 0, fileSize)));
            return r;
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            if (reader != null) {
                reader.close();
            }
            deleteQuietly(sourceFile.getParent());
        }
    }

    @State(Scope.Benchmark)
    public static class FileSinkState {
        @Param({"4", "16"})
        public int mapEntries;

        FileTwoPhaseCommitSink<Map<String, Object>> sink;
        Map<String, Object> payload;
        Path sinkDir;
        int invokes;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            Files.createDirectories(Path.of("target", "bench-connector"));
            sinkDir = Files.createTempDirectory(Path.of("target", "bench-connector"), "filesink-");
            sink = new FileTwoPhaseCommitSink<>(sinkDir.toAbsolutePath().toString());
            payload = new LinkedHashMap<>();
            for (int i = 0; i < mapEntries; i++) {
                payload.put("col" + i, VALUE_TEXT);
            }
        }

        @TearDown(Level.Trial)
        public void tearDown() throws IOException {
            deleteQuietly(sinkDir);
        }
    }

    @State(Scope.Benchmark)
    public static class JdbcSinkState {
        JdbcTwoPhaseCommitSink<Map<String, Object>> sink;
        Map<String, Object> payload;
        int invokes;

        @Setup(Level.Trial)
        public void setup() {
            IJdbcTemplate template = (IJdbcTemplate) Proxy.newProxyInstance(
                    IJdbcTemplate.class.getClassLoader(), new Class<?>[]{IJdbcTemplate.class},
                    (proxy, method, args) -> {
                        throw new UnsupportedOperationException("bench stub: " + method.getName());
                    });
            List<String> columns = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) {
                columns.add("c" + i);
            }
            // invoke() 只做 recordMapper + 内存缓冲，模板桩永不命中；saveState 等路径不在本口径内。
            sink = new JdbcTwoPhaseCommitSink<>(template, "bench", "bench_t", "bench_ledger_t",
                    columns, v -> v);
            payload = new LinkedHashMap<>();
            for (int i = 0; i < 10; i++) {
                payload.put("c" + i, VALUE_TEXT);
            }
        }
    }

    /** file source 逐行读取：单次 pollNext 一行；文件耗尽时重建 reader（每数十万行一次，摊销可忽略）。 */
    @Benchmark
    public void fileSourceReadLine(SourceState s, Blackhole bh) throws Exception {
        Optional<String> line = s.reader.pollNext();
        if (!line.isPresent()) {
            s.reader.close();
            s.reader = s.freshReader();
            line = s.reader.pollNext();
        }
        bh.consume(line.orElse(null));
    }

    /** file sink 逐记录缓冲：Map/Bean 负载的 {@code value.toString()} 物化成本（R4-P1 口径）。 */
    @Benchmark
    public void fileSinkInvokeMap(FileSinkState s, Blackhole bh) throws Exception {
        s.sink.invoke(s.payload);
        // 周期性清空缓冲（rollback = currentBuffer.clear()）：invoke 只测逐记录路径，
        // 缓冲无界增长会扭曲稳态。每 4096 次 ~2 次数组扩容，摊销可忽略。
        if ((++s.invokes & 0xFFF) == 0) {
            s.sink.rollback();
        }
        bh.consume(true);
    }

    /** jdbc sink 逐记录缓冲：每记录防御性 Map 拷贝成本（R4-P3 口径）。 */
    @Benchmark
    public void jdbcSinkInvokeMap(JdbcSinkState s, Blackhole bh) throws Exception {
        s.sink.invoke(s.payload);
        // 同 fileSinkInvokeMap：无界缓冲会扭曲稳态，周期清空。
        if ((++s.invokes & 0xFFF) == 0) {
            s.sink.rollback();
        }
        bh.consume(true);
    }

    private static void deleteQuietly(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                Files.deleteIfExists(p);
            }
        }
        Files.deleteIfExists(dir);
    }
}
