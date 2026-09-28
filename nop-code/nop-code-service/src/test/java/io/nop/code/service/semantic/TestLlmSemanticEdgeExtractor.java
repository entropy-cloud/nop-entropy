package io.nop.code.service.semantic;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.code.core.graph.SymbolTable;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.core.semantic.CodeSemanticEdge;
import io.nop.code.core.semantic.EdgeConfidence;
import io.nop.code.core.semantic.SemanticRelationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TestLlmSemanticEdgeExtractor {
    private SymbolTable symbolTable;
    private AtomicInteger chatCallCount;

    @BeforeEach
    void setUp() {
        symbolTable = new SymbolTable();
        addSymbol("Animal", "demo.Animal", CodeSymbolKind.CLASS);
        addSymbol("Dog", "demo.Dog", CodeSymbolKind.CLASS);
        addSymbol("Cat", "demo.Cat", CodeSymbolKind.CLASS);
        chatCallCount = new AtomicInteger(0);
    }

    private void addSymbol(String name, String qn, CodeSymbolKind kind) {
        CodeSymbol s = new CodeSymbol();
        s.setId(name + "-id");
        s.setName(name);
        s.setQualifiedName(qn);
        s.setKind(kind);
        symbolTable.add(s);
    }

    private IChatService stubChat(String jsonResponse) {
        return new IChatService() {
            @Override
            public java.util.concurrent.CompletionStage<ChatResponse> callAsync(
                    ChatRequest request, io.nop.api.core.util.ICancelToken token) {
                chatCallCount.incrementAndGet();
                ChatResponse resp = new ChatResponse();
                resp.setMessages(List.of(new io.nop.ai.api.chat.messages.ChatAssistantMessage(jsonResponse)));
                return java.util.concurrent.CompletableFuture.completedFuture(resp);
            }

            @Override
            public java.util.concurrent.Flow.Publisher<io.nop.ai.api.chat.stream.ChatStreamChunk> callStream(
                    ChatRequest request, io.nop.api.core.util.ICancelToken token) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void extractsEdgesFromLlmResponse() {
        String json = "[{\"source\":\"demo.Animal\",\"target\":\"demo.Dog\","
                + "\"relation\":\"SEMANTICALLY_SIMILAR_TO\",\"score\":0.9,"
                + "\"rationale\":\"Both are animal types\"}]";
        LlmSemanticEdgeExtractor extractor = new LlmSemanticEdgeExtractor(
                stubChat(json), new LlmEdgeBudget(10000), new LlmEdgeCache(), 0.7, 10);

        List<CodeSemanticEdge> edges = extractor.extract(symbolTable, null);
        assertEquals(3, edges.size()); // stub returns same response for all 3 pairs
        assertEquals(SemanticRelationType.SEMANTICALLY_SIMILAR_TO, edges.get(0).getRelationType());
        assertEquals(0.9, edges.get(0).getConfidenceScore(), 0.001);
        assertEquals(EdgeConfidence.INFERRED, edges.get(0).getConfidence());
        assertEquals("llm-infer", edges.get(0).getExtractorId());
        assertTrue(edges.get(0).getRationale().contains("animal"));
    }

    @Test
    void lowConfidenceFiltered() {
        String json = "[{\"source\":\"demo.Animal\",\"target\":\"demo.Dog\","
                + "\"relation\":\"SEMANTICALLY_SIMILAR_TO\",\"score\":0.3,\"rationale\":\"weak\"}]";
        LlmSemanticEdgeExtractor extractor = new LlmSemanticEdgeExtractor(
                stubChat(json), new LlmEdgeBudget(10000), new LlmEdgeCache(), 0.7, 10);
        assertTrue(extractor.extract(symbolTable, null).isEmpty(),
                "score 0.3 < threshold 0.7 must be filtered");
    }

    @Test
    void unknownRelationSkipped() {
        String json = "[{\"source\":\"demo.Animal\",\"target\":\"demo.Dog\","
                + "\"relation\":\"TOTALLY_MADE_UP\",\"score\":0.9,\"rationale\":\"x\"}]";
        LlmSemanticEdgeExtractor extractor = new LlmSemanticEdgeExtractor(
                stubChat(json), new LlmEdgeBudget(10000), new LlmEdgeCache(), 0.7, 10);
        assertTrue(extractor.extract(symbolTable, null).isEmpty(),
                "unknown relation enum value must be skipped (M2)");
    }

    @Test
    void malformedJsonSkippedNotCrash() {
        LlmSemanticEdgeExtractor extractor = new LlmSemanticEdgeExtractor(
                stubChat("not valid json"), new LlmEdgeBudget(10000), new LlmEdgeCache(), 0.7, 10);
        assertTrue(extractor.extract(symbolTable, null).isEmpty(), "malformed JSON must be skipped");
    }

    @Test
    void cachePreventsDuplicateCalls() {
        String json = "[{\"source\":\"demo.Animal\",\"target\":\"demo.Dog\","
                + "\"relation\":\"SEMANTICALLY_SIMILAR_TO\",\"score\":0.9,\"rationale\":\"x\"}]";
        LlmEdgeCache cache = new LlmEdgeCache();
        LlmSemanticEdgeExtractor extractor = new LlmSemanticEdgeExtractor(
                stubChat(json), new LlmEdgeBudget(10000), cache, 0.7, 10);

        extractor.extract(symbolTable, null);
        int after = chatCallCount.get();
        extractor.extract(symbolTable, null);
        assertEquals(after, chatCallCount.get(), "cache hit must not trigger second LLM call");
    }

    @Test
    void budgetExhaustionStopsCalls() {
        LlmSemanticEdgeExtractor extractor = new LlmSemanticEdgeExtractor(
                stubChat("[]"), new LlmEdgeBudget(100), new LlmEdgeCache(), 0.7, 100);
        extractor.extract(symbolTable, null);
        assertTrue(chatCallCount.get() <= 2, "budget must limit LLM calls");
    }

    @Test
    void emptySymbolTableReturnsEmpty() {
        SymbolTable empty = new SymbolTable();
        LlmSemanticEdgeExtractor extractor = new LlmSemanticEdgeExtractor(
                stubChat("[]"), new LlmEdgeBudget(10000), new LlmEdgeCache(), 0.7, 10);
        assertTrue(extractor.extract(empty, null).isEmpty());
    }
}
