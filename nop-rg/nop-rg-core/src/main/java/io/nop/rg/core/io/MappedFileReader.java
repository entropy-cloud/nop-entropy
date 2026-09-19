package io.nop.rg.core.io;

import io.nop.rg.core.NopRgException;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 单次内存映射只读文件读取器（FFM API，plan 2263 IO-01）。
 *
 * <p>try-with-resources 确定性释放：{@link #close()} 关闭 Arena 即解除映射。
 * 关闭后访问 segment 抛 IllegalStateException。
 */
public final class MappedFileReader implements AutoCloseable {
    private final FileChannel channel;
    private final Arena arena;
    private final MemorySegment segment;
    private final long size;
    private boolean closed;

    public MappedFileReader(Path file) {
        try {
            this.channel = FileChannel.open(file, StandardOpenOption.READ);
            this.arena = Arena.ofConfined();
            boolean ok = false;
            try {
                this.size = channel.size();
                this.segment = size == 0 ? null : channel.map(FileChannel.MapMode.READ_ONLY, 0, size, arena);
                ok = true;
            } finally {
                if (!ok) {
                    closeChannelAndArena();
                }
            }
        } catch (IOException e) {
            throw new NopRgException("map file failed: " + file, e);
        }
    }

    public long getSize() {
        return size;
    }

    public MemorySegment getSegment() {
        return segment;
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
}
