package io.nop.report.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopReportErrors {
    String ARG_REPORT_PATH = "reportPath";

    /**
     * plan 2586：auth checker 未装配时 fail-closed（拒绝服务），不再静默跳过授权。
     */
    ErrorCode ERR_REPORT_AUTH_CHECKER_MISSING =
            define("nop.err.report.auth-checker-missing",
                    "No IReportAuthChecker is registered: report access check for [{reportPath}] cannot be " +
                            "performed and the request is denied (fail-closed).",
                    ARG_REPORT_PATH);
}
