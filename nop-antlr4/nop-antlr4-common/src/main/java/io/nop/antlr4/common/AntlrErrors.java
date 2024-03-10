/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.antlr4.common;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface AntlrErrors {
    String ARG_START_TOKEN = "startToken";
    String ARG_OFFENDING_TOKEN = "offendingToken";

    String ARG_EXPECTED = "expected";
    String ARG_SOURCE = "source";
    String ARG_FULL_SOURCE = "fullSource";

    ErrorCode ERR_ANTLR_PARSE_FAIL = define("nop.err.antlr.common.parse-fail", "解析失败", ARG_OFFENDING_TOKEN,
            ARG_EXPECTED);

    ErrorCode ERR_ANTLR_PARSE_NOT_END_PROPERLY = define("nop.err.antlr.not-end-properly", "语句没有正常结束");

    ErrorCode ERR_ANTLR_LEXER_PARSE_FAIL = define("nop.err.antlr.lexer-parse-fail", "词法解析失败：{source}", ARG_SOURCE);

    ErrorCode ERR_ANTLR_STRING_LITERAL_NOT_END = define("nop.err.antlr.string-literal-not-end",
            "字符串解析失败，没有找到结束符：{source}", ARG_SOURCE);
}
