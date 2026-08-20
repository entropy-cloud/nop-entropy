package io.nop.xlang.truffle;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.compare.ExecNodeBaseline;
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

import static io.nop.xlang.XLangErrors.ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * truffle 侧覆盖矩阵断言测试（I6 Phase 3 落地，roadmap I6 验收第二项；与 java 侧
 * {@code TestExecTranslationCoverageMatrix} 同基线同口径——基线 = 同一 {@link ExecNodeBaseline}
 * 实体，单一事实源）。
 *
 * <p>矩阵口径（plan Phase 1 定稿）：
 * <ul>
 * <li>注册证据 = 翻译器支持集可编程枚举（{@link ExecToTruffleTranslator#getSupportedNodeClasses()}）
 *   与基线 {@code registeredTarget()}（I2 子集 + A 族 + 并入残余 = 87 类）双向 set 相等；</li>
 * <li>真实翻译验证（非清单自证）：87 类逐类最小实例经翻译器真实翻译成功
 *   （slot 依赖节点程序入口帧包装，嵌套同族变体经顶层类工厂产生）；</li>
 * <li>fail-fast 反证：B 族 35 类逐类断言不支持（pending 可观测、不算通过），树节点类同时
 *   断言翻译报 {@code ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE}；</li>
 * <li>红灯注入：未注册具体节点类（测试域合成 {@code FutureExecutable}）→ 支持集不含 +
 *   翻译 fail-fast + 基线判未归属（新鲜度红灯路径），已注册类绿对照。</li>
 * </ul>
 */
public class TestTruffleCoverageMatrix {

    private static final ExecToTruffleTranslator TRANSLATOR = new ExecToTruffleTranslator();

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // 注册证据：支持集 ↔ 基线 registeredTarget 双向一致（同基线同口径可验证）
    // ------------------------------------------------------------------

    @Test
    public void testSupportSetMatchesBaselineRegisteredTarget() {
        Set<String> supported = new TreeSet<>();
        for (Class<?> cls : ExecToTruffleTranslator.getSupportedNodeClasses())
            supported.add(cls.getSimpleName());
        Set<String> baseline = new TreeSet<>(ExecNodeBaseline.registeredTarget());
        assertEquals(baseline, supported,
                "translator support set must equal baseline registeredTarget (missing="
                        + diff(baseline, supported) + ", extra=" + diff(supported, baseline) + ")");
        assertEquals(ExecNodeBaseline.i3Scope().size() + ExecNodeBaseline.I2_SUBSET.size(),
                baseline.size(), "registeredTarget = i3Scope + i2Subset");
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        TreeSet<String> ret = new TreeSet<>(a);
        ret.removeAll(b);
        return ret;
    }

    // ------------------------------------------------------------------
    // 真实翻译验证：registeredTarget 87 类逐类最小实例翻译成功（非清单自证）
    // ------------------------------------------------------------------

    static Stream<String> registeredTargetClasses() {
        return new TreeSet<>(ExecNodeBaseline.registeredTarget()).stream();
    }

    @ParameterizedTest(name = "translate:{0}")
    @MethodSource("registeredTargetClasses")
    public void testRegisteredClassTranslates(String className) {
        IExecutableExpression tree = CoverageNodes.minimalTree(className);
        io.nop.xlang.truffle.translate.TranslatedUnit unit;
        try {
            unit = TRANSLATOR.translate("matrix/" + className + ".xpl",
                    TreeFingerprints.fingerprint(tree), tree, null);
        } catch (RuntimeException e) {
            throw new IllegalStateException("matrix translate failed for " + className, e);
        }
        assertNotNull(unit);
        assertNotNull(unit.getRootNode());
        assertNotNull(unit.getRootNode().getBody(), "translated AST must have a body node: " + className);
        assertNotNull(unit.getCallTarget(), "translated unit must expose its CallTarget: " + className);
    }

    // ------------------------------------------------------------------
    // fail-fast 反证：B 族 35 类逐类 pending（不支持 + 翻译 fail-fast）
    // ------------------------------------------------------------------

    static Stream<String> bFamilyClasses() {
        return new TreeSet<>(ExecNodeBaseline.bFamily()).stream();
    }

    @ParameterizedTest(name = "pending:{0}")
    @MethodSource("bFamilyClasses")
    public void testBFamilyPendingFailsFast(String className) throws Exception {
        Class<?> cls = Class.forName("io.nop.xlang.exec." + className);
        assertFalse(ExecToTruffleTranslator.isNodeClassSupported(cls),
                "B-family class must not be in translator support set: " + className);
        if (!IExecutableExpression.class.isAssignableFrom(cls) || NO_TREE_FORM.contains(className)) {
            // 无树级翻译形态的类，pending 证据 = 支持集不含（矩阵可观测）：
            // - GenNodeAttrExecutable：GenNode 的属性描述符（非树节点）；
            // - LazyCompiledExecutableFunction：visit() 解析 lazy 编译体（需 XPL 编译器现场），
            //   无独立可 visit 的最小树形态；其分派路径与其余 B 族同一 fail-fast 链。
            return;
        }
        IExecutableExpression tree = CoverageNodes.bMinimalTree(className);
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("matrix/b-" + className + ".xpl",
                        TreeFingerprints.fingerprint(tree), tree, null));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), err.getErrorCode(),
                "B-family node must fail-fast as unsupported: " + className);
        assertNotNull(err.getParam("className"), "unsupported error must report node class name");
    }

    /** 无独立树级形态的 B 族类（pending 证据 = 支持集不含）。 */
    private static final Set<String> NO_TREE_FORM = Set.of("GenNodeAttrExecutable",
            "LazyCompiledExecutableFunction");

    // ------------------------------------------------------------------
    // 红灯注入：未注册具体节点类 → 矩阵红灯（红/绿对照）
    // ------------------------------------------------------------------

    @Test
    public void testRedLightInjectionUnregisteredNodeClass() {
        IExecutableExpression future = new CoverageNodes.FutureExecutable(CoverageNodes.LOC);
        // 红：未注册具体节点类 → 支持集不含 + 翻译 fail-fast + 基线判未归属（新鲜度红灯路径）
        assertFalse(ExecToTruffleTranslator.isNodeClassSupported(future.getClass()),
                "unregistered concrete node class must not resolve as supported (matrix red)");
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("matrix/red-light.xpl",
                        TreeFingerprints.fingerprint(future), future, null));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), err.getErrorCode());
        assertTrue(String.valueOf(err.getParam("className")).contains("FutureExecutable"),
                "unsupported error must report the injected class name");
        assertFalse(ExecNodeBaseline.isClassified("FutureExecutable"),
                "baseline must classify the injected class as unassigned (freshness red)");
        // 绿对照：已注册类判绿（I2/A 族翻译成功、B 族判 pending 但已归属）
        assertTrue(ExecToTruffleTranslator.isNodeClassSupported(
                io.nop.xlang.exec.LiteralExecutable.class), "registered class must resolve (green)");
        assertTrue(ExecNodeBaseline.isClassified("LiteralExecutable"));
        assertTrue(ExecNodeBaseline.isClassified("IfExecutable"), "B-family class is classified (pending green)");
    }
}
