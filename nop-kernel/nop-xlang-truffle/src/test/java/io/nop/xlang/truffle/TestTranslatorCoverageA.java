package io.nop.xlang.truffle;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.DisabledEvalOutput;
import io.nop.core.lang.eval.EvalScopeImpl;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.NewListExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.nodes.XExprNode;
import io.nop.xlang.truffle.nodes.XLangRootNode;
import io.nop.xlang.truffle.nodes.XRefReadNode;
import io.nop.xlang.truffle.nodes.XRefWriteNode;
import io.nop.xlang.truffle.nodes.XScopeReadNode;
import io.nop.xlang.truffle.nodes.XScopeWriteNode;
import io.nop.xlang.truffle.nodes.XSlotReadNode;
import io.nop.xlang.truffle.nodes.XSlotWriteNode;
import io.nop.xlang.truffle.translate.ExecToTruffleTranslator;
import io.nop.xlang.truffle.translate.TreeFingerprints;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 A（+并入残余）翻译级单测（Phase 2 Exit Criteria）：每族 ≥1 翻译断言（AST 节点类型 +
 * 行为级执行）+ Q3 残余路径两分支验证（slot 化优先 / scope 链查找节点 fallback，节点
 * context-independent 保持）。
 */
public class TestTranslatorCoverageA {

    private static final SourceLocation LOC = CoverageNodes.LOC;

    private static XLangTruffleEval eval;

    private final ExecToTruffleTranslator translator = new ExecToTruffleTranslator();

    @BeforeAll
    public static void init() {
        io.nop.core.initialize.CoreInitialization.initialize();
        eval = new XLangTruffleEval();
    }

    @AfterAll
    public static void destroy() {
        eval.close();
        io.nop.core.initialize.CoreInitialization.destroy();
    }

    // ------------------------------------------------------------------
    // Q3 残余路径两分支（设计 truffle 02 §四 Q3 验收）
    // ------------------------------------------------------------------

    @Test
    public void testQ3SlotPathPreferredForFrameSlots() {
        // 可 slot 化访问：程序入口帧 slot → XSlotReadNode/XSlotWriteNode（不经 scope 链）
        IExecutableExpression tree = CoverageNodes.programEntry("a",
                new SlotAssignExecutable(LOC, "a", 0, CoverageNodes.literal(3)));
        XLangRootNode root = translate(tree).getRootNode();
        assertInstanceOf(XSlotWriteNode.class, root.getBody(), "slot assign must translate to frame slot write");

        IExecutableExpression readTree = CoverageNodes.programEntry("a",
                new SlotIdentifierExecutable(LOC, "a", 0));
        XLangRootNode readRoot = translate(readTree).getRootNode();
        assertInstanceOf(XSlotReadNode.class, readRoot.getBody(), "slot read must translate to frame slot read");

        // slot 写后读经 CallTarget 执行（slot 化路径端到端）
        IExecutableExpression program = CoverageNodes.programEntry("a", CoverageNodes.seq(
                new SlotAssignExecutable(LOC, "a", 0, CoverageNodes.literal(21)),
                new SlotIdentifierExecutable(LOC, "a", 0)));
        assertEquals(21, run(program, new EvalScopeImpl()).getReturnValue());
    }

    @Test
    public void testQ3ScopeLookupNodeForNonSlotizedAccess() {
        // 无法 slot 化的按名访问 → context 持有的 scope 链查找节点（XScopeReadNode）
        ScopeIdentifierExecutable scopeRead = new ScopeIdentifierExecutable(LOC, "x");
        XLangRootNode root = translate(scopeRead).getRootNode();
        assertInstanceOf(XScopeReadNode.class, root.getBody(),
                "non-slotized named access must translate to context-held scope chain lookup node");

        // 求值窗口内经 scope 链取值；不同 scope 各取各值（节点不存 context 数据/运行时值）
        Map<String, Object> varsA = new LinkedHashMap<>();
        varsA.put("x", 11);
        assertEquals(11, run(scopeRead, new EvalScopeImpl(varsA)).getReturnValue());
        Map<String, Object> varsB = new LinkedHashMap<>();
        varsB.put("x", 22);
        assertEquals(22, run(scopeRead, new EvalScopeImpl(varsB)).getReturnValue(),
                "scope lookup node must read the bound scope per evaluation (no stored context)");

        // scope 写节点：写回 scope 可见（side effect）
        ScopeAssignExecutable assign = new ScopeAssignExecutable(LOC, "n", CoverageNodes.literal(7));
        XLangRootNode assignRoot = translate(assign).getRootNode();
        assertInstanceOf(XScopeWriteNode.class, assignRoot.getBody());
        IEvalScope scope = new EvalScopeImpl();
        assertEquals(7, run(assign, scope).getReturnValue());
        assertEquals(7, scope.getValue("n"), "scope write must be visible in the bound scope");
    }

