package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.fix.FixApplier;
import io.nop.lint.core.fix.UnifiedDiff;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.rule.RuleDslModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The v1 {@code nop-lint check} assembly loop (design 03 §2.4): it only
 * wires the existing pipeline together — {@link TargetScanner} file
 * discovery, {@link RuleSetLoader} classpath-VFS rule loading, and one
 * shared {@link LintEngine} (deadline installation and the suppression
 * tail run inside every {@link LintEngine#lint} call). It implements no
 * pipeline semantics of its own.
 *
 * <p>Fix flow (roadmap item 25, design 03 §2.4 增注): {@code --fix} drives
 * {@link FixApplier} per file — the multipass loop writes every successful
 * pass — and the report then lints the final on-disk content, so the
 * diagnostics and the exit code describe the residuals exactly as a
 * re-run without {@code --fix} would see them. {@code --fix-dry-run} runs
 * the identical loop in memory, reports the untouched content, and adds a
 * {@link FileDiff} per file the loop would change. Either way the summary
 * carries the fix counters alongside the engine counters.</p>
 *
 * <p>Failure contract (no silent continuation): a rule group whose
 * language is not bound in the registry stops the run before any file is
 * linted — linting with a reduced rule set would report false
 * confidence. A file that cannot be read or parsed aborts the run with an
 * exception carrying the file path; the CLI maps that to exit code 2.</p>
 */
public final class CheckRunner {

    private final LanguageRegistry registry;
    private final RuleSetLoader ruleLoader;
    private final String rulesPrefix;

    /**
     * A run over the conventional production rule prefix.
     */
    public CheckRunner(LanguageRegistry registry, RuleSetLoader ruleLoader) {
        this(registry, ruleLoader, RuleSetLoader.DEFAULT_RULES_PREFIX);
    }

    /**
     * A run over an explicit rule prefix (test harnesses point this at
     * fixture rule sets).
     */
    public CheckRunner(LanguageRegistry registry, RuleSetLoader ruleLoader, String rulesPrefix) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.ruleLoader = Objects.requireNonNull(ruleLoader, "ruleLoader must not be null");
        this.rulesPrefix = Objects.requireNonNull(rulesPrefix, "rulesPrefix must not be null");
    }

    /**
     * Runs the check over the scan's lintable files (report-only) and
     * returns the aggregated outcome.
     */
    public CheckOutcome run(TargetScanner.ScanResult scan, LintProfile profile) {
        return run(scan, profile, CliOptions.FixMode.NONE);
    }

    /**
     * Runs the check over the scan's lintable files under the given fix
     * mode and returns the aggregated outcome.
     *
     * @param scan    the completed target scan (files + skipped accounting)
     * @param profile the execution profile from the CLI options
     * @param fixMode the fix flow the run drives (report-only when {@code NONE})
     * @throws NopLintException when a rule language is unbound or a file
     *                          cannot be read/parsed/written
     */
    public CheckOutcome run(TargetScanner.ScanResult scan, LintProfile profile,
                            CliOptions.FixMode fixMode) {
        Map<String, List<RuleDslModel>> rulesByLanguage = ruleLoader.loadGroupedByLanguage(rulesPrefix);
        verifyRuleLanguages(rulesByLanguage);

        LintEngine engine = new LintEngine(registry, profile);
        RunSummary summary = new RunSummary(scan.skipped());
        List<FileFindings> findings = new ArrayList<>(scan.lintable().size());
        List<FileDiff> diffs = new ArrayList<>();

        for (TargetScanner.LintableFile file : scan.lintable()) {
            findings.add(switch (fixMode) {
                case NONE -> lintFile(engine, rulesByLanguage, file, summary);
                case APPLY -> fixFile(engine, rulesByLanguage, file, summary, false, diffs);
                case DRY_RUN -> fixFile(engine, rulesByLanguage, file, summary, true, diffs);
            });
        }
        return new CheckOutcome(findings, summary, fixMode, diffs,
                suggestOnlyFixDescriptions(rulesByLanguage));
    }

    /**
     * Every rule group must resolve against the registry: a rule set
     * shipping a language the runtime cannot bind is a deployment error
     * (exit 2), not a reduced silent run. The message names the offending
     * rule ids so the broken rule is fixable without re-deriving it.
     */
    private void verifyRuleLanguages(Map<String, List<RuleDslModel>> rulesByLanguage) {
        for (Map.Entry<String, List<RuleDslModel>> entry : rulesByLanguage.entrySet()) {
            String language = entry.getKey();
            if (registry.registeredIds().contains(language)) {
                continue;
            }
            List<String> ids = entry.getValue().stream().map(RuleDslModel::getId).toList();
            throw new NopLintException("no language binding registered for lint language '"
                    + language + "' (rules: " + String.join(", ", ids)
                    + "; registered bindings: " + registry.registeredIds() + ")");
        }
    }

    /**
     * The rule ids of suggestion-only fix rules mapped to their fix
     * description — the reporter annotates those rules' diagnostics with it
     * (a suggestion is listed, never applied).
     */
    private static Map<String, String> suggestOnlyFixDescriptions(
            Map<String, List<RuleDslModel>> rulesByLanguage) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (List<RuleDslModel> models : rulesByLanguage.values()) {
            for (RuleDslModel model : models) {
                if (model.getFix() != null && model.getFix().isSuggest()) {
                    descriptions.put(model.getId(), model.getFix().getDescription());
                }
            }
        }
        return descriptions;
    }

    /**
     * Lints one file through the shared engine and folds the result into
     * the summary. The engine call owns parsing, deadline installation, and
     * the suppression tail; any failure propagates with the file path
     * attached.
     */
    private FileFindings lintFile(LintEngine engine, Map<String, List<RuleDslModel>> rulesByLanguage,
                                  TargetScanner.LintableFile file, RunSummary summary) {
        byte[] bytes = readSource(file.path());
        LintResult result = lintWithTrace(engine, rulesByLanguage, file, bytes);
        summary.accumulate(result);
        return new FileFindings(file.path().toString(), new LineIndex(new String(bytes,
                StandardCharsets.UTF_8)), result.diagnostics());
    }

    /**
     * The fix flow for one file: the {@link FixApplier} multipass (writing
     * per pass unless dry-run), then the reporting lint over the content
     * the run leaves on disk — post-fix residuals for apply, the untouched
     * content for dry-run, plus the proposed diff in dry-run. The fix
     * stats fold into the summary; the applier's internal lint runs do not
     * double-count engine stats.
     */
    private FileFindings fixFile(LintEngine engine, Map<String, List<RuleDslModel>> rulesByLanguage,
                                 TargetScanner.LintableFile file, RunSummary summary, boolean dryRun,
                                 List<FileDiff> diffs) {
        byte[] original = readSource(file.path());
        List<RuleDslModel> rules = rulesByLanguage.getOrDefault(file.languageId(), List.of());
        LintLanguage language = registry.resolve(file.languageId());
        FixApplier applier = new FixApplier(
                source -> lintWithTrace(engine, rulesByLanguage, file, source),
                language);

        FixApplier.FixResult result = applier.run(file.path(), original, dryRun);
        summary.addFixStats(result.stats());

        byte[] reportSource = dryRun ? original : result.finalSource();
        if (dryRun && !Arrays.equals(original, result.finalSource())) {
            String displayPath = file.path().toString();
            diffs.add(new FileDiff(displayPath, UnifiedDiff.of(displayPath,
                    new String(original, StandardCharsets.UTF_8),
                    new String(result.finalSource(), StandardCharsets.UTF_8))));
        }

        LintResult report = lintWithTrace(engine, rulesByLanguage, file, reportSource);
        summary.accumulate(report);
        return new FileFindings(file.path().toString(),
                new LineIndex(new String(reportSource, StandardCharsets.UTF_8)),
                report.diagnostics());
    }

    /**
     * One engine run over the file's rules with the file path attached to
     * every failure (the L2 resolver resolves positions against it).
     */
    private LintResult lintWithTrace(LintEngine engine, Map<String, List<RuleDslModel>> rulesByLanguage,
                                     TargetScanner.LintableFile file, byte[] source) {
        List<RuleDslModel> rules = rulesByLanguage.getOrDefault(file.languageId(), List.of());
        try {
            return engine.lint(rules, file.languageId(), file.path().toString(),
                    new String(source, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new NopLintException("lint check failed for file '"
                    + file.path() + "': " + e.getMessage(), e);
        }
    }

    private byte[] readSource(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new NopLintException("failed to read lint target file '" + path
                    + "': " + e.getMessage(), e);
        }
    }
}
