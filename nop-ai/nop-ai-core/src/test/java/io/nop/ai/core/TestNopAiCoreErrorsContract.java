package io.nop.ai.core;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.core.api.support.Media;
import io.nop.ai.core.mock.MockChatService;
import io.nop.ai.core.routing.CandidateHealthProvider;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static io.nop.ai.core.NopAiCoreErrors.ARG_DETAIL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_CORE_INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Error-code test nails for the two core-side codes introduced by plan 356
 * (M5-P1 round-1 audit finding 1): {@code ERR_AI_CORE_INVALID_ARGUMENT},
 * {@code ERR_AI_CORE_INVALID_STATE}. Before this test the codes had ~26
 * throw sites and zero {@code getErrorCode()} assertions, so the code value /
 * parameter contract could drift silently.
 *
 * <p>Each test triggers a REAL production throw site through its public entry
 * point (not a test stub) and asserts the error-code value, the
 * {@code nop.err.ai.core.*} id string, the {@code detail} param, and that
 * the message template renders with the param (Minimum Rules #22/#23 — value
 * contract, not just exception type).
 *
 * <p>Representative throw-site inventory (Proof, plan M5-P1 Phase 1):
 * <ul>
 *   <li>{@code ERR_AI_CORE_INVALID_ARGUMENT} — routing path:
 *       routing/CandidateHealthProvider.java:32 (null candidate) — the only
 *       throw site for this code in main.</li>
 *   <li>{@code ERR_AI_CORE_INVALID_STATE} — service path:
 *       mock/MockChatService.java:55 (ResponseProvider not set),
 *       mock/InMemoryResponseProvider.java:170 (no mock response available),
 *       mock/FileSystemResponseProvider.java:91 (mock timeout); api path:
 *       api/support/Media.java:149 (data is not byte[]); reliability path:
 *       reliability/ThresholdBreaker.java:141 (unknown circuit state);
 *       file path: file/FileDiffApplier.java:139 (application position lost).</li>
 * </ul>
 */
public class TestNopAiCoreErrorsContract {

    private static void assertCoreCode(NopAiCoreException ex, io.nop.api.core.exceptions.ErrorCode code,
                                       String expectedId, String detailValue) {
        assertEquals(code.getErrorCode(), ex.getErrorCode(),
                "thrown exception must carry the ErrorCode value of " + expectedId);
        assertEquals(expectedId, ex.getErrorCode(),
                "error-code id must follow the nop.err.ai.core.* dotted convention");
        assertNotNull(ex.getDescription(), "description template must be present");
        assertEquals(detailValue, ex.getParam(ARG_DETAIL),
                "detail param must be attached by the throw site");
        assertTrue(ex.getMessage().contains(detailValue),
                "message template must render the detail param. Got: " + ex.getMessage());
    }

    // ========================================================================
    // ERR_AI_CORE_INVALID_ARGUMENT
    // ========================================================================

    @Test
    void invalidArgumentFromCandidateHealthProvider() {
        CandidateHealthProvider provider = new CandidateHealthProvider(null, null);
        NopAiCoreException ex = assertThrows(NopAiCoreException.class,
                () -> provider.healthOf(null));
        assertCoreCode(ex, ERR_AI_CORE_INVALID_ARGUMENT, "nop.err.ai.core.invalid-argument",
                "candidate must not be null");
    }

    // ========================================================================
    // ERR_AI_CORE_INVALID_STATE
    // ========================================================================

    @Test
    void invalidStateFromMockChatServiceWithoutProvider() {
        MockChatService service = new MockChatService();
        NopAiCoreException ex = assertThrows(NopAiCoreException.class,
                () -> service.callAsync(new ChatRequest(), null));
        assertCoreCode(ex, ERR_AI_CORE_INVALID_STATE, "nop.err.ai.core.invalid-state",
                "ResponseProvider is not set");
    }

    @Test
    void invalidStateFromMediaNonByteData() throws Exception {
        Media media = new Media("image/png", new URL("http://localhost/mock.png"));
        NopAiCoreException ex = assertThrows(NopAiCoreException.class, media::getDataAsByteArray);
        assertCoreCode(ex, ERR_AI_CORE_INVALID_STATE, "nop.err.ai.core.invalid-state",
                "Media data is not a byte[]");
    }
}