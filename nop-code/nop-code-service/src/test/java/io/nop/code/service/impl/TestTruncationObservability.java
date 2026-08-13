package io.nop.code.service.impl;

import io.nop.code.core.graph.CallGraph;
import io.nop.code.core.graph.SymbolTable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WP-6 AR-136/168/177 + AR-76/61: truncation observability.
 *
 * <p>Single-shot capped queries must detect when a result hit the cap (size == cap ⇒ possibly more
 * rows exist) so a WARN can surface the silent truncation. The cache isTruncated flag must remain
 * consumer-readable so graph analysis can flag incomplete input.
 */
class TestTruncationObservability {

    @Test
    void isCappedDetectsTruncationThreshold() {
        assertTrue(CodeQueryService.isCapped(100, 100), "size == cap means the result may be truncated");
        assertTrue(CodeQueryService.isCapped(100, 1));
        assertFalse(CodeQueryService.isCapped(99, 100), "size < cap means the full result was returned");
        assertFalse(CodeQueryService.isCapped(0, 100));
        assertFalse(CodeQueryService.isCapped(50, 0), "cap of 0 means no cap applied");
    }

    @Test
    void cacheTruncatedFlagRemainsConsumerReadable() {
        SymbolTable table = new SymbolTable();
        assertFalse(table.isTruncated());
        table.setTruncated(true);
        assertTrue(table.isTruncated(), "consumer (CodeGraphService.warnIfCacheTruncated) reads this flag");

        CallGraph graph = new CallGraph();
        graph.setTruncated(true);
        assertTrue(graph.isTruncated(), "consumer (CodeGraphService.warnIfCacheTruncated) reads this flag");
    }
}
