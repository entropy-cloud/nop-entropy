/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.rocksdb.incremental;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rocksdb.RocksDBException;

import io.nop.api.core.exceptions.NopException;
import io.nop.stream.core.checkpoint.incremental.IncrementalSnapshotResult;
import io.nop.stream.core.checkpoint.incremental.SharedStateHandle;
import io.nop.stream.core.checkpoint.storage.ISegmentStore;
import io.nop.stream.core.checkpoint.storage.LocalFileSegmentStore;
import io.nop.stream.core.common.state.ValueState;
import io.nop.stream.core.common.state.ValueStateDescriptor;
import io.nop.stream.core.common.state.backend.StateSnapshot;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.rocksdb.RocksDBKeyedStateBackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-02 / F-03 (Plan 2026-09-04-1326-1 Phase 3): RocksDB incremental task-local
 * lifecycle boundedness + restore-time physical integrity.
 *
 * <ul>
 *   <li><b>F-02 boundedness</b>: N &gt; K successive incremental checkpoints leave at
 *       most K task-local {@code cp-{id}} dirs (rolling retention, adjudication D3=(b));
 *       local disk usage does not grow linearly in the checkpoint count.</li>
 *   <li><b>F-02 restore correctness</b>: the newest checkpoint remains fully
 *       restorable after pruning (the retained dirs are exactly what restore needs).</li>
 *   <li><b>F-03 corruption injection ×3</b>: flipped SST byte / truncated (half-written)
 *       segment / truncated MANIFEST each fail the restore FAST with a typed error
 *       carrying the real cause — never a bare native exception, never silent wrong
 *       data.</li>
 * </ul>
 */
public class TestRocksDBIncrementalLifecycleAndIntegrity {

    private static final int MAX_P = 16;
    private static final ValueStateDescriptor<Long> COUNT =
            new ValueStateDescriptor<>("count", Long.class, 0L);

    @TempDir
    Path tempDir;

    private RocksDBKeyedStateBackend<String> newBackend(String sub, ISegmentStore store) throws IOException {
        java.io.File dir = tempDir.resolve(sub).toFile();
        dir.mkdirs();
        RocksDBKeyedStateBackend<String> backend = new RocksDBKeyedStateBackend<>(
                dir.getAbsolutePath(), String.class, MAX_P, null);
        backend.setIncrementalCheckpointEnabled(true);
        backend.setCheckpointBaseDir(tempDir.resolve(sub + "-ckp").toString());
        if (store != null) {
            backend.setSegmentStore(store);
        }
        return backend;
    }

    private static IncrementalSnapshotResult markerOf(StateSnapshot snapshot) {
        Object marker = snapshot.getStateData().get(IncrementalSnapshotResult.MARKER_KEY);
        assertTrue(marker instanceof IncrementalSnapshotResult, "incremental marker expected");
        return (IncrementalSnapshotResult) marker;
    }

    private ISegmentStore materialize(IncrementalSnapshotResult result) throws IOException {
        ISegmentStore store = new LocalFileSegmentStore(tempDir.resolve("seg-store"));
        for (SharedStateHandle h : result.getSstHandles()) {
            store.storeSegment(Path.of(h.getFilePath()), h.getContentHash());
        }
        return store;
    }

