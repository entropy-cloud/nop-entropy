/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb;

import java.io.File;
import java.nio.charset.StandardCharsets;

import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 3: the RocksDB memory-control preset on
 * {@link RocksDBOptionConfig} (shared block cache, WriteBufferManager total
 * memtable cap, bloom filter). Off by default — the default config must leave
 * the native option set exactly as before the preset (no cache, no manager,
 * no custom table format) — and fully wired when a knob is positive, verified
 * here by read-back from the live native objects plus a block-cache charge
 * proof (flush + get).
 */
class TestRocksDBMemoryControl {

    @TempDir
    File tempDir;

    private static final long MB = 1024L * 1024L;

    private RocksDBKeyedStateBackend<String> newBackend(RocksDBOptionConfig optionConfig) {
        return new RocksDBKeyedStateBackend<>(
                new File(tempDir, "db-" + System.nanoTime()).getAbsolutePath(), String.class, 16, optionConfig);
    }

    @Test
    void defaultConfigKeepsLegacyNativeOptionSet() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend(new RocksDBOptionConfig());
        try {
            assertNull(backend.getBlockCacheForTest(), "no shared cache by default");
            assertNull(backend.getWriteBufferManagerForTest(), "no write-buffer manager by default");
            assertNull(backend.getBloomFilterForTest(), "no bloom filter by default");
            assertNull(backend.getDbOptionsForTest().writeBufferManager(),
                    "DB-level write-buffer manager not registered by default");
            assertNull(backend.getCfOptionsForTest().tableFormatConfig(),
                    "no custom table format by default (pre-preset behavior)");
        } finally {
            backend.close();
        }
    }

    @Test
    void memoryControlWiresCacheManagerAndBloomFilter() throws Exception {
        RocksDBOptionConfig optionConfig = new RocksDBOptionConfig(4 * MB, 2, 8 * MB, 4 * MB, 10d);
        assertTrue(optionConfig.isMemoryControlEnabled());
        RocksDBKeyedStateBackend<String> backend = newBackend(optionConfig);
        try {
            // Build-parameter assertions on the constructed native objects.
            org.rocksdb.Cache cache = backend.getBlockCacheForTest();
            org.rocksdb.WriteBufferManager wbm = backend.getWriteBufferManagerForTest();
            org.rocksdb.Filter bloom = backend.getBloomFilterForTest();
            assertNotNull(cache, "shared block cache built");
            assertNotNull(wbm, "write-buffer manager built");
            assertNotNull(bloom, "bloom filter built");
            // Live read-back: the manager is registered at DB level and charged
            // to the shared cache; the filter is on the shared table format.
            assertSame(wbm, backend.getDbOptionsForTest().writeBufferManager(),
                    "WriteBufferManager registered on DBOptions");
            org.rocksdb.TableFormatConfig tableConfig = backend.getCfOptionsForTest().tableFormatConfig();
            assertTrue(tableConfig instanceof org.rocksdb.BlockBasedTableConfig,
                    "custom table format attached to the shared column-family options");
            assertSame(bloom, ((org.rocksdb.BlockBasedTableConfig) tableConfig).filterPolicy(),
                    "bloom filter attached as the table filter policy");

            // Behavioral wiring proof: after flushing the state's column family
            // and reading it back, the data block is served through the shared
            // cache (the manager wiring itself is proven by the DBOptions
            // read-back above).
            ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
            ValueState<Integer> state = backend.getState(desc);
            backend.setCurrentKey("k");
            state.update(42);
            assertEquals(42, state.value());
            long memtableBytes = Long.parseLong(backend.getDbForTest()
                    .getProperty(backend.getOrCreateColumnFamily("v"), "rocksdb.cur-size-all-mem-tables"));
            assertTrue(memtableBytes > 0, "state write occupies a memtable before flush");
            org.rocksdb.FlushOptions flushOptions = new org.rocksdb.FlushOptions().setWaitForFlush(true);
            backend.getDbForTest().flush(flushOptions, backend.getOrCreateColumnFamily("v"));
            assertEquals(42, state.value(), "value readable after flush");
            assertTrue(cache.getUsage() > 0, "post-flush read is served through the shared block cache");
        } finally {
            backend.close();
        }
    }

    @Test
    void closeWithMemoryControlReleasesNativeHandlesCleanly() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend(
                new RocksDBOptionConfig(4 * MB, 2, 8 * MB, 4 * MB, 10d));
        ValueStateDescriptor<Integer> desc = new ValueStateDescriptor<>("v", Integer.class);
        backend.getState(desc);
        backend.close(); // must not throw: db -> manager -> cache -> filter close order
        // The preset native handles are detached after close (cleared before
        // the Java-side fields drop), so a later scrape of the getters cannot
        // reach a disposed native object.
        assertNull(backend.getBlockCacheForTest());
        assertNull(backend.getWriteBufferManagerForTest());
        assertNull(backend.getBloomFilterForTest());
    }

    @Test
    void bloomOnlyConfigAttachesFilterWithoutCache() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend(
                new RocksDBOptionConfig(4 * MB, 2, 0L, 0L, 10d));
        try {
            assertNull(backend.getBlockCacheForTest(), "bloom-only: no shared cache");
            assertNull(backend.getWriteBufferManagerForTest(), "bloom-only: no write-buffer manager");
            assertNotNull(backend.getBloomFilterForTest(), "bloom-only: filter built");
            assertNotNull(backend.getCfOptionsForTest().tableFormatConfig(),
                    "bloom-only: custom table format attached");
        } finally {
            backend.close();
        }
    }

    @Test
    void writeBufferManagerWithoutBlockCacheFailsFast() {
        StreamException thrown = assertThrows(StreamException.class, () ->
                newBackend(new RocksDBOptionConfig(4 * MB, 2, 0L, 4 * MB, 0d)));
        assertEquals(NopStreamErrors.ERR_STREAM_INVALID_ARG.getErrorCode(), thrown.getErrorCode(),
                "WriteBufferManager cap without a block cache is a typed config error");
        assertTrue(String.valueOf(thrown.getParam("detail")).contains("blockCacheSize"),
                "error names the missing blockCacheSize precondition");
    }

    @Test
    void backendWorksOnConfiguredPresetEndToEnd() throws Exception {
        RocksDBKeyedStateBackend<String> backend = newBackend(
                new RocksDBOptionConfig(4 * MB, 2, 8 * MB, 4 * MB, 10d));
        try {
            ValueStateDescriptor<String> desc = new ValueStateDescriptor<>("s", String.class);
            ValueState<String> state = backend.getState(desc);
            for (int i = 0; i < 100; i++) {
                backend.setCurrentKey("key-" + i);
                state.update("value-" + i);
            }
            // Raw byte-level probe through the same DB: the entry is physically present.
            org.rocksdb.ColumnFamilyHandle cf = backend.getOrCreateColumnFamily("s");
            try (org.rocksdb.RocksIterator it = backend.getDbForTest().newIterator(cf)) {
                int count = 0;
                for (it.seekToFirst(); it.isValid(); it.next()) {
                    String key = new String(it.key(), StandardCharsets.UTF_8);
                    assertTrue(key.contains("key-"), "storage key carries the raw key: " + key);
                    count++;
                }
                assertEquals(100, count, "all entries written through the preset-configured DB");
            }
        } finally {
            backend.close();
        }
    }
}
