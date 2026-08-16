package io.nop.datav.service.query;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.IDataRow;

import io.nop.datav.biz.PanelComponentMeta;
import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.component.IPanelComponent;
import io.nop.datav.service.component.PanelComponentRegistry;
import io.nop.datav.service.component.PanelTypeMapping;

import io.nop.report.dao.entity.NopReportDataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ARG_PANEL_ID;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DATASET_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DATASET_REF_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_QUERY_FAILED;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_UNSUPPORTED_DATASET_TYPE;

/**
 * 面板数据绑定管线。端到端编排：面板 → 数据集引用解析 → 参数求值 → nop-report 数据集 SQL 查询执行 → 结果回传。
 *
 * <p>参见 {@code ai-dev/design/nop-datav/runtime-design.md} §2 数据绑定管线。</p>
 *
 * <p>由 {@code NopDatavPanelBizModel.getPanelData} / {@code refreshPanel} 调用。两种入口共用本管线，避免查询逻辑重复。</p>
 */
public class PanelDataBinder {

    public static final String DS_TYPE_SQL = "sql";

    private final IDaoProvider daoProvider;
    private final IJdbcTemplate jdbcTemplate;
    private final PanelComponentRegistry componentRegistry;

    public PanelDataBinder(IDaoProvider daoProvider, IJdbcTemplate jdbcTemplate) {
        this(daoProvider, jdbcTemplate, PanelComponentRegistry.getInstance());
    }

    public PanelDataBinder(IDaoProvider daoProvider, IJdbcTemplate jdbcTemplate,
                           PanelComponentRegistry componentRegistry) {
        this.daoProvider = daoProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.componentRegistry = componentRegistry;
    }

    /**
     * 查询面板数据。
     *
     * @param panelId       面板 ID（非空）
     * @param panel         已加载的面板实体（若为 null，将从 DAO 按 panelId 加载；找不到则抛 PANEL_NOT_FOUND）
     * @param requestParams API 请求参数 Map（允许 null）
     * @return 面板数据结果
     */
    public PanelDataResult queryPanelData(String panelId, NopDatavPanel panel,
                                          Map<String, Object> requestParams) {
        return queryPanelData(panelId, panel, requestParams, null);
    }

    /**
     * 查询面板数据（可选行数上限，用于导出防 OOM）。
     *
     * <p>当 {@code rowLimit} 非空时，经平台 {@code LongRangeBean} 在数据集层限定最多取 {@code rowLimit} 行
     * （跨方言，由 {@link IJdbcTemplate} 经 dialect paging 实现，不手写 LIMIT SQL）。
     * 导出路径用此重载传入 {@code maxRows}，取数阶段即防 OOM（非取数后校验）。</p>
     *
     * @param rowLimit 最多取的行数；null 表示不限制（运行时 getPanelData 路径）
     */
    public PanelDataResult queryPanelData(String panelId, NopDatavPanel panel,
                                          Map<String, Object> requestParams, Integer rowLimit) {
        return queryPanelData(panelId, panel, requestParams, rowLimit, null);
    }

    /**
     * 查询面板数据（可选查询结果缓存，plan 2026-08-15-0004-3，裁定见 runtime-design.md §8.3/§8.4）。
     *
     * <p>缓存接入点固定为 {@code getDashboardData} 批量路径：仅该调用方传入非 null 缓存；既有调用方
     * （getPanelData/refreshPanel/exportDashboard/AlertEvaluator）经 2/4 参重载委托本方法传 null，
     * 行为逐字节等价（缓存全链路零触达）。缓存键在参数求值之后构造（本方法内，不复制求值逻辑）；
     * 无数据集条目在本方法缓存判定点之前返回（不缓存）；面板级失败抛 NopException（不产生可缓存值）。
     * 命中时以当前面板的 panelId/componentType 重建响应字段，rows/columns 复用缓存实例
     * （{@link PanelDataResult} 按不可变契约对待，§8.4）。</p>
     *
     * @param resultCache 查询结果缓存；null 表示不缓存（既有调用方全部为 null）
     */
    public PanelDataResult queryPanelData(String panelId, NopDatavPanel panel,
                                          Map<String, Object> requestParams, Integer rowLimit,
                                          DashboardPanelQueryCache resultCache) {
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", panelId);
        }

        String componentType = PanelTypeMapping.toComponentType(panel.getPanelType());
        IPanelComponent component = componentRegistry.requireComponent(componentType);
        PanelComponentMeta meta = component.getMetadata();

