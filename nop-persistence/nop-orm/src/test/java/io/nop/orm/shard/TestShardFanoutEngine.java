package io.nop.orm.shard;

import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConfigs;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.dao.shard.ShardSelection;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataRow;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.ddl.ShardDdlHelper;
import io.nop.orm.exceptions.OrmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_CONSTRUCT;
import static io.nop.orm.OrmErrors.ARG_COUNT;
import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_LIMIT;
import static io.nop.orm.OrmErrors.ARG_ORDER_KEY;
import static io.nop.orm.OrmErrors.ARG_POLICY;
import static io.nop.orm.OrmErrors.ARG_REASON;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_CONDITION_MISSING;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_FANOUT_NOT_SUPPORT;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2321 Phase 3 focused 测试 + 最小端到端（H2 双分片，分表形态）：执行引擎资格收口、
 * 归并四语义、行数上限中断、逐份 readOnly 与强制 selector 断言、开关关闭退化。
 *
 * <p>数据布局（mod 2 分片，nopShard 路由，sid 跨分片交错构造）：
 * shard_0: (a,va) (c,null)；shard_1: (b,vb) (d,vd)。</p>
 */
public class TestShardFanoutEngine extends AbstractOrmTestCase {

    static final String SHARD_ENTITY = "test.entity.TestOrmShardTable";
    static final String MAIN_ENTITY = "test.entity.TestOrmTable";

    /**
     * 记录型 selector：selectAllShards 委托配置轨（真实全集），selectShard 计数（对照断言用）
     */
    static class RecordingFanoutSelector implements IShardSelector {
        final ConfigShardSelector delegate = new ConfigShardSelector();
        int selectShardCalls;

        void init(String configJson) {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, configJson);
            try {
                delegate.init();
            } finally {
                AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
            }
        }

