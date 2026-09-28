package io.nop.ai.rag;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-ai-rag 模块错误码（K2 起，plan knowledge-rag/02）。
 */
public interface NopAiRagErrors {
    String ARG_DETAIL = "detail";
    String ARG_DOC_ID = "docId";
    String ARG_QUERY = "query";

    ErrorCode ERR_AI_RAG_QUERY_VECTOR_REQUIRED =
            define("nop.err.ai.rag.query-vector-required",
                    "Vector search requires query.vector (text->embedding conversion "
                            + "belongs to the RAG pipeline)", ARG_DETAIL);

    ErrorCode ERR_AI_RAG_EMPTY_DOCUMENT =
            define("nop.err.ai.rag.empty-document",
                    "Document {docId} is empty or blank", ARG_DOC_ID);

    ErrorCode ERR_AI_RAG_EMPTY_QUERY =
            define("nop.err.ai.rag.empty-query",
                    "Query text must not be blank", ARG_QUERY);
}
