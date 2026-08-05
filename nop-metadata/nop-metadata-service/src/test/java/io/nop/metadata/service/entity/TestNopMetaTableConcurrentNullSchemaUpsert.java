package io.nop.metadata.service.entity;

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
import io.nop.metadata.dao.entity.NopMetaDataSource;
import io.nop.metadata.dao.entity.NopMetaModule;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.service.connection.IMetaDataSourceConnectionProcessor;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-07（R6.3，plan-2026-08-05-2157-3）NULL-schema 并发双插判别性回归测试：
 *
 * <p>R4.2 将 UK 扩展为 (metaModuleId, tableName, isDelta, metaSchema)（metaSchema 可空）后，
 * NULL-schema 外部表并发同步（两线程同时 {@code syncExternalTables} 同一数据源同一 NULL-schema 表）时，
 * {@code upsertExternalTable} 的 find-then-insert 非原子——两线程均 find 为空 → 两插皆成功
 * （NULL≠NULL 不参与 UK 冲突判定）→ 静默重复行。
 *
 * <p>修复（路径 C'：per-key 锁 + 每表独立事务提交，锁跨 find→insert→flush→commit）：后到线程
 * 在锁释放后执行 find，可见先到线程已提交的行 → 收敛为 update，不产生重复行。
 *
 * <p>NULL-schema 产生方式：H2 的 TABLE_SCHEM 恒为 PUBLIC，真实 JDBC 扫描无法产出 NULL-schema 行
 * （plan 允许"或直插路径"）；本测试将 BizModel 的 connectionService 换成包一层 {@code NullSchemaMetadataProcessor}
 * ——仅把 {@link DatabaseMetaData#getTables} 结果集的 TABLE_SCHEM 置 null，其余全部委托真实实现——
 * 使真实 {@code syncExternalTables} → 真实 {@code upsertExternalTable} 走 NULL-schema 分支。
 *
 * <p>测试规格（plan 要求）：
 * <ul>
 *   <li>预创建外部表归属 module（{@code nop/meta-external}），隔离 {@code ensureExternalSystemModule}
 *       本身的 NopMetaModule find-then-insert 竞态</li>
 *   <li>{@link CountDownLatch} 对齐两线程起点</li>
 *   <li>≥20 轮，每轮独立物理库 + 独立表名（无需轮间清理），断言每轮恰好 1 行落盘 + 0 错误</li>
 *   <li>未修复代码上必须观察到失败（判别性验证；顺序双插为 vacuous 不作为验证项）</li>
 * </ul>
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestNopMetaTableConcurrentNullSchemaUpsert extends JunitBaseTestCase {

    static final int ROUNDS = 20;
    static final int THREADS = 2;

    public TestNopMetaTableConcurrentNullSchemaUpsert() {
        setTestConfig("nop.orm.init-database-schema", true);
    }

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    NopMetaDataSourceBizModel dataSourceBizModel;

    /**
     * 判别性验证：每轮两线程同时 sync 同一外部表（schemaPattern=PUBLIC 限定扫描面，TABLE_SCHEM 被置
     * null → upsert 键的 metaSchema=NULL），断言恰好 1 行落盘（无静默重复行）+ 两线程均无错误
     * （并发失败方收敛为 update 而非报错/追加）。
     */
    @Test
    public void testConcurrentNullSchemaDoubleSyncProducesSingleRow() throws Exception {
        ensureExternalSystemModule();
        dataSourceBizModel.connectionService = new NullSchemaMetadataProcessor(dataSourceBizModel.connectionService);

        int failedRounds = 0;
        StringBuilder failures = new StringBuilder();
        for (int round = 0; round < ROUNDS; round++) {
            String tableName = "EXT_NULL_" + round;
            String querySpace = "qs_null_race_" + round;
            String dbUrl = "jdbc:h2:mem:" + querySpace + ";DB_CLOSE_DELAY=-1";
            try (Connection c = DriverManager.getConnection(dbUrl, "sa", "");
                 Statement st = c.createStatement()) {
                st.execute("CREATE TABLE " + tableName + " (id INT)");
            }
            saveDataSource("ds-" + querySpace, querySpace, dbUrl);

            ConcurrentSyncOutcome outcome = syncConcurrently("ds-" + querySpace);

            List<NopMetaTable> rows = findExternalTables(tableName);
            if (outcome.errorCount != 0 || rows.size() != 1) {
                failedRounds++;
                failures.append("round ").append(round)
                        .append(": errors=").append(outcome.errorCount)
                        .append(", rows=").append(rows.size())
                        .append(", schemas=")
                        .append(rows.stream().map(NopMetaTable::getMetaSchema)
                                .collect(java.util.stream.Collectors.toList()))
                        .append("\n");
            }
        }
        assertEquals(0, failedRounds,
                "every round must converge to exactly 1 row with no sync errors (buggy code duplicates rows):\n"
                        + failures);
    }

    // ============================ helpers ============================

    private static class ConcurrentSyncOutcome {
        final int errorCount;
        final int syncedCount;

        ConcurrentSyncOutcome(int errorCount, int syncedCount) {
            this.errorCount = errorCount;
            this.syncedCount = syncedCount;
        }
    }

    /** 两线程同时执行 syncExternalTables（schemaPattern=PUBLIC 限定扫描面 → TABLE_SCHEM 置 null 后 NULL-schema），对齐起点，等待全部完成。 */
    private ConcurrentSyncOutcome syncConcurrently(String dataSourceId) throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger syncedCount = new AtomicInteger(0);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                try {
                    startLatch.await();
                    GraphQLResponseBean resp = graphQLEngine.executeGraphQL(graphQLEngine.newGraphQLContext(req(
                            "mutation { NopMetaDataSource__syncExternalTables(dataSourceId: \"" + dataSourceId
                                    + "\", schemaPattern: \"PUBLIC\") { syncedTableCount errors { code message } } }")));
                    if (resp.hasError()) {
                        errorCount.incrementAndGet();
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) resp.getData();
                    Map<String, Object> result = (Map<String, Object>) data.get("NopMetaDataSource__syncExternalTables");
                    Object errs = result.get("errors");
                    if (errs instanceof List && !((List<?>) errs).isEmpty()) {
                        errorCount.incrementAndGet();
                        return;
                    }
                    syncedCount.addAndGet(((Number) result.get("syncedTableCount")).intValue());
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS), "both sync threads must finish within timeout");
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        return new ConcurrentSyncOutcome(errorCount.get(), syncedCount.get());
    }

    private List<NopMetaTable> findExternalTables(String tableName) {
        IEntityDao<NopMetaTable> tableDao = daoProvider.daoFor(NopMetaTable.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTable.PROP_NAME_tableName, tableName));
        q.addFilter(FilterBeans.eq("tableType", "external"));
        return tableDao.findAllByQuery(q);
    }

    private void saveDataSource(String id, String querySpace, String dbUrl) {
        IEntityDao<NopMetaDataSource> dao = daoProvider.daoFor(NopMetaDataSource.class);
        NopMetaDataSource ds = dao.newEntity();
        ds.setDataSourceId(id);
        ds.setQuerySpace(querySpace);
        ds.setName(id);
        ds.setDatasourceType("jdbc");
        ds.setConnectionConfig("{\"jdbcUrl\":\"" + dbUrl + "\",\"username\":\"sa\",\"password\":\"\","
                + "\"driverClassName\":\"org.h2.Driver\"}");
        ds.setStatus("ACTIVE");
        ds.setVersion(1L);
        Timestamp now = new Timestamp(System.currentTimeMillis());
        ds.setCreateTime(now);
        ds.setUpdateTime(now);
        dao.saveEntity(ds);
    }

    /** 预创建外部表系统模块（nop/meta-external），隔离 ensureExternalSystemModule 的模块 UK 竞态。 */
    private void ensureExternalSystemModule() {
        IEntityDao<NopMetaModule> moduleDao = daoProvider.daoFor(NopMetaModule.class);
        NopMetaModule m = moduleDao.newEntity();
        m.setModuleId("nop/meta-external");
        m.setModuleName("meta-external");
        m.setDisplayName("外部表系统模块");
        m.setModuleVersion(1L);
        m.setStatus("RELEASED");
        m.setImportedAt(new Timestamp(System.currentTimeMillis()));
        moduleDao.saveEntity(m);
    }

    private GraphQLRequestBean req(String query) {
        GraphQLRequestBean request = new GraphQLRequestBean();
        request.setQuery(query);
        return request;
    }

    /**
     * 委托真实 connection processor，但把传给 action 的 {@link DatabaseMetaData} 换成 JDK 动态代理：
     * 仅 {@code getTables} 结果集的 TABLE_SCHEM 列返回 null（模拟无 schema 物理库，如 SQLite），
     * 其余全部委托真实实现。使真实 syncExternalTables 走 NULL-schema upsert 分支。
     */
    static class NullSchemaMetadataProcessor implements IMetaDataSourceConnectionProcessor {

        private final IMetaDataSourceConnectionProcessor delegate;

        NullSchemaMetadataProcessor(IMetaDataSourceConnectionProcessor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void withConnection(String datasourceType, String connectionConfig,
                                   BiConsumer<Connection, DatabaseMetaData> action) {
            delegate.withConnection(datasourceType, connectionConfig, (conn, meta) ->
                    action.accept(conn, nullSchemaMetaData(meta)));
        }

        @Override
        public Map<String, Object> testConnect(String datasourceType, String connectionConfig) {
            return delegate.testConnect(datasourceType, connectionConfig);
        }

        private static DatabaseMetaData nullSchemaMetaData(DatabaseMetaData real) {
            return (DatabaseMetaData) Proxy.newProxyInstance(
                    TestNopMetaTableConcurrentNullSchemaUpsert.class.getClassLoader(),
                    new Class[]{DatabaseMetaData.class},
                    (proxy, method, args) -> {
                        if ("getTables".equals(method.getName()) && args != null && args.length == 4) {
                            ResultSet rs = (ResultSet) method.invoke(real, args);
                            return Proxy.newProxyInstance(
                                    TestNopMetaTableConcurrentNullSchemaUpsert.class.getClassLoader(),
                                    new Class[]{ResultSet.class},
                                    (rsProxy, rsMethod, rsArgs) -> {
                                        if ("getString".equals(rsMethod.getName()) && rsArgs != null
                                                && rsArgs.length == 1 && rsArgs[0] instanceof String
                                                && "TABLE_SCHEM".equalsIgnoreCase((String) rsArgs[0])) {
                                            return null;
                                        }
                                        return rsMethod.invoke(rs, rsArgs);
                                    });
                        }
                        return method.invoke(real, args);
                    });
        }
    }
}
