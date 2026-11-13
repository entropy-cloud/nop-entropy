package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.4-C6-001：构造器在打开文件后取长度失败时必须关闭已打开句柄（构造泄漏族，fd 计数口径沿 TestFileDownloadSinkClose）。
 */
public class TestRafConstructorLeak_C6_001 {
    static final int ATTEMPTS = 40;
    static final int MAX_FD_GROWTH = 10;

    File file;

    @BeforeEach
    public void setUp() throws Exception {
        file = File.createTempFile("c6-001-", ".bin");
        Files.write(file.toPath(), new byte[16]);
    }

    @AfterEach
    public void tearDown() {
        file.delete();
    }

    static boolean openFileDescriptorCountAvailable() {
        try {
            Class.forName("com.sun.management.UnixOperatingSystemMXBean");
            return ManagementFactory.getOperatingSystemMXBean()
                    instanceof com.sun.management.UnixOperatingSystemMXBean;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static long openFdCount() {
        return ((com.sun.management.UnixOperatingSystemMXBean) ManagementFactory
                .getOperatingSystemMXBean()).getOpenFileDescriptorCount();
    }

    static class FailingSizeReader extends RandomAccessFileBinaryDataReader {
        FailingSizeReader(File file) {
            super(file);
        }

        @Override
        protected long initSize(RandomAccessFile raf) throws IOException {
            throw new IOException("injected length failure");
        }
    }

    @Test
    @EnabledIf("openFileDescriptorCountAvailable")
    public void testSizeFailureDoesNotLeakFileDescriptor() {
        for (int i = 0; i < 3; i++) {
            assertThrows(NopException.class, () -> new FailingSizeReader(file));
        }
        long before = openFdCount();
        for (int i = 0; i < ATTEMPTS; i++) {
            assertThrows(NopException.class, () -> new FailingSizeReader(file));
        }
        long growth = openFdCount() - before;
        assertTrue(growth <= MAX_FD_GROWTH, "fd growth after failed constructions: " + growth);
    }

    @Test
    public void testCloseIsIdempotent() throws Exception {
        RandomAccessFileBinaryDataReader reader = new RandomAccessFileBinaryDataReader(file);
        assertEquals(16, reader.size());
        reader.close();
        reader.close();
    }
}
