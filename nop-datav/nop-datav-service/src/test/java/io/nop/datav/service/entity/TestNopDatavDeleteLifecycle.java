package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavAlertRuleBiz;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavDashboardShareBiz;
import io.nop.datav.biz.INopDatavPanelBiz;
import io.nop.datav.biz.INopDatavReportTaskBiz;
import io.nop.datav.biz.INopDatavScreenBiz;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardShare;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavFilterState;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.dao.entity.NopDatavScreen;
import io.nop.datav.dao.entity.NopDatavScreenSnapshot;
import io.nop.datav.dao.entity.NopDatavScreenWidget;
import io.nop.datav.service.alert.NopDatavAlertScheduler;
import io.nop.datav.service.report.NopDatavReportScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_PANEL_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_SHARE_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 看板删除生命周期级联测试（plan 2026-08-14-2020-1 Phase 2）。
 *
 * <p><b>接线验证（Anti-Hollow rule #23）</b>：全部删除均经 biz 层标准 delete action
 * （{@code INopDatav*Biz.delete(id, context)}，即 CrudBizModel 标准路径 {@code delete(id) →
 * doDelete → doDeleteEntity}）触发，非直调内部级联方法——证明级联确实挂在标准 CRUD 删除路径上。</p>
 *
 * <p>覆盖：①删除看板后分享访问显式拒绝（ERR_DATAV_SHARE_DISABLED）；②删除看板后全部分享
 * enabled=0（DAO 级断言）；③删除看板后 ReportTask/AlertRule 置 DISABLED 且调度器 job 即时注销
 * （getRegisteredJobNames 可观察）；④子对象 + 快照级联物理删除；⑤getSharedDashboard 看板存活防御
 * 独立于级联生效（ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND）；⑥Gap #3 回归——直接删除告警规则/报告任务
 * 后 cron job 即时注销；⑦删除大屏后 widget/snapshot 级联物理删除（Phase 3）；⑧删除面板后关联
 * AlertRule 停用 + 即时注销（Phase 3）。</p>
 */
