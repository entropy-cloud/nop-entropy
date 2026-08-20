package io.nop.xlang.truffle.frame;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.ObjectBindingAssignExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ReferenceSelfAssignExecutable;
import io.nop.xlang.exec.ReferenceSelfDecExecutable;
import io.nop.xlang.exec.ReferenceSelfIncExecutable;
import io.nop.xlang.exec.RenewReferenceExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.VarStatusExecutable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 帧/slot 映射（设计 truffle 02 §四）：前端 {@code LexicalScopeAnalysis} 产出的 slot 布局
 * （程序入口 CallFuncExecutable 的 slotNames）直译为每 RootNode 一份
 * {@link FrameDescriptor}（slot 下标一一对应）。
 *
 * <p>kind 标注规则（不虚构类型）：仅当 slot 的<b>全部</b>写入源都是同族 primitive 字面量
 * （Integer/Long/Double/Float/Boolean）时标注对应 primitive kind（monotonic 单调写入下
 * 类型稳定）；推断不出（非字面量写入、混合族、零写入、程序入口参数帧）保持
 * {@link FrameSlotKind#Object}。字面量族外的字面量（String/BigDecimal 等）同为 Object。
 * 未推断原因经 {@link KindReason} 分类（kind 覆盖率实测的统计口径）。
 *
 * <p>帧访问模式按节点实际用法声明：READ（SlotIdentifier/ReferenceIdentifier/复合赋值旧值读/
 * 解构引用写旧 cell 读等）/WRITE（SlotAssign/引用写/自增自减/绑定写/VarStatus 等 A 族
 * 实际用法来源）记入 {@link SlotMeta}；MATERIALIZE 在当前支持集内无使用（无闭包捕获节点，
 * 不物化帧），物化路径归 I7 闭包覆盖。
 */
public final class FrameLayoutMapper {

    private FrameLayoutMapper() {
    }

    /**
     * kind 未推断原因分类（覆盖 A 全量实测统计口径）。
     */
    public enum KindReason {
        /** 同族 primitive 字面量单调写入，已推断 primitive kind。 */
        INFERRED,
        /** 有写入但混合族（含字面量 + 非字面量混合、多字面量族混合），回落 Object。 */
        MIXED_FAMILY,
        /** 有写入但全部为非字面量来源（绑定/引用/复合赋值等），不可推断。 */
        NON_LITERAL_WRITE,
        /** 写入全部为字面量但属字面量族外（String/BigDecimal 等），Object。 */
        NON_PRIMITIVE_LITERAL,
        /** 零写入（只读或未用 slot）。 */
        ZERO_WRITE
    }

    /**
     * @param tree 编译单元根（程序入口 CallFuncExecutable 或纯表达式）
     */
    public static FrameLayout map(IExecutableExpression tree) {
        String[] slotNames = slotNamesOf(tree);
        SlotScan scan = new SlotScan(slotNames);
        tree.visit(scan);

        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        List<SlotMeta> metas = new ArrayList<>(slotNames.length);
        for (int i = 0; i < slotNames.length; i++) {
            SlotUsage usage = scan.usage(i);
            FrameSlotKind kind = usage.inferableKind();
            KindReason reason = usage.kindReason();
            SlotMeta meta = new SlotMeta(i, slotNames[i], kind,
                    usage.readCount > 0, usage.writeCount > 0, reason == KindReason.INFERRED, reason);
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
        private final String[] slotNames;

        private final SlotUsage[] usages;

        SlotScan(String[] slotNames) {
            this.slotNames = slotNames;
            usages = new SlotUsage[slotNames.length];
            for (int i = 0; i < slotNames.length; i++)
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
            } else if (expr instanceof ReferenceIdentifierExecutable) {
                ReferenceIdentifierExecutable ref = (ReferenceIdentifierExecutable) expr;
                requireInFrame(ref.getSlot(), ref).readCount++;
            } else if (expr instanceof ReferenceAssignExecutable) {
                ReferenceAssignExecutable assign = (ReferenceAssignExecutable) expr;
                readWrite(assign.getSlot(), assign);
            } else if (expr instanceof ReferenceSelfAssignExecutable) {
                ReferenceSelfAssignExecutable self = (ReferenceSelfAssignExecutable) expr;
                readWrite(self.getSlot(), self);
            } else if (expr instanceof ReferenceSelfIncExecutable) {
                ReferenceSelfIncExecutable self = (ReferenceSelfIncExecutable) expr;
                readWrite(self.getSlot(), self);
            } else if (expr instanceof ReferenceSelfDecExecutable) {
                ReferenceSelfDecExecutable self = (ReferenceSelfDecExecutable) expr;
                readWrite(self.getSlot(), self);
            } else if (expr instanceof RenewReferenceExecutable) {
                RenewReferenceExecutable renew = (RenewReferenceExecutable) expr;
                readWrite(renew.getSlot(), renew);
            } else if (expr instanceof InitRefSlotExecutable) {
                InitRefSlotExecutable init = (InitRefSlotExecutable) expr;
                write(init.getSlot(), init);
            } else if (expr instanceof EnhanceRefSlotExecutable) {
                EnhanceRefSlotExecutable enhance = (EnhanceRefSlotExecutable) expr;
                readWrite(enhance.getSlot(), enhance);
            } else if (expr instanceof SelfAssignExecutable) {
                SelfAssignExecutable self = (SelfAssignExecutable) expr;
                readWrite(self.getSlot(), self);
            } else if (expr instanceof SelfIncExecutable) {
                SelfIncExecutable self = (SelfIncExecutable) expr;
                readWrite(self.getSlot(), self);
            } else if (expr instanceof SelfDecExecutable) {
                SelfDecExecutable self = (SelfDecExecutable) expr;
                readWrite(self.getSlot(), self);
            } else if (expr instanceof VarStatusExecutable) {
                VarStatusExecutable varStatus = (VarStatusExecutable) expr;
                write(varStatus.getVarStatusSlot(), varStatus);
            } else if (expr instanceof BindVarExecutable) {
                BindVarExecutable bind = (BindVarExecutable) expr;
                for (int slot : bind.getSlots()) {
                    write(slot, bind);
                }
            } else if (expr instanceof ArrayBindingAssignExecutable) {
                ArrayBindingAssignExecutable binding = (ArrayBindingAssignExecutable) expr;
                for (AssignIdentifier id : binding.getElementBindings()) {
                    bindingUsage(id, binding);
                }
                if (binding.getRestBinding() != null)
                    bindingUsage(binding.getRestBinding(), binding);
            } else if (expr instanceof ObjectBindingAssignExecutable) {
                ObjectBindingAssignExecutable binding = (ObjectBindingAssignExecutable) expr;
                for (AssignIdentifier id : binding.getPropBindings()) {
                    bindingUsage(id, binding);
                }
                if (binding.getRestBinding() != null)
                    bindingUsage(binding.getRestBinding(), binding);
            } else if (expr instanceof DebugIdentifierExecutable) {
                DebugIdentifierExecutable identifier = (DebugIdentifierExecutable) expr;
                int slot = indexOfSlotName(identifier.getVarName());
                if (slot >= 0)
                    usage(slot).readCount++;
                // 名字不在入口帧 = scope 按名查找（Q3 残余路径），无帧访问
            }
            return true;
        }

        private void bindingUsage(AssignIdentifier id, IExecutableExpression node) {
            int slot = id.getVarSlot();
            if (slot < 0)
                return;
            SlotUsage usage = requireInFrame(slot, node);
            usage.readCount++;
            usage.writeCount++;
            usage.writeKinds.add(null);
        }

        private void readWrite(int slot, IExecutableExpression node) {
            SlotUsage usage = requireInFrame(slot, node);
            usage.readCount++;
            usage.writeCount++;
            usage.writeKinds.add(null);
        }

        private void write(int slot, IExecutableExpression node) {
            SlotUsage usage = requireInFrame(slot, node);
            usage.writeCount++;
            usage.writeKinds.add(null);
        }

        private int indexOfSlotName(String varName) {
            for (int i = 0; i < slotNames.length; i++) {
                if (varName.equals(slotNames[i]))
                    return i;
            }
            return -1;
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
            return kindReason() == KindReason.INFERRED ? primitiveKind(writeKinds.get(0)) : FrameSlotKind.Object;
        }

        KindReason kindReason() {
            if (writeCount == 0)
                return KindReason.ZERO_WRITE;
            String first = writeKinds.get(0);
            for (String kind : writeKinds) {
                if (!Objects.equals(first, kind))
                    return KindReason.MIXED_FAMILY;
            }
            if (first == null)
                return KindReason.NON_LITERAL_WRITE;
            if (first.equals("object-literal"))
                return KindReason.NON_PRIMITIVE_LITERAL;
            return KindReason.INFERRED;
        }

        private static FrameSlotKind primitiveKind(String family) {
            switch (family) {
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

        private final KindReason kindReason;

        SlotMeta(int index, String name, FrameSlotKind kind, boolean read, boolean written,
                 boolean kindInferred, KindReason kindReason) {
            this.index = index;
            this.name = name;
            this.kind = kind;
            this.read = read;
            this.written = written;
            this.kindInferred = kindInferred;
            this.kindReason = kindReason;
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

        public KindReason getKindReason() {
            return kindReason;
        }

        @Override
        public String toString() {
            return "SlotMeta[" + index + ':' + name + ',' + kind + ",read=" + read + ",write=" + written
                    + ",reason=" + kindReason + ']';
        }
    }
}
