package io.nop.xlang.truffle.compare;

import com.oracle.truffle.api.RootCallTarget;
import io.nop.xlang.compare.BackendExecRequest;
import io.nop.xlang.compare.BackendExecutionEvidence;
import io.nop.xlang.compare.BackendExecutionResult;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.CompareUnit;
import io.nop.xlang.compare.CompareUnitKind;
import io.nop.xlang.compare.IEvalBackendColumn;
import io.nop.xlang.truffle.eval.XLangTruffleEval;
import io.nop.xlang.truffle.nodes.XLangRootNode;

/**
 * 对拍矩阵 truffle 列（I5 落地，I1 harness 的真实后端列；测试域 driver，生产路由归 I9）。
 *
 * <p>执行通路：以请求中的<b>同一棵 Executable 树实例</b>为翻译源（对拍对象是树，差异只可能
 * 来自后端），经 {@link XLangTruffleEval} 宿主侧 facade 驱动 polyglot Context.eval ——
 * XLangLanguage.parse 查翻译缓存返回 CallTarget，引擎执行翻译 AST，根节点在求值窗口内绑定
 * 本列独占的求值现场（scope + 输出缓冲）。静态与动态单元均适用（动态单元 java 列为
 * "不适用"，I1 列适用性机制）。
 *
 * <p>证据契约：executedArtifact = 翻译产物 {@link XLangRootNode}（其 sourceTree 即请求树实例，
 * 身份断言依据，由 harness 经 {@link TruffleBackendIdentityRule} 依据证据判定，列不自证）；
 * executorArtifact = 该根节点的 {@link RootCallTarget}（JIT 编译粒度，返回值经求值 handoff
 * 同线程原样交接——polyglot Value 转换会丢失精确 Java 类型）。
 *
 * <p>sourceKey 口径（plan I5 决策 D2）：静态单元 = sourceLocationPath（resourcePath 键）；
 * 动态单元 = 源内容哈希键（{@link XLangTruffleEval#dynamicSourceKey}）。
 */
public final class TruffleBackendColumn implements IEvalBackendColumn, AutoCloseable {

    private final XLangTruffleEval eval;

    private TruffleBackendColumn(XLangTruffleEval eval) {
        this.eval = eval;
    }

    /**
     * 打开一个 truffle 列（单 polyglot Context 串行求值——SHARED 形态下的非共享 Engine
     * 用法，每 Context 独立语言实例；多线程池化并发对拍走
     * {@code io.nop.xlang.truffle.runtime.XLangContextPool}（TestCorpusConcurrentTruffleColumn），
     * 调用方负责 close）。
     */
    public static TruffleBackendColumn open() {
        return new TruffleBackendColumn(new XLangTruffleEval());
    }

    @Override
    public String getBackendId() {
        return CompareBackendIds.TRUFFLE;
    }

    @Override
    public boolean isSupportsStaticUnits() {
        return true;
    }

    @Override
    public boolean isSupportsDynamicUnits() {
        return true;
    }

    @Override
    public BackendExecutionResult execute(BackendExecRequest request) {
        CompareUnit unit = request.getUnit();
        String sourceKey = unit.getKind() == CompareUnitKind.STATIC
                ? unit.getSourceLocationPath()
                : XLangTruffleEval.dynamicSourceKey(unit.getSource());

        XLangTruffleEval.TranslatedEval result = eval.eval(sourceKey, request.getExpr(),
                request.getScope(), request.getOut());
        BackendExecutionEvidence evidence = new BackendExecutionEvidence(
                result.getUnit().getRootNode(), result.getUnit().getCallTarget());
        if (result.getThrown() != null)
            return BackendExecutionResult.error(result.getThrown(), evidence);
        return BackendExecutionResult.value(result.getReturnValue(), evidence);
    }

    @Override
    public void close() {
        eval.close();
    }
}