        // 无数据集组件（text/iframe/container）：返回明确标识，不报错也不静默跳过（不经缓存：零成本重建）
        if (!meta.isNeedsDataset()) {
            return new PanelDataResult(panelId, componentType, false,
                    Collections.emptyList(), Collections.emptyList());
        }

        // 有数据集组件：走完整查询委托链路
        String datasetRefId = panel.getDatasetRefId();
        if (datasetRefId == null || datasetRefId.isEmpty()) {
            throw new NopException(ERR_DATAV_DATASET_REF_NOT_FOUND)
                    .param("datasetRefId", "<null>")
                    .param("panelId", panelId);
        }

        NopDatavDatasetRef datasetRef = daoProvider.daoFor(NopDatavDatasetRef.class)
                .getEntityById(datasetRefId);
        if (datasetRef == null) {
            throw new NopException(ERR_DATAV_DATASET_REF_NOT_FOUND)
                    .param("datasetRefId", datasetRefId)
                    .param("panelId", panelId);
        }

        String refDatasetId = datasetRef.getRefDatasetId();
        IEntityDao<NopReportDataset> reportDatasetDao = daoProvider.daoFor(NopReportDataset.class);
        NopReportDataset reportDataset = reportDatasetDao.getEntityById(refDatasetId);
        if (reportDataset == null) {
            throw new NopException(ERR_DATAV_DATASET_NOT_FOUND)
                    .param("refDatasetId", refDatasetId)
                    .param("panelId", panelId);
        }

        String dsType = reportDataset.getDsType();
        if (!DS_TYPE_SQL.equalsIgnoreCase(dsType)) {
            throw new NopException(ERR_DATAV_UNSUPPORTED_DATASET_TYPE)
                    .param("dsType", dsType)
                    .param("panelId", panelId);
        }

        String dsText = reportDataset.getDsText();
        // PanelParamEvaluator 抛出的 NopException(ERR_DATAV_INVALID_PARAM_CONFIG) 静态工具层无 panelId 上下文，
        // 在此补 panelId 后 rethrow（catch 紧贴 evaluate，仍在下方 executeQuery try 之外，保持结构清晰）
        Map<String, Object> params;
        try {
            params = PanelParamEvaluator.evaluate(datasetRef.getParamMapping(), requestParams);
        } catch (NopException ne) {
            throw ne.param(ARG_PANEL_ID, panelId);
        }

        // 查询结果缓存（仅批量路径传入非 null）：键=数据集身份+求值后参数+行数约束（求值完成后构键，不复制求值逻辑）
        String cacheKey = null;
        if (resultCache != null) {
            cacheKey = DashboardPanelQueryCache.buildKey(refDatasetId, params, rowLimit);
            PanelDataResult cached = resultCache.get(cacheKey);
            if (cached != null) {
                return new PanelDataResult(panelId, componentType, cached.isHasDataset(),
                        cached.getColumns(), cached.getRows());
            }
        }

        SQL sql = PanelSqlBuilder.build(dsText, params, panelId);

        // 使用 executeQuery 同时拿到 meta + rows；导出路径经 LongRangeBean 在数据集层限行（防 OOM）
        try {
            PanelDataResult result = jdbcTemplate.executeQuery(sql,
                    rowLimit == null ? null : LongRangeBean.longRange(0, rowLimit.longValue()),
                    ds -> {
                        List<String> columns = extractColumnNames(ds.getMeta());
                        List<Map<String, Object>> rows = extractRows(ds);
                        return new PanelDataResult(panelId, componentType, true, columns, rows);
                    });
            if (resultCache != null) {
                // 成功结果回填（行数超准入上界不缓存；失败路径不到达此处——面板级失败以 NopException 抛出）
                resultCache.tryPut(cacheKey, result);
            }
            return result;
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_DATAV_QUERY_FAILED)
                    .param("panelId", panelId)
                    .cause(e);
        }
    }

    private static List<String> extractColumnNames(IDataSetMeta meta) {
        int count = meta.getFieldCount();
        if (count == 0) {
            return Collections.emptyList();
        }
        List<String> columns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            columns.add(meta.getFieldName(i));
        }
        return Collections.unmodifiableList(columns);
    }

    private static List<Map<String, Object>> extractRows(IDataSet ds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (ds.hasNext()) {
            IDataRow row = ds.next();
            rows.add(row.toMap());
        }
        return Collections.unmodifiableList(rows);
    }
}
