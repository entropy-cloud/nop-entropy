package io.nop.report.spl;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface SplErrors {
    String ARG_RESULT_TYPE = "resultType";
    String ARG_PATH = "path";

    ErrorCode ERR_XPT_SPL_RESULT_NOT_CURSOR = define("nop.err.xpt.spl-result-not-cursor",
            "结果类型为[{resultType}]，不是ICursor类型", ARG_RESULT_TYPE);

    ErrorCode ERR_XPT_INVALID_SPL_MODEL_FILE_TYPE = define("nop.err.xpt.invalid-spl-model-file-type",
            "SPL模型文件名后缀必须是splx、spl等: {path}", ARG_PATH);

    ErrorCode ERR_XPT_UNKNOWN_SPL_RESOURCE = define("nop.err.xpt.unknown-spl-resource",
            "SPL模型文件不存在：{path}", ARG_PATH);

}
