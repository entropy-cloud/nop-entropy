package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaProfilingRuleBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
import io.nop.metadata.dao.entity.NopMetaProfilingRule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionService;
import io.nop.metadata.service.profiling.MetaTableProfiler;
import io.nop.metadata.service.profiling.ProfilingColumnStats;
import io.nop.metadata.service.profiling.ProfilingSnapshot;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据剖析规则 BizModel：基线 CRUD（{@link CrudBizModel}）+ 按规则执行剖析（架构基线 §2.7.2 / 设计 06 §三 D3）。
 *
 * <p>剖析主入口是 {@code NopMetaTableBizModel.profileTable}（metaTableId 为键）。本类提供辅助入口
 * {@link #executeProfilingRule}：按 {@link NopMetaProfilingRule} 定义的 columns/stats 执行剖析，
 * 委托同一无状态剖析路径（{@link MetaTableProfiler}），使 ProfilingRule 实体可运行、非空壳。
 *
 * <p>物理解析 + 失败/不可执行路径显式化自包含（与 {@code NopMetaQualityRuleBizModel} 同模式）：
 * 表不存在/非 external/无数据源/DISABLED/非 jdbc 显式失败，不静默通过。
 */
@BizModel("NopMetaProfilingRule")
public class NopMetaProfilingRuleBizModel extends CrudBizModel<NopMetaProfilingRule> implements INopMetaProfilingRuleBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaProfilingRuleBizModel.class);

    static final ErrorCode ERR_PROFILING_RULE_NOT_FOUND =
            ErrorCode.define("metadata.profiling-rule-not-found",
                    "Profiling rule not found: {profilingRuleId}", "profilingRuleId");

    @Inject
    protected IMetaDataSourceConnectionService connectionService;

    /** 数据剖析器（无状态，与 profileTable 共用同一剖析路径）。 */
    private final MetaTableProfiler profiler = new MetaTableProfiler();

    public NopMetaProfilingRuleBizModel() {
        setEntityName(NopMetaProfilingRule.class.getName());
    }

    /**
     * 按剖析规则定义执行（架构基线 §2.7.2 D3）：加载规则 → 解析目标 external 表 + 数据源 →
     * {@code withConnection} callback → 剖析器按规则 columns/stats 统计 → 追加一行 NopMetaProfilingResult。
     *
     * @param profilingRuleId 规则 ID
     * @param schemaPattern   可选 schema 限定（null/空串表示依赖连接默认 schema）
     * @param context         服务上下文
     * @return {@code {profilingResultId, columnCount, unavailable:[...], errors:[...]}}
     */
    @BizMutation
    public Map<String, Object> executeProfilingRule(@Name("profilingRuleId") String profilingRuleId,
                                                     @Optional @Name("schemaPattern") String schemaPattern,
                                                     IServiceContext context) {
        NopMetaProfilingRule rule = dao().getEntityById(profilingRuleId);
        if (rule == null) {
            throw new NopException(ERR_PROFILING_RULE_NOT_FOUND).param("profilingRuleId", profilingRuleId);
        }

        NopMetaTable table = resolveExternalTableOrThrow(rule);
        NopMetaDataSource dataSource = resolveDataSourceOrThrow(rule, table);

        // 规则定义的 columns 作为列过滤（stats 指标首版全量收集，规则仅记录意图，不裁剪以保证剖析完整性）
        String columns = rule.getColumns();

        final ProfilingSnapshot[] holder = new ProfilingSnapshot[1];
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    holder[0] = profiler.profile(conn, metaData, schemaPattern,
                            table.getTableName(), columns, safeProductName(metaData));
                });

        ProfilingSnapshot snapshot = holder[0];
        NopMetaProfilingResult row = appendProfilingResult(
                rule.getProfilingRuleId(), table.getMetaTableId(), snapshot);
        return buildResultMap(row, snapshot);
    }

    // ============================================================
    // helpers（自包含，与 NopMetaQualityRuleBizModel 同模式）
    // ============================================================

    /** 解析规则目标表：rule.tableId → NopMetaTable；不存在/非 external 显式失败。 */
    private NopMetaTable resolveExternalTableOrThrow(NopMetaProfilingRule rule) {
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(rule.getTableId());
        if (table == null) {
            throw new NopException(NopMetaTableBizModel.ERR_PROFILING_TABLE_NOT_FOUND)
                    .param("metaTableId", rule.getTableId());
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(table.getTableType())) {
            throw new NopException(NopMetaTableBizModel.ERR_PROFILING_TABLE_NOT_EXTERNAL)
                    .param("metaTableId", rule.getTableId())
                    .param("tableType", String.valueOf(table.getTableType()));
        }
        return table;
    }

    /** 解析目标表对应数据源：table.querySpace → NopMetaDataSource；不存在/DISABLED 显式失败。 */
    private NopMetaDataSource resolveDataSourceOrThrow(NopMetaProfilingRule rule, NopMetaTable table) {
        IEntityDao<NopMetaDataSource> dsDao = daoFor(NopMetaDataSource.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, table.getQuerySpace()));
        NopMetaDataSource dataSource = dsDao.findFirstByQuery(q);
        if (dataSource == null) {
            throw new NopException(NopMetaTableBizModel.ERR_PROFILING_NO_DATASOURCE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("querySpace", table.getQuerySpace());
        }
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(dataSource.getStatus())) {
            throw new NopException(NopMetaTableBizModel.ERR_PROFILING_DATASOURCE_DISABLED)
                    .param("dataSourceId", dataSource.getDataSourceId());
        }
        return dataSource;
    }

    /** 追加一行 NopMetaProfilingResult（时序语义：snapshotTime=now，不覆盖）。 */
    private NopMetaProfilingResult appendProfilingResult(String profilingRuleId, String metaTableId,
                                                         ProfilingSnapshot snapshot) {
        IEntityDao<NopMetaProfilingResult> resultDao = daoFor(NopMetaProfilingResult.class);
        NopMetaProfilingResult row = resultDao.newEntity();
        row.setProfilingRuleId(profilingRuleId);
        row.setMetaTableId(metaTableId);
        row.setSnapshotTime(new Timestamp(System.currentTimeMillis()));
        row.setTableStats(JsonTool.stringify(snapshot.toTableStatsMap()));
        row.setColumnStats(JsonTool.stringify(snapshot.toColumnStatsList()));
        resultDao.saveEntity(row);
        return row;
    }

    /** 构建返回 Map（profilingResultId + 列数 + 表级不可用 + 列级 errors）。 */
    private static Map<String, Object> buildResultMap(NopMetaProfilingResult row, ProfilingSnapshot snapshot) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("profilingResultId", row.getProfilingResultId());
        m.put("columnCount", snapshot.getColumnStats().size());
        m.put("unavailable", snapshot.getTableUnavailable());
        List<String> columnUnavailable = new ArrayList<>();
        for (ProfilingColumnStats cs : snapshot.getColumnStats()) {
            columnUnavailable.addAll(cs.getUnavailable());
        }
        m.put("columnUnavailable", columnUnavailable);
        m.put("errors", snapshot.getErrors());
        return m;
    }

    private static String safeProductName(DatabaseMetaData metaData) {
        try {
            return metaData.getDatabaseProductName();
        } catch (SQLException e) {
            LOG.warn("getDatabaseProductName failed, product name will be absent from tableStats", e);
            return null;
        }
    }
}
