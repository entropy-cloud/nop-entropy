package io.nop.ai.coder;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AiCoderErrors {
    String ARG_SQL_TYPE = "sqlType";

    String ARG_HEADERS = "headers";
    String ARG_DATA = "data";

    String ARG_FROM = "from";
    String ARG_TO = "to";

    String ARG_SIGNATURE = "signature";

    ErrorCode ERR_AI_CODER_UNKNOWN_SQL_TYPE = define("nop.err.ai.coder.unknown-sql-type",
            "未知的SQL类型: {sqlType}", ARG_SQL_TYPE);

    ErrorCode ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH =
            define("nop.err.ai.coder.headers-and-data-not-match", "表头和数据的列数不匹配:headers={headers},data={data}",
                    ARG_HEADERS, ARG_DATA);

    /**
     * Converted from bare {@code IllegalArgumentException} throws (plan
     * 2026-08-01-0936-2): English descriptions preserve the historical
     * message semantics verbatim (AGENTS.md English error-message
     * convention for newly added codes).
     */
    ErrorCode ERR_AI_CODER_UNSUPPORTED_CONVERSION =
            define("nop.err.ai.coder.unsupported-conversion", "Unsupported conversion:{from}->{to}", ARG_FROM, ARG_TO);

    ErrorCode ERR_AI_CODER_METHOD_SIGNATURE_NOT_FOUND =
            define("nop.err.ai.coder.method-signature-not-found", "Method signature not found: {signature}", ARG_SIGNATURE);

    ErrorCode ERR_AI_CODER_UNBALANCED_BRACES =
            define("nop.err.ai.coder.unbalanced-braces", "Unbalanced braces in method body");


}
