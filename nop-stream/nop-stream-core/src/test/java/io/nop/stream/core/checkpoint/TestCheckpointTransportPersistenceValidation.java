package io.nop.stream.core.checkpoint;

import io.nop.stream.core.exceptions.StreamException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 369 Phase 1 C4: capability entry "output-side in-flight data ×
 * transport persistence". A NON_DURABLE transport declaration combined with
 * {@code unalignedCheckpointEnabled=true} must fail fast at startup
 * ({@code validateUnalignedConfig}); the conservative DURABLE default never
 * triggers the check (existing configurations unaffected).
 */
class TestCheckpointTransportPersistenceValidation {

    @Test
    void testDefaultIsDurable() {
        CheckpointConfig config = new CheckpointConfig();
        assertEquals(CheckpointConfig.TransportPersistence.DURABLE, config.getTransportPersistence(),
                "The default declaration is the conservative DURABLE tier");
        assertDoesNotThrow(config::validateUnalignedConfig);
    }

    @Test
    void testNonDurableWithUnalignedFailsFast() {
        CheckpointConfig config = CheckpointConfig.builder()
                .unalignedCheckpointEnabled(true)
                .transportPersistence(CheckpointConfig.TransportPersistence.NON_DURABLE)
                .build();
        StreamException e = assertThrows(StreamException.class, config::validateUnalignedConfig,
                "non-durable transport × unaligned output must be rejected at startup");
        assertTrue(String.valueOf(e.getMessage()).contains("NON_DURABLE")
                        || String.valueOf(e).contains("NON_DURABLE"),
                "the failure message must name the transport declaration: " + e);
    }

    @Test
    void testNonDurableWithoutUnalignedIsAccepted() {
        CheckpointConfig config = CheckpointConfig.builder()
                .unalignedCheckpointEnabled(false)
                .transportPersistence(CheckpointConfig.TransportPersistence.NON_DURABLE)
                .build();
        assertDoesNotThrow(config::validateUnalignedConfig,
                "with unaligned disabled the output-side in-flight data does not depend on the transport");
    }

    @Test
    void testDurableDeclarationWithUnalignedIsAccepted() {
        CheckpointConfig config = CheckpointConfig.builder()
                .unalignedCheckpointEnabled(true)
                .transportPersistence(CheckpointConfig.TransportPersistence.DURABLE)
                .build();
        assertDoesNotThrow(config::validateUnalignedConfig);
    }

    @Test
    void testNullDeclarationFallsBackToDurable() {
        CheckpointConfig config = new CheckpointConfig();
        config.setTransportPersistence(null);
        assertEquals(CheckpointConfig.TransportPersistence.DURABLE, config.getTransportPersistence(),
                "a null declaration must fall back to the conservative DURABLE tier");
    }
}
