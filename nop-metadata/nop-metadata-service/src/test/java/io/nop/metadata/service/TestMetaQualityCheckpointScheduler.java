/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.metadata.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.job.api.IJobScheduler;
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaQualityCheckpoint;
import io.nop.metadata.dao.entity.NopMetaQualityResult;
import io.nop.metadata.dao.entity.NopMetaQualityRule;
import io.nop.metadata.dao.entity.NopMetaQualityScore;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.quality.MetaQualityCheckpointScheduler;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证质量检查点 cron 定时调度（架构基线 §2.7.3.1，plan 2026-07-17-1308-1）：
 * 经 nop-job 动态调度路径（{@link IJobScheduler#addJob}）注册 cron job，用 {@link IJobScheduler#fireNow}
 * 同步立即触发，完整走 scheduler → invoker → 包装方法 → BizModel → executor 编排链（非 hollow）。
 *
 * <p><b>Anti-Hollow 端到端验证</b>（Minimum Rules #22/#23）：从 cron 触发入口（fireNow）到结果落盘
 * （NopMetaQualityResult + NopMetaQualityScore 行）的完整路径有测试覆盖；新增调度组件
 * （MetaQualityCheckpointScheduler）在运行时确实调用 executeCheckpoint 编排链（断言 QualityResult 行存在，
 * 证明调用链连通）。
 *
 * <p>覆盖路径：
 * <ul>
 *   <li>(a) 成功触发：注册 cron job → fireNow → QualityResult + QualityScore 行落盘</li>
 *   <li>(b) 非 ACTIVE 状态：registerCheckpoint 不注册（fireNow 返回 false）</li>
 *   <li>(c) 空 cron：registerCheckpoint 不注册</li>
 *   <li>(d) 未知 checkpoint：fireNow 对未注册 job 返回 false</li>
 *   <li>(e) 启动 scanner：init 后无 ACTIVE 检查点时注册 0 个 job（不抛崩）</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        testBeansFile = "/nop/metadata/beans/test-mock.beans.xml")
public class TestMetaQualityCheckpointScheduler extends JunitBaseTestCase {

    public TestMetaQualityCheckpointScheduler() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IDaoProvider daoProvider;

    @Inject
    MetaQualityCheckpointScheduler checkpointScheduler;

    @Inject
    IGraphQLEngine graphQLEngine;

    // ===== (a) 成功触发：cron job 注册 → fireNow → 结果落盘 =====

    /**
     * (a) 端到端 + 接线验证：保存检查点（extConfig.schedule=cron + ACTIVE）+ 规则 + 外部表 →
     * registerCheckpoint 注册 cron job → fireNow 同步触发 → 断言：
     * <ul>
     *   <li>job 已注册（getJobNames 含 jobName）</li>
     *   <li>fireNow 返回 true（job 存在且执行）</li>
     *   <li>NopMetaQualityResult 行落盘（证明 executeScheduledCheckpoint → executeCheckpoint → executor 编排链连通）</li>
     *   <li>NopMetaQualityScore 行落盘（证明 autoScore 接线在 cron 路径自动生效）</li>
     * </ul>
     *
     * <p>关键 Anti-Hollow 断言：QualityResult 行的存在证明运行时调用链
     * {@code fireNow → BeanMethodJobInvoker → executeScheduledCheckpoint → executeCheckpoint
     * → MetaQualityCheckpointExecutor.execute → judge → QualityResultWriter} 完整连通，非空壳。
     */
    @Test
    public void testCronJobFireNowWritesResultsAndScores() throws Exception {
        String dbUrl = "jdbc:h2:mem:meta_cron_e2e;DB_CLOSE_DELAY=-1";
        seedTable(dbUrl, "CREATE TABLE ext_cron (id INT NOT NULL)",
                "INSERT INTO ext_cron VALUES (1)", "INSERT INTO ext_cron VALUES (2)");
        PreparedEnv env = prepare(dbUrl, "qs_cron_e2e");
        String tableId = env.tableId("EXT_CRON");

        saveRule("r-cron-vol", "volume", "table", tableId, null, null, "{\"minRows\":1}"); // PASS
        // checkpoint: ACTIVE + extConfig.schedule=cron
        saveCheckpointWithSchedule("cp-cron", "ACTIVE",
                "[{\"tableIds\":[\"" + tableId + "\"]}]", null, "0 0/5 * * * ?");

        // 运行时增量注册（D4）——模拟 save override 调用
        checkpointScheduler.registerCheckpoint("cp-cron");

        String jobName = MetaQualityCheckpointScheduler.jobName("cp-cron");
        IJobScheduler scheduler = checkpointScheduler.getScheduler();
        assertNotNull(scheduler, "IJobScheduler must be injected in test (nop-job-local test-scope)");
        assertTrue(scheduler.getJobNames().contains(jobName),
                "cron job must be registered after registerCheckpoint: " + scheduler.getJobNames());

        long resultsBefore = countResults("r-cron-vol");
        long scoresBefore = countScores(tableId);
        assertEquals(0, resultsBefore, "no result before fireNow");
        assertEquals(0, scoresBefore, "no score before fireNow");

        // D5 测试触发机制：fireNow 同步立即触发（不绕过 cron/invoker 调用链）
        boolean fired = scheduler.fireNow(jobName);
        assertTrue(fired, "fireNow must return true for registered idle job");

        // fireNow 对同步方法（返回 Map 非 CompletionStage）同步完成，结果立即可见
        // QualityResult 行落盘（编排链连通的铁证）
        assertEquals(1, countResults("r-cron-vol"),
                "fireNow must trigger executeCheckpoint chain → QualityResult written (anti-hollow)");
        // autoScore 在 cron 路径自动生效（评分逻辑与触发源解耦）
        assertEquals(1, countScores(tableId),
                "autoScore must trigger in cron path → QualityScore written");
    }

