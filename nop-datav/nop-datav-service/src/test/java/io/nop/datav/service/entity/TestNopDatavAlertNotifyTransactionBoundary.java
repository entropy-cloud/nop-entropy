package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.datav.dao.entity.NopDatavAlertRule;
import io.nop.datav.dao.entity.NopDatavAlertState;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.service.alert.AlertEvaluator;
import io.nop.datav.service.alert.NopDatavAlertStateValue;
import io.nop.datav.service.mock.MockEmailSender;
import io.nop.integration.api.email.IEmailSender;
import io.nop.report.dao.entity.NopReportDataset;
import io.nop.sys.dao.entity.NopSysNoticeTemplate;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.math.BigDecimal;
import java.sql.Timestamp;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-06 回归测试（plan 2026-08-15-2146-2 Phase 3）：告警通知事务边界（裁定主案 (a)）。
 *
 * <p>缺陷机制：{@code evaluateAlertNow}（@BizMutation）链路内 {@code AlertEvaluator.evaluate} 的
 * {@code sendAlertNotification} 同步 SMTP/IM 远程发送（典型 30-60s 超时）+ 多次状态写全程运行在
 * 数据库事务内（cron 路径不受影响——BeanMethodJobInvoker 纯反射无事务装饰）。</p>
 *
 * <p><b>修复形态（主案 (a)，落档 schedule-report-design.md）</b>：事务上下文内评估 → interim 状态
 * （TRIGGERED/OK，不含 lastNotifiedTime）事务内写入并提交 → afterCommit 发送通知（远程调用不进
 * 事务）→ 发送成功后独立短事务回写 lastNotifiedTime/lastResolvedTime；发送失败 ERROR 日志且不回写
 * ——rearm 契约保持（lastNotifiedTime 仅在通知成功后写入，失败留 null/旧值待重试）。无事务上下文
 * （cron 路径 / 直调）保持同步发送 + 立即回写（行为与修复前逐条等价，由既有
 * TestNopDatavAlertE2E 套件锚定）。</p>
 *
 * <p>mutate-fail：若回退「事务内同步发送」，事务内 sendCount 断言（==0）确定性失败；若回写不重载
 * 状态行/不落独立短事务，commit 后 lastNotifiedTime 断言失败。</p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/datav/beans/test-report-mock.beans.xml")
