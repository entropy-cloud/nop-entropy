package io.nop.datav.service.entity;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavPanel;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNopDatavDashboardBizModel extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    // ==================== CRUD Tests ====================

    @Test
    public void testDashboardCrud() {
        NopDatavDashboard dashboard = newDashboard("dash-crud", "crud-dashboard");
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(dashboard);

        NopDatavDashboard loaded = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById("dash-crud");
        assertNotNull(loaded);
        assertEquals("crud-dashboard", loaded.getDashboardName());

        loaded.setDisplayName("Updated Display");
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(loaded);

        NopDatavDashboard updated = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById("dash-crud");
        assertEquals("Updated Display", updated.getDisplayName());
    }

    @Test
    public void testPanelCrud() {
        NopDatavDashboard dashboard = saveDashboard("dash-panel-crud", "panel-crud");
        NopDatavPanel panel = newPanel("panel-1", dashboard.getDashboardId(), "First Panel");
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(panel);

        NopDatavPanel loaded = daoProvider.daoFor(NopDatavPanel.class).getEntityById("panel-1");
        assertNotNull(loaded);
        assertEquals(dashboard.getDashboardId(), loaded.getDashboardId());
        assertEquals("First Panel", loaded.getPanelName());

        loaded.setPanelName("Updated Panel");
        daoProvider.daoFor(NopDatavPanel.class).updateEntityDirectly(loaded);

        NopDatavPanel updated = daoProvider.daoFor(NopDatavPanel.class).getEntityById("panel-1");
        assertEquals("Updated Panel", updated.getPanelName());
    }

    @Test
    public void testTabCrud() {
        NopDatavDashboard dashboard = saveDashboard("dash-tab-crud", "tab-crud");
        NopDatavDashboardTab tab = newTab("tab-1", dashboard.getDashboardId(), "First Tab");
        daoProvider.daoFor(NopDatavDashboardTab.class).saveEntityDirectly(tab);

        NopDatavDashboardTab loaded = daoProvider.daoFor(NopDatavDashboardTab.class)
                .getEntityById("tab-1");
        assertNotNull(loaded);
        assertEquals("First Tab", loaded.getTabName());
    }

    @Test
    public void testDatasetRefCrud() {
        NopDatavDashboard dashboard = saveDashboard("dash-ref-crud", "ref-crud");
        NopDatavDatasetRef ref = newDatasetRef("ref-1", dashboard.getDashboardId(),
                "ds-001", "Sales Dataset");
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavDatasetRef loaded = daoProvider.daoFor(NopDatavDatasetRef.class)
                .getEntityById("ref-1");
        assertNotNull(loaded);
        assertEquals("ds-001", loaded.getRefDatasetId());
        assertEquals("Sales Dataset", loaded.getRefDatasetName());
    }

    // ==================== Publish / Snapshot Tests ====================

    @Test
    public void testPublishDashboardCreatesSnapshotAndUpdatesMainTable() {
        NopDatavDashboard dashboard = saveDashboard("dash-publish", "publish-dashboard");
        dashboard.setLayoutConfig(JsonTool.stringify(Map.of("grid", "2x2")));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);
        savePanel("panel-pub-1", dashboard.getDashboardId(), "Chart Panel");
        saveTab("tab-pub-1", dashboard.getDashboardId(), "Main Tab");
        saveDatasetRef("ref-pub-1", dashboard.getDashboardId(), "ds-pub", "Published DS");

        IServiceContext context = newContext("alice");

        NopDatavDashboardSnapshot snapshot = dashboardBiz.publishDashboard(
                dashboard.getDashboardId(), context);

        assertNotNull(snapshot);
        assertEquals(1L, snapshot.getSnapshotVersion());
        assertEquals("alice", snapshot.getPublishedBy());
        assertNotNull(snapshot.getSnapshotContent());
        assertNotNull(snapshot.getPublishedTime());

        Map<String, Object> content = JsonTool.parseMap(snapshot.getSnapshotContent());
        assertNotNull(content);
        assertNotNull(content.get("layoutConfig"));
        assertEquals(1, ((List<?>) content.get("panels")).size());
        assertEquals(1, ((List<?>) content.get("tabs")).size());
        assertEquals(1, ((List<?>) content.get("datasetRefs")).size());

        NopDatavDashboard updated = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById(dashboard.getDashboardId());
        assertEquals(NopDatavDashboardBizModel.PUBLISH_STATUS_PUBLISHED, updated.getPublishStatus());
        assertEquals(1L, updated.getPublishedVersion());
        assertEquals("alice", updated.getPublishedBy());
    }

    @Test
    public void testPublishTwiceIncrementsVersion() {
        NopDatavDashboard dashboard = saveDashboard("dash-publish-2", "publish-twice");
        IServiceContext context = newContext("bob");

        NopDatavDashboardSnapshot snap1 = dashboardBiz.publishDashboard(
                dashboard.getDashboardId(), context);
        assertEquals(1L, snap1.getSnapshotVersion());

        NopDatavDashboardSnapshot snap2 = dashboardBiz.publishDashboard(
                dashboard.getDashboardId(), context);
        assertEquals(2L, snap2.getSnapshotVersion());
        assertNotEquals(snap1.getSnapshotId(), snap2.getSnapshotId());

        NopDatavDashboard updated = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById(dashboard.getDashboardId());
        assertEquals(2L, updated.getPublishedVersion());
    }

    @Test
    public void testGetPublishedDashboardReturnsLatestSnapshot() {
        NopDatavDashboard dashboard = saveDashboard("dash-getpub", "get-published");
        IServiceContext context = newContext("alice");

        dashboardBiz.publishDashboard(dashboard.getDashboardId(), context);
        NopDatavDashboardSnapshot snap2 = dashboardBiz.publishDashboard(
                dashboard.getDashboardId(), context);

        NopDatavDashboardSnapshot published = dashboardBiz.getPublishedDashboard(
                dashboard.getDashboardId(), context);

        assertNotNull(published);
        assertEquals(snap2.getSnapshotVersion(), published.getSnapshotVersion());
    }

    @Test
    public void testGetPublishedDashboardThrowsWhenNotPublished() {
        NopDatavDashboard dashboard = saveDashboard("dash-nopub", "no-publish");
        IServiceContext context = newContext("alice");

        NopException ex = assertThrows(NopException.class,
                () -> dashboardBiz.getPublishedDashboard(dashboard.getDashboardId(), context));
        assertEquals(ERR_DATAV_SNAPSHOT_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    @Test
    public void testRollbackDashboardRestoresFromHistoricalSnapshot() {
        NopDatavDashboard dashboard = saveDashboard("dash-rollback", "rollback-dashboard");
        dashboard.setLayoutConfig(JsonTool.stringify(Map.of("theme", "dark")));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        IServiceContext context = newContext("alice");

        NopDatavDashboardSnapshot snap1 = dashboardBiz.publishDashboard(
                dashboard.getDashboardId(), context);

        dashboard.setLayoutConfig(JsonTool.stringify(Map.of("theme", "light")));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        dashboardBiz.publishDashboard(dashboard.getDashboardId(), context);

        NopDatavDashboardSnapshot rolled = dashboardBiz.rollbackDashboard(
                dashboard.getDashboardId(), snap1.getSnapshotVersion(), context);

        assertNotNull(rolled);
        assertEquals(snap1.getSnapshotVersion(), rolled.getSnapshotVersion());

        NopDatavDashboard restored = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById(dashboard.getDashboardId());
        Map<String, Object> layout = JsonTool.parseMap(restored.getLayoutConfig());
        assertEquals("dark", layout.get("theme"));
        assertEquals(snap1.getSnapshotVersion(), restored.getPublishedVersion());
    }

    @Test
    public void testRollbackThrowsForNonExistentVersion() {
        NopDatavDashboard dashboard = saveDashboard("dash-rollback-404", "rollback-404");
        IServiceContext context = newContext("alice");

        dashboardBiz.publishDashboard(dashboard.getDashboardId(), context);

        NopException ex = assertThrows(NopException.class,
                () -> dashboardBiz.rollbackDashboard(dashboard.getDashboardId(), 999L, context));
        assertEquals(ERR_DATAV_SNAPSHOT_VERSION_NOT_FOUND.getErrorCode(), ex.getErrorCode());
    }

    // ==================== End-to-End Test ====================

    @Test
    public void testEndToEndCreateConfigurePublishViewRollback() {
        IServiceContext context = newContext("e2e-user");

        // 1. Create dashboard
        NopDatavDashboard dashboard = saveDashboard("dash-e2e", "e2e-dashboard");
        dashboard.setLayoutConfig(JsonTool.stringify(Map.of("grid", "2x2")));
        daoProvider.daoFor(NopDatavDashboard.class).updateEntityDirectly(dashboard);

        // 2. Add panels, tabs, dataset refs
        savePanel("panel-e2e-1", dashboard.getDashboardId(), "Sales Chart");
        savePanel("panel-e2e-2", dashboard.getDashboardId(), "Revenue Table");
        saveTab("tab-e2e-1", dashboard.getDashboardId(), "Overview");
        saveDatasetRef("ref-e2e-1", dashboard.getDashboardId(), "ds-sales", "Sales DS");

        // 3. Publish
        NopDatavDashboardSnapshot snap1 = dashboardBiz.publishDashboard(
                dashboard.getDashboardId(), context);
        assertEquals(1L, snap1.getSnapshotVersion());

        // Verify snapshot content contains all related entities
        Map<String, Object> content1 = JsonTool.parseMap(snap1.getSnapshotContent());
        assertEquals(2, ((List<?>) content1.get("panels")).size());
        assertEquals(1, ((List<?>) content1.get("tabs")).size());
        assertEquals(1, ((List<?>) content1.get("datasetRefs")).size());

        // 4. Modify and publish again
        savePanel("panel-e2e-3", dashboard.getDashboardId(), "Extra Panel");
        NopDatavDashboardSnapshot snap2 = dashboardBiz.publishDashboard(
                dashboard.getDashboardId(), context);
        assertEquals(2L, snap2.getSnapshotVersion());

        // 5. View published (should be latest = version 2)
        NopDatavDashboardSnapshot published = dashboardBiz.getPublishedDashboard(
                dashboard.getDashboardId(), context);
        assertEquals(2L, published.getSnapshotVersion());

        // 6. Rollback to version 1
        NopDatavDashboardSnapshot rolled = dashboardBiz.rollbackDashboard(
                dashboard.getDashboardId(), 1L, context);
        assertEquals(1L, rolled.getSnapshotVersion());

        // Verify main table now points to version 1
        NopDatavDashboard restored = daoProvider.daoFor(NopDatavDashboard.class)
                .getEntityById(dashboard.getDashboardId());
        assertEquals(1L, restored.getPublishedVersion());
    }

    // ==================== Helpers ====================

    private IServiceContext newContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavDashboard saveDashboard(String id, String name) {
        NopDatavDashboard d = newDashboard(id, name);
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }

    private void savePanel(String id, String dashboardId, String name) {
        NopDatavPanel p = newPanel(id, dashboardId, name);
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
    }

    private void saveTab(String id, String dashboardId, String name) {
        NopDatavDashboardTab t = newTab(id, dashboardId, name);
        daoProvider.daoFor(NopDatavDashboardTab.class).saveEntityDirectly(t);
    }

    private void saveDatasetRef(String id, String dashboardId, String refDsId, String refDsName) {
        NopDatavDatasetRef r = newDatasetRef(id, dashboardId, refDsId, refDsName);
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(r);
    }

    private NopDatavDashboard newDashboard(String id, String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("test");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("test");
        d.setUpdateTime(new Timestamp(now));
        return d;
    }

    private NopDatavPanel newPanel(String id, String dashboardId, String name) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(id);
        p.setDashboardId(dashboardId);
        p.setPanelName(name);
        p.setDisplayName(name);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("test");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("test");
        p.setUpdateTime(new Timestamp(now));
        return p;
    }

    private NopDatavDashboardTab newTab(String id, String dashboardId, String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboardTab t = new NopDatavDashboardTab();
        t.setTabId(id);
        t.setDashboardId(dashboardId);
        t.setTabName(name);
        t.setDisplayName(name);
        t.setSortOrder(0);
        t.setVersion(0L);
        t.setCreatedBy("test");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("test");
        t.setUpdateTime(new Timestamp(now));
        return t;
    }

    private NopDatavDatasetRef newDatasetRef(String id, String dashboardId, String refDsId,
                                              String refDsName) {
        long now = System.currentTimeMillis();
        NopDatavDatasetRef r = new NopDatavDatasetRef();
        r.setDatasetRefId(id);
        r.setDashboardId(dashboardId);
        r.setRefDatasetId(refDsId);
        r.setRefDatasetName(refDsName);
        r.setVersion(0L);
        r.setCreatedBy("test");
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy("test");
        r.setUpdateTime(new Timestamp(now));
        return r;
    }
}
