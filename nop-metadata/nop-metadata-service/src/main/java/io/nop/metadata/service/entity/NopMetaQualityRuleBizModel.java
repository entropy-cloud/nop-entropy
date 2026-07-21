/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaQualityRuleBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import io.nop.metadata.service.datasource.MetaDataSourceResolver;
import io.nop.metadata.service.quality.MetaQualityRuleExecutor;
import io.nop.metadata.service.quality.QualityAlertWorkflowService;
import io.nop.metadata.service.quality.QualityResultWriter;
import io.nop.metadata.service.quality.QualityRuleJudgment;
import io.nop.metadata.service.tableref.MetaTableReferenceResolver;
import io.nop.metadata.service.tableref.TableReference;
import io.nop.metadata.service.tableref.TableReferenceExecutor;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 质量规则 BizModel：基线 CRUD（{@link CrudBizModel}）+ 质量规则执行引擎（架构基线 §2.7.1）。
 *
 * <p>执行机制（D2）：BizModel action + P2-1 {@code withConnection} callback（不选 nop-batch）。
 *
 * <p>执行范围（D1）：首版仅 external 类型 NopMetaTable 上挂载的规则（entityType=table 或 field，
 * field 规则 entityId 指向 external NopMetaTable.metaTableId，物理列名取自 params.column）。
 * entity/sql 类型表执行 deferred；entityType=database 首版 SKIP（带 details 标记）。
 *
 * <p>失败/不可执行路径均显式（不静默通过、不吞异常、不伪造值）：
 * <ul>
 *   <li>规则不存在 → 抛 {@link #NopMetadataErrors.ERR_QUALITY_RULE_NOT_FOUND}（不 NPE）</li>
 *   <li>目标表不存在 → 抛 {@link #NopMetadataErrors.ERR_QUALITY_TABLE_NOT_FOUND}</li>
 *   <li>目标表非 external（首版） → 抛 {@link #NopMetadataErrors.ERR_QUALITY_TABLE_NOT_EXTERNAL}</li>
 *   <li>无注册数据源 → 抛 {@link #NopMetadataErrors.ERR_QUALITY_NO_DATASOURCE}</li>
 *   <li>DISABLED 数据源 → 抛 {@link #NopMetadataErrors.ERR_QUALITY_DATASOURCE_DISABLED}</li>
 *   <li>非 jdbc 类型 → 由 {@code withConnection} 抛 NopException</li>
 *   <li>缺 timestampColumn(freshness)/custom_sql 不返回单值 → 写 ERROR 结果行</li>
 *   <li>entityType=database / regex 方言不支持 REGEXP → 写 SKIP 结果行（带 details 标记）</li>
 * </ul>
 */
@BizModel("NopMetaQualityRule")
public class NopMetaQualityRuleBizModel extends CrudBizModel<NopMetaQualityRule> implements INopMetaQualityRuleBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaQualityRuleBizModel.class);


    @Inject
    protected IMetaDataSourceConnectionProcessor connectionService;

    /** querySpace→数据源 解析共享组件。 */
    private final MetaDataSourceResolver dataSourceResolver = new MetaDataSourceResolver();

    /** 共享 table-reference 解析器（架构基线 §4.4.3 D3）。 */
    private final MetaTableReferenceResolver tableRefResolver = new MetaTableReferenceResolver();

    /** 质量规则执行器（无状态，参考 MetaCatalogCollector 收集器模式）。 */
    private final MetaQualityRuleExecutor executor = new MetaQualityRuleExecutor();

    /** 结果写入共享 helper（§2.7.3 D3：与 checkpoint executor 共用，避免复制逻辑）。 */
    private final QualityResultWriter resultWriter = new QualityResultWriter();

    @Inject
    protected QualityAlertWorkflowService alertWorkflowService;

    /** 按 table-reference 形态分派 Connection 获取（§4.4.3 D1/D2）。延迟初始化（需 orm()）。 */
    private TableReferenceExecutor tableRefExecutor;

    public NopMetaQualityRuleBizModel() {
        setEntityName(NopMetaQualityRule.class.getName());
    }

    // ============================================================
    // 单规则执行
    // ============================================================

    /**
     * 执行单条质量规则（架构基线 §2.7.1 D2 + §4.4.3 D1-D5）。
     *
     * <p>解析路径（D3）：rule.entityId → NopMetaTable → {@link MetaTableReferenceResolver} → {@link TableReference}
     * → {@link TableReferenceExecutor} 按 ref 形态分派 Connection → 执行器判定 → 追加一行 NopMetaQualityResult。
     *
     * <p>覆盖范围：external/entity/sql 任意 tableType 的逻辑表上挂载的 table/field 级规则均可执行（D4 能力边界）。
     * entityType=database 仍 SKIP（§2.7.1 D1）。
     *
     * @param qualityRuleId 规则 ID
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema）
     * @return {@code {qualityResultId, status, actualValue, expectedValue, message, details}}
     */
    @BizMutation
    public Map<String, Object> executeQualityRule(@Name("qualityRuleId") String qualityRuleId,
                                                  @Optional @Name("schemaPattern") String schemaPattern,
                                                  IServiceContext context) {
        NopMetaQualityRule rule = dao().getEntityById(qualityRuleId);
        if (rule == null) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_RULE_NOT_FOUND).param("qualityRuleId", qualityRuleId);
        }

        // entityType=database 首版 SKIP（§2.7.1 D1）——不解析表/数据源，直接写 SKIP 结果行
        if (_NopMetadataCoreConstants.QUALITY_ENTITY_TYPE_DATABASE.equals(rule.getEntityType())) {
            QualityRuleJudgment skip = new QualityRuleJudgment();
            skip.setStatus(_NopMetadataCoreConstants.QUALITY_RESULT_STATUS_SKIP);
            skip.setMessage("entityType=database not supported in first version (external-table-only execution)");
            skip.getDetails().put("reason", "database-not-supported-first-version");
            skip.getDetails().put("ruleType", rule.getRuleType());
            skip.getDetails().put("entityType", rule.getEntityType());
            NopMetaQualityResult row = appendQualityResult(rule.getQualityRuleId(), skip);
            return buildSingleResultMap(row, skip);
        }

        // 解析目标表（任意 tableType）+ table-reference
        NopMetaTable table = resolveTargetTableOrThrow(rule);
        TableReference ref = tableRefResolver.resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm());

        // plan 0852-3 Phase 3: 默认 schema 解析在 BizModel 层（持有 NopMetaTable）
        // 未显式传 schemaPattern 且 table.schema 非空 → 默认取 table.schema（持久化一次、多次执行无需重传）
        String effectiveSchema = resolveDefaultSchema(schemaPattern, table);

        // 按 ref 形态分派 Connection 获取并执行
        QualityRuleJudgment judgment = ensureTableRefExecutor().execute(ref,
                (conn, metaData, productName) -> executor.judge(conn, ref, effectiveSchema,
                        rule.getRuleType(), rule.getEntityType(),
                        rule.getParams(), rule.getSqlExpression(),
                        rule.getThreshold(), productName));

        NopMetaQualityResult row = appendQualityResult(rule.getQualityRuleId(), judgment);

        // 触发告警工作流：FAIL + severity=ERROR
        if ("FAIL".equals(judgment.getStatus())
                && _NopMetadataCoreConstants.QUALITY_SEVERITY_ERROR.equals(rule.getSeverity())) {
            try {
                alertWorkflowService.createAlertWorkflow(row);
            } catch (Exception e) {
                LOG.error("Failed to create alert workflow for quality rule: {}", rule.getQualityRuleId(), e);
            }
        }

        return buildSingleResultMap(row, judgment);
    }

    // ============================================================
    // 批量执行（按数据源）
    // ============================================================

    /**
     * 批量执行某数据源 querySpace 下 external 表上挂载的质量规则（架构基线 §2.7.1 D2）。
     *
     * <p>与 {@code NopMetaDataSourceBizModel.collectCatalog} 同入口（dataSourceId）、同 callback 模式、
     * 同 per-rule 失败隔离（try/catch + flushSession/clearSession）。
     *
     * @param dataSourceId  目标数据源 ID
     * @param schemaPattern 可选 schema 限定
     * @return {@code {executedCount: int, results: [...], errors: [{qualityRuleId, error}, ...]}}
     */
    @BizMutation
    public Map<String, Object> executeQualityRulesForDataSource(@Name("dataSourceId") String dataSourceId,
                                                                @Optional @Name("schemaPattern") String schemaPattern,
                                                                IServiceContext context) {
        NopMetaDataSource dataSource = daoFor(NopMetaDataSource.class).getEntityById(dataSourceId);
        if (dataSource == null) {
            throw new NopException(NopMetadataErrors.ERR_DATASOURCE_NOT_FOUND).param("dataSourceId", dataSourceId);
        }
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(dataSource.getStatus())) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_DATASOURCE_DISABLED).param("dataSourceId", dataSourceId);
        }

        // 该 querySpace 下 external 表
        List<NopMetaTable> externalTables = findExternalTables(dataSource.getQuerySpace());
        if (externalTables.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("executedCount", 0);
            empty.put("results", new ArrayList<>());
            empty.put("errors", new ArrayList<>());
            return empty;
        }

        // 表 id → 物理表名 + schema（callback 内按规则 entityId 解析；plan 0852-3 Phase 3 多 schema 批量逐表）
        Map<String, String> tableIdToName = new HashMap<>();
        Map<String, NopMetaTable> tableIdToEntity = new HashMap<>();
        for (NopMetaTable t : externalTables) {
            tableIdToName.put(t.getMetaTableId(), t.getTableName());
            tableIdToEntity.put(t.getMetaTableId(), t);
        }
        List<String> tableIds = new ArrayList<>(tableIdToName.keySet());

        // 找挂载在这些表上的规则（entityId ∈ tableIds，覆盖 entityType=table 和 field；database 规则 entityId 不匹配）
        IEntityDao<NopMetaQualityRule> ruleDao = dao();
        QueryBean ruleQuery = new QueryBean();
        ruleQuery.addFilter(FilterBeans.in(NopMetaQualityRule.PROP_NAME_entityId, tableIds));
        List<NopMetaQualityRule> rules = ruleDao.findAllByQuery(ruleQuery);

        AtomicInteger executedCount = new AtomicInteger(0);
        List<Map<String, Object>> results = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        if (rules.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("executedCount", 0);
            empty.put("results", results);
            empty.put("errors", errors);
            return empty;
        }

        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    String productName = safeProductName(metaData);
                    for (NopMetaQualityRule rule : rules) {
                        try {
                            String tableName = tableIdToName.get(rule.getEntityId());
                            if (tableName == null) {
                                // 规则 entityId 不在该批次 external 表范围内（理论不应发生，防御性显式失败）
                                throw new NopException(NopMetadataErrors.ERR_QUALITY_TABLE_NOT_FOUND)
                                        .param("qualityRuleId", rule.getQualityRuleId())
                                        .param("entityId", rule.getEntityId());
                            }
                            // plan 0852-3 Phase 3: 批量入口逐表默认 schema 解析（各表 schema 可能不同）
                            NopMetaTable targetTable = tableIdToEntity.get(rule.getEntityId());
                            String effectiveSchema = resolveDefaultSchema(schemaPattern, targetTable);
                            QualityRuleJudgment judgment = executor.judge(conn,
                                    new TableReference(TableReference.Kind.EXTERNAL, rule.getEntityId(),
                                            tableName, null, dataSource, null, null, null),
                                    effectiveSchema,
                                    rule.getRuleType(), rule.getEntityType(),
                                    rule.getParams(), rule.getSqlExpression(),
                                    rule.getThreshold(), productName);
                            NopMetaQualityResult row = appendQualityResult(rule.getQualityRuleId(), judgment);
                            orm().flushSession();
                            executedCount.incrementAndGet();
                            results.add(buildSingleResultMap(row, judgment));
                        } catch (Exception e) {
                            LOG.error("executeQualityRulesForDataSource failed for rule: {}",
                                    rule.getQualityRuleId(), e);
                            Map<String, Object> err = new LinkedHashMap<>();
                            err.put("qualityRuleId", rule.getQualityRuleId());
                            err.put("ruleName", rule.getRuleName());
                            err.put("error", toErrorMessage(e));
                            errors.add(err);
                            // 隔离失败：清理未刷出的脏实体，不影响已 flush 的规则与后续规则
                            orm().clearSession();
                        }
                    }
                });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executedCount", executedCount.get());
        result.put("results", results);
        result.put("errors", errors);
        return result;
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 解析规则目标表：entityId → NopMetaTable；不存在显式失败（任意 tableType，§4.4.3 D4）。 */
    private NopMetaTable resolveTargetTableOrThrow(NopMetaQualityRule rule) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(rule.getEntityId());
        if (table == null) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_TABLE_NOT_FOUND)
                    .param("qualityRuleId", rule.getQualityRuleId())
                    .param("entityId", rule.getEntityId());
        }
        return table;
    }

    /** 延迟初始化 TableReferenceExecutor（需 orm()，构造时 orm 不可用）。 */
    private TableReferenceExecutor ensureTableRefExecutor() {
        if (tableRefExecutor == null) {
            tableRefExecutor = new TableReferenceExecutor(connectionService, orm());
        }
        return tableRefExecutor;
    }

    /**
     * 默认 schema 解析（plan 0852-3 Phase 3）：未显式传 schemaPattern（null/空/纯空白）且
     * {@code table.schema} 非空 → 默认取 {@code table.schema}；否则维持入参（可能为 null=不过滤）。
     * 与 {@code NopMetaDataSourceBizModel.resolveDefaultSchema} 同语义。
     */
    private static String resolveDefaultSchema(String schemaPattern, NopMetaTable table) {
        if (schemaPattern != null && !schemaPattern.trim().isEmpty()) {
            return schemaPattern;
        }
        return table.getMetaSchema();
    }

    /** 解析目标表对应数据源：table.querySpace → NopMetaDataSource；不存在/DISABLED 显式失败。 */
    private NopMetaDataSource resolveDataSourceOrThrow(NopMetaQualityRule rule, NopMetaTable table) {
        IEntityDao<NopMetaDataSource> dsDao = daoFor(NopMetaDataSource.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, table.getQuerySpace()));
        NopMetaDataSource dataSource = dsDao.findFirstByQuery(q);
        if (dataSource == null) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_NO_DATASOURCE)
                    .param("qualityRuleId", rule.getQualityRuleId())
                    .param("querySpace", table.getQuerySpace());
        }
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(dataSource.getStatus())) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_DATASOURCE_DISABLED)
                    .param("dataSourceId", dataSource.getDataSourceId());
        }
        return dataSource;
    }

    /** 查找该 querySpace 下所有 external 类型逻辑表（按 tableType=external 限定）。 */
    private List<NopMetaTable> findExternalTables(String querySpace) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_querySpace, querySpace));
        query.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableType,
                _NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL));
        return tableDao.findAllByQuery(query);
    }

    /**
     * 将单规则判定结果追加为一行新的 NopMetaQualityResult（时序语义：executeTime=now，不覆盖旧行）。
     *
     * <p>委托共享 {@link QualityResultWriter}（§2.7.3 D3），与检查点编排路径共用同一写入逻辑，不复制。
     */
    private NopMetaQualityResult appendQualityResult(String qualityRuleId, QualityRuleJudgment judgment) {
        return resultWriter.append(daoFor(NopMetaQualityResult.class), qualityRuleId, judgment);
    }

    private static Map<String, Object> buildSingleResultMap(NopMetaQualityResult row, QualityRuleJudgment j) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("qualityResultId", row.getQualityResultId());
        m.put("status", j.getStatus());
        m.put("actualValue", j.getActualValue());
        m.put("expectedValue", j.getExpectedValue());
        m.put("message", j.getMessage());
        m.put("details", j.getDetails());
        return m;
    }

    /**
     * 根据规则 ID 重新执行判定（用于工作流 re-judge 场景）。
     * 从 DB 加载规则全字段，重建执行上下文，返回判定结果。
     */
    @BizQuery
    public Map<String, Object> judgeByRuleId(@Name("ruleId") String ruleId, IServiceContext context) {
        NopMetaQualityRule rule = dao().getEntityById(ruleId);
        if (rule == null) {
            throw new NopException(NopMetadataErrors.ERR_QUALITY_RULE_NOT_FOUND).param("qualityRuleId", ruleId);
        }

        NopMetaTable table = resolveTargetTableOrThrow(rule);
        TableReference ref = tableRefResolver.resolve(table,
                daoFor(NopMetaDataSource.class), daoFor(NopMetaEntity.class),
                daoFor(NopMetaEntityField.class), orm());

        String effectiveSchema = resolveDefaultSchema(null, table);

        QualityRuleJudgment judgment = ensureTableRefExecutor().execute(ref,
                (conn, metaData, productName) -> executor.judge(conn, ref, effectiveSchema,
                        rule.getRuleType(), rule.getEntityType(),
                        rule.getParams(), rule.getSqlExpression(),
                        rule.getThreshold(), productName));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", judgment.getStatus());
        m.put("actualValue", judgment.getActualValue());
        m.put("expectedValue", judgment.getExpectedValue());
        m.put("message", judgment.getMessage());
        m.put("details", judgment.getDetails());
        return m;
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOG.warn("getDatabaseProductName failed, product name will be absent from details", e);
            return null;
        }
    }

    private static String toErrorMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getName();
    }
}