    private TreeSet<Long> cpIdsPresent(Path baseDir) throws IOException {
        TreeSet<Long> ids = new TreeSet<>();
        try (var s = Files.list(baseDir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p) && name.startsWith("cp-")) {
                    ids.add(Long.parseLong(name.substring(3)));
                }
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // F-02: task-local dirs bounded by rolling retention
    // ------------------------------------------------------------------

    @Test
    public void taskLocalCheckpointDirsStayBoundedAfterManySnapshots() throws Exception {
        Path baseDir = tempDir.resolve("bounded-ckp");
        try (RocksDBKeyedStateBackend<String> backend = newBackend("bounded", null)) {
            // N snapshots with state churn between them, N >> default retention (2)
            for (int cp = 1; cp <= 8; cp++) {
                ValueState<Long> state = backend.getState(COUNT);
                backend.setCurrentKey("key-" + cp);
                state.update(1000L + cp);
                StateSnapshot snap = backend.snapshotState();
                assertEquals(cp, markerOf(snap).getCheckpointId());
            }
        }

        int retention = RocksDBIncrementalSnapshotStrategy.localRetention();
        TreeSet<Long> present = cpIdsPresent(baseDir);
        assertTrue(present.size() <= retention,
                "task-local cp dirs must be bounded by retention " + retention + ", got: " + present);
        // and the retained dirs are the NEWEST ones (what restore resolves)
        assertEquals(retention, present.size(), "retention keeps exactly the newest K dirs");
        assertEquals(Long.valueOf(7L), present.first(), "second-newest dir retained");
        assertEquals(Long.valueOf(8L), present.last(), "newest dir retained");
    }

    @Test
    public void newestCheckpointStillRestoresAfterPruning() throws Exception {
        ISegmentStore store = new LocalFileSegmentStore(tempDir.resolve("seg-store"));
        StateSnapshot lastSnapshot = null;
        try (RocksDBKeyedStateBackend<String> backend = newBackend("prune-src", store)) {
            for (int cp = 1; cp <= 5; cp++) {
                ValueState<Long> state = backend.getState(COUNT);
                backend.setCurrentKey("key-" + cp);
                state.update(2000L + cp);
                lastSnapshot = backend.snapshotState();
            }
        }
        assertNotNull(lastSnapshot, "snapshots were taken");
        // materialize the LAST snapshot's segments like the coordinator does
        for (SharedStateHandle h : markerOf(lastSnapshot).getSstHandles()) {
            store.storeSegment(Path.of(h.getFilePath()), h.getContentHash());
        }

        // restore the newest snapshot into a fresh backend — pruning must not have
        // destroyed what the restore resolves (non-sst companions of the newest cp)
        try (RocksDBKeyedStateBackend<String> restored = newBackend("prune-dst", store)) {
            restored.restoreState(lastSnapshot);
            restored.setCurrentKey("key-5");
            assertEquals(2005L, restored.getState(COUNT).value(),
                    "newest checkpoint's data must survive rolling retention");
        }
    }

    // ------------------------------------------------------------------
    // F-03: corruption injection — typed fail-fast
    // ------------------------------------------------------------------

    /** Takes one snapshot, materializes segments, and returns the store. */
    private StateSnapshot snapshotAndMaterialize(String tag) throws Exception {
        StateSnapshot[] holder = new StateSnapshot[1];
        try (RocksDBKeyedStateBackend<String> backend = newBackend(tag + "-src", null)) {
            for (int i = 0; i < 30; i++) {
                ValueState<Long> state = backend.getState(COUNT);
                backend.setCurrentKey("key-" + i);
                state.update(3000L + i);
            }
            holder[0] = backend.snapshotState();
        }
        materialize(markerOf(holder[0]));
        return holder[0];
    }

    @Test
    public void flippedSstByteInStoreFailsTypedCorruption() throws Exception {
        StateSnapshot snapshot = snapshotAndMaterialize("flip");
        ISegmentStore store = new LocalFileSegmentStore(tempDir.resolve("seg-store"));

        // Flip one byte in the FIRST stored {hash}.sst (bit rot / tampering)
        SharedStateHandle victim = markerOf(snapshot).getSstHandles().get(0);
        Path segmentFile = store.getSegmentPath(victim.getContentHash());
        assertTrue(Files.exists(segmentFile), "victim segment materialized");
        byte[] bytes = Files.readAllBytes(segmentFile);
        bytes[bytes.length / 2] ^= 0x5A;
        Files.write(segmentFile, bytes);

        assertSegmentCorruptTyped(store, snapshot, victim.getContentHash());
    }

    @Test
    public void truncatedSegmentHalfWriteFailsTypedCorruption() throws Exception {
        StateSnapshot snapshot = snapshotAndMaterialize("halfwrite");
        ISegmentStore store = new LocalFileSegmentStore(tempDir.resolve("seg-store"));

        // Truncate a stored segment to ~40%: the crash-residue shape a non-atomic
        // storeSegment could previously leave at the FINAL name (F-03b's motivation)
        SharedStateHandle victim = markerOf(snapshot).getSstHandles().get(0);
        Path segmentFile = store.getSegmentPath(victim.getContentHash());
        byte[] bytes = Files.readAllBytes(segmentFile);
        try (var out = java.nio.channels.FileChannel.open(segmentFile,
                java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            out.write(java.nio.ByteBuffer.wrap(bytes, 0, (int) (bytes.length * 0.4)));
        }

        assertSegmentCorruptTyped(store, snapshot, victim.getContentHash());
    }

    private void assertSegmentCorruptTyped(ISegmentStore store, StateSnapshot snapshot, String victimHash)
            throws Exception {
        try (RocksDBKeyedStateBackend<String> backend = newBackend("corrupt-dst", store)) {
            StreamException ex = org.junit.jupiter.api.Assertions.assertThrows(StreamException.class,
                    () -> backend.restoreState(snapshot),
                    "corrupt shared segment must fail the restore fast");
            assertEquals(NopStreamErrors.ERR_STREAM_CHECKPOINT_SEGMENT_CORRUPT.getErrorCode(),
                    ((NopException) ex).getErrorCode(),
                    "typed segment-corruption error code required");
            String msg = String.valueOf(ex.getMessage());
            assertTrue(msg.contains(victimHash.substring(0, 8)),
                    "error must carry the corrupted segment identity: " + msg);
        }
    }

    @Test
    public void truncatedManifestFailsTypedOnRestoreOpen() throws Exception {
        StateSnapshot snapshot = snapshotAndMaterialize("manifest");
        ISegmentStore store = new LocalFileSegmentStore(tempDir.resolve("seg-store"));

        // Truncate the MANIFEST in the checkpoint's local non-sst companion dir: the
        // segment hashes still verify (SSTs untouched), so only the DB OPEN catches it —
        // the native failure must be wrapped typed, not escape as RocksDBException.
        Path nonSstDir = Path.of(markerOf(snapshot).getNonSstDir());
        Path manifest = findManifest(nonSstDir);
        byte[] bytes = Files.readAllBytes(manifest);
        try (var out = java.nio.channels.FileChannel.open(manifest,
                java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            out.write(java.nio.ByteBuffer.wrap(bytes, 0, Math.max(1, bytes.length / 3)));
        }

        try (RocksDBKeyedStateBackend<String> backend = newBackend("manifest-dst", store)) {
            StreamException ex = org.junit.jupiter.api.Assertions.assertThrows(StreamException.class,
                    () -> backend.restoreState(snapshot),
                    "truncated MANIFEST must fail the restore fast");
            assertEquals(NopStreamErrors.ERR_STREAM_STATE_ERROR.getErrorCode(),
                    ((NopException) ex).getErrorCode(),
                    "typed state error wrapping the native open failure required");
            // the native root cause stays chained (real reason visible)
            Throwable t = ex;
            boolean hasNativeCause = false;
            while (t != null) {
                if (t instanceof RocksDBException) {
                    hasNativeCause = true;
                    break;
                }
                t = t.getCause();
            }
            assertTrue(hasNativeCause, "native RocksDBException root cause must stay chained");
        }
    }

    private static Path findManifest(Path nonSstDir) throws IOException {
        Path fallback = null;
        try (var s = Files.list(nonSstDir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String name = p.getFileName().toString();
                if (name.startsWith("MANIFEST-")) {
                    return p;
                }
                if (fallback == null && Files.isRegularFile(p)
                        && !name.equals(RocksDBIncrementalSnapshotStrategy.SST_NAME_MAP_FILE)) {
                    fallback = p;
                }
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------
    // F-03b: LocalFileSegmentStore atomic write
    // ------------------------------------------------------------------

    @Test
    public void segmentStoreNeverLeavesTempOrPartialFilesAtFinalName() throws Exception {
        // A complete write lands the full bytes at the final name and no temp residue
        LocalFileSegmentStore store = new LocalFileSegmentStore(tempDir.resolve("atomic-store"));
        Path source = tempDir.resolve("src.sst");
        Files.write(source, "hello-segment-bytes".getBytes(StandardCharsets.UTF_8));
        String hash = io.nop.stream.core.checkpoint.incremental.SstFileChecksum.sha256Hex(source);

        store.storeSegment(source, hash);

        Path target = store.getSegmentPath(hash);
        assertTrue(Files.exists(target), "segment present at final name");
        assertEquals(hash, io.nop.stream.core.checkpoint.incremental.SstFileChecksum.sha256Hex(target),
                "segment content complete at final name");
        try (var s = Files.list(target.getParent())) {
            List<Path> residue = s.filter(p -> p.getFileName().toString().contains(".tmp-")).toList();
            assertTrue(residue.isEmpty(), "no temp residue after a successful store: " + residue);
        }

        // Re-storing the same hash is a no-op (content-addressed reuse); the final file
        // is untouched — the exists-short-circuit is sound because presence == complete
        byte[] before = Files.readAllBytes(target);
        Path other = tempDir.resolve("other.sst");
        Files.write(other, "hello-segment-bytes".getBytes(StandardCharsets.UTF_8));
        store.storeSegment(other, hash);
        assertEquals(before.length, Files.readAllBytes(target).length, "reuse must not rewrite the segment");
    }
}
