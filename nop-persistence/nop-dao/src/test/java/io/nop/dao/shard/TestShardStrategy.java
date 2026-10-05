/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.dao.shard;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.function.Supplier;

import static io.nop.dao.DaoErrors.ERR_DAO_SHARD_ALGORITHM_FAIL;
import static io.nop.dao.DaoErrors.ERR_DAO_SHARD_CONFIG_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2320 Phase 1：内置分片算法与配置注册表单测。
 * 覆盖：5算法边界值/负数/非整数分片键/null分片键显式错误、注册表解析、
 * 绑定校验、缺省Empty行为不变。
 */
public class TestShardStrategy extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    private ConfigShardSelector selectorOf(String json) {
        ConfigShardSelector selector = new ConfigShardSelector();
        io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                io.nop.dao.DaoConfigs.CFG_ORM_SHARD, json);
        try {
            selector.init();
        } finally {
            io.nop.api.core.config.AppConfig.getConfigProvider().updateConfigValue(
                    io.nop.dao.DaoConfigs.CFG_ORM_SHARD, null);
        }
        return selector;
    }

    private ShardStrategy strategy(String type, String extraJson) {
        String json = "{\"strategies\":{\"s\":{\"type\":\"" + type + "\"" + extraJson + "}}}";
        return selectorOf(json).getStrategies().get("s");
    }

    private NopException assertConfigError(Supplier<?> action) {
        NopException e = assertThrows(NopException.class, action::get);
        assertEquals(ERR_DAO_SHARD_CONFIG_INVALID.getErrorCode(), e.getErrorCode());
        return e;
    }

    private NopException assertAlgorithmError(Supplier<?> action) {
        NopException e = assertThrows(NopException.class, action::get);
        assertEquals(ERR_DAO_SHARD_ALGORITHM_FAIL.getErrorCode(), e.getErrorCode());
        return e;
    }

    // ---------- mod ----------

    @Test
    public void testModBoundaries() {
        ShardStrategy mod = strategy("mod", ",\"shard-count\":4,\"entities\":[\"a.B\"]");
        assertEquals("0", mod.selectShard("sid", 0L).getShardName());
        assertEquals("3", mod.selectShard("sid", 3L).getShardName());
        assertEquals("0", mod.selectShard("sid", 4L).getShardName());
        // 负数分片键映射到非负分片
        assertEquals("3", mod.selectShard("sid", -1L).getShardName());
        assertEquals("1", mod.selectShard("sid", -3L).getShardName());
        assertEquals(Arrays.asList("0", "1", "2", "3"), mod.shardNames());
    }

    @Test
    public void testModBadValues() {
        ShardStrategy mod = strategy("mod", ",\"shard-count\":4");
        // 非整数分片键显式错误
        assertAlgorithmError(() -> mod.selectShard("sid", "abc"));
        assertAlgorithmError(() -> mod.selectShard("sid", new Object()));
        // null分片键显式错误
        assertAlgorithmError(() -> mod.selectShard("sid", null));
    }

    @Test
    public void testModRequiresShardCount() {
        assertConfigError(() -> strategy("mod", ""));
        assertConfigError(() -> strategy("mod", ",\"shard-count\":0"));
    }

    // ---------- hash-mod ----------

    @Test
    public void testHashModStable() {
        ShardStrategy hashMod = strategy("hash-mod", ",\"shard-count\":8");
        // 同值同分片
        assertEquals(hashMod.selectShard("sid", "abc"), hashMod.selectShard("sid", "abc"));
        // 数值与其字符串表示同分片（跨类型一致性：3与"3"等价）
        assertEquals(hashMod.selectShard("sid", 3), hashMod.selectShard("sid", "3"));
        int shard = Integer.parseInt(hashMod.selectShard("sid", "order-123").getShardName());
        assertTrue(shard >= 0 && shard < 8);
        // 跨进程稳定：FNV-1a("order-123") mod 8 的期望值固定
        assertEquals(String.valueOf(Math.floorMod(ShardAlgorithms.HashModShardAlgorithm.fnv1a("order-123"), 8)),
                hashMod.selectShard("sid", "order-123").getShardName());
    }

    // ---------- range ----------

    @Test
    public void testRangeBoundaries() {
        ShardStrategy range = strategy("range",
                ",\"ranges\":{\"100\":\"0\",\"200\":\"1\",\"300\":\"2\"}");
        assertEquals("0", range.selectShard("sid", 0L).getShardName());
        // 上边界闭区间
        assertEquals("0", range.selectShard("sid", 100L).getShardName());
        assertEquals("1", range.selectShard("sid", 101L).getShardName());
        assertEquals("2", range.selectShard("sid", 300L).getShardName());
        // 超出全部边界显式错误
        assertAlgorithmError(() -> range.selectShard("sid", 301L));
        assertEquals(Arrays.asList("0", "1", "2"), range.shardNames());
    }

    @Test
    public void testRangeRequiresRanges() {
        assertConfigError(() -> strategy("range", ""));
    }

    // ---------- interval ----------

    @Test
    public void testIntervalDayStep() {
        ShardStrategy interval = strategy("interval",
                ",\"interval-step\":\"1d\",\"datetime-lower\":\"2026-01-01\",\"datetime-upper\":\"2026-01-03\"");
        assertEquals("20260101", interval.selectShard("sid", LocalDate.parse("2026-01-01")).getShardName());
        assertEquals("20260102", interval.selectShard("sid", LocalDate.parse("2026-01-02")).getShardName());
        assertEquals(Arrays.asList("20260101", "20260102", "20260103"), interval.shardNames());
        assertAlgorithmError(() -> interval.selectShard("sid", LocalDate.parse("2026-01-04")));
        assertAlgorithmError(() -> interval.selectShard("sid", LocalDate.parse("2025-12-31")));
    }

    @Test
    public void testIntervalMonthStep() {
        ShardStrategy interval = strategy("interval",
                ",\"interval-step\":\"2M\",\"datetime-lower\":\"2026-01-01\",\"datetime-upper\":\"2026-06-30\"");
        assertEquals("20260101", interval.selectShard("sid", LocalDate.parse("2026-02-20")).getShardName());
        assertEquals("20260301", interval.selectShard("sid", LocalDate.parse("2026-04-01")).getShardName());
        assertEquals(Arrays.asList("20260101", "20260301", "20260501"), interval.shardNames());
    }

    @Test
    public void testIntervalRequiresLower() {
        assertConfigError(() -> strategy("interval", ",\"interval-step\":\"1d\""));
        assertConfigError(() -> strategy("interval", ",\"datetime-lower\":\"not-a-date\""));
    }

    // ---------- inline ----------

    @Test
    public void testInlineExpression() {
        ShardStrategy inline = strategy("inline",
                ",\"algorithm-expression\":\"'p' + (value % 2)\"");
        assertEquals("p0", inline.selectShard("sid", 4L).getShardName());
        assertEquals("p1", inline.selectShard("sid", 3L).getShardName());
        // inline不可静态枚举：shardNames显式配置前抛配置错误
        assertConfigError(inline::shardNames);
    }

    @Test
    public void testInlineNullResultFails() {
        ShardStrategy inline = strategy("inline", ",\"algorithm-expression\":\"null\"");
        assertAlgorithmError(() -> inline.selectShard("sid", 1L));
    }

    @Test
    public void testInlineRequiresExpression() {
        assertConfigError(() -> strategy("inline", ""));
    }

    // ---------- 注册表与绑定 ----------

    private static final String FULL_CONFIG = "{\"strategies\":{"
            + "\"order-mod\":{\"type\":\"mod\",\"shard-count\":4,\"entities\":[\"app.Order\"]},"
            + "\"user-hash\":{\"type\":\"hash-mod\",\"shard-count\":2,\"query-space-pattern\":\"ds_{shard}\","
            + "\"missing-shard-policy\":\"default-shard\",\"default-shard\":\"0\",\"entities\":[\"app.User\"]}"
            + "},\"default-strategy\":\"order-mod\"}";

    @Test
    public void testRegistryBinding() {
        ConfigShardSelector selector = selectorOf(FULL_CONFIG);
        assertTrue(selector.hasStrategyConfig());
        assertTrue(selector.isEntityBound("app.Order"));
        // default-strategy回落绑定
        assertTrue(selector.isEntityBound("app.Other"));
        assertTrue(selector.isEntityBound("app.User"));

        assertEquals("2", selector.selectShard("app.Order", "sid", 2L).getShardName());
        assertNull(selector.selectShard("app.Order", "sid", 2L).getQuerySpace());

        // query-space-pattern映射
        ShardSelection sel = selector.selectShard("app.User", "uid", "abc");
        assertEquals("ds_" + sel.getShardName(), sel.getQuerySpace());

        // 缺分片值策略：app.Order缺省fail；app.User显式default-shard（回落分片同样经query-space-pattern映射）
        assertEquals(MissingShardPolicy.FAIL, selector.getMissingShardPolicy("app.Order"));
        assertEquals(MissingShardPolicy.DEFAULT_SHARD, selector.getMissingShardPolicy("app.User"));
        assertEquals(new ShardSelection("ds_0", "0"), selector.selectShardWhenMissing("app.User", "uid"));
        assertNull(selector.selectShardWhenMissing("app.Order", "sid"));

        // DDL分片名全集
        assertEquals(Arrays.asList("0", "1"), selector.getShardNames("app.User"));
        assertEquals(Arrays.asList("0", "1", "2", "3"), selector.getShardNames("app.Other"));
    }

    @Test
    public void testDuplicateEntityBindingRejected() {
        String json = "{\"strategies\":{"
                + "\"a\":{\"type\":\"mod\",\"shard-count\":2,\"entities\":[\"x.Y\"]},"
                + "\"b\":{\"type\":\"mod\",\"shard-count\":2,\"entities\":[\"x.Y\"]}}}";
        assertConfigError(() -> selectorOf(json));
    }

    @Test
    public void testUnknownDefaultStrategyRejected() {
        String json = "{\"strategies\":{\"a\":{\"type\":\"mod\",\"shard-count\":2}},\"default-strategy\":\"missing\"}";
        assertConfigError(() -> selectorOf(json));
    }

    @Test
    public void testBadJsonRejected() {
        assertConfigError(() -> selectorOf("not-a-json{"));
        assertConfigError(() -> selectorOf("[1,2]"));
        assertConfigError(() -> selectorOf("{}"));
        assertConfigError(() -> selectorOf("{\"strategies\":{},\"default-strategy\":null}"));
    }

    @Test
    public void testUnknownTypeRejected() {
        assertConfigError(() -> strategy("unknown-type", ""));
    }

    @Test
    public void testDefaultShardPolicyRequiresDefaultShard() {
        assertConfigError(() -> strategy("mod", ",\"shard-count\":2,\"missing-shard-policy\":\"default-shard\""));
    }

    @Test
    public void testDefaultShardImpliesPolicy() {
        ShardStrategy s = strategy("mod", ",\"shard-count\":2,\"default-shard\":\"1\"");
        assertEquals(MissingShardPolicy.DEFAULT_SHARD, s.getMissingShardPolicy());
        assertEquals(new ShardSelection(null, "1"), s.selectShardWhenMissing("sid"));
    }

    // ---------- 缺省Empty行为不变 ----------

    @Test
    public void testEmptySelectorUnchanged() {
        assertTrue(!EmptyShardSelector.INSTANCE.hasStrategyConfig());
        assertNull(EmptyShardSelector.INSTANCE.selectShard("x.Y", "sid", 1L));
        assertEquals(MissingShardPolicy.NONE, EmptyShardSelector.INSTANCE.getMissingShardPolicy("x.Y"));
        assertNull(EmptyShardSelector.INSTANCE.selectShardWhenMissing("x.Y", "sid"));
        assertTrue(EmptyShardSelector.INSTANCE.getShardNames("x.Y").isEmpty());
    }

    @Test
    public void testUnconfiguredConfigSelectorBehavesEmpty() {
        ConfigShardSelector selector = new ConfigShardSelector();
        selector.init(); // 无配置
        assertTrue(!selector.hasStrategyConfig());
        assertNull(selector.selectShard("x.Y", "sid", 1L));
        assertEquals(MissingShardPolicy.NONE, selector.getMissingShardPolicy("x.Y"));
        assertTrue(selector.getShardNames("x.Y").isEmpty());
    }

    @Test
    public void testTablePattern() {
        ShardStrategy def = strategy("mod", ",\"shard-count\":2");
        assertEquals("T_1", def.buildTableName("T", "1"));
        ShardStrategy custom = strategy("mod",
                ",\"shard-count\":2,\"table-pattern\":\"shard_{table}_{shard}\"");
        assertEquals("shard_T_0", custom.buildTableName("T", "0"));
    }

    @Test
    public void testMissingPolicyParse() {
        assertEquals(MissingShardPolicy.FAIL, MissingShardPolicy.parse(null));
        assertEquals(MissingShardPolicy.FAIL, MissingShardPolicy.parse("fail"));
        assertEquals(MissingShardPolicy.DEFAULT_SHARD, MissingShardPolicy.parse("default-shard"));
        assertThrows(IllegalArgumentException.class, () -> MissingShardPolicy.parse("other"));
    }
}
