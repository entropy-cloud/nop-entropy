package io.nop.rg.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MappedFileReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String name, byte[] data) throws Exception {
        Path p = tempDir.resolve(name);
        Files.write(p, data);
        return p;
    }

    @Test
    public void testSmallFileMappedContent() throws Exception {
        byte[] data = "hello mmap".getBytes(StandardCharsets.UTF_8);
        Path file = write("small.txt", data);
        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals(data.length, reader.getSize());
            MemorySegment seg = reader.getSegment();
            assertNotNull(seg);
            for (int i = 0; i < data.length; i++) {
                assertEquals(data[i], seg.get(ValueLayout.JAVA_BYTE, i));
            }
        }
    }

    @Test
    public void testEmptyFile() throws Exception {
        Path file = write("empty.txt", new byte[0]);
        try (MappedFileReader reader = new MappedFileReader(file)) {
            assertEquals(0, reader.getSize());
            assertNull(reader.getSegment());
        }
    }

    @Test
    public void testAdviseSequentialSmoke() throws Exception {
        // Wave 5 item 17（plan 2574 Phase A）：每次 open 都经过 adviseSequential；
        // macOS 平台裁定（rg mmap.rs:73-79 同因）：madvise 仅 Linux 启用，本机 macOS
        // 上该分支静默跳过——本用例钉住 best-effort advice 不改变 open/read/close 行为。
        byte[] data = "advise smoke".getBytes(StandardCharsets.UTF_8);
        Path file = write("advise.txt", data);
        for (int i = 0; i < 3; i++) {
            try (MappedFileReader reader = new MappedFileReader(file)) {
                assertEquals(data.length, reader.getSize());
                MemorySegment seg = reader.getSegment();
                assertEquals(data[0], seg.get(ValueLayout.JAVA_BYTE, 0));
            }
        }
    }

    @Test
    public void testAccessAfterCloseThrows() throws Exception {
        Path file = write("closed.txt", "data".getBytes(StandardCharsets.UTF_8));
        MemorySegment seg;
        try (MappedFileReader reader = new MappedFileReader(file)) {
            seg = reader.getSegment();
        }
        // Arena 已关闭：访问已映射内存抛 IllegalStateException
        assertThrows(IllegalStateException.class, () -> seg.get(ValueLayout.JAVA_BYTE, 0));
        // 重复 close 幂等
        try (MappedFileReader reader = new MappedFileReader(file)) {
            reader.close();
            reader.close();
        }
    }
}
