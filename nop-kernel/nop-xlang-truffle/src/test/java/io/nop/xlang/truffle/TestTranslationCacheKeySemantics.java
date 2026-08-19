package io.nop.xlang.truffle;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.translate.TranslationCache;
import io.nop.xlang.truffle.translate.TreeFingerprints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 翻译缓存键语义单测（Phase 3 Exit Criteria）：键 = sourceKey + 树指纹。
 *
 * <ul>
 * <li>同 sourceKey 不同树指纹 → 分键不串用（两次翻译/两个 CallTarget）；</li>
 * <li>同 sourceKey 同树（含不同实例但结构相同的树）→ 命中同一翻译产物（同一 CallTarget）；</li>
 * <li>不同 sourceKey 同结构树 → 分键（resourcePath 防串用语义）；</li>
 * <li>D2 口径：无 resourcePath 动态源按源内容哈希键——不同内容分键、同内容同键。</li>
 * </ul>
 */
public class TestTranslationCacheKeySemantics {

    private static final SourceLocation LOC = SourceLocation.fromPath("/cache-key-test.xpl");

    private XLangTruffleEval eval;

    @BeforeEach
    public void setUp() {
        eval = new XLangTruffleEval();
    }

    @AfterEach
    public void tearDown() {
        eval.close();
    }

    @Test
    public void testSameSourceKeyDifferentFingerprintsGetDistinctUnits() {
        IExecutableExpression treeOne = LiteralExecutable.build(LOC, 1);
        IExecutableExpression treeTwo = LiteralExecutable.build(LOC, 2);
        assertNotEquals(TreeFingerprints.fingerprint(treeOne), TreeFingerprints.fingerprint(treeTwo),
                "different literal values must produce different tree fingerprints");

        XLangTruffleEval.TranslatedEval first = eval.eval("/same-key.xpl", treeOne,
                new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        XLangTruffleEval.TranslatedEval second = eval.eval("/same-key.xpl", treeTwo,
                new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);

        assertEquals(1, first.getReturnValue(), "first tree must return its own value (no stale reuse)");
        assertEquals(2, second.getReturnValue(), "second tree must return its own value (no cross-contamination)");
        assertNotSame(first.getUnit(), second.getUnit(),
                "same sourceKey + different fingerprints must not share the cached translation");
        assertNotSame(first.getUnit().getCallTarget(), second.getUnit().getCallTarget(),
                "same sourceKey + different fingerprints must get distinct CallTargets");
    }

    @Test
    public void testSameSourceKeySameStructureHitsCache() {
        // 结构相同的两棵树（不同实例）指纹相同 → 命中同一翻译产物
        IExecutableExpression treeA = LiteralExecutable.build(LOC, 7);
        IExecutableExpression treeB = LiteralExecutable.build(LOC, 7);
        assertEquals(TreeFingerprints.fingerprint(treeA), TreeFingerprints.fingerprint(treeB));

        XLangTruffleEval.TranslatedEval first = eval.eval("/hit-key.xpl", treeA,
                new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        XLangTruffleEval.TranslatedEval second = eval.eval("/hit-key.xpl", treeB,
                new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);

        assertEquals(7, first.getReturnValue());
        assertEquals(7, second.getReturnValue());
        assertSame(first.getUnit(), second.getUnit(), "same key + same fingerprint must hit the cached unit");
        assertSame(first.getUnit().getCallTarget(), second.getUnit().getCallTarget());
    }

    @Test
    public void testDifferentSourceKeysSameStructureGetDistinctUnits() {
        IExecutableExpression treeA = LiteralExecutable.build(LOC, 3);
        IExecutableExpression treeB = LiteralExecutable.build(LOC, 3);

        XLangTruffleEval.TranslatedEval first = eval.eval("/tenant-a/unit.xpl", treeA,
                new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        XLangTruffleEval.TranslatedEval second = eval.eval("/tenant-b/unit.xpl", treeB,
                new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);

        assertEquals(3, first.getReturnValue());
        assertEquals(3, second.getReturnValue());
        assertNotSame(first.getUnit(), second.getUnit(),
                "different sourceKeys (e.g. tenant-differentiated resourcePaths) must not share translations");
    }

    @Test
    public void testDynamicSourceKeyIsContentHash() {
        String keyAb = XLangTruffleEval.dynamicSourceKey("1 + 2");
        String keyAc = XLangTruffleEval.dynamicSourceKey("1 + 3");
        assertNotEquals(keyAb, keyAc, "different dynamic source content must produce different keys (D2)");
        assertEquals(keyAb, XLangTruffleEval.dynamicSourceKey("1 + 2"),
                "same dynamic source content must produce the same key");
        assertEquals("dyn:", keyAb.substring(0, 4), "dynamic keys carry the dyn: prefix");

        // D2 端到端：动态键驱动翻译缓存分键（不同内容不串用）
        IExecutableExpression treeOne = LiteralExecutable.build(LOC, 1);
        IExecutableExpression treeTwo = LiteralExecutable.build(LOC, 2);
        XLangTruffleEval.TranslatedEval first = eval.eval(XLangTruffleEval.dynamicSourceKey("1 + 2"),
                treeOne, new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        XLangTruffleEval.TranslatedEval second = eval.eval(XLangTruffleEval.dynamicSourceKey("1 + 3"),
                treeTwo, new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
        assertEquals(1, first.getReturnValue());
        assertEquals(2, second.getReturnValue());
        assertNotSame(first.getUnit(), second.getUnit());
    }

    @Test
    public void testCachePeekBySourceKeyAndFingerprintPair() {
        // 缓存 peek 口径：键 = sourceKey + 树指纹（翻译产物按此二元组可再定位）
        TranslationCache cache = new TranslationCache();
        IExecutableExpression tree = LiteralExecutable.build(LOC, 5);
        io.nop.xlang.truffle.translate.TranslatedUnit unit = cache.getOrBuild("/peek.xpl", tree, null);
        assertEquals("/peek.xpl", unit.getSourceKey());
        assertEquals(TreeFingerprints.fingerprint(tree), unit.getTreeFingerprint());
        assertSame(unit, cache.peek("/peek.xpl", TreeFingerprints.fingerprint(tree)));
        assertEquals(null, cache.peek("/peek.xpl", TreeFingerprints.fingerprint(LiteralExecutable.build(LOC, 6))));
        assertEquals(1, cache.size());
    }
}
