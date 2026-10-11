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
