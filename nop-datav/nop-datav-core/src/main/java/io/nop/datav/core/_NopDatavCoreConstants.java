package io.nop.datav.core;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopDatavCoreConstants {
    
    /**
     * 看板类型: 看板 
     */
    int DASHBOARD_TYPE_DASHBOARD = 0;
                    
    /**
     * 看板类型: 报表 
     */
    int DASHBOARD_TYPE_REPORT = 10;
                    
    /**
     * 发布状态: 草稿 
     */
    int PUBLISH_STATUS_DRAFT = 0;
                    
    /**
     * 发布状态: 已发布 
     */
    int PUBLISH_STATUS_PUBLISHED = 10;
                    
    /**
     * 面板类型: 图表 
     */
    int PANEL_TYPE_CHART = 0;
                    
    /**
     * 面板类型: 表格 
     */
    int PANEL_TYPE_TABLE = 10;
                    
    /**
     * 面板类型: 指标 
     */
    int PANEL_TYPE_METRIC = 20;
                    
    /**
     * 面板类型: 文本 
     */
    int PANEL_TYPE_TEXT = 30;
                    
    /**
     * 面板类型: 容器 
     */
    int PANEL_TYPE_CONTAINER = 40;
                    
    /**
     * 面板类型: 透视表 
     */
    int PANEL_TYPE_PIVOT_TABLE = 50;
                    
    /**
     * 面板类型: 地图 
     */
    int PANEL_TYPE_MAP = 60;
                    
    /**
     * 面板类型: 内嵌页面 
     */
    int PANEL_TYPE_IFRAME = 70;
                    
    /**
     * 导出来源: 面板 
     */
    String EXPORT_SOURCE_PANEL = "panel";
                    
    /**
     * 导出来源: 看板 
     */
    String EXPORT_SOURCE_DASHBOARD = "dashboard";
                    
    /**
     * 导出格式: CSV 
     */
    String EXPORT_FORMAT_CSV = "csv";
                    
    /**
     * 导出格式: Excel 
     */
    String EXPORT_FORMAT_XLSX = "xlsx";
                    
    /**
     * 导出任务状态: 待执行 
     */
    int EXPORT_STATUS_PENDING = 0;
                    
    /**
     * 导出任务状态: 执行中 
     */
    int EXPORT_STATUS_RUNNING = 10;
                    
    /**
     * 导出任务状态: 成功 
     */
    int EXPORT_STATUS_SUCCEEDED = 20;
                    
    /**
     * 导出任务状态: 失败 
     */
    int EXPORT_STATUS_FAILED = 30;
                    
    /**
     * 导出任务状态: 已取消 
     */
    int EXPORT_STATUS_CANCELLED = 40;
                    
    /**
     * 大屏适配模式: 高度优先 
     */
    int SCREEN_ADAPTOR_HEIGHT_FIRST = 0;
                    
    /**
     * 大屏适配模式: 整体铺满 
     */
    int SCREEN_ADAPTOR_FULL = 10;
                    
    /**
     * 大屏适配模式: 保持原始 
     */
    int SCREEN_ADAPTOR_KEEP = 20;
                    
    /**
     * 报告任务状态: 已禁用 
     */
    int REPORT_TASK_STATUS_DISABLED = 0;
                    
    /**
     * 报告任务状态: 已启用 
     */
    int REPORT_TASK_STATUS_ENABLED = 10;
                    
    /**
     * 报告交付状态: 待执行 
     */
    int DELIVERY_STATUS_PENDING = 0;
                    
    /**
     * 报告交付状态: 执行中 
     */
    int DELIVERY_STATUS_RUNNING = 10;
                    
    /**
     * 报告交付状态: 成功 
     */
    int DELIVERY_STATUS_SUCCEEDED = 20;
                    
    /**
     * 报告交付状态: 失败 
     */
    int DELIVERY_STATUS_FAILED = 30;
                    
    /**
     * 报告交付状态: 已跳过 
     */
    int DELIVERY_STATUS_SKIPPED = 40;
                    
    /**
     * 报告触发来源: 定时触发 
     */
    String REPORT_TRIGGER_SOURCE_SCHEDULE = "schedule";
                    
    /**
     * 报告触发来源: 手动触发 
     */
    String REPORT_TRIGGER_SOURCE_MANUAL = "manual";
                    
    /**
     * 通知渠道: 邮件 
     */
    String NOTIFY_CHANNEL_EMAIL = "email";
                    
    /**
     * 通知渠道: 即时消息 
     */
    String NOTIFY_CHANNEL_IM = "im";
                    
}
