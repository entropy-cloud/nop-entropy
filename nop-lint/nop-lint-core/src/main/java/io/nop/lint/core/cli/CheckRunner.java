package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.Diagnostic;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.fix.FixApplier;
import io.nop.lint.core.fix.UnifiedDiff;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.engine.DeepResolvers;
import io.nop.lint.core.semantic.DataflowResolver;
import io.nop.lint.core.semantic.MetricsResolver;
import io.nop.lint.core.semantic.DataflowResolverDiscovery;
import io.nop.lint.core.semantic.MetricsResolverDiscovery;
import io.nop.lint.core.semantic.ScopeResolverDiscovery;
import io.nop.lint.core.semantic.SemanticResolver;
import io.nop.lint.core.semantic.SemanticResolverDiscovery;
import io.nop.lint.core.suppress.BaselineEngine;
import io.nop.lint.core.suppress.BaselineFile;
import io.nop.lint.core.suppress.ExemptionFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    private final Set<String> ruleFilter;

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
        this(registry, ruleLoader, rulesPrefix, Set.of());
    }

    /**
     * A run with the CLI's {@code --rules} narrowing (roadmap item 39, R1
     * 3.2 adjudication): the filter applies AFTER the rule-set load and
     * BEFORE the language verification — an explicitly requested narrowing,
     * so a language whose rules are all filtered out no longer triggers the
     * unbound-language check. Unknown ids fail the run (never a silent
     * empty filter).
     */
    public CheckRunner(LanguageRegistry registry, RuleSetLoader ruleLoader, String rulesPrefix,
                       Set<String> ruleFilter) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.ruleLoader = Objects.requireNonNull(ruleLoader, "ruleLoader must not be null");
        this.rulesPrefix = Objects.requireNonNull(rulesPrefix, "rulesPrefix must not be null");
        this.ruleFilter = ruleFilter == null ? Set.of() : Set.copyOf(ruleFilter);
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
        return run(scan, profile, fixMode, CliOptions.BaselineOp.NONE, null);
    }

    /**
     * Runs the check over the scan's lintable files under the given fix and
     * baseline flows and returns the aggregated outcome.
     *
     * @param scan         the completed target scan (files + skipped accounting)
     * @param profile      the execution profile from the CLI options
     * @param fixMode      the fix flow the run drives (report-only when {@code NONE})
     * @param baselineOp   the baseline flow (roadmap item 27, design 09 §5): APPLY
     *                     suppresses baseline-matched diagnostics, CHECK additionally
     *                     reports stale entries on the outcome (the caller turns them
     *                     into the exit-code-1 tightening gate), WRITE generates the
     *                     baseline from the run's residual
     * @param baselineFile the baseline file path for the non-NONE ops
     * @throws NopLintException when a rule language is unbound, a file cannot be
     *                          read/parsed/written, or the baseline cannot be loaded
     */
    public CheckOutcome run(TargetScanner.ScanResult scan, LintProfile profile,
                            CliOptions.FixMode fixMode, CliOptions.BaselineOp baselineOp,
                            String baselineFile) {
        return run(scan, profile, fixMode, baselineOp, baselineFile, null);
    }

    /**
     * The {@code --cache} face (roadmap item 43): replayable per-file
     * diagnostics keyed by content hash under a run fingerprint. Only the
     * report-only flow is cacheable — fix and baseline runs carry semantics
     * a replay cannot serve (plan adjudication).
     */
    public CheckOutcome run(TargetScanner.ScanResult scan, LintProfile profile,
                            CliOptions.FixMode fixMode, CliOptions.BaselineOp baselineOp,
                            String baselineFile, String cacheFile) {
        RuleSetLoader.LoadedRuleSet loaded = applyRuleFilter(ruleLoader.loadRuleSet(rulesPrefix));
        verifyRuleLanguages(loaded.rulesByLanguage());
        ExemptionFilter exemptions = ExemptionFilter.of(loaded.exemptions());

        Map<String, List<BaselineFile.Entry>> baselineByFile = Map.of();
        if (baselineOp == CliOptions.BaselineOp.APPLY || baselineOp == CliOptions.BaselineOp.CHECK) {
            baselineByFile = BaselineEngine.byFile(
                    BaselineFile.load(Path.of(baselineFile)).entries());
        }

        LintEngine engine = new LintEngine(registry, profile, new DeepResolvers(
                MetricsResolverDiscovery.discover(), ScopeResolverDiscovery.discover(),
                SemanticResolverDiscovery.discover(), DataflowResolverDiscovery.discover()));
        RunSummary summary = new RunSummary(scan.skipped());
        List<FileFindings> findings = new ArrayList<>(scan.lintable().size());
        List<FileDiff> diffs = new ArrayList<>();
        List<BaselineFile.Entry> staleEntries = new ArrayList<>();
        List<BaselineFile.Entry> writeEntries = new ArrayList<>();

        RuleResultCache cache = null;
        if (cacheFile != null) {
            if (fixMode != CliOptions.FixMode.NONE || baselineOp != CliOptions.BaselineOp.NONE) {
                throw new NopLintException("--cache cannot combine with --fix/--fix-dry-run or the"
                        + " --baseline family: replayed diagnostics cannot drive a fix multipass"
                        + " or a baseline flow (fail-closed)");
            }
            cache = RuleResultCache.load(java.nio.file.Path.of(cacheFile),
                    RuleResultCache.runFingerprint(loaded, profile.name(), fixMode,
                            List.copyOf(ruleFilter)));
        }

        for (TargetScanner.LintableFile file : scan.lintable()) {
            if (cache != null) {
                byte[] bytes = readSource(file.path());
                List<Diagnostic> replayed = cacheHit(cache, file, bytes, summary, exemptions);
                if (replayed != null) {
                    findings.add(new FileFindings(file.path().toString(),
                            new LineIndex(new String(bytes, StandardCharsets.UTF_8)), replayed));
                    continue;
                }
                FileFindings computed = lintFile(engine, loaded.rulesByLanguage(), file, summary,
                        exemptions, baselineOp, baselineByFile, staleEntries, writeEntries);
                cache.put(file.path().toString(), bytes, computed.diagnostics());
                findings.add(computed);
                continue;
            }
            findings.add(switch (fixMode) {
                case NONE -> lintFile(engine, loaded.rulesByLanguage(), file, summary, exemptions,
                        baselineOp, baselineByFile, staleEntries, writeEntries);
                case APPLY -> fixFile(engine, loaded.rulesByLanguage(), file, summary, exemptions,
                        baselineOp, baselineByFile, staleEntries, false, diffs);
                case DRY_RUN -> fixFile(engine, loaded.rulesByLanguage(), file, summary, exemptions,
                        baselineOp, baselineByFile, staleEntries, true, diffs);
            });
        }

        if (cache != null) {
            cache.save();
        }

        if (baselineOp == CliOptions.BaselineOp.WRITE) {
            new BaselineFile(BaselineFile.VERSION_1, writeEntries)
                    .writeTo(Path.of(baselineFile));
        }
        return new CheckOutcome(findings, summary, fixMode, diffs,
                suggestOnlyFixDescriptions(loaded.rulesByLanguage()), staleEntries);
    }

    /**
     * The ServiceLoader-discovered metrics provider (roadmap item 32, plan
     * Decision 5): the first implementation on the classpath serves the
     * run's {@code metrics} binding under the deep profile; none means the
     * affected rules degrade (the fail-closed default). Cached — discovery
     * is a one-time cost per process.
     */
    private static MetricsResolver discoverMetricsResolver() {
        return MetricsResolverDiscovery.discover();
    }

    private static SemanticResolver discoverSemanticResolver() {
        return SemanticResolverDiscovery.discover();
    }

    private static DataflowResolver discoverDataflowResolver() {
        return DataflowResolverDiscovery.discover();
    }

    /**
     * The {@code --rules} narrowing (roadmap item 39): unknown requested ids
     * abort the run naming the offenders (a mistyped whitelist must not
     * become a silent zero-rule run); the survivors keep their language
     * grouping and scan order.
     */
    private RuleSetLoader.LoadedRuleSet applyRuleFilter(RuleSetLoader.LoadedRuleSet loaded) {
        if (ruleFilter.isEmpty()) {
            return loaded;
        }
        Set<String> loadedIds = new LinkedHashSet<>();
        loaded.rulesByLanguage().values().forEach(rules -> rules.forEach(r -> loadedIds.add(r.getId())));
        List<String> unknown = ruleFilter.stream().sorted()
                .filter(id -> !loadedIds.contains(id)).toList();
        if (!unknown.isEmpty()) {
            throw new NopLintException("--rules names ids the loaded rule set does not declare: "
                    + String.join(", ", unknown) + " (loaded: " + String.join(", ", loadedIds)
                    + ")");
        }
        Map<String, List<RuleDslModel>> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, List<RuleDslModel>> entry : loaded.rulesByLanguage().entrySet()) {
            List<RuleDslModel> kept = entry.getValue().stream()
                    .filter(r -> ruleFilter.contains(r.getId())).toList();
            if (!kept.isEmpty()) {
                filtered.put(entry.getKey(), kept);
            }
        }
        return new RuleSetLoader.LoadedRuleSet(filtered, loaded.exemptions());
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
     * the summary. The ruleset exemptions and the baseline matches are
     * applied BEFORE the severity accumulation — the summary counters and
     * the exit code describe only the post-filter residual (plan
     * 2026-09-24-0050-1 exit-code data flow adjudication). The engine call
     * owns parsing, deadline installation, and the suppression tail; any
     * failure propagates with the file path attached.
     */
    private FileFindings lintFile(LintEngine engine, Map<String, List<RuleDslModel>> rulesByLanguage,
                                  TargetScanner.LintableFile file, RunSummary summary,
                                  ExemptionFilter exemptions, CliOptions.BaselineOp baselineOp,
                                  Map<String, List<BaselineFile.Entry>> baselineByFile,
                                  List<BaselineFile.Entry> staleEntries,
                                  List<BaselineFile.Entry> writeEntries) {
        byte[] bytes = readSource(file.path());
        LintResult result = lintWithTrace(engine, rulesByLanguage, file, bytes);
        Filtered filtered = applyFilters(result.diagnostics(), file, bytes, exemptions,
                baselineOp, baselineByFile, summary, staleEntries);
        collectWriteEntries(writeEntries, file, filtered, baselineOp, bytes);
        summary.accumulate(new LintResult(filtered.kept(), result.stats()));
        return new FileFindings(file.path().toString(), new LineIndex(new String(bytes,
                StandardCharsets.UTF_8)), filtered.kept());
    }

    /**
     * One filter sweep's outcome: the kept diagnostics and — when the
     * baseline flow is active — the per-file decision set built from this
     * sweep (the fix flow reuses it as its stateless pass predicate).
     */
    private record Filtered(List<Diagnostic> kept, BaselineEngine.FileBaseline baseline) {
    }

    /**
     * The shared CLI-layer filter: ruleset exemptions first, then the
     * baseline decision. Counting happens on the ORIGINAL content's sweep
     * only (the fix multipass sweeps filter without counting — the plan's
     * consumption adjudication). The stale entries land on the run-level
     * list, never into the diagnostic stream.
     */
    private Filtered applyFilters(List<Diagnostic> diagnostics, TargetScanner.LintableFile file,
                                  byte[] source, ExemptionFilter exemptions,
                                  CliOptions.BaselineOp baselineOp,
                                  Map<String, List<BaselineFile.Entry>> baselineByFile,
                                  RunSummary summary, List<BaselineFile.Entry> staleEntries) {
        List<Diagnostic> kept = new ArrayList<>(diagnostics.size());
        int exempted = 0;
        for (Diagnostic diagnostic : diagnostics) {
            if (exemptions.suppresses(diagnostic.ruleId(), file.path())) {
                exempted++;
            } else {
                kept.add(diagnostic);
            }
        }
        if (exempted > 0) {
            summary.addExemptedDiagnostics(exempted);
        }

        if (baselineOp != CliOptions.BaselineOp.APPLY && baselineOp != CliOptions.BaselineOp.CHECK) {
            return new Filtered(kept, null);
        }

        String canonicalFile = ExemptionFilter.normalize(file.path());
        BaselineEngine.FileBaseline baseline = BaselineEngine.compute(canonicalFile, kept,
                baselineByFile.get(canonicalFile), source);
        if (baselineOp == CliOptions.BaselineOp.CHECK) {
            staleEntries.addAll(baseline.staleEntries());
        }
        List<Diagnostic> baselineKept = new ArrayList<>(kept.size());
        int baselined = 0;
        for (Diagnostic diagnostic : kept) {
            if (baseline.suppresses(diagnostic.ruleId(),
                    BaselineEngine.fingerprint(diagnostic.ruleId(), source, diagnostic.range()))) {
                baselined++;
            } else {
                baselineKept.add(diagnostic);
            }
        }
        if (baselined > 0) {
            summary.addBaselinedDiagnostics(baselined);
        }
        return new Filtered(baselineKept, baseline);
    }

    /**
     * The write primitive of {@code --write-baseline}: the run's residual
     * (post-exemption) diagnostics become the baseline entries.
     */
    private static void collectWriteEntries(List<BaselineFile.Entry> writeEntries,
                                            TargetScanner.LintableFile file, Filtered filtered,
                                            CliOptions.BaselineOp baselineOp, byte[] source) {
        if (baselineOp == CliOptions.BaselineOp.WRITE) {
            writeEntries.addAll(BaselineEngine.writeEntries(ExemptionFilter.normalize(file.path()),
                    filtered.kept(), source));
        }
    }

    /**
     * The fix flow for one file: the {@link FixApplier} multipass (writing
     * per pass unless dry-run), then the reporting lint over the content
     * the run leaves on disk — post-fix residuals for apply, the untouched
     * content for dry-run, plus the proposed diff in dry-run. The
     * exemption filter rides the applier's lint entry (a stateless
     * predicate, so every multipass sweep sees the same filter) and the
     * report lint alike. The fix stats fold into the summary; the
     * applier's internal lint runs do not double-count engine stats.
     */
    private FileFindings fixFile(LintEngine engine, Map<String, List<RuleDslModel>> rulesByLanguage,
                                 TargetScanner.LintableFile file, RunSummary summary,
                                 ExemptionFilter exemptions, CliOptions.BaselineOp baselineOp,
                                 Map<String, List<BaselineFile.Entry>> baselineByFile,
                                 List<BaselineFile.Entry> staleEntries, boolean dryRun,
                                 List<FileDiff> diffs) {
        byte[] original = readSource(file.path());
        List<RuleDslModel> rules = rulesByLanguage.getOrDefault(file.languageId(), List.of());
        LintLanguage language = registry.resolve(file.languageId());

        // the decision set is built ONCE from the original content's lint
        // (counting happens here and nowhere else), then acts as the
        // stateless predicate for every pass and the report lint
        LintResult originalResult = lintWithTrace(engine, rulesByLanguage, file, original);
        Filtered originalFiltered = applyFilters(originalResult.diagnostics(), file, original,
                exemptions, baselineOp, baselineByFile, summary, staleEntries);
        BaselineEngine.FileBaseline fileBaseline = originalFiltered.baseline();

        FixApplier applier = new FixApplier(
                source -> {
                    LintResult passResult = lintWithTrace(engine, rulesByLanguage, file, source);
                    List<Diagnostic> kept = filterForPass(passResult.diagnostics(), file, source,
                            exemptions, fileBaseline);
                    return new LintResult(kept, passResult.stats());
                },
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
        List<Diagnostic> reportKept = filterForPass(report.diagnostics(), file, reportSource,
                exemptions, fileBaseline);
        summary.accumulate(new LintResult(reportKept, report.stats()));
        return new FileFindings(file.path().toString(),
                new LineIndex(new String(reportSource, StandardCharsets.UTF_8)), reportKept);
    }

    /**
     * The stateless pass/report filter: ruleset exemptions and the fixed
     * baseline decision set, without any counting (the counters were
     * recorded once on the original content's sweep).
     */
    private List<Diagnostic> filterForPass(List<Diagnostic> diagnostics,
                                           TargetScanner.LintableFile file, byte[] source,
                                           ExemptionFilter exemptions,
                                           BaselineEngine.FileBaseline fileBaseline) {
        if (exemptions.size() == 0 && fileBaseline == null) {
            return diagnostics;
        }
        List<Diagnostic> kept = new ArrayList<>(diagnostics.size());
        for (Diagnostic diagnostic : diagnostics) {
            if (exemptions.suppresses(diagnostic.ruleId(), file.path())) {
                continue;
            }
            if (fileBaseline != null && fileBaseline.suppresses(diagnostic.ruleId(),
                    BaselineEngine.fingerprint(diagnostic.ruleId(), source, diagnostic.range()))) {
                continue;
            }
            kept.add(diagnostic);
        }
        return kept;
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

    /**
     * The cache-hit face: looks the file up by content hash and, on a hit,
     * applies the LIVE exemption filter (ruleset-level exemptions may have
     * changed even under a stable file hash), counts one cache hit, and
     * returns the replayed diagnostics — null on a miss (the caller lints).
     */
    private List<Diagnostic> cacheHit(RuleResultCache cache, TargetScanner.LintableFile file,
                                      byte[] bytes, RunSummary summary, ExemptionFilter exemptions) {
        RuleResultCache.CachedDiagnostics cached = cache.get(file.path().toString(), bytes);
        if (cached == null) {
            return null;
        }
        List<Diagnostic> kept = new ArrayList<>(cached.diagnostics().size());
        int exempted = 0;
        for (Diagnostic diagnostic : cached.diagnostics()) {
            if (exemptions.suppresses(diagnostic.ruleId(), file.path())) {
                exempted++;
            } else {
                kept.add(diagnostic);
            }
        }
        if (exempted > 0) {
            summary.addExemptedDiagnostics(exempted);
        }
        summary.addCacheHits(1);
        // replayed diagnostics join the severity totals so the report and
        // the exit contract see them exactly like a linted file (the engine
        // stats stay zero — no rules ran for this file)
        summary.accumulate(new LintResult(kept,
                io.nop.lint.core.engine.LintStats.builder().build()));
        return kept;
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
