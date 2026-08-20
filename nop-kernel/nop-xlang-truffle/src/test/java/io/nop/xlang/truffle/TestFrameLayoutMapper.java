package io.nop.xlang.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.PlusExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.frame.FrameLayoutMapper;
import io.nop.xlang.truffle.frame.FrameLayoutMapper.SlotMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 帧/slot 映射单测（Phase 2）：slot 布局逐 slot 对应（数量/标识/kind 标注规则/访问模式声明）；
 * kind 推断不出的 slot 保持 Object kind 且不虚构类型（无静默虚构）。
 */
public class TestFrameLayoutMapper {

    private static final SourceLocation LOC = SourceLocation.fromPath("/frame-mapper-test.xpl");

    @Test
    public void testSlotLayoutOneToOne() {
        IExecutableExpression tree = programEntry(new String[]{"x", "y", "z"},
                seq(assign(0, literal(3)), assign(1, literal("s")), assign(2, LiteralExecutable.build(LOC, null))));
        FrameLayout layout = FrameLayoutMapper.map(tree);

        assertEquals(3, layout.getSlotCount());
        FrameDescriptor descriptor = layout.getDescriptor();
        assertEquals(3, descriptor.getNumberOfSlots());
        for (int i = 0; i < 3; i++) {
            SlotMeta meta = layout.getSlot(i);
            assertEquals(i, meta.getIndex());
            assertSame(meta, descriptor.getSlotInfo(i), "slot meta must be attached as FrameSlot info");
            assertEquals(layout.getSlot(i).getName(), descriptor.getSlotName(i));
        }
        assertEquals("x", layout.getSlot(0).getName());
        assertEquals("y", layout.getSlot(1).getName());
        assertEquals("z", layout.getSlot(2).getName());
    }

    @Test
    public void testKindInferenceRules() {
        IExecutableExpression tree = programEntry(new String[]{"a", "b", "c", "d", "e", "f"},
                seq(
                        assign(0, literal(5)),
                        assign(1, literal("str")),
                        assign(2, plus(literal(1), literal(2))),
                        assign(3, literal(1L)),
                        assign(4, literal(Boolean.TRUE)),
                        literal(0)));
        FrameLayout layout = FrameLayoutMapper.map(tree);

        assertEquals(FrameSlotKind.Int, layout.getSlot(0).getKind(), "all-literal int writes → Int kind");
        assertEquals(FrameSlotKind.Object, layout.getSlot(1).getKind(), "string literal writes → Object kind");
        assertEquals(FrameSlotKind.Object, layout.getSlot(2).getKind(),
                "non-literal write → Object kind (never fabricate type)");
        assertEquals(FrameSlotKind.Long, layout.getSlot(3).getKind());
        assertEquals(FrameSlotKind.Boolean, layout.getSlot(4).getKind());
        assertEquals(FrameSlotKind.Object, layout.getSlot(5).getKind(),
                "zero-write slot → Object kind (never fabricate type)");
        assertTrue(layout.getSlot(0).isKindInferred());
        assertFalse(layout.getSlot(2).isKindInferred());
    }

    @Test
    public void testMixedLiteralFamiliesFallBackToObject() {
        IExecutableExpression tree = programEntry(new String[]{"m"},
                seq(assign(0, literal(5)), assign(0, literal("s"))));
        FrameLayout layout = FrameLayoutMapper.map(tree);
        assertEquals(FrameSlotKind.Object, layout.getSlot(0).getKind(),
                "mixed write families → Object kind (monotonic safety)");
    }

    @Test
    public void testAccessModesDeclaredByActualUsage() {
        // slot r: write + read; slot w: write only; slot rw: write + read
        IExecutableExpression body = seq(
                assign(0, literal(1)),
                assign(1, literal(2)),
                assign(2, literal(3)),
                slotRead(0),
                slotRead(2));
        IExecutableExpression tree = programEntry(new String[]{"r", "w", "rw"}, body);
        FrameLayout layout = FrameLayoutMapper.map(tree);

        assertTrue(layout.getSlot(0).isRead() && layout.getSlot(0).isWritten());
        assertTrue(layout.getSlot(1).isWritten());
        assertFalse(layout.getSlot(1).isRead(), "write-only slot must not declare READ");
        assertTrue(layout.getSlot(2).isRead() && layout.getSlot(2).isWritten());
    }

    @Test
    public void testPureExpressionHasEmptyFrame() {
        FrameLayout layout = FrameLayoutMapper.map(literal(42));
        assertEquals(0, layout.getSlotCount());
        assertEquals(0, layout.getDescriptor().getNumberOfSlots());
    }

