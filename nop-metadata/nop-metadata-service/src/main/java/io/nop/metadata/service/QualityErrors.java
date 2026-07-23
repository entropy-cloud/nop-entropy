/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

interface QualityErrors extends NopMetadataArgs {

    ErrorCode ERR_QUALITY_INVALID_IDENTIFIER =
            ErrorCode.define("nop.err.metadata.quality-invalid-identifier",
                    "Identifier (column/table/schema) does not match whitelist ^[A-Za-z_][A-Za-z0-9_]*$: {identifier}",
                    ARG_IDENTIFIER);
    ErrorCode ERR_QUALITY_CUSTOM_SQL_BLOCKED =
            ErrorCode.define("nop.err.metadata.quality-custom-sql-blocked",
                    "custom_sql rule SQL contains forbidden keyword: "
                            + "{ruleKey} reason={reason} sqlHash={sqlHash}",
                    ARG_RULE_KEY, ARG_REASON, ARG_SQL_HASH);
    ErrorCode ERR_QUALITY_SQL_NO_ROW =
            ErrorCode.define("nop.err.metadata.quality-sql-no-row",
                    "Quality custom_sql returned no rows: {ruleKey}", ARG_RULE_KEY);
    ErrorCode ERR_QUALITY_SQL_FAILED =
            ErrorCode.define("nop.err.metadata.quality-sql-failed",
                    "Quality rule SQL execution failed: {ruleKey} -- {error}",
                    ARG_RULE_KEY, ARG_ERROR);
    ErrorCode ERR_CHECKPOINT_SCHEDULER_INVALID_CRON =
            ErrorCode.define("nop.err.metadata.checkpoint-scheduler-invalid-cron",
                    "Quality checkpoint schedule cron expression is invalid: "
                            + "{checkpointId} cron={cron}", ARG_CHECKPOINT_ID, ARG_CRON);
    ErrorCode ERR_CHECKPOINT_NOT_ACTIVE =
            ErrorCode.define("nop.err.metadata.checkpoint-not-active",
                    "Quality checkpoint is not ACTIVE (paused/disabled), cannot execute: "
                            + "{checkpointId} status={status}", ARG_CHECKPOINT_ID, ARG_STATUS);
    ErrorCode ERR_CHECKPOINT_NO_RULES =
            ErrorCode.define("nop.err.metadata.checkpoint-no-rules",
                    "Quality checkpoint resolved to an empty rule set: {checkpointId}", ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_ACTION_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.checkpoint-action-not-supported",
                    "Quality checkpoint action type is not supported (only store/webhook/notify): "
                            + "{checkpointId} actionType={actionType}", ARG_CHECKPOINT_ID, ARG_ACTION_TYPE);
    ErrorCode ERR_CHECKPOINT_RULE_TARGET_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.checkpoint-rule-target-table-not-found",
                    "Quality rule in checkpoint target table not found: "
                            + "{checkpointId} qualityRuleId={qualityRuleId} entityId={entityId}",
                    ARG_CHECKPOINT_ID, ARG_QUALITY_RULE_ID, ARG_ENTITY_ID);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NO_CLIENT =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-no-client",
                    "Quality checkpoint webhook action configured but IHttpClient is not registered: {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NO_URL =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-no-url",
                    "Quality checkpoint webhook action config is missing required 'url': {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_NOTIFY_NO_SERVICE =
            ErrorCode.define("nop.err.metadata.checkpoint-notify-no-service",
                    "Quality checkpoint notify action configured but IMessageService is not registered: {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_NOTIFY_NO_CHANNEL =
            ErrorCode.define("nop.err.metadata.checkpoint-notify-no-channel",
                    "Quality checkpoint notify action config is missing required 'channel': {checkpointId}",
                    ARG_CHECKPOINT_ID);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_URL_BLOCKED =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-url-blocked",
                    "Quality checkpoint webhook URL is blocked by SSRF protection policy: "
                            + "{checkpointId} url={url} reason={reason}",
                    ARG_CHECKPOINT_ID, ARG_URL, ARG_REASON);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_METHOD_BLOCKED =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-method-blocked",
                    "Quality checkpoint webhook method is not in whitelist (allowed: POST/PUT): "
                            + "{checkpointId} method={method}", ARG_CHECKPOINT_ID, ARG_METHOD);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NULL_RESPONSE =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-null-response",
                    "Quality checkpoint webhook returned null response: {checkpointId} url={url}",
                    ARG_CHECKPOINT_ID, ARG_URL);
    ErrorCode ERR_CHECKPOINT_WEBHOOK_NON_2XX =
            ErrorCode.define("nop.err.metadata.checkpoint-webhook-non-2xx",
                    "Quality checkpoint webhook returned non-2xx HTTP status: {checkpointId} url={url} status={status}",
                    ARG_CHECKPOINT_ID, ARG_URL, ARG_STATUS);
    ErrorCode ERR_SCORE_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.score-table-not-found",
                    "Quality score target table not found (NopMetaTable missing): {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_SCORE_NO_RULES =
            ErrorCode.define("nop.err.metadata.score-no-rules",
                    "Quality score target table has no mounted quality rules, nothing to score: {metaTableId}",
                    ARG_META_TABLE_ID);
    ErrorCode ERR_SCORE_ALL_SKIP =
            ErrorCode.define("nop.err.metadata.score-all-skip",
                    "Quality score target table's all rule latest results are SKIP (or never executed), every "
                            + "dimension is null, cannot score: {metaTableId}", ARG_META_TABLE_ID);
    ErrorCode ERR_QUALITY_RULE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.quality-rule-not-found",
                    "Quality rule not found: {qualityRuleId}", ARG_QUALITY_RULE_ID);
    ErrorCode ERR_QUALITY_RESULT_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.quality-result-not-found",
                    "Quality result not found: {qualityResultId}", ARG_QUALITY_RESULT_ID);
    ErrorCode ERR_QUALITY_TABLE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.quality-table-not-found",
                    "Quality rule target table not found (entityId does not refer to an existing NopMetaTable): "
                            + "{qualityRuleId} entityId={entityId}", ARG_QUALITY_RULE_ID, ARG_ENTITY_ID);
    ErrorCode ERR_QUALITY_TABLE_NOT_EXTERNAL =
            ErrorCode.define("nop.err.metadata.quality-table-not-external",
                    "Quality rule target table is not external (first version supports external-only execution): "
                            + "{qualityRuleId} tableType={tableType}", ARG_QUALITY_RULE_ID, ARG_TABLE_TYPE);
    ErrorCode ERR_QUALITY_NO_DATASOURCE =
            ErrorCode.define("nop.err.metadata.quality-no-datasource",
                    "No registered MetaDataSource for querySpace of target table: "
                            + "{qualityRuleId} querySpace={querySpace}", ARG_QUALITY_RULE_ID, ARG_QUERY_SPACE);
    ErrorCode ERR_QUALITY_DATASOURCE_DISABLED =
            ErrorCode.define("nop.err.metadata.quality-datasource-disabled",
                    "MetaDataSource is disabled, cannot execute quality rule: {dataSourceId}", ARG_DATA_SOURCE_ID);
    ErrorCode ERR_QUALITY_EXPECT_PASS_WHEN_INVALID =
            ErrorCode.define("nop.err.metadata.quality-expect-pass-when-invalid",
                    "Quality rule expectPassWhen expression is invalid: {qualityRuleId} expr={expr}",
                    ARG_QUALITY_RULE_ID, ARG_EXPR);
}
