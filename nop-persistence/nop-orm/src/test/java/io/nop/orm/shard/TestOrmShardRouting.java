/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.shard;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConfigs;
import io.nop.dao.dialect.IDialect;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.dao.shard.ShardSelection;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.IOrmEntity;
import io.nop.orm.ddl.DdlSqlCreator;
import io.nop.orm.ddl.ShardDdlHelper;
import io.nop.orm.exceptions.OrmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ARG_PROP_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_CONDITION_MISSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2320 Phase 2 + Phase 4 端到端：配置即用的单分片路由。
 *
 * <p>链路（Anti-Hollow接线验证）：EQL编译（ShardMarkerInjector写入marker静态信息）→
 * 执行期GenSqlTransformer.collectShardSelections从paramValues取分片值 → selector.selectShard
 * 路由 → 表名_N后缀 + querySpace改写 → 物理表落点可观察。写路径（EntityPersisterImpl直取）
 * 与读路径共用同一selector。</p>
 *
 * <p>覆盖：参数/字面量形态等值路由、缺分片键fail/default两策略、join绑定表语义、
 * 跨querySpace既有错误、querySpace分库形态focused、未绑定实体零变更。</p>
 */
public class TestOrmShardRouting extends AbstractOrmTestCase {

    static final String SHARD_ENTITY = "test.entity.TestOrmShardTable";
    static final String MAIN_ENTITY = "test.entity.TestOrmTable";

    /**
     * 接线验证桩：记录selectShard调用（分片值真实经marker进入collectShardSelections的证据）
     */
    static class RecordingSelector implements IShardSelector {
        final ConfigShardSelector delegate = new ConfigShardSelector();
        final List<String> calls = new ArrayList<>();
        int callCount;

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
            callCount++;
            calls.add(entityName + ":" + shardProp + ":" + shardValue);
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
    }

    RecordingSelector selector;

    /**
     * 全局策略配置：mod 2片，绑定两个useShard测试实体，缺分片键fail（缺省）
     */
    static String shardConfig() {
        return "{\"strategies\":{\"test-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"entities\":[\"" + SHARD_ENTITY + "\",\"" + MAIN_ENTITY + "\"]}}}";
    }

    @Override
    protected IShardSelector shardSelector() {
        // 每个测试的AbstractOrmTestCase.setUp构建factory时调用一次（超类@BeforeEach先于子类执行）
        if (selector == null) {
            selector = new RecordingSelector();
            selector.init(shardConfig());
        }
        return selector;
    }