public class TestNopDatavDeleteLifecycle extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavDashboardShareBiz shareBiz;

    @Inject
    INopDatavAlertRuleBiz alertRuleBiz;

    @Inject
    INopDatavReportTaskBiz reportTaskBiz;

    @Inject
    INopDatavScreenBiz screenBiz;

    @Inject
    INopDatavPanelBiz panelBiz;

    @Inject
    NopDatavAlertScheduler alertScheduler;

    @Inject
    NopDatavReportScheduler reportScheduler;

    /**
     * 经 ORM session 包裹 biz 调用（镜像生产请求级 session 语义：整个 mutation 在同一 session 内，
     * doDelete 加载的实体在 deleteEntity 时仍 attached）。测试直调 biz 层无请求级 session，
     * 需显式提供——与生产 GraphQL 层开启请求 session 的行为一致。
     */
    private void runInSession(Runnable action) {
        ormTemplate.runInSession(session -> {
            action.run();
            return null;
        });
    }

    // ==================== ①② 删除看板 → 分享吊销 ====================

    /**
     * ① 删除看板后 {@code getSharedDashboard(旧 token)} 显式拒绝：级联把该看板全部分享置
     * enabled=0，公共访问走 ERR_DATAV_SHARE_DISABLED 拒绝路径（不再返回已发布快照）。
     */
    @Test
    public void testDeleteDashboardRevokesSharedAccess() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-del-share", "del-share", "alice");
        seedSnapshot(dash.getDashboardId(), "alice");
        NopDatavDashboardShare share = shareBiz.createShare(dash.getDashboardId(), null, null, ownerCtx);
        assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()),
                "share works before delete");

        runInSession(() -> assertTrue(dashboardBiz.delete(dash.getDashboardId(), ownerCtx), "biz delete returns true"));

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_DISABLED.getErrorCode(), ex.getErrorCode(),
                "revoked share rejected explicitly after dashboard delete");
    }

    /**
     * ② 删除看板后该看板全部分享 enabled=0（DAO 级断言；启用与已禁用混合——已禁用行保持 0 不扰动）。
     */
    @Test
    public void testDeleteDashboardDisablesAllShares() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-del-shares", "del-shares", "alice");
        seedSnapshot(dash.getDashboardId(), "alice");
        NopDatavDashboardShare active1 = shareBiz.createShare(dash.getDashboardId(), null, null, ownerCtx);
        NopDatavDashboardShare active2 = shareBiz.createShare(dash.getDashboardId(), "pwd", null, ownerCtx);
        NopDatavDashboardShare alreadyDisabled = shareBiz.createShare(dash.getDashboardId(), null, null, ownerCtx);
        shareBiz.revokeShare(alreadyDisabled.getShareId(), ownerCtx);

        runInSession(() -> assertTrue(dashboardBiz.delete(dash.getDashboardId(), ownerCtx)));

        assertEquals((byte) 0, shareById(active1.getShareId()).getEnabled(), "active share 1 revoked");
        assertEquals((byte) 0, shareById(active2.getShareId()).getEnabled(), "active share 2 revoked");
        assertEquals((byte) 0, shareById(alreadyDisabled.getShareId()).getEnabled(), "already-disabled stays 0");
        assertNotNull(shareById(active1.getShareId()), "share rows retained for audit (D2 logical revoke)");
    }

    // ==================== ③④ 删除看板 → 调度停用 + 子对象/快照级联 ====================

    /**
     * ③④ 完整级联：删看板后 ReportTask/AlertRule 置 DISABLED 且调度器注册表即时不含对应 job；
     * Panel/Tab/DatasetRef/FilterState/Snapshot 全部级联物理删除（行数 0）；AlertRule 行保留（仅停用）。
     */
    @Test
    public void testDeleteDashboardDisablesSchedulersAndCascadesChildren() {
        assertNotNull(alertScheduler.getScheduler(), "test env must register IJobScheduler (nop-job-local test scope)");
        assertNotNull(reportScheduler.getScheduler(), "test env must register IJobScheduler (nop-job-local test scope)");

        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-del-cascade", "del-cascade", "alice");
        String dashboardId = dash.getDashboardId();
        seedPanel("panel-c1", dashboardId);
        seedPanel("panel-c2", dashboardId);
        seedTab("tab-c1", dashboardId);
        seedDatasetRef("ref-c1", dashboardId);
        seedFilterState("state-c1", dashboardId, "alice");
        seedSnapshot(dashboardId, "alice");

        NopDatavAlertRule enabledRule = seedAlertRule("rule-c1", "panel-c1", dashboardId, true);
        NopDatavAlertRule disabledRule = seedAlertRule("rule-c2", "panel-c2", dashboardId, false);
        NopDatavReportTask task = seedReportTask("task-c1", dashboardId, true);

        alertScheduler.registerRule(enabledRule.getAlertRuleId());
        reportScheduler.registerTask(task.getReportTaskId());
        String alertJob = NopDatavAlertScheduler.jobName(enabledRule.getAlertRuleId());
        String reportJob = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(alertScheduler.getRegisteredJobNames().contains(alertJob), "alert job registered before delete");
        assertTrue(reportScheduler.getRegisteredJobNames().contains(reportJob), "report job registered before delete");

        runInSession(() -> assertTrue(dashboardBiz.delete(dashboardId, ownerCtx)));

        // 主表 + 子对象 + 快照：物理删除终态（jdbcTemplate 行数断言，不受 ORM session 缓存影响）
        assertNull(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dashboardId), "dashboard row gone");
        assertEquals(0, countRows("nop_datav_panel", dashboardId), "panels cascaded");
        assertEquals(0, countRows("nop_datav_tab", dashboardId), "tabs cascaded");
        assertEquals(0, countRows("nop_datav_dataset_ref", dashboardId), "dataset refs cascaded");
        assertEquals(0, countRows("nop_datav_filter_state", dashboardId), "filter states cascaded");
        assertEquals(0, countRows("nop_datav_snapshot", dashboardId), "snapshots cascaded (D1)");

        // 调度消费者：DISABLED（行保留）+ 注册表即时不含 job
        assertEquals((int) NopDatavReportTaskStatus.DISABLED,
                ruleById(enabledRule.getAlertRuleId()).getStatus(), "enabled rule disabled by cascade");
        assertEquals((int) NopDatavReportTaskStatus.DISABLED,
                ruleById(disabledRule.getAlertRuleId()).getStatus(), "already-disabled rule stays disabled");
        assertEquals((int) NopDatavReportTaskStatus.DISABLED,
                taskById(task.getReportTaskId()).getStatus(), "report task disabled by cascade");
        assertFalse(alertScheduler.getRegisteredJobNames().contains(alertJob),
                "alert job unregistered immediately: " + alertScheduler.getRegisteredJobNames());
        assertFalse(reportScheduler.getRegisteredJobNames().contains(reportJob),
                "report job unregistered immediately: " + reportScheduler.getRegisteredJobNames());
    }

    // ==================== ⑤ getSharedDashboard 看板存活防御（独立于级联） ====================

    /**
     * ⑤ 构造场景：分享仍 enabled=1 但看板主表行已删（绕过 biz 级联直接删行）——
     * 防御校验独立于级联吊销生效，显式拒绝 ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND（非静默返回旧快照）。
     */
    @Test
    public void testSharedDashboardDefenseRejectsDeletedDashboardIndependently() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-defense", "defense", "alice");
        seedSnapshot(dash.getDashboardId(), "alice");
        NopDatavDashboardShare share = shareBiz.createShare(dash.getDashboardId(), null, null, ownerCtx);

        // 绕过 biz 级联直接删主表行（构造「分享启用但看板已删」的残留场景）
        runInSession(() -> daoProvider.daoFor(NopDatavDashboard.class)
                .deleteEntity(daoProvider.daoFor(NopDatavDashboard.class).getEntityById(dash.getDashboardId())));
        assertEquals((byte) 1, shareById(share.getShareId()).getEnabled(),
                "constructed scenario: share still enabled");

        NopException ex = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_DASHBOARD_NOT_FOUND.getErrorCode(), ex.getErrorCode(),
                "defense-in-depth rejects deleted dashboard even when share row is still enabled");
    }

    // ==================== ⑥ Gap #3 回归：直接删除规则/任务即时注销 ====================

    /**
     * ⑥a Gap #3 回归：直接经 biz delete 删除 ENABLED 告警规则（注册表先含 job）→
     * 删除后注册表即时不含该 job（原先 3 参 afterEntityChange 覆写在 delete 路径不触发）。
     */
    @Test
    public void testDeleteAlertRuleUnregistersCronJobImmediately() {
        assertNotNull(alertScheduler.getScheduler(), "test env must register IJobScheduler (nop-job-local test scope)");
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-rule-del", "rule-del", "alice");
        seedPanel("panel-rule-del", dash.getDashboardId());
        NopDatavAlertRule rule = seedAlertRule("rule-del", "panel-rule-del", dash.getDashboardId(), true);

        alertScheduler.registerRule(rule.getAlertRuleId());
        String jobName = NopDatavAlertScheduler.jobName(rule.getAlertRuleId());
        assertTrue(alertScheduler.getRegisteredJobNames().contains(jobName), "job registered before delete");

        runInSession(() -> assertTrue(alertRuleBiz.delete(rule.getAlertRuleId(), ownerCtx)));
        assertFalse(alertScheduler.getRegisteredJobNames().contains(jobName),
                "job unregistered immediately after biz delete (Gap #3): "
                        + alertScheduler.getRegisteredJobNames());
        assertNull(daoProvider.daoFor(NopDatavAlertRule.class).getEntityById(rule.getAlertRuleId()),
                "rule row deleted");
    }

    /**
     * ⑥b Gap #3 回归：直接经 biz delete 删除 ENABLED 报告任务 → 注册表即时不含该 job。
     */
    @Test
    public void testDeleteReportTaskUnregistersCronJobImmediately() {
        assertNotNull(reportScheduler.getScheduler(), "test env must register IJobScheduler (nop-job-local test scope)");
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-task-del", "task-del", "alice");
        NopDatavReportTask task = seedReportTask("task-del", dash.getDashboardId(), true);

        reportScheduler.registerTask(task.getReportTaskId());
        String jobName = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(reportScheduler.getRegisteredJobNames().contains(jobName), "job registered before delete");

        runInSession(() -> assertTrue(reportTaskBiz.delete(task.getReportTaskId(), ownerCtx)));
        assertFalse(reportScheduler.getRegisteredJobNames().contains(jobName),
                "job unregistered immediately after biz delete (Gap #3): "
                        + reportScheduler.getRegisteredJobNames());
        assertNull(daoProvider.daoFor(NopDatavReportTask.class).getEntityById(task.getReportTaskId()),
                "task row deleted");
    }

    // ==================== ⑦ 大屏删除级联（Phase 3） ====================

    /**
     * ⑦ 删除大屏（经 biz 层标准 delete action）后：ScreenWidget 与 ScreenSnapshot 均级联物理删除
     * （D1 裁定的可观察终态——widget 不可再读 = 行数为 0；快照处置 = 行数为 0，非口头落地）。
     */
    @Test
    public void testDeleteScreenCascadesWidgetsAndSnapshots() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavScreen screen = seedScreen("screen-del", "del-screen", "alice");
        seedWidget("widget-del-1", screen.getScreenId(), "metric");
        seedWidget("widget-del-2", screen.getScreenId(), "text");
        seedScreenSnapshot(screen.getScreenId(), "alice", 1L);
        seedScreenSnapshot(screen.getScreenId(), "alice", 2L);
        assertEquals(2L, countScreenRows("nop_datav_screen_widget", screen.getScreenId()), "widgets seeded");
        assertEquals(2L, countScreenRows("nop_datav_screen_snapshot", screen.getScreenId()), "snapshots seeded");

        runInSession(() -> assertTrue(screenBiz.delete(screen.getScreenId(), ownerCtx)));

        assertNull(daoProvider.daoFor(NopDatavScreen.class).getEntityById(screen.getScreenId()), "screen row gone");
        assertEquals(0L, countScreenRows("nop_datav_screen_widget", screen.getScreenId()),
                "widgets no longer readable (D1 physical cascade)");
        assertEquals(0L, countScreenRows("nop_datav_screen_snapshot", screen.getScreenId()),
                "snapshots cascaded (D1 disposition asserted)");
    }

    // ==================== ⑧ 面板删除停用关联告警规则（Phase 3） ====================

    /**
     * ⑧ 删除面板（经 biz 层标准 delete action）后：按 panelId 关联的 AlertRule 置 status=DISABLED
     * （行保留）且调度器注册表即时不含该规则 job（ENABLED 规则删除前已注册）。
     */
    @Test
    public void testDeletePanelDisablesAndUnregistersAlertRules() {
        assertNotNull(alertScheduler.getScheduler(), "test env must register IJobScheduler (nop-job-local test scope)");
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-panel-del", "panel-del", "alice");
        seedPanel("panel-del-target", dash.getDashboardId());
        NopDatavAlertRule enabledRule = seedAlertRule("rule-panel-del-1", "panel-del-target",
                dash.getDashboardId(), true);
        NopDatavAlertRule disabledRule = seedAlertRule("rule-panel-del-2", "panel-del-target",
                dash.getDashboardId(), false);

        alertScheduler.registerRule(enabledRule.getAlertRuleId());
        String jobName = NopDatavAlertScheduler.jobName(enabledRule.getAlertRuleId());
        assertTrue(alertScheduler.getRegisteredJobNames().contains(jobName), "job registered before delete");

        runInSession(() -> assertTrue(panelBiz.delete("panel-del-target", ownerCtx)));

        assertNull(daoProvider.daoFor(NopDatavPanel.class).getEntityById("panel-del-target"), "panel row gone");
        assertEquals((int) NopDatavReportTaskStatus.DISABLED,
                ruleById(enabledRule.getAlertRuleId()).getStatus(), "enabled rule disabled by panel delete");
        assertEquals((int) NopDatavReportTaskStatus.DISABLED,
                ruleById(disabledRule.getAlertRuleId()).getStatus(), "already-disabled rule stays disabled");
        assertFalse(alertScheduler.getRegisteredJobNames().contains(jobName),
                "rule job unregistered immediately after panel delete: "
                        + alertScheduler.getRegisteredJobNames());
    }

    // ==================== ⑨ 端到端全链路（Phase 4，Anti-Hollow rule #22） ====================

    /**
     * ⑨ 端到端：创建看板（含多面板）→ 发布 → 建分享 → 建告警规则 + 报告任务（ENABLED，注册 cron job）
     * → 经 biz 层 delete 删除看板 → 断言：分享访问拒绝（ERR_DATAV_SHARE_DISABLED）、面板查询报
     * panel not found（ERR_DATAV_PANEL_NOT_FOUND）、规则/任务 DISABLED 且 job 已注销——从 biz 入口到
     * DAO 终态与调度器注册表终态完整走通。
     */
    @Test
    public void testE2eFullDeleteLifecycleChain() {
        assertNotNull(alertScheduler.getScheduler(), "test env must register IJobScheduler (nop-job-local test scope)");
        assertNotNull(reportScheduler.getScheduler(), "test env must register IJobScheduler (nop-job-local test scope)");
        IServiceContext ownerCtx = ownerContext("alice");

        // 1. 创建看板（含多面板）
        NopDatavDashboard dash = saveDashboard("dash-e2e-del", "e2e-del", "alice");
        seedPanel("panel-e2e-del-1", dash.getDashboardId());
        seedPanel("panel-e2e-del-2", dash.getDashboardId());

        // 2. 发布（产生快照）
        NopDatavDashboardSnapshot snapshot = dashboardBiz.publishDashboard(dash.getDashboardId(), ownerCtx);
        assertNotNull(snapshot.getSnapshotContent(), "publish produced snapshot");

        // 3. 建分享
        NopDatavDashboardShare share = shareBiz.createShare(dash.getDashboardId(), null, null, ownerCtx);
        assertNotNull(shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()),
                "share access works before delete");

        // 4. 建告警规则 + 报告任务（ENABLED）并注册 cron job
        NopDatavAlertRule rule = seedAlertRule("rule-e2e-del", "panel-e2e-del-1", dash.getDashboardId(), true);
        NopDatavReportTask task = seedReportTask("task-e2e-del", dash.getDashboardId(), true);
        alertScheduler.registerRule(rule.getAlertRuleId());
        reportScheduler.registerTask(task.getReportTaskId());
        String alertJob = NopDatavAlertScheduler.jobName(rule.getAlertRuleId());
        String reportJob = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(alertScheduler.getRegisteredJobNames().contains(alertJob), "alert job registered");
        assertTrue(reportScheduler.getRegisteredJobNames().contains(reportJob), "report job registered");

        // 5. 删除看板（biz 层标准 delete action）
        runInSession(() -> assertTrue(dashboardBiz.delete(dash.getDashboardId(), ownerCtx)));

        // 6. 断言链：分享访问拒绝 + 面板 not found + 规则/任务 DISABLED + job 注销
        NopException shareEx = assertThrows(NopException.class,
                () -> shareBiz.getSharedDashboard(share.getShareToken(), null, anonymousContext()));
        assertEquals(ERR_DATAV_SHARE_DISABLED.getErrorCode(), shareEx.getErrorCode(), "share access rejected");

        // 面板查询显式报 not found：requireEntity 对缺失实体抛 UnknownEntityException
        // （entityName=NopDatavPanel + panelId），非静默返回数据
        io.nop.dao.exceptions.UnknownEntityException panelEx = assertThrows(
                io.nop.dao.exceptions.UnknownEntityException.class,
                () -> panelBiz.getPanelData("panel-e2e-del-1", null, ownerCtx));
        assertEquals("io.nop.datav.dao.entity.NopDatavPanel", panelEx.getEntityName(), "panel not found");
        assertEquals("panel-e2e-del-1", String.valueOf(panelEx.getEntityId()), "panelId carried in error");

        assertEquals((int) NopDatavReportTaskStatus.DISABLED, ruleById(rule.getAlertRuleId()).getStatus(),
                "alert rule DISABLED");
        assertEquals((int) NopDatavReportTaskStatus.DISABLED, taskById(task.getReportTaskId()).getStatus(),
                "report task DISABLED");
        assertFalse(alertScheduler.getRegisteredJobNames().contains(alertJob), "alert job unregistered");
        assertFalse(reportScheduler.getRegisteredJobNames().contains(reportJob), "report job unregistered");
    }

    // ==================== Helpers ====================

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private IServiceContext anonymousContext() {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        return context;
    }

    private long countRows(String table, String dashboardId) {
        // 经 jdbcTemplate 原生 count 断言物理删除终态（不受 ORM session 缓存影响）
        Long count = jdbcTemplate.findLong(SQL.begin().name("count:" + table)
                .sql("select count(*) from " + table + " where DASHBOARD_ID=").param(dashboardId).end(), 0L);
        return count == null ? 0L : count;
    }

    private long countScreenRows(String table, String screenId) {
        Long count = jdbcTemplate.findLong(SQL.begin().name("count:" + table)
                .sql("select count(*) from " + table + " where SCREEN_ID=").param(screenId).end(), 0L);
        return count == null ? 0L : count;
    }

    private NopDatavDashboardShare shareById(String shareId) {
        return daoProvider.daoFor(NopDatavDashboardShare.class).getEntityById(shareId);
    }

    private NopDatavAlertRule ruleById(String ruleId) {
        return daoProvider.daoFor(NopDatavAlertRule.class).getEntityById(ruleId);
    }

    private NopDatavReportTask taskById(String taskId) {
        return daoProvider.daoFor(NopDatavReportTask.class).getEntityById(taskId);
    }

    private NopDatavDashboard saveDashboard(String id, String name, String owner) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy(owner);
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy(owner);
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }

    private void seedPanel(String panelId, String dashboardId) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelId + "-name");
        p.setDisplayName(panelId + "-display");
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("alice");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("alice");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
    }

    private void seedTab(String tabId, String dashboardId) {
        long now = System.currentTimeMillis();
        NopDatavDashboardTab t = new NopDatavDashboardTab();
        t.setTabId(tabId);
        t.setDashboardId(dashboardId);
        t.setTabName(tabId + "-name");
        t.setDisplayName(tabId + "-display");
        t.setSortOrder(0);
        t.setVersion(0L);
        t.setCreatedBy("alice");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("alice");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboardTab.class).saveEntityDirectly(t);
    }

    private void seedDatasetRef(String refId, String dashboardId) {
        long now = System.currentTimeMillis();
        NopDatavDatasetRef r = new NopDatavDatasetRef();
        r.setDatasetRefId(refId);
        r.setDashboardId(dashboardId);
        r.setRefDatasetId(refId + "-ds");
        r.setParamMapping("{}");
        r.setVersion(0L);
        r.setCreatedBy("alice");
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy("alice");
        r.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(r);
    }

    private void seedFilterState(String stateId, String dashboardId, String userName) {
        long now = System.currentTimeMillis();
        NopDatavFilterState s = new NopDatavFilterState();
        s.setStateId(stateId);
        s.setUserName(userName);
        s.setDashboardId(dashboardId);
        s.setStateContent("{}");
        s.setVersion(0L);
        s.setCreatedBy(userName);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(userName);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavFilterState.class).saveEntityDirectly(s);
    }

    private void seedSnapshot(String dashboardId, String owner) {
        long now = System.currentTimeMillis();
        NopDatavDashboardSnapshot s = new NopDatavDashboardSnapshot();
        s.setSnapshotId(java.util.UUID.randomUUID().toString().replace("-", ""));
        s.setDashboardId(dashboardId);
        s.setSnapshotVersion(1L);
        s.setSnapshotContent("{\"dashboardId\":\"" + dashboardId + "\"}");
        s.setPublishedBy(owner);
        s.setPublishedTime(new Timestamp(now));
        s.setVersion(0L);
        s.setCreatedBy(owner);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(owner);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(s);
    }

    private NopDatavAlertRule seedAlertRule(String ruleId, String panelId, String dashboardId, boolean enabled) {
        long now = System.currentTimeMillis();
        NopDatavAlertRule r = new NopDatavAlertRule();
        r.setAlertRuleId(ruleId);
        r.setRuleName(ruleId + "-name");
        r.setDisplayName(ruleId + "-display");
        r.setPanelId(panelId);
        r.setValueField("amount");
        r.setAggregation("sum");
        r.setOperator("gt");
        r.setThresholdValue(new BigDecimal("100"));
        r.setRearmSeconds(0);
        r.setCronExpr("0 0 8 * * ?");
        r.setNotifyChannels("[\"email\"]");
        r.setRecipients("[\"a@example.com\"]");
        r.setStatus(enabled ? NopDatavReportTaskStatus.ENABLED : NopDatavReportTaskStatus.DISABLED);
        r.setVersion(0L);
        r.setCreatedBy("alice");
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy("alice");
        r.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavAlertRule.class).saveEntityDirectly(r);
        return r;
    }

    private NopDatavReportTask seedReportTask(String taskId, String dashboardId, boolean enabled) {
        long now = System.currentTimeMillis();
        NopDatavReportTask t = new NopDatavReportTask();
        t.setReportTaskId(taskId);
        t.setTaskName(taskId + "-name");
        t.setDisplayName(taskId + "-display");
        t.setDashboardId(dashboardId);
        t.setCronExpr("0 0 8 * * ?");
        t.setFormat("csv");
        t.setRecipients("[\"a@example.com\"]");
        t.setNotifyChannels("[\"email\"]");
        t.setStatus(enabled ? NopDatavReportTaskStatus.ENABLED : NopDatavReportTaskStatus.DISABLED);
        t.setGraceMinutes(60);
        t.setVersion(0L);
        t.setCreatedBy("alice");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("alice");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportTask.class).saveEntityDirectly(t);
        return t;
    }

    private NopDatavScreen seedScreen(String id, String name, String owner) {
        long now = System.currentTimeMillis();
        NopDatavScreen s = new NopDatavScreen();
        s.setScreenId(id);
        s.setScreenName(name);
        s.setDisplayName(name);
        s.setScreenWidth(1920);
        s.setScreenHeight(1080);
        s.setAdaptorMode(10);
        s.setPublishStatus(0);
        s.setVersion(0L);
        s.setCreatedBy(owner);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(owner);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavScreen.class).saveEntityDirectly(s);
        return s;
    }

    private void seedWidget(String id, String screenId, String componentType) {
        long now = System.currentTimeMillis();
        NopDatavScreenWidget widget = new NopDatavScreenWidget();
        widget.setWidgetId(id);
        widget.setScreenId(screenId);
        widget.setWidgetName(id);
        widget.setDisplayName(id);
        widget.setComponentType(componentType);
        widget.setX(0);
        widget.setY(0);
        widget.setW(100);
        widget.setH(100);
        widget.setZ(0);
        widget.setVersion(0L);
        widget.setCreatedBy("alice");
        widget.setCreateTime(new Timestamp(now));
        widget.setUpdatedBy("alice");
        widget.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavScreenWidget.class).saveEntityDirectly(widget);
    }

    private void seedScreenSnapshot(String screenId, String owner, long version) {
        long now = System.currentTimeMillis();
        NopDatavScreenSnapshot s = new NopDatavScreenSnapshot();
        s.setSnapshotId(java.util.UUID.randomUUID().toString().replace("-", ""));
        s.setScreenId(screenId);
        s.setSnapshotVersion(version);
        s.setSnapshotContent("{\"screenId\":\"" + screenId + "\"}");
        s.setPublishedBy(owner);
        s.setPublishedTime(new Timestamp(now));
        s.setVersion(0L);
        s.setCreatedBy(owner);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(owner);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavScreenSnapshot.class).saveEntityDirectly(s);
    }
}
