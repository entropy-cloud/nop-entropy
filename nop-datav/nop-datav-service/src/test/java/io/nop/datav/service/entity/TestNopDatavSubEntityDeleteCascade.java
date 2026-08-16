package io.nop.datav.service.entity;

import io.nop.api.core.context.TenantProxyContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.biz.INopDatavAlertRuleBiz;
import io.nop.datav.biz.INopDatavChatSessionBiz;
import io.nop.datav.biz.INopDatavDashboardBiz;
import io.nop.datav.biz.INopDatavDashboardTabBiz;
import io.nop.datav.biz.INopDatavReportTaskBiz;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavAlertState;
import io.nop.datav.dao.entity.NopDatavChatMessage;
import io.nop.datav.dao.entity.NopDatavChatSession;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardTab;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.alert.NopDatavAlertScheduler;
import io.nop.datav.service.chatbi.ChatBiSessionManager;
import io.nop.datav.service.report.NopDatavReportDeliveryStatus;
import io.nop.datav.service.report.NopDatavReportScheduler;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.datav.service.report.NopDatavReportTriggerSource;
import io.nop.orm.IOrmTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-09（plan 2026-08-15-2146-3 Phase 3）子实体删除级联/解绑回归测试 ×4。
 *
 * <p><b>接线验证（Anti-Hollow rule #23）</b>：全部删除均经 biz 层标准 delete action
 * （{@code INopDatav*Biz.delete(id, context)}，即 {@code CrudBizModel} 标准路径
 * {@code delete(id) → doDelete → doDeleteEntity}）触发，非直调内部级联方法——证明级联/解绑
 * 确实挂在标准 CRUD 删除路径上。物理删除终态经 jdbcTemplate 原生 count 断言
 * （不受 ORM session 缓存影响）。</p>
 *
 * <p>覆盖：①删 ReportTask 级联物理删除 ReportDelivery 子行（调度注销副作用保持）；
 * ②删 AlertRule 级联物理删除 AlertState 子行（unregisterRule 保持）；③ChatSession 标准
 * {@code __delete} 级联物理删除 ChatMessage——与自定义 {@code deleteChatSession}
 * （ChatBiSessionManager）语义收敛（两路径均零孤儿消息）；④删 Tab 解绑引用面板
 * （{@code Panel.tabId} 置 null，面板保留，无悬挂引用）。</p>
 */
