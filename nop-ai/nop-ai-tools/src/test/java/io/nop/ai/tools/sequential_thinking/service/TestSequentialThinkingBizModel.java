package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.core.AiCoreErrors;
import io.nop.ai.tools.sequential_thinking.model.ProcessThoughtRequest;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestSequentialThinkingBizModel {

    @Test
    public void testErrorCodeUsedForEmptyThought() {
        SequentialThinkingBizModel bizModel = new SequentialThinkingBizModel();
        ProcessThoughtRequest request = new ProcessThoughtRequest();
        request.setThought("");
        request.setThoughtNumber(1);
        request.setTotalThoughts(1);
        request.setStage("test");

        NopException thrown = assertThrows(NopException.class, () -> {
            bizModel.processThought(request, null);
        });
        assertEquals(AiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT.getErrorCode(), thrown.getErrorCode());
    }

    @Test
    public void testErrorCodeForNegativeThoughtNumber() {
        SequentialThinkingBizModel bizModel = new SequentialThinkingBizModel();
        ProcessThoughtRequest request = new ProcessThoughtRequest();
        request.setThought("valid thought");
        request.setThoughtNumber(-1);
        request.setTotalThoughts(5);

        NopException thrown = assertThrows(NopException.class, () -> {
            bizModel.processThought(request, null);
        });
        assertEquals(AiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT.getErrorCode(), thrown.getErrorCode());
    }

    @Test
    public void testErrorCodeForNullStage() {
        SequentialThinkingBizModel bizModel = new SequentialThinkingBizModel();
        ProcessThoughtRequest request = new ProcessThoughtRequest();
        request.setThought("valid thought");
        request.setThoughtNumber(1);
        request.setTotalThoughts(1);
        request.setStage(null);

        NopException thrown = assertThrows(NopException.class, () -> {
            bizModel.processThought(request, null);
        });
        assertEquals("nop.err.ai.tools.invalid-thought", thrown.getErrorCode());
    }
}
