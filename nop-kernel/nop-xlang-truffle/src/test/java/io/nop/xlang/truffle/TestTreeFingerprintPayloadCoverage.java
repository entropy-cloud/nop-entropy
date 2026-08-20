package io.nop.xlang.truffle;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.compare.ExecNodeBaseline;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.translate.ExecToTruffleTranslator;
import io.nop.xlang.truffle.translate.TreeFingerprints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * 树指纹载荷覆盖单测（Phase 2 Exit Criteria，防缓存串用——设计 truffle 02 §七自认最危险
 * 缺陷形态的硬验收）：truffleRegisteredTarget 全部 120 类（I2 子集 + 覆盖 A 五族 + 并入残余 + 覆盖 B 三族（I7 扩展），
 * 无语义载荷类除外；无树形态类对齐矩阵证据形态口径跳过）——
 *
 * <ul>
 * <li>仅标量语义载荷不同的两棵树 → 指纹不同（同 sourceKey 不串用缓存）；</li>
 * <li>含子表达式的复合节点类另构造仅子树不同（同标量载荷）的树对 → 指纹不同；</li>
 * <li>载荷覆盖对支持集无缺口 = 可翻译类不可能静默落入弱哈希兜底（兜底分支仅对不可翻译类
 * 生效，反证见本测试）。</li>
 * </ul>
 */
public class TestTreeFingerprintPayloadCoverage {

    /**
     * 无子表达式的节点类（子树变体不适用，标量载荷变体已覆盖其全量语义载荷）。
     */
    private static final Set<String> NO_CHILD_CLASSES = Set.of(
            "LiteralExecutable", "SlotIdentifierExecutable", "StaticGetterGetPropertyExecutable",
            "ScopeIdentifierExecutable", "GlobalVarExecutable", "ScopeSelfIncExecutable",
            "ScopeSelfDecExecutable", "ReferenceIdentifierExecutable", "ReferenceSelfIncExecutable",
            "ReferenceSelfDecExecutable", "RenewReferenceExecutable", "InitRefSlotExecutable",
            "EnhanceRefSlotExecutable", "DebugIdentifierExecutable", "SelfIncExecutable",
            "SelfDecExecutable", "CloneLiteralExecutable",
            "OutputTextExecutable", "FunctionalAdapterExecutable");

    /**
     * 无任何语义载荷的节点类（NullExecutable 单例：任意两实例指纹相同是正确语义，排除变体测试）。
     */
    private static final Set<String> NO_PAYLOAD_CLASSES = Set.of("NullExecutable",
            "BreakExecutable", "ContinueExecutable", "LocationFunction");

    /**
     * 无标量载荷的复合节点类（类名 + 子树即全载荷，"仅语义载荷不同"经子树差异表达）。
     */
    private static final Set<String> NO_SCALAR_PAYLOAD_CLASSES = Set.of(
            "PlusExecutable", "MinusExecutable", "MultiplyExecutable", "DivideExecutable",
            "AndExecutable", "OrExecutable", "NotExecutable",
            "EqExecutable", "NeExecutable", "GtExecutable", "GeExecutable", "LtExecutable", "LeExecutable",
            "StrictEqExecutable", "StrictNeExecutable", "GuardNotNullExecutable",
            "SeqExecutable", "BlockExecutable", "ReturnNullExecutable",
            "NewListExecutable", "NewMapExecutable", "SetAttrExecutable",
            "NegExecutable", "BitNotExecutable", "TypeOfExecutable",
            "EqNullExecutable", "NeNullExecutable", "StrictEqNullExecutable", "StrictNeNullExecutable",
            "NullCoalesceExecutable", "PropInExecutable", "ConcatExecutable", "RangeExecutable",
            "DebugExecutable",
            // 覆盖 B（I7）：无标量载荷复合类（语义载荷 = 子树，经子树差异表达）
            "IfExecutable", "ForExecutable", "WhileExecutable", "DoWhileExecutable",
            "ReturnExecutable", "ThrowErrorCodeExecutable", "ThrowExceptionExecutable",
            "OutputValueExecutable", "GenXJsonExecutable", "GenNodeExecutable",
            "CollectJsonExecutable", "CollectSqlExecutable", "CollectTextExecutable");

