package io.nop.xlang.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.truffle.translate.SyntheticSources;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.truffle.eval.EvalHandoff;
import io.nop.xlang.truffle.frame.FrameLayout;
import io.nop.xlang.truffle.lang.XLangContext;
import io.nop.xlang.truffle.lang.XLangLanguage;

/**
 * XLang 编译单元根节点：每编译单元一个（= JIT 编译粒度，经 {@link #getCallTarget()} 获取
 * CallTarget）。
 *
 * <p>求值窗口协议（设计 truffle 02 §三/§五租借协议）：宿主侧 facade（或池租借 Lease）
 * 在同线程注册求值现场（scope + 输出缓冲）后经 {@code Context.eval} 触发执行；根节点入口
 * 将现场绑定到语言 context（节点经 {@link XLangContext} 读取），出口清空。context 内状态
 * 只在求值窗口内有效；SHARED 形态下并发由多 Context 承担（同一翻译 AST 可被多 Context
 * 并发执行——节点不持 context 数据或运行时值的 context-independent 准则由翻译纪律保证，
 * 池归还侧另做残留检测，plan I8）。
 */
public final class XLangRootNode extends RootNode {

    /**
     * 引擎可见的求值完成标记：guest 语言顶层不得向引擎返回 Java null（引擎
     * {@code PolyglotLanguageContext.asValue} 对 null receiver 断言失败——null 须映射为
     * 语言级 null 对象）。真实返回值经求值 handoff 同线程原样交接（类型保真），引擎侧
     * 返回值仅作完成信号，宿主侧 facade 不消费。
     */
    static final String COMPLETION_MARKER = "xl:eval-completed";

    private final String sourceKey;

    private final long treeFingerprint;

    private final IExecutableExpression sourceTree;

    private final FrameLayout frameLayout;

    private final XExprNode body;

    private final XLangLanguage language;

    private SourceSection rootSection;

    public XLangRootNode(XLangLanguage language, FrameLayout frameLayout, String sourceKey,
                         long treeFingerprint, IExecutableExpression sourceTree, XExprNode body) {
        super(language, frameLayout.getDescriptor());
        this.language = language;
        this.frameLayout = frameLayout;
        this.sourceKey = sourceKey;
        this.treeFingerprint = treeFingerprint;
        this.sourceTree = sourceTree;
        this.body = body;
    }

    /**
     * 本翻译产物的语言实例（SHARED + 共享 Engine 下跨 Context 单份；运行时/测试侧经翻译产物
     * 取语言实例的入口——翻译失败观测注册与 SHARED 激活断言的取用通道，plan I8）。
     */
    public XLangLanguage getXLangLanguage() {
        return language;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public long getTreeFingerprint() {
        return treeFingerprint;
    }

    /**
     * 翻译源树（翻译缓存键与身份断言的关联证据）。
     */
    public IExecutableExpression getSourceTree() {
        return sourceTree;
    }

    public FrameLayout getFrameLayout() {
        return frameLayout;
    }

    public XExprNode getBody() {
        return body;
    }

    @Override
    public String getName() {
        return "xl:" + sourceKey;
    }

    /**
     * 根节点 SourceSection（入口位置合成）：语言异常（XLangTruffleException）携带的定位。
     */
    @Override
    public SourceSection getSourceSection() {
        SourceLocation loc = sourceTree.getLocation();
        if (loc == null)
            return null;
        if (rootSection == null)
            rootSection = SyntheticSources.sectionOf(loc);
        return rootSection;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        EvalHandoff.Pending pending = EvalHandoff.requireActive(sourceKey);
        XLangContext ctx = XLangLanguage.currentContext();
        IEvalScope scope = pending.getScope();
        IEvalOutput output = pending.getOutput();
        ctx.bindEvaluation(scope, output);
        try {
            Object result = body.execute(frame);
            pending.captureReturned(result);
            return COMPLETION_MARKER;
        } catch (XLReturnException e) {
            // 根边界（plan I7 Phase 1 §1）：XLReturn → 取值返回（= 解释器 return 值经调用链回传）
            pending.captureReturned(e.getValue());
            return COMPLETION_MARKER;
        } catch (XLBreakException | XLContinueException e) {
            // 根边界清零吞没（= 解释器 root CallFunc finally setExitMode(null)，调用结果 null）
            pending.captureReturned(null);
            return COMPLETION_MARKER;
        } catch (ControlFlowException e) {
            throw e;
        } catch (Exception e) {
            pending.captureThrown(e);
            throw new XLangTruffleException(e, this);
        } finally {
            ctx.clearEvaluation();
        }
    }
}
