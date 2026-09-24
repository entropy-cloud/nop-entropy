package io.nop.lint.maven;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.lint.core.cli.CheckOutcome;
import io.nop.lint.core.cli.CheckRunner;
import io.nop.lint.core.cli.CliOptions;
import io.nop.lint.core.cli.ConsoleReporter;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.cli.TargetScanner;
import io.nop.lint.core.engine.LanguageRegistry;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code nop-lint:check} goal (roadmap item 37, design 03 §2.1): binds
 * the check pipeline to the {@code validate} phase. The Mojo adds no
 * pipeline semantics of its own — it maps parameters onto the CLI option
 * surface ({@link CliOptions#parse} keeps the flag mutual-exclusion rules in
 * one place) and delegates to the same {@link CheckRunner} the CLI uses;
 * the only new behavior is the Maven-side target resolution and the
 * exit-to-exception mapping.
 *
 * <p>Failure mapping (plan Decision 2, the exact matrix): error-severity
 * diagnostics or stale baseline entries fail the build through
 * {@link MojoFailureException} when {@link #failOnError} is on, and log a
 * warning otherwise; internal errors (rule-loading failures, unreadable
 * files) raise {@link MojoExecutionException} under the same switch and log
 * an error otherwise. {@code writeBaseline} is a generation run — it always
 * succeeds, mirroring the CLI's exit-0 contract.</p>
 *
 * <p>Target semantics (plan Decision 5): an explicitly configured target
 * that does not exist is a hard error (the CLI's exit-2 contract); the
 * default {@code src/} target missing is a warning plus a quiet return, so
 * reactor modules without sources stay green out of the box — a deliberate
 * adaptation to the Maven lifecycle, recorded as a design 03 §2.1 增注
 * deviation.</p>
 *
 * <p>Lifecycle (plan Decision 4): each {@link #execute()} performs one
 * {@code CoreInitialization} register-component cycle unless the JVM is
 * already initialized, mirroring the CLI — multi-module reactor builds pay
 * the init/destroy pair per module, the same order of cost as the CLI.</p>
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VALIDATE, threadSafe = true)
public class CheckMojo extends AbstractMojo {

    private static final String DEFAULT_TARGET = "src";

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * Skip the whole check (the standard Maven escape hatch).
     */
    @Parameter(property = "noplint.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Error-severity diagnostics (and stale baseline entries) fail the
     * build when true; when false they only log.
     */
    @Parameter(property = "noplint.failOnError", defaultValue = "true")
    private boolean failOnError;

    /**
     * The execution profile: {@code fast} or {@code standard} (the CLI's
     * {@code --profile}).
     */
    @Parameter(property = "noplint.profile", defaultValue = "standard")
    private String profile;

    /**
     * Files or directories to lint, resolved against the project base dir.
     * Defaults to {@code ${project.basedir}/src}.
     */
    @Parameter(property = "noplint.targets")
    private List<File> targets;

    /**
     * The classpath-VFS prefix the rule set loads from; the production
     * default is the nop-lint-nop library. Test harnesses point this at
     * fixture rule sets (the same surface the CLI tests use).
     */
    @Parameter(property = "noplint.rulesPrefix")
    private String rulesPrefix;

    /**
     * Apply the rules' fix templates after linting (the CLI's {@code --fix}).
     */
    @Parameter(property = "noplint.fix", defaultValue = "false")
    private boolean fix;

    /**
     * Report the fix diffs without writing them (the CLI's
     * {@code --fix-dry-run}; mutually exclusive with {@link #fix} — the
     * CLI's option parser owns that rule).
     */
    @Parameter(property = "noplint.fixDryRun", defaultValue = "false")
    private boolean fixDryRun;

    /**
     * The baseline file for the {@code baselineApply} / {@code baselineCheck}
     * flows.
     */
    @Parameter(property = "noplint.baselineFile")
    private String baselineFile;

    /**
     * Suppress diagnostics the baseline matches (the CLI's {@code --baseline}).
     */
    @Parameter(property = "noplint.baselineApply", defaultValue = "false")
    private boolean baselineApply;

    /**
     * Fail when diagnostics no longer match the baseline — the
     * "baseline only shrinks" gate through the Maven channel (the CLI's
     * {@code --baseline-check}; stale entries map to
     * {@link MojoFailureException} exactly like error diagnostics).
     */
    @Parameter(property = "noplint.baselineCheck", defaultValue = "false")
    private boolean baselineCheck;

    /**
     * Generate the baseline from the run's residual and always succeed (the
     * CLI's {@code --write-baseline}; mutually exclusive with the fix flags
     * — the CLI's option parser owns that rule too).
     */
    @Parameter(property = "noplint.writeBaseline", defaultValue = "false")
    private boolean writeBaseline;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();
        if (skip) {
            log.info("nop-lint check is skipped.");
            return;
        }

        List<String> resolved = resolveTargets();
        if (resolved == null) {
            return;
        }

        boolean selfInitialized = false;
        try {
            if (!CoreInitialization.isInitialized()) {
                CoreInitialization.initializeTo(
                        CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
                selfInitialized = true;
            }

            CliOptions options = CliOptions.parse(buildArgs(resolved));
            LanguageRegistry registry = LanguageRegistry.discoverDefaults();
            CheckOutcome outcome = new CheckRunner(registry, new RuleSetLoader(),
                            rulesPrefix == null ? RuleSetLoader.DEFAULT_RULES_PREFIX : rulesPrefix)
                    .run(TargetScanner.scan(options.targets(), registry), options.profile(),
                            options.fixMode(), options.baselineOp(), options.baselineFile());
            new ConsoleReporter(bridgeToLog(log)).render(outcome);

            mapOutcome(outcome, options);
        } catch (MojoFailureException e) {
            // the failure face mapped by mapOutcome — never an internal error
            throw e;
        } catch (Exception | StackOverflowError e) {
            // every pipeline failure (rule loading, unreadable file, bad
            // option combination) is an internal error: exit-2 semantics
            String message = "nop-lint check aborted: " + e.getMessage();
            if (failOnError) {
                throw new MojoExecutionException(message, e);
            }
            log.error(message, e);
        } finally {
            if (selfInitialized) {
                CoreInitialization.destroy();
            }
        }
    }

    /**
     * The target resolution contract (plan Decision 5): explicit targets
     * must exist (a hard error otherwise — the CLI's exit-2 face); the
     * default {@code src/} missing means "nothing to lint in this module" —
     * a warning and a quiet return. A {@code null} return means the caller
     * stops without running.
     */
    private List<String> resolveTargets() throws MojoExecutionException {
        if (targets != null && !targets.isEmpty()) {
            List<String> resolved = new ArrayList<>(targets.size());
            for (File target : targets) {
                File path = target.isAbsolute() ? target
                        : new File(project.getBasedir(), target.getPath());
                if (!path.exists()) {
                    throw new MojoExecutionException("nop-lint target '" + path
                            + "' does not exist (explicit targets must resolve)");
                }
                resolved.add(path.toString());
            }
            return resolved;
        }

        File defaultTarget = new File(project.getBasedir(), DEFAULT_TARGET);
        if (!defaultTarget.exists()) {
            getLog().warn("nop-lint: no default target '" + defaultTarget
                    + "' in this module; nothing to lint.");
            return null;
        }
        return List.of(defaultTarget.toString());
    }

    /**
     * The flag surface maps onto CLI argument syntax so every mutual
     * exclusion and value rule stays owned by {@link CliOptions#parse} —
     * the Mojo introduces no parsing semantics of its own.
     */
    private String[] buildArgs(List<String> resolvedTargets) {
        List<String> args = new ArrayList<>(resolvedTargets.size() + 8);
        args.add("check");
        args.addAll(resolvedTargets);
        args.add("--profile");
        args.add(profile == null ? "standard" : profile);
        if (fix) {
            args.add("--fix");
        }
        if (fixDryRun) {
            args.add("--fix-dry-run");
        }
        if (baselineApply && baselineFile != null) {
            args.add("--baseline");
            args.add(baselineFile);
        }
        if (baselineCheck && baselineFile != null) {
            args.add("--baseline-check");
            args.add(baselineFile);
        }
        if (writeBaseline && baselineFile != null) {
            args.add("--write-baseline");
            args.add(baselineFile);
        }
        return args.toArray(String[]::new);
    }

    /**
     * The exit contract mapped onto Maven (plan Decision 2): a generation
     * run always succeeds; residual error diagnostics or stale baseline
     * entries are the build-failure face; {@link #failOnError}=false
     * demotes the failure face to warnings. Internal errors are handled at
     * the catch site.
     */
    private void mapOutcome(CheckOutcome outcome, CliOptions options)
            throws MojoFailureException {
        if (options.baselineOp() == CliOptions.BaselineOp.WRITE) {
            return;
        }
        boolean stale = options.baselineOp() == CliOptions.BaselineOp.CHECK
                && outcome.hasStaleBaselineEntries();
        if (!outcome.hasErrorDiagnostics() && !stale) {
            return;
        }

        String message = summarize(outcome, stale);
        if (failOnError) {
            throw new MojoFailureException(message);
        }
        getLog().warn(message);
    }

    private static String summarize(CheckOutcome outcome, boolean stale) {
        List<String> errorRules = outcome.findings().stream()
                .flatMap(f -> f.diagnostics().stream())
                .filter(d -> "error".equals(d.severity()))
                .map(d -> d.ruleId())
                .distinct()
                .toList();
        StringBuilder sb = new StringBuilder("nop-lint found ").append(errorRules.size())
                .append(" error-severity diagnostic(s) (rules: ")
                .append(String.join(", ", errorRules))
                .append(")");
        if (stale) {
            sb.append(" and ").append(outcome.staleBaselineEntries().size())
                    .append(" stale baseline entr(y/ies)");
        }
        sb.append(" — see the report above");
        return sb.toString();
    }

    /**
     * The console report rides a line-buffered bridge into the Maven log
     * (the Maven log API is not a {@link PrintStream}; plan Decision: the
     * machine-readable formats stay on the CLI channel, item 39).
     */
    private static PrintStream bridgeToLog(Log log) {
        return new PrintStream(new LogOutputStream(log), true);
    }

    /**
     * Buffers bytes and forwards complete lines to {@code log.info(String)};
     * a trailing partial line flushes on close (ConsoleReporter always ends
     * its output with a newline, but the report must survive a change there).
     */
    private static final class LogOutputStream extends OutputStream {
        private final Log log;
        private final StringBuilder line = new StringBuilder();

        LogOutputStream(Log log) {
            this.log = log;
        }

        @Override
        public void write(int b) throws IOException {
            if (b == '\n') {
                flushLine();
                return;
            }
            if (b == '\r') {
                return;
            }
            line.append((char) b);
        }

        @Override
        public void flush() throws IOException {
            flushLine();
        }

        private void flushLine() {
            if (line.length() > 0) {
                log.info(line.toString());
                line.setLength(0);
            }
        }
    }
}
