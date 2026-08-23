package io.nop.ai.tools.sequential_thinking.service;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.tools.sequential_thinking.model.ProcessThoughtRequest;
import io.nop.ai.tools.sequential_thinking.model.ThoughtAnalysis;
import io.nop.ai.tools.sequential_thinking.model.ThoughtSummary;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.ServiceContextImpl;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

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
        assertEquals(NopAiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT.getErrorCode(), thrown.getErrorCode());
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
        assertEquals(NopAiCoreErrors.ERR_AI_TOOLS_INVALID_THOUGHT.getErrorCode(), thrown.getErrorCode());
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

    @Test
    public void testProcessThoughtSuccessPathPersistsAcrossStorage() throws Exception {
        // P3-MA1-013 regression: storage serialization previously always failed
        // (@DataBean missing on ThoughtSession + Instant unsupported by JsonTool strict mode),
        // so the success path never ran. Now processThought → addThought → JSON round-trip works.
        File dir = Files.createTempDirectory("nop-ai-bizmodel").toFile();
        try {
            SequentialThinkingBizModel bizModel = new SequentialThinkingBizModel();
            bizModel.setStorageDirPath(dir.getAbsolutePath());
            bizModel.init();

            ServiceContextImpl ctx = new ServiceContextImpl();
            ctx.setRequestHeader("nop-chat-session-Id", "bizmodel-test-session");

            ProcessThoughtRequest request = new ProcessThoughtRequest();
            request.setThought("decompose the problem");
            request.setThoughtNumber(1);
            request.setTotalThoughts(2);
            request.setStage("Analysis");
            request.setNextThoughtNeeded(true);

            ThoughtAnalysis analysis = bizModel.processThought(request, ctx);
            assertNotNull(analysis);
            assertEquals(0, analysis.getAnalysis().getRelatedThoughtsCount());

            ThoughtSummary summary = bizModel.generateSummary(ctx);
            assertNotNull(summary);
            assertTrue(new File(dir, "bizmodel-test-session.json").exists(),
                    "session file must persist under the configured storage dir");
        } finally {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files)
                    Files.deleteIfExists(f.toPath());
            }
            Files.deleteIfExists(dir.toPath());
        }
    }

    /**
     * P0 path-traversal regression (audit ai-toolkit-skills, D5): the
     * {@code nop-chat-session-Id} header value used to flow raw into
     * {@code new File(storageDir, sessionId + ".json")}. All three entry points
     * (processThought / generateSummary / clearHistory) must reject a traversal
     * header with {@code ERR_AI_SESSION_ID_INVALID} and leave the file system
     * untouched.
     */
    @Test
    public void testHeaderSessionIdPathTraversalRejected() throws Exception {
        File dir = Files.createTempDirectory("nop-ai-bizmodel").toFile();
        try {
            SequentialThinkingBizModel bizModel = new SequentialThinkingBizModel();
            bizModel.setStorageDirPath(dir.getAbsolutePath());
            bizModel.init();

            ServiceContextImpl ctx = new ServiceContextImpl();
            ctx.setRequestHeader("nop-chat-session-Id", "../../tmp/evil");

            ProcessThoughtRequest request = new ProcessThoughtRequest();
            request.setThought("harmless thought");
            request.setThoughtNumber(1);
            request.setTotalThoughts(1);
            request.setStage("Analysis");
            request.setNextThoughtNeeded(false);

            NopException ex = assertThrows(NopException.class, () -> bizModel.processThought(request, ctx));
            assertEquals(NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID.getErrorCode(), ex.getErrorCode());

            NopException exSummary = assertThrows(NopException.class, () -> bizModel.generateSummary(ctx));
            assertEquals(NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID.getErrorCode(), exSummary.getErrorCode());

            NopException exClear = assertThrows(NopException.class, () -> bizModel.clearHistory(ctx));
            assertEquals(NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID.getErrorCode(), exClear.getErrorCode());

            // no file or directory created under the configured storage dir
            File[] entries = dir.listFiles();
            assertNotNull(entries, "storage dir must still exist");
            assertEquals(0, entries.length, "storage dir must stay untouched for a malicious session header");
        } finally {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files)
                    Files.deleteIfExists(f.toPath());
            }
            Files.deleteIfExists(dir.toPath());
        }
    }
}
