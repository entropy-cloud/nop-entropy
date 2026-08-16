package io.nop.datav.service.entity;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.auth.core.login.UserContextImpl;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.txn.ITransactionTemplate;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.datav.dao.entity.NopDatavDashboard;
import io.nop.datav.dao.entity.NopDatavDatasetRef;
import io.nop.datav.dao.entity.NopDatavExportTask;
import io.nop.datav.dao.entity.NopDatavPanel;
import io.nop.datav.dao.entity.NopDatavReportDelivery;
import io.nop.datav.dao.entity.NopDatavReportTask;
import io.nop.datav.service.export.NopDatavExportTaskStatus;
import io.nop.datav.service.report.NopDatavReportDeliveryStatus;
import io.nop.datav.service.report.NopDatavReportTaskStatus;
import io.nop.datav.service.report.NopDatavReportTriggerSource;
import io.nop.datav.service.report.ReportDeliveryExecutor;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static io.nop.datav.service.component.PanelTypeMapping.TYPE_CHART;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-2 回归测试（plan 2026-08-15-2146-2 Phase 2）：异步提交时序与可观测性。
 *
 * <p>缺陷机制：{@code createExportTask}（@BizMutation）在事务提交前 INSERT pending 任务后立即
 * {@code submitExecution} 提交到 {@code GlobalExecutors.globalWorker()}；worker 新 session 在
 * READ_COMMITTED 下读不到未提交行 → {@code task == null → return null} 零日志静默 no-op →
 * 导出永停 PENDING / 报告交付静默不执行。{@code ReportDeliveryExecutor} 同模式。</p>
 *
 * <p><b>测试设计（plan Phase 2 Proof 裁定：选项 (a) afterCommit 注册语义断言 + (c) 无事务守护分支 组合）</b>：
 * 「可见性」本身无法构成缺陷锚定（修复前单机 H2 下 worker 重试也可能凑巧读到行），故采用可观察口径——
 * 经 {@code submitExecutionInvokedCount} seam 断言注册与触发时序：
 * <ul>
 *   <li>(a) 事务上下文内经 {@link ITransactionTemplate#runInTransaction} 直调 biz 方法：
 *       事务内（commit 前）seam 计数必须为 0（仅注册未触发）；commit 返回后计数 +1（afterCommit 触发）；
 *       回滚（事务内抛错）后计数保持不变（onAfterCommit 不触发——回滚则不提交异步任务，语义恰好正确）。</li>
 *   <li>(c) 无事务上下文（直调裸 bean）不抛 {@code ERR_TXN_NOT_IN_TRANSACTION} 且立即提交（计数 +1），
 *       任务正常推进到终态（cron/恢复路径行为不变的守护分支锚定）。</li>
 * </ul>
 * 另含一条 <b>graphQLEngine mutation 真实事务装饰器路径 + 持久层副作用断言</b> 的常驻 E2E
 * （plan Closure Gates：AR-1 之外的 AR-2 资产）：经 {@link IGraphQLEngine} mutation 调用
 * {@code createExportTask}，返回后轮询裸 JDBC 断言任务行必然被 worker 消费至 SUCCEEDED
 * （worker 只可能消费已 commit 的行——mutation 返回即事务已提交）。</p>
 *
 * <p>mutate-fail：若回退为「事务内立即 submitExecution」，事务内 seam 计数为 1 → 首组断言确定性失败；
 * 若移除 {@code isTransactionOpened} 守护（无事务时也注册 listener），直调路径抛
 * ERR_TXN_NOT_IN_TRANSACTION → (c) 断言失败。</p>
 */
@io.nop.api.core.annotations.autotest.NopTestProperty(name = "nop.file.store-dir", value = "target/test-file-store")
public class TestNopDatavAsyncSubmitTransactionPath extends AbstractNopDatavTest {

    private static final long POLL_TIMEOUT_MS = 30_000L;
    private static final long POLL_INTERVAL_MS = 100L;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    ITransactionTemplate transactionTemplate;

    @Inject
    NopDatavExportTaskBizModel exportBizModel;

    @Inject
    ReportDeliveryExecutor reportDeliveryExecutor;

    @Inject
    IGraphQLEngine graphQLEngine;

    @AfterEach
    public void clearUserContext() {
        IUserContext.set(null);
        io.nop.api.core.context.IContext ctx = ContextProvider.currentContext();
        if (ctx != null) {
            ctx.setUserName(null);
        }
    }

    // ==================== (a) afterCommit 注册语义：导出 ====================

    /**
     * 事务上下文内 createExportTask：commit 前 worker 提交仅注册未触发（计数 0），
     * commit 返回后触发（计数 +1），任务行被 worker 消费至 SUCCEEDED。
     */
    @Test
    public void testExportSubmitDeferredUntilCommitInsideTransaction() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-ar2-commit");
        saveChartPanelWithDataset("panel-ar2-commit", dashboardId, "Sales");
        IServiceContext ctx = ownerContext("alice");

        int before = exportBizModel.getSubmitExecutionInvokedCountForTest();
        int[] insideTxn = {-1};
        AtomicReference<String> taskIdRef = new AtomicReference<>();

        transactionTemplate.runInTransaction(txn -> {
            NopDatavExportTask task = exportBizModel.createExportTask(
                    "panel", "panel-ar2-commit", "csv", Map.of("region", "north"), ctx);
            taskIdRef.set(task.getTaskId());
            // commit 尚未发生：submitExecution 必须仅注册而未触发
            insideTxn[0] = exportBizModel.getSubmitExecutionInvokedCountForTest() - before;
            return null;
        });

        assertEquals(0, insideTxn[0], "inside open transaction: submitExecution must be registered but NOT invoked");
        assertEquals(1, exportBizModel.getSubmitExecutionInvokedCountForTest() - before,
                "after commit: afterCommit listener must have invoked submitExecution exactly once");

        NopDatavExportTask done = pollUntilTerminal(taskIdRef.get());
        assertEquals(NopDatavExportTaskStatus.SUCCEEDED, done.getStatus(),
                "worker consumes the committed pending row to SUCCEEDED, errorMsg=" + done.getErrorMsg());
    }

    /**
     * 事务回滚：onAfterCommit 不触发 → worker 提交计数不变，且任务行随事务回滚不存在
     * （无孤儿异步任务）。
     */
    @Test
    public void testExportSubmitNotTriggeredOnRollback() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-ar2-rollback");
        saveChartPanelWithDataset("panel-ar2-rollback", dashboardId, "Sales");
        IServiceContext ctx = ownerContext("alice");

        int before = exportBizModel.getSubmitExecutionInvokedCountForTest();
        AtomicReference<String> taskIdRef = new AtomicReference<>();

        assertThrows(NopException.class, () ->
                transactionTemplate.runInTransaction(txn -> {
                    NopDatavExportTask task = exportBizModel.createExportTask(
                            "panel", "panel-ar2-rollback", "csv", null, ctx);
                    taskIdRef.set(task.getTaskId());
                    throw new NopException(ERR_FORCED_ROLLBACK);
                }));

        assertEquals(before, exportBizModel.getSubmitExecutionInvokedCountForTest(),
                "rollback: onAfterCommit must NOT fire, submitExecution stays un-invoked");
        assertNull(daoProvider.daoFor(NopDatavExportTask.class).getEntityById(taskIdRef.get()),
                "task row is rolled back with the transaction (no pending orphan row)");
    }

    // ==================== (c) 无事务守护分支：导出 ====================

    /**
     * 无事务上下文直调（镜像 cron/恢复/裸调用路径）：不抛 ERR_TXN_NOT_IN_TRANSACTION，
     * 立即提交（计数 +1），任务正常推进至终态。
     */
    @Test
    public void testExportSubmitImmediateWithoutTransaction() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-ar2-notxn");
        saveChartPanelWithDataset("panel-ar2-notxn", dashboardId, "Sales");
        IServiceContext ctx = ownerContext("alice");

        int before = exportBizModel.getSubmitExecutionInvokedCountForTest();

        NopDatavExportTask task = exportBizModel.createExportTask(
                "panel", "panel-ar2-notxn", "csv", Map.of("region", "north"), ctx);

        assertEquals(1, exportBizModel.getSubmitExecutionInvokedCountForTest() - before,
                "no transaction context: submitExecution must be invoked immediately (guard branch)");
        NopDatavExportTask done = pollUntilTerminal(task.getTaskId());
        assertEquals(NopDatavExportTaskStatus.SUCCEEDED, done.getStatus(),
                "no-tx path task progresses normally, errorMsg=" + done.getErrorMsg());
    }

    // ==================== (a) afterCommit 注册语义：报告交付 ====================

    /**
     * 事务上下文内 triggerReportNow 路径（ReportDeliveryExecutor.execute）：
     * commit 前仅注册未触发，commit 后触发。交付行被 worker 消费（走 FAILED——
     * 看板未发布，证明 worker 读到了已提交行而非静默 no-op）。
     */
    @Test
    public void testReportSubmitDeferredUntilCommitInsideTransaction() {
        seedReportTask("rt-ar2-commit", "dash-missing-ar2");
        int before = reportDeliveryExecutor.getSubmitExecutionInvokedCountForTest();
        int[] insideTxn = {-1};
        AtomicReference<String> deliveryIdRef = new AtomicReference<>();

        transactionTemplate.runInTransaction(txn -> {
            String deliveryId = reportDeliveryExecutor.execute(
                    "rt-ar2-commit", NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
            deliveryIdRef.set(deliveryId);
            insideTxn[0] = reportDeliveryExecutor.getSubmitExecutionInvokedCountForTest() - before;
            return null;
        });

        assertEquals(0, insideTxn[0], "inside open transaction: report worker submit must be registered but NOT invoked");
        assertEquals(1, reportDeliveryExecutor.getSubmitExecutionInvokedCountForTest() - before,
                "after commit: afterCommit listener must have invoked report worker submit exactly once");

        NopDatavReportDelivery done = pollDeliveryUntilTerminal(deliveryIdRef.get());
        assertTrue(done.getStatus() == NopDatavReportDeliveryStatus.FAILED
                        || done.getStatus() == NopDatavReportDeliveryStatus.SUCCEEDED,
                "worker consumed the committed delivery row (terminal reached, not stuck PENDING), status="
                        + done.getStatus());
    }

    /**
     * 事务回滚：报告 worker 提交不触发，交付行随事务回滚不存在。
     */
    @Test
    public void testReportSubmitNotTriggeredOnRollback() {
        seedReportTask("rt-ar2-rollback", "dash-missing-ar2-rollback");
        int before = reportDeliveryExecutor.getSubmitExecutionInvokedCountForTest();
        AtomicReference<String> deliveryIdRef = new AtomicReference<>();

        assertThrows(NopException.class, () ->
                transactionTemplate.runInTransaction(txn -> {
                    String deliveryId = reportDeliveryExecutor.execute(
                            "rt-ar2-rollback", NopDatavReportTriggerSource.MANUAL, System.currentTimeMillis());
                    deliveryIdRef.set(deliveryId);
                    throw new NopException(ERR_FORCED_ROLLBACK);
                }));

        assertEquals(before, reportDeliveryExecutor.getSubmitExecutionInvokedCountForTest(),
                "rollback: onAfterCommit must NOT fire for report worker submit");
        assertNull(daoProvider.daoFor(NopDatavReportDelivery.class).getEntityById(deliveryIdRef.get()),
                "delivery row is rolled back with the transaction (no pending orphan row)");
    }

    // ==================== graphQLEngine mutation 真实事务路径 E2E（Closure Gate AR-2 资产） ====================

    /**
     * 经 {@link IGraphQLEngine} 以 mutation 调用 {@code createExportTask}（真实事务装饰器路径），
     * mutation 返回即事务已提交；随后裸 JDBC 轮询断言 worker 消费已提交行至 SUCCEEDED
     * （持久层副作用断言，不经 ORM session 缓存）。
     */
    @Test
    public void testExportMutationViaGraphQLEngineWorkerConsumesCommittedRow() {
        setupSalesData();
        String dashboardId = setupDashboard("dash-ar2-graphql");
        saveChartPanelWithDataset("panel-ar2-graphql", dashboardId, "Sales");

        setUserContext("alice");
        ApiResponse<?> resp = executeCreateExportTaskMutation("panel-ar2-graphql", "csv");
        assertEquals(0, resp.getStatus(), "createExportTask mutation should succeed: " + resp);

        // 裸 JDBC：mutation 返回后 pending 行必然已提交可见（事务装饰器 commit 先于返回）
        String taskId = readSingleTaskIdRaw();
        assertNotNull(taskId, "committed pending row visible via raw JDBC after mutation returns");
        int status = readTaskStatusRaw(taskId);
        assertTrue(status == NopDatavExportTaskStatus.PENDING || status == NopDatavExportTaskStatus.RUNNING
                        || NopDatavExportTaskStatus.isTerminal(status),
                "task row visible with plausible status, got " + status);

        // worker 消费至终态
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        do {
            status = readTaskStatusRaw(taskId);
            if (NopDatavExportTaskStatus.isTerminal(status)) {
                break;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
            }
        } while (System.currentTimeMillis() < deadline);
        assertEquals(NopDatavExportTaskStatus.SUCCEEDED, status,
                "worker must consume the committed row to SUCCEEDED (AR-2: no eternal PENDING)");
    }

    // ==================== Helpers ====================

    private static final io.nop.api.core.exceptions.ErrorCode ERR_FORCED_ROLLBACK =
            io.nop.api.core.exceptions.ErrorCode.define("nop.err.datav.test-forced-rollback",
                    "test forced rollback");

    private ApiResponse<?> executeCreateExportTaskMutation(String panelId, String format) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("sourceType", "panel");
        data.put("sourceId", panelId);
        data.put("format", format);
        data.put("params", Map.of("region", "north"));
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext context = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, "NopDatavExportTask__createExportTask", request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(context));
    }

    private String readSingleTaskIdRaw() {
        return jdbcTemplate.executeQuery(SQL.begin().name("readExportTaskId")
                        .sql("select TASK_ID from NOP_DATAV_EXPORT_TASK").end(),
                rs -> rs.hasNext() ? (String) rs.next().getObject(0) : null);
    }

    private int readTaskStatusRaw(String taskId) {
        Integer status = jdbcTemplate.executeQuery(SQL.begin().name("readExportTaskStatus")
                        .sql("select STATUS from NOP_DATAV_EXPORT_TASK where TASK_ID=").param(taskId).end(),
                rs -> rs.hasNext() ? (Integer) rs.next().getObject(0) : null);
        assertNotNull(status, "task row must exist: " + taskId);
        return status;
    }

    private NopDatavExportTask pollUntilTerminal(String taskId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            NopDatavExportTask task = daoProvider.daoFor(NopDatavExportTask.class).getEntityById(taskId);
            if (task != null && NopDatavExportTaskStatus.isTerminal(task.getStatus())) {
                return task;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
            }
        }
        throw new AssertionError("task " + taskId + " did not reach terminal state within " + POLL_TIMEOUT_MS + "ms");
    }

    private NopDatavReportDelivery pollDeliveryUntilTerminal(String deliveryId) {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            NopDatavReportDelivery delivery = daoProvider.daoFor(NopDatavReportDelivery.class)
                    .getEntityById(deliveryId);
            if (delivery != null && NopDatavReportDeliveryStatus.isTerminal(delivery.getStatus())) {
                return delivery;
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw NopException.adapt(e);
            }
        }
        throw new AssertionError("delivery " + deliveryId + " did not reach terminal state within "
                + POLL_TIMEOUT_MS + "ms");
    }

    private void setUserContext(String userName) {
        UserContextImpl userContext = new UserContextImpl();
        userContext.setUserId(userName);
        userContext.setUserName(userName);
        userContext.setRoles(CollectionHelper.buildImmutableSet("admin"));
        IUserContext.set(userContext);
        ContextProvider.getOrCreateContext().setUserName(userName);
    }

    private IServiceContext ownerContext(String userName) {
        ServiceContextImpl context = new ServiceContextImpl();
        context.setContext(new io.nop.api.core.context.TenantProxyContext(context.getContext()));
        context.getContext().setUserName(userName);
        return context;
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

    private void setupSalesData() {
        try {
            jdbcTemplate.executeUpdate(SQL.begin().name("drop:TEST_DATAV_SALES")
                    .sql("drop table TEST_DATAV_SALES").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
        jdbcTemplate.executeUpdate(SQL.begin().name("create:TEST_DATAV_SALES")
                .sql("create table TEST_DATAV_SALES(REGION varchar(50), PRODUCT varchar(50), AMOUNT int)")
                .end());
        insertSalesRow("north", "widget", 100);
        insertSalesRow("north", "gadget", 200);
        insertSalesRow("south", "widget", 50);
    }

    private void insertSalesRow(String region, String product, int amount) {
        jdbcTemplate.executeUpdate(SQL.begin().name("insert:TEST_DATAV_SALES")
                .sql("insert into TEST_DATAV_SALES(REGION, PRODUCT, AMOUNT) values(")
                .param(region).sql(",").param(product).sql(",").param(amount).sql(")")
                .end());
    }

    private void saveChartPanelWithDataset(String panelId, String dashboardId, String panelName) {
        String refId = panelId + "-ref";
        String dsId = panelId + "-ds";
        NopReportDataset ds = new NopReportDataset();
        ds.setSid(dsId);
        ds.setDsName(dsId);
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select REGION as region, PRODUCT as product, AMOUNT as amount "
                + "from TEST_DATAV_SALES where REGION = ${region} order by AMOUNT desc");
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
        ref.setParamMapping(JsonTool.stringify(Map.of(
                "region", Map.of("source", "region", "defaultValue", "south"))));
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
    }

    private void seedReportTask(String taskId, String dashboardId) {
        long now = System.currentTimeMillis();
        NopDatavReportTask t = new NopDatavReportTask();
        t.setReportTaskId(taskId);
        t.setTaskName(taskId + "-name");
        t.setDisplayName(taskId + "-display");
        t.setDashboardId(dashboardId);
        t.setCronExpr("0 0 8 * * ?");
        t.setFormat("xlsx");
        t.setRecipients("[\"a@example.com\"]");
        t.setNotifyChannels("[\"email\"]");
        t.setParams(null);
        t.setStatus(NopDatavReportTaskStatus.ENABLED);
        t.setGraceMinutes(60);
        t.setTemplateKey(null);
        t.setDelFlag((byte) 0);
        t.setVersion(0L);
        t.setCreatedBy("alice");
        t.setCreateTime(new Timestamp(now));
        t.setUpdatedBy("alice");
        t.setUpdateTime(new Timestamp(now));
        daoProvider.daoFor(NopDatavReportTask.class).saveEntityDirectly(t);
    }
}
