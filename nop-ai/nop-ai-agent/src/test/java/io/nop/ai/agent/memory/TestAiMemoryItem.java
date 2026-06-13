package io.nop.ai.agent.memory;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestAiMemoryItem {

    @Test
    void testDefaultValues() {
        AiMemoryItem item = new AiMemoryItem();

        assertEquals(0, item.getPriority());
        assertEquals(0, item.getTokenEstimate());
        assertFalse(item.isPinned());
        assertNull(item.getChecksum());
        assertNull(item.getLastAccessTime());
        assertEquals(0, item.getAccessCount());
    }

    @Test
    void testTokenEstimateFallbackWhenContentSet() {
        AiMemoryItem item = new AiMemoryItem();
        item.setContent("12345678");

        assertEquals(2, item.getTokenEstimate());
    }

    @Test
    void testTokenEstimateFallbackWhenContentNull() {
        AiMemoryItem item = new AiMemoryItem();

        assertEquals(0, item.getTokenEstimate());
    }

    @Test
    void testTokenEstimateExplicitValue() {
        AiMemoryItem item = new AiMemoryItem();
        item.setTokenEstimate(42);

        assertEquals(42, item.getTokenEstimate());
    }

    @Test
    void testTokenEstimateExplicitZero() {
        AiMemoryItem item = new AiMemoryItem();
        item.setTokenEstimate(0);

        assertEquals(0, item.getTokenEstimate());
    }

    @Test
    void testLastAccessTimeFallbackToCreateTime() {
        LocalDateTime ct = LocalDateTime.of(2026, 1, 1, 12, 0);
        AiMemoryItem item = new AiMemoryItem();
        item.setCreateTime(ct);

        assertEquals(ct, item.getLastAccessTime());
    }

    @Test
    void testLastAccessTimeExplicitValue() {
        LocalDateTime ct = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime lat = LocalDateTime.of(2026, 6, 12, 10, 0);
        AiMemoryItem item = new AiMemoryItem();
        item.setCreateTime(ct);
        item.setLastAccessTime(lat);

        assertEquals(lat, item.getLastAccessTime());
    }

    @Test
    void testLastAccessTimeNullWhenCreateTimeAlsoNull() {
        AiMemoryItem item = new AiMemoryItem();

        assertNull(item.getLastAccessTime());
    }

    @Test
    void testAllNewFieldsSetAndGet() {
        LocalDateTime ct = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime lat = LocalDateTime.of(2026, 6, 12, 10, 0);

        AiMemoryItem item = new AiMemoryItem();
        item.setPriority(5);
        item.setTokenEstimate(100);
        item.setPinned(true);
        item.setChecksum("abc123");
        item.setLastAccessTime(lat);
        item.setAccessCount(7);

        assertEquals(5, item.getPriority());
        assertEquals(100, item.getTokenEstimate());
        assertTrue(item.isPinned());
        assertEquals("abc123", item.getChecksum());
        assertEquals(lat, item.getLastAccessTime());
        assertEquals(7, item.getAccessCount());
    }

    @Test
    void testOriginalFieldsUnchanged() {
        LocalDateTime ct = LocalDateTime.of(2026, 1, 1, 12, 0);
        AiMemoryItem item = new AiMemoryItem();
        item.setKey("k1");
        item.setType("fact");
        item.setContent("hello world");
        item.setCreateTime(ct);

        assertEquals("k1", item.getKey());
        assertEquals("fact", item.getType());
        assertEquals("hello world", item.getContent());
        assertEquals(ct, item.getCreateTime());
    }
}
