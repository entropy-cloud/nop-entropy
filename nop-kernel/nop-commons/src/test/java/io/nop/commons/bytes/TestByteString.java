/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.bytes;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestByteString {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void testStartsWith() {
        assertTrue(ByteString.fromUtf8("abcd").startsWithBytes(bytes("ab")));
        assertTrue(ByteString.fromUtf8("abcd").startsWith(ByteString.fromUtf8("abcd")));
        assertFalse(ByteString.fromUtf8("abcd").startsWithBytes(bytes("cd")));
    }

    @Test
    public void testEndsWith() {
        // 非零偏移的匹配不应失效
        assertTrue(ByteString.fromUtf8("abcd").endsWithBytes(bytes("cd")));
        assertTrue(ByteString.fromUtf8("abcd").endsWith(ByteString.fromUtf8("abcd")));
        assertFalse(ByteString.fromUtf8("abcd").endsWithBytes(bytes("bc")));
    }

    @Test
    public void testIndexOfBytes() {
        assertEquals(0, ByteString.fromUtf8("abcd").indexOfBytes(bytes("ab")));
        // 只能命中位置0之外的偏移也应正常命中
        assertEquals(2, ByteString.fromUtf8("abcd").indexOfBytes(bytes("cd")));
        assertEquals(1, ByteString.fromUtf8("abcabc").indexOfBytes(bytes("bc")));
        assertEquals(4, ByteString.fromUtf8("abcabc").indexOfBytes(bytes("bc"), 2));
        assertEquals(4, ByteString.fromUtf8("abcabc").indexOf(ByteString.fromUtf8("bc"), 2));
        assertEquals(-1, ByteString.fromUtf8("abcd").indexOfBytes(bytes("xyz")));
        assertEquals(-1, ByteString.fromUtf8("abcd").indexOfBytes(bytes("abcd"), 1));
    }

    @Test
    public void testLastIndexOfBytes() {
        assertEquals(4, ByteString.fromUtf8("abcabc").lastIndexOfBytes(bytes("bc")));
        assertEquals(1, ByteString.fromUtf8("abcabc").lastIndexOfBytes(bytes("bc"), 2));
        assertEquals(-1, ByteString.fromUtf8("abcd").lastIndexOfBytes(bytes("xyz")));
    }

    @Test
    public void testRangeEquals() {
        assertTrue(ByteString.fromUtf8("abcd").rangeEqualsBytes(2, bytes("cd"), 0, 2));
        assertTrue(ByteString.fromUtf8("abcd").rangeEquals(1, ByteString.fromUtf8("bc"), 0, 2));
        assertFalse(ByteString.fromUtf8("abcd").rangeEqualsBytes(1, bytes("cd"), 0, 2));
    }

    @Test
    public void testIsSafeUtf8() {
        // 数字/字母/-/_ 组成的字节序列应返回true
        assertTrue(ByteString.fromUtf8("abc-123_").isSafeUtf8());
        assertTrue(ByteString.fromUtf8("").isSafeUtf8());
        assertFalse(ByteString.fromUtf8("ab+").isSafeUtf8());
        assertFalse(ByteString.fromUtf8("a b").isSafeUtf8());
    }
}
