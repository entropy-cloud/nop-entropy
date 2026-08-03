/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.message.debezium.engine;

import org.apache.kafka.connect.runtime.WorkerConfig;
import org.apache.kafka.connect.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestNopStreamOffsetBackingStore {

    private static final String CONNECTOR = "test-connector";

    @AfterEach
    void cleanup() {
        NopStreamOffsetBackingStore.clearConnector(CONNECTOR);
        NopStreamOffsetBackingStore.clearConnector("_default_");
    }

    private static ByteBuffer bb(String s) {
        return ByteBuffer.wrap(s.getBytes());
    }

    @Test
    void testNopStreamOffsetBackingStoreSpiRoundTrip() throws Exception {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        store.start();

        // set via SPI
        Map<ByteBuffer, ByteBuffer> values = new HashMap<>();
        values.put(bb("key1"), bb("val1"));
        values.put(bb("key2"), bb("val2"));

        AtomicReference<Throwable> cbErr = new AtomicReference<>();
        Callback<Void> cb = (err, result) -> cbErr.set(err);
        Future<Void> f = store.set(values, cb);
        assertNotNull(f);
        f.get();
        assertNull(cbErr.get(), "callback must complete without error");

        // get via SPI
        Future<Map<ByteBuffer, ByteBuffer>> gf = store.get(values.keySet());
        Map<ByteBuffer, ByteBuffer> result = gf.get();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("val1", new String(result.get(bb("key1")).array()));
        assertEquals("val2", new String(result.get(bb("key2")).array()));

        // get for unknown key returns empty result
        Future<Map<ByteBuffer, ByteBuffer>> gf2 = store.get(Collections.singletonList(bb("unknown")));
        assertTrue(gf2.get().isEmpty());

        store.stop();
        assertFalse(store.isStarted());
    }

    @Test
    void testNopStreamOffsetBackingStoreHelperGetSetOffsets() {
        NopStreamOffsetBackingStore store = NopStreamOffsetBackingStore.forConnector(CONNECTOR);

        Map<ByteBuffer, ByteBuffer> offsets = new LinkedHashMap<>();
        offsets.put(bb("p1"), bb("o1"));
        offsets.put(bb("p2"), bb("o2"));
        store.setOffsets(offsets);

        Map<ByteBuffer, ByteBuffer> read = store.getOffsets();
        assertEquals(2, read.size());
        assertEquals("o1", new String(read.get(bb("p1")).array()));
        assertEquals("o2", new String(read.get(bb("p2")).array()));

        // setOffsets is additive (merge), does not clear existing
        Map<ByteBuffer, ByteBuffer> more = new LinkedHashMap<>();
        more.put(bb("p3"), bb("o3"));
        store.setOffsets(more);

        assertEquals(3, store.getOffsets().size());
    }

    @Test
    void testConnectorNameRegistrySharesDataAcrossInstances() {
        // Source-function instance
        NopStreamOffsetBackingStore sourceInstance = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        sourceInstance.setOffsets(Collections.singletonMap(bb("pk"), bb("ov")));

        // A second instance for the same connector name must observe the same data (simulates the
        // engine-instantiated instance sharing the registry entry)
        NopStreamOffsetBackingStore secondInstance = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        Map<ByteBuffer, ByteBuffer> read = secondInstance.getOffsets();
        assertEquals(1, read.size());
        assertEquals("ov", new String(read.get(bb("pk")).array()));
    }

    @Test
    void testSerializeDeserializeRoundTrip() {
        Map<ByteBuffer, ByteBuffer> offsets = new LinkedHashMap<>();
        offsets.put(bb("partition-A"), bb("offset-12345"));
        offsets.put(bb("partition-B"), bb("offset-67890"));

        Map<String, String> serialized = NopStreamOffsetBackingStore.toSerializable(offsets);
        assertEquals(2, serialized.size());

        Map<ByteBuffer, ByteBuffer> restored = NopStreamOffsetBackingStore.fromSerializable(serialized);
        assertEquals(2, restored.size());
        assertEquals("offset-12345", new String(restored.get(bb("partition-A")).array()));
        assertEquals("offset-67890", new String(restored.get(bb("partition-B")).array()));
    }

    @Test
    void testConfigureWithWorkerConfigBindsToRegistry() {
        // Simulate the engine instantiating a store via reflection + configure(WorkerConfig)
        NopStreamOffsetBackingStore sourceInstance = NopStreamOffsetBackingStore.forConnector(CONNECTOR);
        sourceInstance.setOffsets(Collections.singletonMap(bb("k"), bb("v")));

        NopStreamOffsetBackingStore engineInstance = new NopStreamOffsetBackingStore();
        // Mock WorkerConfig: extracting the connector name from originals()
        WorkerConfig wc = mock(WorkerConfig.class);
        Map<String, Object> originals = new HashMap<>();
        originals.put("name", CONNECTOR);
        when(wc.originals()).thenReturn(originals);
        engineInstance.configure(wc);

        // The engine instance must see the offsets seeded by the source instance (shared registry)
        Map<ByteBuffer, ByteBuffer> read = engineInstance.getOffsets();
        assertEquals(1, read.size());
        assertEquals("v", new String(read.get(bb("k")).array()));
    }
}
