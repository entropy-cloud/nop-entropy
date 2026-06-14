package io.nop.ai.agent.security;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies the {@link ActionFingerprint} determinism and collision-resistance
 * properties (design §6.2/§6.3): identical inputs produce identical
 * fingerprints, canonical map serialization makes insertion-order irrelevant,
 * any differing input produces a differing fingerprint, null inputs are safe,
 * and the fingerprint has the fixed {@link ActionFingerprint#FINGERPRINT_HEX_LENGTH}.
 */
public class TestActionFingerprint {

    // ========================================================================
    // Determinism
    // ========================================================================

    @Test
    void identicalInputsProduceIdenticalFingerprints() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "/etc/passwd");
        args.put("mode", "rw");

        ActionFingerprint a = ActionFingerprint.compute("shell.exec", args, "/work", null);
        ActionFingerprint b = ActionFingerprint.compute("shell.exec", args, "/work", null);
        assertEquals(a, b, "identical inputs must produce identical fingerprints");
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.getValue(), b.getValue());
    }

    // ========================================================================
    // Canonical serialization — insertion order does not matter
    // ========================================================================

    @Test
    void argumentsKeyOrderDoesNotChangeFingerprint() {
        // Two maps with the same contents but different insertion orders.
        Map<String, Object> args1 = new LinkedHashMap<>();
        args1.put("zeta", "1");
        args1.put("alpha", "2");
        args1.put("middle", "3");

        Map<String, Object> args2 = new LinkedHashMap<>();
        args2.put("alpha", "2");
        args2.put("middle", "3");
        args2.put("zeta", "1");

        ActionFingerprint a = ActionFingerprint.compute("tool", args1, null, null);
        ActionFingerprint b = ActionFingerprint.compute("tool", args2, null, null);
        assertEquals(a, b, "arguments map insertion order must not affect the fingerprint (canonical serialization)");
    }

    @Test
    void criticalEnvKeyOrderDoesNotChangeFingerprint() {
        Map<String, String> env1 = new LinkedHashMap<>();
        env1.put("Z", "v1");
        env1.put("A", "v2");

        Map<String, String> env2 = new LinkedHashMap<>();
        env2.put("A", "v2");
        env2.put("Z", "v1");

        ActionFingerprint a = ActionFingerprint.compute("tool", null, null, env1);
        ActionFingerprint b = ActionFingerprint.compute("tool", null, null, env2);
        assertEquals(a, b, "criticalEnv map insertion order must not affect the fingerprint");
    }

    // ========================================================================
    // Differing inputs produce differing fingerprints
    // ========================================================================

    @Test
    void differentActionKindProducesDifferentFingerprint() {
        Map<String, Object> args = Map.of("path", "/a");
        ActionFingerprint a = ActionFingerprint.compute("read-file", args, null, null);
        ActionFingerprint b = ActionFingerprint.compute("write-file", args, null, null);
        assertNotEquals(a, b, "different actionKind must produce different fingerprints");
    }

    @Test
    void differentArgumentsProduceDifferentFingerprint() {
        // This is the property that makes legitimate follow-up (changed
        // parameters) automatically not a blind retry.
        ActionFingerprint a = ActionFingerprint.compute("write-file", Map.of("path", "/a/b.txt"), null, null);
        ActionFingerprint b = ActionFingerprint.compute("write-file", Map.of("path", "/a/c.txt"), null, null);
        assertNotEquals(a, b, "different arguments must produce different fingerprints (legitimate follow-up)");
    }

    @Test
    void differentWorkDirProducesDifferentFingerprint() {
        Map<String, Object> args = Map.of("path", "/a");
        ActionFingerprint a = ActionFingerprint.compute("tool", args, "/work-a", null);
        ActionFingerprint b = ActionFingerprint.compute("tool", args, "/work-b", null);
        assertNotEquals(a, b, "different workDir must produce different fingerprints");
    }

    @Test
    void differentCriticalEnvProducesDifferentFingerprint() {
        ActionFingerprint a = ActionFingerprint.compute("tool", null, null, Map.of("TOKEN", "secret1"));
        ActionFingerprint b = ActionFingerprint.compute("tool", null, null, Map.of("TOKEN", "secret2"));
        assertNotEquals(a, b, "different criticalEnv must produce different fingerprints");
    }

    // ========================================================================
    // Null safety
    // ========================================================================

    @Test
    void nullArgumentsWorkDirAndCriticalEnvDoNotThrow() {
        ActionFingerprint fp = ActionFingerprint.compute(null, null, null, null);
        assertNotNull(fp.getValue(), "null inputs must not produce a null fingerprint");
        assertEquals(ActionFingerprint.FINGERPRINT_HEX_LENGTH, fp.getValue().length(),
                "fingerprint must be the fixed length even for all-null inputs");
    }

    @Test
    void emptyArgumentsMapHandledLikeNull() {
        ActionFingerprint a = ActionFingerprint.compute("tool", null, null, null);
        ActionFingerprint b = ActionFingerprint.compute("tool", Map.of(), null, null);
        assertEquals(a, b, "empty arguments map must behave like null");
    }

    // ========================================================================
    // Fixed length
    // ========================================================================

    @Test
    void fingerprintHasFixedHexLength() {
        Map<String, Object> args = Map.of("k1", "v1", "k2", 42, "k3", true);
        ActionFingerprint fp = ActionFingerprint.compute("shell.exec", args, "/some/long/work/dir",
                Map.of("CRITICAL", "env-value"));
        assertEquals(ActionFingerprint.FINGERPRINT_HEX_LENGTH, fp.getValue().length(),
                "fingerprint must be exactly " + ActionFingerprint.FINGERPRINT_HEX_LENGTH + " hex chars");
        assertEquals(true, fp.getValue().matches("[0-9a-f]+"),
                "fingerprint must be lowercase hex");
    }

    // ========================================================================
    // of() wrapper + value semantics
    // ========================================================================

    @Test
    void ofWrapsValue() {
        ActionFingerprint fp = ActionFingerprint.of("deadbeef");
        assertEquals("deadbeef", fp.getValue());
    }

    @Test
    void equalsHashCodeBasedOnValue() {
        ActionFingerprint a = ActionFingerprint.of("abc");
        ActionFingerprint b = ActionFingerprint.of("abc");
        ActionFingerprint c = ActionFingerprint.of("xyz");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(null, a);
        assertNotEquals("abc", a);
    }

    @Test
    void toStringContainsValue() {
        ActionFingerprint fp = ActionFingerprint.of("deadbeef");
        assertEquals(true, fp.toString().contains("deadbeef"));
    }
}
