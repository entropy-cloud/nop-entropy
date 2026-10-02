/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.connector.lookup;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * WI14: ITableLookup contract semantics — null for missing keys, delegation counted,
 * and the interface is usable as a bean-injected lookup facade (§III #8: applications
 * bridge it to IJdbcTemplate / IBatchLoader, never a self-managed data source).
 */
public class TestDimTableLookupFunction {

    @Test
    public void lookupContractNullForMissingAndDelegates() {
        Map<String, String> rows = new HashMap<>();
        rows.put("a", "A");
        AtomicInteger calls = new AtomicInteger();
        ITableLookup lookup = key -> {
            calls.incrementAndGet();
            return rows.get(key);
        };

        assertEquals("A", lookup.lookup("a"));
        assertNull(lookup.lookup("missing"), "missing key must yield null, not an exception");
        assertEquals(2, calls.get(), "every lookup delegates to the bridge");
    }

    @Test
    public void interfaceIsSerializableContract() {
        // operator deep copies serialize the UDF graph — the facade must be
        // Serializable to be injectable
        org.junit.jupiter.api.Assertions.assertTrue(
                java.io.Serializable.class.isAssignableFrom(ITableLookup.class));
    }
}
