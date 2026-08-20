package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 构造器属性读取节点（MakePropertyExecutable 直译）：经共享 maker helper 解析并读取
 * （null 接收者抛 ERR_EXEC_MAKE_PROP_OBJ_NULL，与解释器同源）。
 */
public final class XMakePropertyNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String objDisplay;

    private final String propName;

    private final XExprNode obj;

    public XMakePropertyNode(SourceLocation loc, String display, String objDisplay, String propName,
                             XExprNode obj) {
        this.loc = loc;
        this.display = display;
        this.objDisplay = objDisplay;
        this.propName = propName;
        this.obj = obj;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return XLangSemantics.makeProperty(loc, display, objDisplay, propName,
                obj.execute(frame), XLangLanguage.currentContext().requireEvalScope());
    }
}
