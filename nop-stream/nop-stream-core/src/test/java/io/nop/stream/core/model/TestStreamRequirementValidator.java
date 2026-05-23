package io.nop.stream.core.model;

import io.nop.stream.core.exceptions.StreamException;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class TestStreamRequirementValidator {

    @Test
    void testValidateSucceedsWhenAllRequirementsSupported() {
        StreamComponents components = new StreamComponents();
        components.addRequirement(StreamRequirement.STATEFUL_PROCESSING);
        StreamModel model = new StreamModel(components, new java.util.LinkedHashMap<>());

        StreamBackendCapability capability = StreamBackendCapability.localRuntime();
        assertDoesNotThrow(() -> StreamRequirementValidator.validate(model, capability));
    }

    @Test
    void testValidateFailsWhenRequirementNotSupported() {
        StreamComponents components = new StreamComponents();
        components.addRequirement(StreamRequirement.DISTRIBUTED_EXECUTION);
        StreamModel model = new StreamModel(components, new java.util.LinkedHashMap<>());

        StreamBackendCapability capability = StreamBackendCapability.localRuntime();
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamRequirementValidator.validate(model, capability));
        assertTrue(ex.getMessage().contains("DISTRIBUTED_EXECUTION"));
    }

    @Test
    void testValidateStrictExactlyOnceFailsWithoutReplayableSource() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamRequirementValidator.validateStrictExactlyOnce(
                        EnumSet.of(StreamRequirement.STRICT_EXACTLY_ONCE),
                        false, true));
        assertTrue(ex.getMessage().contains("REPLAYABLE"));
    }

    @Test
    void testValidateStrictExactlyOnceFailsWithoutTwoPhaseCommit() {
        StreamException ex = assertThrows(StreamException.class,
                () -> StreamRequirementValidator.validateStrictExactlyOnce(
                        EnumSet.of(StreamRequirement.STRICT_EXACTLY_ONCE),
                        true, false));
        assertTrue(ex.getMessage().contains("TWO_PHASE_COMMIT"));
    }

    @Test
    void testValidateStrictExactlyOnceSucceedsWithAllCapabilities() {
        assertDoesNotThrow(() -> StreamRequirementValidator.validateStrictExactlyOnce(
                EnumSet.of(StreamRequirement.STRICT_EXACTLY_ONCE),
                true, true));
    }
}
