package io.nop.xlang.truffle.lang;

import com.oracle.truffle.api.TruffleLanguage;
import io.nop.core.lang.eval.IEvalOutput;
import io.nop.core.lang.eval.IEvalScope;

/**
 * XLang 语言上下文（设计 truffle 02 §三）：每次 Context 持有输出缓冲（{@link IEvalOutput}，
 * 线程绑定，绝不可跨 Context 共享）与本次求值的全局作用域句柄；二者只在根节点求值窗口
 * （bind..clear）内有效。可共享数据（翻译缓存等）存语言实例，节点不存 context 数据或
 * 运行时值（context-independent 准则）。
 */
public final class XLangContext {

    private final TruffleLanguage.Env env;

    private IEvalScope evalScope;

    private IEvalOutput output;

    XLangContext(TruffleLanguage.Env env) {
        this.env = env;
    }

    public TruffleLanguage.Env getEnv() {
        return env;
    }

    /**
     * 求值窗口协议（根节点入口绑定/出口清空；池租借协议的基座，plan I8 复用裁定——
     * 注入链路保持 handoff → 根节点绑定，池侧归还补残留检测 + 防御清空）。
     */
    public void bindEvaluation(IEvalScope evalScope, IEvalOutput output) {
        this.evalScope = evalScope;
        this.output = output;
    }

    public void clearEvaluation() {
        this.evalScope = null;
        this.output = null;
    }

    /**
     * 归还残留检测（plan I8 池租借协议）：求值窗口协议应已在根节点 finally 清空；
     * evalScope/output 任一非 null 即残留（借用方泄漏求值状态的协议违约信号，池归还时
     * 据此红灯并退役该 Context）。
     */
    public boolean hasEvaluationResidue() {
        return evalScope != null || output != null;
    }

    /**
     * 本次求值的作用域句柄；求值窗口外访问为接线缺陷，fail-fast 而非静默返回 null。
     */
    public IEvalScope requireEvalScope() {
        IEvalScope scope = evalScope;
        if (scope == null)
            throw new IllegalStateException("XLangContext eval scope is not bound (outside evaluation window)");
        return scope;
    }

    /**
     * 本次求值的输出缓冲（线程绑定）。
     */
    public IEvalOutput requireOutput() {
        IEvalOutput out = output;
        if (out == null)
            throw new IllegalStateException("XLangContext output buffer is not bound (outside evaluation window)");
        return out;
    }

    /**
     * 换缓冲协议（plan I7 Phase 1 §2）：context 持有输出缓冲的线程绑定 swap/restore——
     * Collect 族与 Gen 族运行期换缓冲的 truffle 承载（对应解释器 {@code rt.setOut} 换/恢复）。
     * 求值窗口内（租借期内单线程串行）调用方以栈式 try/finally 配对使用；返回换出的旧缓冲供恢复。
     */
    public IEvalOutput swapOutput(IEvalOutput newOutput) {
        if (newOutput == null)
            throw new IllegalStateException("swapOutput requires a non-null output buffer");
        IEvalOutput old = this.output;
        this.output = newOutput;
        return old;
    }

    /**
     * 换缓冲恢复（与 {@link #swapOutput} 配对；异常路径经 finally 不丢恢复由调用方保证）。
     */
    public void restoreOutput(IEvalOutput previous) {
        this.output = previous;
    }
}
