/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.typeutils;

import java.util.ArrayList;
import java.util.List;

import com.example.stream.UserStatePayload;

import org.junit.jupiter.api.Test;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-10a (plan 2026-09-04-1326-3): JEP 290 whitelist filter at the native
 * deserialization choke point ({@link JavaStreamSerializer#deserialize}).
 *
 * <p>Checkpoint bytes cross a trust boundary (storage write-access set &gt; operator
 * set); this path previously ran {@code readObject()} with NO filter — the one channel
 * bypassing the platform {@code ClassNameValidator} discipline. Proofs:
 * <ol>
 *   <li>baseline classes (io.nop.*, java.*, JDK collections) round-trip unchanged;</li>
 *   <li>a third-party-package {@code Serializable} payload is REJECTED with the typed
 *       {@code ERR_STREAM_CLASS_NOT_ALLOWED} error (never a raw InvalidClassException);</li>
 *   <li>the documented escape hatch (system property declaring extra prefixes) restores
 *       user-defined state classes without code changes.</li>
 * </ol>
 */
class TestStreamDeserializationFilter {

    private final JavaStreamSerializer<java.io.Serializable> serializer = JavaStreamSerializer.of();

    private final JavaStreamSerializer<UserStatePayload> payloadSerializer = JavaStreamSerializer.of();

    @Test
    void baselinePrefixesRoundTripThroughTheFilter() {
        // io.nop.* type
        UserStateLikeProbe probe = new UserStateLikeProbe("probe");
        byte[] bytes = serializer.serialize(probe);
        Object restored = serializer.deserialize(bytes, java.io.Serializable.class);
        assertEquals(probe, restored);

        // java.* / JDK collections
        List<String> list = new ArrayList<>(List.of("a", "b"));
        byte[] listBytes = serializer.serialize((java.io.Serializable) list);
        assertEquals(list, serializer.deserialize(listBytes, java.io.Serializable.class));
    }

    @Test
    void nonWhitelistedClassIsRejectedTyped() {
        byte[] bytes = payloadSerializer.serialize(new UserStatePayload("gadget"));

        StreamException ex = assertThrows(StreamException.class,
                () -> payloadSerializer.deserialize(bytes, UserStatePayload.class),
                "third-party-package class must be rejected by the JEP 290 filter");
        assertEquals(NopStreamErrors.ERR_STREAM_CLASS_NOT_ALLOWED.getErrorCode(), ex.getErrorCode(),
                "rejection is the typed whitelist error, not a raw InvalidClassException");
        assertTrue(String.valueOf(ex).contains(StreamDeserializationFilter.EXTRA_ALLOWED_PREFIXES_PROPERTY),
                "rejection carries the escape-hatch migration hint: " + ex);
    }

    @Test
    void escapeHatchRestoresUserDeclaredPrefixes() {
        byte[] bytes = payloadSerializer.serialize(new UserStatePayload("user-state"));
        String property = StreamDeserializationFilter.EXTRA_ALLOWED_PREFIXES_PROPERTY;
        String prior = System.getProperty(property);
        System.setProperty(property, "com.example.stream.");
        try {
            UserStatePayload restored = payloadSerializer.deserialize(bytes, UserStatePayload.class);
            assertEquals("user-state", restored.getValue(),
                    "user-declared prefix restores the custom Serializable state");
        } finally {
            if (prior == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, prior);
            }
        }
    }

    @Test
    void extraPrefixParsingTrimsAndDropsBlanks() {
        assertEquals(List.of("a.b.", "c.d"),
                StreamDeserializationFilter.parseExtraPrefixes(" a.b. , c.d ,, "));
        assertEquals(List.of(), StreamDeserializationFilter.parseExtraPrefixes(null));
        assertEquals(List.of(), StreamDeserializationFilter.parseExtraPrefixes("  "));
        assertTrue(StreamDeserializationFilter.isAllowed("com.example.stream.UserStatePayload",
                StreamDeserializationFilter.baselinePrefixes()) == false,
                "baseline alone does not admit third-party prefixes");
        assertTrue(StreamDeserializationFilter.isAllowed("io.nop.stream.core.X",
                StreamDeserializationFilter.baselinePrefixes()),
                "baseline admits io.nop.*");
    }
}
