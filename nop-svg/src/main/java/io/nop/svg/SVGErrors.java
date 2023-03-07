/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.svg;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface SVGErrors {
    String ARG_LINE = "line";
    String ARG_COL = "col";

    String ARG_CUR = "cur";
    String ARG_EXPECTED = "expected";

    ErrorCode ERR_SVG_PARSE_FAIL = define("nop.err.svg.parse-fail", "解析SVG文本失败");

    ErrorCode ERR_SVG_PARSE_PATH_NOT_END = define("nop.err.svg.parse-path-not-end", "SVG的路径表达式没有正常结束");

    ErrorCode ERR_SVG_PARSE_TRANSFORM_NOT_END = define("nop.err.svg.parse-transform-not-end", "SVG的矩阵变换表达式没有正常结束");

    ErrorCode ERR_SVG_PARSE_UNEXPECTED_CHAR = define("nop.err.svg.unexpected-char", "解析SVG文本失败，不是期待的字符");

    ErrorCode ERR_SVG_MATRIX_INVERT_FAIL = define("nop.err.svg.matrix-invert-fail", "转换矩阵求逆失败");
}
