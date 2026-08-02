/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLocalFileSegmentStore {

    @TempDir
    Path tmp;

    private LocalFileSegmentStore newStore() {
        return new LocalFileSegmentStore(tmp.resolve("ss"));
    }

    private Path write(String name, byte[] content) throws IOException {
        Path f = tmp.resolve(name);
        Files.write(f, content);
        return f;
    }

    private static final String HASH_A = "aabbccdd".repeat(8);   // 64 chars, prefix "aa"
    private static final String HASH_B = "bbccdd11".repeat(8);   // prefix "bb"

    // ---- storeSegment ----

    @Test
    void storeSegmentMaterializesFileContentAddressed() throws Exception {
        LocalFileSegmentStore store = newStore();
        Path src = write("a.sst", "content-a".getBytes());
        store.storeSegment(src, HASH_A);

        assertTrue(store.segmentExists(HASH_A));
        Path stored = store.getSegmentPath(HASH_A);
        assertTrue(Files.exists(stored));
        assertEquals("content-a", Files.readString(stored));
        // sharded by 2-char prefix
        assertTrue(stored.startsWith(tmp.resolve("ss").resolve("shared-state").resolve("aa")));
        assertTrue(stored.getFileName().toString().endsWith(HASH_A + ".sst"));
    }

    @Test
    void storeSegmentReusesExistingFileForSameHash() throws Exception {
        LocalFileSegmentStore store = newStore();
        Path src1 = write("a1.sst", "content-a".getBytes());
        store.storeSegment(src1, HASH_A);

        // A second store under the same hash with DIFFERENT source bytes must NOT overwrite
        // (content-addressed identity wins; the hash is the contract, not the source bytes).
        Path src2 = write("a2.sst", "DIFFERENT-BYTES".getBytes());
        store.storeSegment(src2, HASH_A);

        assertEquals("content-a", Files.readString(store.getSegmentPath(HASH_A)),
                "existing content-addressed file must be reused, not overwritten");
    }

    @Test
    void storeSegmentRejectsBadArgs() {
        LocalFileSegmentStore store = newStore();
        assertThrows(IllegalArgumentException.class, () -> store.storeSegment(null, HASH_A));
        assertThrows(IllegalArgumentException.class, () -> store.storeSegment(tmp.resolve("x"), null));
        assertThrows(IllegalArgumentException.class, () -> store.storeSegment(tmp.resolve("x"), "x")); // < 2 chars
    }

    // ---- segmentExists ----

    @Test
    void segmentExistsReturnsFalseForUnknownAndShortHash() throws Exception {
        LocalFileSegmentStore store = newStore();
        assertFalse(store.segmentExists(HASH_A));
        assertFalse(store.segmentExists(null));
        assertFalse(store.segmentExists("a"));
    }

    // ---- discardSegment ----

    @Test
    void discardSegmentDeletesFile() throws Exception {
        LocalFileSegmentStore store = newStore();
        store.storeSegment(write("a.sst", "x".getBytes()), HASH_A);
        assertTrue(store.segmentExists(HASH_A));

        store.discardSegment(HASH_A);
        assertFalse(store.segmentExists(HASH_A));
        assertFalse(Files.exists(store.getSegmentPath(HASH_A)));
    }

    @Test
    void discardSegmentIsIdempotentForMissing() throws Exception {
        LocalFileSegmentStore store = newStore();
        assertDoesNotThrow(() -> store.discardSegment(HASH_A));
        store.discardSegment(HASH_A); // double discard — no exception
        store.discardSegment(null);   // null safe
    }

    // ---- getSegmentPath ----

    @Test
    void getSegmentPathIsStableAndDoesNotRequireExistence() {
        LocalFileSegmentStore store = newStore();
        Path p = store.getSegmentPath(HASH_A);
        assertFalse(Files.exists(p));
        assertEquals(store.getSegmentPath(HASH_A), store.getSegmentPath(HASH_A));
    }

    @Test
    void getSegmentPathRejectsShortHash() {
        LocalFileSegmentStore store = newStore();
        assertThrows(IllegalArgumentException.class, () -> store.getSegmentPath("a"));
        assertThrows(IllegalArgumentException.class, () -> store.getSegmentPath(null));
    }

    // ---- distinct hashes get distinct physical files ----

    @Test
    void distinctHashesStoredSeparately() throws Exception {
        LocalFileSegmentStore store = newStore();
        store.storeSegment(write("a.sst", "a".getBytes()), HASH_A);
        store.storeSegment(write("b.sst", "b".getBytes()), HASH_B);

        assertTrue(store.segmentExists(HASH_A));
        assertTrue(store.segmentExists(HASH_B));
        assertThrows(IllegalArgumentException.class, () -> store.getSegmentPath(HASH_A.substring(0, 1)),
                "1-char hash must be rejected");
        assertEquals("a", Files.readString(store.getSegmentPath(HASH_A)));
        assertEquals("b", Files.readString(store.getSegmentPath(HASH_B)));
    }

    // ---- baseDir + name ----

    @Test
    void nullBaseDirRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LocalFileSegmentStore(null));
    }

    @Test
    void getNameIncludesBaseDir() {
        LocalFileSegmentStore store = new LocalFileSegmentStore(tmp.resolve("ss"));
        assertTrue(store.getName().contains("shared-state".replace("shared-state", "ss"))
                || store.getName().contains("ss"));
    }
}
