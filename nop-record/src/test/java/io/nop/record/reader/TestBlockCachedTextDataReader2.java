package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.nop.record.RecordErrors.ERR_RECORD_NO_ENOUGH_DATA;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBlockCachedTextDataReader2 {
    private static final String TEST_DATA = StringHelper.repeat("0123456789", 100); // 1000 bytes
    private static final String TEST_DATA_WITH_NEWLINES = "line1\nline2\r\nline3\rline4\n";
    private ITextDataReader underlyingReader;
    private BlockCachedTextDataReader cachedReader;

    @BeforeEach
    void setUp() {
        underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 100); // 使用100字节的块大小
    }

    @AfterEach
    void tearDown() throws IOException {
        if (cachedReader != null) {
            cachedReader.close();
        }
    }

    @Test
    void testReadFullyAcrossBlocks() throws IOException {
        // 测试跨越多个块的读取
        String result = cachedReader.readFully(150);
        assertEquals(TEST_DATA.substring(0, 150), result);
        assertEquals(150, cachedReader.pos());
    }

    @Test
    void testReadFullyPartialWhenEof() throws IOException {
        cachedReader.seek(950);
        // 应该抛出异常而不是返回部分数据
        NopException e = assertThrows(NopException.class, () -> cachedReader.readFully(100));
        assertEquals(ERR_RECORD_NO_ENOUGH_DATA.getErrorCode(), e.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {97, 101, 103, 107})
        // 使用质数作为块大小
    void testPrimeSizedBlocks(int blockSize) throws IOException {
        underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, blockSize);

        // 读取不同长度的数据
        String result1 = cachedReader.readFully(53);
        assertEquals(TEST_DATA.substring(0, 53), result1);

        String result2 = cachedReader.readFully(89);
        assertEquals(TEST_DATA.substring(53, 142), result2);

        cachedReader.seek(500);
        String result3 = cachedReader.readFully(97);
        assertEquals(TEST_DATA.substring(500, 597), result3);
    }

    @Test
    void testSeekBackwardWithinCache() throws IOException {
        // 先读取一些数据建立缓存
        cachedReader.readFully(300);

        // 向后seek应在缓存允许范围内
        cachedReader.seek(250);
        assertEquals(250, cachedReader.pos());
        assertEquals(TEST_DATA.charAt(250), cachedReader.readChar()); // 250是"0123456789"的第0个字符(250%10=0)
    }

    @Test
    void testReadLineAcrossBlocks() throws IOException {
        // 创建跨块的长行数据
        String longLine = StringHelper.repeat("abcde", 50); // 250字符
        String data = longLine + "\nsecond line";
        underlyingReader = new SimpleTextDataReader(data);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 100); // 行跨越多个块

        String line = cachedReader.readLine(500);
        assertEquals(longLine, line);
        assertEquals(251, cachedReader.pos()); // 包括换行符

        line = cachedReader.readLine(100);
        assertEquals("second line", line);
    }

    @Test
    void testReadLineWithCRLFSplitAcrossBlocks() throws IOException {
        // 创建跨块的\r\n换行符
        String part1 = StringHelper.repeat("x", 98); // 使\r位于块末尾
        String part2 = "\nnext line";
        underlyingReader = new SimpleTextDataReader(part1 + "\r" + part2);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 100);

        String line = cachedReader.readLine(200);
        assertEquals(part1, line);
        assertEquals(100, cachedReader.pos()); // 应跳过\r\n

        line = cachedReader.readLine(100);
        assertEquals("next line", line);
    }

    @Test
    void testMixedOperations() throws IOException {
        // 混合多种操作
        cachedReader.readFully(50);
        cachedReader.skip(25);
        assertEquals(75, cachedReader.pos());

        int c = cachedReader.readChar();
        assertEquals('5', (char) c); // 75%10=5
        assertEquals(76, cachedReader.pos());

        String part = cachedReader.tryReadFully(20);
        assertEquals(TEST_DATA.substring(76, 96), part);
        assertEquals(96, cachedReader.pos());

        cachedReader.seek(200);
        assertEquals(200, cachedReader.pos());
    }

    @Test
    void testCacheEviction() throws IOException {
        // 使用小缓存测试缓存淘汰
        underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 100, true, 3); // 设置strictBlockSize=true

        // 读取超过缓存大小的数据
        cachedReader.readFully(400);
        assertEquals(400, cachedReader.pos());

        // 验证第一个块已被淘汰
        assertTrue(cachedReader.getFirstCachedPosition() > 99,
                "First block should have been evicted");

        // 尝试seek到已被淘汰的块应该抛出异常
        assertThrows(NopException.class, () -> cachedReader.seek(99),
                "Should throw when seeking to evicted position");

        // 但可以seek到仍在缓存中的位置
        assertDoesNotThrow(() -> cachedReader.seek(350));
    }

    @Test
    void testRandomAccessPattern() throws IOException {
        // 模拟随机访问模式
        Random random = new Random(42);
        List<Long> positions = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            positions.add(random.nextInt(800) + 100L);
        }

        for (Long pos : positions) {
            cachedReader.seek(pos);
            assertEquals((char) ('0' + (pos % 10)), (char) cachedReader.readChar());
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
            String data = cachedReader.readFully(Math.min(requestLen, expectedAvailable));
            assertEquals(Math.min(requestLen, expectedAvailable), data.length());
        }
    }

    @Test
    void testDetach() throws IOException {
        cachedReader.readFully(100);
        long pos = cachedReader.pos();

        ITextDataReader detached = cachedReader.detach();
        assertNotSame(cachedReader, detached);
        assertEquals(pos, detached.pos());

        // 原始reader应仍可用
        assertEquals(TEST_DATA.substring(100, 110), cachedReader.readFully(10));

        // 分离后的reader独立操作
        assertEquals(TEST_DATA.substring(100, 110), detached.readFully(10));
    }

    @Test
    void testLargeFileSimulation() throws IOException {
        // 模拟大文件处理
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeData.append(String.format("%04d", i)); // 每行4位数字
            if (i % 10 == 9) {
                largeData.append("\n");
            }
        }

        underlyingReader = new SimpleTextDataReader(largeData.toString());
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 512); // 中等块大小

        int lineCount = 0;
        String line;
        while ((line = cachedReader.readLine(100)) != null) {
            assertTrue(line.length() <= 40); // 最长40字符(10个4位数字)
            lineCount++;
        }

        assertEquals(1000, lineCount); // 10000/10=1000行
    }
}