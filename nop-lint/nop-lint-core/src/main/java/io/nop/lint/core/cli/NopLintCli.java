package io.nop.lint.core.cli;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.engine.LanguageRegistry;

import java.io.PrintStream;

/**
 * The {@code nop-lint} command line entry point (design 03 §2.4 v1 落地
 * 裁定, 2026-09-22): {@code nop-lint check <path>... [--profile
 * fast|standard]}. The CLI only assembles the existing pipeline — option
 * parsing, ServiceLoader language discovery, classpath-VFS rule loading,
 * the shared {@code LintEngine} per-file loop, and console rendering.
 *
 * <p>Exit codes (design 03 §2.4): {@link #EXIT_OK} when no error-severity
 * diagnostic was collected, {@link #EXIT_VIOLATIONS} when at least one was,
 * and {@link #EXIT_INTERNAL} for every internal error — an unparseable
 * argument, a missing target, a rule-loading failure, or a file that cannot
 * be read or parsed (the message carries the file path). Errors go to
 * stderr with the {@code nop-lint: error:} prefix; nothing is swallowed and
 * a partial report is never presented as success.</p>
 *
 * <p>Distribution: plain {@code java -cp} classpath assembly — the runtime
 * classpath must carry nop-lint-java (the ServiceLoader binding),
 * nop-lint-nop (the rule resources), and nop-treesitter (native library);
 * nop-lint-core holds no compile-time dependency on either.</p>
 */
public final class NopLintCli {

    /**
     * No error-severity diagnostic was found.
     */
    public static final int EXIT_OK = 0;

    /**
     * At least one error-severity diagnostic was found.
     */
    public static final int EXIT_VIOLATIONS = 1;

    /**
     * An internal error aborted the run (bad input, rule failure).
     */
    public static final int EXIT_INTERNAL = 2;

    private NopLintCli() {
    }

    /**
     * The JVM entry point; exits with the check's exit code.
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * The in-process entry point: parses, discovers languages through the
     * ServiceLoader, runs the check, renders the console report, and
     * returns the exit code. Test harnesses call this directly with
     * captured streams.
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        return run(args, LanguageRegistry.discoverDefaults(), out, err);
    }

    /**
     * The full chain with an explicit registry (the production path passes
     * the ServiceLoader-discovered registry; tests may pass a registry with
     * additional bindings).
     */
    public static int run(String[] args, LanguageRegistry registry, PrintStream out, PrintStream err) {
        return runFull(args, registry, RuleSetLoader.DEFAULT_RULES_PREFIX, out, err);
    }

    /**
     * The full chain over an explicit rule prefix (test harnesses point
     * this at fixture rule sets); production behavior is identical.
     * Internal errors are reported in full before the exit code is
     * produced: the message goes to {@code err} with the
     * {@code nop-lint: error:} prefix, the complete stack trace follows,
     * and {@link #EXIT_INTERNAL} is returned — a partial report is never
     * presented as success.
     */
    static int runFull(String[] args, LanguageRegistry registry, String rulesPrefix,
                       PrintStream out, PrintStream err) {
        try {
            return runChecked(args, registry, rulesPrefix, out, err);
        } catch (AbortedRun aborted) {
            return EXIT_INTERNAL;
        }
    }

    private static int runChecked(String[] args, LanguageRegistry registry, String rulesPrefix,
                                  PrintStream out, PrintStream err) {
        boolean selfInitialized = false;
        try {
            CliOptions options = CliOptions.parse(args);
            if (!CoreInitialization.isInitialized()) {
                CoreInitialization.initializeTo(
                        CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
                selfInitialized = true;
            }

            CheckOutcome outcome = new CheckRunner(registry, new RuleSetLoader(), rulesPrefix)
                    .run(TargetScanner.scan(options.targets(), registry), options.profile(),
                            options.fixMode(), options.baselineOp(), options.baselineFile());
            new ConsoleReporter(out).render(outcome);

            // exit-code contract (design 03 §2.4 + roadmap item 27): residual
            // error diagnostics, or stale baseline entries under
            // --baseline-check ("基线只减不增" enforcement), mean exit 1;
            // --write-baseline is a generation run — success is exit 0
            // regardless of the reported findings it recorded
            if (options.baselineOp() == CliOptions.BaselineOp.WRITE) {
                return EXIT_OK;
            }
            boolean stale = options.baselineOp() == CliOptions.BaselineOp.CHECK
                    && outcome.hasStaleBaselineEntries();
            return outcome.hasErrorDiagnostics() || stale ? EXIT_VIOLATIONS : EXIT_OK;
        } catch (Exception | StackOverflowError e) {
            err.println("nop-lint: error: " + e.getMessage());
            e.printStackTrace(err);
            throw new AbortedRun(e);
        } finally {
            if (selfInitialized) {
                CoreInitialization.destroy();
            }
        }
    }

    /**
     * The control-flow signal raised after an internal error has been
     * fully reported to {@code err}; {@link #runFull} maps it to
     * {@link #EXIT_INTERNAL}. The cause is carried so the abort is never
     * confusable with a success return, but its stack trace was already
     * printed at the reporting site.
     */
    private static final class AbortedRun extends RuntimeException {
        private static final long serialVersionUID = 1L;

        AbortedRun(Throwable cause) {
            super(cause);
        }
    }
}
