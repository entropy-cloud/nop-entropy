package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.utils.DebugHelper;

/**
 * 调试输出节点（DebugExecutable 直译）：值与前缀按序各单次求值（顺序与解释器一致），
 * 经同一 {@link DebugHelper#v} 记录调试日志（副作用不在对拍断言域），返回原值。
 */
public final class XDebugNode extends XExprNode {

    private final SourceLocation loc;

    private final String valueDisplay;

    private final XExprNode value;

    private final XExprNode prefix;

    public XDebugNode(SourceLocation loc, String valueDisplay, XExprNode value, XExprNode prefix) {
        this.loc = loc;
        this.valueDisplay = valueDisplay;
        this.value = value;
        this.prefix = prefix;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object v = value.execute(frame);
        Object prefixValue = prefix.execute(frame);
        DebugHelper.v(loc, ConvertHelper.toString(prefixValue), valueDisplay, v);
        return v;
    }
}
