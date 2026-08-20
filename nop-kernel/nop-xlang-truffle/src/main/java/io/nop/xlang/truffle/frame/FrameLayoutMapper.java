package io.nop.xlang.truffle.frame;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExecutableExpressionVisitor;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.BuildClosureBodyExecutable;
import io.nop.xlang.exec.BuildFuncRefExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CallFuncWithClosureExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.LazyCompiledExecutableFunction;
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
 * 实际用法来源）记入 {@link SlotMeta}；MATERIALIZE 无用法来源（plan I7 Phase 1 §4 闭包形态
 * 裁定 = 急切值拷贝，函数体为独立 RootNode + 独立 FrameDescriptor，不物化帧——javadoc 口径
 * 依此更新；被调帧的实参槽/闭包目标槽以入口写入（WRITE，非字面量源）记入被调帧布局）。
 *
 * <p><b>嵌套帧（plan I7）</b>：函数体帧由帧开启者节点携带（非根 CallFunc /
 * CallFuncWithClosure / LazyCompiledExecutableFunction 的被调体、BuildFuncRef 与
 * LiteralExecutable 的 ExecutableFunction 载荷、BuildClosureBody 的目标帧）。扫描遇开启者：
 * 实参表达式与 sourceSlots 在<b>调用者帧</b>记录用量，被调体以独立 SlotScan 递归扫描
 * （被调帧 slotNames 为底、入口槽记 WRITE）；BuildClosureBody 的 expr 属不可知目标帧
 * （全仓无产生路径），显式跳过不记录。
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
        // 仅程序入口 CallFunc 根免开启者处理（其 slotNames 即底层帧）；其他形态根（含
        // LazyCompiled 等可作根的可翻译节点）按嵌套帧开启者处理，被调体独立扫描
        SlotScan scan = new SlotScan(slotNames, tree instanceof CallFuncExecutable ? tree : null);
        tree.visit(scan);
        return buildLayout(slotNames, scan);
    }

    /**
     * 函数体帧布局（plan I7：每个被翻译函数体独立 RootNode 的 FrameDescriptor 来源）。
     *
     * @param slotNames   被调函数帧 slot 布局（ExecutableFunction.getSlotNames() / 调用节点自携带）
     * @param argBindCount 实参绑定槽数（0..argBindCount-1 为入口写入，非字面量源 → Object kind）
     * @param targetSlots 闭包捕获目标槽（BuildFuncRef/CallFuncWithClosure 绑定写入；可为 null）
     * @param body        被调函数体树
     */
    public static FrameLayout mapFunction(String[] slotNames, int argBindCount, int[] targetSlots,
                                          IExecutableExpression body) {
        if (slotNames == null)
            slotNames = new String[0];
        SlotScan scan = new SlotScan(slotNames, null);
        if (body != null) {
            for (int i = 0; i < argBindCount; i++)
                scan.write(i, body);
            if (targetSlots != null) {
                for (int slot : targetSlots)
                    scan.write(slot, body);
            }
            body.visit(scan);
        }
        return buildLayout(slotNames, scan);
    }

    private static FrameLayout buildLayout(String[] slotNames, SlotScan scan) {
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

        /**
         * 底层帧对应的树节点（程序入口 map() 的根节点——其自身不再作为嵌套帧开启者处理）；
         * 函数体扫描（mapFunction / 递归被调帧）无根标记（null）。
         */
        private final IExecutableExpression frameRoot;

        private final SlotUsage[] usages;

        SlotScan(String[] slotNames, IExecutableExpression frameRoot) {
            this.slotNames = slotNames;
            this.frameRoot = frameRoot;
            usages = new SlotUsage[slotNames.length];
            for (int i = 0; i < slotNames.length; i++)
                usages[i] = new SlotUsage();
        }

        SlotUsage usage(int slot) {
            return usages[slot];
        }

        @Override
        public boolean onVisitExpr(IExecutableExpression expr) {
            if (expr != frameRoot && visitNestedFrameOpener(expr))
                return false;

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

        /**
         * 嵌套帧开启者处理（plan I7）：实参与 sourceSlots 在调用者帧记录，被调体递归独立扫描；
         * 返回 true = 已接管子树遍历（调用方返回 false 跳过默认 visit 递归）。
         */
        private boolean visitNestedFrameOpener(IExecutableExpression expr) {
            if (expr instanceof CallFuncExecutable) {
                CallFuncExecutable call = (CallFuncExecutable) expr;
                scanCalleeFrame(call.getSlotNames(), call.getArgExprs(), call.getBodyExpr(), null, call);
                return true;
            }
            if (expr instanceof CallFuncWithClosureExecutable) {
                CallFuncWithClosureExecutable call = (CallFuncWithClosureExecutable) expr;
                for (int slot : call.getSourceSlots())
                    read(slot, call);
                scanCalleeFrame(call.getSlotNames(), call.getArgExprs(), call.getBodyExpr(),
                        call.getTargetSlots(), call);
                return true;
            }
            if (expr instanceof LazyCompiledExecutableFunction) {
                LazyCompiledExecutableFunction lazy = (LazyCompiledExecutableFunction) expr;
                for (IExecutableExpression argExpr : lazy.getArgExprs())
                    argExpr.visit(this);
                ExecutableFunction fn = compiledOrNull(lazy);
                if (fn != null)
                    scanFunctionFrame(fn, null, lazy);
                return true;
            }
            if (expr instanceof BuildFuncRefExecutable) {
                BuildFuncRefExecutable ref = (BuildFuncRefExecutable) expr;
                for (int slot : ref.getSourceSlots())
                    read(slot, ref);
                scanFunctionFrame(ref.getFunc(), ref.getTargetSlots(), ref);
                return true;
            }
            if (expr instanceof LiteralExecutable
                    && ((LiteralExecutable) expr).getValue() instanceof ExecutableFunction) {
                scanFunctionFrame((ExecutableFunction) ((LiteralExecutable) expr).getValue(), null, expr);
                return true;
            }
            if (expr instanceof BuildClosureBodyExecutable) {
                // sourceSlots 在调用者帧读取；expr 属不可知目标帧（全仓无产生路径），显式跳过
                BuildClosureBodyExecutable closure = (BuildClosureBodyExecutable) expr;
                for (int slot : closure.getSourceSlots())
                    read(slot, closure);
                return true;
            }
            return false;
        }

        /** 被调帧扫描：实参在调用者帧（本扫描）求值；被调体以独立扫描递归（入口槽记 WRITE）。 */
        private void scanCalleeFrame(String[] slotNames, IExecutableExpression[] argExprs,
                                     IExecutableExpression body, int[] targetSlots,
                                     IExecutableExpression node) {
            if (argExprs != null) {
                for (IExecutableExpression argExpr : argExprs)
                    argExpr.visit(this);
            }
            SlotScan inner = new SlotScan(slotNames == null ? new String[0] : slotNames, null);
            if (argExprs != null) {
                for (int i = 0; i < argExprs.length; i++)
                    inner.write(i, node);
            }
            if (targetSlots != null) {
                for (int slot : targetSlots)
                    inner.write(slot, node);
            }
            if (body != null)
                body.visit(inner);
        }

        /** ExecutableFunction 载荷帧扫描（BuildFuncRef / Literal 函数字面量）。 */
        private void scanFunctionFrame(ExecutableFunction fn, int[] targetSlots, IExecutableExpression node) {
            if (fn == null || fn.getBody() == null)
                return;
            SlotScan inner = new SlotScan(fn.getSlotNames() == null ? new String[0] : fn.getSlotNames(), null);
            for (int i = 0; i < fn.getArgCount(); i++)
                inner.write(i, node);
            if (targetSlots != null) {
                for (int slot : targetSlots)
                    inner.write(slot, node);
            }
            fn.getBody().visit(inner);
        }

        private static ExecutableFunction compiledOrNull(LazyCompiledExecutableFunction lazy) {
            try {
                return lazy.getCompiled();
            } catch (RuntimeException e) {
                // 载荷不可解析（null/惰性编译失败）——翻译阶段将显式 fail-fast，扫描跳过
                return null;
            }
        }

        private void read(int slot, IExecutableExpression node) {
            SlotUsage usage = requireInFrame(slot, node);
            usage.readCount++;
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
