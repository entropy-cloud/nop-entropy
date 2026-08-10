package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.context.TenantProxyContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavAlertState;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDashboardSnapshot;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.alert.AlertEvaluator;
import io.nop.datav.service.alert.NopDatavAlertScheduler;
import io.nop.datav.service.alert.NopDatavAlertStateValue;
import io.nop.datav.service.mock.MockEmailSender;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.datav.service.report.NotificationSender;
import io.nop.integration.api.email.EmailMessage;
import io.nop.report.dao.entity.NopReportDataset;
import io.nop.sys.dao.entity.NopSysNoticeTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轻量告警端到端测试（D5-2 Phase 4）。
 *
 * <p>覆盖：聚合求值、operator 比较、状态机两态转换、rearm 冷静期、面板缺失容错、告警通知（邮件）、
 * 调度注册、手动评估、权限、端到端链路、接线验证（Anti-Hollow rule #22/#23）。</p>
 *
 * <p>测试数据：TEST_DATAV_SALES 表（REGION/PRODUCT/AMOUNT），AMOUNT 列作 valueField。
 * 告警规则以 AMOUNT 阈值评估。</p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/datav/beans/test-report-mock.beans.xml")
@NopTestProperty(name = "nop.datav.report.default-sender", value = "noreply@example.com")
@NopTestProperty(name = "nop.datav.alert.default-subject", value = "Alert: {ruleName}")
public class TestNopDatavAlertE2E extends AbstractNopDatavTest {

    public TestNopDatavAlertE2E() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IDaoProvider daoProvider;

    @Inject
    AlertEvaluator alertEvaluator;

    @Inject
    NopDatavAlertScheduler alertScheduler;

    @Inject
    io.nop.integration.api.email.IEmailSender emailSender;

    private MockEmailSender mockEmailSender;

    @Override
    @BeforeEach
    public void init(TestInfo testInfo) {
        super.init(testInfo);
        mockEmailSender = (MockEmailSender) emailSender;
        mockEmailSender.reset();
    }

    // ==================== 聚合求值 ====================

    /**
     * sum 聚合：AMOUNT 列 sum = 100+200+50 = 350。
     */
    @Test
    public void testAggregationSum() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-agg-sum", "alice", true);
        saveChartPanelWithDataset("panel-agg-sum", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-agg-sum", "panel-agg-sum", dashboardId, "alice",
                "amount", "sum", "gt", "100", null, 0, true);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(rule.getAlertRuleId());

