package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.exec.XLangSemantics;

/**
 * 函数值调用点的两级内联缓存分派节点（plan I7 Phase 1 §3，设计 truffle 02 §六准则表；
 * VarFunctionExecutable/VarExecutableFunction 的翻译产物）：
 *
 * <ul>
 * <li><b>一级缓存 = CallTarget 身份</b>：guard {@code targetOf(function) == cachedTarget}
 *     ——逐调用从传入函数值重取 CallTarget 比较身份，<b>不缓存函数实例身份、不缓存任何
 *     运行时值身份</b>（同一函数体的两个函数值共享 CallTarget → 同一缓存条目，captured
 *     各自随调用传递，值身份不误命中）；</li>
 * <li><b>二级缓存 = 直达调用形态</b>：命中路径经 {@link DirectCallNode}（PE 可内联）；</li>
 * <li>上限与泛化：{@code limit = 3}，多态调用点超限自动转入 generic（replaces）——
 *     {@link XLangSemantics#callVarFunction} 共享 helper（与解释器/java 生成代码同一实现来源，
 *     无双实现），不无限扩容；</li>
 * <li><b>可观测缓存状态探针</b>：directCalls/genericCalls 计数器随节点暴露（接线证据——
 *     行为级对拍对缓存透明，不能证伪摆设缓存；测试经 AST 遍历读取探针断言命中/未命中/
 *     超限泛化路径）。</li>
 * </ul>
 */
public abstract class XFunctionDispatchNode extends Node {

    // ---- 可观测缓存状态探针（接线证据载体，不参与语义） ----
    private long directCalls;

    private long genericCalls;

    public long getDirectCalls() {
        return directCalls;
    }

    public long getGenericCalls() {
        return genericCalls;
    }

    static boolean isTruffleFunction(Object function) {
        return function instanceof XLangTruffleFunction;
    }

    /** L1 身份载体：函数值 → CallTarget（非 truffle 函数值 → null，由 guard 前置条件排除）。 */
    static CallTarget targetOf(Object function) {
        return function instanceof XLangTruffleFunction
                ? ((XLangTruffleFunction) function).getCallTarget() : null;
    }

    /** L2 直达调用形态工厂（DSL @Cached 静态工厂惯例：DirectCallNode 按 CallTarget 创建）。 */
    static DirectCallNode createDirectCall(CallTarget target) {
        return Truffle.getRuntime().createDirectCallNode(target);
    }

    public abstract Object executeDispatch(SourceLocation loc, String display, boolean optional,
                                           IEvalScope scope, Object function, Object[] args);

    /**
     * L1+L2 命中路径：CallTarget 身份 guard + DirectCallNode 直达调用
     * （captured 随函数值传递——同 CallTarget 不同 captured 的函数值共用条目且结果各自正确）。
     */
    @Specialization(guards = {"isTruffleFunction(function)", "targetOf(function) == cachedTarget"},
            limit = "3")
    protected Object dispatchDirect(SourceLocation loc, String display, boolean optional,
                                    IEvalScope scope, Object function, Object[] args,
                                    @Cached("targetOf(function)") CallTarget cachedTarget,
                                    @Cached("createDirectCall(cachedTarget)") DirectCallNode callNode) {
        directCalls++;
        return callNode.call(((XLangTruffleFunction) function).callArguments(args, scope));
    }

    /**
     * 泛化路径（replaces，超限/非 truffle 函数值）：共享 helper 统一调用语义
     * （null/EXPR_NOT_RETURN_FUNC/ExecutableFunction/其余 IEvalFunction 分派）。
     */
    @Specialization(replaces = "dispatchDirect")
    protected Object dispatchGeneric(SourceLocation loc, String display, boolean optional,
                                     IEvalScope scope, Object function, Object[] args) {
        genericCalls++;
        return XLangSemantics.callVarFunction(loc, display, optional, function, args, scope);
    }
}
