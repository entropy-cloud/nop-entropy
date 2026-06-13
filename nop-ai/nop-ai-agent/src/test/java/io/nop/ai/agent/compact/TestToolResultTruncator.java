package io.nop.ai.agent.compact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestToolResultTruncator {

    @Test
    void contentBelowThresholdPassesThrough() {
        String content = "short content";
        String result = ToolResultTruncator.truncate(content, 8000);
        assertEquals("short content", result);
    }

    @Test
    void contentAboveThresholdIsTruncated() {
        String content = "A".repeat(20000);
        String result = ToolResultTruncator.truncate(content, 8000);
        assertNotNull(result);
        assertTrue(result.contains("TRUNCATED"), "Should contain truncation marker");
        assertTrue(result.contains("characters removed"), "Should show truncated character count");
        assertTrue(result.length() < 8000, "Total length should be under threshold: " + result.length());
    }

    @Test
    void truncatedContentPreservesHeadAndTail() {
        String content = "H".repeat(7000) + "M".repeat(6000) + "T".repeat(7000);
        String result = ToolResultTruncator.truncate(content, 8000);
        assertTrue(result.startsWith("H"), "Should preserve head");
        assertTrue(result.endsWith("T"), "Should preserve tail");
    }

    @Test
    void truncationMarkerContainsCharacterCount() {
        String content = "X".repeat(20000);
        String result = ToolResultTruncator.truncate(content, 8000);
        int truncatedCount = 20000 - ToolResultTruncator.HEAD_CHARS - ToolResultTruncator.TAIL_CHARS;
        assertTrue(result.contains(String.valueOf(truncatedCount)),
                "Marker should contain truncated character count: " + truncatedCount);
    }

    @Test
    void nullContentPassesThrough() {
        assertNull(ToolResultTruncator.truncate(null, 8000));
    }

    @Test
    void emptyContentPassesThrough() {
        assertEquals("", ToolResultTruncator.truncate("", 8000));
    }

    @Test
    void nonTruncatableToolNotTruncated() {
        String content = "A".repeat(20000);
        String result = ToolResultTruncator.truncateIfAllowed(content, 8000, "ask-oracle");
        assertEquals(content, result, "ask-oracle tool results should not be truncated");
    }

    @Test
    void askHumanToolNotTruncated() {
        String content = "A".repeat(20000);
        String result = ToolResultTruncator.truncateIfAllowed(content, 8000, "ask-human");
        assertEquals(content, result, "ask-human tool results should not be truncated");
    }

    @Test
    void normalToolTruncated() {
        String content = "A".repeat(20000);
        String result = ToolResultTruncator.truncateIfAllowed(content, 8000, "bash");
        assertTrue(result.contains("TRUNCATED"), "Normal tool results should be truncated");
    }

    @Test
    void nullToolNameTruncates() {
        String content = "A".repeat(20000);
        String result = ToolResultTruncator.truncateIfAllowed(content, 8000, null);
        assertTrue(result.contains("TRUNCATED"), "Null tool name should still be truncated");
    }

    @Test
    void contentBelowThresholdNotTruncatedEvenForNormalTool() {
        String content = "short content";
        String result = ToolResultTruncator.truncateIfAllowed(content, 8000, "bash");
        assertEquals("short content", result);
    }

    @Test
    void exactThresholdNotTruncated() {
        String content = "A".repeat(8000);
        String result = ToolResultTruncator.truncate(content, 8000);
        assertEquals(content, result, "Content exactly at threshold should not be truncated");
    }

    @Test
    void oneOverThresholdIsTruncated() {
        String content = "A".repeat(8001);
        String result = ToolResultTruncator.truncate(content, 8000);
        assertTrue(result.contains("TRUNCATED"), "Content 1 over threshold should be truncated");
    }

    @Test
    void nonTruncatableToolsSetContainsExpectedTools() {
        assertTrue(ToolResultTruncator.NON_TRUNCATABLE_TOOLS.contains("ask-oracle"));
        assertTrue(ToolResultTruncator.NON_TRUNCATABLE_TOOLS.contains("ask-human"));
    }

    @Test
    void defaultThresholdIs8000() {
        assertEquals(8000, ToolResultTruncator.DEFAULT_TRUNCATION_THRESHOLD_CHARS);
    }
}
