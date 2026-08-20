package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.ExitMode;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.exec.XLangSemantics;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * 换缓冲类调用节点（CollectText/CollectJson/CollectNode/CollectSql/GenXJson 直译，
 * plan I7 Phase 1 §2 形态）：共享 helper（I4 提取的换缓冲族——save/body/collect 同一实现
 * 来源）+ IGeneratedOutBody 回调接入——回调内 {@link XLangContext} 线程绑定 swap/restore
 * （对应解释器 rt.setOut 换/恢复）+ 控制流异常捕获抑制进 ExitMode cell（I4 调用点协议）。
 *
 * <p>helper 返回收集值后按 {@link XLControlFlowException#dispatchPendingExit} 分派：
 * cell 空 → 收集值；RETURN → XLReturn(收集值)；BREAK/CONTINUE → 词法循环内重抛由循环消费 /
 * 无循环 → XLReturn(收集值)（边界清零吞没 + 收集值为终值的合并对应）。循环内判定 =
 * 翻译期词法循环嵌套静态标志。
 */
public final class XOutputSwapNode extends XExprNode {

    /** 换缓冲 helper 变体（与 XLangSemantics 换缓冲族一一对应）。 */
    public enum Kind {
        COLLECT_TEXT, COLLECT_JSON, COLLECT_NODE, COLLECT_SQL, GEN_XJSON
    }

    private final Kind kind;

    private final SourceLocation loc;

    private final boolean singleNode;

    private final boolean inLoop;

    private final XExprNode body;

    public XOutputSwapNode(Kind kind, SourceLocation loc, boolean singleNode, boolean inLoop, XExprNode body) {
        this.kind = kind;
        this.loc = loc;
        this.singleNode = singleNode;
        this.inLoop = inLoop;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        XLangContext context = XLangLanguage.currentContext();
        IEvalScope scope = context.requireEvalScope();
        ExitMode[] exit = new ExitMode[1];
        Object value = runHelper(scope, exit, frame, context);
        return XLControlFlowException.dispatchPendingExit(exit, value, inLoop);
    }

    private Object runHelper(IEvalScope scope, ExitMode[] exit, VirtualFrame frame, XLangContext context) {
        switch (kind) {
            case COLLECT_TEXT:
                return XLangSemantics.collectText(scope, exit, swapBody(context, frame, exit), null);
            case COLLECT_JSON:
                return XLangSemantics.collectJson(scope, exit, swapBody(context, frame, exit), null);
            case COLLECT_NODE:
                return XLangSemantics.collectNode(scope, loc, singleNode, exit, swapBody(context, frame, exit), null);
            case COLLECT_SQL:
                return XLangSemantics.collectSql(scope, exit, swapBody(context, frame, exit), null);
            case GEN_XJSON:
            default:
                return XLangSemantics.genXjson(scope, exit, swapBody(context, frame, exit), null);
        }
    }

    /**
     * 换缓冲回调：context swap/restore（异常路径不丢恢复 = 双层 finally）+ 控制流异常
     * → ExitMode cell 抑制（body 异常穿透时 restore 先行，helper 侧无恢复逻辑依赖）。
     */
    private XLangSemantics.IGeneratedOutBody swapBody(XLangContext context, VirtualFrame frame, ExitMode[] exit) {
        return ($scope, $out, $exit, $frame) -> {
            IEvalOutput old = context.swapOutput($out);
            try {
                body.execute(frame);
            } catch (XLControlFlowException cf) {
                $exit[0] = cf.getExitMode();
            } finally {
                context.restoreOutput(old);
            }
            return null;
        };
    }
}
