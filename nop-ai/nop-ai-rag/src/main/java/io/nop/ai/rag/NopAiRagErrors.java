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
    // C3-005 (plan 2520): provider error facets carried on synthesize failure
    String ARG_ERROR_CODE = "errorCode";
    String ARG_ERROR_CLASSIFICATION = "errorClassification";
    String ARG_RETRY_AFTER_MS = "retryAfterMs";

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

    /**
     * C3-005 (plan 2520, error-delivered-as-success family): synthesize must fail
     * loud when the LLM provider returns an error response — folding it to an
     * empty-string success silently bills retrieval+call while delivering nothing.
     * A successful response with no assistant text is NOT an error (legal empty
     * answer, returns "").
     */
    ErrorCode ERR_AI_RAG_SYNTHESIZE_FAILED =
            define("nop.err.ai.rag.synthesize-failed",
                    "LLM provider returned an error response for RAG synthesize: {detail}",
                    ARG_DETAIL, ARG_ERROR_CODE, ARG_ERROR_CLASSIFICATION, ARG_RETRY_AFTER_MS);

    /**
     * Relocated verbatim from {@code io.nop.ai.core.NopAiCoreErrors} (WI9
     * error-code relocation, plan 2438): WI4 moved CosineSimilarity /
     * RelevanceScore into nop-ai-rag, leaving this code with zero core-side
     * consumers, so the holder moves next to its consumer. ID and English
     * description preserved verbatim (persisted/logged contract).
     */
    ErrorCode ERR_AI_VECTOR_LENGTH_MISMATCH =
            define("nop.err.ai.vector-length-mismatch", "Length of vector a  must be equal to the length of vector b");
}
