package io.nop.refactor.graphql;

import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.util.SourceLocation;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.engine.LanguageRegistry;
import io.nop.lint.core.engine.LintEngine;
import io.nop.lint.core.engine.LintProfile;
import io.nop.lint.core.engine.LintResult;
import io.nop.lint.core.fix.EditPlanApplier;
import io.nop.lint.core.fix.Fix;
import io.nop.lint.core.lang.LintLanguage;
import io.nop.lint.core.rule.RuleDslModel;
import io.nop.lint.core.suppress.ExemptionFilter;
import io.nop.refactor.core.EditedFile;
import io.nop.refactor.core.FileEdit;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.RefactorResult;
import io.nop.refactor.core.RefactorRuleGates;
import io.nop.refactor.core.RefactorVerifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The GraphQL contract face of the refactor capability (nop-refactor WI6,
 * baseline §三): {@code Refactor__previewRewrite} / {@code Refactor__applyRewrite}
 * share one stateless execution chain — load the ruleset, collect the
 * targets behind the write-face path grammar, lint under the fixed STANDARD
 * profile, gate the transform rewrites through the ruleset's exemptions,
 * apply through the single {@link EditPlanApplier} mechanical core, and
 * assemble through the single {@link RefactorVerifier}. Apply re-computes
 * the same plan (stateless re-execution, no session, no plan-token).
 *
 * <p>Resource caps follow the {@code Lint__checkSource} precedent: a
 * byte-granularity pre-read gate plus a post-read backstop on
 * {@code nop.refactor.graphql.max-source-size}, and a target-count gate on
 * {@code nop.refactor.graphql.max-target-files} (default 512, plan 06
 * adjudication: the bulk face's added risk dimension, rejected before any
 * read). The rename action pair is deliberately not declared in v1 — the
 * schema grows non-destructively when the WI12 rename face lands
 * (adjudication: a declared-but-unsupported action is a false advertisement;
 * an undeclared one simply does not exist yet).</p>
 */
@BizModel("Refactor")
public class NopRefactorBizModel {

    static final io.nop.api.core.config.IConfigReference<Integer> CFG_MAX_SOURCE_SIZE =
            AppConfig.varRef(SourceLocation.fromClass(NopRefactorBizModel.class),
                    "nop.refactor.graphql.max-source-size", Integer.class, 1024 * 1024);

    static final io.nop.api.core.config.IConfigReference<Integer> CFG_MAX_TARGET_FILES =
            AppConfig.varRef(SourceLocation.fromClass(NopRefactorBizModel.class),
                    "nop.refactor.graphql.max-target-files", Integer.class, 512);

    @BizMutation
    public RefactorResult previewRewrite(@Name("input") RewriteInput input) {
        return rewrite(input, true);
    }

    @BizMutation
    public RefactorResult applyRewrite(@Name("input") RewriteInput input) {
        return rewrite(input, false);
    }

    /**
     * The shared execution chain (stateless re-execution): compute the full
     * edit plan first (read + lint + gates — nothing written), then land it
     * (apply only). A degraded transform generation aborts before any
     * partial write; a mid-apply IO failure surfaces the already-landed
     * files in the error message (fail-closed, never a faked success).
     */
    private RefactorResult rewrite(RewriteInput input, boolean dryRun) {
        if (input == null || input.getRulesetPrefix() == null
                || input.getRulesetPrefix().isBlank()) {
            throw new NopRefactorException(
                    "Refactor__rewrite requires a non-blank 'input.rulesetPrefix'");
        }
        if (input.getPaths() == null || input.getPaths().isEmpty()) {
            throw new NopRefactorException(
                    "Refactor__rewrite requires at least one entry in 'input.paths'");
        }

        LanguageRegistry registry = LanguageRegistry.discoverDefaults();
        RuleSetLoader.LoadedRuleSet loaded;
        try {
            loaded = new RuleSetLoader().loadRuleSet(input.getRulesetPrefix());
        } catch (NopLintException e) {
            // the loader's fail-closed face re-wrapped in the module's own
            // exception type — same message, structured for the refactor face
            throw new NopRefactorException(e.getMessage(), e);
        }
        RefactorRuleGates.verifyRewriteRuleset(loaded, registry);
        ExemptionFilter exemptions = ExemptionFilter.of(loaded.exemptions());
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD);

        List<NonApply> nonApplies = new ArrayList<>();
        List<TargetFile> targets = collectTargets(input.getPaths(), nonApplies);
        if (targets.size() > CFG_MAX_TARGET_FILES.get()) {
            throw new NopRefactorException("target set has " + targets.size()
                    + " files, over the configured cap nop.refactor.graphql.max-target-files="
                    + CFG_MAX_TARGET_FILES.get() + " (rejected before any read; fail-closed)");
        }

