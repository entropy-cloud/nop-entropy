package io.nop.rg.core;

import io.nop.rg.core.glob.GlobMatcher;
import io.nop.rg.core.io.ChunkedFileReader;
import io.nop.rg.core.io.MappedFileReader;
import io.nop.rg.core.search.ScalarByteSearcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 核心层端到端集成测试（plan 2263 Phase 5 / 里程碑 M1）：
 * 磁盘文件 → ChunkedFileReader/MappedFileReader → ScalarByteSearcher → GlobMatcher 过滤 → 命中偏移聚合。
 * 断言粒度：文件 + 字节偏移（行号语义不在 Wave 1 范围）。
 */
public class CoreSearchIntegrationTest {

    @TempDir
    Path tempDir;

    private static final byte[] NEEDLE = bytes("needle");

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private Path writeFile(String rel, String content) throws IOException {
        Path p = tempDir.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.write(p, bytes(content));
        return p;
    }

    /**
     * 命中偏移聚合（测试自持 helper，plan 2268 Phase 1 删除 Wave 1 死数据类型后的最小替代面——
     * 偏移列表 + maxMatches 截断标志）。
     */
    private static final class HitList {
        final List<Long> offsets = new ArrayList<>();
        final int maxMatches;
        boolean truncated;

        HitList(int maxMatches) {
            this.maxMatches = maxMatches;
        }

        boolean hasMore() {
            return maxMatches <= 0 || offsets.size() < maxMatches;
        }

        void add(long offset) {
            offsets.add(offset);
        }
    }

    /**
     * 单文件扫描：reader 逐块读取，searcher 搜索，主区间去重规则聚合进 {@link HitList}。
     */
    private HitList scanFile(Path file, byte[] pattern, int maxMatches, int chunkSize, int overlap) {
        HitList result = new HitList(maxMatches);
        try (ChunkedFileReader reader = new ChunkedFileReader(file, chunkSize, overlap)) {
            ChunkedFileReader.Chunk chunk;
            while ((chunk = reader.nextChunk()) != null) {
                long from = 0;
                while (result.hasMore()) {
                    long pos = ScalarByteSearcher.INSTANCE.findPattern(
                            chunk.segment(), from, chunk.viewLen(), pattern);
                    if (pos < 0) {
                        break;
                    }
                    long absolute = chunk.absoluteOffset(pos);
                    if (chunk.inPrimary(absolute)) {
                        result.add(absolute);
                    }
                    from = pos + 1;
                }
            }
        }
        return result;
    }

    @Test
    public void testEndToEndSmallFilesWithMappedReader() throws IOException {
        writeFile("src/Main.java", "int needle = 0; // the needle here");
        writeFile("src/Util.java", "no match in this file");
        writeFile("docs/readme.md", "needle in markdown");

        // MappedFileReader + glob 正/负规则过滤
        GlobMatcher globs = GlobMatcher.of(Arrays.asList("*.java", "!Util.java"));
        List<String> searchOrder = new ArrayList<>();
        try (var files = Files.walk(tempDir)) {
            files.filter(Files::isRegularFile)
                    .map(p -> tempDir.relativize(p).toString().replace('\\', '/'))
                    .filter(globs::accept)
                    .sorted()
                    .forEach(searchOrder::add);
        }
        // 排除规则生效：仅两个 .java 且排除 Util.java
        assertEquals(List.of("src/Main.java"), searchOrder);

        try (MappedFileReader reader = new MappedFileReader(tempDir.resolve("src/Main.java"))) {
            List<Long> offsets = new ArrayList<>();
            byte[] pattern = NEEDLE;
            long from = 0;
            MemorySegmentBridge bridge = new MemorySegmentBridge(reader.getSegment());
            while (true) {
                long pos = bridge.findPattern(from, reader.getSize(), pattern);
                if (pos < 0) {
                    break;
                }
                offsets.add(pos);
                from = pos + 1;
            }
            assertEquals(2, offsets.size());
            assertEquals(4L, offsets.get(0));
            assertEquals(23L, offsets.get(1));
        }
    }

    @Test
    public void testEndToEndChunkedAcrossBoundary() throws IOException {
        // 内容构造：模式 "needle" 横跨 chunk 边界（chunkSize=16 → 边界在 16）
        StringBuilder sb = new StringBuilder("0123456789ABCDEF");
        sb.append("needle"); // 起点 16，跨界
        sb.append("-tail-");
        sb.append("needle"); // 第二处完整位于第二块
        Path file = writeFile("big/boundary.bin", sb.toString());

        HitList result = scanFile(file, NEEDLE, 0, 16, 8);
        assertEquals(2, result.offsets.size());
        assertEquals(16L, result.offsets.get(0));
        assertEquals(16 + 6 + 6, result.offsets.get(1));
    }

    @Test
    public void testEndToEndGlobFilteringAndAggregation() throws IOException {
        writeFile("a/one.txt", "needle here\nplain line\nneedle again");
        writeFile("a/two.txt", "nothing relevant");
        writeFile("b/three.txt", "needle in b");
        writeFile("skipme.log", "needle in log");

        GlobMatcher globs = GlobMatcher.of(Arrays.asList("**", "!skipme.log", "!b/**"));

        // 目录树遍历（无 walker，测试内 Files.walk）+ glob 过滤 + 内容搜索聚合
        Map<String, HitList> results = new TreeMap<>();
        try (var files = Files.walk(tempDir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                String rel = tempDir.relativize(p).toString().replace('\\', '/');
                if (!globs.accept(rel)) {
                    return;
                }
                results.put(rel, scanFile(p, NEEDLE, 0, 16, 8));
            });
        }

        assertEquals(2, results.size());
        assertEquals(2, results.get("a/one.txt").offsets.size());
        assertEquals(0L, results.get("a/one.txt").offsets.get(0));
        assertEquals(23L, results.get("a/one.txt").offsets.get(1));
        assertEquals(0, results.get("a/two.txt").offsets.size());
    }

    @Test
    public void testMaxMatchesTruncation() throws IOException {
        writeFile("trunc.txt", "needle needle needle");
        // maxMatches=2：扫描在累计 2 个命中后停止（第三处不计入）
        HitList result = scanFile(tempDir.resolve("trunc.txt"), NEEDLE, 2, 64, 8);
        assertEquals(2, result.offsets.size());
        // 扫描路径本身不设置截断标志（由上层消费者决定），此处仅守护默认值
        assertFalse(result.truncated);
    }

    /**
     * 测试内桥接：将 MappedFileReader 的 segment 与 searcher 连接。
     */
    private record MemorySegmentBridge(MemorySegment segment) {
        long findPattern(long offset, long limit, byte[] pattern) {
            return ScalarByteSearcher.INSTANCE.findPattern(segment, offset, limit, pattern);
        }
    }
}
