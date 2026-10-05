package io.nop.orm.shard;

import io.nop.api.core.config.AppConfig;
import io.nop.dao.DaoConfigs;
import io.nop.dao.shard.ConfigShardSelector;
import io.nop.dao.shard.IShardSelector;
import io.nop.dao.shard.MissingShardPolicy;
import io.nop.dao.shard.ShardSelection;
import io.nop.orm.AbstractOrmTestCase;
import io.nop.orm.exceptions.OrmException;
import io.nop.orm.persister.IPersistEnv;
import io.nop.orm.sql.GenSqlTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static io.nop.orm.OrmErrors.ARG_ENTITY_NAME;
import static io.nop.orm.OrmErrors.ERR_ORM_SHARD_FANOUT_NOT_SUPPORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * plan 2321 Phase 2 接线验证（Minimum Rules #23/#24）：FANOUT 策略经
 * ConfigShardSelector.getMissingShardPolicy 真实流入 GenSqlTransformer 的兜底分支
 * 并响亮失败（selector→transform 运行时调用链断言，非仅枚举值存在）。
 *
 * <p>语义：fanout 策略本应被执行引擎在 transform 之前接管；本测试以 selector 直驱
 * transform（不经引擎）模拟"引擎未接线"的泄漏形态——修复前该形态与 NONE 同款静默
 * fall-through 基表执行（plan 2320 消灭的陷阱借新策略值复活），修复后显式失败。</p>
 */
public class TestShardFanoutTransformFallback extends AbstractOrmTestCase {

    static final String SHARD_ENTITY = "test.entity.TestOrmShardTable";
    static final String MAIN_ENTITY = "test.entity.TestOrmTable";

    /**
     * 记录 getMissingShardPolicy 调用的装饰器（委托真实 ConfigShardSelector，
     * 证明 FANOUT 策略经配置轨 selector 真实流入 transform）
     */
    static class RecordingFanoutSelector implements IShardSelector {
        final ConfigShardSelector delegate = new ConfigShardSelector();
        int policyCalls;

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
            policyCalls++;
            return delegate.getMissingShardPolicy(entityName);
        }
    }

    RecordingFanoutSelector selector;

    @Override
    protected IShardSelector shardSelector() {
        // 装配进 sessionFactory 保证 ormModel 绑定校验通过（fanout 策略显式绑定测试实体）
        if (selector == null) {
            selector = new RecordingFanoutSelector();
            selector.init("{\"strategies\":{\"fanout-mod\":{\"type\":\"mod\",\"shard-count\":2,"
                    + "\"missing-shard-policy\":\"fanout\",\"entities\":[\"" + SHARD_ENTITY + "\",\""
                    + MAIN_ENTITY + "\"]}}}");
        }
        return selector;
    }

    @AfterEach
    public void tearDownFanoutSelector() {
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_ORM_SHARD, null);
        selector = null;
    }

    @Test
    public void testFanoutPolicyLeakingIntoTransformFailsLoudly() {
        IPersistEnv env = (IPersistEnv) sessionFactory;

        // 无分片键条件的 SELECT：TABLE marker 无分片值（模拟引擎未接管的泄漏形态）
        io.nop.orm.eql.ICompiledSql compiled = env.compileSql("fanout-fallback-test",
                "select o from " + SHARD_ENTITY + " o", false, false, false);
        List<Object> params = compiled.buildParams(new ArrayList<>());

        GenSqlTransformer transformer = new GenSqlTransformer(selector, env.getOrmModel(),
                jdbcTemplate, env.getLoadedOrmModel(), env.getEntityFilterProvider());

        // transform 直驱（不经引擎）：FANOUT 策略命中兜底分支，响亮失败而非静默基表执行
        OrmException e = assertThrows(OrmException.class,
                () -> transformer.transform(compiled.getSql(), params).end());
        assertEquals(ERR_ORM_SHARD_FANOUT_NOT_SUPPORT.getErrorCode(), e.getErrorCode());
        assertEquals(SHARD_ENTITY, e.getParams().get(ARG_ENTITY_NAME));

        // 接线验证：策略确实经 ConfigShardSelector.getMissingShardPolicy 流入 transform
        assertEquals(1, selector.policyCalls);
    }
}
