/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.biz;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface BizReportErrors {
    String ARG_EXPORT_FORMAT = "exportFormat";

    ErrorCode ERR_BIZ_REPORT_UNSUPPORTED_EXPORT_FORMAT =
            define("nop.err.biz.report.unsupported-export-format", "不支持的导出格式:{exportFormat}", ARG_EXPORT_FORMAT);
}
