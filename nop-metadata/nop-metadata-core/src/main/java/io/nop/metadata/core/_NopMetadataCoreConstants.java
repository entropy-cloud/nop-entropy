package io.nop.metadata.core;

@SuppressWarnings({"PMD","java:S116"})
public interface _NopMetadataCoreConstants {
    
    /**
     * 模块状态: 编辑中 
     */
    String MODULE_STATUS_DRAFTING = "DRAFTING";
                    
    /**
     * 模块状态: 已发布 
     */
    String MODULE_STATUS_RELEASED = "RELEASED";
                    
    /**
     * 模块状态: 已废弃 
     */
    String MODULE_STATUS_DEPRECATED = "DEPRECATED";
                    
    /**
     * 数据源状态: 已禁用 
     */
    String DATASOURCE_STATUS_DISABLED = "DISABLED";
                    
    /**
     * 数据源状态: 启用 
     */
    String DATASOURCE_STATUS_ACTIVE = "ACTIVE";
                    
    /**
     * 数据源类型: JDBC数据库 
     */
    String DATASOURCE_TYPE_JDBC = "jdbc";
                    
    /**
     * 数据源类型: HTTP接口 
     */
    String DATASOURCE_TYPE_HTTP = "http";
                    
    /**
     * 数据源类型: REST接口 
     */
    String DATASOURCE_TYPE_REST = "rest";
                    
    /**
     * 数据源类型: 文件 
     */
    String DATASOURCE_TYPE_FILE = "file";
                    
    /**
     * 逻辑表类型: 实体表 
     */
    String TABLE_TYPE_ENTITY = "entity";
                    
    /**
     * 逻辑表类型: SQL视图 
     */
    String TABLE_TYPE_SQL = "sql";
                    
    /**
     * 逻辑表类型: 外部表 
     */
    String TABLE_TYPE_EXTERNAL = "external";
                    
    /**
     * 关系类型: 一对一/多对一 
     */
    String RELATION_TYPE_TO_ONE = "to-one";
                    
    /**
     * 关系类型: 一对多 
     */
    String RELATION_TYPE_TO_MANY = "to-many";
                    
    /**
     * 维度类型: 分类 
     */
    String DIMENSION_TYPE_CATEGORICAL = "categorical";
                    
    /**
     * 维度类型: 时间 
     */
    String DIMENSION_TYPE_TEMPORAL = "temporal";
                    
    /**
     * 维度类型: 地理 
     */
    String DIMENSION_TYPE_GEOGRAPHICAL = "geographical";
                    
    /**
     * 聚合函数: 求和 
     */
    String AGG_FUNC_SUM = "sum";
                    
    /**
     * 聚合函数: 计数 
     */
    String AGG_FUNC_COUNT = "count";
                    
    /**
     * 聚合函数: 平均 
     */
    String AGG_FUNC_AVG = "avg";
                    
    /**
     * 聚合函数: 最小值 
     */
    String AGG_FUNC_MIN = "min";
                    
    /**
     * 聚合函数: 最大值 
     */
    String AGG_FUNC_MAX = "max";
                    
    /**
     * 聚合函数: 去重计数 
     */
    String AGG_FUNC_COUNT_DISTINCT = "countDistinct";
                    
    /**
     * 关联类型: 内连接 
     */
    String JOIN_TYPE_INNER = "inner";
                    
    /**
     * 关联类型: 左连接 
     */
    String JOIN_TYPE_LEFT = "left";
                    
    /**
     * 关联类型: 右连接 
     */
    String JOIN_TYPE_RIGHT = "right";
                    
    /**
     * 侧别: 左 
     */
    String JOIN_SIDE_LEFT = "left";
                    
    /**
     * 侧别: 右 
     */
    String JOIN_SIDE_RIGHT = "right";
                    
    /**
     * 血缘转换类型: 直接映射 
     */
    String LINEAGE_TRANSFORM_DIRECT = "direct";
                    
    /**
     * 血缘转换类型: 派生 
     */
    String LINEAGE_TRANSFORM_DERIVED = "derived";
                    
    /**
     * 血缘转换类型: 聚合 
     */
    String LINEAGE_TRANSFORM_AGGREGATED = "aggregated";
                    
