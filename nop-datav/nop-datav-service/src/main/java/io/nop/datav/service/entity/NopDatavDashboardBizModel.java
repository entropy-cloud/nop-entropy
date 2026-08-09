package io.nop.datav.service.entity;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoEntity;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.orm.dao.IOrmEntityDao;

import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavPanel;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND;

@BizModel("NopDatavDashboard")
public class NopDatavDashboardBizModel extends CrudBizModel<NopDatavDashboard>
        implements INopDatavDashboardBiz {

    public static final int PUBLISH_STATUS_DRAFT = 0;
    public static final int PUBLISH_STATUS_PUBLISHED = 10;

    @jakarta.inject.Inject
    protected IJdbcTemplate jdbcTemplate;

    public NopDatavDashboardBizModel() {
        setEntityName(NopDatavDashboard.class.getName());
    }

    @Override
    @BizMutation
    public NopDatavDashboardSnapshot publishDashboard(@Name("id") String id, IServiceContext context) {
        NopDatavDashboard dashboard = requireEntity(id, "publishDashboard", context);

        String snapshotContent = serializeDashboardContent(dashboard);
        long nextVersion = calculateNextVersion(id);
        String publishedBy = resolveOperator(context);
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

    private String serializeDashboardContent(NopDatavDashboard dashboard) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("dashboardName", dashboard.getDashboardName());
        content.put("displayName", dashboard.getDisplayName());
        content.put("description", dashboard.getDescription());
        content.put("layoutConfig", parseJson(dashboard.getLayoutConfig()));
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

    private String resolveOperator(IServiceContext context) {
        String userName = null;
        if (context != null) {
            if (context.getUserContext() != null) {
                userName = context.getUserContext().getUserName();
            }
            if ((userName == null || userName.isEmpty()) && context.getContext() != null) {
                userName = context.getContext().getUserName();
            }
        }
        return userName == null || userName.isEmpty() ? "system" : userName;
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
                .sql(",PUBLISH_STATUS=").param(source.getPublishStatus())
                .sql(",PUBLISHED_VERSION=").param(source.getPublishedVersion())
                .sql(",PUBLISHED_BY=").param(source.getPublishedBy())
                .sql(",PUBLISHED_TIME=").param(source.getPublishedTime())
                .sql(" where DASHBOARD_ID=").param(dashboardId).end());
    }
}
