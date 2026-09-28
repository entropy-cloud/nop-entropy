package io.nop.ai.rag.synthesize;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.rag.search.RagSearchService;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;

import java.util.List;

import static io.nop.ai.rag.NopAiRagErrors.ERR_AI_RAG_EMPTY_QUERY;

/**
 * K3 RAG 合成服务：检索 top-K → 上下文拼接 → IChatService 合成回答。
 */
public class RagSynthesizeService {
    private final IChatService chatService;
    private final RagSearchService searchService;

    public RagSynthesizeService(IChatService chatService, RagSearchService searchService) {
        this.chatService = chatService;
        this.searchService = searchService;
    }

    public String synthesize(String indexId, String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new NopException(ERR_AI_RAG_EMPTY_QUERY);
        }

        List<RagSearchService.RagSearchHit> hits = searchService.search(indexId, query, topK);

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            RagSearchService.RagSearchHit hit = hits.get(i);
            if (i > 0) context.append("\n\n");
            context.append("[").append(i + 1).append("] ").append(hit.getContent());
        }

        String prompt = "Based on the following context, answer the question.\n\n"
                + "Context:\n" + context + "\n\nQuestion: " + query
                + "\n\nAnswer:";

        ChatRequest request = new ChatRequest();
        request.withUserPrompt(prompt);
        ChatResponse response = chatService.call(request, null);
        String answer = response != null ? response.outputText() : null;
        return answer != null ? answer : "";
    }
}
