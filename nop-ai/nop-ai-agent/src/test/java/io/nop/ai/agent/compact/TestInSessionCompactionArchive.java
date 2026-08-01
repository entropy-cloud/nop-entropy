package io.nop.ai.agent.compact;

import io.nop.ai.toolkit.api.ICompactionArchive;
import io.nop.ai.toolkit.compact.ShortRefHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InSessionCompactionArchive}: content-addressed put/get
 * (design §8.2 Decision B), deduplication (same content -> same hash,
 * single stored copy), and fail-fast on null/empty content
 * (Minimum Rules #24).
 */
public class TestInSessionCompactionArchive {

    @Test
    void putReturnsSha256HashAndGetReadsItBack() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        String content = "line1\nline2\nline3\n".repeat(100);

        String hash = archive.put(content);

        assertNotNull(hash);
        assertTrue(hash.startsWith(ShortRefHasher.ALGORITHM_PREFIX),
                "hash should use the sha256: prefix");
        assertEquals(ShortRefHasher.hash(content), hash,
                "put-returned hash must equal independently-computed hash");
        assertTrue(archive.contains(hash));
        assertEquals(content, archive.getByHash(hash),
                "getByHash must read back the exact original content");
    }

    @Test
    void deduplicationSameContentYieldsSameHashSingleCopy() {
        InSessionCompactionArchive archive = new InSessionCompactionArchive();
        String content = "duplicate-me-content";

        String hash1 = archive.put(content);
        String hash2 = archive.put(content);

        assertEquals(hash1, hash2,
                "identical content must yield identical hash (content addressing)");
        assertEquals(1, archive.size(),
                "identical content must be stored only once (deduplicated)");
        assertEquals(content, archive.getByHash(hash1));
    }

    @Test
    void differentContentYieldsDifferentHashes() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        String hash1 = archive.put("content-A");
        String hash2 = archive.put("content-B");
        assertFalse(hash1.equals(hash2), "different content must yield different hashes");
        assertEquals("content-A", archive.getByHash(hash1));
        assertEquals("content-B", archive.getByHash(hash2));
    }

    @Test
    void missingHashReturnsNullSoCallersFailLoud() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        assertNull(archive.getByHash("sha256:nonexistent"),
                "missing hash must return null so callers can detect invalid reference");
        assertFalse(archive.contains("sha256:nonexistent"));
    }

    @Test
    void putRejectsNullContentFailFast() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        assertThrows(IllegalArgumentException.class, () -> archive.put(null),
                "put(null) must fail fast, not silently store null (Minimum Rules #24)");
    }

    @Test
    void putRejectsEmptyContentFailFast() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        assertThrows(IllegalArgumentException.class, () -> archive.put(""),
                "put(\"\") must fail fast, not silently store empty content (Minimum Rules #24)");
    }

    @Test
    void getAndContainsRejectNullHashFailFast() {
        ICompactionArchive archive = new InSessionCompactionArchive();
        assertThrows(NullPointerException.class, () -> archive.getByHash(null));
        assertThrows(NullPointerException.class, () -> archive.contains(null));
    }

    @Test
    void emptyArchiveHasZeroSize() {
        InSessionCompactionArchive archive = new InSessionCompactionArchive();
        assertEquals(0, archive.size());
        assertDoesNotThrow(() -> archive.contains("sha256:anything"));
    }
}
