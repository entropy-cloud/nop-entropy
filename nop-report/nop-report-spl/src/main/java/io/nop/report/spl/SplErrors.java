/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.spl;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface SplErrors {
    String ARG_RESULT_TYPE = "resultType";
    String ARG_PATH = "path";
    String ARG_PARAM_NAME = "paramName";

    ErrorCode ERR_XPT_SPL_RESULT_NOT_CURSOR = define("nop.err.xpt.spl-result-not-cursor",
            "结果类型为[{resultType}]，不是ICursor类型", ARG_RESULT_TYPE);

    ErrorCode ERR_XPT_INVALID_SPL_MODEL_FILE_TYPE = define("nop.err.xpt.invalid-spl-model-file-type",
            "SPL模型文件名后缀必须是splx、spl等: {path}", ARG_PATH);

    ErrorCode ERR_XPT_UNKNOWN_SPL_RESOURCE = define("nop.err.xpt.unknown-spl-resource",
            "SPL模型文件不存在：{path}", ARG_PATH);

    ErrorCode ERR_XPT_UNKNOWN_SPL_PARAM =
            define("nop.err.xpt.unknown-spl-param",
                    "未定义的SPL参数:{paramName}", ARG_PARAM_NAME);
}
