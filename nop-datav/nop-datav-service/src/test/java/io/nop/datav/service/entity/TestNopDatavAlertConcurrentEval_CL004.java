package io.nop.datav.service.entity;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.datav.service.alert.AlertEvaluator;
import io.nop.datav.service.mock.MockEmailSender;
import io.nop.dao.api.IDaoProvider;
import io.nop.integration.api.email.IEmailSender;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CL-004 告警状态行并发评估红⑥钉（plan 2526 裁定⑧）。
 *
 * <p>双入口（cron 同步路径语义）barrier 并发评估同一必然触发规则：per-ruleId 互斥使
 * 「判定+通知+回写」线性化——单冷静期窗口（rearmSeconds=60）至多一次通知（sendCount==1）
 * 且状态行唯一（loadOrCreateState 建行竞态闭合）。缺陷态（无互斥）双线程在 barrier 窗口内
 * 均读旧值决策 → 双通知（压力构造红，plan 预声明形态；Dim14-03 的 shouldRearm(null)=true
 * 首通知分支使双发在「TRIGGERED+null」与「OK→TRIGGERED」两条路径下均成立，红稳定）。</p>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = io.nop.api.core.annotations.core.OptionalBoolean.TRUE,
        testBeansFile = "/nop/datav/beans/test-report-mock.beans.xml")
@NopTestProperty(name = "nop.datav.report.default-sender", value = "noreply@example.com")
public class TestNopDatavAlertConcurrentEval_CL004 extends AbstractNopDatavTest {

    @Inject
    IDaoProvider daoProvider;

    @Inject
    AlertEvaluator alertEvaluator;

    @Inject
    IEmailSender emailSender;

    private MockEmailSender mockEmailSender;

    public TestNopDatavAlertConcurrentEval_CL004() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Override
    @org.junit.jupiter.api.BeforeEach
    public void init(org.junit.jupiter.api.TestInfo testInfo) {
        super.init(testInfo);
        mockEmailSender = (MockEmailSender) emailSender;
        mockEmailSender.reset();
    }

    @Test
    public void testConcurrentEvaluateSendsAtMostOneNotificationPerCooldown() throws Exception {
        String ruleId = seedTriggeringRule("rule-cl004", 60);

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] workers = new Thread[2];
        for (int t = 0; t < 2; t++) {
            workers[t] = new Thread(() -> {
                try {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    alertEvaluator.evaluate(ruleId);
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "cl004-eval-" + t);
            workers[t].start();
        }
        for (Thread worker : workers) {
            worker.join(60_000);
            assertEquals(false, worker.isAlive(), "evaluate worker must finish in time");
        }
        if (failure.get() != null) {
            throw new AssertionError("concurrent evaluate failed", failure.get());
        }

        assertEquals(1, mockEmailSender.getSendCount(),
                "single cooldown window must produce at most one notification under concurrent entry");
        assertEquals(1, countStateRows(ruleId),
                "loadOrCreateState race must yield exactly one state row");
        assertNotNull(loadState(ruleId).getLastNotifiedTime(),
                "winner's writeback persisted (lastNotifiedTime set)");
    }

    // ==================== Helpers（沿 TestNopDatavAlertNotifyTransactionBoundary harness） ====================

    private long countStateRows(String ruleId) {
        return daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavAlertState.class).findAll().stream()
                .filter(s -> ruleId.equals(s.getAlertRuleId()))
                .count();
    }

