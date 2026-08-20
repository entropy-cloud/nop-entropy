package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangLanguage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 对象解构绑定节点（ObjectBindingAssignExecutable 直译）：源值单次求值后经共享 helper 归一为
 * Map，逐属性按序绑定（缺省属性应用默认初始化器），rest 绑定收集未消费键值对；
 * 节点返回 null（解释器语义）。
 */
public final class XObjectBindingNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final XExprNode value;

    private final String[] keys;

    private final XExprNode[] initializers;

    private final XBindingAssign[] bindings;

    private final Set<String> boundKeys;

    private final XBindingAssign rest;

    public XObjectBindingNode(SourceLocation loc, String display, XExprNode value, String[] keys,
                              XExprNode[] initializers, XBindingAssign[] bindings, Set<String> boundKeys,
                              XBindingAssign rest) {
        this.loc = loc;
        this.display = display;
        this.value = value;
        this.keys = keys;
        this.initializers = initializers;
        this.bindings = bindings;
        this.boundKeys = boundKeys;
        this.rest = rest;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        IEvalScope scope = XLangLanguage.currentContext().requireEvalScope();
        Map<String, Object> map = XLangSemantics.asMapBinding(loc, display, value.execute(frame));
        for (int i = 0; i < bindings.length; i++) {
            Object propValue = map.get(keys[i]);
            if (propValue == null && initializers[i] != null)
                propValue = initializers[i].execute(frame);
            bindings[i].assign(frame, propValue, scope);
        }
        if (rest != null) {
            Map<String, Object> tail = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!boundKeys.contains(entry.getKey()))
                    tail.put(entry.getKey(), entry.getValue());
            }
            rest.assign(frame, tail, scope);
        }
        return null;
    }
}