    // ------------------------------------------------------------------
    // 每族 ≥1 翻译 + 行为断言
    // ------------------------------------------------------------------

    @Test
    public void testScopeChainFamilyTranslatesAndExecutes() {
        // 引用族（slot 化路径）：InitRef 建引用 cell → ReferenceAssign 写值 → ReferenceIdentifier 解引用
        IExecutableExpression refProgram = CoverageNodes.programEntry("x", CoverageNodes.seq(
                new InitRefSlotExecutable(LOC, "x", 0),
                new ReferenceAssignExecutable(LOC, "x", 0, CoverageNodes.literal(10)),
                new ReferenceIdentifierExecutable(LOC, "x", 0)));
        XLangTruffleEval.TranslatedEval refResult = run(refProgram, new EvalScopeImpl());
        assertEquals(10, refResult.getReturnValue());
        assertInstanceOf(XRefWriteNode.class, translate(CoverageNodes.programEntry("x",
                new ReferenceAssignExecutable(LOC, "x", 0, CoverageNodes.literal(1))))
                .getRootNode().getBody(), "reference write translates to slot ref write node");
        assertInstanceOf(XRefReadNode.class, translate(CoverageNodes.programEntry("x",
                new ReferenceIdentifierExecutable(LOC, "x", 0))).getRootNode().getBody(),
                "reference read translates to slot ref read node");

        // ScopeSelfAssign：旧值读 → change 求值 → 复合写回（解释器求值顺序语义）
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("n", 5);
        ScopeSelfAssignExecutable selfAssign = ScopeSelfAssignExecutable.build(LOC, "n",
                XLangOperator.SELF_ASSIGN_ADD, CoverageNodes.literal(3));
        IEvalScope scope = new EvalScopeImpl(vars);
        assertEquals(8, run(selfAssign, scope).getReturnValue());
        assertEquals(8, scope.getValue("n"));
    }

    @Test
    public void testTypeOpFamilyTranslatesAndExecutes() {
        // Cast 行为：以同型值走转换（int 目标 + String 原值是解释器忠实保留的 quirk——
        // converted 非空时按原值 isInstance 校验恒失败，此处用 String→String 验证正常路径）
        assertEquals("5", run(new io.nop.xlang.exec.CastExecutable(LOC, CoverageNodes.literal("5"),
                String.class), new EvalScopeImpl()).getReturnValue());
        assertEquals(12, run(CoverageNodes.minimalTree("ConvertExecutable"), new EvalScopeImpl()).getReturnValue());
        assertEquals(1, run(CoverageNodes.minimalTree("ConvertWithDefaultExecutable"), new EvalScopeImpl())
                .getReturnValue(), "null value must take the default path");
        assertEquals(Boolean.TRUE, run(CoverageNodes.minimalTree("InstanceOfExecutable"), new EvalScopeImpl())
                .getReturnValue());
        assertEquals("java.lang.Integer", run(CoverageNodes.minimalTree("TypeOfExecutable"), new EvalScopeImpl())
                .getReturnValue());
    }

    @Test
    public void testObjCollectionFamilyTranslatesAndExecutes() {
        assertEquals(3, run(CoverageNodes.minimalTree("GetPropertyExecutable"), new EvalScopeImpl())
                .getReturnValue(), "\"ab1\".length() == 3");
        Object list = run(new NewListExecutable(LOC, new ListItemExecutable[]{
                new ListItemExecutable(false, CoverageNodes.literal(1)),
                new ListItemExecutable(true, CoverageNodes.minimalTree("NewListExecutable"))}), new EvalScopeImpl())
                .getReturnValue();
        assertEquals(List.of(1, 1), list, "list construction incl. spread element");

        Object map = run(CoverageNodes.minimalTree("NewMapExecutable"), new EvalScopeImpl()).getReturnValue();
        assertEquals(Map.of("a", 1), map);

        Object sb = run(CoverageNodes.minimalTree("NewObjectExecutable"), new EvalScopeImpl()).getReturnValue();
        assertInstanceOf(StringBuilder.class, sb);
        assertEquals(1, ((StringBuilder) sb).append("x").length() > 0 ? 1 : 0);
    }

