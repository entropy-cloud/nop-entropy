/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.execution.transport;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.streamrecord.StreamRecord;
import org.junit.jupiter.api.Test;

import java.io.ObjectInputFilter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plan 360 R1 focused tests (Minimum Rules #25): the decode type cache and the
 * cached JEP290 filter must be behavior-equivalent to the previous per-call
 * implementations.
 *
 * <ul>
 *   <li>Decode cache: same input → same decoded result across repeated calls;
 *       a class name outside the whitelist is still rejected typed on EVERY
 *       call (never cached, never admitted).</li>
 *   <li>Filter cache: the same filter instance is returned while the escape-hatch
 *       property is unchanged, and a property change takes effect on the next
 *       call (old behavior: fresh filter per call re-read the property).</li>
 * </ul>
 */
class TestPlan360R1Equivalence {

    private static StreamMessageEnvelope envelope(String valueType, String json) {
        return new StreamMessageEnvelope(7L, StreamMessageEnvelope.TYPE_STREAM_RECORD,
                valueType, json);
    }

    @Test
    void decodeCacheReturnsEquivalentResultsAcrossCalls() {
        StreamMessageEnvelope env = envelope("java.lang.String", "\"hello\"");

        Object first = StreamElementCodec.decode(env);
        Object second = StreamElementCodec.decode(env);
        Object third = StreamElementCodec.decode(env);

        assertEquals(first, second, "cached decode must yield the same value");
        assertEquals(first, third, "cached decode must be stable across calls");
        assertNotSame(first, second, "decode still produces a fresh StreamRecord per call");
        assertEquals("hello", ((StreamRecord<?>) first).getValue());
    }

    @Test
    void illegalValueTypeIsRejectedOnEveryCall() {
        StreamMessageEnvelope env = envelope("com.outside.prefix.Evil", "{}");

        for (int i = 0; i < 3; i++) {
            StreamException ex = assertThrows(StreamException.class, () -> StreamElementCodec.decode(env),
                    "a whitelisting violation must fail typed on every call (never cached)");
            assertEquals(NopStreamErrors.ERR_STREAM_CLASS_NOT_ALLOWED.getErrorCode(), ex.getErrorCode());
        }
    }

    @Test
    void unknownValueTypeStillFailsTypedAfterCacheWarm() {
        // Warm the cache with a legal type first.
        StreamElementCodec.decode(envelope("java.lang.String", "\"x\""));
        // A legal-prefix but nonexistent class must still fail with the load-failure code.
        StreamMessageEnvelope env = envelope("io.nop.stream.core.NoSuchTypeAnywhere", "{}");
        StreamException ex = assertThrows(StreamException.class, () -> StreamElementCodec.decode(env));
        assertEquals(NopStreamErrors.ERR_STREAM_CODEC_VALUE_TYPE_LOAD_FAILED.getErrorCode(), ex.getErrorCode());
    }

    @Test
    void filterIsCachedWhilePropertyUnchangedAndRefreshesOnChange() {
        String prop = io.nop.stream.core.common.typeutils.StreamDeserializationFilter
                .EXTRA_ALLOWED_PREFIXES_PROPERTY;
        String old = System.getProperty(prop);
        try {
            System.clearProperty(prop);
            ObjectInputFilter f1 = io.nop.stream.core.common.typeutils.StreamDeserializationFilter.create();
            ObjectInputFilter f2 = io.nop.stream.core.common.typeutils.StreamDeserializationFilter.create();
            assertSame(f1, f2, "unchanged property → cached filter instance");

            System.setProperty(prop, "com.example.stream.state.");
            ObjectInputFilter f3 = io.nop.stream.core.common.typeutils.StreamDeserializationFilter.create();
            assertNotSame(f1, f3, "property change must rebuild the filter (old behavior: fresh per call)");
            ObjectInputFilter f4 = io.nop.stream.core.common.typeutils.StreamDeserializationFilter.create();
            assertSame(f3, f4, "unchanged property → cached again");
        } finally {
            if (old == null) {
                System.clearProperty(prop);
            } else {
                System.setProperty(prop, old);
            }
        }
    }
}
