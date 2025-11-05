package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.bytes.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class TestBlockCachedBinaryDataReader {
    private static final byte[] TEST_DATA = createTestData(); // 500 bytes
    private BlockCachedBinaryDataReader cachedReader;

    private static byte[] createTestData() {
        byte[] data = new byte[500];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 10);
        }
        return data;
    }

    @BeforeEach
    void setUp() throws IOException {
        // Initialize underlying reader and cached reader
        IBinaryDataReader underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 100); // Use 100-byte block size
    }

    @AfterEach
    void tearDown() throws IOException {
        if (cachedReader != null) {
            cachedReader.close();
        }
    }

    @Test
    void testReadFully() throws IOException {
        // Read first 50 bytes
        byte[] result = cachedReader.readFully(50);
        assertArrayEquals(getSubArray(TEST_DATA, 0, 50), result);
        assertEquals(50, cachedReader.pos());

        // Read next 50 bytes
        result = cachedReader.readFully(50);
        assertArrayEquals(getSubArray(TEST_DATA, 50, 50), result);
        assertEquals(100, cachedReader.pos());
    }

    @Test
    void testTryRead() throws IOException {
        // Try to read 20 bytes
        byte[] result = cachedReader.tryReadFully(20);
        assertArrayEquals(getSubArray(TEST_DATA, 0, 20), result);
        assertEquals(20, cachedReader.pos());

        // Try to read more bytes than remaining data
        cachedReader.seek(490);
        result = cachedReader.tryReadFully(20);
        assertArrayEquals(getSubArray(TEST_DATA, 490, 10), result);
        assertEquals(500, cachedReader.pos());
    }

    @Test
    void testReadByte() throws IOException {
        // Read bytes one by one
        for (int i = 0; i < 10; i++) {
            byte b = cachedReader.readByte();
            assertEquals(TEST_DATA[i], b);
            assertEquals(i + 1, cachedReader.pos());
        }
    }

    @Test
    void testSeek() throws IOException {
        // Read some data initially to establish cache window
        cachedReader.readFully(100);
        long firstCachedPos = cachedReader.getFirstCachedPosition();
        assertTrue(firstCachedPos <= 100); // Ensure there is cached data

        // 1. Test normal seek
        cachedReader.seek(50);
        assertEquals(50, cachedReader.pos());

        // 2. Test seek to current cache boundary
        cachedReader.seek(firstCachedPos);
        assertEquals(firstCachedPos, cachedReader.pos());

        // 3. Test seek backward beyond cache window
        assertThrows(IllegalArgumentException.class, () -> {
            cachedReader.seek(firstCachedPos - 1);
        });

        // 4. Test seek forward to load new data
        cachedReader.seek(200);
        assertEquals(200, cachedReader.pos());

        // 5. Test seek to end of file
        cachedReader.seek(500);
        assertEquals(500, cachedReader.pos());

        // 6. Test seek beyond file size
        assertThrows(IOException.class, () -> {
            cachedReader.seek(501);
        });
    }

    @Test
    void testSkip() throws IOException {
        // Skip first 100 bytes
        cachedReader.skip(100);
        assertEquals(100, cachedReader.pos());

        // Read next 10 bytes to verify
        byte[] result = cachedReader.readFully(10);
        assertArrayEquals(getSubArray(TEST_DATA, 100, 10), result);

        // Try to skip beyond max allowed distance
        assertThrows(NopException.class, () -> cachedReader.skip(1024 * 1024 + 1));
    }

    @Test
    void testIsEof() throws IOException {
        assertFalse(cachedReader.isEof());

        // Move to end of file
        cachedReader.seek(500);
        assertTrue(cachedReader.isEof());

        // Move to near end
        cachedReader.seek(499);
        assertFalse(cachedReader.isEof());
        assertEquals(TEST_DATA[499], cachedReader.readByte()); // Read last byte
        assertTrue(cachedReader.isEof());
    }

    @Test
    void testAvailable() throws IOException {
        assertEquals(500, cachedReader.available());

        // Read some data and check remaining available
        cachedReader.readFully(100);
        assertEquals(400, cachedReader.available());

        // Move to end
        cachedReader.seek(500);
        assertEquals(0, cachedReader.available());
    }

    @Test
    void testReset() throws IOException {
        // Read some data then reset
        cachedReader.readFully(100);
        cachedReader.reset();

        assertEquals(0, cachedReader.pos());
        assertArrayEquals(getSubArray(TEST_DATA, 0, 10), cachedReader.readFully(10));
    }

    @Test
    void testDetach() throws IOException {
        BlockCachedBinaryDataReader detachedReader = (BlockCachedBinaryDataReader) cachedReader.detach();

        // Verify detached reader can operate independently
        detachedReader.readFully(100);
        assertEquals(100, detachedReader.pos());
        assertEquals(0, cachedReader.pos()); // Original reader unaffected

        detachedReader.close();
    }

    @Test
    void testPrimeSizedBlocks() throws IOException {
        // Use prime number as block size
        IBinaryDataReader underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 97); // 97 is prime

        // Read data of different lengths, including prime lengths
        byte[] result = cachedReader.readFully(53); // 53 is prime
        assertArrayEquals(getSubArray(TEST_DATA, 0, 53), result);
        assertEquals(53, cachedReader.pos());

        // Read data spanning two blocks
        result = cachedReader.readFully(89); // 89 is prime
        assertArrayEquals(getSubArray(TEST_DATA, 53, 89), result);
        assertEquals(142, cachedReader.pos());

        // Read data that doesn't completely fill a block
        result = cachedReader.readFully(23); // 23 is prime
        assertArrayEquals(getSubArray(TEST_DATA, 142, 23), result);
        assertEquals(165, cachedReader.pos());
    }

    @Test
    void testMixedReadOperationsWithPrimes() throws IOException {
        // Use prime block size
        IBinaryDataReader underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 127); // 127 is prime

        // Mix different read methods
        byte[] result1 = cachedReader.readFully(31); // 31 is prime
        assertArrayEquals(getSubArray(TEST_DATA, 0, 31), result1);

        byte[] result2 = cachedReader.tryReadFully(17); // 17 is prime
        assertArrayEquals(getSubArray(TEST_DATA, 31, 17), result2);

        // Skip a prime-length amount of data
        cachedReader.skip(13); // 13 is prime
        assertEquals(61, cachedReader.pos());

        // Read single byte
        byte b = cachedReader.readByte();
        assertEquals(TEST_DATA[61], b);
        assertEquals(62, cachedReader.pos());

        // Read data spanning block boundary
        byte[] result3 = cachedReader.readFully(71); // 71 is prime
        assertArrayEquals(getSubArray(TEST_DATA, 62, 71), result3);
        assertEquals(133, cachedReader.pos());
    }

    @Test
    void testEdgeCasesWithPrimeSizes() throws IOException {
        // Use small prime block size
        IBinaryDataReader underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 11); // 11 is prime

        // Test reading exactly filling one block
        byte[] result = cachedReader.readFully(11);
        assertArrayEquals(getSubArray(TEST_DATA, 0, 11), result);
        assertEquals(11, cachedReader.pos());

        // Test reading length one less than block size
        result = cachedReader.readFully(10);
        assertArrayEquals(getSubArray(TEST_DATA, 11, 10), result);
        assertEquals(21, cachedReader.pos());

        // Test reading length one more than block size
        result = cachedReader.readFully(12);
        assertArrayEquals(getSubArray(TEST_DATA, 21, 12), result);
        assertEquals(33, cachedReader.pos());

        // Test reading all remaining data
        result = cachedReader.readFully(500 - 33);
        assertArrayEquals(getSubArray(TEST_DATA, 33, 500 - 33), result);
        assertEquals(500, cachedReader.pos());
    }

    @Test
    void testSeekWithPrimeSizes() throws IOException {
        // Use prime block size
        IBinaryDataReader underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 83); // 83 is prime

        // 1. Test seek to prime position
        cachedReader.seek(79); // 79 is prime
        assertEquals(79, cachedReader.pos());
        assertEquals(TEST_DATA[79], cachedReader.readByte());

        // 2. Test seek to block boundary
        cachedReader.seek(83);
        assertEquals(83, cachedReader.pos());
        assertArrayEquals(getSubArray(TEST_DATA, 83, 10), cachedReader.readFully(10));

        // 3. Test seek to prime position near end of file
        cachedReader.seek(491); // 491 is prime
        assertEquals(491, cachedReader.pos());
        assertArrayEquals(getSubArray(TEST_DATA, 491, 9), cachedReader.readFully(9));
    }

    @Test
    void testReadIntegers() throws IOException {
        // Create test data with known integer values
        byte[] intData = new byte[16];
        ByteBuffer.wrap(intData).order(ByteOrder.BIG_ENDIAN)
                .putInt(0x01020304)
                .putInt(0x05060708)
                .putInt(0x090A0B0C)
                .putInt(0x0D0E0F10);
        
        IBinaryDataReader underlyingReader = new ByteBufferBinaryDataReader(intData);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 8); // Small block size
        
        assertEquals(0x01020304, cachedReader.readInt());
        assertEquals(0x05060708, cachedReader.readInt());
        assertEquals(0x090A0B0C, cachedReader.readInt());
        assertEquals(0x0D0E0F10, cachedReader.readInt());
    }

    private byte[] getSubArray(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }
}