        // phase 1 (compute): read behind the cap, lint, gate — nothing written
        record Prepared(TargetFile file, byte[] original, List<Fix> rewrites,
                        LintLanguage language) {
        }
        List<Prepared> prepared = new ArrayList<>();
        Map<String, LintLanguage> languageByPath = new HashMap<>();
        for (TargetFile target : targets) {
            byte[] original;
            try {
                long size = Files.size(target.path());
                checkPreReadCap(size, target.path().toString());
                original = Files.readAllBytes(target.path());
            } catch (IOException e) {
                throw new NopRefactorException("target '" + target.path()
                        + "' is not readable (fail-closed, the run aborts rather than "
                        + "silently skipping a rewrite target): " + e.getMessage(), e);
            }
            if (original.length > CFG_MAX_SOURCE_SIZE.get()) {
                throw new NopRefactorException("target '" + target.path() + "' is "
                        + original.length + " bytes, over the configured cap "
                        + "nop.refactor.graphql.max-source-size=" + CFG_MAX_SOURCE_SIZE.get()
                        + " (post-read backstop; fail-closed)");
            }

            List<RuleDslModel> rules = loaded.rulesByLanguage()
                    .getOrDefault(target.languageId(), List.of());
            if (rules.isEmpty()) {
                nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE, target.path()
                        .toString(), "no rules for language '" + target.languageId()
                        + "' in ruleset '" + input.getRulesetPrefix() + "'"));
                continue;
            }
            LintLanguage language = registry.resolve(target.languageId());
            languageByPath.put(target.path().toString(), language);
            LintResult lint = engine.lint(rules, language, target.path().toString(),
                    new String(original, java.nio.charset.StandardCharsets.UTF_8));
            if (lint.stats().getTransformDegraded() > 0) {
                throw new NopRefactorException("the resource gate closed transform generation "
                        + "for '" + target.path() + "' ("
                        + lint.stats().getTransformDegraded()
                        + " edit(s) lost; a lost rewrite on the rewrite face is an abort, "
                        + "never a silent skip)");
            }

            List<Fix> rewrites = new ArrayList<>(lint.transformFixes().size());
            for (Fix rewrite : lint.transformFixes()) {
                if (exemptions.suppresses(rewrite.ruleId(), target.path())) {
                    nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE,
                            target.path().toString(), "rewrite from '" + rewrite.ruleId()
                            + "' exempted by ruleset exemption"));
                } else {
                    rewrites.add(rewrite);
                }
            }
            if (!rewrites.isEmpty()) {
                prepared.add(new Prepared(target, original, rewrites, language));
            }
        }

        // phase 2 (land): the compute phase verified every file — writes start
        // only after the last degrade/gate check has passed
        List<EditedFile> files = new ArrayList<>();
        List<FileEdit> edits = new ArrayList<>();
        List<String> landed = new ArrayList<>();
        for (Prepared item : prepared) {
            EditPlanApplier.EditPlanResult plan;
            try {
                plan = EditPlanApplier.apply(item.file().path(),
                        item.original(), item.rewrites(), item.language(), dryRun);
            } catch (RuntimeException e) {
                if (!dryRun && !landed.isEmpty()) {
                    // mid-apply failure after partial landing: enumerate what
                    // landed, the failing path, and the remaining count — the
                    // response must never hide a half-rewritten target set
                    throw new NopRefactorException("apply failed at '" + item.file().path()
                            + "' after " + landed.size() + " file(s) already landed ("
                            + String.join(", ", landed) + "); " + (prepared.size()
                            - landed.size()) + " file(s) not attempted; the failure is: "
                            + e.getMessage(), e);
                }
                throw e;
            }
            for (Fix skipped : plan.skippedEdits()) {
                nonApplies.add(new NonApply(NonApply.Reason.CONFLICT, item.file().path()
                        .toString(), "overlaps an earlier-priority edit from '"
                        + skipped.ruleId() + "'"));
            }
            if (plan.rolledBack()) {
                nonApplies.add(new NonApply(NonApply.Reason.ROLLED_BACK, item.file().path()
                        .toString(), "guard rollback: the rewrites broke the file's syntax, "
                        + "the content was restored to its pre-edit state"));
                continue;
            }
            if (plan.appliedFixes().isEmpty()) {
                continue;
            }
            for (Fix applied : plan.appliedFixes()) {
                edits.add(new FileEdit(item.file().path().toString(), applied.range(),
                        applied.description()));
            }
            files.add(new EditedFile(item.file().path().toString(), item.original(),
                    plan.finalSource(), plan.appliedEdits()));
            landed.add(item.file().path().toString());
        }

        try {
            java.util.function.Function<String, LintLanguage> resolver =
                    languageByPath::get;
            RefactorVerifier verifier = new RefactorVerifier(resolver, null, List.of());
            return verifier.assemble(!dryRun, files, edits, nonApplies);
        } catch (RuntimeException e) {
            if (!dryRun && !landed.isEmpty()) {
                throw new NopRefactorException("apply failed after " + landed.size()
                        + " file(s) already landed (" + String.join(", ", landed)
                        + "); the failure is: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * One collected rewrite target: the real disk path (the application
     * entry writes through java.nio.file.Path) and the language id its
     * extension resolved to.
     */
    record TargetFile(Path path, String languageId) {
    }

    /**
     * The write-face path grammar (plan 06 adjudication 5 — three branches,
     * each pinned): (a) namespace-prefixed paths are rejected (the VFS
     * resource namespace has no write face in v1); (b) {@code /}-rooted
     * paths are rejected for the same reason — the application entry needs a
     * disk path, which a VFS resource does not provide (stricter than the
     * read face, which resolves them through the VFS); (c) everything else
     * resolves as a disk path that must stay inside the working directory
     * after {@code toRealPath()} resolves symlinks. Directories expand
     * recursively through the extension table; unsupported extensions stay
     * out and surface as one structured out-of-scope entry.
     */
    private List<TargetFile> collectTargets(List<String> paths, List<NonApply> nonApplies) {
        List<TargetFile> targets = new ArrayList<>();
        Map<String, Integer> skipped = new HashMap<>();
        for (String raw : paths) {
            String path = raw;
            String firstSegment = path;
            int slash = path.indexOf('/');
            if (slash >= 0) {
                firstSegment = path.substring(0, slash);
            }
            if (firstSegment.indexOf(':') >= 0) {
                throw new NopRefactorException("rewrite target must not carry a resource "
                        + "namespace prefix (rejected '" + path + "'; the write face only "
                        + "accepts paths inside the working directory)");
            }
            if (path.startsWith("/")) {
                throw new NopRefactorException("rewrite target must not be '/'-rooted "
                        + "(rejected '" + path + "'; the write face writes disk paths inside "
                        + "the working directory only — stricter than the read face, which "
                        + "resolves such paths through the VFS)");
            }

            Path workdir;
            try {
                workdir = Path.of("").toRealPath();
            } catch (IOException e) {
                throw new NopRefactorException("the working directory cannot be resolved: "
                        + e.getMessage(), e);
            }
            // the containment judgment runs on the normalized absolute path so
            // a not-yet-existing target is still judged (../ escape), before
            // existence; an existing target is then re-checked through
            // toRealPath so a symlink cannot smuggle the write outside
            Path normalized = Path.of(path).toAbsolutePath().normalize();
            if (!normalized.startsWith(workdir)) {
                throw new NopRefactorException("rewrite target escapes the working "
                        + "directory: " + path);
            }
            if (!Files.exists(normalized)) {
                throw new NopRefactorException("rewrite target does not exist: " + path);
            }
            Path real;
            try {
                real = normalized.toRealPath();
            } catch (IOException e) {
                throw new NopRefactorException("rewrite target cannot be resolved: " + path
                        + " (" + e.getMessage() + ")");
            }
            if (!real.startsWith(workdir)) {
                throw new NopRefactorException("rewrite target escapes the working "
                        + "directory through a symlink: " + path);
            }

            if (Files.isDirectory(real)) {
                try (Stream<Path> walk = Files.walk(real)) {
                    walk.filter(Files::isRegularFile).forEach(child ->
                            classifyFile(child, targets, skipped));
                } catch (IOException e) {
                    throw new NopRefactorException("rewrite target directory cannot be "
                            + "expanded: " + path + " (" + e.getMessage() + ")");
                }
            } else {
                classifyFile(real, targets, skipped);
            }
        }
        if (!skipped.isEmpty()) {
            StringBuilder detail = new StringBuilder("unsupported extensions: ");
            skipped.forEach((extension, count) -> detail.append(extension).append('x')
                    .append(count).append(' '));
            nonApplies.add(new NonApply(NonApply.Reason.OUT_OF_SCOPE, "(skipped targets)",
                    detail.toString().trim()));
        }
        return targets;
    }

    private void classifyFile(Path file, List<TargetFile> targets,
                              Map<String, Integer> skipped) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "(none)" : name.substring(dot + 1);
        String languageId = io.nop.lint.core.cli.TargetScanner
                .languageIdForExtension(extension);
        if (languageId == null) {
            skipped.merge(extension, 1, Integer::sum);
            return;
        }
        targets.add(new TargetFile(file, languageId));
    }

    private static void checkPreReadCap(long bytes, String path) {
        long cap = CFG_MAX_SOURCE_SIZE.get();
        if (bytes > cap) {
            throw new NopRefactorException("rewrite target exceeds the configured cap "
                    + "nop.refactor.graphql.max-source-size=" + cap + " (file '" + path
                    + "' is " + bytes + " bytes; rejected before read)");
        }
    }
}
