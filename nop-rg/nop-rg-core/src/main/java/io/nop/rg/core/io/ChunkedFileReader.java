package io.nop.rg.core.io;

import io.nop.rg.core.NopRgException;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 大文件分块内存映射读取器（plan 2263 IO-02）。
 *
 * <p>分块语义（plan 钉死，实现中修正为双向 overlap）：主区间无缝平铺——第 i 块主区间为
 * {@code [i*chunkSize, i*chunkSize + primaryLen)}，末块 primaryLen 延伸到文件尾；
 * 每块映射视图向主区间两侧各扩展 overlap 字节
 * （viewStart = max(0, chunkStart - overlap)，viewEnd = min(fileSize, chunkStart + primaryLen + overlap)），
 * 保证起点落在本块主区间内、长度 ≤ overlap+1 的模式完整可见——
 * 包括恰好起始于主区间末端、向后跨出主区间的模式。
 *
 * <p>去重归消费者：只报告匹配起点落在主区间内（chunkStart &lt;= matchStart &lt; chunkStart+primaryLen）的命中，
 * 恰好落在 overlap 区的命中由下一块的主区间覆盖报告，全局恰好一次。
 */
public final class ChunkedFileReader implements AutoCloseable {
    public static final int DEFAULT_CHUNK_SIZE = 256 * 1024 * 1024;
    public static final int DEFAULT_OVERLAP = 1023; // 覆盖 ≥1024 字节模式的跨界可见性

    private final FileChannel channel;
    private final Arena arena;
    private final long fileSize;
    private final int chunkSize;
    private final int overlap;
    private long nextStart;
    private boolean closed;

    public ChunkedFileReader(Path file) {
        this(file, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    /**
     * @param chunkSize 主区间长度（测试可注入小值）；末块不足时按剩余字节收缩
     * @param overlap   视图向前扩展的字节数（≤ chunkSize），用于跨界模式可见性
     */
    public ChunkedFileReader(Path file, int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap > chunkSize) {
            throw new IllegalArgumentException("overlap must be in [0, chunkSize]");
        }
        try {
            this.channel = FileChannel.open(file, StandardOpenOption.READ);
            this.arena = Arena.ofConfined();
            boolean ok = false;
            try {
                this.fileSize = channel.size();
                this.chunkSize = chunkSize;
                this.overlap = overlap;
                ok = true;
            } finally {
                if (!ok) {
                    closeChannelAndArena();
                }
            }
        } catch (IOException e) {
            throw new NopRgException("open file failed: " + file, e);
        }
    }

    public long getFileSize() {
        return fileSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * 返回下一分块视图；遍历完毕返回 null。空文件直接返回 null。
     */
    public Chunk nextChunk() {
        if (closed || nextStart >= fileSize) {
            return null;
        }
        long chunkStart = nextStart;
        long primaryLen = Math.min(chunkSize, fileSize - chunkStart);
        long viewStart = Math.max(0, chunkStart - overlap);
        long viewEnd = Math.min(fileSize, chunkStart + primaryLen + (long) overlap);
        long viewLen = viewEnd - viewStart;
        try {
            MemorySegment segment = channel.map(FileChannel.MapMode.READ_ONLY, viewStart, viewLen, arena);
            nextStart = chunkStart + primaryLen;
            return new Chunk(chunkStart, primaryLen, viewStart, viewLen, segment);
        } catch (IOException e) {
            throw new NopRgException("map chunk failed at " + chunkStart, e);
        }
    }

    @Override
    public void close() {
        closeChannelAndArena();
    }

    private void closeChannelAndArena() {
        if (closed) {
            return;
        }
        closed = true;
        arena.close();
        try {
            channel.close();
        } catch (IOException e) {
            throw new NopRgException("close file channel failed", e);
        }
    }

    /**
     * 单个分块视图：主区间 + 前向 overlap 扩展。
     */
    public record Chunk(long chunkStart, long primaryLen, long viewStart, long viewLen, MemorySegment segment) {
        /**
         * 匹配起点（segment 内相对偏移）换算为文件内绝对偏移。
         */
        public long absoluteOffset(long offsetInSegment) {
            return viewStart + offsetInSegment;
        }

        /**
         * 文件内绝对偏移是否落在本块主区间内（消费者去重判据）。
         */
        public boolean inPrimary(long absoluteOffset) {
            return absoluteOffset >= chunkStart && absoluteOffset < chunkStart + primaryLen;
        }
    }
}
