package nop.ai.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopAiDaoConstants {
    
    /**
     * 消息类型: 用户 用户输入消息
     */
    String MESSAGE_TYPE_USER = "002";
                    
    /**
     * 消息类型: 助手 AI回复消息
     */
    String MESSAGE_TYPE_TOOL = "003";
                    
    /**
     * 规则类型: 编码规范 代码格式和命名规则
     */
    String RULE_TYPE_CODING_STYLE = "001";
                    
    /**
     * 规则类型: 安全规则 安全检测和防护规则
     */
    String RULE_TYPE_SECURITY = "002";
                    
    /**
     * 规则类型: 性能规则 性能优化规则
     */
    String RULE_TYPE_PERFORMANCE = "003";
                    
    /**
     * 规则类型: 架构规则 系统架构约束
     */
    String RULE_TYPE_ARCHITECTURE = "004";
                    
    /**
     * 规则类型: 自定义规则 用户自定义规则
     */
    String RULE_TYPE_CUSTOM = "005";
                    
    /**
     * 配置类型: 文本 字符串类型配置
     */
    String CONFIG_TYPE_TEXT = "001";
                    
    /**
     * 配置类型: 数值 数字类型配置
     */
    String CONFIG_TYPE_NUMBER = "002";
                    
    /**
     * 配置类型: 布尔 真假值配置
     */
    String CONFIG_TYPE_BOOLEAN = "003";
                    
    /**
     * AI供应商: OpenAI OpenAI服务
     */
    String MODEL_PROVIDER_OPENAI = "001";
                    
    /**
     * AI供应商: Claude Claude服务
     */
    String MODEL_PROVIDER_CLAUDE = "002";
                    
    /**
     * AI供应商: 本地模型 本地部署模型
     */
    String MODEL_PROVIDER_LOCAL = "003";
                    
    /**
     * 需求类型: 总览 需求概览
     */
    String REQUIREMENT_TYPE_OVERVIEW = "001";
                    
    /**
     * 需求类型: 模块 功能模块需求
     */
    String REQUIREMENT_TYPE_MODULE = "002";
                    
    /**
     * 需求类型: 用例 测试用例需求
     */
    String REQUIREMENT_TYPE_CASE = "003";
                    
    /**
     * 状态类型: 草稿 可编辑状态
     */
    String STATUS_TYPE_DRAFT = "001";
                    
    /**
     * 状态类型: 初步定稿 需人工确认
     */
    String STATUS_TYPE_PRE_FINAL = "002";
                    
    /**
     * 状态类型: 最终定稿 不可修改状态
     */
    String STATUS_TYPE_FINAL = "003";
                    
    /**
     * 文件格式: 纯文本 无格式文本
     */
    String FILE_FORMAT_TEXT = "001";
                    
    /**
     * 文件格式: Markdown Markdown格式
     */
    String FILE_FORMAT_MARKDOWN = "002";
                    
    /**
     * 模块类型: ORM模块 数据库模型
     */
    String MODULE_TYPE_ORM = "001";
                    
    /**
     * 模块类型: API模块 服务接口
     */
    String MODULE_TYPE_API = "002";
                    
    /**
     * 模块类型: UI模块 页面定义
     */
    String MODULE_TYPE_UI = "003";
                    
}
