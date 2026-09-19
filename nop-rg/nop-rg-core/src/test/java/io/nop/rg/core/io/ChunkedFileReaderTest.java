package io.nop.rg.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChunkedFileReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String name, byte[] data) throws Exception {
        Path p = tempDir.resolve(name);
        Files.write(p, data);
        return p;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testSmallFileSingleChunk() throws Exception {
        byte[] data = bytes("tiny file");
        Path file = write("tiny.txt", data);
        try (ChunkedFileReader reader = new ChunkedFileReader(file, 64, 8)) {
            assertEquals(data.length, reader.getFileSize());
            ChunkedFileReader.Chunk chunk = reader.nextChunk();
            assertEquals(0, chunk.chunkStart());
            assertEquals(data.length, chunk.primaryLen());
            assertEquals(0, chunk.viewStart());
            assertEquals(data.length, chunk.viewLen());
            assertNull(reader.nextChunk());
        }
    }

    @Test
    public void testEmptyFileYieldsNoChunks() throws Exception {
        Path file = write("empty.bin", new byte[0]);
        try (ChunkedFileReader reader = new ChunkedFileReader(file, 16, 4)) {
            assertNull(reader.nextChunk());
        }
    }

    @Test
    public void testChunksTileFileWithoutGaps() throws Exception {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) ('0' + (i % 10));
        }
        Path file = write("tiled.bin", data);
        try (ChunkedFileReader reader = new ChunkedFileReader(file, 30, 10)) {
            long covered = 0;
            ChunkedFileReader.Chunk chunk;
            List<long[]> primaries = new ArrayList<>();
            while ((chunk = reader.nextChunk()) != null) {
                assertEquals(covered, chunk.chunkStart(), "主区间必须无缝平铺");
                MemorySegment seg = chunk.segment();
                for (long p = chunk.chunkStart() - chunk.viewStart(); p < chunk.viewLen(); p++) {
                    long absolute = chunk.absoluteOffset(p);
                    assertEquals(data[(int) absolute], seg.get(ValueLayout.JAVA_BYTE, p),
                            "视图内容与文件在 " + absolute + " 不一致");
                }
                primaries.add(new long[]{chunk.chunkStart(), chunk.primaryLen()});
                covered += chunk.primaryLen();
            }
            assertEquals(data.length, covered, "主区间并集必须覆盖全文件");
            assertEquals(4, primaries.size()); // 30+30+30+10
        }
    }

    @Test
    public void testPatternCrossingChunkBoundaryFoundOnce() throws Exception {
        byte[] prefix = bytes("0123456789ABCDEF"); // 16 字节
        byte[] pattern = bytes("CROSS");
        byte[] suffix = bytes("GHIJKLMNOPQRSTUVWXYZ");
        byte[] data = new byte[prefix.length + pattern.length + suffix.length];
        System.arraycopy(prefix, 0, data, 0, prefix.length);
        System.arraycopy(pattern, 0, data, prefix.length, pattern.length);
        System.arraycopy(suffix, 0, data, prefix.length + pattern.length, suffix.length);
        // 模式横跨 chunk 边界：前 2 字节在第一块，后 3 字节在第二块
        Path file = write("cross.bin", data);

        List<Long> hits = scan(file, 16, 8, pattern);
        assertEquals(1, hits.size(), "跨界命中必须恰好报告一次，实际 " + hits);
        assertEquals(prefix.length, hits.get(0));
    }

    @Test
    public void testPatternFullyInsideOverlapReportedOnce() throws Exception {
        // chunkSize=16, overlap=12：第二块视图向前延伸 12 字节
        // 构造一个完全落在第二块 overlap 区（绝对偏移 [16-12, 16) 即 [4,16)）内的模式
        byte[] head = bytes("AAAA");
        byte[] pattern = bytes("OVERLAPX"); // 8 字节，绝对位置 4..11，完全在第二块 overlap 区内
        byte[] tail = bytes("BBBBBBBBBBBBBBBBBBBBBBBB"); // 保证第二块存在且有内容
        byte[] data = new byte[head.length + pattern.length + tail.length];
        System.arraycopy(head, 0, data, 0, head.length);
        System.arraycopy(pattern, 0, data, head.length, pattern.length);
        System.arraycopy(tail, 0, data, head.length + pattern.length, tail.length);
        Path file = write("overlap.bin", data);

        List<Long> hits = scan(file, 16, 12, pattern);
        assertEquals(1, hits.size(), "overlap 区内命中必须恰好报告一次，实际 " + hits);
        assertEquals(4, hits.get(0));
    }

    @Test
    public void testInvalidArguments() throws Exception {
        Path file = write("args.bin", bytes("x"));
        assertThrows(IllegalArgumentException.class, () -> new ChunkedFileReader(file, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ChunkedFileReader(file, 16, -1));
        assertThrows(IllegalArgumentException.class, () -> new ChunkedFileReader(file, 8, 16));
    }

    @Test
    public void testAccessAfterCloseThrows() throws Exception {
        Path file = write("closed.bin", bytes("some data here"));
        ChunkedFileReader.Chunk chunk;
        try (ChunkedFileReader reader = new ChunkedFileReader(file, 8, 4)) {
            chunk = reader.nextChunk();
        }
        MemorySegment seg = chunk.segment();
        assertThrows(IllegalStateException.class, () -> seg.get(ValueLayout.JAVA_BYTE, 0));
        // 重复 close 幂等
        ChunkedFileReader reader = new ChunkedFileReader(file, 8, 4);
        reader.close();
        reader.close();
        assertNull(reader.nextChunk());
    }

    /**
     * 消费者侧标准扫描循环：对每块视图执行 BMH 搜索，仅报告主区间内命中（plan 钉死的去重规则）。
     */
    private List<Long> scan(Path file, int chunkSize, int overlap, byte[] pattern) {
        List<Long> hits = new ArrayList<>();
        try (ChunkedFileReader reader = new ChunkedFileReader(file, chunkSize, overlap)) {
            ChunkedFileReader.Chunk chunk;
            while ((chunk = reader.nextChunk()) != null) {
                MemorySegment seg = chunk.segment();
                long searchFrom = 0;
                while (true) {
                    long pos = ScalarSearchBridge.findPattern(seg, searchFrom, chunk.viewLen(), pattern);
                    if (pos < 0) {
                        break;
                    }
                    long absolute = chunk.absoluteOffset(pos);
                    if (chunk.inPrimary(absolute)) {
                        hits.add(absolute);
                    }
                    searchFrom = pos + 1;
                    if (searchFrom + pattern.length > chunk.viewLen()) {
                        break;
                    }
                }
            }
        }
        return hits;
    }

    /**
     * 测试内桥接：避免 io 包依赖 search 包（生产消费者按同一规则自实现调用）。
     */
    private static final class ScalarSearchBridge {
        private static final io.nop.rg.core.search.ByteSearchStrategy SEARCHER =
                io.nop.rg.core.search.ScalarByteSearcher.INSTANCE;

        static long findPattern(MemorySegment seg, long offset, long limit, byte[] pattern) {
            return SEARCHER.findPattern(seg, offset, limit, pattern);
        }
    }
}
