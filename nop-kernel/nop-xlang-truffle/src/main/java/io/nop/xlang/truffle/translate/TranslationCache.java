package io.nop.xlang.truffle.translate;

import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.lang.XLangLanguage;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译缓存（设计 truffle 02 §七）：键 = sourceKey + 树指纹（sourceKey = resourcePath，
 * 或无 resourcePath 动态源按源内容哈希键——plan I5 决策 D2），language 实例作用域
 * （SHARED 下跨 Context 复用）。不同树自然分键，不依赖失效通知；缓存淘汰（容量上限/LRU）
 * 归 I8，本缓存只增不淘汰。
 */
public final class TranslationCache {

    private final Map<CacheKey, TranslatedUnit> cache = new ConcurrentHashMap<>();

    private final ExecToTruffleTranslator translator = new ExecToTruffleTranslator();

    public TranslatedUnit getOrBuild(String sourceKey, IExecutableExpression tree, XLangLanguage language) {
        Objects.requireNonNull(sourceKey, "sourceKey");
        Objects.requireNonNull(tree, "tree");
        long fingerprint = TreeFingerprints.fingerprint(tree);
        return cache.computeIfAbsent(new CacheKey(sourceKey, fingerprint),
                key -> translator.translate(key.sourceKey, key.fingerprint, tree, language));
    }

    public TranslatedUnit peek(String sourceKey, long fingerprint) {
        return cache.get(new CacheKey(sourceKey, fingerprint));
    }

    public int size() {
        return cache.size();
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
