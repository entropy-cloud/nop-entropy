package io.nop.ai.dao;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopAiDaoConstants {
    
    /**
     * 消息类型: 用户 用户输入消息
     */
    int MESSAGE_TYPE_USER = 10;
                    
    /**
     * 消息类型: 助手 AI回复消息
     */
    int MESSAGE_TYPE_ASSISTANT = 20;
                    
    /**
     * 消息类型: 压缩 上下文压缩摘要
     */
    int MESSAGE_TYPE_COMPACTION = 30;
                    
    /**
     * 消息类型: Shell Shell命令执行结果
     */
    int MESSAGE_TYPE_SHELL = 40;
                    
    /**
     * 消息类型: 合成 系统合成的中间消息
     */
    int MESSAGE_TYPE_SYNTHETIC = 50;
                    
    /**
     * 消息类型: 系统 系统级消息
     */
    int MESSAGE_TYPE_SYSTEM = 60;
                    
    /**
     * 消息类型: Agent切换 Agent切换消息
     */
    int MESSAGE_TYPE_AGENT_SWITCHED = 70;
                    
    /**
     * 消息类型: 模型切换 模型切换消息
     */
    int MESSAGE_TYPE_MODEL_SWITCHED = 80;
                    
    /**
     * 项目语言: Java Java语言项目
     */
    String PROJECT_LANGUAGE_JAVA = "001";
                    
    /**
     * 项目语言: Python Python语言项目
     */
    String PROJECT_LANGUAGE_PYTHON = "002";
                    
    /**
     * 项目语言: JavaScript JavaScript/TypeScript项目
     */
    String PROJECT_LANGUAGE_JAVASCRIPT = "003";
                    
    /**
     * 项目语言: Go Go语言项目
     */
    String PROJECT_LANGUAGE_GO = "004";
                    
    /**
     * 项目语言: C# C#语言项目
     */
    String PROJECT_LANGUAGE_CSHARP = "005";
                    
    /**
     * 项目语言: C++ C++语言项目
     */
    String PROJECT_LANGUAGE_CPP = "006";
                    
    /**
     * 项目语言: 其他语言 其他编程语言
     */
    String PROJECT_LANGUAGE_OTHER = "007";
                    
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
                    
    /**
     * 会话状态: 已创建 会话已创建
     */
    int SESSION_STATUS_CREATED = 10;
                    
    /**
     * 会话状态: 运行中 会话运行中
     */
    int SESSION_STATUS_RUNNING = 20;
                    
    /**
     * 会话状态: 空闲 会话空闲
     */
    int SESSION_STATUS_IDLE = 30;
                    
    /**
     * 会话状态: 已完成 会话已完成
     */
    int SESSION_STATUS_COMPLETED = 40;
                    
    /**
     * 会话状态: 失败 会话失败
     */
    int SESSION_STATUS_FAILED = 50;
                    
    /**
     * 会话状态: 已停止 会话已停止
     */
    int SESSION_STATUS_STOPPED = 60;
                    
    /**
     * 待办状态: 待处理 待处理
     */
    int TODO_STATUS_PENDING = 10;
                    
    /**
     * 待办状态: 进行中 进行中
     */
    int TODO_STATUS_IN_PROGRESS = 20;
                    
    /**
     * 待办状态: 已完成 已完成
     */
    int TODO_STATUS_COMPLETED = 30;
                    
    /**
     * 待办状态: 已取消 已取消
     */
    int TODO_STATUS_CANCELLED = 40;
                    
    /**
     * 待办优先级: 高 高优先级
     */
    int TODO_PRIORITY_HIGH = 10;
                    
    /**
     * 待办优先级: 中 中优先级
     */
    int TODO_PRIORITY_MEDIUM = 20;
                    
    /**
     * 待办优先级: 低 低优先级
     */
    int TODO_PRIORITY_LOW = 30;
                    
    /**
     * 输入投递方式: 立即投递 立即投递
     */
    int INPUT_DELIVERY_STEER = 10;
                    
    /**
     * 输入投递方式: 排队 排队投递
     */
    int INPUT_DELIVERY_QUEUE = 20;
                    
    /**
     * 事件类型: 创建 会话创建事件
     */
    int EVENT_TYPE_CREATED = 10;
                    
    /**
     * 事件类型: 启动 会话启动事件
     */
    int EVENT_TYPE_STARTED = 20;
                    
    /**
     * 事件类型: 结束 会话结束事件
     */
    int EVENT_TYPE_ENDED = 30;
                    
    /**
     * 事件类型: 失败 会话失败事件
     */
    int EVENT_TYPE_FAILED = 40;
                    
    /**
     * 事件类型: 压缩 上下文压缩事件
     */
    int EVENT_TYPE_COMPACTED = 50;
                    
    /**
     * 事件类型: 归档 会话归档事件
     */
    int EVENT_TYPE_ARCHIVED = 60;
                    
    /**
     * 压缩类型: 全量 全量压缩
     */
    int COMPACTION_TYPE_FULL = 10;
                    
    /**
     * 压缩类型: 增量 增量压缩
     */
    int COMPACTION_TYPE_INCREMENTAL = 20;
                    
}
