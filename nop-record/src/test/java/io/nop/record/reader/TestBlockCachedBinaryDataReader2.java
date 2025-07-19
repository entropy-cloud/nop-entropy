package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBlockCachedBinaryDataReader2 {
    private static final byte[] TEST_DATA = createTestData(1000); // 1000 bytes
    private static final byte[] TEST_DATA_WITH_MARKERS = createTestDataWithMarkers();
    private IBinaryDataReader underlyingReader;
    private BlockCachedBinaryDataReader cachedReader;

    private static byte[] createTestData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 10);
        }
        return data;
    }

    private static byte[] createTestDataWithMarkers() {
        ByteBuffer buffer = ByteBuffer.allocate(100);
        buffer.put((byte) 0x01).put((byte) 0x02).put((byte) 0x03).put((byte) 0x04); // marker
        buffer.put(createTestData(96)); // fill rest with test pattern
        return buffer.array();
    }

    @BeforeEach
    void setUp() {
        underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 100); // 100-byte block size
    }

    @AfterEach
    void tearDown() throws IOException {
        if (cachedReader != null) {
            cachedReader.close();
        }
    }

    @Test
    void testReadFullyAcrossBlocks() throws IOException {
        // Test reading across multiple blocks
        byte[] result = cachedReader.readFully(150);
        assertArrayEquals(getSubArray(TEST_DATA, 0, 150), result);
        assertEquals(150, cachedReader.pos());
    }

    @Test
    void testReadFullyPartialWhenEof() throws IOException {
        cachedReader.seek(950);
        // Should throw exception rather than return partial data
        EOFException e = assertThrows(EOFException.class, () -> cachedReader.readFully(100));
    }

    @ParameterizedTest
    @ValueSource(ints = {97, 101, 103, 107})
        // Use prime numbers as block sizes
    void testPrimeSizedBlocks(int blockSize) throws IOException {
        underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, blockSize);

        // Read data of different lengths
        byte[] result1 = cachedReader.readFully(53);
        assertArrayEquals(getSubArray(TEST_DATA, 0, 53), result1);

        byte[] result2 = cachedReader.readFully(89);
        assertArrayEquals(getSubArray(TEST_DATA, 53, 89), result2);

        cachedReader.seek(500);
        byte[] result3 = cachedReader.readFully(97);
        assertArrayEquals(getSubArray(TEST_DATA, 500, 97), result3);
    }

    @Test
    void testSeekBackwardWithinCache() throws IOException {
        // First read some data to establish cache
        cachedReader.readFully(300);

        // Seeking backward should work within cache limits
        cachedReader.seek(250);
        assertEquals(250, cachedReader.pos());
        assertEquals(TEST_DATA[250], cachedReader.readByte()); // 250 % 10 = 0
    }

    @Test
    void testReadBytesAcrossBlocks() throws IOException {
        // Create data with marker bytes spanning blocks
        byte[] longData = new byte[250];
        System.arraycopy(TEST_DATA_WITH_MARKERS, 0, longData, 0, 4); // marker at start
        System.arraycopy(TEST_DATA, 0, longData, 4, 246); // fill rest

        underlyingReader = new ByteBufferBinaryDataReader(longData);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 100); // data spans blocks

        byte[] result = cachedReader.readFully(150);
        assertArrayEquals(getSubArray(longData, 0, 150), result);
        assertEquals(150, cachedReader.pos());
    }

    @Test
    void testReadIntegersAcrossBlocks() throws IOException {
        // Create test data with integers spanning block boundaries
        ByteBuffer buffer = ByteBuffer.allocate(200).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < 50; i++) {
            buffer.putInt(i);
        }
        byte[] intData = buffer.array();

        underlyingReader = new ByteBufferBinaryDataReader(intData);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 100); // block boundary at 100

        // Read first integer (should be in first block)
        assertEquals(0, cachedReader.readInt());

        // Read integer at block boundary (bytes 96-100)
        cachedReader.seek(96);
        assertEquals(24, cachedReader.readInt()); // 96/4=24
    }

    @Test
    void testMixedOperations() throws IOException {
        // Mix different operations
        cachedReader.readFully(50);
        cachedReader.skip(25);
        assertEquals(75, cachedReader.pos());

        byte b = cachedReader.readByte();
        assertEquals((byte) 5, b); // 75 % 10 = 5
        assertEquals(76, cachedReader.pos());

        byte[] part = cachedReader.tryReadFully(20);
        assertArrayEquals(getSubArray(TEST_DATA, 76, 20), part);
        assertEquals(96, cachedReader.pos());

        cachedReader.seek(200);
        assertEquals(200, cachedReader.pos());
    }

    @Test
    void testCacheEviction() throws IOException {
        // Test cache eviction with small cache
        underlyingReader = new ByteBufferBinaryDataReader(TEST_DATA);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 100, true, 3); // strictBlockSize=true

        // Read more data than cache can hold
        cachedReader.readFully(400);
        assertEquals(400, cachedReader.pos());

        // Verify first block has been evicted
        assertTrue(cachedReader.getFirstCachedPosition() > 99,
                "First block should have been evicted");

        // Attempting to seek to evicted block should throw
        assertThrows(NopException.class, () -> cachedReader.seek(99),
                "Should throw when seeking to evicted position");

        // But can seek to positions still in cache
        assertDoesNotThrow(() -> cachedReader.seek(350));
    }

    @Test
    void testRandomAccessPattern() throws IOException {
        // Simulate random access pattern
        Random random = new Random(42);
        List<Long> positions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            positions.add(random.nextInt(800) + 100L);
        }

        for (Long pos : positions) {
            cachedReader.seek(pos);
            assertEquals((byte) (pos % 10), cachedReader.readByte());
        }
    }

    @ParameterizedTest
    @CsvSource({
            "0, 1000, 1000",
            "500, 500, 500",
            "900, 200, 100",
            "1000, 100, 0"
    })
    void testAvailable(long seekPos, int requestLen, int expectedAvailable) throws IOException {
        cachedReader.seek(seekPos);
        assertEquals(expectedAvailable, cachedReader.available());

        if (requestLen > 0) {
            byte[] data = cachedReader.readFully(Math.min(requestLen, expectedAvailable));
            assertEquals(Math.min(requestLen, expectedAvailable), data.length);
        }
    }

    @Test
    void testDetach() throws IOException {
        cachedReader.readFully(100);
        long pos = cachedReader.pos();

        IBinaryDataReader detached = cachedReader.detach();
        assertNotSame(cachedReader, detached);
        assertEquals(pos, detached.pos());

        // Original reader should still work
        assertArrayEquals(getSubArray(TEST_DATA, 100, 10), cachedReader.readFully(10));

        // Detached reader operates independently
        assertArrayEquals(getSubArray(TEST_DATA, 100, 10), detached.readFully(10));
    }

    @Test
    void testLargeFileSimulation() throws IOException {
        // Simulate large file processing
        ByteBuffer largeData = ByteBuffer.allocate(40000); // 40KB
        for (int i = 0; i < 10000; i++) {
            largeData.putInt(i); // each record is 4 bytes
        }
        byte[] largeArray = largeData.array();

        underlyingReader = new ByteBufferBinaryDataReader(largeArray);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 512); // medium block size

        // Read all integers and verify
        for (int i = 0; i < 10000; i++) {
            assertEquals(i, cachedReader.readInt());
        }
    }

    @Test
    void testReadBits() throws IOException {
        // Test bit-level reading
        byte[] bitData = new byte[]{(byte) 0b10101010, (byte) 0b11001100};
        underlyingReader = new ByteBufferBinaryDataReader(bitData);
        cachedReader = new BlockCachedBinaryDataReader(underlyingReader, 2);

        // Read 3 bits (101)
        assertEquals(0b101, cachedReader.readBitsIntBe(3));
        assertEquals(5, cachedReader.getBitsLeft());

        // Read 5 bits (01010)
        assertEquals(0b01010, cachedReader.readBitsIntBe(5));
        assertEquals(0, cachedReader.getBitsLeft());

        // Read next byte (11001100)
        assertEquals(0b11001100, cachedReader.readByte() & 0xFF);
    }

    private byte[] getSubArray(byte[] source, int offset, int length) {
        byte[] result = new byte[length];
        System.arraycopy(source, offset, result, 0, length);
        return result;
    }
}