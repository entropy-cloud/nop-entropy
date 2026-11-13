/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.engine;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.retry.api.NopRetryApiConstants;

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

    // 1.6-C3-003（plan 2515 裁定④）：续租 CAS 0 行 = 租约被扫描器接管（或他链抢持），
    // 败方显式中止（WARN + failedStage），不静默续跑
    ErrorCode ERR_RETRY_LEASE_LOST = define("nop.err.retry.lease-lost",
            "重试记录租约已被接管，本链中止: {recordId}", ARG_RECORD_ID);

    String ARG_CONFIG_KEY = "configKey";
    String ARG_CONFIG_VALUE = "configValue";

    ErrorCode ERR_RETRY_LEDGER_CONFIG_INVALID = define("nop.err.retry.ledger-config-invalid",
            "retry账本保留策略配置非法: {configKey}={configValue}", ARG_CONFIG_KEY, ARG_CONFIG_VALUE);

    // 1.6-C3-003（plan 2515 裁定④）：attempt 级超时上界必须小于租约，装配期 fail-fast
    ErrorCode ERR_RETRY_ATTEMPT_TIMEOUT_INVALID = define("nop.err.retry.attempt-timeout-invalid",
            "attempt 超时上界必须小于租约: attemptTimeoutMs={configValue}, leaseTimeoutMs="
                    + NopRetryApiConstants.DEFAULT_RETRYING_TIMEOUT_MS, ARG_CONFIG_VALUE);

    // 1.6-C1-002（plan 2543 W-01）：OVERWRITE 输家判定为重读行已消失（键位被并发删除），
    // 既非「已被合法替换」也非「旧行存活」——不造死信，纯失败传播
    ErrorCode ERR_RETRY_OVERWRITE_RACE_LOST = define("nop.err.retry.overwrite-race-lost",
            "OVERWRITE 竞争败方且键位已空: {recordId}", ARG_RECORD_ID);

    // 1.6-C8-013（plan 2543 W-14）：扫描/批处理参数越界，装配期 fail-fast
    ErrorCode ERR_RETRY_SCANNER_CONFIG_INVALID = define("nop.err.retry.scanner-config-invalid",
            "扫描器配置非法: {configKey}={configValue}", ARG_CONFIG_KEY, ARG_CONFIG_VALUE);
}
