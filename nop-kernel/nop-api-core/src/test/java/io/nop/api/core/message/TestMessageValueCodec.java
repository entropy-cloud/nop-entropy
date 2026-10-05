/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI24 (F-31/去重): the shared wire codec — String passthrough, Number/Boolean toString,
 * JSON for the rest; the single source both broker backends delegate to, so identical
 * payloads produce identical wire strings on Kafka and Pulsar.
 */
class TestMessageValueCodec {

    @Test
    void encodeValueCoversProviderFreePayloadClasses() {
        // POJO JSON-stringification requires the JSON provider (registered by nop-core at
        // startup) — asserted in the nop-message module tests where the provider exists.
        assertEquals("text", MessageValueCodec.encodeValue("text"));
        assertEquals("42", MessageValueCodec.encodeValue(42));
        assertEquals("3.5", MessageValueCodec.encodeValue(3.5));
        assertEquals("true", MessageValueCodec.encodeValue(Boolean.TRUE));
        assertNull(MessageValueCodec.encodeValue(null));
    }

    @Test
    void decodeValueIsUtf8WithNullTolerance() {
        assertNull(MessageValueCodec.decodeValue(null));
        assertNull(MessageValueCodec.decodeValue(new byte[0]));
        assertEquals("text", MessageValueCodec.decodeValue("text".getBytes()));
    }
}
