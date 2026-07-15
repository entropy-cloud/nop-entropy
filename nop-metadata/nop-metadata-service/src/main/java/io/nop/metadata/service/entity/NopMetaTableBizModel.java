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
import io.nop.metadata.biz.INopMetaTableBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaProfilingResult;
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
 * 逻辑表 BizModel：基线 CRUD（{@link CrudBizModel}）+ 数据剖析入口（架构基线 §2.7.2 / 设计 06 §三 D3）。
 *
 * <p>剖析入口 {@link #profileTable} 是数据剖析的主入口（metaTableId 是入口键，操作对象是表，
 * 与 collectCatalog 入口风格一致）。辅助入口 {@code NopMetaProfilingRuleBizModel.executeProfilingRule}
 * 按规则定义执行，内部委托同一剖析路径。
 *
 * <p>执行机制（D3）：复用 P2-1/P2-4/P2-6 范式——BizModel action + {@code withConnection} callback + 无状态剖析器
 * （{@link MetaTableProfiler}）。
 *
 * <p>失败/不可执行路径均显式（不静默通过、不吞异常、不伪造值）：
 * <ul>
 *   <li>表不存在 → 抛 {@link #ERR_PROFILING_TABLE_NOT_FOUND}（不 NPE）</li>
 *   <li>目标表非 external（首版） → 抛 {@link #ERR_PROFILING_TABLE_NOT_EXTERNAL}</li>
 *   <li>无注册数据源 → 抛 {@link #ERR_PROFILING_NO_DATASOURCE}</li>
 *   <li>DISABLED 数据源 → 抛 {@link #ERR_PROFILING_DATASOURCE_DISABLED}</li>
 *   <li>非 jdbc 类型 → 由 {@code withConnection} 抛 UnsupportedOperationException</li>
 *   <li>单列剖析失败 → per-column try/catch 收集进 errors，不中断整表</li>
 *   <li>方言特定统计（sizeBytes/lastModified）→ null + unavailable 显式标记（不伪造）</li>
 * </ul>
 */
@BizModel("NopMetaTable")
public class NopMetaTableBizModel extends CrudBizModel<NopMetaTable> implements INopMetaTableBiz {

    private static final Logger LOG = LoggerFactory.getLogger(NopMetaTableBizModel.class);

    static final ErrorCode ERR_PROFILING_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.profiling-table-not-found",
                    "Profiling target table not found: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_PROFILING_TABLE_NOT_EXTERNAL =
            ErrorCode.define("metadata.profiling-table-not-external",
                    "Profiling target table is not external (first version supports external-only execution): "
                            + "{metaTableId} tableType={tableType}", "metaTableId", "tableType");
    static final ErrorCode ERR_PROFILING_NO_DATASOURCE =
            ErrorCode.define("metadata.profiling-no-datasource",
                    "No registered MetaDataSource for querySpace of target table: "
                            + "{metaTableId} querySpace={querySpace}", "metaTableId", "querySpace");
    static final ErrorCode ERR_PROFILING_DATASOURCE_DISABLED =
            ErrorCode.define("metadata.profiling-datasource-disabled",
                    "MetaDataSource is disabled, cannot profile table: {dataSourceId}", "dataSourceId");

    /** inline ErrorCode：profiling action 执行失败的通用包装。 */
    static final ErrorCode ERR_PROFILING_TABLE_FAILED =
            ErrorCode.define("metadata.profiling-table-failed",
                    "Profile table failed: {metaTableId} -- {error}", "metaTableId", "error");

    @Inject
    protected IMetaDataSourceConnectionService connectionService;

    /** 数据剖析器（无状态，参考 MetaCatalogCollector 收集器模式）。 */
    private final MetaTableProfiler profiler = new MetaTableProfiler();

    public NopMetaTableBizModel() {
        setEntityName(NopMetaTable.class.getName());
    }

    /**
     * 数据剖析主入口（架构基线 §2.7.2 / 设计 06 §三 D3）：对 external 表的列做统计分析。
     *
     * <p>解析路径：metaTableId → NopMetaTable(external) → querySpace → NopMetaDataSource →
     * {@code withConnection} callback → 剖析器逐列统计 → 追加一行 NopMetaProfilingResult（snapshotTime=now）。
     *
     * @param metaTableId   目标逻辑表 ID（须 external 类型）
     * @param schemaPattern 可选 schema 限定（null/空串表示依赖连接默认 schema）
     * @param columns       可选，要剖析的列名（逗号分隔，null/空=所有列，运行时由 DatabaseMetaData.getColumns 解析）
     * @param context       服务上下文
     * @return {@code {profilingResultId, columnCount, unavailable:[...], errors:[...]}}
     */
    @BizMutation
    public Map<String, Object> profileTable(@Name("metaTableId") String metaTableId,
                                             @Optional @Name("schemaPattern") String schemaPattern,
                                             @Optional @Name("columns") String columns,
                                             IServiceContext context) {
        NopMetaTable table = resolveExternalTableOrThrow(metaTableId);
        NopMetaDataSource dataSource = resolveDataSourceOrThrow(table);

        // 剖析在 withConnection callback 内执行；callback 结束自动释放外部连接（本方法不自建连接）
        final ProfilingSnapshot[] holder = new ProfilingSnapshot[1];
        connectionService.withConnection(dataSource.getDatasourceType(), dataSource.getConnectionConfig(),
                (Connection conn, DatabaseMetaData metaData) -> {
                    holder[0] = profiler.profile(conn, metaData, schemaPattern,
                            table.getTableName(), columns, safeProductName(metaData));
                });

        ProfilingSnapshot snapshot = holder[0];
        // 按规则定义执行的剖析可挂规则 id；profileTable 入口无规则，留空（结果行的 profilingRuleId 可空，便于无规则直接剖析）
        NopMetaProfilingResult row = appendProfilingResult(null, metaTableId, snapshot);
        return buildResultMap(row, snapshot);
    }

    // ============================================================
    // helpers
    // ============================================================

    /** 解析目标表：metaTableId → NopMetaTable；不存在/非 external 显式失败。 */
    NopMetaTable resolveExternalTableOrThrow(String metaTableId) {
        IEntityDao<NopMetaTable> tableDao = dao();
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_PROFILING_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        if (!_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(table.getTableType())) {
            throw new NopException(ERR_PROFILING_TABLE_NOT_EXTERNAL)
                    .param("metaTableId", metaTableId)
                    .param("tableType", String.valueOf(table.getTableType()));
        }
        return table;
    }

    /** 解析目标表对应数据源：table.querySpace → NopMetaDataSource；不存在/DISABLED 显式失败。 */
    NopMetaDataSource resolveDataSourceOrThrow(NopMetaTable table) {
        IEntityDao<NopMetaDataSource> dsDao = daoFor(NopMetaDataSource.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaDataSource.PROP_NAME_querySpace, table.getQuerySpace()));
        NopMetaDataSource dataSource = dsDao.findFirstByQuery(q);
        if (dataSource == null) {
            throw new NopException(ERR_PROFILING_NO_DATASOURCE)
                    .param("metaTableId", table.getMetaTableId())
                    .param("querySpace", table.getQuerySpace());
        }
        if (_NopMetadataCoreConstants.DATASOURCE_STATUS_DISABLED.equals(dataSource.getStatus())) {
            throw new NopException(ERR_PROFILING_DATASOURCE_DISABLED)
                    .param("dataSourceId", dataSource.getDataSourceId());
        }
        return dataSource;
    }

    /**
     * 将剖析快照追加为一行新的 NopMetaProfilingResult（时序语义：snapshotTime=now，不覆盖旧行）。
     * tableStats/columnStats 用 JsonTool 序列化（mediumtext 列承载，已含 unavailable 标记）。
     */
    NopMetaProfilingResult appendProfilingResult(String profilingRuleId, String metaTableId,
                                                 ProfilingSnapshot snapshot) {
        IEntityDao<NopMetaProfilingResult> resultDao = daoFor(NopMetaProfilingResult.class);
        NopMetaProfilingResult row = resultDao.newEntity();
        if (profilingRuleId != null) {
            row.setProfilingRuleId(profilingRuleId);
        }
        row.setMetaTableId(metaTableId);
        row.setSnapshotTime(new Timestamp(System.currentTimeMillis()));
        row.setTableStats(JsonTool.stringify(snapshot.toTableStatsMap()));
        row.setColumnStats(JsonTool.stringify(snapshot.toColumnStatsList()));
        resultDao.saveEntity(row);
        return row;
    }

    /** 构建返回 Map（profilingResultId + 列数 + 表级不可用 + 列级 errors）。 */
    static Map<String, Object> buildResultMap(NopMetaProfilingResult row, ProfilingSnapshot snapshot) {
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