    @BeforeAll
    public static void init() {
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    /**
     * 无独立树级形态类（对齐矩阵证据形态口径：宿主载体/支持集成员 + 载荷反证——不适用变体测试）。
     */
    private static final Set<String> NO_TREE_FORM_CLASSES = Set.of("GenNodeAttrExecutable",
            "LazyCompiledExecutableFunction");

    static Stream<String> payloadClasses() {
        return new TreeSet<>(ExecNodeBaseline.truffleRegisteredTarget()).stream()
                .filter(name -> !NO_PAYLOAD_CLASSES.contains(name))
                .filter(name -> !NO_TREE_FORM_CLASSES.contains(name));
    }

    static Stream<String> compositeClasses() {
        return new TreeSet<>(ExecNodeBaseline.truffleRegisteredTarget()).stream()
                .filter(name -> !NO_PAYLOAD_CLASSES.contains(name))
                .filter(name -> !NO_CHILD_CLASSES.contains(name))
                .filter(name -> !NO_TREE_FORM_CLASSES.contains(name));
    }

    @ParameterizedTest(name = "payload:{0}")
    @MethodSource("payloadClasses")
    public void testScalarPayloadDifferenceChangesFingerprint(String className) {
        // 无标量载荷的复合类：语义载荷 = 子树，经子树差异表达；其余：仅标量载荷不同
        IExecutableExpression treeA;
        IExecutableExpression treeB;
        if (NO_SCALAR_PAYLOAD_CLASSES.contains(className)) {
            treeA = CoverageNodes.subtreeVariant(className, 1);
            treeB = CoverageNodes.subtreeVariant(className, 2);
        } else {
            treeA = CoverageNodes.payloadVariant(className, 0);
            treeB = CoverageNodes.payloadVariant(className, 1);
        }
        assertNotEquals(TreeFingerprints.fingerprint(treeA), TreeFingerprints.fingerprint(treeB),
                className + ": trees differing only in scalar semantic payload must get different fingerprints");
    }

    @ParameterizedTest(name = "subtree:{0}")
    @MethodSource("compositeClasses")
    public void testSubtreeDifferenceChangesFingerprint(String className) {
        IExecutableExpression treeA = CoverageNodes.subtreeVariant(className, 1);
        IExecutableExpression treeB = CoverageNodes.subtreeVariant(className, 2);
        assertNotEquals(TreeFingerprints.fingerprint(treeA), TreeFingerprints.fingerprint(treeB),
                className + ": trees with same scalar payload but different subtrees must get different fingerprints");
    }

    @Test
    public void testPayloadDifferencePreventsCacheCrossUseUnderSameSourceKey() {
        // 端到端：同 sourceKey（同 resourcePath）+ 载荷不同（租户 Delta/热改形态）→ 不串用旧缓存 AST
        try (XLangTruffleEval eval = new XLangTruffleEval()) {
            IExecutableExpression treeA = CoverageNodes.payloadVariant("GetPropertyExecutable", 0);
            IExecutableExpression treeB = CoverageNodes.payloadVariant("GetPropertyExecutable", 1);
            XLangTruffleEval.TranslatedEval first = eval.eval("/tenant/unit.xpl", treeA,
                    new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
            XLangTruffleEval.TranslatedEval second = eval.eval("/tenant/unit.xpl", treeB,
                    new EvalScopeImpl(), DisabledEvalOutput.INSTANCE);
            assertNotSame(first.getUnit(), second.getUnit(),
                    "same sourceKey + payload-different trees must not share cached translation");
            assertNotEquals(first.getUnit().getTreeFingerprint(), second.getUnit().getTreeFingerprint());
        }
    }

    @Test
    public void testWeakHashFallbackConfinedToUntranslatableNodes() {
        // 反证：不可翻译节点（支持集不含）允许弱哈希兜底（翻译必然 fail-fast，指纹值不进缓存）；
        // 可翻译类的全量载荷覆盖由上述 87 类参数化变体测试保证——兜底不可能命中可翻译类
        SourceLocation loc = SourceLocation.fromLine("weak-hash.xpl", 1);
        CoverageNodes.FutureExecutable unsupported = new CoverageNodes.FutureExecutable(loc);
        assertFalse(ExecToTruffleTranslator.isNodeClassSupported(unsupported.getClass()),
                "injected node must not resolve as translatable");
        TreeFingerprints.fingerprint(unsupported);
    }

    @Test
    public void testFingerprintOnlyDependsOnStructureNotIdentity() {
        // 结构相同（同载荷同位置）的两棵树 → 指纹相同（缓存命中语义不被破坏）
        IExecutableExpression treeA = CoverageNodes.minimalTree("GetPropertyExecutable");
        IExecutableExpression treeB = CoverageNodes.minimalTree("GetPropertyExecutable");
        assertEquals(TreeFingerprints.fingerprint(treeA), TreeFingerprints.fingerprint(treeB),
                "structurally identical trees must share the fingerprint");
        IExecutableExpression litA = LiteralExecutable.build(CoverageNodes.LOC, 7);
        IExecutableExpression litB = LiteralExecutable.build(CoverageNodes.LOC, 7);
        assertEquals(TreeFingerprints.fingerprint(litA), TreeFingerprints.fingerprint(litB));
    }
}
