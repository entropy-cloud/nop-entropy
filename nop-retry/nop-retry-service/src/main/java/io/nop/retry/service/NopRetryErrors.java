/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.service;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface NopRetryErrors {

    String ARG_RECORD_ID = "recordId";
    String ARG_TEMPLATE_ID = "templateId";
    String ARG_DEAD_LETTER_ID = "deadLetterId";
    String ARG_SERVICE_NAME = "serviceName";
    String ARG_SERVICE_METHOD = "serviceMethod";
    String ARG_POLICY_ID = "policyId";
    String ARG_RETRY_COUNT = "retryCount";
    String ARG_MAX_RETRY_COUNT = "maxRetryCount";

    ErrorCode ERR_RETRY_RECORD_NOT_FOUND = define("nop.err.retry.record-not-found",
            "重试记录不存在: {recordId}", ARG_RECORD_ID);

    ErrorCode ERR_RETRY_DEAD_LETTER_NOT_FOUND = define("nop.err.retry.dead-letter-not-found",
            "死信记录不存在: {deadLetterId}", ARG_DEAD_LETTER_ID);

    ErrorCode ERR_RETRY_DEAD_LETTER_INVALID_EXECUTOR = define("nop.err.retry.dead-letter-invalid-executor",
            "死信记录缺少执行器信息: {deadLetterId}", ARG_DEAD_LETTER_ID);

    ErrorCode ERR_RETRY_DEAD_LETTER_INVALID_REQUEST = define("nop.err.retry.dead-letter-invalid-request",
            "死信记录缺少请求信息: {deadLetterId}", ARG_DEAD_LETTER_ID);
    ErrorCode ERR_RETRY_TEMPLATE_NOT_FOUND = define("nop.err.retry.template-not-found",
            "重试模板不存在: {templateId}", ARG_TEMPLATE_ID);

    ErrorCode ERR_RETRY_RECORD_ALREADY_SUSPENDED = define("nop.err.retry.record-already-suspended",
            "重试记录已暂停: {recordId}", ARG_RECORD_ID);

    ErrorCode ERR_RETRY_RECORD_NOT_SUSPENDED = define("nop.err.retry.record-not-suspended",
            "重试记录未暂停，无法恢复: {recordId}", ARG_RECORD_ID);

    ErrorCode ERR_RETRY_RECORD_ALREADY_COMPLETED = define("nop.err.retry.record-already-completed",
            "重试记录已完成: {recordId}", ARG_RECORD_ID);

    ErrorCode ERR_RETRY_MAX_RETRIES_EXCEEDED = define("nop.err.retry.max-retries-exceeded",
            "重试次数超过最大限制: {retryCount}/{maxRetryCount}", ARG_RETRY_COUNT, ARG_MAX_RETRY_COUNT);

    ErrorCode ERR_RETRY_SERVICE_NOT_FOUND = define("nop.err.retry.service-not-found",
            "服务不存在: {serviceName}", ARG_SERVICE_NAME);

    ErrorCode ERR_RETRY_METHOD_NOT_FOUND = define("nop.err.retry.method-not-found",
            "服务方法不存在: {serviceName}.{serviceMethod}", ARG_SERVICE_NAME, ARG_SERVICE_METHOD);

    ErrorCode ERR_RETRY_POLICY_NOT_FOUND = define("nop.err.retry.policy-not-found",
            "重试策略不存在: {policyId}", ARG_POLICY_ID);

    ErrorCode ERR_RETRY_EXECUTION_FAILED = define("nop.err.retry.execution-failed",
            "重试执行失败");

    ErrorCode ERR_RETRY_DEADLINE_EXCEEDED = define("nop.err.retry.deadline-exceeded",
            "重试任务已超过截止时间: {recordId}", ARG_RECORD_ID);

    ErrorCode ERR_RETRY_RECORD_OVERWRITTEN = define("nop.err.retry.record-overwritten",
            "重试记录已被覆盖: {recordId}", ARG_RECORD_ID);
}
