package io.nop.xlang.truffle;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.translate.TranslatedUnit;
import io.nop.xlang.truffle.translate.TranslationCache;
import io.nop.xlang.truffle.translate.TreeFingerprints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 翻译缓存淘汰单测（plan I8 Phase 2 Exit Criteria）：容量上限/LRU 语义（访问刷新新近度、
 * 超限淘汰 eldest）、淘汰可观测（计数器）、淘汰不影响正确性（被淘汰单元重翻译结果一致）；
 * 键语义 = sourceKey + 树指纹纪律保持（淘汰前后同键命中行为不变）。
 */
public class TestTranslationCacheEviction {

    private static final SourceLocation LOC = SourceLocation.fromPath("/cache-eviction-test.xpl");

    private XLangTruffleEval eval;

    @BeforeEach
    public void setUp() {
        eval = new XLangTruffleEval();
    }

    @AfterEach
    public void tearDown() {
        eval.close();
    }

    private static IExecutableExpression literal(int value) {
        return LiteralExecutable.build(LOC, value);
    }

    @Test
    public void testNonPositiveMaxEntriesFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> new TranslationCache(0),
                "non-positive capacity must fail fast");
        assertThrows(IllegalArgumentException.class, () -> new TranslationCache((Integer) null),
                "null capacity must fail fast");
    }

    @Test
    public void testCapacityOverflowEvictsEldest() {
        TranslationCache cache = new TranslationCache(2);
        IExecutableExpression a = literal(1);
        IExecutableExpression b = literal(2);
        IExecutableExpression c = literal(3);

        cache.getOrBuild("/k/a.xpl", a, null);
        cache.getOrBuild("/k/b.xpl", b, null);
        assertEquals(2, cache.size());
        assertEquals(0, cache.getEvictedCount());

        cache.getOrBuild("/k/c.xpl", c, null);
        assertEquals(2, cache.size(), "capacity must hold after inserting a third entry");
        assertEquals(1, cache.getEvictedCount(), "eldest entry (a) must be evicted");
        assertNull(cache.peek("/k/a.xpl", TreeFingerprints.fingerprint(a)), "evicted entry must be gone");
        assertTrue(cache.peek("/k/b.xpl", TreeFingerprints.fingerprint(b)) != null);
        assertTrue(cache.peek("/k/c.xpl", TreeFingerprints.fingerprint(c)) != null);
    }

    @Test
    public void testAccessRefreshesRecency() {
        TranslationCache cache = new TranslationCache(2);
        IExecutableExpression a = literal(1);
        IExecutableExpression b = literal(2);
        IExecutableExpression c = literal(3);

        cache.getOrBuild("/k/a.xpl", a, null);
        cache.getOrBuild("/k/b.xpl", b, null);
        // 访问 a 刷新新近度 → a 不再是 LRU 候选
        cache.peek("/k/a.xpl", TreeFingerprints.fingerprint(a));

        cache.getOrBuild("/k/c.xpl", c, null);
        assertEquals(1, cache.getEvictedCount());
        assertNull(cache.peek("/k/b.xpl", TreeFingerprints.fingerprint(b)),
                "b (least recently used) must be evicted, not a");
        assertTrue(cache.peek("/k/a.xpl", TreeFingerprints.fingerprint(a)) != null,
                "recently accessed a must survive");
    }

    @Test
    public void testEvictedUnitReTranslatesWithEquivalentResult() {
        TranslationCache cache = new TranslationCache(1);
        IExecutableExpression tree = literal(5);

        TranslatedUnit first = cache.getOrBuild("/k/re.xpl", tree, null);
        cache.getOrBuild("/k/other.xpl", literal(6), null); // 淘汰 re
        assertEquals(1, cache.getEvictedCount());
        assertNull(cache.peek("/k/re.xpl", TreeFingerprints.fingerprint(tree)));

        TranslatedUnit rebuilt = cache.getOrBuild("/k/re.xpl", literal(5), null);
        assertNotSame(first, rebuilt, "evicted entry must be re-translated (new unit instance)");
        assertEquals(first.getSourceKey(), rebuilt.getSourceKey());
        assertEquals(first.getTreeFingerprint(), rebuilt.getTreeFingerprint(),
                "key semantics (sourceKey + fingerprint) must be unchanged by eviction");

        // 淘汰后重翻译行为等价：经执行通路断言结果一致（三层断言最小载体 = 返回值含类型）
        XLangTruffleEval.TranslatedEval result = eval.eval("/k/re.xpl", tree, new EvalScopeImpl(),
                DisabledEvalOutput.INSTANCE);
        assertEquals(5, result.getReturnValue(), "re-translated unit must evaluate to the same result");
    }

    @Test
    public void testDefaultCapacityFromConfig() {
        TranslationCache cache = new TranslationCache();
        assertEquals(io.nop.xlang.truffle.XLangTruffleConfigs.CFG_TRUFFLE_TRANSLATION_CACHE_MAX_ENTRIES.get(),
                cache.getMaxEntries(), "default capacity must come from the config reference");
        assertTrue(cache.getMaxEntries() > 0);
    }
}
