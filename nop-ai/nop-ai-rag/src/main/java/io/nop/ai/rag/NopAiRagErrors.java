package io.nop.ai.rag;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-ai-rag 模块错误码（K2 起，plan knowledge-rag/02）。
 */
public interface NopAiRagErrors {
    String ARG_DETAIL = "detail";

    ErrorCode ERR_AI_RAG_QUERY_VECTOR_REQUIRED =
            define("nop.err.ai.rag.query-vector-required",
                    "Vector search requires query.vector (text->embedding conversion "
                            + "belongs to the RAG pipeline)", ARG_DETAIL);
}
