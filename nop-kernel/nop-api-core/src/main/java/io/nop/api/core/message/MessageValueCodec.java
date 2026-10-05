/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.message;

import io.nop.api.core.json.JSON;

import java.nio.charset.StandardCharsets;

/**
 * WI24 (D2 envelope contract): the single wire-value codec shared by the Kafka and Pulsar
 * backends (both STRING-schema transports) — {@link #encodeValue} guarantees the two
 * backends produce identical wire strings for the same payload, closing MSG-F-31.
 *
 * <p>{@link #decodeValue(byte[])} is the inverse for byte-array header transports (Kafka);
 * Pulsar message properties are already Strings and must not use it.
 */
public final class MessageValueCodec {
    private MessageValueCodec() {
    }

    /**
     * Wire encoding for STRING-schema transports: String passes through, numbers and
     * booleans use their toString form, anything else is JSON-stringified; {@code null}
     * stays {@code null}.
     */
    public static String encodeValue(Object value) {
        if (value == null)
            return null;

        if (value instanceof String)
            return (String) value;

        if (value instanceof Number || value instanceof Boolean)
            return value.toString();

        return JSON.stringify(value);
    }

    /**
     * UTF-8 header decoding with null-tolerance (null or empty → null); malformed bytes
     * degrade to the replacement-char form exactly like {@code new String(raw, UTF_8)}.
     */
    public static String decodeValue(byte[] bytes) {
        if (bytes == null || bytes.length == 0)
            return null;
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
