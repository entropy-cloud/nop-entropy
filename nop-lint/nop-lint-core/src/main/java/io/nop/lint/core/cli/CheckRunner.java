package io.nop.lint.core.cli;

import io.nop.lint.core.NopLintException;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.node.LineIndex;
import io.nop.lint.core.rule.RuleDslModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
     * Runs the check over the scan's lintable files and returns the
     * aggregated outcome.
     *
     * @param scan    the completed target scan (files + skipped accounting)
     * @param profile the execution profile from the CLI options
     * @throws NopLintException when a rule language is unbound or a file
     *                          cannot be read/parsed
     */
    public CheckOutcome run(TargetScanner.ScanResult scan, LintProfile profile) {
        Map<String, List<RuleDslModel>> rulesByLanguage = ruleLoader.loadGroupedByLanguage(rulesPrefix);
        verifyRuleLanguages(rulesByLanguage);

        LintEngine engine = new LintEngine(registry, profile);
        RunSummary summary = new RunSummary(scan.skipped());
        List<FileFindings> findings = new ArrayList<>(scan.lintable().size());

        for (TargetScanner.LintableFile file : scan.lintable()) {
            findings.add(lintFile(engine, rulesByLanguage, file, summary));
        }
        return new CheckOutcome(findings, summary);
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
     * Lints one file through the shared engine and folds the result into
     * the summary. The engine call owns parsing, deadline installation, and
     * the suppression tail; any failure propagates with the file path
     * attached.
     */
    private FileFindings lintFile(LintEngine engine, Map<String, List<RuleDslModel>> rulesByLanguage,
                                  TargetScanner.LintableFile file, RunSummary summary) {
        String source = readSource(file.path());
        List<RuleDslModel> rules = rulesByLanguage.getOrDefault(file.languageId(), List.of());
        LintResult result;
        try {
            result = engine.lint(rules, file.languageId(), source);
        } catch (Exception e) {
            throw new NopLintException("lint check failed for file '"
                    + file.path() + "': " + e.getMessage(), e);
        }
        summary.accumulate(result);
        return new FileFindings(file.path().toString(), new LineIndex(source),
                result.diagnostics());
    }

    private String readSource(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopLintException("failed to read lint target file '" + path
                    + "': " + e.getMessage(), e);
        }
    }
}
