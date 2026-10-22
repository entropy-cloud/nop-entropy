/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.core;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * 报表参数错误码（nop.err.report.param.*）。消息为英文；
 * 配置指引：在模板 XptWorkbookModel 的 params 中声明该参数，或为必填参数传入有效值。
 */
public interface ReportParamErrors {
    String ARG_PARAM_NAME = "paramName";
    String ARG_VALUE = "value";
    String ARG_EXPECT_TYPE = "expectType";

    ErrorCode ERR_REPORT_PARAM_MISSING_REQUIRED =
            define("nop.err.report.param.missing-required",
                    "Required report parameter [{paramName}] is missing. " +
                            "Declare it in the template's params model or pass a valid value.",
                    ARG_PARAM_NAME);

    ErrorCode ERR_REPORT_PARAM_UNKNOWN =
            define("nop.err.report.param.unknown",
                    "Unknown report parameter [{paramName}]. Declare it in the template's params model.",
                    ARG_PARAM_NAME);

    ErrorCode ERR_REPORT_PARAM_TYPE_ERROR =
            define("nop.err.report.param.type-error",
                    "Report parameter [{paramName}] expects type [{expectType}] but got value [{value}].",
                    ARG_PARAM_NAME, ARG_EXPECT_TYPE, ARG_VALUE);
}
