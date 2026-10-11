package io.nop.refactor.core.cli;

import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.cli.TargetScanner;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.suppress.ExemptionFilter;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.RefactorResult;
import io.nop.refactor.core.operation.PreparedTarget;
import io.nop.refactor.core.operation.RefactorOperationRunner;
import io.nop.refactor.core.operation.RewriteOperation;
import io.nop.refactor.core.operation.RewriteRequest;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The refactor CLI (nop-refactor WI7): the batch-processing form of the same
 * engine the GraphQL face (WI6) serves — preview computes the edit plan and
 * renders the unified diff without writing, apply re-computes the same plan
 * (stateless re-execution, baseline §三) and lands it. The CLI adds
 * only argument parsing, target orchestration, rendering, and the exit-code
 * mapping — no engine logic, no second payload, no second assembly: since
 * WI9 the execution chain itself is the operation framework's (RewriteOperation
 * through {@link RefactorOperationRunner}), the same path the GraphQL face
 * and the rename operations run on.
 *
 * <p>Exit codes (plan 07's pinned three-state boundary): {@link #EXIT_OK} =
 * the run completed with an empty nonApplied list; {@link #EXIT_NONAPPLIED} =
 * the run completed with nonApplied entries (conflict / out-of-scope /
 * unresolved-target slot / guard rollback); {@link #EXIT_INTERNAL} = the run
 * aborted (argument parsing, ruleset load or load-gate failure, unreadable
 * target, IO failure, or a degraded transform generation — a lost edit on
 * the rewrite face is an abort, never a silent skip). Fail-closed throughout:
 * nothing is guessed, nothing is swallowed.</p>
 */
public final class NopRefactorCli {

    public static final int EXIT_OK = 0;
    public static final int EXIT_NONAPPLIED = 1;
    public static final int EXIT_INTERNAL = 2;

    /** Single-file source cap (read gate), mirroring the Lint CLI's default. */
    public static final int DEFAULT_MAX_SOURCE_BYTES = 1024 * 1024;

    /** Target-count cap: bulk rewrite's added risk dimension (plan 07 pins 512). */
    public static final int DEFAULT_MAX_TARGET_FILES = 512;

    private NopRefactorCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * The production in-process entry: default language registry, STANDARD
     * profile (the transform channel is open only outside FAST — FAST closes
     * the generation surface entirely, so it can never serve a rewrite CLI).
     */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        return runFull(args, registry, LintProfile.STANDARD, out, err);
    }

    /**
     * The test/injection entry (the NopLintCli runFull precedent): registry
     * and profile ride the arguments so e2e harnesses can wire fixture
     * bindings and profiles without touching the production path. The load
     * gate always judges against STANDARD regardless of the injected profile.
     */
    public static int runFull(String[] args, LanguageRegistry registry, LintProfile profile,
                              PrintStream out, PrintStream err) {
        Objects.requireNonNull(registry, "registry must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        try {
            RefactorOptions options = RefactorOptions.parse(args);
            RefactorResult result = execute(options, registry, profile);
            if (options.json()) {
                RefactorRenderers.renderJson(result, out);
            } else {
                RefactorRenderers.renderConsole(options.mode(), result, out);
            }
            return exitFor(0, result);
        } catch (Exception e) {
            // String.valueOf keeps the exception class name on the error line
            // (structured enough for tooling); the full trace follows — an
            // aborted run is never a bare message
            err.println("nop-refactor: error: " + String.valueOf(e));
            e.printStackTrace(err);
            return EXIT_INTERNAL;
        }
    }

    /**
     * The single execution chain both modes share: load ruleset, scan
     * targets, read them behind the source cap, then hand the prepared
     * target set to the WI9 operation framework — the same
     * plan/apply/verify path every refactor consumer runs on.
     */
    static RefactorResult execute(RefactorOptions options, LanguageRegistry registry,
                                  LintProfile profile) throws Exception {
        RuleSetLoader.LoadedRuleSet loaded = new RuleSetLoader().loadRuleSet(options.rulesetPrefix());
        ExemptionFilter exemptions = ExemptionFilter.of(loaded.exemptions());

        boolean dryRun = options.mode() == RefactorOptions.Mode.PREVIEW;
        List<NonApply> nonApplies = new ArrayList<>();

        TargetScanner.ScanResult scan = TargetScanner.scan(options.targets(), registry);
        if (scan.skipped().total() > 0) {
            // unsupported-extension targets are an explicit structured face,
            // never a silent drop (plan 06 adjudication 6)
            nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE, "(skipped targets)",
                    "unsupported extensions: " + scan.skipped().describe()));
        }
        if (scan.lintable().size() > DEFAULT_MAX_TARGET_FILES) {
            throw new NopRefactorException("target set has " + scan.lintable().size()
                    + " files, over the nop.refactor.graphql.max-target-files-equivalent cap "
                    + DEFAULT_MAX_TARGET_FILES + " (rejected before any read; fail-closed)");
        }

        LintEngine engine = new LintEngine(registry, profile);
        List<PreparedTarget> prepared = new ArrayList<>(scan.lintable().size());
        for (TargetScanner.LintableFile lintable : scan.lintable()) {
            Path path = lintable.path();
            byte[] original;
            try {
                original = Files.readAllBytes(path);
            } catch (Exception e) {
                throw new NopRefactorException("target '" + path
                        + "' is not readable (fail-closed, the run aborts rather than "
                        + "silently skipping a rewrite target): " + e.getMessage(), e);
            }
            if (original.length > DEFAULT_MAX_SOURCE_BYTES) {
                throw new NopRefactorException("target '" + path + "' is " + original.length
                        + " bytes, over the " + DEFAULT_MAX_SOURCE_BYTES
                        + "-byte source cap (rejected before rendering; fail-closed)");
            }
            prepared.add(new PreparedTarget(path, lintable.languageId(), original));
        }

        RewriteRequest request = new RewriteRequest(loaded, exemptions, engine, registry,
                prepared, nonApplies, options.rulesetPrefix());
        return RefactorOperationRunner.INSTANCE.run(RewriteOperation.INSTANCE, request, dryRun);
    }

    /**
     * The exit-code mapping (plan 07's three-state boundary): a degraded
     * transform generation is an abort (2), an empty nonApplied list is a
     * full success (0), and any nonApplied entry is a structured partial (1).
     * Package-private so the mapping grid is component-testable with a
     * constructed sample (public incTransformDegraded builder) — the
     * production path reaches state 2 through execute's fail-closed throw.
     */
    static int exitFor(int transformDegraded, RefactorResult result) {
        if (transformDegraded > 0) {
            return EXIT_INTERNAL;
        }
        return result.nonApplied().isEmpty() ? EXIT_OK : EXIT_NONAPPLIED;
    }
}
