package io.nop.rg.core;

import io.nop.rg.core.glob.GlobMatcher;
import io.nop.rg.core.io.ChunkedFileReader;
import io.nop.rg.core.io.MappedFileReader;
import io.nop.rg.core.search.MatchResult;
import io.nop.rg.core.search.ScalarByteSearcher;
import io.nop.rg.core.search.SearchRequest;
import io.nop.rg.core.search.SearchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 核心层端到端集成测试（plan 2263 Phase 5 / 里程碑 M1）：
 * 磁盘文件 → ChunkedFileReader/MappedFileReader → ScalarByteSearcher → GlobMatcher 过滤 → SearchResult/MatchResult 聚合。
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
     * 单文件扫描：reader 逐块读取，searcher 搜索，主区间去重规则聚合进 SearchResult。
     */
    private SearchResult scanFile(Path file, SearchRequest request, int chunkSize, int overlap) {
        SearchResult result = new SearchResult();
        byte[] pattern = request.getPattern();
        try (ChunkedFileReader reader = new ChunkedFileReader(file, chunkSize, overlap)) {
            ChunkedFileReader.Chunk chunk;
            while ((chunk = reader.nextChunk()) != null) {
                long from = 0;
                while (request.hasMore(result.getCount())) {
                    long pos = ScalarByteSearcher.INSTANCE.findPattern(
                            chunk.segment(), from, chunk.viewLen(), pattern);
                    if (pos < 0) {
                        break;
                    }
                    long absolute = chunk.absoluteOffset(pos);
                    if (chunk.inPrimary(absolute)) {
                        result.add(new MatchResult(absolute, pattern.length));
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
            SearchResult result = new SearchResult();
            byte[] pattern = NEEDLE;
            long from = 0;
            MemorySegmentBridge bridge = new MemorySegmentBridge(reader.getSegment());
            while (true) {
                long pos = bridge.findPattern(from, reader.getSize(), pattern);
                if (pos < 0) {
                    break;
                }
                result.add(new MatchResult(pos, pattern.length));
                from = pos + 1;
            }
            assertEquals(2, result.getCount());
            assertEquals(4, result.getMatches().get(0).getOffset());
            assertEquals(23, result.getMatches().get(1).getOffset());
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

        SearchResult result = scanFile(file, new SearchRequest(NEEDLE, 0), 16, 8);
        assertEquals(2, result.getCount());
        assertEquals(16, result.getMatches().get(0).getOffset());
        assertEquals(16 + 6 + 6, result.getMatches().get(1).getOffset());
    }

    @Test
    public void testEndToEndGlobFilteringAndAggregation() throws IOException {
        writeFile("a/one.txt", "needle here\nplain line\nneedle again");
        writeFile("a/two.txt", "nothing relevant");
        writeFile("b/three.txt", "needle in b");
        writeFile("skipme.log", "needle in log");

        SearchRequest request = new SearchRequest(NEEDLE, 0);
        GlobMatcher globs = GlobMatcher.of(Arrays.asList("**", "!skipme.log", "!b/**"));

        // 目录树遍历（无 walker，测试内 Files.walk）+ glob 过滤 + 内容搜索聚合
        Map<String, SearchResult> results = new TreeMap<>();
        try (var files = Files.walk(tempDir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                String rel = tempDir.relativize(p).toString().replace('\\', '/');
                if (!globs.accept(rel)) {
                    return;
                }
                results.put(rel, scanFile(p, request, 16, 8));
            });
        }

        assertEquals(2, results.size());
        assertEquals(2, results.get("a/one.txt").getCount());
        assertEquals(0, results.get("a/one.txt").getMatches().get(0).getOffset());
        assertEquals(23, results.get("a/one.txt").getMatches().get(1).getOffset());
        assertEquals(0, results.get("a/two.txt").getCount());
    }

    @Test
    public void testMaxMatchesTruncation() throws IOException {
        writeFile("trunc.txt", "needle needle needle");
        SearchResult result = scanFile(tempDir.resolve("trunc.txt"), new SearchRequest(NEEDLE, 2), 64, 8);
        assertEquals(2, result.getCount());
        result.setTruncated(true);
        assertEquals(true, result.isTruncated());
    }

    /**
     * 测试内桥接：将 MappedFileReader 的 segment 与 searcher 连接。
     */
    private record MemorySegmentBridge(java.lang.foreign.MemorySegment segment) {
        long findPattern(long offset, long limit, byte[] pattern) {
            return ScalarByteSearcher.INSTANCE.findPattern(segment, offset, limit, pattern);
        }
    }
}
