package io.nop.ai.tools;

import io.nop.ai.tools.file.FileToolBizModel;
import io.nop.ai.tools.sequential_thinking.model.ThoughtData;
import io.nop.ai.tools.sequential_thinking.model.ThoughtStage;
import io.nop.ai.tools.sequential_thinking.service.ThoughtAnalyzer;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static io.nop.ai.core.NopAiCoreErrors.ARG_VALUE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_MAX_RESULTS;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_PROJECT_NAME;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_STAGE;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_THOUGHT_EMPTY;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused value-level tests for the error codes introduced by plan
 * 2026-08-01-0936-3: the 7 bare {@code IllegalArgumentException} throws in
 * nop-ai-tools converted to {@code NopException + NopAiCoreErrors} codes.
 */
public class TestToolErrorCodeConversion {

    private static class ExposedFileToolBizModel extends FileToolBizModel {
        File exposeGetProjectDir(String projectName) {
            return getProjectDir(projectName);
        }
    }

    @Test
    public void testInvalidProjectNameCarriesErrorCodeAndVerbatimMessage() {
        ExposedFileToolBizModel model = new ExposedFileToolBizModel();
        model.setBaseDir(new File("target"));

        NopException ex = assertThrows(NopException.class, () -> model.exposeGetProjectDir("a*b"));
        assertEquals(ERR_AI_TOOLS_INVALID_PROJECT_NAME.getErrorCode(), ex.getErrorCode(),
                "invalid project name must carry ERR_AI_TOOLS_INVALID_PROJECT_NAME");
        assertEquals("a*b", ex.getParam(ARG_VALUE),
                "the offending project name must be attached as a param");
        assertTrue(ex.getMessage().contains("projectName must be valid file directory name:a*b"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testEmptyThoughtCarriesErrorCodeAndVerbatimMessage() {
        ThoughtData data = new ThoughtData();

        NopException ex = assertThrows(NopException.class, () -> data.setThought("   "));
        assertEquals(ERR_AI_TOOLS_THOUGHT_EMPTY.getErrorCode(), ex.getErrorCode(),
                "empty thought must carry ERR_AI_TOOLS_THOUGHT_EMPTY");
        assertTrue(ex.getMessage().contains("Thought cannot be empty"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testInvalidThoughtNumberCarriesErrorCodeAndVerbatimMessage() {
        ThoughtData data = new ThoughtData();

        NopException ex = assertThrows(NopException.class, () -> data.setThoughtNumber(0));
        assertEquals(ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER.getErrorCode(), ex.getErrorCode(),
                "non-positive thought number must carry ERR_AI_TOOLS_INVALID_THOUGHT_NUMBER");
        assertTrue(ex.getMessage().contains("Thought number must be positive"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testInvalidTotalThoughtsCarriesErrorCodeAndVerbatimMessage() {
        ThoughtData data = new ThoughtData();

        NopException ex = assertThrows(NopException.class, () -> data.setTotalThoughts(0));
        assertEquals(ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS.getErrorCode(), ex.getErrorCode(),
                "non-positive total thoughts must carry ERR_AI_TOOLS_INVALID_TOTAL_THOUGHTS");
        assertTrue(ex.getMessage().contains("Total thoughts must be positive"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testTotalThoughtsLessThanNumberCarriesErrorCodeAndVerbatimMessage() {
        ThoughtData data = new ThoughtData();
        data.setThoughtNumber(3);

        NopException ex = assertThrows(NopException.class, () -> data.setTotalThoughts(2));
        assertEquals(ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER.getErrorCode(), ex.getErrorCode(),
                "total thoughts below thought number must carry ERR_AI_TOOLS_TOTAL_THOUGHTS_LESS_THAN_NUMBER");
        assertTrue(ex.getMessage().contains("Total thoughts must be >= thought number"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testInvalidStageCarriesErrorCodeAndVerbatimMessage() {
        NopException ex = assertThrows(NopException.class, () -> ThoughtStage.fromString("BOGUS"));
        assertEquals(ERR_AI_TOOLS_INVALID_STAGE.getErrorCode(), ex.getErrorCode(),
                "unknown stage must carry ERR_AI_TOOLS_INVALID_STAGE");
        assertEquals("BOGUS", ex.getParam(ARG_VALUE),
                "the offending stage value must be attached as a param");
        assertTrue(ex.getMessage().contains("Invalid ThoughtStage: BOGUS"),
                "message must preserve the original verbatim text");
    }

    @Test
    public void testInvalidMaxResultsCarriesErrorCodeAndVerbatimMessage() {
        ThoughtAnalyzer analyzer = new ThoughtAnalyzer();
        ThoughtData current = new ThoughtData();
        current.setThought("t");
        current.setThoughtNumber(1);
        current.setTotalThoughts(1);

        NopException ex = assertThrows(NopException.class,
                () -> analyzer.findRelatedThoughts(current, List.of(), 0));
        assertEquals(ERR_AI_TOOLS_INVALID_MAX_RESULTS.getErrorCode(), ex.getErrorCode(),
                "non-positive maxResults must carry ERR_AI_TOOLS_INVALID_MAX_RESULTS");
        assertTrue(ex.getMessage().contains("maxResults must be positive"),
                "message must preserve the original verbatim text");
    }
}
