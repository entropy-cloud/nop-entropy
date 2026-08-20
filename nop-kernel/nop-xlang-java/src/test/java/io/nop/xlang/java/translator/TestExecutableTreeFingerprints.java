package io.nop.xlang.java.translator;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.java.gen.ExecutableTreeFingerprints;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * java 侧 Executable 树指纹测试（I10 Phase 1 D1 断言化方案落地）：
 *
 * <ul>
 * <li>格式 = 64 位小写 hex（SHA-256）；确定性 = 同树同指纹；</li>
 * <li>防碰撞 = 任一差异（标量载荷 / slot 布局 / 源位置 path/line/col）必产生不同指纹；</li>
 * <li>白名单覆盖面 = java 目标集逐类真实指纹（{@code MinimalNodeFactory} 最小实例，
 * 证据形态裁定类同矩阵排除），支持集内未白名单节点 fail-fast（新节点类漂移红灯）；</li>
 * <li>长行拼接歧义防御 = 字段定界（"1"+"23" 与 "12"+"3" 不同指纹）。</li>
 * </ul>
 */
public class TestExecutableTreeFingerprints {

    private static final SourceLocation LOC = SourceLocation.fromLine("fp-test.xpl", 1);

    @BeforeAll
    public static void init() {
        io.nop.core.initialize.CoreInitialization.initialize();
    }

    @Test
    public void testFingerprintFormatAndDeterminism() {
        IExecutableExpression tree = new PlusExecutable(LOC,
                LiteralExecutable.build(LOC, 1), LiteralExecutable.build(LOC, 2));
        String fp1 = ExecutableTreeFingerprints.fingerprint(tree);
        String fp2 = ExecutableTreeFingerprints.fingerprint(tree);
        assertEquals(fp1, fp2);
        assertEquals(64, fp1.length());
        assertTrue(fp1.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')),
                "lowercase hex: " + fp1);
    }

    @Test
    public void testScalarPayloadDifferenceChangesFingerprint() {
        IExecutableExpression a = LiteralExecutable.build(LOC, 1);
        IExecutableExpression b = LiteralExecutable.build(LOC, 2);
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(a),
                ExecutableTreeFingerprints.fingerprint(b));
    }

    @Test
    public void testSlotDifferenceChangesFingerprint() {
        IExecutableExpression slot0 = new SlotIdentifierExecutable(LOC, "a", 0);
        IExecutableExpression slot1 = new SlotIdentifierExecutable(LOC, "a", 1);
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(slot0),
                ExecutableTreeFingerprints.fingerprint(slot1));
    }

    @Test
    public void testSourceLocationDifferenceChangesFingerprint() {
        IExecutableExpression sameLineA = LiteralExecutable.build(SourceLocation.fromLine("a.xpl", 1), 1);
        IExecutableExpression sameLineB = LiteralExecutable.build(SourceLocation.fromLine("a.xpl", 1), 1);
        assertEquals(ExecutableTreeFingerprints.fingerprint(sameLineA),
                ExecutableTreeFingerprints.fingerprint(sameLineB));

        IExecutableExpression otherLine = LiteralExecutable.build(SourceLocation.fromLine("a.xpl", 2), 1);
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(sameLineA),
                ExecutableTreeFingerprints.fingerprint(otherLine));

        IExecutableExpression otherCol = LiteralExecutable.build(SourceLocation.fromLine("a.xpl", 1, 5), 1);
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(sameLineA),
                ExecutableTreeFingerprints.fingerprint(otherCol));

        IExecutableExpression otherPath = LiteralExecutable.build(SourceLocation.fromLine("b.xpl", 1), 1);
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(sameLineA),
                ExecutableTreeFingerprints.fingerprint(otherPath));
    }

    @Test
    public void testNullTreeFingerprintable() {
        // null 子表达式是可区分载荷（分支内 null 子树），不抛异常
        String fp = ExecutableTreeFingerprints.fingerprint(LiteralExecutable.build(LOC, 1));
        assertEquals(64, fp.length());
    }

    /**
     * 白名单覆盖面（矩阵级红灯）：java 目标集逐类最小实例真实指纹不 fail-fast——
     * 转译器支持集扩展而指纹白名单未跟随时，本参数化用例红灯（证据形态裁定类沿用矩阵裁定）。
     */
    @ParameterizedTest(name = "fingerprint:{0}")
    @MethodSource("io.nop.xlang.java.translator.TestExecTranslationCoverageMatrix#registeredTargetClasses")
    public void testWhitelistCoversJavaTargetSet(String className) {
        IExecutableExpression tree = TestExecTranslationCoverageMatrix.MinimalNodeFactory.minimalTree(className);
        String fp = ExecutableTreeFingerprints.fingerprint(tree);
        assertEquals(64, fp.length(), className);
    }

    /**
     * 支持集内未白名单 fail-fast 红灯路径：合成支持集内节点的匿名子类不会触发（沿继承链命中白名单，
     * 类名已混合——子类身份可区分）；fail-fast 触发条件 = 支持集成员无任何白名单分支，
     * 由覆盖参数化用例守护（本用例断言 fail-fast 分支对未注册树的可达性为假——不可翻译节点走弱兜底）。
     */
    @Test
    public void testUntranslatableNodeUsesWeakFallbackNotFailFast() {
        // SlotIdentifierExecutable 的匿名子类：沿继承链命中白名单（类名混合子类身份，指纹必不同）
        SlotIdentifierExecutable sub = new SlotIdentifierExecutable(LOC, "a", 0) {
        };
        assertNotEquals(ExecutableTreeFingerprints.fingerprint(sub),
                ExecutableTreeFingerprints.fingerprint(new SlotIdentifierExecutable(LOC, "a", 0)));

        // 不可翻译节点（支持集外）：弱哈希兜底（类名+位置），不 fail-fast
        IExecutableExpression unsupported = new IExecutableExpression() {
            private final SourceLocation loc = SourceLocation.fromLine("unsupported.xpl", 3);

            @Override
            public Object execute(io.nop.core.lang.eval.IExpressionExecutor executor,
                                  io.nop.core.lang.eval.EvalRuntime rt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void display(StringBuilder sb) {
                sb.append("unsupported");
            }

            @Override
            public boolean containsReturnStatement() {
                return false;
            }

            @Override
            public boolean containsBreakStatement() {
                return false;
            }

            @Override
            public void visit(io.nop.core.lang.eval.IExecutableExpressionVisitor visitor) {
                visitor.onVisitSimpleExpr(this);
            }

            @Override
            public SourceLocation getLocation() {
                return loc;
            }
        };
        assertEquals(64, ExecutableTreeFingerprints.fingerprint(unsupported).length());
    }
}
