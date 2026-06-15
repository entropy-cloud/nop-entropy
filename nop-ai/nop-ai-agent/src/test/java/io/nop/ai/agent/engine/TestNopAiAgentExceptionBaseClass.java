package io.nop.ai.agent.engine;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 196 (AUDIT-09-01) focused verification of the {@link NopAiAgentException}
 * base-class switch from {@code extends RuntimeException} to
 * {@code extends NopException}. Verifies the six in-scope behaviour points so
 * the module exception is now part of the framework unified exception system:
 *
 * <ol>
 *   <li><b>instanceof NopException</b>: {@code new NopAiAgentException("msg")}
 *       is an instance of {@link NopException} AND still an instance of
 *       {@link RuntimeException} (backward-compatible catch sites).</li>
 *   <li><b>getErrorCode preserves original text</b>: the original message
 *       string is recoverable via {@code getErrorCode()} — important so the
 *       ~100+ string-based throw sites still carry their diagnostic text.</li>
 *   <li><b>getMessage().contains compatibility</b>: the structured
 *       {@code getMessage()} returned by {@link NopException} embeds the
 *       original text as a substring, so the ~20 existing
 *       {@code ex.getMessage().contains("...")} assertions across the module
 *       remain green without modification.</li>
 *   <li><b>.param(...) chaining</b>: a {@code NopAiAgentException} can be
 *       constructed with a plain string and then chain {@code .param(k, v)}
 *       before being thrown — proving the module exception now participates in
 *       the framework structured-context mechanism (this capability did NOT
 *       exist when it extended {@code RuntimeException}).</li>
 *   <li><b>(ErrorCode) constructor</b>: the new {@code (ErrorCode)} ctor
 *       constructs an instance whose {@code getErrorCode()} returns the
 *       {@link ErrorCode#getErrorCode()} value (not the description, not the
 *       raw message).</li>
 *   <li><b>(String, Throwable) preserves cause</b>: the retained two-arg
 *       string ctor keeps the cause chain intact ({@code getCause()} non-null
 *       and equal to the supplied cause).</li>
 * </ol>
 *
 * <p>Point 4 (param chaining) and point 5 ((ErrorCode) ctor) are
 * <b>wiring verification</b> (Minimum Rules #23): they actually invoke the new
 * framework-integration capability at runtime, not just assert the type
 * exists. There is no silent no-op (Minimum Rules #24): every constructor
 * delegates to a real {@code super(...)} call.
 */
public class TestNopAiAgentExceptionBaseClass {

    // ========================================================================
    // Behaviour 1: instanceof NopException (and still instanceof RuntimeException)
    // ========================================================================

    /**
     * Behaviour 1: {@code new NopAiAgentException("msg")} is now both a
     * {@link NopException} (the fix) and still a {@link RuntimeException}
     * (backward compatibility — existing {@code catch (RuntimeException)} and
     * {@code catch (NopAiAgentException)} sites still match).
     */
    @Test
    void stringCtorIsNopExceptionAndRuntimeException() {
        NopAiAgentException ex = new NopAiAgentException("boom");

        assertTrue(ex instanceof NopException,
                "NopAiAgentException must now extend NopException (the plan-196 fix). Got class: "
                        + ex.getClass().getName());
        assertTrue(ex instanceof RuntimeException,
                "NopAiAgentException must still be a RuntimeException for backward compatibility. Got class: "
                        + ex.getClass().getName());
    }

    // ========================================================================
    // Behaviour 2: getErrorCode() preserves original message text
    // ========================================================================

    /**
     * Behaviour 2: the original message passed to the string ctor is
     * recoverable verbatim via {@code getErrorCode()}. This proves the ~100+
     * string-based throw sites still expose their diagnostic text through the
     * framework accessor (NopException.getErrorCode() returns
     * super.getMessage()).
     */
    @Test
    void getErrorCodeReturnsOriginalMessageText() {
        String original = "Session not found: sessionId=abc-123";
        NopAiAgentException ex = new NopAiAgentException(original);

        assertEquals(original, ex.getErrorCode(),
                "getErrorCode() must return the original message text passed to the string ctor. "
                        + "Got: " + ex.getErrorCode());
    }

    // ========================================================================
    // Behaviour 3: getMessage().contains(...) compatibility
    // ========================================================================

    /**
     * Behaviour 3: the structured {@link NopException#getMessage()} embeds the
     * original message text as a substring (inside the {@code errorCode=...}
     * segment), so all existing {@code ex.getMessage().contains("...")}
     * assertions across the module remain valid without modification. This is
     * the key backward-compatibility guarantee of the base-class switch.
     */
    @Test
    void getMessageContainsOriginalTextForBackwardCompatibility() {
        String original = "FileBackedSessionStore: rootDirectory must not be null";
        NopAiAgentException ex = new NopAiAgentException(original);

        String structured = ex.getMessage();
        assertTrue(structured.contains(original),
                "Structured getMessage() must embed the original message text as a substring so existing "
                        + ".contains() assertions remain green. Got: " + structured);
        // Also confirm the structured shape (className[seq=...,errorCode=...,params=...])
        assertTrue(structured.startsWith("NopAiAgentException["),
                "getMessage() must follow the NopException structured shape. Got: " + structured);
    }

    // ========================================================================
    // Behaviour 4: .param(...) chaining (wiring verification of new capability)
    // ========================================================================

    /**
     * Behaviour 4 (wiring verification, Minimum Rules #23): a
     * {@code NopAiAgentException} built from the string ctor can chain
     * {@code .param(k, v)} and then be thrown. The thrown instance is the
     * original {@code NopAiAgentException} (param returns the NopException
     * supertype, which IS-A RuntimeException and is throwable), and the param
     * is retrievable via {@code getParam(k)}. This capability did not exist
     * when the class extended {@code RuntimeException}.
     */
    @Test
    void stringCtorSupportsParamChainingAndIsThrowable() {
        NopException thrown;
        try {
            throw new NopAiAgentException("Agent name is invalid")
                    .param("agentName", "bad/name")
                    .param("reason", "must-match-allow-list");
            // The throw expression returns NopException (supertype), which IS-A
            // RuntimeException and is therefore throwable as-is.
        } catch (NopAiAgentException e) {
            thrown = e;
        } catch (NopException e) {
            thrown = e;
        }

        assertNotNull(thrown, "The chained .param(...) expression must be throwable and caught");
        assertEquals("bad/name", thrown.getParam("agentName"),
                "Chained param 'agentName' must be retrievable. Params: " + thrown.getParams());
        assertEquals("must-match-allow-list", thrown.getParam("reason"),
                "Chained param 'reason' must be retrievable. Params: " + thrown.getParams());
        // Original message text still recoverable after chaining.
        assertEquals("Agent name is invalid", thrown.getErrorCode());
    }

    // ========================================================================
    // Behaviour 5: (ErrorCode) constructor (wiring verification of new ctor)
    // ========================================================================

    /**
     * Behaviour 5 (wiring verification, Minimum Rules #23): the new
     * {@code (ErrorCode)} constructor builds an instance whose
     * {@code getErrorCode()} returns the {@link ErrorCode#getErrorCode()}
     * value (the stable error code), not the description and not null. This
     * proves the (ErrorCode) ctor is a real implementation (delegates to
     * {@code super(errorCode)}), not a silent no-op.
     */
    @Test
    void errorCodeCtorReturnsErrorCodeValue() {
        ErrorCode code = ErrorCode.define("nop.err.ai-agent.test-invalid-name",
                "Agent name is invalid: {agentName}", "agentName");

        NopAiAgentException ex = new NopAiAgentException(code);

        assertEquals("nop.err.ai-agent.test-invalid-name", ex.getErrorCode(),
                "(ErrorCode) ctor must surface ErrorCode.getErrorCode() via getErrorCode(). Got: "
                        + ex.getErrorCode());
        // The (ErrorCode) ctor path does not lose structured getMessage either.
        assertTrue(ex.getMessage().contains("nop.err.ai-agent.test-invalid-name"),
                "(ErrorCode) ctor path must also produce a structured getMessage embedding the code. Got: "
                        + ex.getMessage());
    }

    /**
     * Companion to behaviour 5: the {@code (ErrorCode, Throwable)} ctor also
     * surfaces the error code AND retains the cause chain.
     */
    @Test
    void errorCodeAndCauseCtorReturnsErrorCodeAndCause() {
        ErrorCode code = ErrorCode.define("nop.err.ai-agent.test-load-failed",
                "Failed to load agent model: {agentName}", "agentName");
        Throwable cause = new java.io.IOException("disk read error");

        NopAiAgentException ex = new NopAiAgentException(code, cause);

        assertEquals("nop.err.ai-agent.test-load-failed", ex.getErrorCode());
        assertEquals(cause, ex.getCause(),
                "(ErrorCode, Throwable) ctor must retain the supplied cause");
    }

    // ========================================================================
    // Behaviour 6: (String, Throwable) preserves cause chain
    // ========================================================================

    /**
     * Behaviour 6: the retained two-arg string ctor
     * {@code (String, Throwable)} keeps the cause chain intact —
     * {@code getCause()} returns the supplied cause (not null, not wrapped).
     * This is the backward-compat guarantee for the ~100+ existing
     * {@code throw new NopAiAgentException("...", e)} sites.
     */
    @Test
    void stringAndThrowableCtorPreservesCause() {
        Throwable cause = new IllegalStateException("underlying failure");
        NopAiAgentException ex = new NopAiAgentException("wrapper message", cause);

        assertEquals(cause, ex.getCause(),
                "(String, Throwable) ctor must retain the supplied cause verbatim");
        assertEquals("wrapper message", ex.getErrorCode(),
                "(String, Throwable) ctor must still surface the message via getErrorCode()");
    }

    /**
     * Sanity companion to behaviour 6: the single-arg {@code (String)} ctor
     * has no cause ( getCause() == null ), confirming the two ctors behave
     * distinctly and there is no accidental cause injection.
     */
    @Test
    void stringOnlyCtorHasNoCause() {
        NopAiAgentException ex = new NopAiAgentException("just a message");
        assertNull(ex.getCause(),
                "(String) ctor must have no cause. Got: " + ex.getCause());
    }
}