        @Override
        public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
            selectShardCalls++;
            return delegate.selectShard(entityName, shardProp, shardValue);
        }

        @Override
        public boolean hasStrategyConfig() {
            return delegate.hasStrategyConfig();
        }

        @Override
        public boolean isEntityBound(String entityName) {
            return delegate.isEntityBound(entityName);
        }

        @Override
        public MissingShardPolicy getMissingShardPolicy(String entityName) {
            return delegate.getMissingShardPolicy(entityName);
        }

        @Override
        public ShardSelection selectShardWhenMissing(String entityName, String shardProp) {
            return delegate.selectShardWhenMissing(entityName, shardProp);
        }

        @Override
        public List<String> getShardNames(String entityName) {
            return delegate.getShardNames(entityName);
        }

        @Override
        public List<ShardSelection> selectAllShards(String entityName) {
            return delegate.selectAllShards(entityName);
        }
    }

    /**
     * 不覆盖 selectAllShards 的自定义轨 selector（接口 default 空列表 → 空全集形态）
     */
    static class CustomSelectorWithoutAllShards extends RecordingFanoutSelector {
        @Override
        public List<ShardSelection> selectAllShards(String entityName) {
            // 显式不委托：模拟自定义轨 selector 未实现 fanout 分片全集（接口 default 语义）
            return new ArrayList<>();
        }
    }

    RecordingFanoutSelector selector;
    final List<SQL> executedQuerySqls = new ArrayList<>();

    static String fanoutConfig() {
        return "{\"strategies\":{\"fanout-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"missing-shard-policy\":\"fanout\",\"entities\":[\"" + SHARD_ENTITY + "\",\""
                + MAIN_ENTITY + "\"]}}}";
    }

    @Override
    protected IShardSelector shardSelector() {
        if (selector == null) {
            selector = new RecordingFanoutSelector();
            selector.init(fanoutConfig());
        }
        // 引擎运行期门控读取实时配置：开启全局开关
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD_FANOUT_ENABLED, true);
        return selector;
    }

    @Override
    protected IJdbcTemplate createJdbcTemplate() {
        IJdbcTemplate real = super.createJdbcTemplate();
        // 记录型 jdbcTemplate：捕获物理 SELECT SQL（逐份 readOnly 与分片发起数断言）
        return (IJdbcTemplate) Proxy.newProxyInstance(IJdbcTemplate.class.getClassLoader(),
                new Class[]{IJdbcTemplate.class}, (proxy, method, args) -> {
                    try {
                        if ("executeQuery".equals(method.getName()) && args != null && args.length > 0
                                && args[0] instanceof SQL) {
                            executedQuerySqls.add((SQL) args[0]);
                        }
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException)
                            throw (RuntimeException) cause;
                        if (cause instanceof Error)
                            throw (Error) cause;
                        throw e;
                    }
                });
    }

    @AfterEach
    public void tearDownFanout() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD_FANOUT_ENABLED, null);
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD_FANOUT_MAX_ROWS, null);
        selector = null;
        executedQuerySqls.clear();
    }

    @Override
    protected void createTables() {
        super.createTables();
        // 为绑定策略的 useShard 测试实体建 N 张分片物理表（dogfood ShardDdlHelper，2320 先例）
        IShardSelector shardSelector = selector;
        for (String entityName : new String[]{SHARD_ENTITY, MAIN_ENTITY}) {
            var entityModel = sessionFactory.getOrmModel().requireEntityModel(entityName);
            IDialect dialect = jdbcTemplate.getDialectForQuerySpace(entityModel.getQuerySpace());
            DdlSqlCreator creator = DdlSqlCreator.forDialect(dialect);
            for (String physicalTable : ShardDdlHelper.shardTableNames(shardSelector, entityModel)) {
                if (!jdbcTemplate.existsTable(entityModel.getQuerySpace(), physicalTable)) {
                    String ddl = creator.createTable(entityModel, false, physicalTable);
                    jdbcTemplate.executeUpdate(new SQL(ddl));
                }
            }
        }
    }

    private IOrmEntity newShardEntity(String sid, String strValue, String shardValue) {
        IOrmEntity entity = orm().newEntity(SHARD_ENTITY);
        entity.orm_propValueByName("sid", sid);
        entity.orm_propValueByName("strValue", strValue);
        entity.orm_propValueByName("nopShard", shardValue);
        return entity;
    }

    /**
     * 数据布局：shard_0: (a,va) (c,null)；shard_1: (b,vb) (d,vd)
     */
    private void prepareShardData() {
        orm().runInSession(() -> {
            orm().save(newShardEntity("a", "va", "0"));
            orm().save(newShardEntity("c", null, "0"));
            orm().save(newShardEntity("b", "vb", "1"));
            orm().save(newShardEntity("d", "vd", "1"));
        });
        selector.selectShardCalls = 0;
        executedQuerySqls.clear();
    }

    private List<List<Object>> queryRows(SQL sql) {
        return orm().executeQuery(sql, null, ds -> {
            List<List<Object>> rows = new ArrayList<>();
            ds.forEach(row -> rows.add(Arrays.asList(row.getObject(0), row.getObject(1))));
            return rows;
        });
    }

    private List<Object> querySingleColumn(SQL sql, LongRangeBean range) {
        return orm().executeQuery(sql, range, ds -> {
            List<Object> rows = new ArrayList<>();
            ds.forEach(row -> rows.add(row.getObject(0)));
            return rows;
        });
    }

    private OrmException assertFanoutNotSupport(Runnable action) {
        OrmException e = assertThrows(OrmException.class, action::run);
        assertEquals(ERR_ORM_SHARD_FANOUT_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
        return e;
    }

    // ---------- 归并四语义（最小端到端：无分片键EQL → 归并结果） ----------

    @Test
    public void testFanoutUnorderedConcat() {
        prepareShardData();

        // 无分片键 SELECT：按分片全集执行 N 份物理 SQL，无序拼接全量完整
        List<List<Object>> rows = queryRows(SQL.begin()
                .sql("select o.sid, o.strValue from " + SHARD_ENTITY + " o").end());
        assertEquals(4, rows.size());
        assertEquals("a", rows.get(0).get(0));
        // 无 ORDER BY：分片顺序拼接（shard_0 先于 shard_1）
        assertEquals(Arrays.asList("a", "c", "b", "d"), Arrays.asList(rows.get(0).get(0),
                rows.get(1).get(0), rows.get(2).get(0), rows.get(3).get(0)));

        // 接线验证：fanout 查询产出 2 份物理 SQL（表名后缀可观察），不消耗 selectShard 值路由
        assertEquals(2, executedQuerySqls.size());
        assertTrue(executedQuerySqls.get(0).getText().contains("TEST_ORM_SHARD_TABLE_0"),
                executedQuerySqls.get(0).getText());
        assertTrue(executedQuerySqls.get(1).getText().contains("TEST_ORM_SHARD_TABLE_1"),
                executedQuerySqls.get(1).getText());
        assertEquals(0, selector.selectShardCalls);
    }

    @Test
    public void testFanoutOrderByTrueOrder() {
        prepareShardData();

        // ORDER BY asc：数据跨分片交错（a,c∈shard_0; b,d∈shard_1），归并排序产出全量真序
        List<Object> asc = querySingleColumn(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o order by o.sid asc").end(), null);
        assertEquals(Arrays.asList("a", "b", "c", "d"), asc);

        List<Object> desc = querySingleColumn(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o order by o.sid desc").end(), null);
        assertEquals(Arrays.asList("d", "c", "b", "a"), desc);
    }

    @Test
    public void testFanoutOrderByNullCrossShards() {
        prepareShardData();

        // null 空序跨分片：asc 缺省 null 在后（null 视为最大值）
        List<Object> asc = querySingleColumn(SQL.begin()
                .sql("select o.strValue from " + SHARD_ENTITY + " o order by o.strValue asc").end(), null);
        assertEquals(Arrays.asList("va", "vb", "vd", null), asc);

        // desc 缺省 null 在前；显式 NULLS LAST 覆盖缺省
        List<Object> desc = querySingleColumn(SQL.begin()
                .sql("select o.strValue from " + SHARD_ENTITY + " o order by o.strValue desc").end(), null);
        assertEquals(Arrays.asList(null, "vd", "vb", "va"), desc);

        List<Object> descNullsLast = querySingleColumn(SQL.begin()
                .sql("select o.strValue from " + SHARD_ENTITY + " o order by o.strValue desc nulls last").end(),
                null);
        assertEquals(Arrays.asList("vd", "vb", "va", null), descNullsLast);
    }

    @Test
    public void testFanoutBareCountSum() {
        prepareShardData();

        // 裸 count 求和：单行结果 = 各分片 count 之和
        List<Object> counts = querySingleColumn(SQL.begin()
                .sql("select count(1) from " + SHARD_ENTITY + " o").end(), null);
        assertEquals(1, counts.size());
        assertEquals(4L, ((Number) counts.get(0)).longValue());
    }

    @Test
    public void testFanoutShallowPagination() {
        prepareShardData();

        // 浅分页：每分片下推 offset+limit，归并排序后截断 [offset, offset+limit)
        List<Object> page = querySingleColumn(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o order by o.sid").end(), LongRangeBean.of(1, 2));
        assertEquals(Arrays.asList("b", "c"), page);

        // 总数 = 各分片 count 求和（独立裸 count fanout 查询承载）
        List<Object> total = querySingleColumn(SQL.begin()
                .sql("select count(1) from " + SHARD_ENTITY + " o").end(), null);
        assertEquals(4L, ((Number) total.get(0)).longValue());

        // offset 超出总量：空结果而非错误
        List<Object> empty = querySingleColumn(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o order by o.sid").end(), LongRangeBean.of(10, 2));
        assertTrue(empty.isEmpty());
    }

    // ---------- 行数上限：逐分片增量计数达限立即中断 ----------

    @Test
    public void testFanoutRowLimitInterruptsBeforeRemainingShards() {
        prepareShardData();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD_FANOUT_MAX_ROWS, 1);

        // 第一分片拉回 2 行即超限（上限1）：不再向剩余分片发起物理 SQL
        OrmException e = assertThrows(OrmException.class, () -> queryRows(SQL.begin()
                .sql("select o.sid, o.strValue from " + SHARD_ENTITY + " o").end()));
        assertEquals(ERR_ORM_SHARD_FANOUT_ROW_LIMIT_EXCEEDED.getErrorCode(), e.getErrorCode());
        assertEquals(2, e.getParams().get(ARG_COUNT));
        assertEquals(1, e.getParams().get(ARG_LIMIT));

        // 中断证据：仅第一分片物理 SQL 已发起
        assertEquals(1, executedQuerySqls.size());
        // 无部分结果：异常传播，调用方拿不到截断集（assertThrows 已保证异常路径）
    }

    // ---------- 逐份 readOnly 断言 ----------

    @Test
    public void testEachPhysicalSqlCarriesReadOnlyIntent() {
        prepareShardData();

        queryRows(SQL.begin().sql("select o.sid, o.strValue from " + SHARD_ENTITY + " o").end());

        // N 份物理 SQL 各带只读标记（引擎逐份显式设置，不存在"标记自然传播"机制）
        assertEquals(2, executedQuerySqls.size());
        assertTrue(executedQuerySqls.get(0).isReadOnly());
        assertTrue(executedQuerySqls.get(1).isReadOnly());
    }

    // ---------- 事务内 fanout 读经既有事务组（设计 §3.3 m3） ----------

    @Test
    public void testFanoutReadInsideTransactionJoinsExistingTxnGroup() {
        prepareShardData();

        // 缺省部署（无 txn-group-map 映射回落 DEFAULT_TXN_GROUP）：事务内 fanout 读经
        // TransactionTemplateImpl 自动创建子事务挂入主组，归并结果正确
        List<Object> sids = txn().runInTransaction(itx -> querySingleColumn(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o order by o.sid").end(), null));
        assertEquals(Arrays.asList("a", "b", "c", "d"), sids);
    }

    // ---------- 非 fanout 查询对照（零变更） ----------

    @Test
    public void testNonFanoutQueryUnchanged() {
        prepareShardData();

        // 带分片键等值条件：既有值路由单分片路径，引擎零参与
        List<IOrmEntity> rows = new ArrayList<>();
        orm().runInSession(() -> rows.addAll(orm().findAll(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard = ?", "0").end())));
        assertEquals(2, rows.size());
        assertEquals(1, selector.selectShardCalls);
        assertEquals(1, executedQuerySqls.size());
    }

    // ---------- 开关关闭退化 fail ----------

    @Test
    public void testFanoutDisabledDegradesToFail() {
        prepareShardData();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD_FANOUT_ENABLED, false);

        // 运行期退化为 fail（不静默降级单分片），携带 policy/原因 arg（Phase 2 m2）
        OrmException e = assertThrows(OrmException.class, () -> queryRows(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o").end()));
        assertEquals(ERR_ORM_SHARD_CONDITION_MISSING.getErrorCode(), e.getErrorCode());
        assertEquals(SHARD_ENTITY, e.getParams().get(ARG_ENTITY_NAME));
        assertEquals("fanout", e.getParams().get(ARG_POLICY));
        String reason = String.valueOf(e.getParams().get(ARG_REASON));
        assertTrue(reason.contains("fanout-enabled=false"), reason);
    }

    // ---------- 空全集形态（自定义 selector 未覆盖 selectAllShards） ----------

    @Test
    public void testEmptyShardSetFailsLoudly() {
        // 换用不覆盖 selectAllShards 的自定义轨 selector（重建 factory → 全新 H2）
        tearDown();
        selector = new CustomSelectorWithoutAllShards();
        selector.init(fanoutConfig());
        setUp();

        prepareShardData();

        // 空全集 =「该选择器不提供分片全集」→ 显式失败，禁止静默空结果（设计 §3.8 m1）
        NopException e = assertThrows(NopException.class, () -> queryRows(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o").end()));
        assertEquals(ERR_ORM_SHARD_FANOUT_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
        String reason = String.valueOf(e.getParams().get(ARG_REASON));
        assertTrue(reason.contains("selectAllShards"), reason);
    }

    // ---------- 资格收口逐形态显式失败 ----------

    @Test
    public void testQualificationNonSelectFails() {
        prepareShardData();

        // UPDATE 命中 fanout 策略：跨分片非原子 DML 不进 v1 引擎（设计 §3.3 评审 M1）
        OrmException e = assertFanoutNotSupport(() -> orm().executeUpdate(SQL.begin()
                .sql("update " + SHARD_ENTITY + " o set o.strValue = 'x'").end()));
        assertEquals("UPDATE", e.getParams().get(ARG_CONSTRUCT));
        assertEquals(SHARD_ENTITY, e.getParams().get(ARG_ENTITY_NAME));

        // DELETE 命中 fanout 策略
        OrmException delErr = assertFanoutNotSupport(() -> orm().executeUpdate(SQL.begin()
                .sql("delete from " + SHARD_ENTITY + " o").end()));
        assertEquals("DELETE", delErr.getParams().get(ARG_CONSTRUCT));
    }

    @Test
    public void testQualificationMultipleFanoutEntitiesFails() {
        prepareShardData();

        // 两个 fanout 绑定实体且均无分片值（隐式跨片 JOIN 意图）
        OrmException e = assertFanoutNotSupport(() -> orm().findAll(SQL.begin()
                .sql("select o.strValue from " + SHARD_ENTITY + " o join " + MAIN_ENTITY
                        + " t on o.strValue = t.strValue").end()));
        String reason = String.valueOf(e.getParams().get(ARG_REASON));
        assertTrue(reason.contains("at most one fanout entity"), reason);
    }

    @Test
    public void testQualificationMixedWithValuedEntityFails() {
        prepareShardData();

        // fanout 实体与带分片值实体混用（t 带值路由、o 缺值 fanout）
        OrmException e = assertFanoutNotSupport(() -> orm().findAll(SQL.begin()
                .sql("select o.strValue from " + SHARD_ENTITY + " o join " + MAIN_ENTITY
                        + " t on o.strValue = t.strValue where t.nopShard = ?", "0").end()));
        String reason = String.valueOf(e.getParams().get(ARG_REASON));
        assertTrue(reason.contains("must not be mixed"), reason);
    }

    @Test
    public void testQualificationConstructsFail() {
        prepareShardData();

        // GROUP BY
        OrmException groupErr = assertFanoutNotSupport(() -> queryRows(SQL.begin()
                .sql("select o.sid, count(1) from " + SHARD_ENTITY + " o group by o.sid").end()));
        assertEquals("GROUP BY", groupErr.getParams().get(ARG_CONSTRUCT));

        // HAVING
        OrmException havingErr = assertFanoutNotSupport(() -> queryRows(SQL.begin()
                .sql("select o.sid, count(1) from " + SHARD_ENTITY + " o group by o.sid"
                        + " having count(1) > 0").end()));
        assertEquals("HAVING", havingErr.getParams().get(ARG_CONSTRUCT));

        // DISTINCT
        OrmException distinctErr = assertFanoutNotSupport(() -> queryRows(SQL.begin()
                .sql("select distinct o.sid from " + SHARD_ENTITY + " o").end()));
        assertEquals("DISTINCT", distinctErr.getParams().get(ARG_CONSTRUCT));

        // 非 count 聚合
        OrmException aggErr = assertFanoutNotSupport(() -> queryRows(SQL.begin()
                .sql("select sum(o.sid) from " + SHARD_ENTITY + " o").end()));
        assertEquals("AGGREGATE", aggErr.getParams().get(ARG_CONSTRUCT));

        // FOR UPDATE
        OrmException forUpdateErr = assertFanoutNotSupport(() -> queryRows(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o for update").end()));
        assertEquals("FOR UPDATE", forUpdateErr.getParams().get(ARG_CONSTRUCT));

        // UNION
        OrmException unionErr = assertFanoutNotSupport(() -> queryRows(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o union select o.sid from "
                        + SHARD_ENTITY + " o").end()));
        assertEquals("UNION", unionErr.getParams().get(ARG_CONSTRUCT));
    }

    @Test
    public void testQualificationUnprojectedOrderKeyFails() {
        prepareShardData();

        // 排序键未投影：映射缺失 → 显式失败，arg=排序键标识（出路=排序键加入投影）
        OrmException e = assertFanoutNotSupport(() -> querySingleColumn(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o order by o.strValue").end(), null));
        assertEquals("orderBy#0", e.getParams().get(ARG_ORDER_KEY));
    }

    // ---------- querySpace 分库形态 focused（强制 selector 断言：querySpace 逐一正确） ----------

    @Test
    public void testFanoutWithQuerySpacePatternPerShard() {
        // 换用 ds_{shard} query-space-pattern 配置（重建 factory → 全新 H2）
        tearDown();
        selector = new RecordingFanoutSelector();
        selector.init("{\"strategies\":{\"fanout-qs\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"query-space-pattern\":\"ds_{shard}\",\"missing-shard-policy\":\"fanout\","
                + "\"entities\":[\"" + SHARD_ENTITY + "\",\"" + MAIN_ENTITY + "\"]}}}");
        setUp();

        // 注册 ds_0/ds_1 事务工厂（指向同一测试库），分片 querySpace 可执行
        io.nop.dao.txn.impl.TransactionTemplateImpl txnTemplate =
                (io.nop.dao.txn.impl.TransactionTemplateImpl) transactionTemplate;
        io.nop.dao.txn.impl.DefaultTransactionManager txnManager =
                (io.nop.dao.txn.impl.DefaultTransactionManager) txnTemplate.getTransactionManager();
        txnManager.addTransactionFactory("ds_0",
                new io.nop.dao.jdbc.txn.JdbcTransactionFactory(dataSource));
        txnManager.addTransactionFactory("ds_1",
                new io.nop.dao.jdbc.txn.JdbcTransactionFactory(dataSource));

        prepareShardData();
        executedQuerySqls.clear();

        List<Object> sids = querySingleColumn(SQL.begin()
                .sql("select o.sid from " + SHARD_ENTITY + " o order by o.sid").end(), null);
        assertEquals(Arrays.asList("a", "b", "c", "d"), sids);

        // 强制 selector 断言：N 份物理 SQL 的 querySpace 逐一正确（ds_0/ds_1 各一份）
        assertEquals(2, executedQuerySqls.size());
        assertEquals("ds_0", executedQuerySqls.get(0).getQuerySpace());
        assertEquals("ds_1", executedQuerySqls.get(1).getQuerySpace());
    }
}
