package io.nop.xlang.truffle.frame;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帧/slot 映射（设计 truffle 02 §四）：前端 {@code LexicalScopeAnalysis} 产出的 slot 布局
 * （程序入口 CallFuncExecutable 的 slotNames）直译为每 RootNode 一份
 * {@link FrameDescriptor}（slot 下标一一对应）。
 *
 * <p>kind 标注规则（不虚构类型）：仅当 slot 的<b>全部</b>写入源都是同族 primitive 字面量
 * （Integer/Long/Double/Float/Boolean）时标注对应 primitive kind（monotonic 单调写入下
 * 类型稳定）；推断不出（非字面量写入、混合族、零写入、程序入口参数帧）保持
 * {@link FrameSlotKind#Object}。字面量族外的字面量（String/BigDecimal 等）同为 Object。
 *
 * <p>帧访问模式按节点实际用法声明：READ（SlotIdentifier 读取）/WRITE（SlotAssign 写入）
 * 记入 {@link SlotMeta}；MATERIALIZE 在表达式子集内无使用（无闭包捕获节点，不物化帧），
 * 物化路径归 I7 闭包覆盖。
 */
public final class FrameLayoutMapper {

    private FrameLayoutMapper() {
    }

    /**
     * @param tree 编译单元根（程序入口 CallFuncExecutable 或纯表达式）
     */
    public static FrameLayout map(IExecutableExpression tree) {
        String[] slotNames = slotNamesOf(tree);
        SlotScan scan = new SlotScan(slotNames.length);
        tree.visit(scan);

        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        List<SlotMeta> metas = new ArrayList<>(slotNames.length);
        for (int i = 0; i < slotNames.length; i++) {
            SlotUsage usage = scan.usage(i);
            FrameSlotKind kind = usage.inferableKind();
            SlotMeta meta = new SlotMeta(i, slotNames[i], kind,
                    usage.readCount > 0, usage.writeCount > 0, kind != FrameSlotKind.Object);
            metas.add(meta);
            builder.addSlot(kind, slotNames[i], meta);
        }
        return new FrameLayout(builder.build(), metas);
    }

    private static String[] slotNamesOf(IExecutableExpression tree) {
        if (tree instanceof CallFuncExecutable) {
            CallFuncExecutable entry = (CallFuncExecutable) tree;
            return entry.getSlotNames() == null ? new String[0] : entry.getSlotNames();
        }
        return new String[0];
    }

    private static final class SlotScan implements IExecutableExpressionVisitor {
        private final SlotUsage[] usages;

        SlotScan(int slotCount) {
            usages = new SlotUsage[slotCount];
            for (int i = 0; i < slotCount; i++)
                usages[i] = new SlotUsage();
        }

        SlotUsage usage(int slot) {
            return usages[slot];
        }

        @Override
        public boolean onVisitExpr(IExecutableExpression expr) {
            if (expr instanceof SlotAssignExecutable) {
                SlotAssignExecutable assign = (SlotAssignExecutable) expr;
                SlotUsage usage = requireInFrame(assign.getSlot(), assign);
                usage.writeCount++;
                usage.writeKinds.add(literalFamily(assign.getExpr()));
            } else if (expr instanceof SlotIdentifierExecutable) {
                SlotIdentifierExecutable identifier = (SlotIdentifierExecutable) expr;
                requireInFrame(identifier.getSlot(), identifier).readCount++;
            }
            return true;
        }

        private SlotUsage requireInFrame(int slot, IExecutableExpression node) {
            if (slot < 0 || slot >= usages.length)
                throw new IllegalArgumentException("slot read/write outside program entry frame: slot=" + slot
                        + ", node=" + node.getClass().getName() + ", location=" + node.getLocation());
            return usages[slot];
        }

        /**
         * 写入源的字面量族标记：null = 非字面量写入（不可推断）。
         */
        private static String literalFamily(IExecutableExpression value) {
            if (!(value instanceof LiteralExecutable))
                return null;
            Object v = ((LiteralExecutable) value).getValue();
            if (v instanceof Integer)
                return "int";
            if (v instanceof Long)
                return "long";
            if (v instanceof Double)
                return "double";
            if (v instanceof Float)
                return "float";
            if (v instanceof Boolean)
                return "boolean";
            return "object-literal";
        }
    }

    private static final class SlotUsage {
        int readCount;

        int writeCount;

        final List<String> writeKinds = new ArrayList<>();

        FrameSlotKind inferableKind() {
            if (writeCount == 0 || writeKinds.size() != writeCount)
                return FrameSlotKind.Object;
            String first = writeKinds.get(0);
            if (first == null)
                return FrameSlotKind.Object;
            for (String kind : writeKinds) {
                if (!first.equals(kind))
                    return FrameSlotKind.Object;
            }
            switch (first) {
                case "int":
                    return FrameSlotKind.Int;
                case "long":
                    return FrameSlotKind.Long;
                case "double":
                    return FrameSlotKind.Double;
                case "float":
                    return FrameSlotKind.Float;
                case "boolean":
                    return FrameSlotKind.Boolean;
                default:
                    return FrameSlotKind.Object;
            }
        }
    }

    /**
     * slot 元数据（帧访问模式声明载体，随 FrameSlot info 存储，repo-observable）。
     */
    public static final class SlotMeta {
        private final int index;

        private final String name;

        private final FrameSlotKind kind;

        private final boolean read;

        private final boolean written;

        private final boolean kindInferred;

        SlotMeta(int index, String name, FrameSlotKind kind, boolean read, boolean written, boolean kindInferred) {
            this.index = index;
            this.name = name;
            this.kind = kind;
            this.read = read;
            this.written = written;
            this.kindInferred = kindInferred;
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public FrameSlotKind getKind() {
            return kind;
        }

        public boolean isRead() {
            return read;
        }

        public boolean isWritten() {
            return written;
        }

        public boolean isKindInferred() {
            return kindInferred;
        }

        @Override
        public String toString() {
            return "SlotMeta[" + index + ':' + name + ',' + kind + ",read=" + read + ",write=" + written + ']';
        }
    }
}
