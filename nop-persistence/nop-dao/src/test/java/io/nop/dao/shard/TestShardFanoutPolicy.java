package io.nop.dao.shard;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.nop.dao.DaoConfigs.CFG_ORM_SHARD_FANOUT_ENABLED;
import static io.nop.dao.DaoConfigs.CFG_ORM_SHARD_FANOUT_MAX_ROWS;
import static io.nop.dao.DaoConfigs.CFG_ORM_SHARD;
import static io.nop.dao.DaoErrors.ERR_DAO_SHARD_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2321 Phase 2 focused 测试（nop-dao 面）：MissingShardPolicy 第四取值 FANOUT、
 * IShardSelector.selectAllShards SPI（接口 default 空列表语义 + ConfigShardSelector
 * 真实全集与 getShardNames 同源）、全局配置 fanout-enabled/fanout-max-rows、
 * 未配置分片体系零变更回归。
 */
public class TestShardFanoutPolicy extends io.nop.core.unittest.BaseTestCase {

    static final String ENTITY = "app.User";

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    @AfterEach
    public void tearDown() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD, null);
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD_FANOUT_ENABLED, null);
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD_FANOUT_MAX_ROWS, null);
    }

    private ConfigShardSelector selectorOf(String json) {
        ConfigShardSelector selector = new ConfigShardSelector();
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD, json);
        try {
            selector.init();
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD, null);
        }
        return selector;
    }

    // ---------- MissingShardPolicy 第四取值 ----------

    @Test
    public void testParseFanout() {
        assertEquals(MissingShardPolicy.FANOUT, MissingShardPolicy.parse("fanout"));
        assertEquals(MissingShardPolicy.FAIL, MissingShardPolicy.parse(null));
        assertEquals(MissingShardPolicy.FAIL, MissingShardPolicy.parse(""));
        assertEquals(MissingShardPolicy.FAIL, MissingShardPolicy.parse("fail"));
        assertEquals(MissingShardPolicy.DEFAULT_SHARD, MissingShardPolicy.parse("default-shard"));

        // 未知取值仍抛，错误文案同步列出全部合法取值
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MissingShardPolicy.parse("unknown"));
        assertTrue(e.getMessage().contains("fanout"), e.getMessage());
        assertTrue(e.getMessage().contains("fail"), e.getMessage());
        assertTrue(e.getMessage().contains("default-shard"), e.getMessage());
    }

    @Test
    public void testFanoutStrategyDoesNotRequireDefaultShard() {
        // FANOUT 不要求 default-shard 配置（DEFAULT_SHARD 的约束不适用）
        ConfigShardSelector selector = selectorOf("{\"strategies\":{\"f\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"missing-shard-policy\":\"fanout\",\"entities\":[\"" + ENTITY + "\"]}}}");
        assertEquals(MissingShardPolicy.FANOUT, selector.getMissingShardPolicy(ENTITY));
        // fanout 策略不参与缺分片值回落
        assertNull(selector.selectShardWhenMissing(ENTITY, "sid"));
    }

    // ---------- selectAllShards SPI ----------

    @Test
    public void testInterfaceDefaultReturnsEmptyList() {
        // 接口 default 语义 =「该选择器不提供分片全集」——自定义轨不覆盖则 fanout 不可用
        IShardSelector custom = new IShardSelector() {
            @Override
            public ShardSelection selectShard(String entityName, String shardProp, Object shardValue) {
                return null;
            }
        };
        assertTrue(custom.selectAllShards(ENTITY).isEmpty());
        assertTrue(EmptyShardSelector.INSTANCE.selectAllShards(ENTITY).isEmpty());
    }

    @Test
    public void testConfigSelectorSelectAllShardsSameSourceAsShardNames() {
        // 同源断言（设计 §3.8 m1）：selectAllShards 与 getShardNames 源自同一 strategy.shardNames() 序列
        ConfigShardSelector plain = selectorOf("{\"strategies\":{\"m\":{\"type\":\"mod\",\"shard-count\":3,"
                + "\"entities\":[\"" + ENTITY + "\"]}}}");
        assertSameSource(plain);

        // query-space-pattern 形态：querySpace 由同策略 pattern 同源推导
        ConfigShardSelector withPattern = selectorOf("{\"strategies\":{\"m\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"query-space-pattern\":\"ds_{shard}\",\"entities\":[\"" + ENTITY + "\"]}}}");
        List<ShardSelection> all = withPattern.selectAllShards(ENTITY);
        assertEquals(2, all.size());
        assertEquals("ds_0", all.get(0).getQuerySpace());
        assertEquals("0", all.get(0).getShardName());
        assertEquals("ds_1", all.get(1).getQuerySpace());
        assertEquals("1", all.get(1).getShardName());
    }

    private void assertSameSource(ConfigShardSelector selector) {
        List<String> names = selector.getShardNames(ENTITY);
        List<ShardSelection> all = selector.selectAllShards(ENTITY);
        assertEquals(names.size(), all.size());
        for (int i = 0; i < names.size(); i++) {
            assertEquals(names.get(i), all.get(i).getShardName());
        }
    }

    @Test
    public void testSelectAllShardsUnboundEntityReturnsEmpty() {
        ConfigShardSelector selector = selectorOf("{\"strategies\":{\"m\":{\"type\":\"mod\",\"shard-count\":2,"
                + "\"entities\":[\"" + ENTITY + "\"]}}}");
        assertTrue(selector.selectAllShards("no.such.Entity").isEmpty());
        assertTrue(selector.getShardNames("no.such.Entity").isEmpty());
    }

    // ---------- 全局配置 ----------

    @Test
    public void testFanoutConfigDefaults() {
        assertEquals(Boolean.FALSE, CFG_ORM_SHARD_FANOUT_ENABLED.get());
        assertEquals(Integer.valueOf(10000), CFG_ORM_SHARD_FANOUT_MAX_ROWS.get());
    }

    @Test
    public void testFanoutMaxRowsNonPositiveFailsAtInitEvenWithoutShardConfig() {
        // 非正数校验先于 nop.orm.shard 未配置早退：坏配置不允许静默
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD_FANOUT_MAX_ROWS, 0);
        NopException e = assertThrows(NopException.class, () -> new ConfigShardSelector().init());
        assertEquals(ERR_DAO_SHARD_CONFIG_INVALID.getErrorCode(), e.getErrorCode());
        assertTrue(String.valueOf(e.getParams().get("reason")).contains("fanout-max-rows"));

        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD_FANOUT_MAX_ROWS, -1);
        assertThrows(NopException.class, () -> new ConfigShardSelector().init());
    }

    @Test
    public void testFanoutEnabledPositiveMaxRowsInitSucceeds() {
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD_FANOUT_ENABLED, true);
        AppConfig.getConfigProvider().updateConfigValue(CFG_ORM_SHARD_FANOUT_MAX_ROWS, 100);
        new ConfigShardSelector().init();
    }

    // ---------- 未配置分片体系零变更回归 ----------

    @Test
    public void testNoShardConfigZeroBehaviorChange() {
        ConfigShardSelector selector = new ConfigShardSelector();
        selector.init();
        assertTrue(selector.selectAllShards(ENTITY).isEmpty());
        assertTrue(selector.getShardNames(ENTITY).isEmpty());
        assertEquals(MissingShardPolicy.NONE, selector.getMissingShardPolicy(ENTITY));
        assertNull(selector.selectShard(ENTITY, "sid", 1));
        assertNull(selector.selectShardWhenMissing(ENTITY, "sid"));
        assertTrue(selector.selectAllShards(ENTITY).isEmpty());
    }
}
