/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.security;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * 数据级安全错误码（nop.err.report.data-sec.*）。消息为英文；
 * 配置指引：行级过滤规则引用了用户上下文占位符（userId/tenantId/deptId），
 * 系统调用方（调度等）必须显式建立用户上下文。
 */
public interface ReportDataSecErrors {
    String ARG_PLACEHOLDER = "placeholder";
    String ARG_DS_NAME = "dsName";
    String ARG_RULE_INDEX = "ruleIndex";
    String ARG_FIELD_NAME = "fieldName";

    ErrorCode ERR_REPORT_DATA_SEC_NO_USER_CONTEXT =
            define("nop.err.report.data-sec.no-user-context",
                    "Row filter rule references user context placeholder [{placeholder}] " +
                            "but no user context is present. Scheduled/system callers must establish an explicit user context.",
                    ARG_PLACEHOLDER);

    /**
     * plan 2586：规则加载期必填字段校验（fail-fast，带规则索引），避免运行期 NPE。
     */
    ErrorCode ERR_REPORT_DATA_SEC_RULE_FIELD_REQUIRED =
            define("nop.err.report.data-sec.rule-field-required",
                    "Data security rule #{ruleIndex} is missing required field [{fieldName}].",
                    ARG_RULE_INDEX, ARG_FIELD_NAME);
}
