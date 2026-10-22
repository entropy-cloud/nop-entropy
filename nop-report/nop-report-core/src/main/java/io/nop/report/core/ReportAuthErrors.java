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
 * 报表授权检查错误码（nop.err.report.auth.*）。
 * 消息为英文；配置指引：为角色配置 NopReportDefinitionAuth / NopReportDatasourceAuth 记录，
 * 或将角色加入配置项 nop.report.auth.exempt-roles。
 */
public interface ReportAuthErrors {
    String ARG_REPORT_PATH = "reportPath";
    String ARG_DS_NAME = "dsName";
    String ARG_ROLES = "roles";

    ErrorCode ERR_REPORT_AUTH_REPORT_DENIED =
            define("nop.err.report.auth.report-denied",
                    "Current user is not allowed to render report [{reportPath}]. " +
                            "Grant a role via NopReportDefinitionAuth or add the role to config nop.report.auth.exempt-roles.",
                    ARG_REPORT_PATH, ARG_ROLES);

    ErrorCode ERR_REPORT_AUTH_DATASOURCE_DENIED =
            define("nop.err.report.auth.datasource-denied",
                    "Current user is not allowed to access report datasource [{dsName}]. " +
                            "Grant a role via NopReportDatasourceAuth or add the role to config nop.report.auth.exempt-roles.",
                    ARG_DS_NAME, ARG_ROLES);
}
