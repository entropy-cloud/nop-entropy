package io.nop.xlang.truffle.translate;

import com.oracle.truffle.api.RootCallTarget;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.nodes.XLangRootNode;

/**
 * 翻译产物：一个编译单元的 Truffle AST 根 + CallTarget（= JIT 编译粒度）。
 */
public final class TranslatedUnit {

    private final String sourceKey;

    private final long treeFingerprint;

    private final IExecutableExpression sourceTree;

    private final XLangRootNode rootNode;

    private final RootCallTarget callTarget;

    TranslatedUnit(String sourceKey, long treeFingerprint, IExecutableExpression sourceTree,
                   XLangRootNode rootNode, RootCallTarget callTarget) {
        this.sourceKey = sourceKey;
        this.treeFingerprint = treeFingerprint;
        this.sourceTree = sourceTree;
        this.rootNode = rootNode;
        this.callTarget = callTarget;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public long getTreeFingerprint() {
        return treeFingerprint;
    }

    public IExecutableExpression getSourceTree() {
        return sourceTree;
    }

    public XLangRootNode getRootNode() {
        return rootNode;
    }

    public RootCallTarget getCallTarget() {
        return callTarget;
    }

    public FrameLayout getFrameLayout() {
        return rootNode.getFrameLayout();
    }
}
