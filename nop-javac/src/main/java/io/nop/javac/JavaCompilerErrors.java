/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.javac;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface JavaCompilerErrors {
    String ARG_DETAIL = "detail";
    String ARG_JAVA_TYPE = "javaType";
    String ARG_AST_KIND = "astKind";

    ErrorCode ERR_JAVAC_PARSE_FAIL = define("nop.err.javac.parse-fail", "解析java文件失败");

    ErrorCode ERR_JAVAC_NOT_SUPPORT_TRANSFORM_TO_XLANG_AST_FAIL = define(
            "nop.err.javac.not-support-transform-to-xlang-ast-fail", "不支持转换到XLang AST语法树节点");
}
