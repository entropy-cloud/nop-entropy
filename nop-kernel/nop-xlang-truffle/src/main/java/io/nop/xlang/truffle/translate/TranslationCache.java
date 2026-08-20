package io.nop.xlang.truffle.translate;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.XLangTruffleConfigs;
import io.nop.xlang.truffle.lang.XLangLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;

/**
 * 翻译缓存（设计 truffle 02 §七）：键 = sourceKey + 树指纹（sourceKey = resourcePath，
 * 或无 resourcePath 动态源按源内容哈希键——plan I5 决策 D2），language 实例作用域
 * （SHARED 下跨 Context 复用）。不同树自然分键，不依赖失效通知。
 *
 * <p><b>容量上限/LRU 淘汰</b>（plan I8 Phase 1 §5 定稿）：{@code LinkedHashMap(accessOrder=true)}
 * + monitor 守护；<b>翻译在锁外执行</b>（miss → 并行 translate → 重入锁收敛：并发重复翻译
 * 为良性竞争，先入库者胜）——与切换前 CHM {@code computeIfAbsent} 的并行翻译语义等价。
 * 淘汰不影响正确性：被淘汰单元下次 getOrBuild 重翻译（行为等价）。淘汰可观测：
 * {@link #getEvictedCount()} 计数器 + DEBUG 日志。
 *
 * <p><b>单元级翻译失败观测</b>（plan I8 Phase 1 §6）：translate 抛错路径经 language 上报
 * {@link TranslationFailureEvent}（内置记录器 + 已注册消费者），随后<b>原样重抛</b>——
 * fail-fast 语义不变，事件是观测增量不是降级；language 为 null（测试直构缓存路径）时
 * 无观测载体，维持纯 fail-fast。
 */
public final class TranslationCache {

    private static final Logger LOG = LoggerFactory.getLogger(TranslationCache.class);

    private final int maxEntries;

    private final Object lock = new Object();

    private final LinkedHashMap<CacheKey, TranslatedUnit> lru;

    private final ExecToTruffleTranslator translator = new ExecToTruffleTranslator();

    private final AtomicLong evictedCount = new AtomicLong();

    public TranslationCache() {
        this(XLangTruffleConfigs.CFG_TRUFFLE_TRANSLATION_CACHE_MAX_ENTRIES.get());
    }

    /**
     * @param maxEntries 容量上限（正数；超出按 LRU 淘汰）
     */
    public TranslationCache(Integer maxEntries) {
        if (maxEntries == null || maxEntries <= 0)
            throw new IllegalArgumentException("translation cache max entries must be positive: " + maxEntries);
        this.maxEntries = maxEntries;
        this.lru = new LinkedHashMap<>(16, 0.75f, true);
    }

    public TranslatedUnit getOrBuild(String sourceKey, IExecutableExpression tree, XLangLanguage language) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(tree, "tree");
        long fingerprint = TreeFingerprints.fingerprint(tree);
        CacheKey key = new CacheKey(sourceKey, fingerprint);

        TranslatedUnit cached = peek(key);
        if (cached != null)
            return cached;

        TranslatedUnit built;
        try {
            // 锁外并行翻译：不同 sourceKey 互不阻塞（translator 无实例字段，单实例并发安全——
            // plan I8 Phase 1 §2 翻译侧裁定）
            built = translator.translate(sourceKey, fingerprint, tree, language);
        } catch (RuntimeException e) {
            reportFailure(sourceKey, e, language);
            throw e;
        }
        return store(key, built);
    }

    public TranslatedUnit peek(String sourceKey, long fingerprint) {
        return peek(new CacheKey(sourceKey, fingerprint));
    }

    private TranslatedUnit peek(CacheKey key) {
        synchronized (lock) {
            return lru.get(key);
        }
    }

    private TranslatedUnit store(CacheKey key, TranslatedUnit built) {
        synchronized (lock) {
            TranslatedUnit existing = lru.get(key);
            if (existing != null)
                return existing;
            lru.put(key, built);
            evictOverflow();
            return built;
        }
    }

    private void evictOverflow() {
        while (lru.size() > maxEntries) {
            Iterator<Map.Entry<CacheKey, TranslatedUnit>> it = lru.entrySet().iterator();
            if (!it.hasNext())
                return;
            Map.Entry<CacheKey, TranslatedUnit> eldest = it.next();
            it.remove();
            evictedCount.incrementAndGet();
            LOG.debug("nop.xlang.truffle.translation-cache.evict: sourceKey={}, fingerprint={}",
                    eldest.getKey().sourceKey, eldest.getKey().fingerprint);
        }
    }

    public int size() {
        synchronized (lock) {
            return lru.size();
        }
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    /**
     * 累计淘汰条目数（淘汰可观测计数器）。
     */
    public long getEvictedCount() {
        return evictedCount.get();
    }

    private static void reportFailure(String sourceKey, RuntimeException error, XLangLanguage language) {
        if (language == null)
            return;
        String nodeClassName = null;
        SourceLocation location = null;
        if (error instanceof NopEvalException) {
            NopEvalException ne = (NopEvalException) error;
            Object className = ne.getParam(ARG_CLASS_NAME);
            if (className != null)
                nodeClassName = String.valueOf(className);
            location = ne.getErrorLocation();
        }
        String reason = error.getMessage() == null ? error.getClass().getName() : error.getMessage();
        language.reportTranslationFailure(
                new TranslationFailureEvent(sourceKey, nodeClassName, location, reason, error));
    }

    private static final class CacheKey {
        private final String sourceKey;

        private final long fingerprint;

        CacheKey(String sourceKey, long fingerprint) {
            this.sourceKey = sourceKey;
            this.fingerprint = fingerprint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof CacheKey))
                return false;
            CacheKey key = (CacheKey) o;
            return fingerprint == key.fingerprint && sourceKey.equals(key.sourceKey);
        }

        @Override
        public int hashCode() {
            return sourceKey.hashCode() * 31 + Long.hashCode(fingerprint);
        }
    }
}