    @Test
    public void testBindingGuardDebugFamilyTranslatesAndExecutes() {
        // 数组解构：绑定 slot 后读取
        IExecutableExpression arrayBinding = CoverageNodes.programEntry("a", CoverageNodes.seq(
                new ArrayBindingAssignExecutable(LOC,
                        new AssignIdentifier[]{new AssignIdentifier(LOC, 0, "a", false, null)},
                        null, CoverageNodes.literal(List.of(42))),
                new SlotIdentifierExecutable(LOC, "a", 0)));
        assertEquals(42, run(arrayBinding, new EvalScopeImpl()).getReturnValue());

        // slot 变量绑定
        IExecutableExpression bindVar = CoverageNodes.programEntry("b", CoverageNodes.seq(
                new BindVarExecutable(LOC, new int[]{0}, new Object[]{5},
                        new SlotIdentifierExecutable(LOC, "b", 0)),
                new SlotIdentifierExecutable(LOC, "b", 0)));
        assertEquals(5, run(bindVar, new EvalScopeImpl()).getReturnValue());

        // 守卫 + 调试值返回 + VarStatus
        assertEquals("v1", run(CoverageNodes.minimalTree("GuardNotEmptyExecutable"), new EvalScopeImpl())
                .getReturnValue());
        assertEquals(1, run(CoverageNodes.minimalTree("DebugExecutable"), new EvalScopeImpl()).getReturnValue());
        Object vs = run(CoverageNodes.minimalTree("VarStatusExecutable"), new EvalScopeImpl()).getReturnValue();
        assertInstanceOf(io.nop.commons.collections.iterator.LoopVarStatus.class, vs,
                "varStatus must construct loop var status");
    }

    @Test
    public void testSlotWriteFamilyTranslatesAndExecutes() {
        // 复合赋值：先写初值再复合（新值返回）
        IExecutableExpression selfAssign = CoverageNodes.programEntry("a", CoverageNodes.seq(
                new SlotAssignExecutable(LOC, "a", 0, CoverageNodes.literal(6)),
                SelfAssignExecutable.build(LOC, "a", 0, XLangOperator.SELF_ASSIGN_MULTI,
                        CoverageNodes.literal(3)),
                new SlotIdentifierExecutable(LOC, "a", 0)));
        assertEquals(18, run(selfAssign, new EvalScopeImpl()).getReturnValue());

        // 自增返回旧值（解释器语义）
        IExecutableExpression selfInc = CoverageNodes.programEntry("a", CoverageNodes.seq(
                new SlotAssignExecutable(LOC, "a", 0, CoverageNodes.literal(4)),
                new SelfIncExecutable(LOC, "a", 0)));
        assertEquals(4, run(selfInc, new EvalScopeImpl()).getReturnValue(),
                "x++ must return the old value");
    }

    @Test
    public void testResidualOpsFamilyTranslatesAndExecutes() {
        assertEquals(-1, run(CoverageNodes.minimalTree("NegExecutable"), new EvalScopeImpl()).getReturnValue());
        assertEquals(-2, run(CoverageNodes.minimalTree("BitNotExecutable"), new EvalScopeImpl()).getReturnValue());
        assertEquals(4, run(CoverageNodes.minimalTree("NullCoalesceExecutable"), new EvalScopeImpl())
                .getReturnValue());
        assertEquals(Boolean.FALSE, run(CoverageNodes.minimalTree("EqNullExecutable"), new EvalScopeImpl())
                .getReturnValue(), "\"a1\" == null is false");
        assertEquals(Boolean.TRUE, run(CoverageNodes.minimalTree("NeNullExecutable"), new EvalScopeImpl())
                .getReturnValue(), "\"a1\" != null is true");
        assertEquals(Boolean.TRUE, run(new io.nop.xlang.exec.EqNullExecutable(LOC,
                io.nop.xlang.exec.NullExecutable.NULL), new EvalScopeImpl())
                .getReturnValue(), "null == null is true");
        assertEquals(1, run(CoverageNodes.minimalTree("BinaryExecutable"), new EvalScopeImpl()).getReturnValue(),
                "7 % 3 = 1");
        assertEquals("ab1", run(CoverageNodes.minimalTree("ConcatExecutable"), new EvalScopeImpl()).getReturnValue());
        assertEquals(Boolean.TRUE, run(CoverageNodes.minimalTree("BetweenOpExecutable"), new EvalScopeImpl())
                .getReturnValue());
        assertEquals(Boolean.TRUE, run(CoverageNodes.minimalTree("AssertOpExecutable"), new EvalScopeImpl())
                .getReturnValue());
        assertInstanceOf(io.nop.commons.collections.iterator.IntRangeIterator.class,
                run(CoverageNodes.minimalTree("RangeExecutable"), new EvalScopeImpl()).getReturnValue());
        assertEquals(Boolean.TRUE, run(CoverageNodes.minimalTree("PropInExecutable"), new EvalScopeImpl())
                .getReturnValue());
    }