    private io.nop.datav.dao.entity.NopDatavAlertState loadState(String ruleId) {
        return daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavAlertState.class).findAll().stream()
                .filter(s -> ruleId.equals(s.getAlertRuleId()))
                .findFirst().orElse(null);
    }

    private String seedTriggeringRule(String ruleId, int rearmSeconds) {
        setupSalesData();
        String dashboardId = setupDashboard("dash-" + ruleId);
        saveChartPanelWithDataset("panel-" + ruleId, dashboardId);
        seedNoticeTemplate();

        long now = System.currentTimeMillis();
        io.nop.datav.dao.entity.NopDatavAlertRule r = new io.nop.datav.dao.entity.NopDatavAlertRule();
        r.setAlertRuleId(ruleId);
        r.setRuleName(ruleId + "-name");
        r.setDisplayName(ruleId + "-display");
        r.setPanelId("panel-" + ruleId);
        r.setValueField("amount");
        r.setAggregation("sum");
        r.setOperator("gt");
        r.setThresholdValue(new java.math.BigDecimal("100"));
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
        daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavAlertRule.class).saveEntityDirectly(r);
        alertEvaluator.createInitialState(r);
        return ruleId;
    }

    private void seedNoticeTemplate() {
        io.nop.sys.dao.entity.NopSysNoticeTemplate t = new io.nop.sys.dao.entity.NopSysNoticeTemplate();
        t.setSid(java.util.UUID.randomUUID().toString().replace("-", ""));
        t.setName("alert-notify");
        t.setTplType(io.nop.datav.service.report.NotificationSender.TPL_TYPE_ALERT_NOTIFY);
        t.setContent("Alert {ruleName} triggered at {triggerTime}, current value {currentValue}");
        t.setVersion(0L);
        t.setCreatedBy("test");
        t.setCreateTime(new Timestamp(System.currentTimeMillis()));
        t.setUpdatedBy("test");
        t.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(io.nop.sys.dao.entity.NopSysNoticeTemplate.class).saveEntityDirectly(t);
    }

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                    .name("drop:TEST_DATAV_SALES_CL004")
                    .sql("drop table TEST_DATAV_SALES_CL004").end());
        } catch (Exception ignored) {
            // 表不存在（首跑）时 drop 失败属预期——建表语句紧随其后
        }
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("create:TEST_DATAV_SALES_CL004")
                .sql("create table TEST_DATAV_SALES_CL004(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin()
                .name("insert:TEST_DATAV_SALES_CL004")
                .sql("insert into TEST_DATAV_SALES_CL004(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private String setupDashboard(String name) {
        long now = System.currentTimeMillis();
        io.nop.datav.dao.entity.NopDatavDashboard d = new io.nop.datav.dao.entity.NopDatavDashboard();
        d.setDashboardName(name);
        d.setDisplayName(name);
        d.setPublishStatus(0);
        d.setVersion(0L);
        d.setCreatedBy("alice");
        d.setCreateTime(new Timestamp(now));
        d.setUpdatedBy("alice");
        d.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavDashboard.class).saveEntityDirectly(d);
        return d.getDashboardId();
    }

    private void saveChartPanelWithDataset(String panelId, String dashboardId) {
        io.nop.report.dao.entity.NopReportDataset ds = new io.nop.report.dao.entity.NopReportDataset();
        ds.setSid(panelId + "-ds");
        ds.setDsName(panelId + "-ds");
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount from TEST_DATAV_SALES_CL004");
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(System.currentTimeMillis()));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(io.nop.report.dao.entity.NopReportDataset.class).saveEntityDirectly(ds);

        long now = System.currentTimeMillis();
        io.nop.datav.dao.entity.NopDatavDatasetRef ref = new io.nop.datav.dao.entity.NopDatavDatasetRef();
        ref.setDatasetRefId(panelId + "-ref");
        ref.setDashboardId(dashboardId);
        ref.setRefDatasetId(panelId + "-ds");
        ref.setRefDatasetName("cl004");
        ref.setParamMapping(io.nop.core.lang.json.JsonTool.stringify(java.util.Map.of()));
        ref.setVersion(0L);
        ref.setCreatedBy("alice");
        ref.setCreateTime(new Timestamp(now));
        ref.setUpdatedBy("alice");
        ref.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavDatasetRef.class).saveEntityDirectly(ref);

        io.nop.datav.dao.entity.NopDatavPanel p = new io.nop.datav.dao.entity.NopDatavPanel();
        p.setPanelId(panelId);
        p.setDashboardId(dashboardId);
        p.setPanelName("cl004");
        p.setDisplayName("cl004");
        p.setPanelType(io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART);
        p.setDatasetRefId(panelId + "-ref");
        p.setSortOrder(0);
        p.setVersion(0L);
        p.setCreatedBy("alice");
        p.setCreateTime(new Timestamp(now));
        p.setUpdatedBy("alice");
        p.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(io.nop.datav.dao.entity.NopDatavPanel.class).saveEntityDirectly(p);
    }
}
