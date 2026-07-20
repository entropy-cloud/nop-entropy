/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.exceptions.ErrorCode;

/**
 * nop-metadata 模块统一 ErrorCode 常量集（plan 2026-07-19-1250-3 Phase 2 维度09-01..09-04 / 17-02）。
 *
 * <p>本接口集中声明跨多个文件使用的 ErrorCode 常量，避免：
 * <ul>
 *   <li>同一错误码字符串在多文件独立 define（维度09-03）</li>
 *   <li>inline {@code throw new NopException(ErrorCode.define(...))} 用法（维度09-04）</li>
 *   <li>ErrorCode 散落在 40+ 文件顶部（维度09-02）</li>
 * </ul>
 *
 * <p>命名规范：{@code nop.err.metadata.<子域>.<错误>}（plan 维度09-01）。子域分组：
 * aggregation / join / query-filter / datasource / tableref / lineage / quality /
 * checkpoint / profiling / reconciliation / contract / event / module。
 *
 * <p>本 phase 引入主入口常量；其余 178+ 处 ErrorCode.define 调用按子域渐进迁移（plan Phase 2
 * follow-up）。已迁移的高频共享 ErrorCode（如 ERR_DATASOURCE_NOT_FOUND 跨 NopMetaDataSourceBizModel
 * 与 NopMetaQualityRuleBizModel 双重定义）放在本接口，消除重复定义。
 *
 * <p>{@code ARG_*} 常量（维度09-01 ARG 引入）：用于 {@code .param(ARG_FOO, value)} 调用，
 * 替代魔法字符串字面量。
 */
public interface NopMetadataErrors {

    // ===== ARG 参数名常量（维度09-01 引入 ARG_*）=====

    String ARG_META_TABLE_ID = "metaTableId";
    String ARG_DATA_SOURCE_ID = "dataSourceId";
    String ARG_DATASOURCE_TYPE = "datasourceType";
    String ARG_JOIN_ID = "joinId";
    String ARG_CONFIG_ID = "configId";
    String ARG_CHECKPOINT_ID = "checkpointId";
    String ARG_QUALITY_RULE_ID = "qualityRuleId";
    String ARG_ENTITY_NAME = "entityName";
    String ARG_ENTITY_ID = "entityId";
    String ARG_BASE_ENTITY_ID = "baseEntityId";
    String ARG_META_MODULE_ID = "metaModuleId";
    String ARG_QUERY_SPACE = "querySpace";
    String ARG_TABLE_TYPE = "tableType";
    String ARG_TABLE_NAME = "tableName";
    String ARG_COLUMN_NAME = "columnName";
    String ARG_DATABASE_PRODUCT_NAME = "databaseProductName";
    String ARG_ERROR = "error";
    String ARG_PATH = "path";
    String ARG_CONTRACT_ID = "contractId";
    String ARG_CRON = "cron";
    String ARG_STATUS = "status";
    String ARG_SQL = "sql";
    String ARG_IDENTIFIER = "identifier";
    String ARG_OP = "op";
    String ARG_NAME = "name";

    // ===== 跨文件去重 ErrorCode（维度09-03）=====

    /** 数据源未找到（原 metadata.datasource-not-found 在 NopMetaDataSourceBizModel 与 NopMetaQualityRuleBizModel 双重 define）。 */
    ErrorCode ERR_DATASOURCE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.datasource-not-found",
                    "MetaDataSource not found: {dataSourceId}", ARG_DATA_SOURCE_ID);

    /** join 端点表类型不允许（跨 MetaJoinExecutor 与 NopMetaTableJoinBizModel 共享语义）。 */
    ErrorCode ERR_JOIN_TABLE_TYPE_NOT_ALLOWED =
            ErrorCode.define("nop.err.metadata.join-table-type-not-allowed",
                    "Join endpoint table type is not allowed for this join: "
                            + "joinId={joinId} metaTableId={metaTableId} side={side} tableId={tableId} tableType={tableType}",
                    ARG_JOIN_ID, ARG_META_TABLE_ID, "side", "tableId", ARG_TABLE_TYPE);

    // ===== 模块异常辅助 ErrorCode（维度09-05/09-06/09-07）=====

    /** 数据源类型不支持（替代 ExternalTableStructureReader 的 UnsupportedOperationException）。 */
    ErrorCode ERR_DATASOURCE_TYPE_NOT_SUPPORTED =
            ErrorCode.define("nop.err.metadata.datasource-type-not-supported",
                    "MetaDataSource type is not supported for external table structure reading: {datasourceType}",
                    ARG_DATASOURCE_TYPE);

    /** manifest 构建失败（替代 MetaManifestBuilder 的 IllegalArgumentException）。 */
    ErrorCode ERR_MANIFEST_BUILD_FAILED =
            ErrorCode.define("nop.err.metadata.manifest-build-failed",
                    "MetaManifest build failed: {metaModuleId} -- {error}",
                    ARG_META_MODULE_ID, ARG_ERROR);

    /** ORM 资源未找到（替代 MetaManifestBuilder 部分路径的 IllegalArgumentException）。 */
    ErrorCode ERR_ORM_RESOURCE_NOT_FOUND =
            ErrorCode.define("nop.err.metadata.orm-resource-not-found",
                    "ORM resource not found: {path}", ARG_PATH);

    /** ORM 资源读取失败（替代 MetaManifestBuilder 部分路径的 IllegalArgumentException）。 */
    ErrorCode ERR_ORM_RESOURCE_READ_FAILED =
            ErrorCode.define("nop.err.metadata.orm-resource-read-failed",
                    "ORM resource read failed: {path} -- {error}", ARG_PATH, ARG_ERROR);

    /** 解析属性失败（LocalReconciliationProcessor.parseProperties 静默吞异常修复后抛出）。 */
    ErrorCode ERR_RECON_PARSE_PROPERTIES_FAILED =
            ErrorCode.define("nop.err.metadata.recon-parse-properties-failed",
                    "Reconciliation parseProperties failed: {error}", ARG_ERROR);

    /** expectPassWhen 表达式非法（plan Phase 5 维度 AR-11）。 */
    ErrorCode ERR_QUALITY_EXPECT_PASS_WHEN_INVALID =
            ErrorCode.define("nop.err.metadata.quality-expect-pass-when-invalid",
                    "Quality rule expectPassWhen expression is invalid: {qualityRuleId} expr={expr}",
                    ARG_QUALITY_RULE_ID, "expr");

    /** 字段序列化失败（plan Phase 1 DTO 序列化异常路径，无静默跳过）。 */
    ErrorCode ERR_DTO_SERIALIZE_FAILED =
            ErrorCode.define("nop.err.metadata.dto-serialize-failed",
                    "DTO serialize failed: {entityType} -- {error}", "entityType", ARG_ERROR);
}