public class TestNopDatavSubEntityDeleteCascade extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IOrmTemplate ormTemplate;

    @Inject
    INopDatavDashboardBiz dashboardBiz;

    @Inject
    INopDatavDashboardTabBiz tabBiz;

    @Inject
    INopDatavAlertRuleBiz alertRuleBiz;

    @Inject
    INopDatavReportTaskBiz reportTaskBiz;

    @Inject
    INopDatavChatSessionBiz chatSessionBiz;

    @Inject
    NopDatavAlertScheduler alertScheduler;

    @Inject
    NopDatavReportScheduler reportScheduler;

    /**
     * 经 ORM session 包裹 biz 调用（镜像生产请求级 session 语义：整个 mutation 在同一 session 内，
     * doDelete 加载的实体在 deleteEntity 时仍 attached）。与 TestNopDatavDeleteLifecycle 同模式。
     */
    private void runInSession(Runnable action) {
        ormTemplate.runInSession(session -> {
            action.run();
            return null;
        });
    }

    // ==================== ① 删 ReportTask → 级联删除 ReportDelivery ====================

    /**
     * ① 标准删除报告任务（ENABLED，注册表先含 job）后：任务行删除 + 全部 ReportDelivery 子行
     * 级联物理删除（jdbcTemplate 行数断言）+ 调度器 job 即时注销（unregisterTask 副作用保持）。
     */
    @Test
    public void testDeleteReportTaskCascadesReportDeliveries() {
        assertNotNull(reportScheduler.getScheduler(), "test env must register IJobScheduler");
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-p109-task");
        NopDatavReportTask task = seedReportTask("task-p109", dash.getDashboardId(), true);
        seedDelivery("dlv-p109-1", task.getReportTaskId());
        seedDelivery("dlv-p109-2", task.getReportTaskId());
        seedDelivery("dlv-p109-other", "task-other-untouched");
        assertEquals(2L, countRows("nop_datav_report_delivery", "REPORT_TASK_ID", task.getReportTaskId()),
                "deliveries seeded");

        reportScheduler.registerTask(task.getReportTaskId());
        String jobName = NopDatavReportScheduler.jobName(task.getReportTaskId());
        assertTrue(reportScheduler.getRegisteredJobNames().contains(jobName), "job registered before delete");

        runInSession(() -> assertTrue(reportTaskBiz.delete(task.getReportTaskId(), ownerCtx), "biz delete returns true"));

        assertNull(daoProvider.daoFor(NopDatavReportTask.class).getEntityById(task.getReportTaskId()),
                "task row gone");
        assertEquals(0L, countRows("nop_datav_report_delivery", "REPORT_TASK_ID", task.getReportTaskId()),
                "deliveries cascaded (no orphan rows)");
        assertEquals(1L, countRows("nop_datav_report_delivery", "REPORT_TASK_ID", "task-other-untouched"),
                "other tasks' deliveries untouched");
        assertFalse(reportScheduler.getRegisteredJobNames().contains(jobName),
                "job unregistered immediately (unregisterTask preserved): "
                        + reportScheduler.getRegisteredJobNames());
    }

    // ==================== ② 删 AlertRule → 级联删除 AlertState ====================

    /**
     * ② 标准删除告警规则（ENABLED，注册表先含 job，规则已有 AlertState 行）后：规则行删除 +
     * AlertState 子行级联物理删除 + 调度器 job 即时注销（unregisterRule 副作用保持）。
     */
    @Test
    public void testDeleteAlertRuleCascadesAlertState() {
        assertNotNull(alertScheduler.getScheduler(), "test env must register IJobScheduler");
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-p109-rule");
        seedPanel("panel-p109", dash.getDashboardId(), null);
        NopDatavAlertRule rule = seedAlertRule("rule-p109", "panel-p109", dash.getDashboardId(), true);
        seedAlertState("state-p109", rule.getAlertRuleId());
        assertEquals(1L, countRows("nop_datav_alert_state", "ALERT_RULE_ID", rule.getAlertRuleId()),
                "alert state seeded");

        alertScheduler.registerRule(rule.getAlertRuleId());
        String jobName = NopDatavAlertScheduler.jobName(rule.getAlertRuleId());
        assertTrue(alertScheduler.getRegisteredJobNames().contains(jobName), "job registered before delete");

        runInSession(() -> assertTrue(alertRuleBiz.delete(rule.getAlertRuleId(), ownerCtx)));

        assertNull(daoProvider.daoFor(NopDatavAlertRule.class).getEntityById(rule.getAlertRuleId()),
                "rule row gone");
        assertEquals(0L, countRows("nop_datav_alert_state", "ALERT_RULE_ID", rule.getAlertRuleId()),
                "alert state cascaded (no orphan row)");
        assertFalse(alertScheduler.getRegisteredJobNames().contains(jobName),
                "job unregistered immediately (unregisterRule preserved): "
                        + alertScheduler.getRegisteredJobNames());
    }

    // ==================== ③ ChatSession 标准 delete 与 deleteChatSession 语义一致 ====================

    /**
     * ③a 标准 {@code __delete}（biz 层标准 delete action）删除会话后：会话行删除 + 全部消息
     * 级联物理删除（jdbcTemplate 行数断言）——原裸 CrudBizModel 路径产生孤儿消息的缺口已补齐。
     */
    @Test
    public void testStandardDeleteChatSessionCascadesMessages() {
        IServiceContext adminCtx = ownerContext("alice");
        NopDatavChatSession session = seedChatSession("sess-p109-std", "alice");
        seedChatMessage("msg-p109-std-1", session.getSessionId(), 1, "user");
        seedChatMessage("msg-p109-std-2", session.getSessionId(), 2, "assistant");
        assertEquals(2L, countRows("nop_datav_chat_message", "SESSION_ID", session.getSessionId()),
                "messages seeded");

        runInSession(() -> assertTrue(chatSessionBiz.delete(session.getSessionId(), adminCtx),
                "standard __delete returns true"));

        assertNull(daoProvider.daoFor(NopDatavChatSession.class).getEntityById(session.getSessionId()),
                "session row gone");
        assertEquals(0L, countRows("nop_datav_chat_message", "SESSION_ID", session.getSessionId()),
                "messages cascaded (no orphan rows)");
    }

    /**
     * ③b 语义收敛对照：自定义 {@code deleteChatSession}（ChatBiSessionManager，owner 校验 + 会话自删）
     * 与标准路径删除语义一致——同一终态（会话行 + 消息行均为 0，零孤儿）。
     */
    @Test
    public void testCustomDeleteChatSessionSemanticsConsistentWithStandardPath() {
        NopDatavChatSession session = seedChatSession("sess-p109-custom", "alice");
        seedChatMessage("msg-p109-custom-1", session.getSessionId(), 1, "user");
        seedChatMessage("msg-p109-custom-2", session.getSessionId(), 2, "assistant");

        ChatBiSessionManager manager = new ChatBiSessionManager();
        manager.setDaoProvider(daoProvider);
        runInSession(() -> manager.deleteSession(session.getSessionId(), "alice"));

        assertNull(daoProvider.daoFor(NopDatavChatSession.class).getEntityById(session.getSessionId()),
                "session row gone (custom path)");
        assertEquals(0L, countRows("nop_datav_chat_message", "SESSION_ID", session.getSessionId()),
                "messages cascaded (custom path, no orphan rows)");
    }

    // ==================== ④ 删 Tab → 解绑引用面板（Panel.tabId 置 null） ====================

    /**
     * ④ 标准删除页签后：引用该 tab 的面板解绑（{@code tabId} 置 null、面板行保留，不悬挂、不阻塞），
     * 未绑定面板不受影响。
     */
    @Test
    public void testDeleteTabUnbindsReferencingPanels() {
        IServiceContext ownerCtx = ownerContext("alice");
        NopDatavDashboard dash = saveDashboard("dash-p109-tab");
        seedTab("tab-p109", dash.getDashboardId());
        seedPanel("panel-p109-bound-1", dash.getDashboardId(), "tab-p109");
        seedPanel("panel-p109-bound-2", dash.getDashboardId(), "tab-p109");
        seedPanel("panel-p109-free", dash.getDashboardId(), null);
        assertEquals("tab-p109", panelById("panel-p109-bound-1").getTabId(), "panel bound before delete");

        runInSession(() -> assertTrue(tabBiz.delete("tab-p109", ownerCtx), "biz delete returns true"));

        assertNull(daoProvider.daoFor(NopDatavDashboardTab.class).getEntityById("tab-p109"), "tab row gone");
        assertNotNull(panelById("panel-p109-bound-1"), "bound panel 1 retained (unbound, not deleted)");
        assertNotNull(panelById("panel-p109-bound-2"), "bound panel 2 retained (unbound, not deleted)");
        assertNull(panelById("panel-p109-bound-1").getTabId(), "panel 1 tabId unbound (null, no dangling ref)");
        assertNull(panelById("panel-p109-bound-2").getTabId(), "panel 2 tabId unbound (null, no dangling ref)");
        assertNotNull(panelById("panel-p109-free"), "free panel untouched");
        assertNull(panelById("panel-p109-free").getTabId(), "free panel tabId stays null");
        assertEquals(0L, countRows("nop_datav_panel", "TAB_ID", "tab-p109"),
                "no panel still references the deleted tab (no dangling reference)");
    }

    // ==================== Helpers ====================

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private long countRows(String table, String column, String value) {
        Long count = jdbcTemplate.findLong(SQL.begin().name("count:" + table)
                .sql("select count(*) from " + table + " where " + column + "=").param(value).end(), 0L);
        return count == null ? 0L : count;
    }

    private NopDatavPanel panelById(String panelId) {
        return daoProvider.daoFor(NopDatavPanel.class).getEntityById(panelId);
    }

    private NopDatavDashboard saveDashboard(String id) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardId(id);
        d.setDashboardName(id);
        d.setDisplayName(id);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d;
    }

    private void seedPanel(String panelId, String dashboardId, String tabId) {
        long now = System.currentTimeMillis();
        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setTabId(tabId);
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

    private void seedAlertState(String stateId, String alertRuleId) {
        long now = System.currentTimeMillis();
        NopDatavAlertState s = new NopDatavAlertState();
        s.setAlertStateId(stateId);
        s.setAlertRuleId(alertRuleId);
        s.setState("OK");
        s.setConsecutiveEvalCount(0);
        s.setVersion(0L);
        s.setCreatedBy("alice");
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy("alice");
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavAlertState.class).saveEntityDirectly(s);
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

    private void seedDelivery(String deliveryId, String reportTaskId) {
        long now = System.currentTimeMillis();
        NopDatavReportDelivery d = new NopDatavReportDelivery();
        d.setDeliveryId(deliveryId);
        d.setReportTaskId(reportTaskId);
        d.setStatus(NopDatavReportDeliveryStatus.SUCCEEDED);
        d.setTriggeredBy(NopDatavReportTriggerSource.MANUAL);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportDelivery.class).saveEntityDirectly(d);
    }

    private NopDatavChatSession seedChatSession(String sessionId, String userName) {
        long now = System.currentTimeMillis();
        NopDatavChatSession s = new NopDatavChatSession();
        s.setSessionId(sessionId);
        s.setUserName(userName);
        s.setSessionTitle("p1-09");
        s.setVersion(0L);
        s.setCreatedBy(userName);
        s.setCreateTime(new Timestamp(now));
        s.setUpdatedBy(userName);
        s.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavChatSession.class).saveEntityDirectly(s);
        return s;
    }

    private void seedChatMessage(String messageId, String sessionId, int seq, String role) {
        long now = System.currentTimeMillis();
        NopDatavChatMessage m = new NopDatavChatMessage();
        m.setMessageId(messageId);
        m.setSessionId(sessionId);
        m.setSeq(seq);
        m.setRole(role);
        m.setContent("p1-09 content " + seq);
        m.setVersion(0L);
        m.setCreatedBy("alice");
        m.setCreateTime(new Timestamp(now));
        m.setUpdatedBy("alice");
        m.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavChatMessage.class).saveEntityDirectly(m);
    }
}
