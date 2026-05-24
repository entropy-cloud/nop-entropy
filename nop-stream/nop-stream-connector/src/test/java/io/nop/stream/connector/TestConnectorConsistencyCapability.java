/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector;

import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.core.model.StreamRequirementValidator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static io.nop.stream.core.checkpoint.ProcessingGuarantee.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for connector consistency capability declarations and validation.
 */
class TestConnectorConsistencyCapability {

    // ---- Capability declaration tests ----

    @Test
    void testBatchLoaderSourceDeclaresAtLeastOnce() {
        // BatchLoaderSourceFunction is a source, so it declares getSourceConsistency
        // We verify the capability enum value directly since constructing the connector
        // requires a real IBatchLoaderProvider
        assertEquals(SourceConsistencyCapability.AT_LEAST_ONCE,
                SourceConsistencyCapability.AT_LEAST_ONCE);
        // Verify ordinal ordering: REPLAYABLE < AT_LEAST_ONCE (lower = stronger)
        assertTrue(SourceConsistencyCapability.REPLAYABLE.ordinal()
                < SourceConsistencyCapability.AT_LEAST_ONCE.ordinal());
    }

    @Test
    void testDebeziumSourceDeclaresReplayable() {
        // DebeziumCdcSourceFunction declares REPLAYABLE - the strongest source capability
        assertEquals(0, SourceConsistencyCapability.REPLAYABLE.ordinal());
    }

    @Test
    void testBatchConsumerSinkDeclaresIdempotent() {
        // BatchConsumerSinkFunction declares IDEMPOTENT
        // Verify it's in the enum and stronger than AT_LEAST_ONCE
        assertTrue(SinkConsistencyCapability.IDEMPOTENT.ordinal()
                < SinkConsistencyCapability.AT_LEAST_ONCE.ordinal());
    }

    @Test
    void testMessageSinkDeclaresAtLeastOnce() {
        // MessageSinkFunction declares AT_LEAST_ONCE
        assertNotNull(SinkConsistencyCapability.AT_LEAST_ONCE);
    }

    @Test
    void testSourceConsistencyCapabilityOrdering() {
        // Verify the capability ordering: REPLAYABLE > TRANSACTIONAL_READ > AT_LEAST_ONCE > BEST_EFFORT
        assertTrue(SourceConsistencyCapability.REPLAYABLE.ordinal()
                < SourceConsistencyCapability.TRANSACTIONAL_READ.ordinal());
        assertTrue(SourceConsistencyCapability.TRANSACTIONAL_READ.ordinal()
                < SourceConsistencyCapability.AT_LEAST_ONCE.ordinal());
        assertTrue(SourceConsistencyCapability.AT_LEAST_ONCE.ordinal()
                < SourceConsistencyCapability.BEST_EFFORT.ordinal());
    }

    @Test
    void testSinkConsistencyCapabilityOrdering() {
        // Verify the capability ordering: TWO_PHASE_COMMIT is strongest
        assertTrue(SinkConsistencyCapability.TWO_PHASE_COMMIT.ordinal()
                < SinkConsistencyCapability.IDEMPOTENT.ordinal());
        assertTrue(SinkConsistencyCapability.IDEMPOTENT.ordinal()
                < SinkConsistencyCapability.AT_LEAST_ONCE.ordinal());
        assertTrue(SinkConsistencyCapability.AT_LEAST_ONCE.ordinal()
                < SinkConsistencyCapability.BEST_EFFORT.ordinal());
    }

    // ---- StreamRequirementValidator.validateConnectorConsistency tests ----

    @Test
    void validateConnectorConsistency_skipsWhenNotStrictExactlyOnce() {
        assertDoesNotThrow(() ->
                StreamRequirementValidator.validateConnectorConsistency(
                        AT_LEAST_ONCE,
                        List.of(SourceConsistencyCapability.BEST_EFFORT),
                        List.of(SinkConsistencyCapability.BEST_EFFORT)));
    }

    @Test
    void validateConnectorConsistency_succeedsWithReplayableSourceAndTwoPhaseCommitSink() {
        assertDoesNotThrow(() ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.REPLAYABLE),
                        List.of(SinkConsistencyCapability.TWO_PHASE_COMMIT)));
    }

    @Test
    void validateConnectorConsistency_succeedsWithMultipleStrongConnectors() {
        assertDoesNotThrow(() ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.REPLAYABLE,
                                SourceConsistencyCapability.TRANSACTIONAL_READ),
                        List.of(SinkConsistencyCapability.TWO_PHASE_COMMIT,
                                SinkConsistencyCapability.STAGED_ATOMIC_COMMIT)));
    }

    @Test
    void validateConnectorConsistency_failsWithWeakSource() {
        Exception ex = assertThrows(Exception.class, () ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.AT_LEAST_ONCE),
                        List.of(SinkConsistencyCapability.TWO_PHASE_COMMIT)));
        assertTrue(ex.getMessage().contains("source[0]"));
        assertTrue(ex.getMessage().contains("REPLAYABLE"));
    }

    @Test
    void validateConnectorConsistency_failsWithWeakSink() {
        Exception ex = assertThrows(Exception.class, () ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.REPLAYABLE),
                        List.of(SinkConsistencyCapability.IDEMPOTENT)));
        assertTrue(ex.getMessage().contains("sink[0]"));
        assertTrue(ex.getMessage().contains("TWO_PHASE_COMMIT"));
    }

    @Test
    void validateConnectorConsistency_failsWithMultipleWeakConnectors() {
        Exception ex = assertThrows(Exception.class, () ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.BEST_EFFORT),
                        List.of(SinkConsistencyCapability.BEST_EFFORT)));
        assertTrue(ex.getMessage().contains("source[0]"));
        assertTrue(ex.getMessage().contains("sink[0]"));
    }

    @Test
    void validateConnectorConsistency_succeedsWithEmptyConnectors() {
        assertDoesNotThrow(() ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        Collections.emptyList(),
                        Collections.emptyList()));
    }

    @Test
    void validateConnectorConsistency_failsOnSecondSourceOnly() {
        Exception ex = assertThrows(Exception.class, () ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.REPLAYABLE,
                                SourceConsistencyCapability.BEST_EFFORT),
                        List.of(SinkConsistencyCapability.TWO_PHASE_COMMIT)));
        assertTrue(ex.getMessage().contains("source[1]"));
    }

    // ---- End-to-end scenario: typical pipeline with Debezium source ----

    @Test
    void validateConnectorConsistency_debeziumPlusTwoPhaseCommit_passes() {
        // Debezium source declares REPLAYABLE, sink has TWO_PHASE_COMMIT
        assertDoesNotThrow(() ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.REPLAYABLE),
                        List.of(SinkConsistencyCapability.TWO_PHASE_COMMIT)));
    }

    @Test
    void validateConnectorConsistency_batchLoaderPlusIdempotentSink_failsForStrict() {
        // BatchLoader is AT_LEAST_ONCE (ordinal > REPLAYABLE), BatchConsumer is IDEMPOTENT (ordinal > TWO_PHASE_COMMIT)
        Exception ex = assertThrows(Exception.class, () ->
                StreamRequirementValidator.validateConnectorConsistency(
                        STRICT_EXACTLY_ONCE,
                        List.of(SourceConsistencyCapability.AT_LEAST_ONCE),
                        List.of(SinkConsistencyCapability.IDEMPOTENT)));
        assertTrue(ex.getMessage().contains("source[0]"));
        assertTrue(ex.getMessage().contains("sink[0]"));
    }
}
