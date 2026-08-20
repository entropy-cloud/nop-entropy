package io.nop.xlang.truffle.translate;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.exec.AbstractBinaryExecutable;
import io.nop.xlang.exec.AbstractObjFunctionExecutable;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssertOpExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BetweenOpExecutable;
import io.nop.xlang.exec.BinaryExecutable;
import io.nop.xlang.exec.BindVarExecutable;
import io.nop.xlang.exec.BitNotExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CastExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.EqNullExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GetterGetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.MakePropertyExecutable;
import io.nop.xlang.exec.MapItemExecutable;
import io.nop.xlang.exec.NeNullExecutable;
import io.nop.xlang.exec.NegExecutable;
import io.nop.xlang.exec.NewListExecutable;
import io.nop.xlang.exec.NewMapExecutable;
import io.nop.xlang.exec.NewObjectExecutable;
import io.nop.xlang.exec.NotExecutable;
import io.nop.xlang.exec.NullCoalesceExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjectBindingAssignExecutable;
import io.nop.xlang.exec.PropBinding;
import io.nop.xlang.exec.PropInExecutable;
import io.nop.xlang.exec.RangeExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ReferenceSelfAssignExecutable;
import io.nop.xlang.exec.ReferenceSelfDecExecutable;
import io.nop.xlang.exec.ReferenceSelfIncExecutable;
import io.nop.xlang.exec.RenewReferenceExecutable;
import io.nop.xlang.exec.ReturnNullExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.ScopeSelfDecExecutable;
import io.nop.xlang.exec.ScopeSelfIncExecutable;
import io.nop.xlang.exec.SelfAssignAttrExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfAssignPropertyExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SetAttrExecutable;
import io.nop.xlang.exec.SetterSetPropertyExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StaticGetterGetPropertyExecutable;
import io.nop.xlang.exec.SetPropertyExecutable;
import io.nop.xlang.exec.StrictEqNullExecutable;
import io.nop.xlang.exec.StrictNeNullExecutable;
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;

/**
 * Executable 树指纹（翻译缓存键组成部分，与 java 后端生成类清单的指纹纪律对称）：
 * 递归混合节点类名、源位置（path:line:col）与节点语义载荷（标量载荷——属性名/变量名/类型名/
 * 算子/标志等——与子表达式结构，二者皆为载荷），不含对象身份——结构相同的两棵树指纹相同，
 * 任一差异（含源位置差异）必产生不同指纹，保证"同路径不同树"（租户差异、资源热变更后重载）
 * 不串用旧翻译产物。
 *
 * <p>载荷覆盖硬保证：全部可翻译节点类均有显式白名单分支（标量载荷 + 子树）；二元算术/比较/
 * 逻辑族为无标量载荷的结构族（类名 + 子树即全载荷）。兜底分支对<b>可翻译但载荷未混合</b>的
 * 节点类 fail-fast（防新增可翻译节点类静默落入弱哈希——同 resourcePath 树变更指纹碰撞串用
 * 旧缓存 AST，设计 truffle 02 §七自认最危险缺陷形态）；不可翻译节点（翻译必然 fail-fast，
 * 指纹值不进入缓存）允许弱哈希兜底。
 */
public final class TreeFingerprints {

    private TreeFingerprints() {
    }

    public static long fingerprint(IExecutableExpression tree) {
        return mix(0x584c414e47L, tree);
    }

