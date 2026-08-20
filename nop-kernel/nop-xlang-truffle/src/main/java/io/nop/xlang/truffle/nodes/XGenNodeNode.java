package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 节点生成节点（GenNodeExecutable + GenNodeAttrExecutable 宿主载体直译，plan I7 Phase 1 §2）：
 * 经共享 helper {@link XLangSemantics#genNode}（DisabledEvalOutput → CollectXNodeHandler 收集
 * 返回 XNode / 否则 {@code (IXNodeHandler)} cast 直发——cast quirk 保真，CCE 原样传播）；
 * attr/tagName 求值在换缓冲后（live 求值顺序：attrs → tagName → body，与解释器一致）；
 * body 经 {@link XLangSemantics#genNodeHandler} 的 Runnable 回调换缓冲执行；
 * pending exit 分派同 {@link XOutputSwapNode}（词法循环嵌套静态标志）。
 */
public final class XGenNodeNode extends XExprNode {

    private final SourceLocation loc;

    private final String display;

    private final String tagName;

    private final IExecutableExpression tagNameExpr;

    private final String[] attrNames;

    private final SourceLocation[] attrValueLocs;

    private final XExprNode[] attrValues;

    private final XExprNode tagNameValueExpr;

    private final XExprNode extAttrs;

    private final SourceLocation extAttrsLoc;

    private final Set<String> attrNamesSet;

    private final boolean inLoop;

    private final XExprNode body;

    public XGenNodeNode(SourceLocation loc, String display, String tagName, IExecutableExpression tagNameExpr,
                        String[] attrNames, SourceLocation[] attrValueLocs, XExprNode[] attrValues,
                        XExprNode tagNameValueExpr, XExprNode extAttrs, SourceLocation extAttrsLoc,
                        Set<String> attrNamesSet, boolean inLoop, XExprNode body) {
        this.loc = loc;
        this.display = display;
        this.tagName = tagName;
        this.tagNameExpr = tagNameExpr;
        this.attrNames = attrNames;
        this.attrValueLocs = attrValueLocs;
        this.attrValues = attrValues;
        this.tagNameValueExpr = tagNameValueExpr;
        this.extAttrs = extAttrs;
        this.extAttrsLoc = extAttrsLoc;
        this.attrNamesSet = attrNamesSet;
        this.inLoop = inLoop;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        XLangContext context = XLangLanguage.currentContext();
        ExitMode[] exit = new ExitMode[1];
        Object value = XLangSemantics.genNode(context.requireOutput(), exit, (handler, $exit, $frame) -> {
            // 换缓冲（IXNodeHandler extends IEvalOutput）：attrs → tagName → body 与解释器次序一致
            IEvalOutput old = context.swapOutput(handler);
            try {
                Map<String, ValueWithLocation> attrs = buildAttrs(frame);
                String name = buildTagName(frame);
                XLangSemantics.genNodeHandler(handler, loc, name, attrs,
                        body == null ? null : () -> {
                            try {
                                body.execute(frame);
                            } catch (XLControlFlowException cf) {
                                $exit[0] = cf.getExitMode();
                            }
                        });
            } finally {
                context.restoreOutput(old);
            }
        }, null);
        return XLControlFlowException.dispatchPendingExit(exit, value, inLoop);
    }

    private String buildTagName(VirtualFrame frame) {
        Object tagNameValue = tagName != null ? tagName : tagNameValueExpr.execute(frame);
        return XLangSemantics.genNodeTagName(loc, tagName, tagNameValue, tagNameExpr, display);
    }

    private Map<String, ValueWithLocation> buildAttrs(VirtualFrame frame) {
        if (attrValues.length == 0 && extAttrs == null)
            return Collections.emptyMap();

        Object[] values = new Object[attrValues.length];
        for (int i = 0; i < attrValues.length; i++) {
            values[i] = attrValues[i].execute(frame);
        }
        Object extAttrsValue = extAttrs == null ? null : extAttrs.execute(frame);
        return XLangSemantics.genNodeAttrs(attrNames, attrValueLocs, values, extAttrsValue,
                extAttrsLoc, attrNamesSet);
    }
}
