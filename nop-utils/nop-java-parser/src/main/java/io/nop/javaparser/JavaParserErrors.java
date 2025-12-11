package io.nop.javaparser;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface JavaParserErrors {
    String ARG_PARSE_RESULT = "parseResult";

    ErrorCode ERR_JAVA_PARSER_PARSE_FAILED =
            define("nop.err.javaparser.parse-failed", "Java解析失败:{parseResult}", ARG_PARSE_RESULT);
}
