/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.rocksdb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;

import io.nop.stream.core.exceptions.StreamException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red⑥ acceptance pin — W-06 (finding 1.2-C6-005;
 * adjudication C.3 / seam S6): {@code openDB}'s failure path must release every
 * native handle it already allocated (dbOptions / cfOptions / the memory-control
 * preset trio Cache→WBM→Bloom, plus a half-filled handles list) before
 * rethrowing. Pre-fix the catch rethrew directly, so a failed open leaked the
 * entire native set until an unreliable finalizer ran (rocksdbjni finalizers are
 * not heap-pressure driven — restart storms stack the leak); the same method's
 * Options listing was already TWR ("mirrors the try-with-resources discipline of
 * RocksDBIncrementalRestore") — the open path now mirrors its own mirror.
 *
 * <p>Legs: (a) package-private white-box on {@code closeResourcesAfterFailedOpen}
 * against REAL native objects — the preset trio + both option objects + a
 * half-filled handles list all report {@code isOwningHandle() == false} after the
 * helper, with an empty cleanup-error list; (b) dir-is-a-file typed calibration
 * leg (the failure shape itself was already typed pre-fix — this leg pins the
 * unchanged code/message, it is the declared calibration, not the red evidence;
 * the red evidence is the compile-red three-piece documented in the ledger).
 */
class TestRocksDBOpenFailureCleanupR404 {

    private static final long MB = 1024L * 1024L;

    @TempDir
    File tempDir;

    @Test
    void failureCleanupHelperClosesHandlesThenPresetThenOptions() throws Exception {
        RocksDBKeyedStateBackend<String> carrier = new RocksDBKeyedStateBackend<>(
                new File(tempDir, "carrier-" + System.nanoTime()).getAbsolutePath(),
                String.class, 16, new RocksDBOptionConfig(4 * MB, 2, 8 * MB, 4 * MB, 10d));
        try {
            // ---- preset trio + option objects: capture the references BEFORE the
            // helper (it nulls the fields with the close() discipline) ----
            org.rocksdb.WriteBufferManager wbm = carrier.getWriteBufferManagerForTest();
            org.rocksdb.Cache cache = carrier.getBlockCacheForTest();
            org.rocksdb.Filter bloom = carrier.getBloomFilterForTest();
            ColumnFamilyOptions cfOpts = carrier.getCfOptionsForTest();
            DBOptions dbOpts = carrier.getDbOptionsForTest();
            assertTrue(wbm.isOwningHandle(), "precondition: WBM owned");
            assertTrue(cache.isOwningHandle(), "precondition: cache owned");
            assertTrue(bloom.isOwningHandle(), "precondition: bloom owned");
            assertTrue(cfOpts.isOwningHandle(), "precondition: cfOptions owned");
            assertTrue(dbOpts.isOwningHandle(), "precondition: dbOptions owned");

            List<RuntimeException> presetCleanupErrors =
                    carrier.closeResourcesAfterFailedOpen(new ArrayList<>());
            assertEquals(Collections.emptyList(), presetCleanupErrors,
                    "a healthy cleanup must collect no errors");
            assertFalse(wbm.isOwningHandle(),
                    "the WriteBufferManager must be released (W-06 red⑥ white-box)");
            assertFalse(cache.isOwningHandle(),
                    "the shared block cache must be released (W-06 red⑥ white-box)");
            assertFalse(bloom.isOwningHandle(),
                    "the bloom filter must be released (W-06 red⑥ white-box)");
            assertFalse(cfOpts.isOwningHandle(),
                    "cfOptions must be released (W-06 red⑥ white-box)");
            assertFalse(dbOpts.isOwningHandle(),
                    "dbOptions must be released (W-06 red⑥ white-box)");

            // ---- half-filled handles list from a real open (the open-time shape);
            // the second helper pass only touches the passed handles (fields are
            // already nulled — idempotent) ----
            File rawDir = new File(tempDir, "raw-" + System.nanoTime());
            rawDir.mkdirs();
            List<ColumnFamilyDescriptor> descriptors = Arrays.asList(
                    new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()),
                    new ColumnFamilyDescriptor("state-a".getBytes(StandardCharsets.UTF_8),
                            new ColumnFamilyOptions()));
            List<ColumnFamilyHandle> handles = new ArrayList<>();
            DBOptions rawDbOptions = new DBOptions()
                    .setCreateIfMissing(true)
                    .setCreateMissingColumnFamilies(true);
            RocksDB rawDb = RocksDB.open(rawDbOptions, rawDir.getAbsolutePath(), descriptors, handles);
            try {
                assertEquals(2, handles.size(), "both column families must be open");
                assertTrue(handles.get(1).isOwningHandle(), "precondition: handle is owned");

                List<RuntimeException> cleanupErrors =
                        carrier.closeResourcesAfterFailedOpen(handles);
                assertEquals(Collections.emptyList(), cleanupErrors,
                        "a healthy cleanup must collect no errors");
                for (ColumnFamilyHandle handle : handles) {
                    assertFalse(handle.isOwningHandle(),
                            "the half-filled handles list must be released by the cleanup helper "
                                    + "(W-06 red⑥ white-box, native ownership gone)");
                }
            } finally {
                // handles-before-db is the correct rocksdbjni close order; the helper
                // already closed them (idempotent) — close the DB itself now
                rawDb.close();
                rawDbOptions.close();
            }
        } finally {
            carrier.close();
        }
    }

    @Test
    void dirIsAFileFailsTypedWithUnchangedCarrier() throws Exception {
        File notADir = new File(tempDir, "plain-file-" + System.nanoTime());
        Files.writeString(notADir.toPath(), "occupying the db path", StandardCharsets.UTF_8);

        StreamException failure = assertThrows(StreamException.class,
                () -> new RocksDBKeyedStateBackend<>(
                        notADir.getAbsolutePath(), String.class, 16, new RocksDBOptionConfig()),
                "opening over a plain file must fail typed (calibration leg — the red "
                        + "evidence for W-06 is the compile-red three-piece + leg a)");
        assertTrue(String.valueOf(failure).contains("Failed to open RocksDB at"),
                "the typed carrier (code/message/param) must stay unchanged, got: " + failure);
    }
}