    @AfterEach
    public void tearDownShardSelector() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        selector = null;
    }

    @Override
    protected void createTables() {
        super.createTables();
        // plan 2320 Phase 3：为绑定策略的useShard测试实体建N张分片物理表（dogfood ShardDdlHelper）
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

    private void rebuildSessionFactoryWithConfig(String configJson) {
        selector.init(configJson);
        tearDown();
        setUp();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findAllMaps(SQL sql) {
        List<?> rows = orm().findAll(sql);
        List<Map<String, Object>> ret = new ArrayList<>();
        for (Object row : rows)
            ret.add((Map<String, Object>) row);
        return ret;
    }

    private List<IOrmEntity> findEntities(SQL sql) {
        List<?> rows = orm().findAll(sql);
        List<IOrmEntity> ret = new ArrayList<>();
        for (Object row : rows)
            ret.add((IOrmEntity) row);
        return ret;
    }

    private long countRows(String tableName) {
        return jdbcTemplate.findLong(new SQL("select count(1) from " + tableName), 0L);
    }

    // ---------- 端到端：写路由 + 读路由（Phase 4链路） ----------

    @Test
    public void testSaveWritesToShardTableAndQueryByShardKeyHitsSameTable() {
        // 1. 保存（写路径路由到_N表）
        orm().runInSession(() -> {
            orm().save(newShardEntity("s1", "v1", "1"));
            orm().save(newShardEntity("s0", "v0", "0"));
        });

        // 写路由物理可观察：基表无数据，_0/_1表各有一行
        assertEquals(0, countRows("TEST_ORM_SHARD_TABLE"));
        assertEquals(1, countRows("TEST_ORM_SHARD_TABLE_0"));
        assertEquals(1, countRows("TEST_ORM_SHARD_TABLE_1"));

        // 2. 按分片键EQL查询（参数形态）命中同一物理表
        selector.calls.clear();
        selector.callCount = 0;
        List<IOrmEntity> rows = findEntities(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard = ?", "1").end());
        assertEquals(1, rows.size());
        assertEquals("v1", rows.get(0).orm_propValueByName("strValue"));

        // 接线验证（Minimum Rules #23）：分片值经marker真实进入collectShardSelections→selector
        assertEquals(1, selector.callCount);
        assertEquals(SHARD_ENTITY + ":nopShard:1", selector.calls.get(0));

        // 3. 字面量形态
        selector.calls.clear();
        selector.callCount = 0;
        rows = findEntities(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard = '0'").end());
        assertEquals(1, rows.size());
        assertEquals("v0", rows.get(0).orm_propValueByName("strValue"));
        assertEquals(SHARD_ENTITY + ":nopShard:0", selector.calls.get(0));
    }

    @Test
    public void testSaveRoutesToShardPhysicalTableNotBase() {
        orm().runInSession(() -> orm().save(newShardEntity("s1", "v1", "1")));

        // 写路径路由（EntityPersisterImpl直取）物理可观察：_1表有数据、基表无
        assertEquals(1, countRows("TEST_ORM_SHARD_TABLE_1"));
        assertEquals(0, countRows("TEST_ORM_SHARD_TABLE"));
    }

    // ---------- 缺分片键策略矩阵 ----------

    @Test
    public void testMissingShardKeyFailsFast() {
        orm().runInSession(() -> orm().save(newShardEntity("s1", "v1", "1")));

        // 无分片键条件：fail（缺省策略）抛ERR_ORM_SHARD_CONDITION_MISSING（携带实体名与分片键列名）
        OrmException e = assertThrows(OrmException.class, () -> orm().findAll(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o").end()));
        assertEquals(ERR_ORM_SHARD_CONDITION_MISSING.getErrorCode(), e.getErrorCode());
        assertEquals(SHARD_ENTITY, e.getParams().get(ARG_ENTITY_NAME));
        assertEquals("nopShard", e.getParams().get(ARG_PROP_NAME));

        // 范围条件无法推出单分片值 → fail
        OrmException rangeErr = assertThrows(OrmException.class, () -> orm().findAll(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard > ?", "0").end()));
        assertEquals(ERR_ORM_SHARD_CONDITION_MISSING.getErrorCode(), rangeErr.getErrorCode());

        // OR分支下的等值条件不下探 → fail（v1边界：无法推出语句级单分片值）
        OrmException orErr = assertThrows(OrmException.class, () -> orm().findAll(SQL.begin()
                .sql("select o from " + SHARD_ENTITY
                        + " o where o.sid = ? or o.nopShard = ?", "s1", "1").end()));
        assertEquals(ERR_ORM_SHARD_CONDITION_MISSING.getErrorCode(), orErr.getErrorCode());

        // 分片键条件值为null → fail
        OrmException nullErr = assertThrows(OrmException.class, () -> orm().findAll(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard = ?", (Object) null).end()));
        assertEquals(ERR_ORM_SHARD_CONDITION_MISSING.getErrorCode(), nullErr.getErrorCode());
    }

    @Test
    public void testDefaultShardPolicyFallsBack() {
        rebuildSessionFactoryWithConfig("{\"strategies\":{\"test-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"missing-shard-policy\":\"default-shard\",\"default-shard\":\"1\","
                + "\"entities\":[\"" + SHARD_ENTITY + "\",\"" + MAIN_ENTITY + "\"]}}}");

        orm().runInSession(() -> orm().save(newShardEntity("s1", "v1", "1")));

        // 无分片键条件：default-shard回落到_1表（显式声明，非静默基表）
        selector.callCount = 0;
        List<IOrmEntity> rows = findEntities(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o").end());
        assertEquals(1, rows.size());
        assertEquals("v1", rows.get(0).orm_propValueByName("strValue"));
        // 回落分片不经过selectShard（值缺失），直接selectShardWhenMissing
        assertEquals(0, selector.callCount);
        assertEquals(0, countRows("TEST_ORM_SHARD_TABLE"));
    }

    // ---------- join绑定表语义 ----------

    @Test
    public void testJoinSameStrategySharesShardValue() {
        orm().runInSession(() -> {
            IOrmEntity main = orm().newEntity(MAIN_ENTITY);
            main.orm_propValueByName("sid", 1);
            main.orm_propValueByName("strValue", "main");
            main.orm_propValueByName("nopShard", "0");
            orm().save(main);
            orm().save(newShardEntity("s0", "child", "0"));
        });
        selector.callCount = 0; // 保存路径的selectShard调用不计入查询路由统计

        // 两实体绑定同一策略，ON条件等值传播分片值 → 同一物理后缀，join可执行
        List<Map<String, Object>> rows = findAllMaps(SQL.begin()
                .sql("select o.strValue as mv, s.strValue as sv from " + MAIN_ENTITY + " o"
                        + " join " + SHARD_ENTITY + " s on o.nopShard = s.nopShard"
                        + " where o.nopShard = ?", "0").end());
        assertEquals(1, rows.size());
        assertEquals("main", rows.get(0).get("mv"));
        assertEquals("child", rows.get(0).get("sv"));

        // ON等值传播：s未在where中出现，但其分片值经绑定表语义与o一致（两表各路由一次同值）
        assertEquals(2, selector.callCount);
    }

    @Test
    public void testJoinCrossQuerySpaceStillRejected() {
        // o路由到default querySpace（无pattern），s路由到ds_{shard} → 跨querySpace报既有错误
        rebuildSessionFactoryWithConfig("{\"strategies\":{"
                + "\"main-mod\":{\"type\":\"mod\",\"shard-count\":2,\"entities\":[\"" + MAIN_ENTITY + "\"]},"
                + "\"shard-qs\":{\"type\":\"mod\",\"shard-count\":2,\"query-space-pattern\":\"ds_{shard}\","
                + "\"entities\":[\"" + SHARD_ENTITY + "\"]}}}");

        OrmException e = assertThrows(OrmException.class, () -> orm().findAll(SQL.begin()
                .sql("select o.strValue from " + MAIN_ENTITY + " o"
                        + " join " + SHARD_ENTITY + " s on o.nopShard = s.nopShard"
                        + " where o.nopShard = ?", "0").end()));
        assertEquals(ERR_ORM_NOT_SUPPORT_MULTIPLE_QUERY_SPACE_IN_ONE_SQL.getErrorCode(), e.getErrorCode());
    }

    // ---------- querySpace分库形态（focused） ----------

    @Test
    public void testQuerySpacePatternSwitchesQuerySpace() {
        rebuildSessionFactoryWithConfig("{\"strategies\":{\"test-mod-qs\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"query-space-pattern\":\"ds_{shard}\","
                + "\"entities\":[\"" + SHARD_ENTITY + "\",\"" + MAIN_ENTITY + "\"]}}}");

        // ds_0/ds_1数据源未注册：路由成功切换querySpace后执行期显式报未知数据源（可观察断言）
        NopException e = assertThrows(NopException.class, () -> orm().findAll(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard = ?", "1").end()));
        assertEquals("nop.err.dao.unknown-query-space", e.getErrorCode());
        assertTrue(String.valueOf(e.getParams().get("querySpace")).contains("ds_1"));
    }

    // ---------- default-strategy全局回落 ----------

    @Test
    public void testDefaultStrategySatisfiesUnboundEntity() {
        // TestOrmShardTable未显式绑定：经default-strategy回落路由（fail策略）
        rebuildSessionFactoryWithConfig("{\"strategies\":{\"irrelevant\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"entities\":[\"no.such.Entity\"]}},\"default-strategy\":\"irrelevant\"}");

        orm().runInSession(() -> orm().save(newShardEntity("s1", "v1", "1")));

        List<IOrmEntity> rows = findEntities(SQL.begin()
                .sql("select o from " + SHARD_ENTITY + " o where o.nopShard = ?", "1").end());
        assertEquals(1, rows.size());
        assertEquals(1, countRows("TEST_ORM_SHARD_TABLE_1"));
        assertEquals(0, countRows("TEST_ORM_SHARD_TABLE"));
    }
}
