package io.nop.datav.service.query;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.IDataRow;

import io.nop.datav.biz.PanelDataResult;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.component.IPanelComponent;
import io.nop.datav.service.component.PanelComponentMeta;
import io.nop.datav.service.component.PanelComponentRegistry;
import io.nop.datav.service.component.PanelTypeMapping;

import io.nop.report.dao.entity.NopReportDataset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        if (panel == null) {
            throw new NopException(ERR_DATAV_PANEL_NOT_FOUND).param("panelId", panelId);
        }

        String componentType = PanelTypeMapping.toComponentType(panel.getPanelType());
        IPanelComponent component = componentRegistry.requireComponent(componentType);
        PanelComponentMeta meta = component.getMetadata();

        // 无数据集组件（text/iframe/container）：返回明确标识，不报错也不静默跳过
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
        Map<String, Object> params = PanelParamEvaluator.evaluate(datasetRef.getParamMapping(), requestParams);

        SQL sql = PanelSqlBuilder.build(dsText, params, panelId);

        // 使用 executeQuery 同时拿到 meta + rows
        try {
            return jdbcTemplate.executeQuery(sql, ds -> {
                List<String> columns = extractColumnNames(ds.getMeta());
                List<Map<String, Object>> rows = extractRows(ds);
                return new PanelDataResult(panelId, componentType, true, columns, rows);
            });
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
