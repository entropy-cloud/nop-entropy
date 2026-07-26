/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSerializerFingerprint {

    @Test
    void equalsAndHashCodeBasedOnAllThreeFields() {
        SerializerFingerprint a = new SerializerFingerprint("s1", 1, "checksum-a");
        SerializerFingerprint b = new SerializerFingerprint("s1", 1, "checksum-a");
        SerializerFingerprint c = new SerializerFingerprint("s1", 1, "checksum-b");
        SerializerFingerprint d = new SerializerFingerprint("s2", 1, "checksum-a");
        SerializerFingerprint e = new SerializerFingerprint("s1", 2, "checksum-a");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        assertNotEquals(a, c);
        assertNotEquals(a, d);
        assertNotEquals(a, e);
    }

    @Test
    void schemaVersionDefaultsToOneWhenNonPositive() {
        assertEquals(1, new SerializerFingerprint("s", 0, "x").getSchemaVersion());
        assertEquals(1, new SerializerFingerprint("s", -1, "x").getSchemaVersion());
        assertEquals(2, new SerializerFingerprint("s", 2, "x").getSchemaVersion());
    }

    @Test
    void equalsSymmetryAndNull() {
        SerializerFingerprint a = new SerializerFingerprint("s", 1, "x");
        assertEquals(a, a);
        assertFalse(a.equals(null));
        assertFalse(a.equals("not a fingerprint"));
    }

    @Test
    void toStringContainsKeyFields() {
        SerializerFingerprint a = new SerializerFingerprint("counter", 1, "abc");
        String s = a.toString();
        assertTrue(s.contains("counter"));
        assertTrue(s.contains("abc"));
    }
}
