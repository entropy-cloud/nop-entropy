package io.nop.refactor.core.cli;

import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.cli.TargetScanner;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.engine.LintStats;
import io.nop.lint.core.fix.EditPlanApplier;
import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.SourceRange;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.suppress.ExemptionFilter;
import io.nop.refactor.core.EditedFile;
import io.nop.refactor.core.FileEdit;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.RefactorResult;
import io.nop.refactor.core.RefactorVerifier;

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
 * (stateless re-execution, baseline §三) and lands it through the single
 * {@link io.nop.lint.core.fix.EditPlanApplier} mechanical core. The CLI adds
 * only argument parsing, target orchestration, rendering, and the exit-code
 * mapping — no engine logic, no second payload, no second assembly (both
 * modes assemble through {@link RefactorVerifier#assemble}).
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
     * The single execution chain both modes share: load ruleset, run the
     * load gate, collect targets, then per file — read (pre-read cap),
     * lint, exemption-gate the rewrites, apply through the one mechanical
     * core, and collect. Assembly happens once through RefactorVerifier.
     */
    static RefactorResult execute(RefactorOptions options, LanguageRegistry registry,
                                  LintProfile profile) throws Exception {
        RuleSetLoader.LoadedRuleSet loaded = new RuleSetLoader().loadRuleSet(options.rulesetPrefix());
        ExemptionFilter exemptions = ExemptionFilter.of(loaded.exemptions());
        verifyLoadGate(loaded, profile);

        boolean dryRun = options.mode() == RefactorOptions.Mode.PREVIEW;
        List<EditedFile> files = new ArrayList<>();
        List<FileEdit> edits = new ArrayList<>();
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
        // per-path language resolution: a mixed-language target set keeps the
        // single assemble path (WI7 additive on the WI5 verifier)
        java.util.Map<String, LintLanguage> languageByPath = new java.util.HashMap<>();
        RefactorVerifier verifier = new RefactorVerifier(languageByPath::get, engine, List.of());

        for (TargetScanner.LintableFile lintable : scan.lintable()) {
            Path path = lintable.path();
            LintLanguage language = registry.resolve(lintable.languageId());
            List<RuleDslModel> rules = loaded.rulesByLanguage()
                    .getOrDefault(lintable.languageId(), List.of());
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
            if (rules.isEmpty()) {
                nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE, path.toString(),
                        "no rules for language '" + lintable.languageId()
                                + "' in ruleset '" + options.rulesetPrefix() + "'"));
                continue;
            }

            languageByPath.put(path.toString(), language);
            LintResult lint = engine.lint(rules, language, path.toString(),
                    new String(original, StandardCharsets.UTF_8));
            if (lint.stats().getTransformDegraded() > 0) {
                throw new NopRefactorException("the resource gate closed transform generation for '"
                        + path + "' (" + lint.stats().getTransformDegraded()
                        + " edit(s) lost; a lost rewrite on the rewrite face is an abort, "
                        + "never a silent skip)");
            }

            List<Fix> rewrites = new ArrayList<>(lint.transformFixes().size());
            for (Fix rewrite : lint.transformFixes()) {
                if (exemptions.suppresses(rewrite.ruleId(), path)) {
                    nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE, path.toString(),
                            "rewrite from '" + rewrite.ruleId()
                                    + "' exempted by ruleset exemption"));
                } else {
                    rewrites.add(rewrite);
                }
            }
            if (rewrites.isEmpty()) {
                continue;
            }

            EditPlanApplier.EditPlanResult plan = EditPlanApplier.apply(path, original,
                    rewrites, language, dryRun);
            for (Fix skipped : plan.skippedEdits()) {
                nonApplies.add(new NonApply(NonApply.Reason.CONFLICT, path.toString(),
                        "overlaps an earlier-priority edit from '" + skipped.ruleId() + "'"));
            }
            if (plan.rolledBack()) {
                nonApplies.add(new NonApply(NonApply.Reason.ROLLED_BACK, path.toString(),
                        "guard rollback: the rewrites broke the file's syntax, the content "
                                + "was restored to its pre-edit state"));
                continue;
            }
            if (plan.appliedFixes().isEmpty()) {
                continue;
            }
            for (Fix applied : plan.appliedFixes()) {
                edits.add(new FileEdit(path.toString(), applied.range(), applied.description()));
            }
            files.add(new EditedFile(path.toString(), original, plan.finalSource(),
                    plan.appliedEdits()));
        }

        // untouched-but-scanned files that carried no rules stay out of the
        // assembly; the out-of-scope / no-rules faces already reported them
        return verifier.assemble(!dryRun, files, edits, nonApplies);
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

    /**
     * The load gate (plan 07, R2 Major B): the rewrite face consumes only
     * transform rules whose requires stay inside the fixed profile's
     * unconditional set — an empty {@code requires} always runs under any
     * profile, so "empty or reject" is the conservative zero-duplication
     * equivalent of the engine's private gate (a non-empty requires could
     * SKIP or DEGRADE the rule and its rewrites would never be computed —
     * silent missed rewrites). Always judged against STANDARD regardless of
     * any injected profile.
     */
    static void verifyLoadGate(RuleSetLoader.LoadedRuleSet loaded, LintProfile profile) {
        for (List<RuleDslModel> rules : loaded.rulesByLanguage().values()) {
            for (RuleDslModel rule : rules) {
                if (rule.getTransform() == null) {
                    throw new NopRefactorException("ruleset rule '" + rule.getId()
                            + "' is not a transform rule (the rewrite CLI's payload face "
                            + "consumes rewrites only; a reporting rule's findings would be "
                            + "silently dropped — fail-closed)");
                }
                if (!rule.getRequires().isEmpty()) {
                    throw new NopRefactorException("transform rule '" + rule.getId()
                            + "' declares requires " + rule.getRequires()
                            + " (a profile gate could skip or degrade the rule and its "
                            + "rewrites would never be computed; the rewrite CLI accepts "
                            + "empty-requires transform rules only — fail-closed)");
                }
            }
        }
    }
}
