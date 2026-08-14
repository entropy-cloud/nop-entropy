package io.nop.datav.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

public interface NopDatavErrors {
    String ARG_DASHBOARD_ID = "dashboardId";
    String ARG_SNAPSHOT_VERSION = "snapshotVersion";
    String ARG_PANEL_ID = "panelId";
    String ARG_DATASET_REF_ID = "datasetRefId";
    String ARG_REF_DATASET_ID = "refDatasetId";
    String ARG_COMPONENT_TYPE = "componentType";
    String ARG_DS_TYPE = "dsType";
    String ARG_PARAM_NAME = "paramName";
    String ARG_VALUE = "value";
    String ARG_EXPECTED_TYPE = "expectedType";
    String ARG_REASON = "reason";
    String ARG_TARGET_PANEL_ID = "targetPanelId";
    String ARG_TARGET_TYPE = "targetType";
    String ARG_TARGET_ID = "targetId";
    String ARG_SOURCE_FIELD = "sourceField";
    String ARG_USER_NAME = "userName";

    String ARG_SHARE_ID = "shareId";
    String ARG_SHARE_TOKEN = "shareToken";

    String ARG_TASK_ID = "taskId";
    String ARG_FORMAT = "format";
    String ARG_SOURCE_TYPE = "sourceType";
    String ARG_SOURCE_ID = "sourceId";
    String ARG_MAX_ROWS = "maxRows";
    String ARG_ROW_COUNT = "rowCount";
    String ARG_MAX_CONCURRENT = "maxConcurrent";
    String ARG_CURRENT_CONCURRENT = "currentConcurrent";

    // ===== 批量面板查询（getDashboardData） =====
    String ARG_PANEL_IDS = "panelIds";
    String ARG_PANEL_COUNT = "panelCount";
    String ARG_MAX_PANELS = "maxPanels";

    String ARG_SCREEN_ID = "screenId";
    String ARG_WIDGET_ID = "widgetId";
    String ARG_CANVAS_WIDTH = "canvasWidth";
    String ARG_CANVAS_HEIGHT = "canvasHeight";
    String ARG_THEME_FIELD = "themeField";

    // ===== D5-1 定时报告 =====
    String ARG_REPORT_TASK_ID = "reportTaskId";
    String ARG_DELIVERY_ID = "deliveryId";
    String ARG_CRON_EXPR = "cronExpr";
    String ARG_TEMPLATE_KEY = "templateKey";
    String ARG_RECIPIENTS = "recipients";
    String ARG_NOTIFY_CHANNELS = "notifyChannels";
    String ARG_FAILED_CHANNELS = "failedChannels";
    String ARG_NO_BINDING_COUNT = "noBindingCount";

    // ===== D5-2 轻量告警 =====
    String ARG_ALERT_RULE_ID = "alertRuleId";
    String ARG_VALUE_FIELD = "valueField";
    String ARG_AGGREGATION = "aggregation";
    String ARG_ALERT_OPERATOR = "operator";
    String ARG_THRESHOLD_VALUE = "thresholdValue";
    String ARG_CURRENT_VALUE = "currentValue";

    ErrorCode ERR_DATAV_DASHBOARD_NOT_FOUND = define(
            "nop.err.datav.dashboard-not-found",
            "Dashboard not found: {dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SNAPSHOT_NOT_FOUND = define(
            "nop.err.datav.snapshot-not-found",
            "Published snapshot not found for dashboard: {dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND = define(
            "nop.err.datav.snapshot-version-not-found",
            "Snapshot version {snapshotVersion} not found for dashboard: {dashboardId}",
            ARG_DASHBOARD_ID, ARG_SNAPSHOT_VERSION
    );