    private static long mix(long seed, IExecutableExpression node) {
        if (node == null)
            return seed * 31 + 1;
        long h = seed * 31 + node.getClass().getName().hashCode();
        h = h * 31 + locationHash(node.getLocation());

        if (node instanceof LiteralExecutable) {
            Object value = ((LiteralExecutable) node).getValue();
            h = mixValue(h, value);
        } else if (node instanceof CloneLiteralExecutable) {
            Object value = ((CloneLiteralExecutable) node).getValue();
            h = mixValue(h, value);
        } else if (node instanceof SlotIdentifierExecutable) {
            h = h * 31 + ((SlotIdentifierExecutable) node).getSlot();
        } else if (node instanceof SlotAssignExecutable) {
            SlotAssignExecutable assign = (SlotAssignExecutable) node;
            h = h * 31 + assign.getSlot();
            h = mix(h, assign.getExpr());
        } else if (node instanceof CallFuncExecutable) {
            CallFuncExecutable entry = (CallFuncExecutable) node;
            h = h * 31 + (entry.getFuncName() == null ? 0 : entry.getFuncName().hashCode());
            h = h * 31 + arrayHash(entry.getSlotNames());
            h = mixAll(h, entry.getArgExprs());
            h = mix(h, entry.getBodyExpr());
        } else if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            h = h * 31 + cmp.getFilterOp().name().hashCode();
            h = mix(h, cmp.getLeft());
            h = mix(h, cmp.getRight());
        } else if (node instanceof BinaryExecutable) {
            BinaryExecutable binary = (BinaryExecutable) node;
            h = h * 31 + binary.getOperator().name().hashCode();
            h = mix(h, binary.getLeft());
            h = mix(h, binary.getRight());
        } else if (node instanceof AbstractObjFunctionExecutable) {
            AbstractObjFunctionExecutable fn = (AbstractObjFunctionExecutable) node;
            h = h * 31 + fn.getFuncName().hashCode();
            h = mix(h, fn.getObjExpr());
            h = mixAll(h, fn.getArgs());
        } else if (node instanceof FunctionExecutable) {
            FunctionExecutable fn = (FunctionExecutable) node;
            h = h * 31 + fn.getFuncName().hashCode();
            h = mixAll(h, fn.getArgs());
        } else if (node instanceof StaticFunctionExecutable) {
            StaticFunctionExecutable fn = (StaticFunctionExecutable) node;
            h = h * 31 + fn.getClassName().hashCode();
            h = h * 31 + fn.getFuncName().hashCode();
            h = h * 31 + (fn.isOptional() ? 1 : 0);
            h = mixAll(h, fn.getArgExprs());
        } else if (node instanceof NotExecutable) {
            h = mix(h, ((NotExecutable) node).getExpr());
        } else if (node instanceof GuardNotNullExecutable) {
            h = mix(h, ((GuardNotNullExecutable) node).getExpr());
        } else if (node instanceof ReturnNullExecutable) {
            h = mix(h, ((ReturnNullExecutable) node).getExecutable());
        } else if (node instanceof ISeqExecutable) {
            h = mixAll(h, ((ISeqExecutable) node).getExprs());
        } else if (node instanceof NullExecutable) {
            // 无语义载荷
        } else if (node instanceof ScopeIdentifierExecutable) {
            h = h * 31 + strHash(((ScopeIdentifierExecutable) node).getVarName());
        } else if (node instanceof GlobalVarExecutable) {
            h = h * 31 + strHash(((GlobalVarExecutable) node).getVarName());
        } else if (node instanceof ScopeAssignExecutable) {
            ScopeAssignExecutable assign = (ScopeAssignExecutable) node;
            h = h * 31 + strHash(assign.getVarName());
            h = mix(h, assign.getExpr());
        } else if (node instanceof ScopeSelfAssignExecutable) {
            ScopeSelfAssignExecutable self = (ScopeSelfAssignExecutable) node;
            h = h * 31 + strHash(self.getVarName());
            h = h * 31 + self.getOperator().name().hashCode();
            h = mix(h, self.getExpr());
        } else if (node instanceof ScopeSelfIncExecutable) {
            h = h * 31 + strHash(((ScopeSelfIncExecutable) node).getVarName());
        } else if (node instanceof ScopeSelfDecExecutable) {
            h = h * 31 + strHash(((ScopeSelfDecExecutable) node).getVarName());
        } else if (node instanceof ReferenceIdentifierExecutable) {
            ReferenceIdentifierExecutable ref = (ReferenceIdentifierExecutable) node;
            h = h * 31 + strHash(ref.getId());
            h = h * 31 + ref.getSlot();
        } else if (node instanceof ReferenceAssignExecutable) {
            ReferenceAssignExecutable assign = (ReferenceAssignExecutable) node;
            h = h * 31 + strHash(assign.getVarName());
            h = h * 31 + assign.getSlot();
            h = mix(h, assign.getExpr());
        } else if (node instanceof ReferenceSelfAssignExecutable) {
            ReferenceSelfAssignExecutable self = (ReferenceSelfAssignExecutable) node;
            h = h * 31 + strHash(self.getVarName());
            h = h * 31 + self.getSlot();
            h = h * 31 + self.getOperator().name().hashCode();
            h = mix(h, self.getExpr());
        } else if (node instanceof ReferenceSelfIncExecutable) {
            ReferenceSelfIncExecutable self =
                    (ReferenceSelfIncExecutable) node;
            h = h * 31 + strHash(self.getVarName());
            h = h * 31 + self.getSlot();
        } else if (node instanceof ReferenceSelfDecExecutable) {
            ReferenceSelfDecExecutable self =
                    (ReferenceSelfDecExecutable) node;
            h = h * 31 + strHash(self.getVarName());
            h = h * 31 + self.getSlot();
        } else if (node instanceof RenewReferenceExecutable) {
            RenewReferenceExecutable renew = (RenewReferenceExecutable) node;
            h = h * 31 + strHash(renew.getVarName());
            h = h * 31 + renew.getSlot();
        } else if (node instanceof CastExecutable) {
            CastExecutable cast = (CastExecutable) node;
            h = h * 31 + strHash(cast.getClazz().getName());
            h = mix(h, cast.getExpr());
        } else if (node instanceof ConvertExecutable) {
            ConvertExecutable convert = (ConvertExecutable) node;
            h = h * 31 + strHash(convert.getFuncName());
            h = mix(h, convert.getExpr());
        } else if (node instanceof ConvertWithDefaultExecutable) {
            ConvertWithDefaultExecutable convert = (ConvertWithDefaultExecutable) node;
            h = h * 31 + strHash(convert.getFuncName());
            h = mix(h, convert.getExpr());
            h = mix(h, convert.getDefaultExpr());
        } else if (node instanceof InstanceOfExecutable) {
            InstanceOfExecutable inst = (InstanceOfExecutable) node;
            h = h * 31 + strHash(inst.getType().getRawClass().getName());
            h = mix(h, inst.getExpr());
        } else if (node instanceof TypeOfExecutable) {
            h = mix(h, ((TypeOfExecutable) node).getExpr());
        } else if (node instanceof NegExecutable) {
            h = mix(h, ((NegExecutable) node).getExpr());
        } else if (node instanceof BitNotExecutable) {
            h = mix(h, ((BitNotExecutable) node).getExpr());
        } else if (node instanceof EqNullExecutable
                || node instanceof StrictEqNullExecutable) {
            h = mix(h, node instanceof EqNullExecutable
                    ? ((EqNullExecutable) node).getExpr() : ((StrictEqNullExecutable) node).getExpr());
        } else if (node instanceof NeNullExecutable
                || node instanceof StrictNeNullExecutable) {
            h = mix(h, node instanceof NeNullExecutable
                    ? ((NeNullExecutable) node).getExpr() : ((StrictNeNullExecutable) node).getExpr());
        } else if (node instanceof MakePropertyExecutable || node instanceof GetPropertyExecutable) {
            GetPropertyExecutable get = (GetPropertyExecutable) node;
            h = h * 31 + strHash(get.getPropName());
            h = h * 31 + (get.isOptional() ? 1 : 0);
            h = mix(h, get.getObjExpr());
        } else if (node instanceof GetterGetPropertyExecutable) {
            GetterGetPropertyExecutable get = (GetterGetPropertyExecutable) node;
            h = h * 31 + strHash(get.getPropName());
            h = mix(h, get.getObjExpr());
        } else if (node instanceof StaticGetterGetPropertyExecutable) {
            StaticGetterGetPropertyExecutable get = (StaticGetterGetPropertyExecutable) node;
            h = h * 31 + strHash(get.getClassName());
            h = h * 31 + strHash(get.getPropName());
        } else if (node instanceof SetPropertyExecutable) {
            SetPropertyExecutable set = (SetPropertyExecutable) node;
            h = h * 31 + strHash(set.getPropName());
            h = mix(h, set.getObjExpr());
            h = mix(h, set.getValueExpr());
        } else if (node instanceof SetterSetPropertyExecutable) {
            SetterSetPropertyExecutable set =
                    (SetterSetPropertyExecutable) node;
            h = h * 31 + strHash(set.getPropName());
            h = mix(h, set.getObjExpr());
            h = mix(h, set.getValueExpr());
        } else if (node instanceof SelfAssignPropertyExecutable) {
            SelfAssignPropertyExecutable self = (SelfAssignPropertyExecutable) node;
            h = h * 31 + strHash(self.getPropName());
            h = h * 31 + self.getOperator().name().hashCode();
            h = mix(h, self.getObjExpr());
            h = mix(h, self.getValueExpr());
        } else if (node instanceof GetAttrExecutable) {
            GetAttrExecutable get = (GetAttrExecutable) node;
            h = h * 31 + (get.isOptional() ? 1 : 0);
            h = mix(h, get.getObjExpr());
            h = mix(h, get.getAttrExpr());
        } else if (node instanceof SetAttrExecutable) {
            SetAttrExecutable set = (SetAttrExecutable) node;
            h = mix(h, set.getObjExpr());
            h = mix(h, set.getAttrExpr());
            h = mix(h, set.getValueExpr());
        } else if (node instanceof SelfAssignAttrExecutable) {
            SelfAssignAttrExecutable self = (SelfAssignAttrExecutable) node;
            h = h * 31 + self.getOperator().name().hashCode();
            h = mix(h, self.getObjExpr());
            h = mix(h, self.getAttrExpr());
            h = mix(h, self.getValueExpr());
        } else if (node instanceof NewObjectExecutable) {
            NewObjectExecutable newExpr = (NewObjectExecutable) node;
            h = h * 31 + strHash(newExpr.getClassModel().getClassName());
            h = mixAll(h, newExpr.getArgExprs());
        } else if (node instanceof NewListExecutable) {
            NewListExecutable list = (NewListExecutable) node;
            ListItemExecutable[] items = list.getItems();
            h = h * 31 + items.length;
            for (ListItemExecutable item : items) {
                h = h * 31 + (item.isSpread() ? 1 : 0);
                h = mix(h, item.getValueExpr());
            }
        } else if (node instanceof NewMapExecutable) {
            NewMapExecutable map = (NewMapExecutable) node;
            MapItemExecutable[] items = map.getItems();
            h = h * 31 + items.length;
            for (MapItemExecutable item : items) {
                h = h * 31 + (item.isSpread() ? 1 : 0);
                h = mix(h, item.getKeyExpr());
                h = mix(h, item.getValueExpr());
            }
        } else if (node instanceof BindVarExecutable) {
            BindVarExecutable bind = (BindVarExecutable) node;
            int[] slots = bind.getSlots();
            Object[] vars = bind.getVars();
            h = h * 31 + slots.length;
            for (int i = 0; i < slots.length; i++) {
                h = h * 31 + slots[i];
                h = mixValue(h, vars[i]);
            }
            h = mix(h, bind.getExpr());
        } else if (node instanceof ArrayBindingAssignExecutable) {
            ArrayBindingAssignExecutable binding = (ArrayBindingAssignExecutable) node;
            h = mix(h, binding.getExpr());
            for (AssignIdentifier id : binding.getElementBindings()) {
                h = mixAssign(h, id);
            }
            if (binding.getRestBinding() != null)
                h = mixAssign(h, binding.getRestBinding());
        } else if (node instanceof ObjectBindingAssignExecutable) {
            ObjectBindingAssignExecutable binding = (ObjectBindingAssignExecutable) node;
            h = mix(h, binding.getExpr());
            for (PropBinding prop : binding.getPropBindings()) {
                h = h * 31 + strHash(prop.getKey());
                h = mixAssign(h, prop);
            }
            if (binding.getRestBinding() != null)
                h = mixAssign(h, binding.getRestBinding());
        } else if (node instanceof InitRefSlotExecutable) {
            InitRefSlotExecutable init = (InitRefSlotExecutable) node;
            h = h * 31 + strHash(init.getVarName());
            h = h * 31 + init.getSlot();
        } else if (node instanceof EnhanceRefSlotExecutable) {
            EnhanceRefSlotExecutable enhance =
                    (EnhanceRefSlotExecutable) node;
            h = h * 31 + strHash(enhance.getVarName());
            h = h * 31 + enhance.getSlot();
        } else if (node instanceof GuardNotEmptyExecutable) {
            GuardNotEmptyExecutable guard = (GuardNotEmptyExecutable) node;
            h = h * 31 + strHash(String.valueOf(guard.getTarget()));
            h = mix(h, guard.getExpr());
        } else if (node instanceof DebugExecutable) {
            DebugExecutable debug = (DebugExecutable) node;
            h = mix(h, debug.getValueExpr());
            h = mix(h, debug.getPrefixExpr());
        } else if (node instanceof DebugIdentifierExecutable) {
            h = h * 31 + strHash(((DebugIdentifierExecutable) node).getVarName());
        } else if (node instanceof VarStatusExecutable) {
            VarStatusExecutable varStatus = (VarStatusExecutable) node;
            h = h * 31 + strHash(varStatus.getVarStatusName());
            h = h * 31 + varStatus.getVarStatusSlot();
            h = mix(h, varStatus.getItemsExpr());
        } else if (node instanceof SelfAssignExecutable) {
            SelfAssignExecutable self = (SelfAssignExecutable) node;
            h = h * 31 + strHash(self.getVarName());
            h = h * 31 + self.getSlot();
            h = h * 31 + self.getOperator().name().hashCode();
            h = mix(h, self.getExpr());
        } else if (node instanceof SelfIncExecutable) {
            SelfIncExecutable self = (SelfIncExecutable) node;
            h = h * 31 + strHash(self.getVarName());
            h = h * 31 + self.getSlot();
        } else if (node instanceof SelfDecExecutable) {
            SelfDecExecutable self = (SelfDecExecutable) node;
            h = h * 31 + strHash(self.getVarName());
            h = h * 31 + self.getSlot();
        } else if (node instanceof RangeExecutable) {
            RangeExecutable range = (RangeExecutable) node;
            h = mix(h, range.getBeginExpr());
            h = mix(h, range.getEndExpr());
            h = mix(h, range.getStepExpr());
        } else if (node instanceof BetweenOpExecutable) {
            BetweenOpExecutable between = (BetweenOpExecutable) node;
            h = h * 31 + between.getFilterOp().name().hashCode();
            h = h * 31 + (between.isExcludeMin() ? 1 : 0);
            h = h * 31 + (between.isExcludeMax() ? 1 : 0);
            h = mix(h, between.getValueExpr());
            h = mix(h, between.getMinExpr());
            h = mix(h, between.getMaxExpr());
        } else if (node instanceof AssertOpExecutable) {
            AssertOpExecutable assertOp = (AssertOpExecutable) node;
            h = h * 31 + assertOp.getFilterOp().name().hashCode();
            h = mix(h, assertOp.getValueExpr());
        } else if (node instanceof PropInExecutable
                || node instanceof NullCoalesceExecutable) {
            // 无标量载荷：类名 + 子树即全载荷（经下方结构分支混子节点）
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            h = mix(h, binary.getLeft());
            h = mix(h, binary.getRight());
        } else if (node instanceof ConcatExecutable) {
            h = mixAll(h, ((ConcatExecutable) node).getExprs());
        } else if (node instanceof AbstractBinaryExecutable) {
            // 二元算术/比较/逻辑族：无标量载荷，仅类名 + 子节点（Plus/Minus/... 族由类名完全确定语义）
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            h = mix(h, binary.getLeft());
            h = mix(h, binary.getRight());
        } else if (ExecToTruffleTranslator.isNodeClassSupported(node.getClass())) {
            // 载荷覆盖硬保证：可翻译节点类必须有显式白名单分支，禁止静默弱哈希兜底
            throw new IllegalStateException(
                    "tree fingerprint payload coverage missing for translatable node: "
                            + node.getClass().getName());
        } else {
            // 不可翻译节点（翻译必然 fail-fast，不进入缓存）：弱哈希兜底仅保留类名 + 位置
        }
        return h;
    }

    private static long mixAssign(long seed, AssignIdentifier id) {
        long h = seed * 31 + id.getVarSlot();
        h = h * 31 + strHash(id.getVarName());
        h = h * 31 + (id.isUseRef() ? 1 : 0);
        if (id.getInitializer() != null)
            h = mix(h, id.getInitializer());
        return h;
    }

    private static long mixValue(long seed, Object value) {
        long h = seed * 31 + (value == null ? 0 : value.getClass().getName().hashCode());
        return h * 31 + (value == null ? 0 : String.valueOf(value).hashCode());
    }

    private static long mixAll(long seed, IExecutableExpression[] nodes) {
        long h = seed;
        if (nodes == null)
            return h * 31;
        h = h * 31 + nodes.length;
        for (IExecutableExpression node : nodes) {
            h = mix(h, node);
        }
        return h;
    }

    private static long arrayHash(String[] values) {
        if (values == null)
            return 0;
        long h = values.length;
        for (String value : values) {
            h = h * 31 + (value == null ? 0 : value.hashCode());
        }
        return h;
    }

    private static long strHash(String value) {
        return value == null ? 0 : value.hashCode();
    }

    private static long locationHash(SourceLocation loc) {
        if (loc == null)
            return 0;
        long h = loc.getPath() == null ? 0 : loc.getPath().hashCode();
        h = h * 31 + loc.getLine();
        h = h * 31 + loc.getCol();
        return h;
    }
}
