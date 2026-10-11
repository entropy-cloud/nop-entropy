package io.nop.lint.core.xscript;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.IExpressionExecutor;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.lang.TreeSitterLanguageAdapter;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.pattern.MetaVarEnv;
import io.nop.xlang.api.XLang;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Route A deadline wrapper proofs (design 07 §4, plan item 15 Phase 1):
 * idempotent installation over the install-time executor, verbatim
 * passthrough without a deadline, explicit abort on expiry, host-side
 * deadline injection visible on the real execution path, deadline keys kept
 * out of the compile whitelist, per-loop-back-edge interception (Minimum
 * Rules #23 wiring evidence), and the depth-32 guard with balanced
 * accounting. Every proof drives the real {@link XScriptCompiler} pipeline
 * and the real global-executor slot.
 */
public class TestLintDeadlineExecutor {

    private static final String RULE = "demo/deadline";

    private static final LintLanguage JAVA = new TreeSitterLanguageAdapter("java",
            io.nop.treesitter.language.Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin"), null);

    private static final String SRC = """
            class Demo {
                void m() {
                    System.out.println("x");
                }
            }
            """;

    private static IExpressionExecutor initialExecutor;
    private static LintTree tree;
    private static SourceMap sourceMap;

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
        initialExecutor = EvalExprProvider.getGlobalExecutor();
        tree = JAVA.parse(SRC);
        sourceMap = new SourceMap(tree.source());
    }

    @AfterAll
    static void destroy() {
        EvalExprProvider.registerGlobalExecutor(initialExecutor);
        CoreInitialization.destroy();
    }

    // ==================== installation (the Decision item) ====================

    @Test
    public void installIsIdempotentAndDelegatesToTheInstallTimeExecutor() {
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        IExpressionExecutor base = prior;
        while (base instanceof LintDeadlineExecutor wrapped) {
            base = wrapped.delegate();
        }
        EvalExprProvider.registerGlobalExecutor(base);
        try {
            LintDeadlineExecutor.install();
            IExpressionExecutor first = EvalExprProvider.getGlobalExecutor();
            assertInstanceOf(LintDeadlineExecutor.class, first,
                    "install must own the global executor slot");
            assertSame(base, ((LintDeadlineExecutor) first).delegate(),
                    "the wrapper must delegate to the executor captured at install time, "
                            + "not hardwire DefaultExpressionExecutor");

            LintDeadlineExecutor.install();
            assertSame(first, EvalExprProvider.getGlobalExecutor(),
                    "a second install must not stack another wrapper");
        } finally {
            EvalExprProvider.registerGlobalExecutor(base);
        }
    }

    // ==================== passthrough: no deadline -> untouched semantics ====================

    @Test
    public void noDeadlinePassesThroughWithUnchangedSemantics() {
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        LintDeadlineExecutor.install();
        RecordingExecutor recorder = new RecordingExecutor(EvalExprProvider.getGlobalExecutor());
        EvalExprProvider.registerGlobalExecutor(recorder);
        try {
            IEvalAction action = XScriptCompiler.compile(RULE, "let x = 1; x + 2");
            IEvalScope scope = XLang.newEvalScope();
            Object result = action.invoke(scope.newChildScope());

            assertEquals(3, ((Number) result).intValue(),
                    "the wrapper must not alter evaluation results");
            assertEquals(1, recorder.invocations,
                    "passthrough adds exactly one hop at the global entry: the delegate owns the "
                            + "whole subtree, exactly as the pre-wrapper executor would");
            assertNull(recorder.firstDeadline, "no deadline key was in scope");
        } finally {
            EvalExprProvider.registerGlobalExecutor(prior);
        }
    }

    // ==================== expiry: explicit abort, not silent return ====================

    @Test
    public void expiredDeadlineAbortsExplicitlyAtTheWrapper() {
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        LintDeadlineExecutor.install();
        try {
            IEvalAction action = XScriptCompiler.compile(RULE, "let x = 1; x + 2");
            IEvalScope scope = XLang.newEvalScope().newChildScope();
            scope.setLocalValue(XScriptDeadline.VAR_DEADLINE_NANOS, System.nanoTime() - 1L);
            scope.setLocalValue(XScriptDeadline.VAR_RULE_ID, RULE);
            scope.setLocalValue(XScriptDeadline.VAR_BUDGET_MS, 20);

            XScriptTimeoutException e = assertThrows(XScriptTimeoutException.class,
                    () -> action.invoke(scope), "an expired deadline must abort, not fall through");
            assertEquals(RULE, e.ruleId());
            assertEquals(20, e.budgetMs());
            assertTrue(e.getMessage().contains(RULE), "the abort names the rule: " + e.getMessage());
        } finally {
            EvalExprProvider.registerGlobalExecutor(prior);
        }
    }

    @Test
    public void expiredDeadlineAbortsThroughTheEngineInjectionPath() {
        LintDeadlineExecutor.install();
        XScriptEngine engine = new XScriptEngine(RULE, "warning", XScriptCompiler.compile(RULE, "let x = 1;"));
        XScriptDeadline expired = new XScriptDeadline(RULE, 20, System.nanoTime() - 1L);

        XScriptTimeoutException e = assertThrows(XScriptTimeoutException.class,
                () -> engine.executeMatch(tree.root(), new MetaVarEnv(), sourceMap, expired),
                "engine-injected deadlines must reach the wrapper and abort");
        assertTrue(e.getMessage().contains(RULE), e.getMessage());
    }

    // ==================== injection: host-side values reach the execution path ====================

    @Test
    public void injectedDeadlineValuesAreVisibleOnTheExecutionPath() {
        IExpressionExecutor prior = EvalExprProvider.getGlobalExecutor();
        LintDeadlineExecutor.install();
        RecordingExecutor recorder = new RecordingExecutor(EvalExprProvider.getGlobalExecutor());
        EvalExprProvider.registerGlobalExecutor(recorder);
        try {
            XScriptEngine engine = new XScriptEngine(RULE, "warning",
                    XScriptCompiler.compile(RULE, "report({ message: 'ok' });"));
            XScriptDeadline deadline = XScriptDeadline.startNow(RULE, 250);
            XScriptEngine.MatchOutcome outcome = engine.executeMatch(
                    tree.root(), new MetaVarEnv(), sourceMap, deadline);

            assertEquals(1, outcome.diagnostics().size(), "the script ran to completion");
            assertEquals(deadline.deadlineNanos(), recorder.firstDeadline,
                    "the wrapper saw exactly the injected deadline");
            assertEquals(RULE, recorder.firstRuleId);
            assertEquals(250, recorder.firstBudget);
        } finally {
            EvalExprProvider.registerGlobalExecutor(prior);
        }
    }

    // ==================== whitelist: deadline keys are host-side only ====================

    @Test
    public void deadlineKeysAreNotInTheCompileWhitelist() {
        NopLintException e = assertThrows(NopLintException.class,
                () -> XScriptCompiler.compile(RULE, "__lintDeadlineNanos"),
                "the deadline key is a host-side injection channel, not a script-visible binding");
        assertTrue(e.getMessage().contains("__lint"), e.getMessage());
    }

    // ==================== wiring: the loop back edge re-enters the hook ====================

    /**
     * Per-back-edge interception proof (Minimum Rules #23). The top-level
     * executor entry happens once, before the loop starts, when the deadline
     * is still in the future — so the only way this script can abort is a
     * nested executor entry re-checking the deadline at a loop back edge
     * ({@code WhileExecutable} executes its test/body through the executor
     * handed down, which the wrapper threads as itself). A wrapper that only
     * checked the top-level entry would hang this test.
     */
    @Test
    public void expiryMidLoopProvesPerBackEdgeInterception() {
        LintDeadlineExecutor.install();
        XScriptEngine engine = new XScriptEngine(RULE, "warning", XScriptCompiler.compile(RULE, """
                let i = 0;
                while (i < 1000000000) {
                  i = i + 1;
                }
                """));
        // a 10^9-iteration interpreter loop cannot complete within 50ms, and
        // the deadline starts before the first entry, so the abort must come
        // from a back edge after mid-loop expiry
        XScriptDeadline deadline = XScriptDeadline.startNow(RULE, 50);

        XScriptTimeoutException e = assertThrows(XScriptTimeoutException.class,
                () -> engine.executeMatch(tree.root(), new MetaVarEnv(), sourceMap, deadline),
                "the loop must be cut from inside, at a nested executor entry");
        assertTrue(e.getMessage().contains(RULE), e.getMessage());
    }

    // ==================== depth guard: runaway nesting aborts explicitly ====================

    @Test
    public void evaluationDepthBeyondTheLimitAbortsExplicitly() {
        LintDeadlineExecutor.install();
        XScriptEngine engine = new XScriptEngine(RULE, "warning",
                XScriptCompiler.compile(RULE, "let d = node.kind()" + "+ 1".repeat(40)));

        // generous budget: the abort must come from the depth guard, not expiry
        XScriptDeadline deadline = XScriptDeadline.startNow(RULE, 1000);
        NopLintException e = assertThrows(NopLintException.class,
                () -> engine.executeMatch(tree.root(), new MetaVarEnv(), sourceMap, deadline),
                "nesting beyond 32 executor entries must abort the script");
        assertTrue(e.getMessage().contains("depth"), e.getMessage());
        assertInstanceOf(NopLintException.class, e);
        assertTrue(!(e instanceof XScriptTimeoutException),
                "a depth abort is the script-failure path, not a timeout");
    }

    @Test
    public void shallowNestingRunsWithinTheDepthLimit() {
        LintDeadlineExecutor.install();
        XScriptEngine engine = new XScriptEngine(RULE, "warning",
                XScriptCompiler.compile(RULE, "let d = node.kind() + 1 + 1"));
        XScriptDeadline deadline = XScriptDeadline.startNow(RULE, 1000);

        XScriptEngine.MatchOutcome outcome = engine.executeMatch(
                tree.root(), new MetaVarEnv(), sourceMap, deadline);
        assertTrue(outcome.diagnostics().isEmpty(), "the control script completes normally");
    }

    @Test
    public void depthAccountingIsBalancedAfterAnAbort() {
        LintDeadlineExecutor.install();
        XScriptEngine engine = new XScriptEngine(RULE, "warning",
                XScriptCompiler.compile(RULE, "let d = node.kind()" + "+ 1".repeat(40)));
        try {
            engine.executeMatch(tree.root(), new MetaVarEnv(), sourceMap, XScriptDeadline.startNow(RULE, 1000));
        } catch (Exception expected) {
            // the depth abort under test
        }

        // the same thread must still evaluate normally: the per-thread depth
        // counter unwound through the abort
        IEvalAction action = XScriptCompiler.compile(RULE, "let x = 1; x + 2");
        Object result = action.invoke(XLang.newEvalScope().newChildScope());
        assertEquals(3, ((Number) result).intValue());
    }

    /**
     * Counts every executor entry and records the first deadline scope
     * values it observes — the wiring-evidence seam.
     */
    private static final class RecordingExecutor implements IExpressionExecutor {
        private final IExpressionExecutor delegate;
        int invocations;
        Long firstDeadline;
        String firstRuleId;
        Integer firstBudget;

        RecordingExecutor(IExpressionExecutor delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object execute(IExecutableExpression expr, EvalRuntime rt) {
            invocations++;
            if (firstDeadline == null) {
                IEvalScope scope = rt.getScope();
                Object deadline = scope.getValue(XScriptDeadline.VAR_DEADLINE_NANOS);
                if (deadline != null) {
                    firstDeadline = ((Number) deadline).longValue();
                    firstRuleId = LintDeadlineExecutor.ruleIdOf(scope);
                    firstBudget = LintDeadlineExecutor.budgetMsOf(scope);
                }
            }
            return delegate.execute(expr, rt);
        }
    }
}
