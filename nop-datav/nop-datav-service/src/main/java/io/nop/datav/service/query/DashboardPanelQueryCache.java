package io.nop.datav.service.query;

import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.CacheStats;
import io.nop.commons.cache.LocalCache;
import io.nop.core.lang.json.JsonTool;
import io.nop.datav.biz.PanelDataResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_CACHE_MAX_ENTRIES;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_CACHE_MAX_ROWS_PER_ENTRY;
import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_DASHBOARD_QUERY_CACHE_TTL_SECONDS;

/**
 * getDashboardData 批量路径的查询结果缓存（plan 2026-08-15-0004-3，裁定见 runtime-design.md §8.3/§8.4）。
 *
 * <p>形态：平台 {@link LocalCache} + {@link CacheConfig}（进程内、单节点语义；模块先例
 * {@code NopDatavShareAccessGuard}）。TTL-only 失效（staleness 上界 = TTL）、容量上界驱逐、
 * 单条目行数准入上界（超限不缓存不截断）。面板级失败条目与无数据集条目不经本缓存（失败路径 binder
 * 抛 NopException 不产生可缓存值；无数据集条目在 binder 缓存判定点之前返回）。</p>
 *
 * <p>缓存故障降级（§8.4 裁定）：读/写异常显式 WARN 记录并降级直查——绝不因缓存故障返回错误数据
 * （正确性优先于可用性增益，非吞异常）。</p>
 */
public class DashboardPanelQueryCache {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardPanelQueryCache.class);

    /** rowLimit 为 null（运行时批量路径不限制行数）时的键尾段。 */
    private static final String NO_ROW_LIMIT = "-";

    private final LocalCache<String, PanelDataResult> cache;
    private final int maxRowsPerEntry;

    public DashboardPanelQueryCache(int maxEntries, long ttlMillis, int maxRowsPerEntry) {
        this.cache = LocalCache.newCache("nop-datav-dashboard-query",
                CacheConfig.newConfig(Math.max(1, maxEntries), ttlMillis));
        this.maxRowsPerEntry = maxRowsPerEntry;
    }

    /** 按配置构造（容量/TTL/准入上界；启用开关由调用方按请求判定，不入本类）。 */
    public static DashboardPanelQueryCache fromConfigs() {
        return new DashboardPanelQueryCache(
                CFG_DATAV_DASHBOARD_QUERY_CACHE_MAX_ENTRIES.get(),
                CFG_DATAV_DASHBOARD_QUERY_CACHE_TTL_SECONDS.get() * 1000L,
                CFG_DATAV_DASHBOARD_QUERY_CACHE_MAX_ROWS_PER_ENTRY.get());
    }

    /**
     * 缓存键（§8.4 裁定）：数据集身份 + 求值后参数 + rowLimit——全部影响查询结果的输入。
     * 求值后参数 Map 顺序由 paramMapping 决定（确定性），JsonTool 序列化无碰撞；
     * componentType/panelId 不入键（SQL 结果与面板呈现无关）。
     */
    public static String buildKey(String refDatasetId, Map<String, Object> evaluatedParams, Integer rowLimit) {
        return "ds:" + refDatasetId
                + "|params:" + JsonTool.stringify(evaluatedParams == null ? Map.of() : evaluatedParams)
                + "|limit:" + (rowLimit == null ? NO_ROW_LIMIT : rowLimit);
    }

    /** 读取；缓存基础设施异常 WARN 后降级返回 null（直查）。 */
    public PanelDataResult get(String key) {
        try {
            return cache.getIfPresent(key);
        } catch (RuntimeException e) {
            LOG.warn("nop.datav.dashboard-query-cache.read-fail:degrade-to-direct-query:key={}", key, e);
            return null;
        }
    }

    /**
     * 成功结果回填：行数超过准入上界（§8.5 max-rows-per-entry）不缓存（不截断——绝不从缓存返回
     * 截断数据）；缓存基础设施异常 WARN 后放弃回填（降级直查语义）。
     */
    public void tryPut(String key, PanelDataResult result) {
        if (result == null || !result.isHasDataset()) {
            return;
        }
        if (result.getRows().size() > maxRowsPerEntry) {
            return;
        }
        try {
            cache.put(key, result);
        } catch (RuntimeException e) {
            LOG.warn("nop.datav.dashboard-query-cache.write-fail:degrade-to-direct-query:key={}", key, e);
        }
    }

    /** 测试可观测（命中/未命中计数，Exit Criteria 断言用）。 */
    public long getHitCount() {
        CacheStats stats = cache.stats();
        return stats == null ? 0 : stats.getHitCount();
    }

    /** 测试可观测：未命中计数。 */
    public long getMissCount() {
        CacheStats stats = cache.stats();
        return stats == null ? 0 : stats.getMissCount();
    }

    /** 测试 hygiene：清空全部缓存条目。 */
    public void clearForTest() {
        cache.clear();
    }
}
