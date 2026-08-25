/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.javac;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface JavaCompilerErrors {
    String ARG_DETAIL = "detail";
    String ARG_JAVA_TYPE = "javaType";
    String ARG_AST_KIND = "astKind";
    String ARG_CLASS_NAME = "className";

    ErrorCode ERR_JAVAC_PARSE_FAIL = define("nop.err.javac.parse-fail", "解析java文件失败");

    ErrorCode ERR_JAVAC_INVALID_CLASS_NAME = define("nop.err.javac.invalid-class-name", "非法的Java类名:{className}",
            ARG_CLASS_NAME);

    ErrorCode ERR_JAVAC_NOT_SUPPORT_TRANSFORM_TO_XLANG_AST_FAIL = define(
            "nop.err.javac.not-support-transform-to-xlang-ast-fail", "不支持转换到XLang AST语法树节点");
}
