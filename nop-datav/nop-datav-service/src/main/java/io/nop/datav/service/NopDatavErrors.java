package io.nop.datav.service;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * nop-datav 业务错误码。
 *
 * <p><b>语言契约（P1-07，plan 2026-08-15-2146-3 Phase 4）</b>：define() 描述统一中文
 * （对齐 docs-for-ai/02-core-guides/error-handling.md 契约与 nop-auth 惯例——nop-datav 不在
 * 转换类错误码英文白名单内，全部为业务错误码，无逐条例外）；英文翻译经 i18n 机制提供
 * （`_vfs/i18n/en/nop-datav-errors.i18n.yaml`，键=错误码全串，`I18nMessageManager` 按 locale
 * 查找，缺失时回退本文件默认描述）。错误码字符串键与 ARG 集合保持稳定（wire 兼容）。</p>
 */
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
            "看板不存在：{dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SNAPSHOT_NOT_FOUND = define(
            "nop.err.datav.snapshot-not-found",
            "看板没有已发布的快照：{dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND = define(
            "nop.err.datav.snapshot-version-not-found",
            "看板 {dashboardId} 不存在快照版本 {snapshotVersion}",
            ARG_DASHBOARD_ID, ARG_SNAPSHOT_VERSION
    );

    ErrorCode ERR_DATAV_PANEL_NOT_FOUND = define(
            "nop.err.datav.panel-not-found",
            "面板不存在：{panelId}",
            ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_DATASET_REF_NOT_FOUND = define(
            "nop.err.datav.dataset-ref-not-found",
            "数据集引用不存在：{datasetRefId}",
            ARG_DATASET_REF_ID
    );

    ErrorCode ERR_DATAV_DATASET_NOT_FOUND = define(
            "nop.err.datav.dataset-not-found",
            "引用的 nop-report 数据集不存在：{refDatasetId}",
            ARG_REF_DATASET_ID
    );

    ErrorCode ERR_DATAV_UNSUPPORTED_DATASET_TYPE = define(
            "nop.err.datav.unsupported-dataset-type",
            "不支持的数据集类型：{dsType}，仅支持 'sql'",
            ARG_DS_TYPE
    );

    ErrorCode ERR_DATAV_QUERY_FAILED = define(
            "nop.err.datav.query-failed",
            "面板数据集查询执行失败：{panelId}",
            ARG_PANEL_ID
    );

    // ===== 批量面板查询（getDashboardData，裁定见 runtime-design.md §4.8） =====

    ErrorCode ERR_DATAV_PANEL_NOT_IN_DASHBOARD = define(
            "nop.err.datav.panel-not-in-dashboard",
            "面板不属于该看板：{panelId}（dashboardId: {dashboardId}）",
            ARG_PANEL_ID, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_DASHBOARD_PANEL_LIMIT_EXCEEDED = define(
            "nop.err.datav.dashboard-panel-limit-exceeded",
            "看板面板数 {panelCount} 超过上限 {maxPanels}",
            ARG_PANEL_COUNT, ARG_MAX_PANELS
    );

    ErrorCode ERR_DATAV_UNKNOWN_COMPONENT_TYPE = define(
            "nop.err.datav.unknown-component-type",
            "未知的面板组件类型：{componentType}",
            ARG_COMPONENT_TYPE
    );

    ErrorCode ERR_DATAV_INVALID_PANEL_CONFIG = define(
            "nop.err.datav.invalid-panel-config",
            "面板 panelConfig JSON 不合法：{panelId}",
            ARG_PANEL_ID
    );

    // ===== flux 布局对齐（D1-4，裁定见 runtime-design.md §9.12） =====

    String ARG_LENGTH = "length";
    String ARG_MAX_LENGTH = "maxLength";

    ErrorCode ERR_DATAV_INVALID_LAYOUT = define(
            "nop.err.datav.invalid-layout",
            "看板布局不合法：{reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_LAYOUT_DUPLICATE_PANEL_ID = define(
            "nop.err.datav.layout-duplicate-panel-id",
            "布局中存在重复的面板 ID：{panelId}",
            ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_LAYOUT_FOREIGN_PANEL_ID = define(
            "nop.err.datav.layout-foreign-panel-id",
            "面板 ID 属于其他看板：{panelId}（dashboardId: {dashboardId}）",
            ARG_PANEL_ID, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_LAYOUT_CONFIG_OVERFLOW = define(
            "nop.err.datav.layout-config-overflow",
            "序列化后的 layoutConfig 长度 {length} 超过列上限 {maxLength}",
            ARG_LENGTH, ARG_MAX_LENGTH
    );

    ErrorCode ERR_DATAV_PANEL_CONFIG_OVERFLOW = define(
            "nop.err.datav.panel-config-overflow",
            "面板 {panelId} 序列化后的 panelConfig 长度 {length} 超过列上限 {maxLength}",
            ARG_PANEL_ID, ARG_LENGTH, ARG_MAX_LENGTH
    );

    // 注：此 ErrorCode 由 PanelParamEvaluator（面板 paramMapping）、DashboardParamParser（看板 paramConfig）、
    // DashboardFilterUrlCodec/Resolver（filter）共用。message 保持中性（不绑定单一实体名词），
    // 面板路径经 PanelDataBinder 补 ARG_PANEL_ID 作为结构化 param（供 GraphQL 错误响应/聚合），
    // 看板/filter 路径无 panelId 上下文故不填——若硬编码 "面板:{panelId}" 会误标看板/filter 错误。
    ErrorCode ERR_DATAV_INVALID_PARAM_CONFIG = define(
            "nop.err.datav.invalid-param-config",
            "参数配置不合法：{reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_UNKNOWN_PARAM_NAME = define(
            "nop.err.datav.unknown-param-name",
            "未知的看板参数：{paramName}",
            ARG_PARAM_NAME
    );

    ErrorCode ERR_DATAV_PARAM_TYPE_MISMATCH = define(
            "nop.err.datav.param-type-mismatch",
            "参数 {paramName} 的值 '{value}' 与期望类型 {expectedType} 不匹配",
            ARG_PARAM_NAME, ARG_VALUE, ARG_EXPECTED_TYPE
    );

    // ===== D2-4 flux dashboard-filter 对齐（裁定见 linkage-design.md §11.6） =====

    String ARG_WIDGET = "widget";

    ErrorCode ERR_DATAV_FILTER_DEF_UNKNOWN_WIDGET = define(
            "nop.err.datav.filter-def-unknown-widget",
            "参数 '{paramName}' 使用了未知的筛选组件 '{widget}'（支持 dropdown、date-picker）",
            ARG_WIDGET, ARG_PARAM_NAME
    );

    ErrorCode ERR_DATAV_FILTER_DEF_WIDGET_TYPE_MISMATCH = define(
            "nop.err.datav.filter-def-widget-type-mismatch",
            "筛选组件 '{widget}' 不适用于参数类型 {expectedType}（参数：{paramName}）",
            ARG_WIDGET, ARG_EXPECTED_TYPE, ARG_PARAM_NAME
    );

    ErrorCode ERR_DATAV_INVALID_LINKAGE_CONFIG = define(
            "nop.err.datav.invalid-linkage-config",
            "面板联动配置 JSON 不合法：{panelId}，原因：{reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_INVALID_JUMP_CONFIG = define(
            "nop.err.datav.invalid-jump-config",
            "面板跳转配置 JSON 不合法：{panelId}，原因：{reason}",
            ARG_PANEL_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_LINKAGE_TARGET_PANEL_NOT_FOUND = define(
            "nop.err.datav.linkage-target-panel-not-found",
            "联动目标面板不存在：{targetPanelId}（源面板：{panelId}）",
            ARG_TARGET_PANEL_ID, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_INVALID_JUMP_TARGET = define(
            "nop.err.datav.invalid-jump-target",
            "非法的跳转目标：targetType={targetType}, targetId={targetId}（面板：{panelId}）",
            ARG_TARGET_TYPE, ARG_TARGET_ID, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_LINKAGE_FIELD_NOT_MATCHED = define(
            "nop.err.datav.linkage-field-not-matched",
            "联动点击上下文缺少非空的 'field' 字段（面板：{panelId}，sourceField={sourceField}）",
            ARG_SOURCE_FIELD, ARG_PANEL_ID
    );

    ErrorCode ERR_DATAV_INVALID_FILTER_STATE = define(
            "nop.err.datav.invalid-filter-state",
            "看板筛选状态内容不合法：{dashboardId}，原因：{reason}",
            ARG_DASHBOARD_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_NOT_DASHBOARD_OWNER = define(
            "nop.err.datav.not-dashboard-owner",
            "用户 {userName} 不是看板 {dashboardId} 的所有者",
            ARG_USER_NAME, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SHARE_TOKEN_GENERATE_FAILED = define(
            "nop.err.datav.share-token-generate-failed",
            "多次重试后仍无法生成唯一的分享令牌",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SHARE_NOT_FOUND = define(
            "nop.err.datav.share-not-found",
            "分享链接不存在（shareId: {shareId}）",
            ARG_SHARE_ID
    );

    ErrorCode ERR_DATAV_SHARE_TOKEN_NOT_FOUND = define(
            "nop.err.datav.share-token-not-found",
            "分享链接不存在（token: {shareToken}）",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_DISABLED = define(
            "nop.err.datav.share-disabled",
            "分享链接已停用：{shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_EXPIRED = define(
            "nop.err.datav.share-expired",
            "分享链接已过期：{shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_PASSWORD_REQUIRED = define(
            "nop.err.datav.share-password-required",
            "分享链接需要访问密码：{shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_PASSWORD_MISMATCH = define(
            "nop.err.datav.share-password-mismatch",
            "分享链接访问密码不正确：{shareToken}",
            ARG_SHARE_TOKEN
    );

    ErrorCode ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND = define(
            "nop.err.datav.share-dashboard-not-found",
            "分享链接对应的看板已被删除：{shareToken}（dashboardId: {dashboardId}）",
            ARG_SHARE_TOKEN, ARG_DASHBOARD_ID
    );

    // ===== 分享访问限流（plan 2026-08-15-0004-2，裁定见 permission-sharing-design.md「访问限流与访问统计」） =====

    String ARG_RETRY_AFTER_SECONDS = "retryAfterSeconds";

    ErrorCode ERR_DATAV_SHARE_RATE_LIMITED = define(
            "nop.err.datav.share-rate-limited",
            "分享链接访问过于频繁：{shareToken}，请在 {retryAfterSeconds} 秒后重试",
            ARG_SHARE_TOKEN, ARG_RETRY_AFTER_SECONDS
    );

    ErrorCode ERR_DATAV_SHARE_PASSWORD_LOCKED = define(
            "nop.err.datav.share-password-locked",
            "分享链接因多次密码错误被临时锁定：{shareToken}，请在 {retryAfterSeconds} 秒后重试",
            ARG_SHARE_TOKEN, ARG_RETRY_AFTER_SECONDS
    );

    ErrorCode ERR_DATAV_EXPORT_TASK_NOT_FOUND = define(
            "nop.err.datav.export-task-not-found",
            "导出任务不存在：{taskId}",
            ARG_TASK_ID
    );

    ErrorCode ERR_DATAV_EXPORT_MISSING_SOURCE = define(
            "nop.err.datav.export-missing-source",
            "导出来源缺失：sourceType={sourceType}, sourceId={sourceId}",
            ARG_SOURCE_TYPE, ARG_SOURCE_ID
    );

    ErrorCode ERR_DATAV_EXPORT_TYPE_NOT_SUPPORTED = define(
            "nop.err.datav.export-type-not-supported",
            "不支持的导出格式：{format}，支持 csv、xlsx",
            ARG_FORMAT
    );

    ErrorCode ERR_DATAV_EXPORT_ROW_LIMIT_EXCEEDED = define(
            "nop.err.datav.export-row-limit-exceeded",
            "导出行数 {rowCount} 超过上限 {maxRows}",
            ARG_ROW_COUNT, ARG_MAX_ROWS
    );

    ErrorCode ERR_DATAV_EXPORT_CONCURRENCY_LIMIT = define(
            "nop.err.datav.export-concurrency-limit",
            "并发导出任务数 {currentConcurrent} 超过上限 {maxConcurrent}",
            ARG_CURRENT_CONCURRENT, ARG_MAX_CONCURRENT
    );

    ErrorCode ERR_DATAV_EXPORT_NOT_OWNER = define(
            "nop.err.datav.export-not-owner",
            "用户 {userName} 不是导出任务 {taskId} 的所有者",
            ARG_USER_NAME, ARG_TASK_ID
    );

    ErrorCode ERR_DATAV_EXPORT_NOT_FINISHED = define(
            "nop.err.datav.export-not-finished",
            "导出任务尚未完成（当前状态不允许下载）：{taskId}",
            ARG_TASK_ID
    );

    ErrorCode ERR_DATAV_EXPORT_FAILED = define(
            "nop.err.datav.export-failed",
            "导出任务失败：{taskId}，原因：{reason}",
            ARG_TASK_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_EXPORT_NO_EXPORTABLE_PANELS = define(
            "nop.err.datav.export-no-exportable-panels",
            "看板没有可导出（needsDataset）的面板：{dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_SCREEN_NOT_FOUND = define(
            "nop.err.datav.screen-not-found",
            "大屏不存在：{screenId}",
            ARG_SCREEN_ID
    );

    ErrorCode ERR_DATAV_SCREEN_SNAPSHOT_NOT_FOUND = define(
            "nop.err.datav.screen-snapshot-not-found",
            "大屏没有已发布的快照：{screenId}",
            ARG_SCREEN_ID
    );

    ErrorCode ERR_DATAV_SCREEN_SNAPSHOT_VERSION_NOT_FOUND = define(
            "nop.err.datav.screen-snapshot-version-not-found",
            "大屏 {screenId} 不存在快照版本 {snapshotVersion}",
            ARG_SCREEN_ID, ARG_SNAPSHOT_VERSION
    );

    ErrorCode ERR_DATAV_INVALID_SCREEN_LAYOUT = define(
            "nop.err.datav.invalid-screen-layout",
            "大屏布局 JSON 不合法：{screenId}，原因：{reason}",
            ARG_SCREEN_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_SCREEN_WIDGET_UNKNOWN_COMPONENT = define(
            "nop.err.datav.screen-widget-unknown-component",
            "大屏组件引用了未知的组件类型：{componentType}（widget: {widgetId}）",
            ARG_COMPONENT_TYPE, ARG_WIDGET_ID
    );

    ErrorCode ERR_DATAV_SCREEN_WIDGET_OUT_OF_BOUNDS = define(
            "nop.err.datav.screen-widget-out-of-bounds",
            "大屏组件超出画布边界：widget={widgetId}，canvas={canvasWidth}x{canvasHeight}",
            ARG_WIDGET_ID, ARG_CANVAS_WIDTH, ARG_CANVAS_HEIGHT
    );

    ErrorCode ERR_DATAV_INVALID_THEME_CONFIG = define(
            "nop.err.datav.invalid-theme-config",
            "大屏主题配置不合法：{screenId}，字段：{themeField}，原因：{reason}",
            ARG_SCREEN_ID, ARG_THEME_FIELD, ARG_REASON
    );

    // ===== D5-1 定时报告 =====

    ErrorCode ERR_DATAV_REPORT_TASK_NOT_FOUND = define(
            "nop.err.datav.report-task-not-found",
            "报告任务不存在：{reportTaskId}",
            ARG_REPORT_TASK_ID
    );

    ErrorCode ERR_DATAV_REPORT_CRON_INVALID = define(
            "nop.err.datav.report-cron-invalid",
            "非法的 cron 表达式：{cronExpr}",
            ARG_CRON_EXPR
    );

    ErrorCode ERR_DATAV_REPORT_NO_PUBLISHABLE_DASHBOARD = define(
            "nop.err.datav.report-no-publishable-dashboard",
            "看板没有已发布快照，无法渲染报告：{dashboardId}",
            ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_REPORT_DELIVERY_FAILED = define(
            "nop.err.datav.report-delivery-failed",
            "报告交付失败（任务：{reportTaskId}），原因：{reason}",
            ARG_REPORT_TASK_ID, ARG_REASON
    );

    ErrorCode ERR_DATAV_REPORT_NO_NOTIFIABLE_CHANNEL = define(
            "nop.err.datav.report-no-notifiable-channel",
            "报告任务未配置可用的通知渠道：{reportTaskId}（notifyChannels 为空）",
            ARG_REPORT_TASK_ID, ARG_NOTIFY_CHANNELS
    );

    ErrorCode ERR_DATAV_REPORT_SENDER_NOT_CONFIGURED = define(
            "nop.err.datav.report-sender-not-configured",
            "发件人未配置（nop.datav.report.default-sender 为空），无法为报告任务 {reportTaskId} 发送邮件",
            ARG_REPORT_TASK_ID
    );

    ErrorCode ERR_DATAV_REPORT_TEMPLATE_NOT_FOUND = define(
            "nop.err.datav.report-template-not-found",
            "通知模板不存在（templateKey: {templateKey}，期望匹配 NopSysNoticeTemplate.name）",
            ARG_TEMPLATE_KEY
    );

    ErrorCode ERR_DATAV_REPORT_NOT_DASHBOARD_OWNER = define(
            "nop.err.datav.report-not-dashboard-owner",
            "用户 {userName} 不是看板 {dashboardId} 的所有者，无法管理报告任务",
            ARG_USER_NAME, ARG_DASHBOARD_ID
    );

    ErrorCode ERR_DATAV_REPORT_CHANNEL_SERVICE_NOT_CONFIGURED = define(
            "nop.err.datav.report-channel-service-not-configured",
            "渠道消息服务（IChannelMessageService）未配置，无法为报告任务 {reportTaskId} 发送 IM 通知",
            ARG_REPORT_TASK_ID
    );

    ErrorCode ERR_DATAV_REPORT_ALL_NOTIFY_FAILED = define(
            "nop.err.datav.report-all-notify-failed",
            "报告任务 {reportTaskId} 的所有通知渠道均失败（notifyChannels={notifyChannels}，noBindingCount={noBindingCount}）",
            ARG_REPORT_TASK_ID, ARG_NOTIFY_CHANNELS, ARG_NO_BINDING_COUNT
    );

    // ===== D5-2 轻量告警 =====

    ErrorCode ERR_DATAV_ALERT_RULE_NOT_FOUND = define(
            "nop.err.datav.alert-rule-not-found",
            "告警规则不存在：{alertRuleId}",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND = define(
            "nop.err.datav.alert-value-field-not-found",
            "面板结果列中不存在取值字段：{valueField}（告警规则：{alertRuleId}）",
            ARG_VALUE_FIELD, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_VALUE_NOT_NUMERIC = define(
            "nop.err.datav.alert-value-not-numeric",
            "聚合值不是数值：{currentValue}（valueField: {valueField}，告警规则：{alertRuleId}）",
            ARG_CURRENT_VALUE, ARG_VALUE_FIELD, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_INVALID_THRESHOLD = define(
            "nop.err.datav.alert-invalid-threshold",
            "阈值配置不合法：operator=between 要求 thresholdValue2 非空（告警规则：{alertRuleId}，thresholdValue: {thresholdValue}）",
            ARG_ALERT_RULE_ID, ARG_THRESHOLD_VALUE
    );

    ErrorCode ERR_DATAV_ALERT_VALUE_REQUIRED = define(
            "nop.err.datav.alert-value-required",
            "告警评估要求 currentValue 与 thresholdValue 非空（告警规则：{alertRuleId}）",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_UNSUPPORTED_OPERATOR = define(
            "nop.err.datav.alert-unsupported-operator",
            "不支持的告警比较操作符：{operator}（告警规则：{alertRuleId}）",
            ARG_ALERT_OPERATOR, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_UNSUPPORTED_AGGREGATION = define(
            "nop.err.datav.alert-unsupported-aggregation",
            "不支持的告警聚合方式：{aggregation}（告警规则：{alertRuleId}）",
            ARG_AGGREGATION, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND = define(
            "nop.err.datav.alert-template-not-found",
            "通知模板不存在（templateKey: {templateKey}，期望匹配 NopSysNoticeTemplate.name）",
            ARG_TEMPLATE_KEY
    );

    ErrorCode ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED = define(
            "nop.err.datav.alert-sender-not-configured",
            "发件人未配置（nop.datav.report.default-sender 为空），无法为告警规则 {alertRuleId} 发送邮件",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL = define(
            "nop.err.datav.alert-no-notifiable-channel",
            "告警规则未配置可用的通知渠道：{alertRuleId}（notifyChannels 为空）",
            ARG_ALERT_RULE_ID, ARG_NOTIFY_CHANNELS
    );

    ErrorCode ERR_DATAV_ALERT_PANEL_NOT_FOUND = define(
            "nop.err.datav.alert-panel-not-found",
            "告警规则引用的面板已不存在：{panelId}（告警规则：{alertRuleId}）",
            ARG_PANEL_ID, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_NOT_OWNER = define(
            "nop.err.datav.alert-not-owner",
            "用户 {userName} 不是告警规则 {alertRuleId} 的所有者",
            ARG_USER_NAME, ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_CHANNEL_SERVICE_NOT_CONFIGURED = define(
            "nop.err.datav.alert-channel-service-not-configured",
            "渠道消息服务（IChannelMessageService）未配置，无法为告警规则 {alertRuleId} 发送 IM 通知",
            ARG_ALERT_RULE_ID
    );

    ErrorCode ERR_DATAV_ALERT_ALL_NOTIFY_FAILED = define(
            "nop.err.datav.alert-all-notify-failed",
            "告警规则 {alertRuleId} 的所有通知渠道均失败（notifyChannels={notifyChannels}，noBindingCount={noBindingCount}）",
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
            "ChatBI AI 服务不可用（nop-ai IChatService/IToolManager 未注册）。问题：{question}",
            ARG_QUESTION
    );

    ErrorCode ERR_DATAV_CHATBI_MAX_ITERATIONS_EXCEEDED = define(
            "nop.err.datav.chatbi-max-iterations-exceeded",
            "ChatBI 工具调用超过最大迭代次数（{maxIterations}）仍未得到最终答案。问题：{question}",
            ARG_MAX_ITERATIONS, ARG_QUESTION
    );

    ErrorCode ERR_DATAV_CHATBI_TOOL_EXECUTION_FAILED = define(
            "nop.err.datav.chatbi-tool-execution-failed",
            "ChatBI 工具执行失败（工具：{toolName}），原因：{reason}",
            ARG_TOOL_NAME, ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_NO_RESULT = define(
            "nop.err.datav.chatbi-no-result",
            "ChatBI 未产生结果。问题：{question}",
            ARG_QUESTION
    );

    ErrorCode ERR_DATAV_CHATBI_DATASET_NOT_FOUND = define(
            "nop.err.datav.chatbi-dataset-not-found",
            "ChatBI 数据集不存在：{datasetSid}",
            ARG_DATASET_SID
    );

    ErrorCode ERR_DATAV_CHATBI_DATASET_NOT_SQL = define(
            "nop.err.datav.chatbi-dataset-not-sql",
            "ChatBI 数据集不是 SQL 数据集（dsType 必须为 'sql'）：{datasetSid}",
            ARG_DATASET_SID
    );

    // P1-03（plan 2026-08-15-2146-1，裁定 D4 选项 B）：ChatBI 数据集可见性（createdBy/admin）拒绝码。
    // list 侧静默过滤（枚举不泄露存在性），describe/query 侧显式拒绝（携带该码）。
    ErrorCode ERR_DATAV_CHATBI_DATASET_NO_ACCESS = define(
            "nop.err.datav.chatbi-dataset-no-access",
            "当前用户无权访问该 ChatBI 数据集：{datasetSid}（userName: {userName}）",
            ARG_DATASET_SID, ARG_USER_NAME
    );

    // AR-4: ChatBI dataset-query 专用 ErrorCode（不复用 panel 路径共享的 ERR_DATAV_QUERY_FAILED，
    // 后者 message 绑定 {panelId}，被 PanelDataBinder/PanelSqlBuilder 等 3 处看板路径调用）。
    ErrorCode ERR_DATAV_CHATBI_DATASET_QUERY_FAILED = define(
            "nop.err.datav.chatbi-dataset-query-failed",
            "ChatBI 数据集查询执行失败（数据集：{datasetSid}），原因：{reason}",
            ARG_DATASET_SID, ARG_REASON
    );

    // ===== P1-10（plan 2026-08-15-2146-1，裁定 D5 方案 c）：快照/告警状态标准 mutation 面显式拒绝 =====

    String ARG_ACTION = "action";

    ErrorCode ERR_DATAV_SNAPSHOT_STD_MUTATION_NOT_ALLOWED = define(
            "nop.err.datav.snapshot-std-mutation-not-allowed",
            "快照实体不允许标准 CRUD 写操作（action: {action}）；快照只追加，publish/rollback 是唯一写入点",
            ARG_ACTION
    );

    ErrorCode ERR_DATAV_ALERT_STATE_STD_MUTATION_NOT_ALLOWED = define(
            "nop.err.datav.alert-state-std-mutation-not-allowed",
            "告警状态不允许标准 CRUD 写操作（action: {action}）；告警评估器是唯一写入者",
            ARG_ACTION
    );

    // ===== D6-1b ChatBI 看板生成 =====

    String ARG_DASHBOARD_NAME = "dashboardName";
    String ARG_FIELD_MAPPING = "fieldMapping";

    ErrorCode ERR_DATAV_CHATBI_GENERATE_INVALID_SPEC = define(
            "nop.err.datav.chatbi-generate-invalid-spec",
            "ChatBI 生成看板收到非法的 spec：{reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_DATASET_NOT_FOUND = define(
            "nop.err.datav.chatbi-generate-dataset-not-found",
            "ChatBI 生成看板引用的数据集不存在或未启用：{datasetSid}",
            ARG_DATASET_SID
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_UNSUPPORTED_COMPONENT = define(
            "nop.err.datav.chatbi-generate-unsupported-component",
            "ChatBI 生成看板收到不允许用于看板的装饰/媒体类组件类型（仅允许 8 种看板组件类型）：{componentType}",
            ARG_COMPONENT_TYPE
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_UNKNOWN_COMPONENT = define(
            "nop.err.datav.chatbi-generate-unknown-component",
            "ChatBI 生成看板收到未知的组件类型：{componentType}",
            ARG_COMPONENT_TYPE
    );

    // D1(b)（plan 2026-08-15-2146-3 Phase 2）：dashboardName UK 物化后的重名兜底（镜像 Screen 侧
    // ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME 裁定 Q 先例——预检查 + 底层 UK 冲突映射）
    ErrorCode ERR_DATAV_CHATBI_GENERATE_DUPLICATE_DASHBOARD_NAME = define(
            "nop.err.datav.chatbi-generate-duplicate-dashboard-name",
            "看板名已存在：{dashboardName}",
            ARG_DASHBOARD_NAME
    );

    // ===== D6-2 ChatBI 大屏生成 =====

    String ARG_SCREEN_NAME = "screenName";

    ErrorCode ERR_DATAV_CHATBI_GENERATE_INVALID_WIDGET_POSITION = define(
            "nop.err.datav.chatbi-generate-invalid-widget-position",
            "ChatBI 生成大屏收到非法的组件位置（x/y 必须非负，w/h 必须为正）：{reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_WIDGET_OUT_OF_BOUNDS = define(
            "nop.err.datav.chatbi-generate-widget-out-of-bounds",
            "ChatBI 生成大屏收到超出画布边界的组件（x+w 必须 <= screenWidth，y+h 必须 <= screenHeight）：{reason}",
            ARG_REASON
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_DUPLICATE_SCREEN_NAME = define(
            "nop.err.datav.chatbi-generate-duplicate-screen-name",
            "ChatBI 生成大屏收到重复的大屏名（同名大屏已存在）：{screenName}",
            ARG_SCREEN_NAME
    );

    ErrorCode ERR_DATAV_CHATBI_GENERATE_INVALID_BACKGROUND_CONFIG = define(
            "nop.err.datav.chatbi-generate-invalid-background-config",
            "ChatBI 生成大屏收到无法解析为大屏主题的 backgroundConfig：{reason}",
            ARG_REASON
    );

    // ===== ChatBI 多轮会话（D6-1 follow-up，ai-design.md §10） =====

    String ARG_SESSION_ID = "sessionId";

    ErrorCode ERR_DATAV_CHATBI_SESSION_NOT_FOUND = define(
            "nop.err.datav.chatbi-session-not-found",
            "ChatBI 会话不存在或已删除：{sessionId}",
            ARG_SESSION_ID
    );

    ErrorCode ERR_DATAV_CHATBI_NOT_SESSION_OWNER = define(
            "nop.err.datav.chatbi-not-session-owner",
            "用户 {userName} 不是 ChatBI 会话 {sessionId} 的所有者",
            ARG_USER_NAME, ARG_SESSION_ID
    );
}
