package io.nop.record.reader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 5.4-C1-005：RandomAccessFile reader 的 detach/duplicate 必须返回与父实例读位置互不影响、
 * 生命周期独立的视图（对齐 ByteBuffer/BlockCached 家族语义）。
 */
public class TestRafDetachIndependence_C1_005 {
    File file;

    @BeforeEach
    public void setUp() throws Exception {
        file = File.createTempFile("c1-005-", ".bin");
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        Files.write(file.toPath(), data);
    }

    @AfterEach
    public void tearDown() {
        file.delete();
    }

    @Test
    public void testDetachedReadDoesNotMoveParent() throws Exception {
        try (RandomAccessFileBinaryDataReader parent = new RandomAccessFileBinaryDataReader(file)) {
            parent.readBytes(10);
            IBinaryDataReader child = parent.detach();
            try {
                assertEquals(10, child.read());
                child.readBytes(19);

                assertEquals(10, parent.pos());
                assertEquals(10, parent.read());
            } finally {
                child.close();
            }
        }
    }

    @Test
    public void testDuplicateReadDoesNotMoveParent() throws Exception {
        try (RandomAccessFileBinaryDataReader parent = new RandomAccessFileBinaryDataReader(file)) {
            parent.readBytes(5);
            IBinaryDataReader dup = parent.duplicate();
            try {
                dup.readBytes(40);
                assertEquals(5, parent.pos());
                assertEquals(5, parent.read());
            } finally {
                dup.close();
            }
        }
    }

    // 父子交错读：两条读序列各自连续
    @Test
    public void testInterleavedReadsStayIndependent() throws Exception {
        try (RandomAccessFileBinaryDataReader parent = new RandomAccessFileBinaryDataReader(file)) {
            parent.readBytes(20);
            IBinaryDataReader child = parent.detach();
            try {
                for (int i = 0; i < 10; i++) {
                    assertEquals(20 + i, parent.read());
                    assertEquals(20 + i, child.read());
                }
                assertEquals(30, parent.pos());
            } finally {
                child.close();
            }
        }
    }

    @Test
    public void testClosingChildKeepsParentReadable() throws Exception {
        try (RandomAccessFileBinaryDataReader parent = new RandomAccessFileBinaryDataReader(file)) {
            IBinaryDataReader child = parent.detach();
            child.read();
            child.close();
            assertEquals(0, parent.read());
        }
    }
}
