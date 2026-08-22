/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codec.compress;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestCompressCodec {

    private static final String TEXT = "hello nop-entropy compress codec round trip. ".repeat(64);

    private final byte[] textBytes = TEXT.getBytes(StandardCharsets.UTF_8);

    @Test
    public void testGZipRoundTrip() {
        GZipCompressCodec codec = new GZipCompressCodec();
        byte[] encoded = codec.encodeBytes(textBytes);
        assertNotEquals(TEXT, new String(encoded, StandardCharsets.UTF_8));

        byte[] decoded = codec.decodeBytes(encoded);
        assertEquals(TEXT, new String(decoded, StandardCharsets.UTF_8));
    }

    @Test
    public void testDeflateRoundTrip() {
        DeflateCompressCodec codec = new DeflateCompressCodec();
        byte[] encoded = codec.encodeBytes(textBytes);
        assertNotEquals(TEXT, new String(encoded, StandardCharsets.UTF_8));

        byte[] decoded = codec.decodeBytes(encoded);
        assertEquals(TEXT, new String(decoded, StandardCharsets.UTF_8));
    }
}