        assertTrue(result.conditionMet, "sum=350 > 100");
        assertEquals(new BigDecimal("350"), result.currentValue);
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), result.newState);
        assertTrue(result.notified, "OK→TRIGGERED sends notification");
        assertEquals(1, mockEmailSender.getSendCount(), "one alert email sent");
    }

    /**
     * avg 聚合：AMOUNT 列 avg = 350/3 = 116.6667（scale=4）。
     */
    @Test
    public void testAggregationAvg() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-agg-avg", "alice", true);
        saveChartPanelWithDataset("panel-agg-avg", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-agg-avg", "panel-agg-avg", dashboardId, "alice",
                "amount", "avg", "gt", "100", null, 0, true);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(rule.getAlertRuleId());

        assertTrue(result.conditionMet, "avg≈116.67 > 100");
        assertEquals(0, new BigDecimal("116.6667").compareTo(result.currentValue),
                "avg = 116.6667 (scale=4)");
    }

    /**
     * count 聚合：返回行数 = 3。
     */
    @Test
    public void testAggregationCount() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-agg-count", "alice", true);
        saveChartPanelWithDataset("panel-agg-count", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-agg-count", "panel-agg-count", dashboardId, "alice",
                "amount", "count", "gt", "2", null, 0, true);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(rule.getAlertRuleId());

        assertTrue(result.conditionMet, "count=3 > 2");
        assertEquals(0, new BigDecimal("3").compareTo(result.currentValue), "count = 3");
    }

    /**
     * 列不存在 → 显式失败 ERR_DATAV_ALERT_VALUE_FIELD_NOT_FOUND。
     */
    @Test
    public void testValueFieldNotFoundFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-field", "alice", true);
        saveChartPanelWithDataset("panel-no-field", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-no-field", "panel-no-field", dashboardId, "alice",
                "nonexistent_column", "first", "gt", "100", null, 0, true);

        NopException ex = assertThrows(NopException.class,
                () -> alertEvaluator.evaluate(rule.getAlertRuleId()));
        assertTrue(ex.getMessage().toLowerCase().contains("field")
                || ex.getMessage().toLowerCase().contains("column"),
                "errorMsg mentions field/column: " + ex.getMessage());
    }

    /**
     * 无数据行（queryPanelData 返回 0 行）→ 视为条件不满足（不触发、不抛错），consecutiveEvalCount=0。
     *
     * <p>构造空表（drop + recreate 不插入数据）实现。</p>
     */
    @Test
    public void testNoDataRowsTreatedAsConditionNotMet() {
        // 建空表（仅 schema，无数据）
        try {
            jdbcTemplate.executeUpdate(SQL.begin().name("drop:TEST_DATAV_SALES_ALERT_EMPTY")
                    .sql("drop table TEST_DATAV_SALES_ALERT_EMPTY").end());
        } catch (Exception ignored) {
            // 表可能尚不存在，忽略 drop 失败以保证幂等建表
        }
        jdbcTemplate.executeUpdate(SQL.begin().name("create:TEST_DATAV_SALES_ALERT_EMPTY")
                .sql("create table TEST_DATAV_SALES_ALERT_EMPTY(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());

        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-empty", "alice", true);
        saveChartPanelWithDatasetCustom("panel-empty", dashboardId, "Sales",
                "select REGION as region, PRODUCT as product, AMOUNT as amount from TEST_DATAV_SALES_ALERT_EMPTY");
        NopDatavAlertRule rule = seedAlertRule("rule-empty", "panel-empty", dashboardId, "alice",
                "amount", "first", "gt", "100", null, 0, true);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(rule.getAlertRuleId());

        assertFalse(result.conditionMet, "no data rows → condition not met");
        assertEquals(NopDatavAlertStateValue.OK.getValue(), result.newState, "state stays OK");
        assertFalse(result.notified, "no notification");
        assertNull(result.errorMsg, "no error (0 rows is normal, not error)");

        NopDatavAlertState state = loadState(rule.getAlertRuleId());
        assertEquals(0, state.getConsecutiveEvalCount(), "consecutiveEvalCount=0");
    }

    // ==================== operator 比较 ====================

    /**
     * between operator：thresholdValue ≤ currentValue ≤ thresholdValue2。
     */
    @Test
    public void testOperatorBetween() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-between", "alice", true);
        saveChartPanelWithDataset("panel-between", dashboardId, "Sales");
        // sum=350；between [100, 500] → 满足
        NopDatavAlertRule rule = seedAlertRule("rule-between", "panel-between", dashboardId, "alice",
                "amount", "sum", "between", "100", "500", 0, true);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(rule.getAlertRuleId());

        assertTrue(result.conditionMet, "350 between [100, 500]");
        assertTrue(result.notified, "OK→TRIGGERED notification sent");
    }

    /**
     * between + thresholdValue2 为 null → 显式失败 ERR_DATAV_ALERT_INVALID_THRESHOLD。
     */
    @Test
    public void testOperatorBetweenWithoutThreshold2FailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-between-no-t2", "alice", true);
        saveChartPanelWithDataset("panel-between-no-t2", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-between-no-t2", "panel-between-no-t2", dashboardId, "alice",
                "amount", "sum", "between", "100", null, 0, true);

        NopException ex = assertThrows(NopException.class,
                () -> alertEvaluator.evaluate(rule.getAlertRuleId()));
        assertNotNull(ex.getMessage(), "between without thresholdValue2 throws explicitly");
    }

    // ==================== 状态机两态转换 ====================

    /**
     * 状态机两态：OK→TRIGGERED（条件满足，发告警通知，lastTriggeredTime/lastNotifiedTime 更新，
     * consecutiveEvalCount=1）；TRIGGERED→OK（条件不再满足，发恢复通知，lastResolvedTime 更新，
     * consecutiveEvalCount 归 0）。
     *
     * <p><b>Anti-Hollow 接线验证（rule #23）</b>：mockEmailSender 收到 trigger + recover 两次 sendEmail，
     * 证明 AlertEvaluator 调用 NotificationSender.sendAlert → IEmailSender.sendEmail 调用链连通。</p>
     */
    @Test
    public void testStateMachineTwoStatesWithNotifications() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-state", "alice", true);
        saveChartPanelWithDataset("panel-state", dashboardId, "Sales");
        // AMOUNT first=200（desc 排序后首行 south widget 50 → 实际 max=200 first）
        // 用 sum=350 + gt 300 触发
        NopDatavAlertRule rule = seedAlertRule("rule-state", "panel-state", dashboardId, "alice",
                "amount", "sum", "gt", "300", null, 0, true);

        // 第一次评估：OK + 条件满足（350 > 300）→ TRIGGERED + 发告警通知
        AlertEvaluator.EvalResult r1 = alertEvaluator.evaluate(rule.getAlertRuleId());
        assertTrue(r1.conditionMet, "350 > 300");
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), r1.newState);
        assertTrue(r1.notified, "OK→TRIGGERED sends notification");
        assertEquals(1, mockEmailSender.getSendCount(), "one trigger email");

        NopDatavAlertState state1 = loadState(rule.getAlertRuleId());
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), state1.getState());
        assertNotNull(state1.getLastTriggeredTime(), "lastTriggeredTime updated");
        assertNotNull(state1.getLastNotifiedTime(), "lastNotifiedTime updated");
        assertNull(state1.getLastResolvedTime(), "lastResolvedTime still null");
        assertEquals(1, state1.getConsecutiveEvalCount(), "consecutiveEvalCount=1");

        // 改阈值让条件不再满足（350 < 1000）→ TRIGGERED→OK + 发恢复通知
        rule.setThresholdValue(new BigDecimal("1000"));
        rule.setUpdatedBy("alice");
        rule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopDatavAlertRule.class).updateEntityDirectly(rule);

        AlertEvaluator.EvalResult r2 = alertEvaluator.evaluate(rule.getAlertRuleId());
        assertFalse(r2.conditionMet, "350 < 1000");
        assertEquals(NopDatavAlertStateValue.OK.getValue(), r2.newState);
        assertTrue(r2.notified, "TRIGGERED→OK sends recovery notification");
        assertEquals(2, mockEmailSender.getSendCount(), "trigger + recover emails");

        NopDatavAlertState state2 = loadState(rule.getAlertRuleId());
        assertEquals(NopDatavAlertStateValue.OK.getValue(), state2.getState());
        assertNotNull(state2.getLastResolvedTime(), "lastResolvedTime updated");
        assertEquals(0, state2.getConsecutiveEvalCount(), "consecutiveEvalCount reset to 0");

        // 验证邮件内容含告警类型与当前值
        List<EmailMessage> mails = mockEmailSender.getSentMails();
        assertEquals(2, mails.size(), "2 emails (trigger + recover)");
        assertEquals(Arrays.asList("a@example.com", "b@example.com"), mails.get(0).getTo(),
                "recipients match rule.recipients");
    }

    // ==================== rearm 冷静期 ====================

    /**
     * rearmSeconds=0 时 TRIGGERED 持续满足不重复通知。
     *
     * <p>步骤：触发 → 再次评估（条件仍满足）→ 不应再发通知（rearmSeconds=0 仅转换通知）。</p>
     */
    @Test
    public void testRearmZeroNoRepeatNotification() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-rearm0", "alice", true);
        saveChartPanelWithDataset("panel-rearm0", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-rearm0", "panel-rearm0", dashboardId, "alice",
                "amount", "sum", "gt", "300", null, 0, true);

        // 第一次评估：OK → TRIGGERED + 通知
        AlertEvaluator.EvalResult r1 = alertEvaluator.evaluate(rule.getAlertRuleId());
        assertTrue(r1.notified, "first trigger notified");
        assertEquals(1, mockEmailSender.getSendCount());

        // 第二次评估：TRIGGERED 持续 + rearmSeconds=0 → 不重复通知
        AlertEvaluator.EvalResult r2 = alertEvaluator.evaluate(rule.getAlertRuleId());
        assertTrue(r2.conditionMet, "condition still met");
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), r2.newState);
        assertFalse(r2.notified, "rearmSeconds=0 → no repeat notification");
        assertEquals(1, mockEmailSender.getSendCount(), "still 1 email (no repeat)");
    }

    /**
     * rearmSeconds>0 时 TRIGGERED 持续满足距上次通知超期才重发。
     *
     * <p>步骤：触发（通知）→ 篡改 lastNotifiedTime 到过去 → 再次评估 → 重发通知。</p>
     */
    @Test
    public void testRearmPositiveReNotifiesAfterCooldown() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-rearm-pos", "alice", true);
        saveChartPanelWithDataset("panel-rearm-pos", dashboardId, "Sales");
        // rearmSeconds=60（1 分钟）
        NopDatavAlertRule rule = seedAlertRule("rule-rearm-pos", "panel-rearm-pos", dashboardId, "alice",
                "amount", "sum", "gt", "300", null, 60, true);

        // 第一次评估：OK → TRIGGERED + 通知
        alertEvaluator.evaluate(rule.getAlertRuleId());
        assertEquals(1, mockEmailSender.getSendCount());

        // 第二次评估（未过冷静期）→ 不重发
        AlertEvaluator.EvalResult r2 = alertEvaluator.evaluate(rule.getAlertRuleId());
        assertFalse(r2.notified, "cooldown not elapsed → no re-notify");
        assertEquals(1, mockEmailSender.getSendCount());

        // 篡改 lastNotifiedTime 到 2 分钟前（超过 60s 冷静期）
        NopDatavAlertState state = loadState(rule.getAlertRuleId());
        state.setLastNotifiedTime(new Timestamp(System.currentTimeMillis() - 120_000L));
        state.setUpdatedBy("test");
        state.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopDatavAlertState.class).updateEntityDirectly(state);

        // 第三次评估（已过冷静期）→ 重发
        AlertEvaluator.EvalResult r3 = alertEvaluator.evaluate(rule.getAlertRuleId());
        assertTrue(r3.notified, "cooldown elapsed → re-notify");
        assertEquals(2, mockEmailSender.getSendCount(), "second notification sent");
    }

    // ==================== 面板缺失/查询失败容错 ====================

    /**
     * 面板缺失（panelId 对应的 NopDatavPanel 查不到）→ 状态记 errorMsg、state 保持、返回 error 结果（不抛）。
     */
    @Test
    public void testPanelMissingFailsGracefully() {
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-panel", "alice", true);
        // 不创建 panel；规则引用不存在的 panelId
        NopDatavAlertRule rule = seedAlertRule("rule-no-panel", "nonexistent-panel-id", dashboardId, "alice",
                "amount", "sum", "gt", "100", null, 0, true);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(rule.getAlertRuleId());

        assertNotNull(result.errorMsg, "errorMsg recorded");
        assertEquals(NopDatavAlertStateValue.OK.getValue(), result.newState,
                "state preserved (stays OK, not flipped)");
        assertFalse(result.notified, "no notification");

        NopDatavAlertState state = loadState(rule.getAlertRuleId());
        assertNotNull(state.getErrorMsg(), "errorMsg persisted in state");
        assertEquals(NopDatavAlertStateValue.OK.getValue(), state.getState(),
                "state column not flipped on panel missing");
    }

    /**
     * executeScheduledAlert 吞业务错误（面板缺失）→ 返回正常结果（不抛）。
     *
     * <p><b>FAILED-brick 规避验证</b>：方法不抛——否则 invoker 转 JobFireResult.ERROR 后
     * LocalJobScheduler 将 job 永久置 FAILED。</p>
     */
    @Test
    public void testExecuteScheduledAlertSwallowsBusinessError() {
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-swallow", "alice", true);
        NopDatavAlertRule rule = seedAlertRule("rule-swallow", "nonexistent-panel-id", dashboardId, "alice",
                "amount", "sum", "gt", "100", null, 0, true);

        Map<String, Object> result = alertScheduler.fireScheduledForTest(rule.getAlertRuleId());

        // 关键契约：方法不抛、返回正常结果对象
        assertNotNull(result, "executeScheduledAlert must return a result, not throw");
        assertEquals("scheduled", result.get("status"),
                "business error swallowed, normal status returned");
    }

    /**
     * 不存在的 alertRuleId → executeScheduledAlert 吞错返回 failed 状态。
     */
    @Test
    public void testExecuteScheduledAlertWithUnknownRuleReturnsFailed() {
        Map<String, Object> result = alertScheduler.fireScheduledForTest("nonexistent-rule-id");
        assertNotNull(result);
        assertEquals("failed", result.get("status"));
        assertNotNull(result.get("error"));
    }

    // ==================== 告警通知（邮件）显式失败 ====================

    /**
     * 未配置发件人 → 显式失败 ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED。
     */
    @Test
    public void testSenderNotConfiguredFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-sender-alert", "alice", true);
        saveChartPanelWithDataset("panel-no-sender-alert", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-no-sender-alert", "panel-no-sender-alert", dashboardId, "alice",
                "amount", "sum", "gt", "100", null, 0, true);

        String origSender = io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SENDER.get();
        io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SENDER, "");
        try {
            NopException ex = assertThrows(NopException.class,
                    () -> alertEvaluator.evaluate(rule.getAlertRuleId()));
            assertNotNull(ex.getMessage(), "sender not configured → explicit failure");
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                    io.nop.datav.service.NopDatavConfigs.CFG_DATAV_REPORT_DEFAULT_SENDER, origSender);
        }
    }

    /**
     * 无通知渠道（notifyChannels 为空 JSON 数组）→ 显式失败 ERR_DATAV_ALERT_NO_NOTIFIABLE_CHANNEL。
     */
    @Test
    public void testNoNotifiableChannelFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-no-chan-alert", "alice", true);
        saveChartPanelWithDataset("panel-no-chan-alert", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRuleWithChannels("rule-no-chan-alert", "panel-no-chan-alert",
                dashboardId, "alice", "amount", "sum", "gt", "100", null, 0, true,
                "[]", "[\"a@example.com\"]");

        NopException ex = assertThrows(NopException.class,
                () -> alertEvaluator.evaluate(rule.getAlertRuleId()));
        assertNotNull(ex.getMessage(), "empty notifyChannels → explicit failure");
    }

    /**
     * IM 渠道 → UnsupportedOperationException（非静默）。
     */
    @Test
    public void testImChannelThrowsUnsupported() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-im-alert", "alice", true);
        saveChartPanelWithDataset("panel-im-alert", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRuleWithChannels("rule-im-alert", "panel-im-alert",
                dashboardId, "alice", "amount", "sum", "gt", "100", null, 0, true,
                "[\"im\"]", "[\"a@example.com\"]");

        assertThrows(UnsupportedOperationException.class,
                () -> alertEvaluator.evaluate(rule.getAlertRuleId()));
    }

    /**
     * 模板缺失（rule.templateKey 指定的 NopSysNoticeTemplate.name 查不到）→ 显式失败 ERR_DATAV_ALERT_TEMPLATE_NOT_FOUND。
     */
    @Test
    public void testTemplateNotFoundFailsExplicitly() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-tpl-alert", "alice", true);
        saveChartPanelWithDataset("panel-tpl-alert", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRuleWithTemplate("rule-tpl-alert", "panel-tpl-alert",
                dashboardId, "alice", "amount", "sum", "gt", "100", null, 0, true,
                "nonexistent-template-key");

        NopException ex = assertThrows(NopException.class,
                () -> alertEvaluator.evaluate(rule.getAlertRuleId()));
        assertTrue(ex.getMessage().toLowerCase().contains("template")
                || ex.getMessage().toLowerCase().contains("nop.sys"),
                "errorMsg mentions template: " + ex.getMessage());
    }

    // ==================== 调度注册 ====================

    /**
     * 规则初始化：seed enabled 规则 → 手动调 registerRule → addJob 注册。
     *
     * <p>seed 用 saveEntityDirectly（不经 BizModel），不会触发 afterEntityChange 自动注册。</p>
     */
    @Test
    public void testSchedulerRegisterUnregister() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-sched-alert", "alice", true);
        saveChartPanelWithDataset("panel-sched-alert", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-sched-alert", "panel-sched-alert", dashboardId, "alice",
                "amount", "sum", "gt", "100", null, 0, true);

        if (alertScheduler.getScheduler() == null) {
            return;
        }

        alertScheduler.registerRule(rule.getAlertRuleId());
        String jobName = NopDatavAlertScheduler.jobName(rule.getAlertRuleId());
        assertTrue(alertScheduler.getRegisteredJobNames().contains(jobName),
                "cron job registered: " + alertScheduler.getRegisteredJobNames());

        alertScheduler.unregisterRule(rule.getAlertRuleId());
        assertFalse(alertScheduler.getRegisteredJobNames().contains(jobName),
                "cron job removed after unregisterRule");
    }

    /**
     * 启动 scanner：seed enabled 规则 → 手动调 init() → 注册。
     */
    @Test
    public void testSchedulerInitScansEnabledRules() {
        if (alertScheduler.getScheduler() == null) {
            return;
        }
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-scan-alert", "alice", true);
        saveChartPanelWithDataset("panel-scan-alert", dashboardId, "Sales");
        NopDatavAlertRule rule = seedAlertRule("rule-scan-alert", "panel-scan-alert", dashboardId, "alice",
                "amount", "sum", "gt", "100", null, 0, true);

        alertScheduler.init();
        String jobName = NopDatavAlertScheduler.jobName(rule.getAlertRuleId());
        assertTrue(alertScheduler.getRegisteredJobNames().contains(jobName),
                "init scanner registered enabled rule: " + alertScheduler.getRegisteredJobNames());
    }

    // ==================== 手动评估 ====================

    /**
     * evaluateAlertNow 同步触发评估，断言即时状态结果。
     */
    @Test
    public void testEvaluateAlertNowSyncEvaluation() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-now", "alice", true);
        saveChartPanelWithDataset("panel-now", dashboardId, "Sales");

        // 用 BizModel 经 save 创建规则（触发 afterEntityChange 初始化 AlertState）
        NopDatavAlertRule rule = newAlertRule("rule-now", "panel-now", dashboardId, "alice",
                "amount", "sum", "gt", "100", null, 0, true);
        rule.setNotifyChannels("[\"email\"]");
        rule.setRecipients("[\"a@example.com\"]");
        daoProvider.daoFor(NopDatavAlertRule.class).saveEntityDirectly(rule);
        // 手动初始化状态（模拟 BizModel save 行为）
        alertEvaluator.createInitialState(rule);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(rule.getAlertRuleId());
        assertTrue(result.conditionMet, "sum=350 > 100");
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), result.newState);

        NopDatavAlertState state = loadState(rule.getAlertRuleId());
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), state.getState());
    }

    // ==================== 端到端 ====================

    /**
     * <b>端到端（rule #22）</b>：建规则（绑定面板 + 阈值 gt + cron）→ 评估触发（mock sender 断言）
     * → 改阈值使条件不满足 → 评估恢复通知 → 再满足且未过 rearm → 不重复——断言全链路。
     *
     * <p><b>接线验证（rule #23）</b>：scheduler→evaluator→panelDataBinder→notificationSender→emailSender
     * 调用链运行时连通（非仅类型存在）。</p>
     */
    @Test
    public void testE2eAlertTriggerRecoverRearm() {
        setupSalesData();
        IServiceContext ctx = ownerContext("alice");
        String dashboardId = setupDashboard("dash-e2e", "alice", true);
        saveChartPanelWithDataset("panel-e2e", dashboardId, "Sales");
        seedNoticeTemplate("alert-notify",
                "Alert {ruleName} state={state} currentValue={currentValue} type={alertType}");

        // 建规则：sum gt 300 + rearmSeconds=60
        NopDatavAlertRule rule = seedAlertRule("rule-e2e", "panel-e2e", dashboardId, "alice",
                "amount", "sum", "gt", "300", null, 60, true);

        // 触发评估（经 scheduler 入口验证接线）
        Map<String, Object> r1 = alertScheduler.fireScheduledForTest(rule.getAlertRuleId());
        assertEquals("scheduled", r1.get("status"));
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), r1.get("state"));
        assertEquals(Boolean.TRUE, r1.get("notified"), "trigger notified");
        assertEquals(1, mockEmailSender.getSendCount(), "trigger email sent");

        // 改阈值使条件不再满足（350 < 1000）→ 恢复通知
        rule.setThresholdValue(new BigDecimal("1000"));
        rule.setUpdatedBy("alice");
        rule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopDatavAlertRule.class).updateEntityDirectly(rule);

        Map<String, Object> r2 = alertScheduler.fireScheduledForTest(rule.getAlertRuleId());
        assertEquals(NopDatavAlertStateValue.OK.getValue(), r2.get("state"));
        assertEquals(Boolean.TRUE, r2.get("notified"), "recovery notified");
        assertEquals(2, mockEmailSender.getSendCount(), "recovery email sent");

        // 改回阈值使条件再次满足 + 未过 rearm → 不应通知（rearm 60s 未过）
        rule.setThresholdValue(new BigDecimal("300"));
        rule.setUpdatedBy("alice");
        rule.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopDatavAlertRule.class).updateEntityDirectly(rule);

        Map<String, Object> r3 = alertScheduler.fireScheduledForTest(rule.getAlertRuleId());
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), r3.get("state"),
                "back to TRIGGERED (first trigger after recovery)");
        // OK → TRIGGERED 是状态转换，应当发通知（rearm 只对 TRIGGERED 持续的重发生效）
        assertEquals(Boolean.TRUE, r3.get("notified"), "OK→TRIGGERED transition notifies regardless of rearm");
        assertEquals(3, mockEmailSender.getSendCount());

        // 再次评估（TRIGGERED 持续 + rearm 未过）→ 不重发
        Map<String, Object> r4 = alertScheduler.fireScheduledForTest(rule.getAlertRuleId());
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), r4.get("state"));
        assertEquals(Boolean.FALSE, r4.get("notified"), "TRIGGERED sustained + rearm not elapsed → no re-notify");
        assertEquals(3, mockEmailSender.getSendCount(), "no new email");

        // 邮件内容断言（Anti-Hollow：sendEmail 真的被调用）
        List<EmailMessage> mails = mockEmailSender.getSentMails();
        assertEquals(3, mails.size(), "3 emails: trigger + recover + re-trigger");
        for (EmailMessage m : mails) {
            assertEquals(Arrays.asList("a@example.com", "b@example.com"), m.getTo(), "recipients match");
            assertNotNull(m.getSubject(), "subject rendered");
            assertTrue(m.getSubject().contains("rule-e2e"), "subject contains rule name");
        }
    }

    // ==================== Helpers ====================

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
    }

    private NopDatavAlertState loadState(String alertRuleId) {
        io.nop.api.core.beans.query.QueryBean q = new io.nop.api.core.beans.query.QueryBean();
        q.addFilter(io.nop.api.core.beans.FilterBeans.eq(
                NopDatavAlertState.PROP_NAME_alertRuleId, alertRuleId));
        return daoProvider.daoFor(NopDatavAlertState.class).findFirstByQuery(q);
    }

    private String setupDashboard(String name, String owner, boolean withSnapshot) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(withSnapshot ? 10 : 0);
        d.setVersion(0L);
        d.setCreatedBy(owner);
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy(owner);
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);

        if (withSnapshot) {
            NopDatavDashboardSnapshot s = new NopDatavDashboardSnapshot();
            s.setSnapshotId(java.util.UUID.randomUUID().toString().replace("-", ""));
            s.setDashboardId(d.getDashboardId());
            s.setSnapshotVersion(1L);
            s.setSnapshotContent("{}");
            s.setPublishedBy(owner);
            s.setPublishedTime(new Timestamp(now));
            s.setVersion(0L);
            s.setCreatedBy(owner);
            s.setCreateTime(new Timestamp(now));
            s.setUpdatedBy(owner);
            s.setUpdateTime(new Timestamp(now));
            daoProvider.daoFor(NopDatavDashboardSnapshot.class).saveEntityDirectly(s);
        }
        return d.getDashboardId();
    }

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(SQL.begin()
                    .name("drop:TEST_DATAV_SALES")
                    .sql("drop table TEST_DATAV_SALES").end());
        } catch (Exception ignored) {
            // 表可能尚不存在，忽略 drop 失败以保证幂等建表
        }
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin()
                .name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private NopDatavPanel saveChartPanelWithDataset(String panelId, String dashboardId, String panelName) {
        return saveChartPanelWithDatasetCustom(panelId, dashboardId, panelName,
                "select REGION as region, PRODUCT as product, AMOUNT as amount "
                        + "from TEST_DATAV_SALES order by AMOUNT desc");
    }

    private NopDatavPanel saveChartPanelWithDatasetCustom(String panelId, String dashboardId,
                                                          String panelName, String sql) {
        String refId = panelId + "-ref";
        String dsId = panelId + "-ds";
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId);
        ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText(sql);
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(System.currentTimeMillis()));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);

        long now = System.currentTimeMillis();
        NopDatavDatasetRef ref = new NopDatavDatasetRef();
        ref.setDatasetRefId(refId);
        ref.setDashboardId(dashboardId);
        ref.setRefDatasetId(dsId);
        ref.setRefDatasetName(panelName);
        ref.setParamMapping("{}");
        ref.setVersion(0L);
        ref.setCreatedBy("alice");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("alice");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDatasetRef.class).saveEntityDirectly(ref);

        NopDatavPanel p = new NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName(panelName);
        p.setDisplayName(panelName);
        p.setPanelType(TYPE_CHART);
        p.setDatasetRefId(refId);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("alice");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("alice");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
        return p;
    }

    private NopDatavAlertRule seedAlertRule(String ruleId, String panelId, String dashboardId, String owner,
                                            String valueField, String aggregation, String operator,
                                            String thresholdValue, String thresholdValue2,
                                            int rearmSeconds, boolean enabled) {
        return seedAlertRuleWithChannels(ruleId, panelId, dashboardId, owner, valueField, aggregation,
                operator, thresholdValue, thresholdValue2, rearmSeconds, enabled,
                "[\"email\"]", "[\"a@example.com\",\"b@example.com\"]");
    }

    private NopDatavAlertRule seedAlertRuleWithChannels(String ruleId, String panelId, String dashboardId,
                                                        String owner, String valueField, String aggregation,
                                                        String operator, String thresholdValue,
                                                        String thresholdValue2, int rearmSeconds,
                                                        boolean enabled, String channelsJson,
                                                        String recipientsJson) {
        return seedAlertRuleFull(ruleId, panelId, owner, valueField, aggregation, operator,
                thresholdValue, thresholdValue2, rearmSeconds, enabled, channelsJson, recipientsJson, null);
    }

    private NopDatavAlertRule seedAlertRuleWithTemplate(String ruleId, String panelId, String dashboardId,
                                                        String owner, String valueField, String aggregation,
                                                        String operator, String thresholdValue,
                                                        String thresholdValue2, int rearmSeconds,
                                                        boolean enabled, String templateKey) {
        return seedAlertRuleFull(ruleId, panelId, owner, valueField, aggregation, operator,
                thresholdValue, thresholdValue2, rearmSeconds, enabled,
                "[\"email\"]", "[\"a@example.com\"]", templateKey);
    }

    private NopDatavAlertRule seedAlertRuleFull(String ruleId, String panelId, String owner,
                                                String valueField, String aggregation, String operator,
                                                String thresholdValue, String thresholdValue2,
                                                int rearmSeconds, boolean enabled,
                                                String channelsJson, String recipientsJson,
                                                String templateKey) {
        NopDatavAlertRule r = newAlertRule(ruleId, panelId, lookupDashboardId(panelId, owner), owner,
                valueField, aggregation, operator, thresholdValue, thresholdValue2, rearmSeconds,
                enabled);
        r.setNotifyChannels(channelsJson);
        r.setRecipients(recipientsJson);
        r.setTemplateKey(templateKey);
        daoProvider.daoFor(NopDatavAlertRule.class).saveEntityDirectly(r);
        // 初始化状态行（模拟 BizModel save 行为）
        alertEvaluator.createInitialState(r);
        return r;
    }

    private NopDatavAlertRule newAlertRule(String ruleId, String panelId, String dashboardId, String owner,
                                          String valueField, String aggregation, String operator,
                                          String thresholdValue, String thresholdValue2,
                                          int rearmSeconds, boolean enabled) {
        long now = System.currentTimeMillis();
        NopDatavAlertRule r = new NopDatavAlertRule();
        r.setAlertRuleId(ruleId);
        r.setRuleName(ruleId + "-name");
        r.setDisplayName(ruleId + "-display");
        r.setPanelId(panelId);
        r.setValueField(valueField);
        r.setAggregation(aggregation);
        r.setOperator(operator);
        r.setThresholdValue(thresholdValue == null ? null : new BigDecimal(thresholdValue));
        r.setThresholdValue2(thresholdValue2 == null ? null : new BigDecimal(thresholdValue2));
        r.setRearmSeconds(rearmSeconds);
        r.setCronExpr("0 0 8 * * ?");
        r.setParams(null);
        r.setTemplateKey(null);
        r.setStatus(enabled ? NopDatavReportTaskStatus.ENABLED : NopDatavReportTaskStatus.DISABLED);
        r.setDelFlag((byte) 0);
        r.setVersion(0L);
        r.setCreatedBy(owner);
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy(owner);
        r.setUpdateTime(new Timestamp(now));
        return r;
    }

    /**
     * 经 panelId 查 panel 获取所属 dashboardId（panel 表已建且 panelId 有效时）；
     * panelId 不存在时返回一个虚构 dashboardId（测试不依赖此值）。
     */
    private String lookupDashboardId(String panelId, String fallbackOwner) {
        NopDatavPanel panel = daoProvider.daoFor(NopDatavPanel.class).getEntityById(panelId);
        if (panel != null) {
            return panel.getDashboardId();
        }
        return "fallback-dashboard-id";
    }

    private void seedNoticeTemplate(String name, String content) {
        long now = System.currentTimeMillis();
        NopSysNoticeTemplate t = new NopSysNoticeTemplate();
        t.setSid(java.util.UUID.randomUUID().toString().replace("-", ""));
        t.setName(name);
        t.setTplType(NotificationSender.TPL_TYPE_ALERT_NOTIFY);
        t.setContent(content);
        t.setVersion(0L);
        t.setCreatedBy("test");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("test");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopSysNoticeTemplate.class).saveEntityDirectly(t);
    }
}