    /**
     * 血缘来源: 手工录入 
     */
    String LINEAGE_SOURCE_MANUAL = "manual";
                    
    /**
     * 血缘来源: SQL解析 
     */
    String LINEAGE_SOURCE_SQL_PARSE = "sql_parse";
                    
    /**
     * 血缘来源: OpenLineage 
     */
    String LINEAGE_SOURCE_OPEN_LINEAGE = "open_lineage";
                    
    /**
     * 血缘来源: Hook采集 
     */
    String LINEAGE_SOURCE_HOOK = "hook";
                    
    /**
     * 血缘来源: 指标表达式解析 
     */
    String LINEAGE_SOURCE_MEASURE_PARSE = "measure_parse";
                    
    /**
     * 管道类型: ETL 
     */
    String PIPELINE_TYPE_ETL = "etl";
                    
    /**
     * 管道类型: SQL处理 
     */
    String PIPELINE_TYPE_SQL = "sql";
                    
    /**
     * 管道类型: API拉取 
     */
    String PIPELINE_TYPE_API = "api";
                    
    /**
     * 管道类型: 手工 
     */
    String PIPELINE_TYPE_MANUAL = "manual";
                    
    /**
     * 质量规则类型: 非空检查 
     */
    String QUALITY_RULE_TYPE_NOT_NULL = "not_null";
                    
    /**
     * 质量规则类型: 唯一性检查 
     */
    String QUALITY_RULE_TYPE_UNIQUE = "unique";
                    
    /**
     * 质量规则类型: 范围检查 
     */
    String QUALITY_RULE_TYPE_RANGE = "range";
                    
    /**
     * 质量规则类型: 正则匹配 
     */
    String QUALITY_RULE_TYPE_REGEX = "regex";
                    
    /**
     * 质量规则类型: 自定义SQL 
     */
    String QUALITY_RULE_TYPE_CUSTOM_SQL = "custom_sql";
                    
    /**
     * 质量规则类型: 新鲜度检查 
     */
    String QUALITY_RULE_TYPE_FRESHNESS = "freshness";
                    
    /**
     * 质量规则类型: 行数检查 
     */
    String QUALITY_RULE_TYPE_VOLUME = "volume";
                    
    /**
     * 质量对象类型: 字段 
     */
    String QUALITY_ENTITY_TYPE_FIELD = "field";
                    
    /**
     * 质量对象类型: 表 
     */
    String QUALITY_ENTITY_TYPE_TABLE = "table";
                    
    /**
     * 质量对象类型: 数据库 
     */
    String QUALITY_ENTITY_TYPE_DATABASE = "database";
                    
    /**
     * 质量严重级别: 提示 
     */
    String QUALITY_SEVERITY_INFO = "INFO";
                    
    /**
     * 质量严重级别: 警告 
     */
    String QUALITY_SEVERITY_WARNING = "WARNING";
                    
    /**
     * 质量严重级别: 错误 
     */
    String QUALITY_SEVERITY_ERROR = "ERROR";
                    
    /**
     * 质量结果状态: 通过 
     */
    String QUALITY_RESULT_STATUS_PASS = "PASS";
                    
    /**
     * 质量结果状态: 失败 
     */
    String QUALITY_RESULT_STATUS_FAIL = "FAIL";
                    
    /**
     * 质量结果状态: 执行异常 
     */
    String QUALITY_RESULT_STATUS_ERROR = "ERROR";
                    
    /**
     * 质量结果状态: 跳过 
     */
    String QUALITY_RESULT_STATUS_SKIP = "SKIP";
                    
    /**
     * 检查点状态: 启用 
     */
    String CHECKPOINT_STATUS_ACTIVE = "ACTIVE";
                    
    /**
     * 检查点状态: 暂停 
     */
    String CHECKPOINT_STATUS_PAUSED = "PAUSED";
                    
    /**
     * 检查点状态: 已禁用 
     */
    String CHECKPOINT_STATUS_DISABLED = "DISABLED";
                    
    /**
     * 检查点动作类型: 存储结果 
     */
    String CHECKPOINT_ACTION_TYPE_STORE = "store";
                    