    @Test
    public void testCloneLiteralDeepClonesPerEvaluation() {
        IExecutableExpression clone = CloneLiteralExecutable.build(LOC, new LinkedHashMap<>(Map.of("k", "v")));
        XLangTruffleEval.TranslatedEval first = run(clone, new EvalScopeImpl());
        @SuppressWarnings("unchecked")
        Map<String, Object> firstValue = (Map<String, Object>) first.getReturnValue();
        firstValue.put("mutated", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> secondValue = (Map<String, Object>) run(clone, new EvalScopeImpl()).getReturnValue();
        assertTrue(!secondValue.containsKey("mutated"),
                "clone literal must deep-clone per evaluation (no shared mutable literal state)");
        assertEquals(Map.of("k", "v"), secondValue);
    }

    @Test
    public void testSourceSectionBackMappingOnNewErrorPoints() {
        // 新可抛错点（属性读取 null 报错）携带合成 SourceSection（回映射源位置）
        SourceLocation loc = SourceLocation.fromLine("/coverage-a/backmap.xpl", 3, 5);
        GetPropertyExecutable getProp = new GetPropertyExecutable(loc, CoverageNodes.literal(null),
                false, "length");
        XLangRootNode root = translate(getProp).getRootNode();
        XExprNode body = root.getBody();
        SourceLocation mapped = io.nop.xlang.truffle.translate.SyntheticSources
                .toSourceLocation(body.getSourceSection());
        assertEquals("/coverage-a/backmap.xpl", mapped.getPath(),
                "translatable error-capable node must carry synthetic source section mapping back to source");
        assertEquals(3, mapped.getLine());
        assertEquals(5, mapped.getCol());
    }

    @Test
    public void testPureExpressionReferenceReadOutOfFrameFailsFast() {
        // 纯表达式模式无入口帧：reference 读无帧可落 → fail-fast 保持（slot 越界，帧映射层报节点类名+位置）
        IllegalArgumentException e = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> translate(new ReferenceIdentifierExecutable(LOC, "x", 0)));
        assertTrue(e.getMessage().contains("ReferenceIdentifierExecutable"),
                "error must carry the offending node class name: " + e.getMessage());
        assertTrue(e.getMessage().contains("slot"), "error must report the frame violation: " + e.getMessage());
    }

    @Test
    public void testSupportSetCoversAllCoverageAClasses() {
        // Phase 2 自判：支持集可编程枚举且覆盖 Phase 1 对齐的全部 A 族 + 残余具体节点类
        // （矩阵 live 扫描双向一致交叉验证归 Phase 3 联动）
        java.util.Set<String> supported = new java.util.TreeSet<>();
        for (Class<?> cls : ExecToTruffleTranslator.getSupportedNodeClasses())
            supported.add(cls.getSimpleName());
        assertTrue(supported.containsAll(io.nop.xlang.compare.ExecNodeBaseline.i3Scope()),
                "support set must cover every coverage-A + residual class, missing="
                        + new java.util.TreeSet<>(io.nop.xlang.compare.ExecNodeBaseline.i3Scope()) {
                    {
                        removeAll(supported);
                    }
                });
        // I7 闭环后支持集收敛到全量 120（registeredTarget 87 + B 族 33）；A 范围 containment 断言不变
        assertEquals(120, supported.size(),
                "support set = truffleRegisteredTarget full set after I7 closure (87 + B 33)");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private io.nop.xlang.truffle.translate.TranslatedUnit translate(IExecutableExpression tree) {
        return translator.translate("/coverage-a/unit.xpl", TreeFingerprints.fingerprint(tree), tree, null);
    }

    private static XLangTruffleEval.TranslatedEval run(IExecutableExpression tree, IEvalScope scope) {
        return eval.eval("/coverage-a/unit.xpl", tree, scope, DisabledEvalOutput.INSTANCE);
    }
}
