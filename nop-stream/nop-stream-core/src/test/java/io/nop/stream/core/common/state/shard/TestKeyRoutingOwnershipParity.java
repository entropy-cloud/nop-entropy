/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.shard;

import java.math.BigInteger;
import java.util.Date;
import java.util.UUID;

import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AR-01 / ST-03 unit parity (plan 368 Phase 1): the record-routing entry point
 * {@link KeyGroupAssignment#assignToSubtask} must agree with the state
 * ownership path (key-group &#8594; range owner) for every key type the
 * stable-hash contract covers, and enum keys must hash by {@code name()} (not
 * identity {@code hashCode()}).
 */
public class TestKeyRoutingOwnershipParity {

    public enum Color {
        RED, GREEN, BLUE
    }

    /**
     * A JSON-serializable POJO key (structurally equal instances must route
     * identically). {@code @DataBean} is required for JsonTool serialization —
     * the platform contract for keyed-state keys (non-DataBean keys fall back
     * to identity hashing with a logged warning, which is an adjudicated
     * best-effort path for non-production inputs).
     */
    @io.nop.api.core.annotations.data.DataBean
    public static final class PojoKey {
        private final String a;
        private final int b;

        public PojoKey(String a, int b) {
            this.a = a;
            this.b = b;
        }

        public String getA() {
            return a;
        }

        public int getB() {
            return b;
        }

        // Deliberately no hashCode/equals override: the stable-hash contract
        // must not depend on POJO identity semantics.
    }

    private static final int MAX_P = 128;

    @Test
    void routingAgreesWithOwnershipForStableValueTypes() {
        Object[] keys = {
                "route-key-7", "", "中文键",
                42, -7, 123456789012345L, -99L,
                true, Byte.MAX_VALUE, Short.MIN_VALUE, 'Z',
                1.5f, 2.25d,
                BigInteger.valueOf(1L << 70),
                new java.math.BigDecimal("12345.6789"),
                UUID.fromString("12345678-90ab-cdef-1234-567890abcdef"),
                new Date(1700000000000L)
        };
        for (int p : new int[]{1, 2, 4, 7, 128}) {
            for (Object key : keys) {
                int keyGroup = KeyGroupAssignment.assignToKeyGroup(key, MAX_P);
                int expected = KeyGroupAssignment.assignKeyGroupToSubtask(keyGroup, MAX_P, p);
                assertEquals(expected, KeyGroupAssignment.assignToSubtask(key, MAX_P, p),
                        "routing must equal ownership path for " + key + " at parallelism " + p);
            }
        }
    }

    @Test
    void routingAgreesWithOwnershipForEnumAndPojoKeys() {
        for (int p : new int[]{2, 4, 16}) {
            for (Color c : Color.values()) {
                int keyGroup = KeyGroupAssignment.assignToKeyGroup(c, MAX_P);
                int expected = KeyGroupAssignment.assignKeyGroupToSubtask(keyGroup, MAX_P, p);
                assertEquals(expected, KeyGroupAssignment.assignToSubtask(c, MAX_P, p),
                        "enum routing must equal ownership path at parallelism " + p);
            }
            for (int i = 0; i < 8; i++) {
                PojoKey k1 = new PojoKey("pojo-" + i, i);
                PojoKey k2 = new PojoKey("pojo-" + i, i);
                assertEquals(KeyGroupAssignment.assignToSubtask(k1, MAX_P, p),
                        KeyGroupAssignment.assignToSubtask(k2, MAX_P, p),
                        "structurally equal POJO keys must route to the same subtask");
            }
        }
    }

    @Test
    void enumKeyHashesByNameNotByIdentity() {
        for (Color c : Color.values()) {
            assertEquals(KeyGroupAssignment.stableHash(c.name()), KeyGroupAssignment.stableHash(c),
                    "enum stableHash must be derived from name(), not identity hashCode()");
            assertEquals(KeyGroupAssignment.assignToKeyGroup(c.name(), MAX_P),
                    KeyGroupAssignment.assignToKeyGroup(c, MAX_P),
                    "enum key-group assignment must equal its name's assignment");
        }
    }

    @Test
    void testDataActuallyDistinguishesLegacyRoutingFormula() {
        // Guard against a vacuous parity test: for String keys at small p the
        // legacy (hashCode % p) formula and the ownership formula must differ
        // for a measurable share of keys — otherwise this suite could not
        // detect a regression back to raw hashCode routing.
        int mismatches = 0;
        int samples = 500;
        int p = 4;
        for (int i = 0; i < samples; i++) {
            String key = "distinguish-" + i;
            int legacy = (key.hashCode() & Integer.MAX_VALUE) % p;
            if (legacy != KeyGroupAssignment.assignToSubtask(key, MAX_P, p)) {
                mismatches++;
            }
        }
        assertNotEquals(0, mismatches,
                "legacy and ownership formulas must disagree for some keys at p=4/mp=128");
    }

    @Test
    void invalidArgumentsFailFast() {
        String key = "k";
        assertThrows(StreamException.class, () -> KeyGroupAssignment.assignToSubtask(key, 0, 1));
        assertThrows(StreamException.class, () -> KeyGroupAssignment.assignToSubtask(key, 4, 0));
        assertThrows(StreamException.class, () -> KeyGroupAssignment.assignToSubtask(key, 4, 8));
        // A null key is a supported value (stableHash(null) == 0, documented) —
        // only invalid maxParallelism/parallelism combinations fail fast.
    }
}