    // ===== (b) 非 ACTIVE 状态：registerCheckpoint 不注册 =====

    /** status=PAUSED 的检查点：registerCheckpoint 不注册 cron job（fireNow 返回 false）。 */
    @Test
    public void testPausedCheckpointNotRegistered() {
        saveCheckpointWithSchedule("cp-paused-cron", "PAUSED",
                "[{\"ruleIds\":[\"__any__\"]}]", null, "0 0/5 * * * ?");
        checkpointScheduler.registerCheckpoint("cp-paused-cron");

        IJobScheduler scheduler = checkpointScheduler.getScheduler();
        String jobName = MetaQualityCheckpointScheduler.jobName("cp-paused-cron");
        assertFalse(scheduler.getJobNames().contains(jobName),
                "PAUSED checkpoint must not be registered: " + scheduler.getJobNames());
        assertFalse(scheduler.fireNow(jobName),
                "fireNow on non-registered job must return false");
    }

    // ===== (c) 空 cron：registerCheckpoint 不注册 =====

    /** extConfig 无 schedule 键（空 cron）：registerCheckpoint 不注册。 */
    @Test
    public void testEmptyCronNotRegistered() {
        saveCheckpointWithSchedule("cp-no-cron", "ACTIVE",
                "[{\"ruleIds\":[\"__any__\"]}]", null, null);
        checkpointScheduler.registerCheckpoint("cp-no-cron");

        IJobScheduler scheduler = checkpointScheduler.getScheduler();
        String jobName = MetaQualityCheckpointScheduler.jobName("cp-no-cron");
        assertFalse(scheduler.getJobNames().contains(jobName),
                "checkpoint with empty cron must not be registered: " + scheduler.getJobNames());
    }

    // ===== (d) 未知 checkpoint：fireNow 返回 false =====

    /** 未注册的 job：fireNow 返回 false（不静默、不抛错）。 */
    @Test
    public void testFireNowUnknownJobReturnsFalse() {
        IJobScheduler scheduler = checkpointScheduler.getScheduler();
        String jobName = MetaQualityCheckpointScheduler.jobName("__nonexistent_cp__");
        assertFalse(scheduler.fireNow(jobName),
                "fireNow on unknown job must return false (no silent error)");
    }

    // ===== (e) 启动 scanner：init 后无 ACTIVE 检查点时注册 0 个 job（不抛崩） =====

    /**
     * 启动 scanner（D4 启动全量）在 IoC init 时已运行（@PostConstruct）。测试 DB 初始无数据，
     * scanner 应注册 0 个 job 且不抛崩。本测试断言 scanner bean 已成功初始化（可注入），
     * 且 scheduler 可用（被 activate）。验证 scanner 容错：空 DB 不抛崩。
     */
    @Test
    public void testStartupScannerNoActiveCheckpointsNoCrash() {
        assertNotNull(checkpointScheduler, "scanner bean must initialize without crash");
        assertNotNull(checkpointScheduler.getScheduler(), "scheduler must be available after init");
        // IoC init 完成 = scanner @PostConstruct 未抛崩（否则 bean 注入会失败）
    }

