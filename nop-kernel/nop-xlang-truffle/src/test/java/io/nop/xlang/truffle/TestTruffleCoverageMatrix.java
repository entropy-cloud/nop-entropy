package io.nop.xlang.truffle;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.compare.ExecNodeBaseline;
import io.nop.xlang.exec.LazyCompiledExecutableFunction;
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
 * truffle 侧覆盖矩阵断言测试（I6 落地，I7 闭环——roadmap I7 验收第二项；与 java 侧
 * {@code TestExecTranslationCoverageMatrix} 同基线同口径——基线 = 同一 {@link ExecNodeBaseline}
 * 实体，单一事实源）。
 *
 * <p>矩阵口径（I7 闭环）：
 * <ul>
 * <li>注册证据 = 翻译器支持集可编程枚举（{@link ExecToTruffleTranslator#getSupportedNodeClasses()}）
 *   与基线 truffle 侧目标集 {@code ExecNodeBaseline.truffleRegisteredTarget()}（I7 收敛后全量
 *   120 = registeredTarget 87 + B 族 33）双向 set 相等；<b>B 族 pending 集清零</b>（逐类支持断言）；</li>
 * <li>真实翻译验证（非清单自证）：120 类逐类最小实例经翻译器真实翻译成功（slot 依赖节点程序
 *   入口帧包装，嵌套同族变体经顶层类工厂产生；无独立树形态类按 I4 证据形态口径：
 *   GenNodeAttrExecutable 宿主载体 / LazyCompiledExecutableFunction 支持集成员 + null 载荷
 *   fail-fast 反证）；</li>
 * <li>fail-fast 反证（边界收缩后新边界）：改判排除类（I4 边缘裁定两类）遇即 fail-fast；
 *   LazyCompiled null 载荷显式 fail-fast；</li>
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
    // 注册证据：支持集 ↔ truffle 侧目标集（truffleRegisteredTarget，I7 收敛后 120）双向一致
    // ------------------------------------------------------------------

    @Test
    public void testSupportSetMatchesTruffleRegisteredTarget() {
        Set<String> supported = new TreeSet<>();
        for (Class<?> cls : ExecToTruffleTranslator.getSupportedNodeClasses())
            supported.add(cls.getSimpleName());
        Set<String> baseline = new TreeSet<>(ExecNodeBaseline.truffleRegisteredTarget());
        assertEquals(baseline, supported,
                "translator support set must equal truffle registered target (missing="
                        + diff(baseline, supported) + ", extra=" + diff(supported, baseline) + ")");
        assertEquals(ExecNodeBaseline.javaTargetSet().size(), baseline.size(),
                "truffle target set converged to per-backend full set at I7 closure (= javaTargetSet)");
    }

    /** B 族 pending 清零断言：33 类逐类支持（闭环前为逐类不支持反证）。 */
    @Test
    public void testBFamilyPendingSetCleared() {
        for (String name : ExecNodeBaseline.bFamily()) {
            try {
                Class<?> cls = Class.forName("io.nop.xlang.exec." + name);
                assertTrue(ExecToTruffleTranslator.isNodeClassSupported(cls),
                        "B-family class must be supported after I7 closure: " + name);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("B-family class not found: " + name, e);
            }
        }
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        TreeSet<String> ret = new TreeSet<>(a);
        ret.removeAll(b);
        return ret;
    }

    // ------------------------------------------------------------------
    // 真实翻译验证：truffle 侧目标集 120 类逐类最小实例翻译成功（非清单自证）
    // ------------------------------------------------------------------

    /** 无独立树级翻译形态的类（证据形态口径，对齐 I4 java 侧裁定）。 */
    private static final Set<String> NO_TREE_FORM = Set.of("GenNodeAttrExecutable",
            "LazyCompiledExecutableFunction");

    static Stream<String> registeredTargetClasses() {
        return new TreeSet<>(ExecNodeBaseline.truffleRegisteredTarget()).stream();
    }

    @ParameterizedTest(name = "translate:{0}")
    @MethodSource("registeredTargetClasses")
    public void testRegisteredClassTranslates(String className) throws ClassNotFoundException {
        // 无树级翻译形态的类证据形态（I4 口径消费）：
        // - GenNodeAttrExecutable：GenNode 的属性描述符（非树节点），宿主载体形态覆盖；
        // - LazyCompiledExecutableFunction：visit() 解析 lazy 编译体（需 XPL 编译器现场），
        //   无独立可 visit 的最小树形态；证据 = 支持集成员 + null 载荷 fail-fast 反证
        //   （testLazyCompiledNullPayloadFailsFast）。
        if (NO_TREE_FORM.contains(className)) {
            Class<?> cls = Class.forName("io.nop.xlang.exec." + className);
            assertTrue(ExecToTruffleTranslator.isNodeClassSupported(cls),
                    "no-tree-form class must still be a support set member: " + className);
            return;
        }
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
    // fail-fast 反证（I7 边界收缩后的新边界）
    // -------------------------------------------------------------------

    /** I4 改判排除类（边缘裁定）遇即 fail-fast：支持集不含 + 翻译报不支持。 */
    @ParameterizedTest(name = "excluded:{0}")
    @MethodSource("excludedRedefinedClasses")
    public void testExcludedClassFailsFast(String className) {
        assertFalse(ExecNodeBaseline.bFamily().contains(className),
                "excluded class must not be in B family: " + className);
        IExecutableExpression tree = CoverageNodes.excludedMinimalTree(className);
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("matrix/excluded-" + className + ".xpl",
                        TreeFingerprints.fingerprint(tree), tree, null));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), err.getErrorCode(),
                "excluded class must fail-fast as unsupported: " + className);
        assertTrue(String.valueOf(err.getParam("className")).contains(className),
                "unsupported error must report the excluded class name");
    }

    static Stream<String> excludedRedefinedClasses() {
        return Stream.of("ReturnScopeValuesExecutable", "ExecutableFunctionEvalAction");
    }

    /**
     * LazyCompiled null 载荷显式 fail-fast（I4 Phase 1 §5 同类决策伞：force-compile 不可解析
     * 载荷 → unsupported；矩阵无独立树形态类的反证路径）。
     */
    @Test
    public void testLazyCompiledNullPayloadFailsFast() {
        LazyCompiledExecutableFunction lazy =
                new LazyCompiledExecutableFunction(CoverageNodes.LOC, "__fn_lazy",
                        IExecutableExpression.EMPTY_EXPRS, null);
        assertTrue(ExecToTruffleTranslator.isNodeClassSupported(lazy.getClass()),
                "LazyCompiled must be a support set member (payload fail-fast is the counter-evidence)");
        NopEvalException err = assertThrows(NopEvalException.class,
                () -> TRANSLATOR.translate("matrix/lazy-null.xpl",
                        TreeFingerprints.fingerprint(lazy), lazy, null));
        assertEquals(ERR_EXEC_TRANSLATE_UNSUPPORTED_NODE.getErrorCode(), err.getErrorCode());
        assertTrue(String.valueOf(err.getParam("detail")).contains("lazy function payload not resolvable"),
                "null payload must fail-fast with explicit detail: " + err.getParam("detail"));
    }

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
        // 绿对照：已注册类判绿（I2/A 族与 B 族均翻译成功——I7 闭环后无 pending 第三态）
        assertTrue(ExecToTruffleTranslator.isNodeClassSupported(
                io.nop.xlang.exec.LiteralExecutable.class), "registered class must resolve (green)");
        assertTrue(ExecNodeBaseline.isClassified("LiteralExecutable"));
        assertTrue(ExecToTruffleTranslator.isNodeClassSupported(io.nop.xlang.exec.IfExecutable.class),
                "B-family class must resolve after I7 closure (green, no pending)");
    }
}