    @Test
    public void testOutOfFrameSlotFailsFast() {
        IExecutableExpression tree = programEntry(new String[]{"x"}, slotRead(3));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> FrameLayoutMapper.map(tree));
        assertTrue(e.getMessage().contains("slot"));
    }

    // ------------------------------------------------------------------
    // 覆盖 A：A 族节点实际用法的 READ/WRITE 声明扩展 + kind 未推断原因分类
    // ------------------------------------------------------------------

    @Test
    public void testAFamilySlotUsageDeclaredByActualUsage() {
        // slot 0：引用族（读 cell + 写 cell）；slot 1：slot 写族复合赋值（读旧值 + 写回）；
        // slot 2：绑定写（BindVar 写）；slot 3：VarStatus 写；slot 4：DebugIdentifier 按名读（在帧内）
        io.nop.xlang.exec.InitRefSlotExecutable initRef = new io.nop.xlang.exec.InitRefSlotExecutable(LOC, "r", 0);
        io.nop.xlang.exec.ReferenceAssignExecutable refAssign = new io.nop.xlang.exec.ReferenceAssignExecutable(
                LOC, "r", 0, literal(1));
        io.nop.xlang.exec.SelfIncExecutable selfInc = new io.nop.xlang.exec.SelfIncExecutable(LOC, "s", 1);
        io.nop.xlang.exec.BindVarExecutable bindVar = new io.nop.xlang.exec.BindVarExecutable(
                LOC, new int[]{2}, new Object[]{9}, slotRead(2));
        io.nop.xlang.exec.VarStatusExecutable varStatus = new io.nop.xlang.exec.VarStatusExecutable(
                LOC, "vs", 3, literal(3));
        io.nop.xlang.exec.DebugIdentifierExecutable debugId = new io.nop.xlang.exec.DebugIdentifierExecutable(
                LOC, "d");
        IExecutableExpression body = seq(initRef, refAssign, selfInc, bindVar, varStatus, debugId);
        IExecutableExpression tree = programEntry(new String[]{"r", "s", "b", "vs", "d"}, body);
        FrameLayout layout = FrameLayoutMapper.map(tree);

        assertTrue(layout.getSlot(0).isRead() && layout.getSlot(0).isWritten(),
                "reference family declares read+write");
        assertTrue(layout.getSlot(1).isRead() && layout.getSlot(1).isWritten(),
                "self-inc declares read (old value) + write (new value)");
        assertTrue(layout.getSlot(2).isWritten(), "bind var declares write");
        assertTrue(layout.getSlot(2).isRead(), "bind var body read declares read");
        assertTrue(layout.getSlot(3).isWritten(), "var status declares write");
        assertTrue(layout.getSlot(4).isRead(), "debug identifier resolved in entry frame declares read");
        for (int i = 0; i < 5; i++) {
            assertEquals(FrameSlotKind.Object, layout.getSlot(i).getKind(),
                    "non-literal write sources must stay Object kind");
        }
    }

    @Test
    public void testKindReasonClassification() {
        // slot 0：int 字面量单调写（INFERRED）；slot 1：String 字面量（NON_PRIMITIVE_LITERAL）；
        // slot 2：int 字面量 + String 字面量混合（MIXED_FAMILY）；slot 3：纯非字面量写（NON_LITERAL_WRITE）；
        // slot 4：零写入（ZERO_WRITE）
        IExecutableExpression body = seq(
                assign(0, literal(5)),
                assign(1, literal("str")),
                assign(2, literal(5)),
                assign(2, literal("s")),
                assign(3, plus(literal(1), literal(2))),
                literal(0));
        IExecutableExpression tree = programEntry(new String[]{"a", "b", "c", "d", "e"}, body);
        FrameLayout layout = FrameLayoutMapper.map(tree);

        assertEquals(FrameLayoutMapper.KindReason.INFERRED, layout.getSlot(0).getKindReason());
        assertEquals(FrameLayoutMapper.KindReason.NON_PRIMITIVE_LITERAL, layout.getSlot(1).getKindReason());
        assertEquals(FrameLayoutMapper.KindReason.MIXED_FAMILY, layout.getSlot(2).getKindReason());
        assertEquals(FrameLayoutMapper.KindReason.NON_LITERAL_WRITE, layout.getSlot(3).getKindReason());
        assertEquals(FrameLayoutMapper.KindReason.ZERO_WRITE, layout.getSlot(4).getKindReason());
        assertTrue(layout.getSlot(0).isKindInferred());
        assertFalse(layout.getSlot(1).isKindInferred());
    }

    @Test
    public void testDebugIdentifierOutsideFrameDeclaresNoFrameAccess() {
        // 名字不在入口帧 = scope 按名查找（Q3 残余路径），无帧访问声明
        io.nop.xlang.exec.DebugIdentifierExecutable debugId =
                new io.nop.xlang.exec.DebugIdentifierExecutable(LOC, "notInFrame");
        IExecutableExpression tree = programEntry(new String[]{"x"}, debugId);
        FrameLayout layout = FrameLayoutMapper.map(tree);
        assertFalse(layout.getSlot(0).isRead(), "unresolved name must not declare frame READ");
        assertEquals(FrameLayoutMapper.KindReason.ZERO_WRITE, layout.getSlot(0).getKindReason());
    }

    // ------------------------------------------------------------------
    // 树构造 helpers（直接构造 Executable 节点，不经编译前端）
    // ------------------------------------------------------------------

    private static IExecutableExpression programEntry(String[] slotNames, IExecutableExpression body) {
        return new CallFuncExecutable(LOC, "test-entry", slotNames, new IExecutableExpression[0], body);
    }

    private static IExecutableExpression assign(int slot, IExecutableExpression value) {
        return new SlotAssignExecutable(LOC, "v" + slot, slot, value);
    }

    private static IExecutableExpression slotRead(int slot) {
        return new SlotIdentifierExecutable(LOC, "v" + slot, slot);
    }

    private static IExecutableExpression literal(Object value) {
        return LiteralExecutable.build(LOC, value);
    }

    private static IExecutableExpression plus(IExecutableExpression left, IExecutableExpression right) {
        return new PlusExecutable(LOC, left, right);
    }

    private static IExecutableExpression seq(IExecutableExpression... exprs) {
        return SeqExecutable.valueOf(LOC, exprs);
    }
}
