package io.nop.xlang.truffle.compare;

import com.oracle.truffle.api.RootCallTarget;
import io.nop.xlang.compare.BackendExecRequest;
import io.nop.xlang.compare.BackendExecutionEvidence;
import io.nop.xlang.compare.CompareBackendIds;
import io.nop.xlang.compare.IBackendIdentityRule;
import io.nop.xlang.truffle.nodes.XLangRootNode;

/**
 * truffle 列强身份规则：executedArtifact 必须是本单元请求树确定性翻译产物的
 * {@link XLangRootNode}（sourceTree = 请求中的同一棵树实例，非解释器树自身、非伪装）；
 * executorArtifact 必须是该根节点派生的 {@link RootCallTarget}（返回值经 CallTarget 执行
 * 交接，身份断言 = 翻译 AST 真实经 CallTarget 执行，非解释器兜底）。
 */
public final class TruffleBackendIdentityRule implements IBackendIdentityRule {

    @Override
    public String getBackendId() {
        return CompareBackendIds.TRUFFLE;
    }

    @Override
    public void verifyIdentity(BackendExecutionEvidence evidence, BackendExecRequest request) {
        Object executed = evidence.getExecutedArtifact();
        if (!(executed instanceof XLangRootNode))
            throw new AssertionError("truffle column executed artifact must be the translated XLangRootNode, was: "
                    + executed);
        XLangRootNode root = (XLangRootNode) executed;

        if (root.getSourceTree() != request.getExpr())
            throw new AssertionError("translated root must be derived from the requested executable tree instance: "
                    + "sourceTree=" + root.getSourceTree() + ", request=" + request.getExpr());

        Object executor = evidence.getExecutorArtifact();
        if (!(executor instanceof RootCallTarget))
            throw new AssertionError("truffle column executor artifact must be the translated RootCallTarget, was: "
                    + executor);
        RootCallTarget callTarget = (RootCallTarget) executor;
        if (callTarget.getRootNode() != root)
            throw new AssertionError("truffle column call target must drive the translated root node: target root="
                    + callTarget.getRootNode() + ", evidence root=" + root);
    }
}
