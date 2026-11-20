package io.nop.rg.core.coordinator;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ByteText} UTF-8 合法性扫描与 lossy 解码单元测试（plan 2575 Phase E1）。
 */
public class ByteTextTest {

    @Test
    public void testValidUtf8Accepted() {
        assertTrue(ByteText.isValidUtf8("hello".getBytes(StandardCharsets.UTF_8), 0, 5));
        assertTrue(ByteText.isValidUtf8("中文テスト".getBytes(StandardCharsets.UTF_8), 0, 12));
        assertTrue(ByteText.isValidUtf8("emoji \uD83D\uDE00 ok".getBytes(StandardCharsets.UTF_8), 0, 13));
        assertTrue(ByteText.isValidUtf8(new byte[0], 0, 0));
    }

    @Test
    public void testInvalidUtf8Rejected() {
        // 非法前导字节
        assertFalse(ByteText.isValidUtf8(new byte[]{(byte) 0xB2, 'a'}, 0, 2));
        // 截断多字节序列（中文首字节无后续）
        assertFalse(ByteText.isValidUtf8(new byte[]{(byte) 0xE4, (byte) 0xB8}, 0, 2));
        // 连续字节位置错误
        assertFalse(ByteText.isValidUtf8(new byte[]{(byte) 0xC3, 'a'}, 0, 2));
        // overlong（0xC0 0x80 编码 U+0000）
        assertFalse(ByteText.isValidUtf8(new byte[]{(byte) 0xC0, (byte) 0x80}, 0, 2));
        // 代理区（U+D800 的 3 字节编码）
        assertFalse(ByteText.isValidUtf8(new byte[]{(byte) 0xED, (byte) 0xA0, (byte) 0x80}, 0, 3));
        // 越界（U+110000 的 4 字节编码）
        assertFalse(ByteText.isValidUtf8(new byte[]{(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, 0, 4));
    }

    @Test
    public void testLossyDecodeReplacesPerInvalidByte() {
        byte[] bytes = new byte[]{(byte) 0xB2, (byte) 0xE2, (byte) 0xB4, (byte) 0xD9};
        // 4 个非法字节 → 4 个 U+FFFD，且合法 run 不受影响
        assertEquals("\uFFFD\uFFFD\uFFFD\uFFFD", ByteText.decodeLossy(bytes));
    }

    @Test
    public void testLossyDecodeKeepsValidRuns() {
        byte[] abc = "abc".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[abc.length + 2 + abc.length];
        System.arraycopy(abc, 0, bytes, 0, abc.length);
        bytes[abc.length] = (byte) 0xB2;
        bytes[abc.length + 1] = (byte) 0xFF;
        System.arraycopy(abc, 0, bytes, abc.length + 2, 3);
        assertEquals("abc\uFFFD\uFFFDabc", ByteText.decodeLossy(bytes));
    }

    @Test
    public void testLossyDecodeHandlesTruncatedMultibyte() {
        // 合法中文首字节 + 后续字节被截断 → 逐非法字节各一个 U+FFFD（ByteText 契约粒度）
        byte[] bytes = new byte[]{(byte) 0xE4, (byte) 0xB8, 'x'};
        assertEquals("\uFFFD\uFFFDx", ByteText.decodeLossy(bytes));
    }

    @Test
    public void testBase64RoundTrip() {
        byte[] bytes = {(byte) 0xE4, (byte) 0xB8, (byte) 0xAD, 0x0A};
        assertArrayEquals(java.util.Base64.getDecoder().decode(ByteText.base64(bytes)), bytes);
    }
}
