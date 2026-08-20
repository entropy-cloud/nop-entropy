package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.reflect.IClassModel;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 对象构造节点（NewObjectExecutable 直译）：实参按序求值后经共享 helper 选择构造器创建
 * （复用编译期 classModel，零查找——解释器入口同一实现）。
 */
public final class XNewObjectNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final IClassModel classModel;

    private final XExprNode[] args;

    public XNewObjectNode(SourceLocation loc, String display, IClassModel classModel, XExprNode[] args) {
        this.loc = loc;
        this.display = display;
        this.classModel = classModel;
        this.args = args;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] argValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            argValues[i] = args[i].execute(frame);
        }
        return XLangSemantics.newInstance(loc, display, classModel, argValues,
                XLangLanguage.currentContext().requireEvalScope());
    }
}
