package io.nop.refactor.core;

import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.fix.UnifiedDiff;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.node.LintNode;
import io.nop.lint.core.node.LintTree;
import io.nop.lint.core.rule.RuleDslModel;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The verification computation over an edited-file set (roadmap WI5): every
 * edited file is re-parsed (syntax integrity via nop-treesitter recovery
 * nodes through the LintLanguage abstraction), the configured rule subset
 * re-lints the edited content for residual findings (via the nop-lint
 * engine), diffs are assembled from the original/edited pairs, and the
 * numbers aggregate into the {@link Verification} and {@link RefactorStats}
 * faces of one {@link RefactorResult}.
 *
 * <p>Aggregation adjudications (design 01 §四 增注, plan
 * 05-wi5-refactor-result-verification-payload Phase 2): {@code parseOk} is
 * the AND over per-file predicates, where a file's predicate is "its
 * re-parse has zero ERROR/missing nodes" — the file's own post-edit state,
 * not a delta against the pre-edit parse; {@code errorNodeCount} and
 * {@code residualDiagnostics} sum across files; a parse failure is a
 * fail-closed exception, never a faked {@code parseOk=true}. A verifier
 * built without a rule subset reports {@code residualDiagnostics=0} with
 * {@code residualRuleCount=0} — machine-readably distinct from "configured
 * subset found nothing". The v1 codemod face is pure in-process: the cost
 * tier is always {@link RefactorStats.CostTier#IN_PROCESS}, and
 * {@code symbolIntact} stays null (rename-class operations only, WI9-WI12).</p>
 */
public final class RefactorVerifier {

    private final LintLanguage language;
    private final Function<String, LintLanguage> languageByPath;
    private final LintEngine engine;
    private final List<RuleDslModel> residualRules;

    /**
     * A verifier without a residual rule subset: syntax verification and
     * diff assembly only, residual diagnostics reported as zero with
     * {@code residualRuleCount=0}.
     */
    public RefactorVerifier(LintLanguage language) {
        this(language, null, List.of());
    }

    /**
     * A verifier with a residual rule subset: the subset rides the existing
     * {@link LintEngine} (one engine, one rule set — no second lint path).
     */
    public RefactorVerifier(LintLanguage language, LintEngine engine,
                            List<RuleDslModel> residualRules) {
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.languageByPath = null;
        this.engine = engine;
        this.residualRules = residualRules == null ? List.of() : List.copyOf(residualRules);
        if (engine == null && !this.residualRules.isEmpty()) {
            throw new NopRefactorException("a residual rule subset was configured without an "
                    + "engine to run it (fail-closed, not a silent zero)");
        }
    }

    /**
     * The per-path language resolver form (nop-refactor WI7 additive): a
     * mixed-language target set resolves each file's binding at verify time
     * while keeping the single assemble path. Exactly one of the two
     * resolution forms is set.
     */
    public RefactorVerifier(Function<String, LintLanguage> languageByPath, LintEngine engine,
                            List<RuleDslModel> residualRules) {
        this.languageByPath = Objects.requireNonNull(languageByPath,
                "languageByPath must not be null");
        this.language = null;
        this.engine = engine;
        this.residualRules = residualRules == null ? List.of() : List.copyOf(residualRules);
        if (engine == null && !this.residualRules.isEmpty()) {
            throw new NopRefactorException("a residual rule subset was configured without an "
                    + "engine to run it (fail-closed, not a silent zero)");
        }
    }

    /**
     * The subset size the stats face reports (0 = unconfigured).
     */
    public int residualRuleCount() {
        return residualRules.size();
    }

    public Verification verify(List<EditedFile> files) {
        Objects.requireNonNull(files, "files must not be null");
        boolean parseOk = true;
        int errorNodes = 0;
        int residual = 0;
        for (EditedFile file : files) {
            LintLanguage fileLanguage = languageByPath != null
                    ? languageByPath.apply(file.path())
                    : language;
            if (fileLanguage == null) {
                throw new NopRefactorException("no language binding resolved for edited file '"
                        + file.path() + "' (fail-closed, never a faked parseOk=true)");
            }
            LintTree tree;
            try {
                tree = fileLanguage.parse(file.edited());
            } catch (RuntimeException e) {
                throw new NopRefactorException("re-parse failed for '" + file.path()
                        + "' after editing (fail-closed, never a faked parseOk=true): "
                        + e.getMessage(), e);
            }
            int errors = countErrorNodes(tree);
            if (errors > 0) {
                parseOk = false;
            }
            errorNodes += errors;
            if (engine != null && !residualRules.isEmpty()) {
                LintResult result = engine.lint(residualRules, fileLanguage, file.path(),
                        new String(file.edited(), StandardCharsets.UTF_8));
                residual += result.diagnostics().size();
            }
        }
        return new Verification(parseOk, errorNodes, residual, null);
    }

    /**
     * Assembles the full result: verification over the edited set, the
     * unified diff over the changed files, and the stats with the skip
     * buckets derived from the nonApplied enumeration.
     */
    public RefactorResult assemble(boolean applied, List<EditedFile> files,
                                   List<FileEdit> edits, List<NonApply> nonApplied) {
        return assemble(applied, files, edits, nonApplied, null);
    }

    /**
     * The additive symbol-intact form (plan 10 adjudication 5, the WI10
     * first rung): a rename-class operation supplies its pre-computed
     * reference-count assertion and the single assemble point folds it into
     * the payload — no second assembly path. {@code symbolIntact = null}
     * keeps the rewrite-face semantics (the field stays null); a rolled-back
     * file reports null too (the assertion's input never landed).
     */
    public RefactorResult assemble(boolean applied, List<EditedFile> files,
                                   List<FileEdit> edits, List<NonApply> nonApplied,
                                   Boolean symbolIntact) {
        Objects.requireNonNull(files, "files must not be null");
        Objects.requireNonNull(edits, "edits must not be null");
        Objects.requireNonNull(nonApplied, "nonApplied must not be null");
        Verification parsed = verify(files);
        Verification verification = symbolIntact == null ? parsed
                : new Verification(parsed.parseOk(), parsed.errorNodeCount(),
                        parsed.residualDiagnostics(), symbolIntact);

        List<String> diffs = new ArrayList<>();
        int editsApplied = 0;
        for (EditedFile file : files) {
            editsApplied += file.editCount();
            if (file.changed()) {
                diffs.add(UnifiedDiff.of(file.path(),
                        new String(file.original(), StandardCharsets.UTF_8),
                        new String(file.edited(), StandardCharsets.UTF_8)));
            }
        }
        RefactorStats stats = new RefactorStats(files.size(), editsApplied,
                RefactorStats.SkippedBuckets.of(nonApplied),
                RefactorStats.CostTier.IN_PROCESS, residualRuleCount());
        return new RefactorResult(applied, edits, String.join("\n", diffs), verification,
                stats, nonApplied);
    }

    /**
     * The error-recovery node count of one parsed tree ({@code ERROR} nodes
     * plus missing-node placeholders) — the same predicate shape the
     * nop-lint applier's guard uses, replicated here read-only (the two-file
     * additive scope of plan 05 forbids touching nop-lint for this).
     */
    private static int countErrorNodes(LintTree tree) {
        int count = 0;
        for (LintNode node : tree.root()) {
            if ("ERROR".equals(node.kind()) || node.isMissing()) {
                count++;
            }
        }
        return count;
    }
}