    ErrorCode ERR_DATAV_PANEL_NOT_FOUND = define(
            "nop.err.datav.panel-not-found",
            "Panel not found: {panelId}",
            ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_DATASET_REF_NOT_FOUND = define(
            "nop.err.datav.dataset-ref-not-found",
            "Dataset reference not found: {datasetRefId}",
            ARG_DATASET_REF_ID
    );

    ErrorCode ERR_DATAV_DATASET_NOT_FOUND = define(
            "nop.err.datav.dataset-not-found",
            "Referenced nop-report dataset not found: {refDatasetId}",
            ARG_REF_DATASET_ID
    );

    ErrorCode ERR_DATAV_UNSUPPORTED_DATASET_TYPE = define(
            "nop.err.datav.unsupported-dataset-type",
            "Unsupported dataset type: {dsType}. Only 'sql' is supported.",
            ARG_DS_TYPE
    );

    ErrorCode ERR_DATAV_QUERY_FAILED = define(
            "nop.err.datav.query-failed",
            "Dataset query execution failed for panel: {panelId}",
            ARG_PANEL_ID
    );

    // ===== 批量面板查询（getDashboardData，裁定见 runtime-design.md §4.8） =====

    ErrorCode ERR_DATAV_PANEL_NOT_IN_DASHBOARD = define(
            "nop.err.datav.panel-not-in-dashboard",
            "Panel not found in dashboard: {panelId} (dashboardId: {dashboardId})",
            ARG_PANEL_ID, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED = define(
            "nop.err.datav.dashboard-panel-limit-exceeded",
            "Dashboard panel count {panelCount} exceeds maximum {maxPanels}",
            ARG_PANEL_COUNT, ARG_MAX_PANELS
    );

    ErrorCode ERR_DATAV_UNKNOWN_COMPONENT_TYPE = define(
            "nop.err.datav.unknown-component-type",
            "Unknown panel component type: {componentType}",
            ARG_COMPONENT_TYPE
    );

    ErrorCode ERR_DATAV_INVALID_PANEL_CONFIG = define(
            "nop.err.datav.invalid-panel-config",
            "Invalid panelConfig JSON for panel: {panelId}",
            ARG_PANEL_ID
    );

    // 注：此 ErrorCode 由 PanelParamEvaluator（面板 paramMapping）、DashboardParamParser（看板 paramConfig）、
    // DashboardFilterUrlCodec/Resolver（filter）共用。message 保持中性（不绑定单一实体名词），
    // 面板路径经 PanelDataBinder 补 ARG_PANEL_ID 作为结构化 param（供 GraphQL 错误响应/聚合），
    // 看板/filter 路径无 panelId 上下文故不填——若硬编码 "for panel:{panelId}" 会误标看板/filter 错误。
    ErrorCode ERR_DATAV_INVALID_PARAM_CONFIG = define(
            "nop.err.datav.invalid-param-config",
            "Invalid param config: {reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_UNKNOWN_PARAM_NAME = define(
            "nop.err.datav.unknown-param-name",
            "Unknown dashboard parameter: {paramName}",
            ARG_PARAM_NAME
    );

    ErrorCode ERR_DATAV_PARAM_TYPE_MISMATCH = define(
            "nop.err.datav.param-type-mismatch",
            "Parameter {paramName} value '{value}' does not match expected type {expectedType}",
            ARG_PARAM_NAME, ARG_VALUE, ARG_EXPECTED_TYPE
    );

    ErrorCode ERR_DATAV_INVALID_LINKAGE_CONFIG = define(
            "nop.err.datav.invalid-linkage-config",
            "Invalid linkage config JSON for panel: {panelId}, reason: {reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_INVALID_JUMP_CONFIG = define(
            "nop.err.datav.invalid-jump-config",
            "Invalid jump config JSON for panel: {panelId}, reason: {reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND = define(
            "nop.err.datav.linkage-target-panel-not-found",
            "Linkage target panel not found: {targetPanelId} (source panel: {panelId})",
            ARG_TARGET_PANEL_ID, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_INVALID_JUMP_TARGET = define(
            "nop.err.datav.invalid-jump-target",
            "Invalid jump target: targetType={targetType}, targetId={targetId} (panel: {panelId})",
            ARG_TARGET_TYPE, ARG_TARGET_ID, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED = define(
            "nop.err.datav.linkage-field-not-matched",
            "Linkage click context is missing required non-empty 'field' (panel: {panelId}, sourceField={sourceField})",
            ARG_SOURCE_FIELD, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_INVALID_FILTER_STATE = define(
            "nop.err.datav.invalid-filter-state",
            "Invalid filter state content for dashboard: {dashboardId}, reason: {reason}",
            ARG_DASHBOARD_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_NOT_DASHBOARD_OWNER = define(
            "nop.err.datav.not-dashboard-owner",
            "User {userName} is not the owner of dashboard: {dashboardId}",
            ARG_USER_NAME, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SHARE_TOKEN_GENERATE_FAILED = define(
            "nop.err.datav.share-token-generate-failed",
            "Failed to generate a unique share token after retries",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SHARE_NOT_FOUND = define(
            "nop.err.datav.share-not-found",
            "Share link not found for shareId: {shareId}",
            ARG_SHARE_ID
    );

    ErrorCode ERR_DATAV_SHARE_TOKEN_NOT_FOUND = define(
            "nop.err.datav.share-token-not-found",
            "Share link not found for token: {shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_DISABLED = define(
            "nop.err.datav.share-disabled",
            "Share link is disabled: {shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_EXPIRED = define(
            "nop.err.datav.share-expired",
            "Share link has expired: {shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_PASSWORD_REQUIRED = define(
            "nop.err.datav.share-password-required",
            "Password is required for share link: {shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_PASSWORD_MISMATCH = define(
            "nop.err.datav.share-password-mismatch",
            "Password does not match for share link: {shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND = define(
            "nop.err.datav.share-dashboard-not-found",
            "Dashboard of share link has been deleted: {shareToken} (dashboardId: {dashboardId})",
            ARG_SHARE_TOKEN, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_EXPORT_TASK_NOT_FOUND = define(
            "nop.err.datav.export-task-not-found",
            "Export task not found: {taskId}",
            ARG_TASK_ID
    );

    ErrorCode ERR_DATAV_EXPORT_MISSING_SOURCE = define(
            "nop.err.datav.export-missing-source",
            "Export source is missing: sourceType={sourceType}, sourceId={sourceId}",
            ARG_SOURCE_TYPE, ARG_SOURCE_ID
    );

    ErrorCode ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED = define(
            "nop.err.datav.export-type-not-supported",
            "Export format not supported: {format}. Supported: csv, xlsx.",
            ARG_FORMAT
    );

    ErrorCode ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED = define(
            "nop.err.datav.export-row-limit-exceeded",
            "Export row count {rowCount} exceeds maximum {maxRows}",
            ARG_ROW_COUNT, ARG_MAX_ROWS
    );

    ErrorCode ERR_DATAV_EXPORT_CONCURRENCY_LIMIT = define(
            "nop.err.datav.export-concurrency-limit",
            "Concurrent export task count {currentConcurrent} exceeds maximum {maxConcurrent}",
            ARG_CURRENT_CONCURRENT, ARG_MAX_CONCURRENT
    );

    ErrorCode ERR_DATAV_EXPORT_NOT_OWNER = define(
            "nop.err.datav.export-not-owner",
            "User {userName} is not the owner of export task: {taskId}",
            ARG_USER_NAME, ARG_TASK_ID
    );

    ErrorCode ERR_DATAV_EXPORT_NOT_FINISHED = define(
            "nop.err.datav.export-not-finished",
            "Export task is not finished (current status prevents download): {taskId}",
            ARG_TASK_ID
    );

    ErrorCode ERR_DATAV_EXPORT_FAILED = define(
            "nop.err.datav.export-failed",
            "Export task failed: {taskId}, reason: {reason}",
            ARG_TASK_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS = define(
            "nop.err.datav.export-no-exportable-panels",
            "Dashboard has no exportable (needsDataset) panels: {dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SCREEN_NOT_FOUND = define(
            "nop.err.datav.screen-not-found",
            "Screen not found: {screenId}",
            ARG_SCREEN_ID
    );

    ErrorCode ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND = define(
            "nop.err.datav.screen-snapshot-not-found",
            "Published snapshot not found for screen: {screenId}",
            ARG_SCREEN_ID
    );

    ErrorCode ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND = define(
            "nop.err.datav.screen-snapshot-version-not-found",
            "Snapshot version {snapshotVersion} not found for screen: {screenId}",
            ARG_SCREEN_ID, ARG_SNAPSHOT_VERSION
    );

    ErrorCode ERR_DATAV_INVALID_SCREEN_LAYOUT = define(
            "nop.err.datav.invalid-screen-layout",
            "Invalid screen layout JSON for screen: {screenId}, reason: {reason}",
            ARG_SCREEN_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT = define(
            "nop.err.datav.screen-widget-unknown-component",
            "Screen widget references unknown component type: {componentType} (widget: {widgetId})",
            ARG_COMPONENT_TYPE, ARG_WIDGET_ID
    );

    ErrorCode ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS = define(
            "nop.err.datav.screen-widget-out-of-bounds",
            "Screen widget out of canvas bounds: widget={widgetId}, canvas={canvasWidth}x{canvasHeight}",
            ARG_WIDGET_ID, ARG_CANVAS_WIDTH, ARG_CANVAS_HEIGHT
    );

    ErrorCode ERR_DATAV_INVALID_THEME_CONFIG = define(
            "nop.err.datav.invalid-theme-config",
            "Invalid screen theme config for screen: {screenId}, field: {themeField}, reason: {reason}",
            ARG_SCREEN_ID, ARG_THEME_FIELD, ARG_REASON
    );

    // ===== D5-1 定时报告 =====

    ErrorCode ERR_DATAV_REPORT_TASK_NOT_FOUND = define(
            "nop.err.datav.report-task-not-found",
            "Report task not found: {reportTaskId}",
            ARG_REPORT_TASK_ID
    );

    ErrorCode ERR_DATAV_REPORT_CRON_INVALID = define(
            "nop.err.datav.report-cron-invalid",
            "Invalid cron expression: {cronExpr}",
            ARG_CRON_EXPR
    );

    ErrorCode ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD = define(
            "nop.err.datav.report-no-publishable-dashboard",
            "Dashboard has no published snapshot, cannot render report: {dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_REPORT_DELIVERY_FAILED = define(
            "nop.err.datav.report-delivery-failed",
            "Report delivery failed for task: {reportTaskId}, reason: {reason}",
            ARG_REPORT_TASK_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL = define(
            "nop.err.datav.report-no-notifiable-channel",
            "No notifiable channel configured for report task: {reportTaskId} (notifyChannels is empty)",
            ARG_REPORT_TASK_ID, ARG_NOTIFY_CHANNELS
    );

    ErrorCode ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED = define(
            "nop.err.datav.report-sender-not-configured",
            "Email sender not configured (nop.datav.report.default-sender is empty), cannot send email for report task: {reportTaskId}",
            ARG_REPORT_TASK_ID
    );

    ErrorCode ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND = define(
            "nop.err.datav.report-template-not-found",
            "Notice template not found for templateKey: {templateKey} (expected NopSysNoticeTemplate.name match)",
            ARG_TEMPLATE_KEY
    );

    ErrorCode ERR_DATAV_REPORT_NOT_DASHBOARD_OWNER = define(
            "nop.err.datav.report-not-dashboard-owner",
            "User {userName} is not the owner of dashboard {dashboardId}, cannot manage report task",
            ARG_USER_NAME, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_REPORT_CHANNEL_SERVICE_NOT_CONFIGURED = define(
            "nop.err.datav.report-channel-service-not-configured",
            "Channel message service (IChannelMessageService) is not configured, cannot send IM notification for report task: {reportTaskId}",
            ARG_REPORT_TASK_ID
    );

    ErrorCode ERR_DATAV_REPORT_ALL_NOTIFY_FAILED = define(
            "nop.err.datav.report-all-notify-failed",
            "All notification channels failed for report task: {reportTaskId} (notifyChannels={notifyChannels}, noBindingCount={noBindingCount})",
            ARG_REPORT_TASK_ID, ARG_NOTIFY_CHANNELS, ARG_NO_BINDING_COUNT
    );

    // ===== D5-2 轻量告警 =====

    ErrorCode ERR_DATAV_ALERT_RULE_NOT_FOUND = define(
            "nop.err.datav.alert-rule-not-found",
            "Alert rule not found: {alertRuleId}",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND = define(
            "nop.err.datav.alert-value-field-not-found",
            "Value field not found in panel result columns: {valueField} (alertRule: {alertRuleId})",
            ARG_VALUE_FIELD, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_VALUE_NOT_NUMERIC = define(
            "nop.err.datav.alert-value-not-numeric",
            "Aggregated value is not numeric: {currentValue} (valueField: {valueField}, alertRule: {alertRuleId})",
            ARG_CURRENT_VALUE, ARG_VALUE_FIELD, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_INVALID_THRESHOLD = define(
            "nop.err.datav.alert-invalid-threshold",
            "Invalid threshold config: operator=between requires thresholdValue2 not null (alertRule: {alertRuleId}, thresholdValue: {thresholdValue})",
            ARG_ALERT_RULE_ID, ARG_THRESHOLD_VALUE
    );

    ErrorCode ERR_DATAV_ALERT_VALUE_REQUIRED = define(
            "nop.err.datav.alert-value-required",
            "Alert evaluation requires non-null currentValue and thresholdValue (alertRule: {alertRuleId})",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_UNSUPPORTED_OPERATOR = define(
            "nop.err.datav.alert-unsupported-operator",
            "Unsupported alert operator: {operator} (alertRule: {alertRuleId})",
            ARG_ALERT_OPERATOR, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_UNSUPPORTED_AGGREGATION = define(
            "nop.err.datav.alert-unsupported-aggregation",
            "Unsupported alert aggregation: {aggregation} (alertRule: {alertRuleId})",
            ARG_AGGREGATION, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND = define(
            "nop.err.datav.alert-template-not-found",
            "Notice template not found for templateKey: {templateKey} (expected NopSysNoticeTemplate.name match)",
            ARG_TEMPLATE_KEY
    );

    ErrorCode ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED = define(
            "nop.err.datav.alert-sender-not-configured",
            "Email sender not configured (nop.datav.report.default-sender is empty), cannot send email for alert rule: {alertRuleId}",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL = define(
            "nop.err.datav.alert-no-notifiable-channel",
            "No notifiable channel configured for alert rule: {alertRuleId} (notifyChannels is empty)",
            ARG_ALERT_RULE_ID, ARG_NOTIFY_CHANNELS
    );

    ErrorCode ERR_DATAV_ALERT_PANEL_NOT_FOUND = define(
            "nop.err.datav.alert-panel-not-found",
            "Alert rule references a panel that no longer exists: {panelId} (alertRule: {alertRuleId})",
            ARG_PANEL_ID, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_NOT_OWNER = define(
            "nop.err.datav.alert-not-owner",
            "User {userName} is not the owner of alert rule: {alertRuleId}",
            ARG_USER_NAME, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_CHANNEL_SERVICE_NOT_CONFIGURED = define(
            "nop.err.datav.alert-channel-service-not-configured",
            "Channel message service (IChannelMessageService) is not configured, cannot send IM notification for alert rule: {alertRuleId}",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_ALL_NOTIFY_FAILED = define(
            "nop.err.datav.alert-all-notify-failed",
            "All notification channels failed for alert rule: {alertRuleId} (notifyChannels={notifyChannels}, noBindingCount={noBindingCount})",
            ARG_ALERT_RULE_ID, ARG_NOTIFY_CHANNELS, ARG_NO_BINDING_COUNT
    );

    // ===== D6-1 ChatBI =====

    String ARG_QUESTION = "question";
    String ARG_TOOL_NAME = "toolName";
    String ARG_ITERATIONS = "iterations";
    String ARG_MAX_ITERATIONS = "maxIterations";
    String ARG_DATASET_SID = "datasetSid";

    ErrorCode ERR_DATAV_CHATBI_AI_NOT_AVAILABLE = define(
            "nop.err.datav.chatbi-ai-not-available",
            "ChatBI AI service is not available (nop-ai IChatService/IToolManager not registered). Question: {question}",
            ARG_QUESTION
    );

    ErrorCode ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED = define(
            "nop.err.datav.chatbi-max-iterations-exceeded",
            "ChatBI tool-calling exceeded max iterations ({maxIterations}) without a final answer. Question: {question}",
            ARG_MAX_ITERATIONS, ARG_QUESTION
    );

    ErrorCode ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED = define(
            "nop.err.datav.chatbi-tool-execution-failed",
            "ChatBI tool execution failed for tool: {toolName}, reason: {reason}",
            ARG_TOOL_NAME, ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_NO_RESULT = define(
            "nop.err.datav.chatbi-no-result",
            "ChatBI produced no result for question: {question}",
            ARG_QUESTION
    );

    ErrorCode ERR_DATAV_CHATBI_DATASET_NOT_FOUND = define(
            "nop.err.datav.chatbi-dataset-not-found",
            "ChatBI dataset not found: {datasetSid}",
            ARG_DATASET_SID
    );

    ErrorCode ERR_DATAV_CHATBI_DATASET_NOT_SQL = define(
            "nop.err.datav.chatbi-dataset-not-sql",
            "ChatBI dataset is not a SQL dataset (dsType must be 'sql'): {datasetSid}",
            ARG_DATASET_SID
    );

    // AR-4: ChatBI dataset-query 专用 ErrorCode（不复用 panel 路径共享的 ERR_DATAV_QUERY_FAILED，
    // 后者 message 绑定 {panelId}，被 PanelDataBinder/PanelSqlBuilder 等 3 处看板路径调用）。
    ErrorCode ERR_DATAV_CHATBI_DATASET_QUERY_FAILED = define(
            "nop.err.datav.chatbi-dataset-query-failed",
            "ChatBI dataset query execution failed for dataset: {datasetSid}, reason: {reason}",
            ARG_DATASET_SID, ARG_REASON
    );

    // ===== D6-1b ChatBI 看板生成 =====

    String ARG_DASHBOARD_NAME = "dashboardName";
    String ARG_FIELD_MAPPING = "fieldMapping";

    ErrorCode ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC = define(
            "nop.err.datav.chatbi-generate-invalid-spec",
            "ChatBI generate-dashboard received an invalid spec: {reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND = define(
            "nop.err.datav.chatbi-generate-dataset-not-found",
            "ChatBI generate-dashboard referenced a dataset that does not exist or is not active: {datasetSid}",
            ARG_DATASET_SID
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT = define(
            "nop.err.datav.chatbi-generate-unsupported-component",
            "ChatBI generate-dashboard received a decorative/media component type that is not allowed for dashboards (only the 8 dashboard component types are allowed): {componentType}",
            ARG_COMPONENT_TYPE
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT = define(
            "nop.err.datav.chatbi-generate-unknown-component",
            "ChatBI generate-dashboard received an unknown component type: {componentType}",
            ARG_COMPONENT_TYPE
    );

    // ===== D6-2 ChatBI 大屏生成 =====

    String ARG_SCREEN_NAME = "screenName";

    ErrorCode ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION = define(
            "nop.err.datav.chatbi-generate-invalid-widget-position",
            "ChatBI generate-screen received an invalid widget position (x/y must be non-negative, w/h must be positive): {reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS = define(
            "nop.err.datav.chatbi-generate-widget-out-of-bounds",
            "ChatBI generate-screen received a widget that exceeds the canvas bounds (x+w must be <= screenWidth, y+h must be <= screenHeight): {reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME = define(
            "nop.err.datav.chatbi-generate-duplicate-screen-name",
            "ChatBI generate-screen received a duplicate screenName (a screen with this name already exists): {screenName}",
            ARG_SCREEN_NAME
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_INVALID_BACKGROUND_CONFIG = define(
            "nop.err.datav.chatbi-generate-invalid-background-config",
            "ChatBI generate-screen received an invalid backgroundConfig that cannot be parsed as screen theme: {reason}",
            ARG_REASON
    );

    // ===== ChatBI 多轮会话（D6-1 follow-up，ai-design.md §10） =====

    String ARG_SESSION_ID = "sessionId";

    ErrorCode ERR_DATAV_CHATBI_SESSION_NOT_FOUND = define(
            "nop.err.datav.chatbi-session-not-found",
            "ChatBI session not found or deleted: {sessionId}",
            ARG_SESSION_ID
    );

    ErrorCode ERR_DATAV_CHATBI_NOT_SESSION_OWNER = define(
            "nop.err.datav.chatbi-not-session-owner",
            "User {userName} is not the owner of ChatBI session: {sessionId}",
            ARG_USER_NAME, ARG_SESSION_ID
    );
}
