package io.nop.ai.rag;

import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.IVectorStore;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.rag.biz.NopAiRagBizModel;
import io.nop.ai.rag.search.RagSearchService;
import io.nop.ai.rag.vector.InMemoryVectorStore;
import io.nop.api.core.exceptions.NopException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * K3 end-to-end: ingest multiple docs → search top-K → synthesize, all driven
 * through NopAiRagBizModel action entry points (Rule #23 wiring evidence).
 */
class TestK3PipelineE2E {

    private NopAiRagBizModel bizModel;
    // C3-005 (plan 2520): handle on the chat stub so tests can switch response modes
    private StubChat stubChat;

    @BeforeEach
    void setUp() {
        IEmbeddingModel embedding = new StubEmbedding();
        IVectorStore<VectorData> store = new InMemoryVectorStore();
        stubChat = new StubChat();
        bizModel = new NopAiRagBizModel(stubChat, embedding, store);
    }

    @Test
    void ingestSearchSynthesizeFullLifecycle() {
        // ingest multiple docs
        Map<String, Object> r1 = bizModel.ingestDocument("idx", "doc1",
                "Nop platform uses reversible computation to transform software structures.");
        Map<String, Object> r2 = bizModel.ingestDocument("idx", "doc2",
                "The Nop engine supports delta customization and code generation.");
        assertEquals(1, r1.get("chunkCount"));
        assertNotNull(r1.get("docId"));

        // search
        List<Map<String, Object>> hits = bizModel.search("idx", "reversible computation", 5);
        assertFalse(hits.isEmpty());
        assertTrue(((Number) hits.get(0).get("score")).doubleValue() > 0);
        assertTrue(hits.get(0).get("content").toString().contains("reversible"));
    }

    @Test
    void searchReturnsTopKSorted() {
        bizModel.ingestDocument("idx", "doc1", "machine learning algorithms");
        bizModel.ingestDocument("idx", "doc2", "database query optimization");
        bizModel.ingestDocument("idx", "doc3", "machine learning deep neural networks");

        List<Map<String, Object>> hits = bizModel.search("idx", "machine learning", 2);
        assertEquals(2, hits.size());
        assertTrue(hits.get(0).get("content").toString().contains("machine learning"));
    }

    @Test
    void synthesizeProducesNonEmptyAnswer() {
        bizModel.ingestDocument("idx", "doc1", "Nop platform uses reversible computation.");
        String answer = bizModel.synthesize("idx", "What does Nop use?", 4);
        assertNotNull(answer);
        assertFalse(answer.isEmpty());
    }

    @Test
    void emptyDocumentFailsLoud() {
        assertThrows(Exception.class, () -> bizModel.ingestDocument("idx", "d", ""));
        assertThrows(Exception.class, () -> bizModel.ingestDocument("idx", "d", null));
    }

    @Test
    void emptyQueryFailsLoud() {
        bizModel.ingestDocument("idx", "doc1", "content here");
        assertThrows(Exception.class, () -> bizModel.search("idx", "", 4));
    }

    // ---- C3-005 (plan 2520): provider error must not be delivered as success ----

    @Test
    void synthesizeProviderErrorFailsLoud() {
        bizModel.ingestDocument("idx", "doc1", "Nop platform uses reversible computation.");
        stubChat.setMode(StubChat.Mode.ERROR);

        NopException ex = assertThrows(NopException.class,
                () -> bizModel.synthesize("idx", "What does Nop use?", 4),
                "C3-005: a provider error response must fail loud, not fold to an empty-string success");
        assertEquals("nop.err.ai.rag.synthesize-failed", ex.getCode());
        assertEquals("provider rate limit exceeded", ex.getParam("detail"),
                "provider error message must be carried on the exception");
        assertEquals("rate_limited", ex.getParam("errorCode"));
    }

    @Test
    void synthesizeLegalEmptyAnswerStillReturnsEmptyString() {
        bizModel.ingestDocument("idx", "doc1", "Nop platform uses reversible computation.");
        stubChat.setMode(StubChat.Mode.EMPTY_SUCCESS);

        String answer = bizModel.synthesize("idx", "What does Nop use?", 4);
        assertEquals("", answer,
                "a successful response with no assistant text is a legal empty answer, not an error");
    }

    // ---- stubs ----

    static class StubEmbedding implements IEmbeddingModel {
        @Override
        public java.util.concurrent.CompletionStage<VectorData> embedAsync(AiDocument doc,
                io.nop.ai.core.api.embedding.EmbeddingOptions options) {
            return java.util.concurrent.CompletableFuture.completedFuture(embed(doc.getContent()));
        }

        @Override
        public java.util.concurrent.CompletionStage<List<VectorData>> embedAllAsync(
                List<AiDocument> docs, io.nop.ai.core.api.embedding.EmbeddingOptions options) {
            List<VectorData> result = new java.util.ArrayList<>();
            for (AiDocument doc : docs) {
                result.add(embed(doc.getContent()));
            }
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        }

        static VectorData embed(String text) {
            // deterministic keyword-hash vector (dim=16)
            String lower = text == null ? "" : text.toLowerCase();
            double[] v = new double[16];
            for (String token : lower.split("[^a-z0-9]+")) {
                if (token.length() < 2) continue;
                v[Math.floorMod(token.hashCode(), 16)] += 1;
            }
            // normalize
            double norm = 0;
            for (double x : v) norm += x * x;
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < 16; i++) v[i] /= norm;
            VectorData vd = new VectorData();
            vd.setVector(v);
            return vd;
        }
    }

    static class StubChat implements IChatService {

        // C3-005 (plan 2520): response modes for the synthesize error/empty-answer pins.
        // neverComplete / cancel-injection faces stay out of scope here (IND-06, G60).
        enum Mode {SUCCESS, ERROR, EMPTY_SUCCESS}

        private Mode mode = Mode.SUCCESS;

        void setMode(Mode mode) {
            this.mode = mode;
        }

        @Override
        public java.util.concurrent.CompletionStage<ChatResponse> callAsync(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken token) {
            if (mode == Mode.ERROR) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        ChatResponse.error(io.nop.ai.api.chat.ErrorClassification.RATE_LIMITED,
                                429, "rate_limited", "provider rate limit exceeded", 500L));
            }
            if (mode == Mode.EMPTY_SUCCESS) {
                return java.util.concurrent.CompletableFuture.completedFuture(new ChatResponse());
            }
            ChatResponse resp = new ChatResponse();
            String prompt = "";
            if (request.getMessages() != null) {
                for (var m : request.getMessages()) {
                    if (m.getContent() != null) prompt += m.getContent();
                }
            }
            // synthetic answer echoes keywords from the prompt
            resp.setMessages(List.of(new io.nop.ai.api.chat.messages.ChatAssistantMessage(
                    "Synthesized answer based on " + prompt.length() + " chars of context.")));
            return java.util.concurrent.CompletableFuture.completedFuture(resp);
        }

        @Override
        public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                io.nop.ai.api.chat.ChatRequest request, io.nop.api.core.util.ICancelToken token) {
            throw new UnsupportedOperationException();
        }
    }
}
