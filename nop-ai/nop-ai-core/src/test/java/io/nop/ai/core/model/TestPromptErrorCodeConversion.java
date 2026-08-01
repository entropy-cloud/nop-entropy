package io.nop.ai.core.model;

import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static io.nop.ai.core.NopAiCoreErrors.ARG_PARSE_MODEL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_PROMPT_TEMPLATE_NULL;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_UNSUPPORTED_PARSE_FROM_RESPONSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused value-level tests for the error codes introduced by plan
 * 2026-08-01-0936-2: {@link PromptModel} null-template validation and
 * {@link ModelBasedPromptTemplate} unsupported parseFromResponse config.
 */
public class TestPromptErrorCodeConversion {

    @Test
    public void testNullTemplateRejectedWithErrorCode() {
        PromptModel model = new PromptModel();

        NopException ex = assertThrows(NopException.class, model::init);
        assertEquals(ERR_AI_PROMPT_TEMPLATE_NULL.getErrorCode(), ex.getErrorCode(),
                "null prompt template must carry ERR_AI_PROMPT_TEMPLATE_NULL");
        assertTrue(ex.getMessage().contains("prompt template is null"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testUnsupportedParseFromResponseRejectedWithErrorCode() {
        PromptOutputParseModel parseModel = new PromptOutputParseModel();
        PromptOutputModel output = new PromptOutputModel();
        output.setParseFromResponse(parseModel);

        ModelBasedPromptTemplate template = new ModelBasedPromptTemplate(new PromptModel());

        NopException ex = assertThrows(NopException.class,
                () -> template.parseOutput(null, output, null));
        assertEquals(ERR_AI_UNSUPPORTED_PARSE_FROM_RESPONSE.getErrorCode(), ex.getErrorCode(),
                "parseFromResponse without any parse strategy must carry the unsupported error code");
        assertNotNull(ex.getParam(ARG_PARSE_MODEL),
                "the offending parse model must be attached as a param");
        assertTrue(ex.getMessage().contains("unsupported parseFromResponse"),
                "message must preserve the original verbatim text");
    }
}