@NopTestProperty(name = "nop.datav.report.default-sender", value = "noreply@example.com")
@NopTestProperty(name = "nop.datav.alert.default-subject", value = "Alert: {ruleName}")
public class TestNopDatavAlertNotifyTransactionBoundary extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    AlertEvaluator alertEvaluator;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Inject
    IEmailSender emailSender;

    private MockEmailSender mockEmailSender;

    public TestNopDatavAlertNotifyTransactionBoundary() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Override
    @BeforeEach
    public void init(TestInfo testInfo) {
        super.init(testInfo);
        mockEmailSender = (MockEmailSender) emailSender;
        mockEmailSender.reset();
    }

    // ==================== 主案 (a)：延迟发送 + 成功回写 ====================

    /**
     * 事务上下文内评估（镜像 evaluateAlertNow 的 @BizMutation 事务装饰器）：
     * (a) 事务内（commit 前）通知未发送（sendCount == 0），interim 状态已写入（TRIGGERED，
     *     lastNotifiedTime == null）；
     * (b) commit 后通知发送（sendCount == 1）+ 独立短事务回写 lastNotifiedTime。
     */
    @Test
    public void testNotifyDeferredUntilCommitThenWriteback() {
        String ruleId = seedTriggeringRule("rule-p106-defer", 0);

        transactionTemplate.runInTransaction(txn -> {
            AlertEvaluator.EvalResult result = alertEvaluator.evaluate(ruleId);
            assertTrue(result.conditionMet, "sum=350 > 100");
            assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), result.newState);

            // (a) 事务内：通知未发送（远程调用不进事务）；interim 状态已可见（本事务写入）
            assertEquals(0, mockEmailSender.getSendCount(),
                    "notification must NOT be sent inside the open transaction (P1-06)");
            NopDatavAlertState interim = loadState(ruleId);
            assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), interim.getState(),
                    "interim state written in transaction");
            assertNull(interim.getLastNotifiedTime(),
                    "interim: lastNotifiedTime stays null until notify succeeds (rearm contract)");
            return null;
        });

        // (b) commit 后：通知发送 + 回写提交
        assertEquals(1, mockEmailSender.getSendCount(),
                "afterCommit listener sends the notification after commit");
        NopDatavAlertState after = loadState(ruleId);
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), after.getState());
        assertNotNull(after.getLastNotifiedTime(),
                "lastNotifiedTime written back in independent short transaction after successful notify");
    }

    /**
     * RECOVER 路径对称：事务内 state=OK interim → commit 后恢复通知 → lastResolvedTime 回写。
     */
    @Test
    public void testRecoverNotifyDeferredUntilCommitThenWriteback() {
        String ruleId = seedTriggeringRule("rule-p106-recover", 0);

        // 第一次评估（无事务直调 = cron 语义）：同步触发 + 通知 + lastNotifiedTime 立即写入
        AlertEvaluator.EvalResult first = alertEvaluator.evaluate(ruleId);
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), first.newState);
        assertEquals(1, mockEmailSender.getSendCount());
        assertNotNull(loadState(ruleId).getLastNotifiedTime());

        // 数据变为不再满足条件（删行）→ 第二次评估（事务内）：RECOVER 延迟发送
        jdbcTemplate.executeUpdate(SQL.begin().name("delete:sales-for-recover")
                .sql("delete from TEST_DATAV_SALES_P106 where 1=1").end());

        transactionTemplate.runInTransaction(txn -> {
            AlertEvaluator.EvalResult result = alertEvaluator.evaluate(ruleId);
            assertEquals(NopDatavAlertStateValue.OK.getValue(), result.newState, "recovered to OK");
            assertEquals(1, mockEmailSender.getSendCount(),
                    "recover notification NOT sent inside the open transaction");
            NopDatavAlertState interim = loadState(ruleId);
            assertEquals(NopDatavAlertStateValue.OK.getValue(), interim.getState(),
                    "interim OK state written in transaction");
            return null;
        });

        assertEquals(2, mockEmailSender.getSendCount(), "recover notification sent after commit");
        NopDatavAlertState after = loadState(ruleId);
        assertEquals(NopDatavAlertStateValue.OK.getValue(), after.getState());
        assertNotNull(after.getLastResolvedTime(),
                "lastResolvedTime written back after successful recover notify");
    }

    // ==================== 主案 (a)：发送失败 → rearm 契约（不回写，待重试） ====================

    /**
     * 事务 commit 后通知发送失败（SMTP down）：
     * (a) 异常不传播到调用方（afterCommit listener 由平台 ignoreError 吞掉）但**不静默**——
     *     实现内显式 ERROR 日志锚定（代码级）；
     * (b) lastNotifiedTime 不回写（保持 null）→ rearm 契约：下次评估 shouldRearm(null)=true 立即重试；
     * (c) 重试成功（同步路径，cron 语义）后 lastNotifiedTime 写入。
     */
    @Test
    public void testNotifyFailureAfterCommitKeepsRearmRetryContract() {
        String ruleId = seedTriggeringRule("rule-p106-retry", 60);
        mockEmailSender.setFailOnSend(new io.nop.api.core.exceptions.NopException(
                io.nop.datav.service.NopDatavErrors.ERR_DATAV_ALERT_SENDER_NOT_CONFIGURED)
                .param(io.nop.datav.service.NopDatavErrors.ARG_ALERT_RULE_ID, ruleId));

        // 事务内评估：调用方无异常（发送发生在 commit 后）
        transactionTemplate.runInTransaction(txn -> {
            alertEvaluator.evaluate(ruleId);
            return null;
        });

        assertEquals(0, mockEmailSender.getSendCount(), "failed send not recorded by mock");
        NopDatavAlertState afterFail = loadState(ruleId);
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), afterFail.getState(),
                "interim TRIGGERED committed (objective condition met)");
        assertNull(afterFail.getLastNotifiedTime(),
                "lastNotifiedTime NOT written on notify failure (rearm contract: null → retry)");

        // SMTP 恢复 → 下次评估（无事务直调 = cron 语义）重试成功 → lastNotifiedTime 写入
        mockEmailSender.setFailOnSend(null);
        AlertEvaluator.EvalResult retry = alertEvaluator.evaluate(ruleId);
        assertTrue(retry.notified, "rearm retry (shouldRearm(null)=true) sends notification");
        assertEquals(1, mockEmailSender.getSendCount(), "retry send succeeded");
        NopDatavAlertState afterRetry = loadState(ruleId);
        assertNotNull(afterRetry.getLastNotifiedTime(),
                "lastNotifiedTime written after successful retry (rearm contract closed)");
    }

    // ==================== cron 路径行为不变（守护回归） ====================

    /**
     * 无事务上下文直调（镜像 cron 路径 BeanMethodJobInvoker 纯反射无事务装饰）：
     * 同步发送 + 成功后立即写 lastNotifiedTime（与修复前行为逐条等价——既有
     * TestNopDatavAlertE2E 已锚定主语义，此处锚定「事务模板注入后无事务路径不误入延迟分支」）。
     */
    @Test
    public void testNoTransactionPathStaysSynchronous() {
        String ruleId = seedTriggeringRule("rule-p106-cron", 0);

        AlertEvaluator.EvalResult result = alertEvaluator.evaluate(ruleId);

        assertEquals(1, mockEmailSender.getSendCount(),
                "cron path (no transaction): notification sent synchronously");
        assertTrue(result.notified);
        NopDatavAlertState state = loadState(ruleId);
        assertEquals(NopDatavAlertStateValue.TRIGGERED.getValue(), state.getState());
        assertNotNull(state.getLastNotifiedTime(),
                "cron path: lastNotifiedTime written immediately after synchronous send");
    }

    // ==================== Helpers ====================

    private NopDatavAlertState loadState(String ruleId) {
        return daoProvider.daoFor(NopDatavAlertState.class).findAll().stream()
                .filter(s -> ruleId.equals(s.getAlertRuleId()))
                .findFirst().orElse(null);
    }

    /**
     * 构造一条必然触发（sum=350 > 100）的告警规则（email 渠道 + alert-notify 模板）。
     */
    private String seedTriggeringRule(String ruleId, int rearmSeconds) {
        setupSalesData();
        String dashboardId = setupDashboard("dash-" + ruleId);
        saveChartPanelWithDataset("panel-" + ruleId, dashboardId);
        seedNoticeTemplate();

        long now = System.currentTimeMillis();
        NopDatavAlertRule r = new NopDatavAlertRule();
        r.setAlertRuleId(ruleId);
        r.setRuleName(ruleId + "-name");
        r.setDisplayName(ruleId + "-display");
        r.setPanelId("panel-" + ruleId);
        r.setValueField("amount");
        r.setAggregation("sum");
        r.setOperator("gt");
        r.setThresholdValue(new BigDecimal("100"));
        r.setThresholdValue2(null);
        r.setRearmSeconds(rearmSeconds);
        r.setCronExpr("0 0 8 * * ?");
        r.setParams(null);
        r.setTemplateKey(null);
        r.setNotifyChannels("[\"email\"]");
        r.setRecipients("[\"a@example.com\"]");
        r.setStatus(io.nop.datav.service.report.NopDatavReportTaskStatus.ENABLED);
        r.setDelFlag((byte) 0);
        r.setVersion(0L);
        r.setCreatedBy("alice");
        r.setCreateTime(new Timestamp(now));
        r.setUpdatedBy("alice");
        r.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavAlertRule.class).saveEntityDirectly(r);
        alertEvaluator.createInitialState(r);
        return ruleId;
    }

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(SQL.begin().name("drop:TEST_DATAV_SALES_P106")
                    .sql("drop table TEST_DATAV_SALES_P106").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
        jdbcTemplate.executeUpdate(SQL.begin().name("create:TEST_DATAV_SALES_P106")
                .sql("create table TEST_DATAV_SALES_P106(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin().name("insert:TEST_DATAV_SALES_P106")
                .sql("insert into TEST_DATAV_SALES_P106(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private String setupDashboard(String name) {
        long now = System.currentTimeMillis();
        NopDatavDashboard d = new NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavDashboard.class).saveEntityDirectly(d);
        return d.getDashboardId();
    }

    private void saveChartPanelWithDataset(String panelId, String dashboardId) {
        String refId = panelId + "-ref";
        String dsId = panelId + "-ds";
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId);
        ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount "
                + "from TEST_DATAV_SALES_P106");
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
        ref.setRefDatasetName(panelId);
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
        p.setPanelName(panelId);
        p.setDisplayName(panelId);
        p.setPanelType(TYPE_CHART);
        p.setDatasetRefId(refId);
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("alice");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("alice");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavPanel.class).saveEntityDirectly(p);
    }

    private void seedNoticeTemplate() {
        long now = System.currentTimeMillis();
        NopSysNoticeTemplate t = new NopSysNoticeTemplate();
        t.setSid(java.util.UUID.randomUUID().toString().replace("-", ""));
        t.setName("alert-notify");
        t.setTplType(io.nop.datav.service.report.NotificationSender.TPL_TYPE_ALERT_NOTIFY);
        t.setContent("Alert {ruleName} triggered at {triggerTime}, current value {currentValue}");
        t.setVersion(0L);
        t.setCreatedBy("test");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("test");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopSysNoticeTemplate.class).saveEntityDirectly(t);
    }
}
