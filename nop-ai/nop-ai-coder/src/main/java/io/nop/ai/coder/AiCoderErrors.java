package io.nop.ai.coder;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AiCoderErrors {
    String ARG_SQL_TYPE = "sqlType";

    String ARG_HEADERS = "headers";
    String ARG_DATA = "data";

    ErrorCode ERR_AI_CODER_UNKNOWN_SQL_TYPE = define("nop.err.ai.coder.unknown-sql-type",
            "未知的SQL类型: {sqlType}", ARG_SQL_TYPE);

    ErrorCode ERR_AI_CODER_HEADERS_AND_DATA_NOT_MATCH =
            define("nop.err.ai.coder.headers-and-data-not-match", "表头和数据的列数不匹配:headers={},data={}",
                    ARG_HEADERS, ARG_DATA);

    ErrorCode ERR_FILE_CONTENT_NO_PATH = define("nop.err.file.content.no-path", "文件对象没有指定路径属性");
}
