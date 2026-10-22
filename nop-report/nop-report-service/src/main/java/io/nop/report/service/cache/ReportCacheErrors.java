/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.cache;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * 报表结果缓存错误码（nop.err.report.cache.*）。消息为英文。
 */
public interface ReportCacheErrors {
    String ARG_REPORT_PATH = "reportPath";

    ErrorCode ERR_REPORT_CACHE_NO_DEFINITION =
            define("nop.err.report.cache.no-definition",
                    "Report [{reportPath}] has no NopReportDefinition record (rptText must match the template path); " +
                            "result caching requires a registered report definition.",
                    ARG_REPORT_PATH);
}
