package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangLanguage;

import java.util.List;

/**
 * 数组解构绑定节点（ArrayBindingAssignExecutable 直译）：源值单次求值后经共享 helper 归一为
 * List，逐元素按序绑定（元素不应用默认初始化器，与解释器一致），rest 绑定取尾部切片；
 * 节点返回 null（解释器语义）。
 */
public final class XArrayBindingNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final XExprNode value;

    private final XBindingAssign[] elements;

    private final XBindingAssign rest;

    public XArrayBindingNode(SourceLocation loc, String display, XExprNode value, XBindingAssign[] elements,
                             XBindingAssign rest) {
        this.loc = loc;
        this.display = display;
        this.value = value;
        this.elements = elements;
        this.rest = rest;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        IEvalScope scope = XLangLanguage.currentContext().requireEvalScope();
        List<Object> list = XLangSemantics.asListBinding(loc, display, value.execute(frame));
        for (int i = 0; i < elements.length; i++) {
            elements[i].assign(frame, list.get(i), scope);
        }
        if (rest != null)
            rest.assign(frame, CollectionHelper.copyTail(list, elements.length), scope);
        return null;
    }
}
