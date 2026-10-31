package io.nop.ai.rag.synthesize;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.rag.search.RagSearchService;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;

import java.util.List;

import static io.nop.ai.rag.NopAiRagErrors.ARG_DETAIL;
import static io.nop.ai.rag.NopAiRagErrors.ARG_ERROR_CLASSIFICATION;
import static io.nop.ai.rag.NopAiRagErrors.ARG_ERROR_CODE;
import static io.nop.ai.rag.NopAiRagErrors.ARG_RETRY_AFTER_MS;
import static io.nop.ai.rag.NopAiRagErrors.ERR_AI_RAG_EMPTY_QUERY;
import static io.nop.ai.rag.NopAiRagErrors.ERR_AI_RAG_SYNTHESIZE_FAILED;

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
        // C3-005 (plan 2520): a provider error response must fail loud — folding it
        // to an empty-string success silently bills retrieval+call with no alert
        // surface. Only !isSuccess() triggers; a successful response with no
        // assistant text is a legal empty answer and still returns "".
        if (response == null || !response.isSuccess()) {
            NopException exception = new NopException(ERR_AI_RAG_SYNTHESIZE_FAILED)
                    .param(ARG_DETAIL, response == null ? "null chat response" : response.getError());
            if (response != null) {
                if (response.getErrorCode() != null) {
                    exception.param(ARG_ERROR_CODE, response.getErrorCode());
                }
                if (response.getErrorClassification() != null) {
                    exception.param(ARG_ERROR_CLASSIFICATION, response.getErrorClassification().name());
                }
                if (response.getRetryAfterMs() != null) {
                    exception.param(ARG_RETRY_AFTER_MS, response.getRetryAfterMs());
                }
            }
            throw exception;
        }
        String answer = response.outputText();
        return answer != null ? answer : "";
    }
}
