package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.directive.Auth;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;

import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavFilterState;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.NopDatavOperatorResolver;
import io.nop.datav.service.alert.NopDatavAlertScheduler;
import io.nop.datav.service.filter.DashboardFilterResolver;
import io.nop.datav.service.filter.DashboardFilterUrlCodec;
import io.nop.datav.service.filter.DashboardParamDefinition;
import io.nop.datav.service.filter.DashboardParamParser;
import io.nop.datav.service.report.NopDatavReportScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND;

/**
 * 看板 BizModel。
 *
 * <p><b>删除生命周期（plan 2026-08-14-2020-1，裁定见 permission-sharing-design.md「删除生命周期与分享吊销」）
 * </b>：标准 {@code delete(id)} 路径（含 batchDelete/deleteByQuery 收敛的 {@code doDeleteEntity}）在主表行
 * 删除后级联处理：关联 ReportTask/AlertRule 置 {@code DISABLED} 并即时注销 cron job、该看板全部分享置
 * {@code enabled=0}、子对象（Panel/Tab/DatasetRef/FilterState）与发布快照物理删除。任一子步骤失败显式抛错。</p>
 */
@BizModel("NopDatavDashboard")
public class NopDatavDashboardBizModel extends CrudBizModel<NopDatavDashboard>
        implements INopDatavDashboardBiz {

    public static final int PUBLISH_STATUS_DRAFT = 0;
    public static final int PUBLISH_STATUS_PUBLISHED = 10;

    /**
     * 分享启用标记（domain boolFlag）：1=启用，0=禁用（D2 逻辑吊销值）。
     */
    private static final byte SHARE_ENABLED_TRUE = 1;

    @jakarta.inject.Inject
    protected IJdbcTemplate jdbcTemplate;

    @jakarta.inject.Inject
    protected NopDatavAlertScheduler alertScheduler;

    @jakarta.inject.Inject
    protected NopDatavReportScheduler reportScheduler;

    public NopDatavDashboardBizModel() {
        setEntityName(NopDatavDashboard.class.getName());
    }

    // ==================== 删除生命周期级联（plan 2026-08-14-2020-1） ====================

    /**
     * 标准删除路径级联挂接点（D5 裁定）：{@code delete(id)} / {@code batchDelete} / {@code deleteByQuery}
     * 均虚分派到本方法。级联在 {@code super} 之后执行——权限校验（checkMetaFilter/checkDataAuth）通过后
     * 才产生调度注销等非事务性副作用。任一子步骤失败异常传播（无静默跳过）。
     */
    @Override
    protected void doDeleteEntity(@Name("entity") NopDatavDashboard entity,
                                  @Name("refNamesToCheck") Set<String> refNamesToCheck,
                                  @Name("prepareDelete") BiConsumer<NopDatavDashboard, IServiceContext> prepareDelete,
                                  IServiceContext context) {
        super.doDeleteEntity(entity, refNamesToCheck, prepareDelete, context);
        handleDeleteCascade(entity, context);
    }

    private void handleDeleteCascade(NopDatavDashboard dashboard, IServiceContext context) {
        String dashboardId = dashboard.getDashboardId();
        IDaoProvider daoProvider = daoProvider();
        String operator = NopDatavOperatorResolver.resolveOperator(context);

        // AlertRule 经 panelId 定位，必须先于 Panel 行删除读取面板清单
        List<NopDatavPanel> panels = findRelatedEntities(NopDatavPanel.class, "dashboardId", dashboardId, null);

        disableReportTasksForDashboard(daoProvider, dashboardId, operator);
        disableAlertRulesForPanels(daoProvider, panels, operator);
        revokeSharesForDashboard(daoProvider, dashboardId, operator);

        deleteAllByDashboard(daoProvider, NopDatavFilterState.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavDatasetRef.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavDashboardTab.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavPanel.class, dashboardId);
        deleteAllByDashboard(daoProvider, NopDatavDashboardSnapshot.class, dashboardId);
    }

    /**
     * 关联 ReportTask（按 dashboardId）：置 status=DISABLED（先落库）并即时 unregisterTask（D3 双动作，
     * 事务边界见 schedule-report-design.md §26——注销失败异常传播回滚整个删除）。
     */
    private void disableReportTasksForDashboard(IDaoProvider daoProvider, String dashboardId, String operator) {
        IEntityDao<NopDatavReportTask> dao = daoProvider.daoFor(NopDatavReportTask.class);
        for (NopDatavReportTask task : findAllByField(dao, "dashboardId", dashboardId)) {
            if (task.getStatus() == null || task.getStatus() != NopDatavReportTaskStatus.DISABLED) {
                task.setStatus(NopDatavReportTaskStatus.DISABLED);
                task.setUpdatedBy(operator);
                task.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                dao.updateEntityDirectly(task);
            }
            if (reportScheduler != null) {
                reportScheduler.unregisterTask(task.getReportTaskId());
            }
        }
    }

    /**
     * 关联 AlertRule（经 panel.dashboardId 定位）：置 status=DISABLED 并即时 unregisterRule。
     */
    private void disableAlertRulesForPanels(IDaoProvider daoProvider, List<NopDatavPanel> panels, String operator) {
        if (panels.isEmpty()) {
            return;
        }
        List<String> panelIds = new ArrayList<>(panels.size());
        for (NopDatavPanel panel : panels) {
            panelIds.add(panel.getPanelId());
        }
        IEntityDao<NopDatavAlertRule> dao = daoProvider.daoFor(NopDatavAlertRule.class);
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.in("panelId", panelIds));
        for (NopDatavAlertRule rule : dao.findAllByQuery(query)) {
            if (rule.getStatus() == null || rule.getStatus() != NopDatavReportTaskStatus.DISABLED) {
                rule.setStatus(NopDatavReportTaskStatus.DISABLED);
                rule.setUpdatedBy(operator);
                rule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                dao.updateEntityDirectly(rule);
            }
            if (alertScheduler != null) {
                alertScheduler.unregisterRule(rule.getAlertRuleId());
            }
        }
    }

    /**
     * 该看板全部分享逻辑吊销（D2：enabled=0，保留行可审计；已禁用行不动避免版本扰动）。
     */
    private void revokeSharesForDashboard(IDaoProvider daoProvider, String dashboardId, String operator) {
        IEntityDao<NopDatavDashboardShare> dao = daoProvider.daoFor(NopDatavDashboardShare.class);
        for (NopDatavDashboardShare share : findAllByField(dao, "dashboardId", dashboardId)) {
            if (share.getEnabled() != null && share.getEnabled() == SHARE_ENABLED_TRUE) {
                share.setEnabled((byte) 0);
                share.setUpdatedBy(operator);
                share.setUpdateTime(new Timestamp(System.currentTimeMillis()));
                dao.updateEntityDirectly(share);
            }
        }
    }

    /**
     * 按 dashboardId 物理删除子表全部行（D1：Panel/Tab/DatasetRef/FilterState/Snapshot 级联）。
     */
    private void deleteAllByDashboard(IDaoProvider daoProvider, Class<? extends IDaoEntity> entityClass,
                                      String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        daoProvider.daoFor(entityClass).deleteByQuery(query);
    }

    private static <T extends IDaoEntity> List<T> findAllByField(IEntityDao<T> dao, String field, String value) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(field, value));
        return dao.findAllByQuery(query);
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboard:publishDashboard")
    public NopDatavDashboardSnapshot publishDashboard(@Name("id") String id, IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "publishDashboard", context);

        String snapshotContent = serializeDashboardContent(dashboard);
        long nextVersion = calculateNextVersion(id);
        String publishedBy = NopDatavOperatorResolver.resolveOperator(context);
        Timestamp publishedTime = new Timestamp(System.currentTimeMillis());

        NopDatavDashboardSnapshot snapshot = daoProvider()
                .daoFor(NopDatavDashboardSnapshot.class).newEntity();
        snapshot.setSnapshotId(generateSnapshotId());
        snapshot.setDashboardId(dashboard.getDashboardId());
        snapshot.setSnapshotVersion(nextVersion);
        snapshot.setSnapshotContent(snapshotContent);
        snapshot.setPublishedBy(publishedBy);
        snapshot.setPublishedTime(publishedTime);
        snapshot.setVersion(0L);
        snapshot.setCreatedBy(publishedBy);
        snapshot.setCreateTime(publishedTime);
        snapshot.setUpdatedBy(publishedBy);
        snapshot.setUpdateTime(publishedTime);

        daoProvider().daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(snapshot);

        updateDashboardPublishState(dashboard.getDashboardId(), PUBLISH_STATUS_PUBLISHED,
                nextVersion, publishedBy, publishedTime);

        afterEntityChange(dashboard, "publishDashboard", context);
        return snapshot;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:getPublishedDashboard")
    public NopDatavDashboardSnapshot getPublishedDashboard(@Name("id") String id, IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "getPublishedDashboard", context);

        NopDatavDashboardSnapshot snapshot = findLatestSnapshot(dashboard.getDashboardId());
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SNAPSHOT_NOT_FOUND)
                    .param("dashboardId", dashboard.getDashboardId());
        }
        return snapshot;
    }

    @Override
    @BizMutation
    @Auth(permissions = "NopDatavDashboard:rollbackDashboard")
    public NopDatavDashboardSnapshot rollbackDashboard(@Name("id") String id,
                                                       @Name("snapshotVersion") long snapshotVersion,
                                                       IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "rollbackDashboard", context);

        NopDatavDashboardSnapshot snapshot = findSnapshotByVersion(dashboard.getDashboardId(), snapshotVersion);
        if (snapshot == null) {
            throw new NopException(ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND)
                    .param("dashboardId", dashboard.getDashboardId())
                    .param("snapshotVersion", snapshotVersion);
        }

        restoreDashboardFromSnapshot(dashboard, snapshot);
        updateDashboardFields(dashboard.getDashboardId(), dashboard);

        afterEntityChange(dashboard, "rollbackDashboard", context);
        return snapshot;
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:resolveFilterValues")
    public Map<String, Object> resolveFilterValues(@Name("id") String id,
                                                    @Name("filterValues") Map<String, Object> filterValues,
                                                    IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "resolveFilterValues", context);
        List<DashboardParamDefinition> definitions = DashboardParamParser.parse(dashboard.getParamConfig());
        return DashboardFilterResolver.resolve(definitions, filterValues);
    }

    @Override
    @BizQuery
    @Auth(permissions = "NopDatavDashboard:parseFilterFromUrl")
    public Map<String, Object> parseFilterFromUrl(@Name("id") String id,
                                                   @Name("url") String url,
                                                   IServiceContext context) {
        requireEntity(id, "parseFilterFromUrl", context);
        Map<String, Object> parsed = DashboardFilterUrlCodec.parseQueryString(url);
        if (parsed.isEmpty()) {
            return Collections.emptyMap();
        }
        NopDatavDashboard dashboard = daoProvider().daoFor(NopDatavDashboard.class)
                .getEntityById(id);
        List<DashboardParamDefinition> definitions = DashboardParamParser.parse(dashboard.getParamConfig());
        return DashboardFilterResolver.resolve(definitions, parsed);
    }

    private String serializeDashboardContent(NopDatavDashboard dashboard) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("dashboardName", dashboard.getDashboardName());
        content.put("displayName", dashboard.getDisplayName());
        content.put("description", dashboard.getDescription());
        content.put("layoutConfig", parseJson(dashboard.getLayoutConfig()));
        content.put("paramConfig", parseJson(dashboard.getParamConfig()));
        content.put("panels", serializePanels(dashboard.getDashboardId()));
        content.put("tabs", serializeTabs(dashboard.getDashboardId()));
        content.put("datasetRefs", serializeDatasetRefs(dashboard.getDashboardId()));
        return JsonTool.stringify(content);
    }

    private List<Map<String, Object>> serializePanels(String dashboardId) {
        List<NopDatavPanel> panels = findRelatedEntities(NopDatavPanel.class, "dashboardId", dashboardId, "sortOrder");
        List<Map<String, Object>> result = new ArrayList<>(panels.size());
        for (NopDatavPanel panel : panels) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("panelId", panel.getPanelId());
            map.put("panelName", panel.getPanelName());
            map.put("displayName", panel.getDisplayName());
            map.put("panelType", panel.getPanelType());
            map.put("datasetRefId", panel.getDatasetRefId());
            map.put("tabId", panel.getTabId());
            map.put("sortOrder", panel.getSortOrder());
            map.put("panelConfig", parseJson(panel.getPanelConfig()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeTabs(String dashboardId) {
        List<NopDatavDashboardTab> tabs = findRelatedEntities(NopDatavDashboardTab.class, "dashboardId", dashboardId,
                "sortOrder");
        List<Map<String, Object>> result = new ArrayList<>(tabs.size());
        for (NopDatavDashboardTab tab : tabs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("tabId", tab.getTabId());
            map.put("tabName", tab.getTabName());
            map.put("displayName", tab.getDisplayName());
            map.put("sortOrder", tab.getSortOrder());
            map.put("tabConfig", parseJson(tab.getTabConfig()));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> serializeDatasetRefs(String dashboardId) {
        List<NopDatavDatasetRef> refs = findRelatedEntities(NopDatavDatasetRef.class, "dashboardId", dashboardId, null);
        List<Map<String, Object>> result = new ArrayList<>(refs.size());
        for (NopDatavDatasetRef ref : refs) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("datasetRefId", ref.getDatasetRefId());
            map.put("refDatasetId", ref.getRefDatasetId());
            map.put("refDatasetName", ref.getRefDatasetName());
            map.put("paramMapping", parseJson(ref.getParamMapping()));
            result.add(map);
        }
        return result;
    }

    private <T extends IDaoEntity> List<T> findRelatedEntities(Class<T> entityClass, String filterField, String filterValue,
                                            String orderField) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(filterField, filterValue));
        if (orderField != null) {
            query.addOrderField(orderField, false);
        }
        @SuppressWarnings("unchecked")
        List<T> list = (List<T>) daoProvider().daoFor(entityClass).findAllByQuery(query);
        return list;
    }

    private long calculateNextVersion(String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addOrderField("snapshotVersion", true);
        query.setLimit(1);
        NopDatavDashboardSnapshot latest = daoProvider()
                .daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);
        return latest == null ? 1L : latest.getSnapshotVersion() + 1;
    }

    private NopDatavDashboardSnapshot findLatestSnapshot(String dashboardId) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addOrderField("snapshotVersion", true);
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);
    }

    private NopDatavDashboardSnapshot findSnapshotByVersion(String dashboardId, long snapshotVersion) {
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq("dashboardId", dashboardId));
        query.addFilter(FilterBeans.eq("snapshotVersion", snapshotVersion));
        query.setLimit(1);
        return daoProvider().daoFor(NopDatavDashboardSnapshot.class).findFirstByQuery(query);
    }

    @SuppressWarnings("unchecked")
    private void restoreDashboardFromSnapshot(NopDatavDashboard dashboard, NopDatavDashboardSnapshot snapshot) {
        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        if (content == null) {
            throw new NopException(ERR_DATAV_SNAPSHOT_NOT_FOUND)
                    .param("dashboardId", dashboard.getDashboardId());
        }

        Object layoutConfig = content.get("layoutConfig");
        dashboard.setLayoutConfig(layoutConfig == null ? null : JsonTool.stringify(layoutConfig));

        Object paramConfig = content.get("paramConfig");
        dashboard.setParamConfig(paramConfig == null ? null : JsonTool.stringify(paramConfig));

        dashboard.setPublishStatus(PUBLISH_STATUS_PUBLISHED);
        dashboard.setPublishedVersion(snapshot.getSnapshotVersion());
        dashboard.setPublishedBy(snapshot.getPublishedBy());
        dashboard.setPublishedTime(snapshot.getPublishedTime());
    }

    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return JsonTool.parse(json);
    }

    private String generateSnapshotId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private void updateDashboardPublishState(String dashboardId, int publishStatus, long publishedVersion,
                                              String publishedBy, Timestamp publishedTime) {
        jdbcTemplate.executeUpdate(SQL.begin().name("updateDashboardPublishState")
                .sql("update NOP_DATAV_DASHBOARD set PUBLISH_STATUS=").param(publishStatus)
                .sql(",PUBLISHED_VERSION=").param(publishedVersion)
                .sql(",PUBLISHED_BY=").param(publishedBy)
                .sql(",PUBLISHED_TIME=").param(publishedTime)
                .sql(" where DASHBOARD_ID=").param(dashboardId).end());
    }

    private void updateDashboardFields(String dashboardId, NopDatavDashboard source) {
        jdbcTemplate.executeUpdate(SQL.begin().name("updateDashboardFields")
                .sql("update NOP_DATAV_DASHBOARD set LAYOUT_CONFIG=").param(source.getLayoutConfig())
                .sql(",PARAM_CONFIG=").param(source.getParamConfig())
                .sql(",PUBLISH_STATUS=").param(source.getPublishStatus())
                .sql(",PUBLISHED_VERSION=").param(source.getPublishedVersion())
                .sql(",PUBLISHED_BY=").param(source.getPublishedBy())
                .sql(",PUBLISHED_TIME=").param(source.getPublishedTime())
                .sql(" where DASHBOARD_ID=").param(dashboardId).end());
    }
}
