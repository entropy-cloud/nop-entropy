package io.nop.dao.jdbc;

import io.nop.api.core.config.AppConfig;
import io.nop.commons.cache.ICache;
import io.nop.commons.cache.ICacheProvider;
import io.nop.commons.cache.MapCache;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.DaoConfigs;
import io.nop.dao.jdbc.impl.JdbcTemplateImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 2.3-C5-002（plan 2553 裁定 M）钉：查询缓存单条目行数上界——超限放弃缓存 + WARN。
 * pin-a：CFG_DAO_QUERY_CACHE_MAX_ROWS=1 且结果集 2 行时条目不入缓存（后续执行仍走 DB）；
 * pin-b：上界恢复 0（不设上界档）后同查询正常入缓存，后续执行读缓存（不再打 DB）。
 */
public class TestQueryCacheEntryLimit extends JdbcTestCase {

    private static final String CACHE_NAME = "limit-test-cache";
    private static final String CACHE_KEY = "k1";

    static class SimpleCacheProvider implements ICacheProvider {
        // 同名 cache 必须返回同一实例，否则测试侧 cache() 观察不到 JdbcTemplateImpl 写入的条目
        final java.util.concurrent.ConcurrentHashMap<String, ICache<Object, Object>> caches =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <K, V> ICache<K, V> getCache(String name) {
            return (ICache<K, V>) caches.computeIfAbsent(name, n -> new MapCache<Object, Object>(n, true));
        }

        @Override
        public void clearAllCache() {
            caches.values().forEach(ICache::clear);
        }
    }

    private JdbcTemplateImpl template() {
        return (JdbcTemplateImpl) jdbc();
    }

    private SQL cachedSelect() {
        return SQL.begin().sql("select a, b, c from my_entity order by id").cache(CACHE_NAME, CACHE_KEY).end();
    }

    private ICache<Object, Object> cache() {
        ICacheProvider provider = template().getCacheProvider();
        assertNotNull(provider, "cache provider must be configured by the test");
        return provider.getCache(CACHE_NAME);
    }

    private void insertSecondRow() {
        jdbc().executeUpdate(new SQL(
                "insert into my_entity(id, a, b, c, d) values(2, 5, 6, 'DD', date '2002-01-03')"));
    }

    /**
     * pin-a：上界=1、结果集 2 行（超限）→ 条目放弃缓存。变异红形态：去掉行数上界守卫
     * （saveCacheData 无条件 cache.put）后，update 后再次查询读到陈旧缓存值 → 断言红。
     */
    @Test
    public void pin_a_cacheEntryOverLimitNotCached() {
        Integer oldLimit = DaoConfigs.CFG_DAO_QUERY_CACHE_MAX_ROWS.get();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_QUERY_CACHE_MAX_ROWS, 1);
        try {
            template().setCacheProvider(new SimpleCacheProvider());
            insertSecondRow();

            List<Map<String, Object>> first = template().findAll(cachedSelect());
            assertEquals(2, first.size());
            // 第二次执行：若守卫失效条目已入缓存，这里会直接读缓存
            List<Map<String, Object>> second = template().findAll(cachedSelect());
            assertEquals(2, second.size());

            // 钉机制面：超限条目不得驻留缓存
            assertNull(cache().get(CACHE_KEY),
                    "result set exceeding CFG_DAO_QUERY_CACHE_MAX_ROWS must not be cached (2.3-C5-002)");

            // 钉行为面：条目未缓存，数据变更后再次查询仍打到 DB，读到新值
            jdbc().executeUpdate(new SQL("update my_entity set a=99 where id=1"));
            List<Map<String, Object>> afterUpdate = template().findAll(cachedSelect());
            assertEquals(99, afterUpdate.get(0).get("A"),
                    "over-limit query must hit DB on every execution (entry not cached)");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_QUERY_CACHE_MAX_ROWS, oldLimit);
        }
    }

    /**
     * pin-b：上界恢复缺省 0（不设上界档）→ 同查询正常入缓存，后续执行读缓存
     * （数据变更后仍读到缓存的旧值，证明 DB 未被再次访问）。钉的是守卫不误伤正常路径。
     */
    @Test
    public void pin_b_cacheEntryWithinLimitIsCached() {
        Integer oldLimit = DaoConfigs.CFG_DAO_QUERY_CACHE_MAX_ROWS.get();
        AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_QUERY_CACHE_MAX_ROWS, oldLimit == null ? 0 : oldLimit);
        try {
            template().setCacheProvider(new SimpleCacheProvider());
            insertSecondRow();

            template().findAll(cachedSelect());
            assertNotNull(cache().get(CACHE_KEY),
                    "result set within limit (config=0, no bound) must be cached");

            // 数据变更后再次查询读到缓存旧值：第二次执行跳过了 DB
            jdbc().executeUpdate(new SQL("update my_entity set a=99 where id=1"));
            List<Map<String, Object>> afterUpdate = template().findAll(cachedSelect());
            assertEquals(3, afterUpdate.get(0).get("A"),
                    "cached entry must serve the second execution without hitting DB");
        } finally {
            AppConfig.getConfigProvider().updateConfigValue(DaoConfigs.CFG_DAO_QUERY_CACHE_MAX_ROWS, oldLimit);
        }
    }
}