    // ===== helpers =====

    private void seedTable(String dbUrl, String createDdl, String... inserts) throws Exception {
        try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
             Statement st = c.createStatement()) {
            st.execute(createDdl);
            for (String ins : inserts) {
                st.execute(ins);
            }
        }
    }

    private PreparedEnv prepare(String dbUrl, String querySpace) {
        saveDataSource("ds-" + querySpace, querySpace, "jdbc", "ACTIVE",
                "{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                        + "\"driverClassName\":\"org.h2.Driver\"}");
        GraphQLResponseBean syncResp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"ds-" + querySpace
                        + "\", schemaPattern: \"PUBLIC\") }")));
        assertFalse(syncResp.hasError(), "sync should not error: " + syncResp);
        return new PreparedEnv();
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    private void saveRule(String ruleId, String ruleType, String entityType,
                          String entityId, String sqlExpression, Double threshold, String params) {
        IEntityDao<NopMetaQualityRule> dao = daoProvider.daoFor(NopMetaQualityRule.class);
        NopMetaQualityRule rule = dao.newEntity();
        rule.setQualityRuleId(ruleId);
        rule.setRuleName(ruleId);
        rule.setDisplayName(ruleId);
        rule.setRuleType(ruleType);
        rule.setEntityType(entityType);
        rule.setEntityId(entityId);
        rule.setSeverity("WARNING");
        rule.setSqlExpression(sqlExpression);
        rule.setThreshold(threshold);
        rule.setParams(params);
        rule.setVersion(1L);
        rule.setCreatedBy("autotest");
        rule.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        dao.saveEntity(rule);
    }

    private void saveCheckpointWithSchedule(String checkpointId, String status, String validations,
                                             String actions, String cron) {
        IEntityDao<NopMetaQualityCheckpoint> dao = daoProvider.daoFor(NopMetaQualityCheckpoint.class);
        NopMetaQualityCheckpoint cp = dao.newEntity();
        cp.setCheckpointId(checkpointId);
        cp.setCheckpointName(checkpointId);
        cp.setDisplayName(checkpointId);
        cp.setStatus(status);
        cp.setValidations(validations);
        cp.setActions(actions);
        // extConfig 承载 schedule（D2）+ autoScore 默认开启
        String extConfig = cron != null
                ? "{\"schedule\":\"" + cron + "\",\"autoScore\":true}"
                : "{\"autoScore\":true}";
        cp.setExtConfig(extConfig);
        cp.setVersion(1L);
        cp.setCreatedBy("autotest");
        cp.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        cp.setCreateTime(now);
        cp.setUpdateTime(now);
        dao.saveEntity(cp);
    }

    private void saveDataSource(String id, String querySpace, String datasourceType,
                                String status, String connectionConfig) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType(datasourceType);
        ds.setConnectionConfig(connectionConfig);
        ds.setStatus(status);
        ds.setVersion(1L);
        ds.setCreatedBy("autotest");
        ds.setUpdatedBy("autotest");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    private String ensureExternalSystemModuleId() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaModule.PROP_NAME_moduleId, "nop/meta-external"));
        NopMetaModule module = moduleDao.findFirstByQuery(q);
        if (module != null) {
            return module.getMetaModuleId();
        }
        module = moduleDao.newEntity();
        module.setModuleId("nop/meta-external");
        module.setModuleName("meta-external");
        module.setDisplayName("外部表系统模块");
        module.setModuleVersion(1L);
        module.setStatus("RELEASED");
        module.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(module);
        return module.getMetaModuleId();
    }

    private long countResults(String ruleId) {
        IEntityDao<NopMetaQualityResult> dao = daoProvider.daoFor(NopMetaQualityResult.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityResult.PROP_NAME_qualityRuleId, ruleId));
        return dao.countByQuery(q);
    }

    private long countScores(String metaTableId) {
        IEntityDao<NopMetaQualityScore> dao = daoProvider.daoFor(NopMetaQualityScore.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaQualityScore.PROP_NAME_metaTableId, metaTableId));
        return dao.countByQuery(q);
    }

    /** 环境就绪后的轻量句柄。 */
    private class PreparedEnv {
        String tableId(String tableName) {
            IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
            QueryBean q = new QueryBean();
            q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
            q.addFilter(FilterBeans.eq("tableType", "external"));
            NopMetaTable t = tableDao.findFirstByQuery(q);
            assertNotNull(t, "external table " + tableName + " must exist");
            return t.getMetaTableId();
        }
    }
}
