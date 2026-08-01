package io.nop.ai.core.api.messages;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.ai.core.NopAiCoreErrors.ARG_ROLE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_UNKNOWN_ROLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused value-level test for the error code introduced by plan
 * 2026-08-01-0936-2: {@link AiMessage#create(String)} unknown-role rejection.
 */
public class TestAiMessageErrorCode {

    @Test
    public void testUnknownRoleRejectedWithErrorCode() {
        NopException ex = assertThrows(NopException.class, () -> AiMessage.create("bogus-role"));
        assertEquals(ERR_AI_UNKNOWN_ROLE.getErrorCode(), ex.getErrorCode(),
                "unknown role must carry ERR_AI_UNKNOWN_ROLE");
        assertEquals("bogus-role", ex.getParam(ARG_ROLE),
                "the offending role must be attached as a param");
        assertTrue(ex.getMessage().contains("unknown role:bogus-role"),
                "message must preserve the original verbatim text");
    }
}
