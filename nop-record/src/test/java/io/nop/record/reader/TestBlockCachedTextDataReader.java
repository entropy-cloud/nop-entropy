package io.nop.record.reader;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestBlockCachedTextDataReader {
    private static final String TEST_DATA = StringHelper.repeat("0123456789", 50); // 500 bytes
    private File testFile;
    private BlockCachedTextDataReader cachedReader;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // 初始化底层reader和缓存reader
        ITextDataReader underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 100); // 使用100字节的块大小
    }

    @AfterEach
    void tearDown() throws IOException {
        if (cachedReader != null) {
            cachedReader.close();
        }
    }

    @Test
    void testReadFully() throws IOException {
        // 读取前50个字节
        String result = cachedReader.readFully(50);
        assertEquals(TEST_DATA.substring(0, 50), result);
        assertEquals(50, cachedReader.pos());

        // 读取接下来的50个字节
        result = cachedReader.readFully(50);
        assertEquals(TEST_DATA.substring(50, 100), result);
        assertEquals(100, cachedReader.pos());
    }

    @Test
    void testTryRead() throws IOException {
        // 尝试读取20个字节
        String result = cachedReader.tryReadFully(20);
        assertEquals(TEST_DATA.substring(0, 20), result);
        assertEquals(20, cachedReader.pos());

        // 尝试读取比剩余数据更多的字节
        cachedReader.seek(490);
        result = cachedReader.tryReadFully(20);
        assertEquals(TEST_DATA.substring(490), result);
        assertEquals(500, cachedReader.pos());
    }

    @Test
    void testReadChar() throws IOException {
        // 逐个字符读取
        for (int i = 0; i < 10; i++) {
            int c = cachedReader.readChar();
            assertEquals(TEST_DATA.charAt(i), (char) c);
            assertEquals(i + 1, cachedReader.pos());
        }
    }

    @Test
    void testReadLine() throws IOException {
        // 创建带换行符的测试数据
        String dataWithNewlines = "line1\nline2\r\nline3\rline4";
        ITextDataReader underlyingReader = new SimpleTextDataReader(dataWithNewlines);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 10); // 小块大小测试边界情况

        assertEquals("line1", cachedReader.readLine(100));
        assertEquals("line2", cachedReader.readLine(100));
        assertEquals("line3", cachedReader.readLine(100));
        assertEquals("line4", cachedReader.readLine(100));
    }

    @Test
    void testSeek() throws IOException {
        // 初始读取一些数据以建立缓存窗口
        cachedReader.readFully(100);
        long firstCachedPos = cachedReader.getFirstCachedPosition();
        assertTrue(firstCachedPos <= 100); // 确保有缓存数据

        // 1. 测试正常seek
        cachedReader.seek(50);
        assertEquals(50, cachedReader.pos());

        // 2. 测试seek到当前缓存边界
        cachedReader.seek(firstCachedPos);
        assertEquals(firstCachedPos, cachedReader.pos());

        // 3. 测试向后seek超出缓存窗口
        assertThrows(IllegalArgumentException.class, () -> {
            cachedReader.seek(firstCachedPos - 1);
        });

        // 4. 测试向前seek加载新数据
        cachedReader.seek(200);
        assertEquals(200, cachedReader.pos());

        // 5. 测试seek到文件末尾
        cachedReader.seek(500);
        assertEquals(500, cachedReader.pos());

        // 6. 测试seek超出文件大小
        assertThrows(IOException.class, () -> {
            cachedReader.seek(501);
        });
    }


    @Test
    void testSkip() throws IOException {
        // 跳过前100个字节
        cachedReader.skip(100);
        assertEquals(100, cachedReader.pos());

        // 读取接下来的10个字节验证
        String result = cachedReader.readFully(10);
        assertEquals(TEST_DATA.substring(100, 110), result);

        // 尝试跳过超出最大允许距离
        assertThrows(NopException.class, () -> cachedReader.skip(1024 * 1024 + 1));
    }

    @Test
    void testIsEof() throws IOException {
        assertFalse(cachedReader.isEof());

        // 移动到文件末尾
        cachedReader.seek(500);
        assertTrue(cachedReader.isEof());

        // 移动到接近末尾
        cachedReader.seek(499);
        assertFalse(cachedReader.isEof());
        assertEquals('9', cachedReader.readChar()); // 读取最后一个字符
        assertTrue(cachedReader.isEof());
    }

    @Test
    void testAvailable() throws IOException {
        assertEquals(500, cachedReader.available());

        // 读取部分数据后检查剩余可用量
        cachedReader.readFully(100);
        assertEquals(400, cachedReader.available());

        // 移动到末尾
        cachedReader.seek(500);
        assertEquals(0, cachedReader.available());
    }

    @Test
    void testReset() throws IOException {
        // 读取部分数据后重置
        cachedReader.readFully(100);
        cachedReader.reset();

        assertEquals(0, cachedReader.pos());
        assertEquals(TEST_DATA.substring(0, 10), cachedReader.readFully(10));
    }

    @Test
    void testDetach() throws IOException {
        BlockCachedTextDataReader detachedReader = (BlockCachedTextDataReader) cachedReader.detach();

        // 验证分离后的reader可以独立操作
        detachedReader.readFully(100);
        assertEquals(100, detachedReader.pos());
        assertEquals(0, cachedReader.pos()); // 原reader不受影响

        detachedReader.close();
    }

    @Test
    void testPrimeSizedBlocks() throws IOException {
        // 使用质数作为块大小
        ITextDataReader underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 97); // 97是质数

        // 读取不同长度的数据，包括质数长度
        String result = cachedReader.readFully(53); // 53是质数
        assertEquals(TEST_DATA.substring(0, 53), result);
        assertEquals(53, cachedReader.pos());

        // 读取跨越两个块的数据
        result = cachedReader.readFully(89); // 89是质数
        assertEquals(TEST_DATA.substring(53, 142), result);
        assertEquals(142, cachedReader.pos());

        // 读取不完全填满块的数据
        result = cachedReader.readFully(23); // 23是质数
        assertEquals(TEST_DATA.substring(142, 165), result);
        assertEquals(165, cachedReader.pos());
    }

    @Test
    void testMixedReadOperationsWithPrimes() throws IOException {
        // 使用质数块大小
        ITextDataReader underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 127); // 127是质数

        // 混合使用不同读取方法
        String result1 = cachedReader.readFully(31); // 31是质数
        assertEquals(TEST_DATA.substring(0, 31), result1);

        String result2 = cachedReader.tryReadFully(17); // 17是质数
        assertEquals(TEST_DATA.substring(31, 48), result2);

        // 跳过一个质数长度的数据
        cachedReader.skip(13); // 13是质数
        assertEquals(61, cachedReader.pos());

        // 读取单个字符
        int c = cachedReader.readChar();
        assertEquals(TEST_DATA.charAt(61), (char) c);
        assertEquals(62, cachedReader.pos());

        // 读取跨越块边界的数据
        String result3 = cachedReader.readFully(71); // 71是质数
        assertEquals(TEST_DATA.substring(62, 133), result3);
        assertEquals(133, cachedReader.pos());
    }

    @Test
    void testEdgeCasesWithPrimeSizes() throws IOException {
        // 使用较小的质数块大小
        ITextDataReader underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 11); // 11是质数

        // 测试刚好填满一个块
        String result = cachedReader.readFully(11);
        assertEquals(TEST_DATA.substring(0, 11), result);
        assertEquals(11, cachedReader.pos());

        // 测试读取长度比块大小小1
        result = cachedReader.readFully(10);
        assertEquals(TEST_DATA.substring(11, 21), result);
        assertEquals(21, cachedReader.pos());

        // 测试读取长度比块大小大1
        result = cachedReader.readFully(12);
        assertEquals(TEST_DATA.substring(21, 33), result);
        assertEquals(33, cachedReader.pos());

        // 测试读取剩余所有数据
        result = cachedReader.readFully(500 - 33);
        assertEquals(TEST_DATA.substring(33), result);
        assertEquals(500, cachedReader.pos());
    }

    @Test
    void testSeekWithPrimeSizes() throws IOException {
        // 使用质数块大小
        ITextDataReader underlyingReader = new SimpleTextDataReader(TEST_DATA);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 83); // 83是质数

        // 1. 测试seek到质数位置
        cachedReader.seek(79); // 79是质数
        assertEquals(79, cachedReader.pos());
        assertEquals(TEST_DATA.charAt(79), (char) cachedReader.readChar());

        // 2. 测试seek到块边界
        cachedReader.seek(83);
        assertEquals(83, cachedReader.pos());
        assertEquals(TEST_DATA.substring(83, 93), cachedReader.readFully(10));

        // 3. 测试seek到接近文件末尾的质数位置
        cachedReader.seek(491); // 491是质数
        assertEquals(491, cachedReader.pos());
        assertEquals(TEST_DATA.substring(491), cachedReader.readFully(9));
    }

    @Test
    void testReadLineWithPrimeSizes() throws IOException {
        // 创建带换行符的测试数据，使用质数长度的行
        String dataWithNewlines = "1234567\n12345678901\r\n12345678901234567\r123456789012345678901234567";
        ITextDataReader underlyingReader = new SimpleTextDataReader(dataWithNewlines);
        cachedReader = new BlockCachedTextDataReader(underlyingReader, 13); // 13是质数

        // 每行长度都是质数或质数-1
        assertEquals("1234567", cachedReader.readLine(100)); // 7是质数
        assertEquals("12345678901", cachedReader.readLine(100)); // 11是质数
        assertEquals("12345678901234567", cachedReader.readLine(100)); // 17是质数
        assertEquals("123456789012345678901234567", cachedReader.readLine(100)); // 27不是质数，但测试长行
    }
}