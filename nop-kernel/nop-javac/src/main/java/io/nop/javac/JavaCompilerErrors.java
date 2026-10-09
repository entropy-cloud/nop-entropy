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
    String ARG_OPTION = "option";
    String ARG_VALUE = "value";
    String ARG_PATH = "path";
    String ARG_SOURCE_FILE = "sourceFile";
    String ARG_ENCODING = "encoding";

    ErrorCode ERR_JAVAC_PARSE_FAIL = define("nop.err.javac.parse-fail", "解析java文件失败");

    ErrorCode ERR_JAVAC_INVALID_CLASS_NAME = define("nop.err.javac.invalid-class-name", "非法的Java类名:{className}",
            ARG_CLASS_NAME);

    ErrorCode ERR_JAVAC_NOT_SUPPORT_TRANSFORM_TO_XLANG_AST_FAIL = define(
            "nop.err.javac.not-support-transform-to-xlang-ast-fail", "不支持转换到XLang AST语法树节点");

    ErrorCode ERR_JAVAC_INVALID_OPTION_VALUE = define("nop.err.javac.invalid-option-value",
            "非法的编译选项取值:{option}={value}", ARG_OPTION, ARG_VALUE);

    ErrorCode ERR_JAVAC_INVALID_ENCODING = define("nop.err.javac.invalid-encoding",
            "不支持的源码编码:{encoding}", ARG_ENCODING);

    ErrorCode ERR_JAVAC_SOURCE_NOT_DECODABLE = define("nop.err.javac.source-not-decodable",
            "源文件无法按指定编码解码:{sourceFile},encoding={encoding}", ARG_SOURCE_FILE, ARG_ENCODING);

    ErrorCode ERR_JAVAC_INVALID_OUTPUT_DIR = define("nop.err.javac.invalid-output-dir",
            "非法的编译输出目录:{path}", ARG_PATH);

    ErrorCode ERR_JAVAC_COMPILE_FAIL = define("nop.err.javac.compile-fail", "Java编译失败:{detail}", ARG_DETAIL);

    ErrorCode ERR_JAVAC_COMPILER_NOT_ACQUIRED = define("nop.err.javac.compiler-not-acquired",
            "编译器尚未acquire或已release，无法释放");
}
