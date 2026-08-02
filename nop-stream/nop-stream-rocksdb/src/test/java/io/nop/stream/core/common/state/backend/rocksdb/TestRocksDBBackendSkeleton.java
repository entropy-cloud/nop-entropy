/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.common.state.backend.rocksdb;

import java.nio.charset.StandardCharsets;

import io.nop.stream.core.common.state.backend.IKeyedStateBackend;
import io.nop.stream.core.windowing.windows.GlobalWindow;
import io.nop.stream.core.windowing.windows.TimeWindow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 tests: RocksDB backend skeleton, key/value serialization round-trip,
 * and JNI native library loading on macOS-arm64 / linux-amd64.
 */
class TestRocksDBBackendSkeleton {

    @TempDir
    File tempDir;

    @Test
    void testStateBackendFactoryMethods() {
        RocksDBStateBackend backend = new RocksDBStateBackend(tempDir.getAbsolutePath(), 1);
        assertEquals("RocksDBStateBackend", backend.getName());
        assertEquals(1, backend.getShardCount());
        assertNotNull(backend.createOperatorStateBackend());

        IKeyedStateBackend<String> keyed = backend.createKeyedStateBackend(String.class);
        assertNotNull(keyed);
        keyed.close();
    }

    @Test
    void testKeyedBackendOpenAndClose() {
        RocksDBStateBackend backend = new RocksDBStateBackend(tempDir.getAbsolutePath());
        RocksDBKeyedStateBackend<String> keyed =
                (RocksDBKeyedStateBackend<String>) backend.createKeyedStateBackend(String.class);
        assertNotNull(keyed);

        keyed.setCurrentKey("key1");
        assertEquals("key1", keyed.getCurrentKey());

        keyed.setCurrentNamespace("ns1");
        assertEquals("ns1", keyed.getCurrentNamespace());

        keyed.close();
    }

    @Test
    void testKeyEncodingRoundTripWithStringKey() {
        RocksDBKeyEncoder.DecodedKey dk = encodeAndDecode(IKeyedStateBackend.DEFAULT_NAMESPACE, "user123", 0, String.class);
        assertEquals(IKeyedStateBackend.DEFAULT_NAMESPACE, dk.namespace);
        assertEquals("user123", dk.rawKey);
        assertEquals(0, dk.keyGroupId);
    }

    @Test
    void testKeyEncodingRoundTripWithLongKey() {
        Long key = 42L;
        byte[] encoded = RocksDBKeyEncoder.encode(IKeyedStateBackend.DEFAULT_NAMESPACE, key, 3);
        RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(encoded, Long.class);
        assertEquals(IKeyedStateBackend.DEFAULT_NAMESPACE, dk.namespace);
        assertEquals(42L, dk.rawKey);
        assertEquals(3, dk.keyGroupId);
    }

    @Test
    void testKeyEncodingRoundTripWithTimeWindowNamespace() {
        TimeWindow window = new TimeWindow(1000L, 2000L);
        byte[] encoded = RocksDBKeyEncoder.encode(window, "key1", 0);
        RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(encoded, String.class);
        assertTrue(dk.namespace instanceof TimeWindow);
        TimeWindow decoded = (TimeWindow) dk.namespace;
        assertEquals(1000L, decoded.getStart());
        assertEquals(2000L, decoded.getEnd());
        assertEquals("key1", dk.rawKey);
    }

    @Test
    void testKeyEncodingRoundTripWithGlobalWindowNamespace() {
        byte[] encoded = RocksDBKeyEncoder.encode(GlobalWindow.get(), "key1", 0);
        RocksDBKeyEncoder.DecodedKey dk = RocksDBKeyEncoder.decode(encoded, String.class);
        assertEquals(GlobalWindow.get(), dk.namespace);
        assertEquals("key1", dk.rawKey);
    }

    @Test
    void testKeyEncodingDifferentKeysProduceDifferentBytes() {
        byte[] k1 = RocksDBKeyEncoder.encode("ns", "key1", 0);
        byte[] k2 = RocksDBKeyEncoder.encode("ns", "key2", 0);
        byte[] k3 = RocksDBKeyEncoder.encode("ns2", "key1", 0);
        assertNotEquals(toHexString(k1), toHexString(k2));
        assertNotEquals(toHexString(k1), toHexString(k3));
    }

    @Test
    void testKeyEncodingDifferentShardsProduceDifferentBytes() {
        byte[] k0 = RocksDBKeyEncoder.encode("ns", "key1", 0);
        byte[] k1 = RocksDBKeyEncoder.encode("ns", "key1", 1);
        assertNotEquals(toHexString(k0), toHexString(k1));
    }

    @Test
    void testValueSerializationRoundTrip() {
        byte[] bytes = RocksDBValueSerDe.serialize(42L);
        Long result = RocksDBValueSerDe.deserialize(bytes, Long.class);
        assertEquals(42L, result);
    }

    @Test
    void testValueSerializationComplexObject() {
        TestBean bean = new TestBean("alice", 30);
        byte[] bytes = RocksDBValueSerDe.serialize(bean);
        TestBean result = RocksDBValueSerDe.deserialize(bytes, TestBean.class);
        assertEquals("alice", result.getName());
        assertEquals(30, result.getAge());
    }

    @Test
    void testBaseKeyLengthForMapKeySuffix() {
        byte[] baseKey = RocksDBKeyEncoder.encode("ns", "key1", 0);
        int baseLen = RocksDBKeyEncoder.baseKeyLength(baseKey);
        assertEquals(baseKey.length, baseLen);

        // Simulate a map key appended
        byte[] mapKeyBytes = "mapKey".getBytes(StandardCharsets.UTF_8);
        byte[] fullKey = new byte[baseKey.length + 4 + mapKeyBytes.length];
        System.arraycopy(baseKey, 0, fullKey, 0, baseKey.length);
        System.arraycopy(new byte[]{0, 0, 0, (byte) mapKeyBytes.length}, 0, fullKey, baseKey.length, 4);
        System.arraycopy(mapKeyBytes, 0, fullKey, baseKey.length + 4, mapKeyBytes.length);

        int computedBaseLen = RocksDBKeyEncoder.baseKeyLength(fullKey);
        assertEquals(baseKey.length, computedBaseLen);
    }

    private RocksDBKeyEncoder.DecodedKey encodeAndDecode(Object namespace, Object key, int keyGroupId, Class<?> keyType) {
        byte[] encoded = RocksDBKeyEncoder.encode(namespace, key, keyGroupId);
        return RocksDBKeyEncoder.decode(encoded, keyType);
    }

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @io.nop.api.core.annotations.data.DataBean
    public static class TestBean {
        private String name;
        private int age;

        public TestBean() {
        }

        public TestBean(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }
}
