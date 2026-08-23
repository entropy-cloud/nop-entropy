/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestIoHelper {
    @Test
    public void testSerialize() {
        byte[] data = IoHelper.serializeToByteArray(Collections.emptyMap());
        Object o = IoHelper.deserializeFromByteArray(data);
        assertEquals(o, Collections.emptyMap());
    }

    @Test
    public void testGetEncodingFromBOM() throws Exception {
        // 标准BOM
        assertEquals("UTF-8", IoHelper.getEncodingFromBOM(
                new ByteArrayInputStream(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'})));
        assertEquals("UTF-16", IoHelper.getEncodingFromBOM(
                new ByteArrayInputStream(new byte[]{(byte) 0xFE, (byte) 0xFF, 'a', 'b'})));
        assertEquals("UTF-32", IoHelper.getEncodingFromBOM(
                new ByteArrayInputStream(new byte[]{0x00, 0x00, (byte) 0xFE, (byte) 0xFF})));
        assertEquals("UTF-32", IoHelper.getEncodingFromBOM(
                new ByteArrayInputStream(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x00})));
        // 无BOM时返回null，且流位置被复位
        InputStream is = new ByteArrayInputStream("abcd".getBytes(StandardCharsets.ISO_8859_1));
        assertNull(IoHelper.getEncodingFromBOM(is));
        assertEquals('a', is.read());
    }

    @Test
    public void testGetEncodingFromBOMShortStream() throws Exception {
        // 流长度不足4字节时不应抛"意外EOF"，应按实际字节数探测
        assertEquals("UTF-8", IoHelper.getEncodingFromBOM(
                new ByteArrayInputStream(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF})));
        assertEquals("UTF-16", IoHelper.getEncodingFromBOM(
                new ByteArrayInputStream(new byte[]{(byte) 0xFE, (byte) 0xFF})));
        assertNull(IoHelper.getEncodingFromBOM(
                new ByteArrayInputStream(new byte[]{'x'})));
        assertNull(IoHelper.getEncodingFromBOM(new ByteArrayInputStream(new byte[0])));

        // 无BOM的短流：探测后应复位到起始位置
        InputStream noBom = new ByteArrayInputStream(new byte[]{(byte) 0xEF, (byte) 0xBB, 'x'});
        assertNull(IoHelper.getEncodingFromBOM(noBom));
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        IoHelper.copy(noBom, bos);
        assertEquals(3, bos.size());

        // 探测命中时跳过BOM头：整个流就是BOM，探测后无剩余内容
        InputStream bomOnly = new ByteArrayInputStream(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        assertEquals("UTF-8", IoHelper.getEncodingFromBOM(bomOnly));
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        IoHelper.copy(bomOnly, bos2);
        assertEquals(0, bos2.size());
    }
}
