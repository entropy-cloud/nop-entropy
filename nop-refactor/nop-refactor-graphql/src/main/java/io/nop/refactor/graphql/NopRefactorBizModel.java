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
import io.nop.lint.core.suppress.ExemptionFilter;
import io.nop.refactor.core.NonApply;
import io.nop.refactor.core.NopRefactorException;
import io.nop.refactor.core.RefactorResult;
import io.nop.refactor.core.operation.PreparedTarget;
import io.nop.refactor.core.operation.RefactorOperationRunner;
import io.nop.refactor.core.operation.RewriteOperation;
import io.nop.refactor.core.operation.RewriteRequest;

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
 * targets behind the write-face path grammar, and read them behind the
 * fixed STANDARD profile's engine — then delegate to the WI9 operation
 * framework, whose single plan/apply/verify path lands through the WI4
 * mechanical core and assembles through the WI5 verifier. Apply re-computes
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
     * The shared execution chain (stateless re-execution): the face prepares
     * — input validation, ruleset load, STANDARD engine, write-face target
     * collection behind the caps — then delegates to the WI9 operation
     * framework, whose single plan/apply/verify path (RewriteOperation
     * through {@link RefactorOperationRunner}) this face shares with the
     * CLI and, from WI10/WI11, the rename operations. A degraded transform
     * generation aborts before any partial write; a mid-apply IO failure
     * surfaces the already-landed files in the error message (fail-closed,
     * never a faked success).
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
        ExemptionFilter exemptions = ExemptionFilter.of(loaded.exemptions());
        LintEngine engine = new LintEngine(registry, LintProfile.STANDARD);

        List<NonApply> nonApplies = new ArrayList<>();
        List<TargetFile> targets = collectTargets(input.getPaths(), nonApplies);
        if (targets.size() > CFG_MAX_TARGET_FILES.get()) {
            throw new NopRefactorException("target set has " + targets.size()
                    + " files, over the configured cap nop.refactor.graphql.max-target-files="
                    + CFG_MAX_TARGET_FILES.get() + " (rejected before any read; fail-closed)");
        }

        // the face reads behind its caps; the operation plans, the framework
        // lands and verifies — one path for every consumer
        List<PreparedTarget> prepared = new ArrayList<>(targets.size());
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
            prepared.add(new PreparedTarget(target.path(), target.languageId(), original));
        }

        RewriteRequest request = new RewriteRequest(loaded, exemptions, engine, registry,
                prepared, nonApplies, input.getRulesetPrefix());
        return RefactorOperationRunner.INSTANCE.run(RewriteOperation.INSTANCE, request, dryRun);
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
