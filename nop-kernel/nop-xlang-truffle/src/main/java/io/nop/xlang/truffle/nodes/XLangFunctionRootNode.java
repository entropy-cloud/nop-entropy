package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.frame.FrameLayoutMapper;
import io.nop.xlang.truffle.lang.XLangLanguage;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_MAX_COUNT;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_MANY_ARGS;

/**
 * 被翻译函数体的 RootNode（plan I7 Phase 1 §4 闭包形态裁定：急切值拷贝——每个函数体独立
 * RootNode + 独立 FrameDescriptor，无帧物化）。调用协议：{@code CallTarget.call(args, captured)}
 * ——arguments[0] = 实参数组，arguments[1] = 捕获值快照（BuildFuncRef 捕获时拷贝 /
 * CallFuncWithClosure 调用时拷贝，由调用侧承载两拷贝时序载体差异）。
 *
 * <p>入口绑定次序与解释器一一对应：实参槽（0..n-1）→ 缺省参数槽（字面量缺省内嵌求值，
 * I4 Phase 1 §5 等价裁定消费）→ 闭包目标槽（BindVarExecutable 语义：captured 原样写入）→
 * 求值函数体。入口写入槽 kind 恒 Object（{@link FrameLayoutMapper#mapFunction} 以非字面量
 * 入口写计入）。
 *
 * <p><b>ExitMode 边界清零（I4 同一 live 锚点）</b>：本节点 = 解释器 ExecutableFunction 3 路径 /
 * CallFunc finally / CallFuncWithClosure / LazyCompiled 的 setExitMode(null) 清零点统一对应——
 * XLReturn → 边界取值返回（解释器：值经 Seq/调用链回传）；XLBreak/XLContinue → 边界吞没返回
 * null（解释器：flag 越界后由 finally 清零、调用结果为 null）。控制流异常不外泄。
 */
public final class XLangFunctionRootNode extends RootNode {

    private final String name;

    private final int argCount;

    private final XExprNode[] defaultValues;

    private final int[] targetSlots;

    private final XExprNode body;

    public XLangFunctionRootNode(XLangLanguage language, FrameLayout frameLayout, String name,
                                 int argCount, XExprNode[] defaultValues, int[] targetSlots, XExprNode body) {
        super(language, frameLayout.getDescriptor());
        this.name = name;
        this.argCount = argCount;
        this.defaultValues = defaultValues;
        this.targetSlots = targetSlots;
        this.body = body;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        Object[] args = (Object[]) arguments[0];
        Object[] captured = (Object[]) arguments[1];

        if (args.length > argCount)
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS)
                    .param(ARG_MAX_COUNT, argCount).param(ARG_ARG_COUNT, args.length);

        for (int i = 0; i < args.length; i++)
            frame.setObject(i, args[i]);
        if (defaultValues != null) {
            // 缺省参数槽内嵌字面量求值（I4 Phase 1 §5 等价裁定：前端缺省仅产生 Literal/CloneLiteral）
            for (int i = args.length; i < argCount; i++)
                frame.setObject(i, defaultValues[i - (argCount - defaultValues.length)].execute(frame));
        }
        for (int i = 0; i < targetSlots.length; i++)
            frame.setObject(targetSlots[i], captured[i]);

        try {
            return body.execute(frame);
        } catch (XLReturnException e) {
            return e.getValue();
        } catch (XLBreakException | XLContinueException e) {
            return null;
        }
    }
}
