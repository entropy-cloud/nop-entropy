package io.nop.lint.core.xscript;

import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.lint.core.NopLintException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Route A deadline wrapper (design 07 §4): the global
 * {@link IExpressionExecutor} delegate installed through the public
 * {@link EvalExprProvider#registerGlobalExecutor(IExpressionExecutor)}
 * extension point — zero platform change. Because every XLang executable
 * node (including each {@code while} loop back-edge and lambda body) executes
 * through the executor handed down from the evaluation root, installing this
 * wrapper yields a per-node interception point for lint scripts without any
 * compile-time instrumentation.
 *
 * <p>Semantics (design 07 §4, plan item 15):</p>
 * <ul>
 * <li><b>no deadline in scope</b> — verbatim passthrough to the executor
 * captured at install time: non-lint XLang evaluations keep their exact
 * pre-wrapper behavior, including exception semantics</li>
 * <li><b>deadline expired</b> — {@link XScriptTimeoutException}, aborting the
 * whole match (the runner treats it as a non-matching, separately counted
 * timeout)</li>
 * <li><b>evaluation depth</b> — nested executor entries within a deadline
 * scope are counted per thread; beyond {@value #MAX_EVAL_DEPTH} the script
 * aborts with an explicit failure (runaway recursion guard, design 07 §3).
 * Non-lint evaluations are never depth-limited.</li>
 * </ul>
 *
 * <p>Installation is idempotent: {@link #install()} wraps whatever executor
 * is current at call time (never hardwires
 * {@code DefaultExpressionExecutor}), so it composes with other wrappers
 * (e.g. the debugger executor) instead of replacing them. The visible-chain
 * scan treats an already-installed wrapper as installed, so repeated and
 * nested installs never stack a second wrapper.</p>
 *
 * <p>Ownership (plan 2531 adjudication 2): every install takes one
 * ownership generation and {@link #uninstall()} releases it. The last
 * release of a generation restores the exact slot value its wrapper had
 * replaced — a lint run owns the global slot only for its duration, and no
 * wrapper residue survives it. When the slot top is no longer confirmably
 * ours (a foreign writer such as a debugger attach moved in), the slot is
 * left untouched and the residual is logged loudly — never silently kept,
 * never silently stripped.</p>
 */
public final class LintDeadlineExecutor implements IExpressionExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(LintDeadlineExecutor.class);

    /**
     * The maximum nesting depth of executor entries allowed inside one lint
     * script execution (design 07 §3 "调用深度上限 32").
     */
    public static final int MAX_EVAL_DEPTH = 32;

    private static final ThreadLocal<int[]> EVAL_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    /**
     * The install-ownership ledger (plan 2531 adjudication 2): the number of
     * live install generations and the wrapper instance the current
     * generation created. Both are touched only under the class monitor —
     * every {@code install}/{@code uninstall} is {@code static synchronized},
     * so the lint-vs-lint lost-update face is closed by construction.
     */
    private static int installCount;
    private static LintDeadlineExecutor activeWrapper;

    private final IExpressionExecutor delegate;

    LintDeadlineExecutor(IExpressionExecutor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * The executor this wrapper captured at install time and defers to —
     * also the pre-install snapshot its release restores.
     */
    public IExpressionExecutor delegate() {
        return delegate;
    }

    /**
     * Idempotently installs the wrapper as the global executor, delegating to
     * whatever executor is current at installation time. Safe to call before
     * every lint run: a second call observes the wrapper already installed
     * and does nothing (no wrapper stacking). Each call takes one ownership
     * generation — pair it with {@link #uninstall()} when the run ends.
     */
    public static synchronized void install() {
        IExpressionExecutor current = EvalExprProvider.getGlobalExecutor();
        if (current instanceof LintDeadlineExecutor) {
            // The visible chain already intercepts through a lint deadline
            // wrapper: idempotent, no stacking — only the ownership count
            // moves (plan 2531 adjudication 2, chain-scan form).
            installCount++;
            return;
        }
        installCount++;
        activeWrapper = new LintDeadlineExecutor(current);
        EvalExprProvider.registerGlobalExecutor(activeWrapper);
    }

    /**
     * Releases one install generation. The last release of a generation
     * restores the slot value the generation's wrapper had replaced (its
     * install-time snapshot) — under the same class monitor that guarded the
     * install, so the swap is atomic against other lint runs. When the slot
     * top is no longer confirmably ours (a foreign writer such as a
     * debugger attach replaced or wrapped it), the foreign owner is left in
     * place — restoring our snapshot would clobber it — and the residual is
     * logged loudly.
     *
     * @throws NopLintException on an unpaired call (no install is live) — a
     *                          caller bug must not pass silently
     */
    public static synchronized void uninstall() {
        if (installCount == 0) {
            throw new NopLintException(
                    "unpaired lint deadline executor uninstall: no install generation is live");
        }
        installCount--;
        if (installCount > 0) {
            return;
        }
        LintDeadlineExecutor wrapper = activeWrapper;
        activeWrapper = null;
        if (wrapper == null) {
            // this generation never wrapped (it found a wrapper already on
            // the slot from a lost generation): that residual is not ours
            LOG.warn("nop.lint.deadline-executor-residual:reason=stale-wrapper-not-owned,top={}",
                    EvalExprProvider.getGlobalExecutor());
            return;
        }
        IExpressionExecutor top = EvalExprProvider.getGlobalExecutor();
        if (top == wrapper) {
            // branch 1: this generation's wrapper owns the slot top — swap
            // back exactly what it replaced (the install-time snapshot)
            EvalExprProvider.registerGlobalExecutor(wrapper.delegate());
            return;
        }
        if (visibleChainContains(wrapper, top)) {
            // branch 2: this generation's wrapper is still visible on the
            // chain, but a foreign lint-typed layer owns the slot top
            LOG.warn("nop.lint.deadline-executor-residual:reason=visible-chain-top-foreign,top={}", top);
            return;
        }
        // branch 3: the slot top is foreign (e.g. a debugger attach replaced
        // it wholesale): its underneath is unconfirmable — leave the foreign
        // owner in place and drop the snapshot by declaration
        LOG.warn("nop.lint.deadline-executor-residual:reason=slot-top-foreign,top={}", top);
    }

    /**
     * The visible-chain scan (plan 2531 adjudication 2, R1 Major-2
     * narrowing): the only transparent edge is a lint deadline wrapper's own
     * {@link #delegate()} accessor, so the scan descends through wrapper
     * layers only — an opaque foreign layer (the real debugger executor
     * replaces the slot wholesale and wraps nothing) terminates the scan,
     * and everything underneath it is unconfirmable by design.
     */
    private static boolean visibleChainContains(LintDeadlineExecutor target, IExpressionExecutor top) {
        IExpressionExecutor current = top;
        while (current instanceof LintDeadlineExecutor wrapper) {
            if (wrapper == target) {
                return true;
            }
            current = wrapper.delegate();
        }
        return false;
    }

    @Override
    public Object execute(IExecutableExpression expr, EvalRuntime rt) {
        IEvalScope scope = rt.getScope();
        Object deadline = scope.getValue(XScriptDeadline.VAR_DEADLINE_NANOS);
        if (deadline == null) {
            // No lint deadline in scope: verbatim passthrough. The delegate
            // threads itself down the expression tree exactly as the
            // pre-wrapper executor would, so non-lint evaluations keep their
            // exact semantics and are never depth-counted.
            return delegate.execute(expr, rt);
        }
        if (System.nanoTime() > ((Number) deadline).longValue()) {
            throw new XScriptTimeoutException(ruleIdOf(scope), budgetMsOf(scope));
        }
        int[] depth = EVAL_DEPTH.get();
        if (depth[0] >= MAX_EVAL_DEPTH) {
            throw new NopLintException("Rule '" + ruleIdOf(scope)
                    + "' xscript evaluation depth exceeded the limit of " + MAX_EVAL_DEPTH
                    + " nested executor calls (runaway recursion or expression nesting; fail-closed abort)");
        }
        depth[0]++;
        try {
            // Thread THIS wrapper down the expression tree (the platform
            // executor convention, cf. DefaultExpressionExecutor /
            // DebugExpressionExecutor): every nested executor.execute entry —
            // including each while-loop back edge — re-enters this check.
            return expr.execute(this, rt);
        } finally {
            depth[0]--;
        }
    }

    static String ruleIdOf(IEvalScope scope) {
        Object value = scope.getValue(XScriptDeadline.VAR_RULE_ID);
        return value instanceof String id ? id : "unknown";
    }

    static int budgetMsOf(IEvalScope scope) {
        Object value = scope.getValue(XScriptDeadline.VAR_BUDGET_MS);
        return value instanceof Number number ? number.intValue() : -1;
    }
}
