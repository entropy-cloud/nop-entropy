/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface CodeGenErrors {
    String ARG_CLASS_NAME_1 = "className1";
    String ARG_CLASS_NAME_2 = "className2";

    String ARG_STATIC_IMPORT_1 = "staticImport1";
    String ARG_STATIC_IMPORT_2 = "staticImport2";

    String ARG_LOC_1 = "loc1";

    String ARG_METHOD_NAME_1 = "methodName1";
    String ARG_METHOD_NAME_2 = "methodName2";

    String ARG_TEXT = "text";

    String ARG_ARTIFACT = "artifact";

    String ARG_MESSAGE = "message";

    ErrorCode ERR_CODE_GEN_IMPORT_CLASS_CONFLICTED = define("nop.err.codegen.import-class-conflicted",
            "导入的类名[{className1}]和[className2]冲突", ARG_CLASS_NAME_1, ARG_CLASS_NAME_2, ARG_LOC_1);

    ErrorCode ERR_CODE_GEN_STATIC_IMPORT_CONFLICTED = define("nop.err.codegen.static-import-conflicted",
            "静态导入域[{staticImport1}]和[staticImport2]冲突", ARG_STATIC_IMPORT_1, ARG_STATIC_IMPORT_2, ARG_LOC_1);

    ErrorCode ERR_CODE_GEN_METHOD_DECL_CONFLICTED = define("nop.err.codegen.method-decl-conflicted",
            "方法定义[{methodName1}]和[methodName2]冲突", ARG_METHOD_NAME_1, ARG_METHOD_NAME_2, ARG_LOC_1);

    ErrorCode ERR_CODE_GEN_INVALID_CODE_VISIBILITY = define("nop.err.codegen.invalid-code-visibility",
            "代码可见性枚举值不正确:{text}", ARG_TEXT);

    ErrorCode ERR_CODE_GEN_INVALID_JSON_TYPE = define("nop.err.codegen.invalid-json-type", "j:type属性值应该是list:[{text}]",
            ARG_TEXT);

    ErrorCode ERR_POM_REFERENCE_CONTAINS_LOOP = define("nop.err.pom.reference-contains-loop", "Maven不允许循环依赖",
            ARG_ARTIFACT);

    ErrorCode ERR_GEN_AOP_PROXY_FAIL =
            define("nop.err.codegen.gen-aop-proxy-fail",
                    "生成AOP代理类时失败", ARG_MESSAGE);
}
