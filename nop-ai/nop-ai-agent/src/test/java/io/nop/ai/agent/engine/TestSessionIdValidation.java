package io.nop.ai.agent.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link SessionIds} — the P0 path-traversal guard
 * (finding [13-15]). Proves the valid identifiers pass and every documented
 * rejection vector throws {@link NopAiAgentException} (fail-closed).
 */
public class TestSessionIdValidation {

    @TempDir
    Path tempDir;

    // ========================================================================
    // requireValidIdentifier — valid inputs pass
    // ========================================================================

    @Test
    void validUuidPassesIdentifierCheck() {
        String uuid = java.util.UUID.randomUUID().toString();
        String result = assertDoesNotThrow(() -> SessionIds.requireValidIdentifier(uuid));
        assertEquals(uuid, result, "A standard UUID must pass unchanged");
    }

    @Test
    void validAllowListIdPassesIdentifierCheck() {
        // Every char in [A-Za-z0-9_-] is accepted
        String id = "sess-1_AbcXYZ09";
        String result = assertDoesNotThrow(() -> SessionIds.requireValidIdentifier(id));
        assertEquals(id, result);
    }

    // ========================================================================
    // requireValidIdentifier — rejection vectors (all fail-closed)
    // ========================================================================

    @Test
    void nullRejectedByIdentifierCheck() {
        assertThrows(NopAiAgentException.class, () -> SessionIds.requireValidIdentifier(null),
                "null sessionId must be rejected (fail-closed)");
    }

    @Test
    void emptyRejectedByIdentifierCheck() {
        assertThrows(NopAiAgentException.class, () -> SessionIds.requireValidIdentifier(""),
                "empty sessionId must be rejected (fail-closed)");
    }

    @Test
    void pathTraversalDotDotSlashRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier("../etc/cron.d/exploit"),
                "'../' traversal must be rejected by the regex allow-list");
    }

    @Test
    void absolutePathRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier("/etc/x"),
                "Absolute path '/etc/x' must be rejected ('/' outside allow-list)");
    }

    @Test
    void backslashPathRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier("..\\x"),
                "Backslash traversal '..\\x' must be rejected");
    }

    @Test
    void nulByteRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier("evil\0root"),
                "NUL byte must be rejected");
    }

    @Test
    void whitespaceRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier("session id"),
                "Whitespace must be rejected");
    }

    @Test
    void dotLiteralRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier("."),
                "Literal '.' must be rejected ('.' outside allow-list)");
    }

    @Test
    void dotDotLiteralRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier(".."),
                "Literal '..' must be rejected");
    }

    @Test
    void unicodeCharRejected() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireValidIdentifier("会话-1"),
                "Unicode chars must be rejected");
    }

    // ========================================================================
    // requireContainedPath — valid + containment
    // ========================================================================

    @Test
    void containedPathReturnsNormalizedDirForValidId() {
        Path root = tempDir.resolve("root");
        Path result = assertDoesNotThrow(
                () -> SessionIds.requireContainedPath("sess-1", root));
        assertTrue(result.startsWith(root.normalize()),
                "Resolved path must stay inside the normalized root");
        assertTrue(result.endsWith("sess-1"));
    }

    @Test
    void containedPathRejectsNullRoot() {
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireContainedPath("sess-1", null),
                "null rootDirectory must be rejected");
    }

    @Test
    void containedPathRejectsInvalidIdBeforeTouchingFilesystem() {
        // Even with a valid root, a traversal id must be rejected (identifier
        // check runs first inside requireContainedPath).
        assertThrows(NopAiAgentException.class,
                () -> SessionIds.requireContainedPath("../escape", tempDir),
                "Traversal id must be rejected by the containment check");
    }
}
