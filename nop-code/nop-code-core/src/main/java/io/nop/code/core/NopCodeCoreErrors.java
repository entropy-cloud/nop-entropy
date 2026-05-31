package io.nop.code.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopCodeCoreErrors {

    String ARG_PATH = "path";

    ErrorCode ERR_CODE_ANALYZE_PROJECT_FAILED =
            define("nop.err.code.analyze-project-failed", "项目分析失败:{path}", ARG_PATH);

    ErrorCode ERR_CODE_DIGEST_NOT_AVAILABLE =
            define("nop.err.code.digest-not-available", "SHA-256摘要算法不可用");

    String ARG_GIT_REF = "gitRef";

    ErrorCode ERR_CODE_INVALID_GIT_REF =
            define("nop.err.code.invalid-git-ref", "Invalid git ref: {gitRef}", ARG_GIT_REF);
}
