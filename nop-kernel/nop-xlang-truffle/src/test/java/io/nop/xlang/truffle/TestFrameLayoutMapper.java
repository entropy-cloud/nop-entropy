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
