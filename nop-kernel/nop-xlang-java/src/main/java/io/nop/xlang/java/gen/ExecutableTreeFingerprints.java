/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.gen;

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
import io.nop.xlang.exec.BreakExecutable;
import io.nop.xlang.exec.BuildClosureBodyExecutable;
import io.nop.xlang.exec.BuildFuncRefExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CallFuncWithClosureExecutable;
import io.nop.xlang.exec.CastExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.CollectJsonExecutable;
import io.nop.xlang.exec.CollectNodeExecutable;
import io.nop.xlang.exec.CollectSqlExecutable;
import io.nop.xlang.exec.CollectTextExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ContinueExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DebugIdentifierExecutable;
import io.nop.xlang.exec.DeleteAttrExecutable;
import io.nop.xlang.exec.DeletePropertyExecutable;
import io.nop.xlang.exec.DeleteScopeVarExecutable;
import io.nop.xlang.exec.DoWhileExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.EqNullExecutable;
import io.nop.xlang.exec.EscapeOutputExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.ForExecutable;
import io.nop.xlang.exec.ForInExecutable;
import io.nop.xlang.exec.ForOfExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.FunctionalAdapterExecutable;
import io.nop.xlang.exec.GenNodeAttrExecutable;
import io.nop.xlang.exec.GenNodeExecutable;
import io.nop.xlang.exec.GenXJsonExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GetterGetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.IfExecutable;
import io.nop.xlang.exec.InitRefSlotExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.LazyCompiledExecutableFunction;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.LocationFunction;
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
import io.nop.xlang.exec.OutputTextExecutable;
import io.nop.xlang.exec.OutputValueExecutable;
import io.nop.xlang.exec.OutputXmlAttrExecutable;
import io.nop.xlang.exec.OutputXmlExtAttrsExecutable;
import io.nop.xlang.exec.PropBinding;
import io.nop.xlang.exec.PropInExecutable;
import io.nop.xlang.exec.RangeExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ReferenceSelfAssignExecutable;
import io.nop.xlang.exec.ReferenceSelfDecExecutable;
import io.nop.xlang.exec.ReferenceSelfIncExecutable;
import io.nop.xlang.exec.RenewReferenceExecutable;
import io.nop.xlang.exec.ReturnExecutable;
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
import io.nop.xlang.exec.SetPropertyExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StaticGetterGetPropertyExecutable;
import io.nop.xlang.exec.StrictEqNullExecutable;
import io.nop.xlang.exec.StrictNeNullExecutable;
import io.nop.xlang.exec.SwitchExecutable;
import io.nop.xlang.exec.ThrowErrorCodeExecutable;
import io.nop.xlang.exec.ThrowExceptionExecutable;
import io.nop.xlang.exec.TryExecutable;
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarExecutableFunction;
import io.nop.xlang.exec.VarFunctionExecutable;
import io.nop.xlang.exec.VarStatusExecutable;
import io.nop.xlang.exec.WhileExecutable;
import io.nop.xlang.java.translator.ExecToJavaTranslator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * java 侧 Executable 树指纹（I10 D1 裁定）：hex SHA-256——防 stale 校验且为构建期生成类清单
 * （I11 持久化产物）的数据界面，"必不同"以完整 digest 成立（truffle 侧 long 仅为缓存键口径，
 * 两侧互不比较）。
 *
 * <p>载荷纪律与 truffle {@code TreeFingerprints} 对称（共享纪律而非共享实现——I10 Phase 1 D1）：
 * 递归混合节点类名、源位置（path:line:col）与节点语义载荷（标量载荷 + 子表达式结构），不含对象
 * 身份——结构相同的两棵树指纹相同，任一差异（含源位置差异）必产生不同指纹（字段以类型标签 +
 * 长度前缀定界，防拼接歧义），保证"同路径不同树"（租户差异、资源热变更后重载）不串用生成类。
 *
 * <p>载荷覆盖硬保证：全部可翻译节点类（{@link ExecToJavaTranslator#isNodeClassSupported}）
 * 均有显式白名单分支；兜底分支对<b>可翻译但载荷未混合</b>的节点类 fail-fast（防新增可翻译
 * 节点类静默落入弱哈希——同 resourcePath 树变更指纹碰撞串用旧生成类，设计 java §五自认
 * 最危险缺陷形态）；不可翻译节点允许弱哈希兜底（转译必然 fail-fast，不进清单）。
 */
public final class ExecutableTreeFingerprints {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ExecutableTreeFingerprints() {
    }

    /** 计算树的 hex SHA-256 指纹（64 字符小写） */
    public static String fingerprint(IExecutableExpression tree) {
        MessageDigest md = newDigest();
        mix(md, tree);
        return hex(md.digest());
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private static void mix(MessageDigest md, IExecutableExpression node) {
        if (node == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'E');
        updateString(md, node.getClass().getName());
        updateLocation(md, node.getLocation());

        if (node instanceof LiteralExecutable) {
            Object value = ((LiteralExecutable) node).getValue();
            if (value instanceof ExecutableFunction) {
                // 函数字面量载荷下降：全量函数载荷混合（slotNames/参数规格/缺省/函数体）
                updateBoolean(md, true);
                mixExecutableFunction(md, (ExecutableFunction) value);
            } else {
                updateBoolean(md, false);
                mixValue(md, value);
            }
        } else if (node instanceof ExecutableFunction) {
            // 根级 ExecutableFunction（I11 xlib 每标签形态）：函数本身为指纹施加对象，
            // 全量函数载荷混合（签名 + 缺省 + 函数体——防参数变更 stale 漏检）。
            // 纯增量分支：既有调用方不传函数根，行为不变。
            mixExecutableFunction(md, (ExecutableFunction) node);
        } else if (node instanceof CloneLiteralExecutable) {
            mixValue(md, ((CloneLiteralExecutable) node).getValue());
        } else if (node instanceof SlotIdentifierExecutable) {
            updateInt(md, ((SlotIdentifierExecutable) node).getSlot());
        } else if (node instanceof SlotAssignExecutable) {
            SlotAssignExecutable assign = (SlotAssignExecutable) node;
            updateInt(md, assign.getSlot());
            mix(md, assign.getExpr());
        } else if (node instanceof CallFuncExecutable) {
            CallFuncExecutable entry = (CallFuncExecutable) node;
            updateString(md, entry.getFuncName());
            updateStringArray(md, entry.getSlotNames());
            mixAll(md, entry.getArgExprs());
            mix(md, entry.getBodyExpr());
        } else if (node instanceof CompareOpExecutable) {
            CompareOpExecutable cmp = (CompareOpExecutable) node;
            updateString(md, cmp.getFilterOp().name());
            mix(md, cmp.getLeft());
            mix(md, cmp.getRight());
        } else if (node instanceof BinaryExecutable) {
            BinaryExecutable binary = (BinaryExecutable) node;
            updateString(md, binary.getOperator().name());
            mix(md, binary.getLeft());
            mix(md, binary.getRight());
        } else if (node instanceof AbstractObjFunctionExecutable) {
            AbstractObjFunctionExecutable fn = (AbstractObjFunctionExecutable) node;
            updateString(md, fn.getFuncName());
            mix(md, fn.getObjExpr());
            mixAll(md, fn.getArgs());
        } else if (node instanceof FunctionExecutable) {
            FunctionExecutable fn = (FunctionExecutable) node;
            updateString(md, fn.getFuncName());
            mixAll(md, fn.getArgs());
        } else if (node instanceof StaticFunctionExecutable) {
            StaticFunctionExecutable fn = (StaticFunctionExecutable) node;
            updateString(md, fn.getClassName());
            updateString(md, fn.getFuncName());
            updateBoolean(md, fn.isOptional());
            mixAll(md, fn.getArgExprs());
        } else if (node instanceof NotExecutable) {
            mix(md, ((NotExecutable) node).getExpr());
        } else if (node instanceof GuardNotNullExecutable) {
            mix(md, ((GuardNotNullExecutable) node).getExpr());
        } else if (node instanceof ReturnNullExecutable) {
            mix(md, ((ReturnNullExecutable) node).getExecutable());
        } else if (node instanceof ISeqExecutable) {
            mixAll(md, ((ISeqExecutable) node).getExprs());
        } else if (node instanceof NullExecutable) {
            // 无语义载荷
        } else if (node instanceof ScopeIdentifierExecutable) {
            updateString(md, ((ScopeIdentifierExecutable) node).getVarName());
        } else if (node instanceof GlobalVarExecutable) {
            updateString(md, ((GlobalVarExecutable) node).getVarName());
        } else if (node instanceof ScopeAssignExecutable) {
            ScopeAssignExecutable assign = (ScopeAssignExecutable) node;
            updateString(md, assign.getVarName());
            mix(md, assign.getExpr());
        } else if (node instanceof ScopeSelfAssignExecutable) {
            ScopeSelfAssignExecutable self = (ScopeSelfAssignExecutable) node;
            updateString(md, self.getVarName());
            updateString(md, self.getOperator().name());
            mix(md, self.getExpr());
        } else if (node instanceof ScopeSelfIncExecutable) {
            updateString(md, ((ScopeSelfIncExecutable) node).getVarName());
        } else if (node instanceof ScopeSelfDecExecutable) {
            updateString(md, ((ScopeSelfDecExecutable) node).getVarName());
        } else if (node instanceof ReferenceIdentifierExecutable) {
            ReferenceIdentifierExecutable ref = (ReferenceIdentifierExecutable) node;
            updateString(md, ref.getId());
            updateInt(md, ref.getSlot());
        } else if (node instanceof ReferenceAssignExecutable) {
            ReferenceAssignExecutable assign = (ReferenceAssignExecutable) node;
            updateString(md, assign.getVarName());
            updateInt(md, assign.getSlot());
            mix(md, assign.getExpr());
        } else if (node instanceof ReferenceSelfAssignExecutable) {
            ReferenceSelfAssignExecutable self = (ReferenceSelfAssignExecutable) node;
            updateString(md, self.getVarName());
            updateInt(md, self.getSlot());
            updateString(md, self.getOperator().name());
            mix(md, self.getExpr());
        } else if (node instanceof ReferenceSelfIncExecutable) {
            ReferenceSelfIncExecutable self = (ReferenceSelfIncExecutable) node;
            updateString(md, self.getVarName());
            updateInt(md, self.getSlot());
        } else if (node instanceof ReferenceSelfDecExecutable) {
            ReferenceSelfDecExecutable self = (ReferenceSelfDecExecutable) node;
            updateString(md, self.getVarName());
            updateInt(md, self.getSlot());
        } else if (node instanceof RenewReferenceExecutable) {
            RenewReferenceExecutable renew = (RenewReferenceExecutable) node;
            updateString(md, renew.getVarName());
            updateInt(md, renew.getSlot());
        } else if (node instanceof CastExecutable) {
            CastExecutable cast = (CastExecutable) node;
            updateString(md, cast.getClazz().getName());
            mix(md, cast.getExpr());
        } else if (node instanceof ConvertExecutable) {
            ConvertExecutable convert = (ConvertExecutable) node;
            updateString(md, convert.getFuncName());
            mix(md, convert.getExpr());
        } else if (node instanceof ConvertWithDefaultExecutable) {
            ConvertWithDefaultExecutable convert = (ConvertWithDefaultExecutable) node;
            updateString(md, convert.getFuncName());
            mix(md, convert.getExpr());
            mix(md, convert.getDefaultExpr());
        } else if (node instanceof InstanceOfExecutable) {
            InstanceOfExecutable inst = (InstanceOfExecutable) node;
            updateString(md, inst.getType().getRawClass().getName());
            mix(md, inst.getExpr());
        } else if (node instanceof TypeOfExecutable) {
            mix(md, ((TypeOfExecutable) node).getExpr());
        } else if (node instanceof NegExecutable) {
            mix(md, ((NegExecutable) node).getExpr());
        } else if (node instanceof BitNotExecutable) {
            mix(md, ((BitNotExecutable) node).getExpr());
        } else if (node instanceof EqNullExecutable || node instanceof StrictEqNullExecutable) {
            mix(md, node instanceof EqNullExecutable
                    ? ((EqNullExecutable) node).getExpr() : ((StrictEqNullExecutable) node).getExpr());
        } else if (node instanceof NeNullExecutable || node instanceof StrictNeNullExecutable) {
            mix(md, node instanceof NeNullExecutable
                    ? ((NeNullExecutable) node).getExpr() : ((StrictNeNullExecutable) node).getExpr());
        } else if (node instanceof MakePropertyExecutable || node instanceof GetPropertyExecutable) {
            GetPropertyExecutable get = (GetPropertyExecutable) node;
            updateString(md, get.getPropName());
            updateBoolean(md, get.isOptional());
            mix(md, get.getObjExpr());
        } else if (node instanceof GetterGetPropertyExecutable) {
            GetterGetPropertyExecutable get = (GetterGetPropertyExecutable) node;
            updateString(md, get.getPropName());
            mix(md, get.getObjExpr());
        } else if (node instanceof StaticGetterGetPropertyExecutable) {
            StaticGetterGetPropertyExecutable get = (StaticGetterGetPropertyExecutable) node;
            updateString(md, get.getClassName());
            updateString(md, get.getPropName());
        } else if (node instanceof SetPropertyExecutable) {
            SetPropertyExecutable set = (SetPropertyExecutable) node;
            updateString(md, set.getPropName());
            mix(md, set.getObjExpr());
            mix(md, set.getValueExpr());
        } else if (node instanceof SetterSetPropertyExecutable) {
            SetterSetPropertyExecutable set = (SetterSetPropertyExecutable) node;
            updateString(md, set.getPropName());
            mix(md, set.getObjExpr());
            mix(md, set.getValueExpr());
        } else if (node instanceof SelfAssignPropertyExecutable) {
            SelfAssignPropertyExecutable self = (SelfAssignPropertyExecutable) node;
            updateString(md, self.getPropName());
            updateString(md, self.getOperator().name());
            mix(md, self.getObjExpr());
            mix(md, self.getValueExpr());
        } else if (node instanceof GetAttrExecutable) {
            GetAttrExecutable get = (GetAttrExecutable) node;
            updateBoolean(md, get.isOptional());
            mix(md, get.getObjExpr());
            mix(md, get.getAttrExpr());
        } else if (node instanceof SetAttrExecutable) {
            SetAttrExecutable set = (SetAttrExecutable) node;
            mix(md, set.getObjExpr());
            mix(md, set.getAttrExpr());
            mix(md, set.getValueExpr());
        } else if (node instanceof DeletePropertyExecutable) {
            DeletePropertyExecutable del = (DeletePropertyExecutable) node;
            updateString(md, del.getPropName());
            mix(md, del.getObjExpr());
        } else if (node instanceof DeleteAttrExecutable) {
            DeleteAttrExecutable del = (DeleteAttrExecutable) node;
            mix(md, del.getObjExpr());
            mix(md, del.getAttrExpr());
        } else if (node instanceof DeleteScopeVarExecutable) {
            DeleteScopeVarExecutable del = (DeleteScopeVarExecutable) node;
            updateString(md, del.getVarName());
            updateBoolean(md, del.getAttrExpr() != null);
            if (del.getAttrExpr() != null)
                mix(md, del.getAttrExpr());
        } else if (node instanceof SelfAssignAttrExecutable) {
            SelfAssignAttrExecutable self = (SelfAssignAttrExecutable) node;
            updateString(md, self.getOperator().name());
            mix(md, self.getObjExpr());
            mix(md, self.getAttrExpr());
            mix(md, self.getValueExpr());
        } else if (node instanceof NewObjectExecutable) {
            NewObjectExecutable newExpr = (NewObjectExecutable) node;
            updateString(md, newExpr.getClassModel().getClassName());
            mixAll(md, newExpr.getArgExprs());
        } else if (node instanceof NewListExecutable) {
            NewListExecutable list = (NewListExecutable) node;
            ListItemExecutable[] items = list.getItems();
            updateInt(md, items.length);
            for (ListItemExecutable item : items) {
                updateBoolean(md, item.isSpread());
                mix(md, item.getValueExpr());
            }
        } else if (node instanceof NewMapExecutable) {
            NewMapExecutable map = (NewMapExecutable) node;
            MapItemExecutable[] items = map.getItems();
            updateInt(md, items.length);
            for (MapItemExecutable item : items) {
                updateBoolean(md, item.isSpread());
                mix(md, item.getKeyExpr());
                mix(md, item.getValueExpr());
            }
        } else if (node instanceof BindVarExecutable) {
            BindVarExecutable bind = (BindVarExecutable) node;
            int[] slots = bind.getSlots();
            Object[] vars = bind.getVars();
            updateInt(md, slots.length);
            for (int i = 0; i < slots.length; i++) {
                updateInt(md, slots[i]);
                mixValue(md, vars[i]);
            }
            mix(md, bind.getExpr());
        } else if (node instanceof ArrayBindingAssignExecutable) {
            ArrayBindingAssignExecutable binding = (ArrayBindingAssignExecutable) node;
            mix(md, binding.getExpr());
            for (AssignIdentifier id : binding.getElementBindings()) {
                mixAssign(md, id);
            }
            if (binding.getRestBinding() != null)
                mixAssign(md, binding.getRestBinding());
        } else if (node instanceof ObjectBindingAssignExecutable) {
            ObjectBindingAssignExecutable binding = (ObjectBindingAssignExecutable) node;
            mix(md, binding.getExpr());
            for (PropBinding prop : binding.getPropBindings()) {
                updateString(md, prop.getKey());
                mixAssign(md, prop);
            }
            if (binding.getRestBinding() != null)
                mixAssign(md, binding.getRestBinding());
        } else if (node instanceof InitRefSlotExecutable) {
            InitRefSlotExecutable init = (InitRefSlotExecutable) node;
            updateString(md, init.getVarName());
            updateInt(md, init.getSlot());
        } else if (node instanceof EnhanceRefSlotExecutable) {
            EnhanceRefSlotExecutable enhance = (EnhanceRefSlotExecutable) node;
            updateString(md, enhance.getVarName());
            updateInt(md, enhance.getSlot());
        } else if (node instanceof GuardNotEmptyExecutable) {
            GuardNotEmptyExecutable guard = (GuardNotEmptyExecutable) node;
            updateString(md, String.valueOf(guard.getTarget()));
            mix(md, guard.getExpr());
        } else if (node instanceof DebugExecutable) {
            DebugExecutable debug = (DebugExecutable) node;
            mix(md, debug.getValueExpr());
            mix(md, debug.getPrefixExpr());
        } else if (node instanceof DebugIdentifierExecutable) {
            updateString(md, ((DebugIdentifierExecutable) node).getVarName());
        } else if (node instanceof VarStatusExecutable) {
            VarStatusExecutable varStatus = (VarStatusExecutable) node;
            updateString(md, varStatus.getVarStatusName());
            updateInt(md, varStatus.getVarStatusSlot());
            mix(md, varStatus.getItemsExpr());
        } else if (node instanceof SelfAssignExecutable) {
            SelfAssignExecutable self = (SelfAssignExecutable) node;
            updateString(md, self.getVarName());
            updateInt(md, self.getSlot());
            updateString(md, self.getOperator().name());
            mix(md, self.getExpr());
        } else if (node instanceof SelfIncExecutable) {
            SelfIncExecutable self = (SelfIncExecutable) node;
            updateString(md, self.getVarName());
            updateInt(md, self.getSlot());
        } else if (node instanceof SelfDecExecutable) {
            SelfDecExecutable self = (SelfDecExecutable) node;
            updateString(md, self.getVarName());
            updateInt(md, self.getSlot());
        } else if (node instanceof RangeExecutable) {
            RangeExecutable range = (RangeExecutable) node;
            mix(md, range.getBeginExpr());
            mix(md, range.getEndExpr());
            mix(md, range.getStepExpr());
        } else if (node instanceof BetweenOpExecutable) {
            BetweenOpExecutable between = (BetweenOpExecutable) node;
            updateString(md, between.getFilterOp().name());
            updateBoolean(md, between.isExcludeMin());
            updateBoolean(md, between.isExcludeMax());
            mix(md, between.getValueExpr());
            mix(md, between.getMinExpr());
            mix(md, between.getMaxExpr());
        } else if (node instanceof AssertOpExecutable) {
            AssertOpExecutable assertOp = (AssertOpExecutable) node;
            updateString(md, assertOp.getFilterOp().name());
            mix(md, assertOp.getValueExpr());
        } else if (node instanceof PropInExecutable || node instanceof NullCoalesceExecutable) {
            // 无标量载荷：类名 + 子树即全载荷
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            mix(md, binary.getLeft());
            mix(md, binary.getRight());
        } else if (node instanceof ConcatExecutable) {
            mixAll(md, ((ConcatExecutable) node).getExprs());
        } else if (node instanceof AbstractBinaryExecutable) {
            // 二元算术/比较/逻辑族（Plus/Minus/And/Or/Eq/... 等）：无标量载荷，仅类名 + 子节点
            // （族内每个具体类由类名完全确定语义）
            AbstractBinaryExecutable binary = (AbstractBinaryExecutable) node;
            mix(md, binary.getLeft());
            mix(md, binary.getRight());
        } else if (node instanceof CallFuncWithClosureExecutable) {
            CallFuncWithClosureExecutable call = (CallFuncWithClosureExecutable) node;
            updateString(md, call.getFuncName());
            updateStringArray(md, call.getSlotNames());
            updateIntArray(md, call.getSourceSlots());
            updateIntArray(md, call.getTargetSlots());
            mixAll(md, call.getArgExprs());
            mix(md, call.getBodyExpr());
        } else if (node instanceof BuildFuncRefExecutable) {
            BuildFuncRefExecutable ref = (BuildFuncRefExecutable) node;
            updateIntArray(md, ref.getSourceSlots());
            updateIntArray(md, ref.getTargetSlots());
            mixExecutableFunction(md, ref.getFunc());
        } else if (node instanceof BuildClosureBodyExecutable) {
            BuildClosureBodyExecutable closure = (BuildClosureBodyExecutable) node;
            updateInt(md, closure.getClosureSlot());
            updateIntArray(md, closure.getSourceSlots());
            updateIntArray(md, closure.getTargetSlots());
            mix(md, closure.getExpr());
        } else if (node instanceof VarFunctionExecutable) {
            VarFunctionExecutable var = (VarFunctionExecutable) node;
            updateBoolean(md, var.isOptional());
            mix(md, var.getFuncExpr());
            mixAll(md, var.getArgs());
        } else if (node instanceof VarExecutableFunction) {
            VarExecutableFunction var = (VarExecutableFunction) node;
            updateBoolean(md, var.isOptional());
            mix(md, var.getFuncExpr());
            mixAll(md, var.getArgs());
        } else if (node instanceof LazyCompiledExecutableFunction) {
            LazyCompiledExecutableFunction lazy = (LazyCompiledExecutableFunction) node;
            updateString(md, lazy.getFuncName());
            mixAll(md, lazy.getArgExprs());
            mixExecutableFunction(md, compiledOrNull(lazy));
        } else if (node instanceof FunctionalAdapterExecutable) {
            // 载荷 = 运行期 IEvalFunction 常量对象（全仓无产生路径，仅合成树）：混合类名；
            // 函数对象无结构化表示（非身份哈希——指纹不含对象身份），同 sourceKey 不同载荷
            // 无碰撞场景（无产生路径 + sourceKey 唯一），与 truffle 侧同构记录
            updateString(md, ((FunctionalAdapterExecutable) node).getFunction().getClass().getName());
        } else if (node instanceof LocationFunction) {
            // 无语义载荷（返回编译期固化位置，位置已混入）
        } else if (node instanceof IfExecutable) {
            IfExecutable ifExpr = (IfExecutable) node;
            mix(md, ifExpr.getTest());
            mix(md, ifExpr.getConsequent());
            mix(md, ifExpr.getAlternate());
        } else if (node instanceof SwitchExecutable) {
            SwitchExecutable sw = (SwitchExecutable) node;
            updateBoolean(md, sw.isAsExpr());
            mix(md, sw.getDiscriminant());
            mixAll(md, sw.getTests());
            mixAll(md, sw.getConsequences());
            for (boolean fallthrough : sw.getFallthroughs())
                updateBoolean(md, fallthrough);
            mix(md, sw.getDefaultCase());
        } else if (node instanceof ForExecutable) {
            ForExecutable forExpr = (ForExecutable) node;
            mix(md, forExpr.getInitExpr());
            mix(md, forExpr.getTestExpr());
            mix(md, forExpr.getUpdateExpr());
            mix(md, forExpr.getBodyExpr());
        } else if (node instanceof ForInExecutable) {
            ForInExecutable forIn = (ForInExecutable) node;
            updateInt(md, forIn.getVarSlot());
            mix(md, forIn.getItemsExpr());
            mix(md, forIn.getBodyExpr());
        } else if (node instanceof ForOfExecutable) {
            ForOfExecutable forOf = (ForOfExecutable) node;
            updateInt(md, forOf.getVarSlot());
            updateInt(md, forOf.getIndexSlot());
            updateBoolean(md, forOf.isUseRef());
            mix(md, forOf.getItemsExpr());
            mix(md, forOf.getBodyExpr());
        } else if (node instanceof WhileExecutable) {
            WhileExecutable whileExpr = (WhileExecutable) node;
            mix(md, whileExpr.getTestExpr());
            mix(md, whileExpr.getBodyExpr());
        } else if (node instanceof DoWhileExecutable) {
            DoWhileExecutable doWhile = (DoWhileExecutable) node;
            mix(md, doWhile.getTestExpr());
            mix(md, doWhile.getBodyExpr());
        } else if (node instanceof BreakExecutable || node instanceof ContinueExecutable) {
            // 无语义载荷（三值异常族载体节点）
        } else if (node instanceof ReturnExecutable) {
            mix(md, ((ReturnExecutable) node).getExpr());
        } else if (node instanceof TryExecutable) {
            TryExecutable tryExpr = (TryExecutable) node;
            updateInt(md, tryExpr.getExceptionSlot());
            mix(md, tryExpr.getBodyExpr());
            mix(md, tryExpr.getCatchExpr());
            mix(md, tryExpr.getFinallyExpr());
        } else if (node instanceof ThrowErrorCodeExecutable) {
            ThrowErrorCodeExecutable thr = (ThrowErrorCodeExecutable) node;
            mix(md, thr.getErrorExpr());
            mix(md, thr.getParamsExpr());
        } else if (node instanceof ThrowExceptionExecutable) {
            mix(md, ((ThrowExceptionExecutable) node).getExpr());
        } else if (node instanceof OutputTextExecutable) {
            updateString(md, ((OutputTextExecutable) node).getText());
        } else if (node instanceof OutputValueExecutable) {
            mix(md, ((OutputValueExecutable) node).getValueExpr());
        } else if (node instanceof OutputXmlAttrExecutable) {
            OutputXmlAttrExecutable attr = (OutputXmlAttrExecutable) node;
            updateString(md, attr.getName());
            mix(md, attr.getValueExpr());
        } else if (node instanceof OutputXmlExtAttrsExecutable) {
            OutputXmlExtAttrsExecutable extAttrs = (OutputXmlExtAttrsExecutable) node;
            Set<String> excludeNames = extAttrs.getExcludeNames();
            if (excludeNames == null) {
                md.update((byte) 'N');
            } else {
                md.update((byte) 'S');
                updateInt(md, excludeNames.size());
                for (String name : excludeNames)
                    updateString(md, name);
            }
            mix(md, extAttrs.getAttrsExpr());
        } else if (node instanceof EscapeOutputExecutable) {
            EscapeOutputExecutable escape = (EscapeOutputExecutable) node;
            updateString(md, escape.getEscapeMode().name());
            mix(md, escape.getValueExpr());
        } else if (node instanceof GenXJsonExecutable) {
            mix(md, ((GenXJsonExecutable) node).getExecutable());
        } else if (node instanceof CollectTextExecutable
                || node instanceof CollectJsonExecutable
                || node instanceof CollectSqlExecutable) {
            mix(md, node instanceof CollectTextExecutable
                    ? ((CollectTextExecutable) node).getBodyExpr()
                    : node instanceof CollectJsonExecutable
                    ? ((CollectJsonExecutable) node).getBodyExpr()
                    : ((CollectSqlExecutable) node).getBodyExpr());
        } else if (node instanceof CollectNodeExecutable) {
            CollectNodeExecutable collect = (CollectNodeExecutable) node;
            updateBoolean(md, collect.isSingleNode());
            mix(md, collect.getBodyExpr());
        } else if (node instanceof GenNodeExecutable) {
            GenNodeExecutable gen = (GenNodeExecutable) node;
            updateString(md, gen.getTagName());
            mix(md, gen.getTagNameExpr());
            for (GenNodeAttrExecutable attr : gen.getAttrExprs()) {
                updateString(md, attr.getName());
                mix(md, attr.getValueExpr());
            }
            mix(md, gen.getExtAttrs());
            mix(md, gen.getBodyExpr());
        } else if (ExecToJavaTranslator.isNodeClassSupported(node.getClass())) {
            // 载荷覆盖硬保证：可翻译节点类必须有显式白名单分支，禁止静默弱哈希兜底
            throw new IllegalStateException(
                    "tree fingerprint payload coverage missing for translatable node: "
                            + node.getClass().getName());
        } else {
            // 不可翻译节点（转译必然 fail-fast，不进清单）：弱哈希兜底仅保留类名 + 位置
        }
    }

    private static void mixAssign(MessageDigest md, AssignIdentifier id) {
        updateInt(md, id.getVarSlot());
        updateString(md, id.getVarName());
        updateBoolean(md, id.isUseRef());
        if (id.getInitializer() != null)
            mix(md, id.getInitializer());
    }

    /**
     * ExecutableFunction 载荷全量混合：slotNames/参数规格/缺省值/函数体。
     * null 载荷（LazyCompiled 不可解析形态——转译必然 fail-fast，不进清单）跳过。
     */
    private static void mixExecutableFunction(MessageDigest md, ExecutableFunction fn) {
        if (fn == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'F');
        updateString(md, fn.getFuncName());
        updateInt(md, fn.getArgCount());
        updateInt(md, fn.getDemandArgCount());
        updateStringArray(md, fn.getSlotNames());
        Object[] defaults = fn.getDefaultArgValues();
        if (defaults == null) {
            md.update((byte) 'N');
        } else {
            md.update((byte) 'A');
            updateInt(md, defaults.length);
            for (Object value : defaults)
                mixValue(md, value);
        }
        mix(md, fn.getBody());
    }

    private static ExecutableFunction compiledOrNull(LazyCompiledExecutableFunction lazy) {
        try {
            return lazy.getCompiled();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 标量/常量载荷：值类型名 + 值字符串（防 1 vs "1" 拼接歧义，类型标签定界） */
    private static void mixValue(MessageDigest md, Object value) {
        if (value == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'V');
        updateString(md, value.getClass().getName());
        updateString(md, String.valueOf(value));
    }

    private static void mixAll(MessageDigest md, IExecutableExpression[] nodes) {
        if (nodes == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'A');
        updateInt(md, nodes.length);
        for (IExecutableExpression node : nodes) {
            mix(md, node);
        }
    }

    private static void updateStringArray(MessageDigest md, String[] values) {
        if (values == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'A');
        updateInt(md, values.length);
        for (String value : values)
            updateString(md, value);
    }

    private static void updateIntArray(MessageDigest md, int[] values) {
        if (values == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'A');
        updateInt(md, values.length);
        for (int value : values)
            updateInt(md, value);
    }

    private static void updateString(MessageDigest md, String value) {
        if (value == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'S');
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(md, bytes.length);
        md.update(bytes);
    }

    private static void updateInt(MessageDigest md, int value) {
        md.update((byte) 'I');
        md.update((byte) (value >>> 24));
        md.update((byte) (value >>> 16));
        md.update((byte) (value >>> 8));
        md.update((byte) value);
    }

    private static void updateBoolean(MessageDigest md, boolean value) {
        md.update((byte) 'B');
        md.update(value ? (byte) 1 : (byte) 0);
    }

    private static void updateLocation(MessageDigest md, SourceLocation loc) {
        if (loc == null) {
            md.update((byte) 'N');
            return;
        }
        md.update((byte) 'P');
        updateString(md, loc.getPath());
        updateInt(md, loc.getLine());
        updateInt(md, loc.getCol());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX[(b >> 4) & 0xF]);
            sb.append(HEX[b & 0xF]);
        }
        return sb.toString();
    }
}