    /**
     * 检查点动作类型: Webhook投递 
     */
    String CHECKPOINT_ACTION_TYPE_WEBHOOK = "webhook";
                    
    /**
     * 检查点动作类型: 消息通知 
     */
    String CHECKPOINT_ACTION_TYPE_NOTIFY = "notify";
                    
    /**
     * 质量评分趋势方向: 改善 
     */
    String QUALITY_TREND_DIRECTION_IMPROVING = "improving";
                    
    /**
     * 质量评分趋势方向: 稳定 
     */
    String QUALITY_TREND_DIRECTION_STABLE = "stable";
                    
    /**
     * 质量评分趋势方向: 恶化 
     */
    String QUALITY_TREND_DIRECTION_DEGRADING = "degrading";
                    
    /**
     * 数据契约状态: 草稿 
     */
    String CONTRACT_STATUS_DRAFT = "DRAFT";
                    
    /**
     * 数据契约状态: 生效 
     */
    String CONTRACT_STATUS_ACTIVE = "ACTIVE";
                    
    /**
     * 数据契约状态: 已废弃 
     */
    String CONTRACT_STATUS_DEPRECATED = "DEPRECATED";
                    
    /**
     * 数据契约状态: 已退役 
     */
    String CONTRACT_STATUS_RETIRED = "RETIRED";
                    
    /**
     * 对账状态: 已匹配 
     */
    String RECONCILIATION_STATUS_MATCHED = "MATCHED";
                    
    /**
     * 对账状态: 未匹配 
     */
    String RECONCILIATION_STATUS_UNMATCHED = "UNMATCHED";
                    
    /**
     * 对账状态: 多候选 
     */
    String RECONCILIATION_STATUS_MULTIPLE = "MULTIPLE";
                    
    /**
     * 对账状态: 人工确认 
     */
    String RECONCILIATION_STATUS_MANUAL = "MANUAL";
                    
    /**
     * 匹配策略: 精确匹配 
     */
    String MATCH_STRATEGY_EXACT = "exact";
                    
    /**
     * 匹配策略: 模糊匹配 
     */
    String MATCH_STRATEGY_FUZZY = "fuzzy";
                    
    /**
     * 元数据变更事件类型: 实体创建 
     */
    String CHANGE_EVENT_TYPE_ENTITY_CREATED = "ENTITY_CREATED";
                    
    /**
     * 元数据变更事件类型: 实体更新 
     */
    String CHANGE_EVENT_TYPE_ENTITY_UPDATED = "ENTITY_UPDATED";
                    
    /**
     * 元数据变更事件类型: 实体删除 
     */
    String CHANGE_EVENT_TYPE_ENTITY_DELETED = "ENTITY_DELETED";
                    
    /**
     * 标签标注来源: 分类标签 
     */
    String TAG_LABEL_SOURCE_CLASSIFICATION = "Classification";
                    
    /**
     * 标签标注来源: 业务术语 
     */
    String TAG_LABEL_SOURCE_GLOSSARY = "Glossary";
                    
    /**
     * 标签标注类型: 手动标注 
     */
    String TAG_LABEL_TYPE_MANUAL = "Manual";
                    
    /**
     * 标签标注类型: 血缘传播 
     */
    String TAG_LABEL_TYPE_PROPAGATED = "Propagated";
                    
    /**
     * 标签标注类型: 自动识别 
     */
    String TAG_LABEL_TYPE_AUTOMATED = "Automated";
                    
    /**
     * 标签标注类型: 派生标注 
     */
    String TAG_LABEL_TYPE_DERIVED = "Derived";
                    
    /**
     * 标签标注类型: 系统生成 
     */
    String TAG_LABEL_TYPE_GENERATED = "Generated";
                    
    /**
     * 标签标注状态: 建议 
     */
    String TAG_LABEL_STATE_SUGGESTED = "Suggested";
                    
    /**
     * 标签标注状态: 已确认 
     */
    String TAG_LABEL_STATE_CONFIRMED = "Confirmed";
                    
    /**
     * 标签提供者: 系统内置 
     */
    String TAG_PROVIDER_SYSTEM = "system";
                    
    /**
     * 标签提供者: 用户自定义 
     */
    String TAG_PROVIDER_USER = "user";
                    
}
