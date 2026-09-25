package io.nop.lint.graphql;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.lint.core.NopLintException;
import io.nop.lint.core.cli.RuleSetLoader;
import io.nop.lint.core.cli.TargetScanner;
import io.nop.lint.core.engine.Diagnostic;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 * The GraphQL surface over the fast-profile lint engine (roadmap item 38,
 * design 03 §2.3): {@code Lint__checkSource}, {@code Lint__checkFile} and
 * {@code Lint__listRules}. The service holds the engine lifecycle (plan
 * Decision 6): one {@link LintEngine} over the {@link LintProfile#FAST}
 * profile with the rule set loaded once at first use — per-request YAML
 * rescans would dominate the fast profile's latency budget; rule changes
 * require a restart (the v1 contract, recorded in the design annotation).
 *
 * <p>The security boundaries are fail-closed (plan Decision 2/3/5): the
 * source (and the {@code checkFile} content alike) is capped by the
 * {@code nop.lint.graphql.max-source-size} configuration; unknown rule ids
 * and unknown languages abort; the {@code checkFile} path grammar rejects
 * every namespace-prefixed VFS path (the {@code file:} namespace handler
 * has no default allowlist — admitting it would be arbitrary file read),
 * resolves plain {@code /}-rooted paths through the VFS, and confines bare
 * disk paths to the working directory after {@code toRealPath()} symlink
 * resolution. Nothing in the response face carries fix payloads (plan
 * Decision 4): the fast profile's fix carrier does not cross the service
 * boundary. The error face is the message-mode {@link NopLintException}
 * (plan Decision 3 R2 m5) — tests assert the message content.</p>
 */
@BizModel("Lint")
public class NopLintBizModel {

    static final IConfigReference<Integer> CFG_MAX_SOURCE_SIZE = AppConfig.varRef(
            io.nop.api.core.util.SourceLocation.fromClass(NopLintBizModel.class),
            "nop.lint.graphql.max-source-size", Integer.class, 1024 * 1024);

    private volatile Runtime runtime;

    /**
     * One engine + one rule set per service instance (plan Decision 6):
     * built lazily on the first query, reused afterwards.
     */
    private Runtime runtime() {
        Runtime rt = this.runtime;
        if (rt == null) {
            synchronized (this) {
                rt = this.runtime;
                if (rt == null) {
                    LanguageRegistry registry = LanguageRegistry.discoverDefaults();
                    RuleSetLoader.LoadedRuleSet loaded = new RuleSetLoader()
                            .loadRuleSet(RuleSetLoader.DEFAULT_RULES_PREFIX);
                    rt = new Runtime(registry, new LintEngine(registry, LintProfile.FAST), loaded);
                    this.runtime = rt;
                }
            }
        }
        return rt;
    }

    /**
     * Lints one in-memory source string under the fast profile. Unknown
     * languages fail through the registry's own fail-closed resolve.
     */
    @BizQuery
    public LintCheckResult checkSource(@Name("source") String source,
                                       @Name("language") String language,
                                       @Optional @Name("rules") List<String> rules,
                                       IServiceContext ctx) {
        if (source == null || language == null || language.isBlank()) {
            throw new NopLintException(
                    "Lint__checkSource requires non-blank 'source' and 'language'");
        }
        checkSourceCap(source.length());
        Runtime rt = runtime();
        String languageId = normalizeLanguage(language);
        List<RuleDslModel> selected = rt.select(languageId, rules);
        return lint(rt, languageId, selected, source);
    }

    /**
     * Lints one file behind the plan-Decision-5 path grammar: namespace
     * paths are rejected outright, {@code /}-rooted paths go through the
     * VFS, and bare disk paths must stay inside the working directory after
     * symlink resolution. The source cap gates the READ (byte-level
     * pre-check) and stays on the read content as the character-level
     * backstop — same cap as {@code checkSource}.
     */
    @BizQuery
    public LintCheckResult checkFile(@Name("path") String path,
                                     @Optional @Name("rules") List<String> rules,
                                     IServiceContext ctx) {
        if (path == null || path.isBlank()) {
            throw new NopLintException("Lint__checkFile requires a non-blank 'path'");
        }
        String source = readControlled(path);
        checkSourceCap(source.length());

        Runtime rt = runtime();
        String name = fileNameOf(path);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? TargetScanner.NO_EXTENSION
                : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        String languageId = TargetScanner.languageIdForExtension(extension);
        List<RuleDslModel> selected = rt.select(languageId, rules);
        return lint(rt, languageId, selected, source);
    }

    /**
     * The rule library's metadata face (severity from the top-level slot —
     * the diagnostic payload authority; null metadata maps to safe
     * defaults, plan Decision 4 R2 m4).
     */
    @BizQuery
    public List<LintRuleView> listRules(@Optional @Name("language") String language,
                                        IServiceContext ctx) {
        Runtime rt = runtime();
        List<LintRuleView> views = new ArrayList<>();
        for (List<RuleDslModel> rules : rt.loaded.rulesByLanguage().values()) {
            for (RuleDslModel rule : rules) {
                if (language != null && !language.isBlank()
                        && !normalizeLanguage(language).equals(normalizeLanguage(rule.getLanguage()))) {
                    continue;
                }
                views.add(new LintRuleView(rule.getId(), rule.getSeverity(), rule.getMessage(),
                        rule.getMetadata() == null ? "" : rule.getMetadata().getCategory(),
                        rule.getMetadata() != null && rule.getMetadata().isAutoFixable()));
            }
        }
        views.sort((a, b) -> a.id().compareTo(b.id()));
        return views;
    }

    private LintCheckResult lint(Runtime rt, String languageId, List<RuleDslModel> rules,
                                 String source) {
        LintResult result = rt.engine.lint(rules, languageId, "graphql-check", source);
        LineIndex lines = new LineIndex(source);
        List<LintDiagnosticView> views = new ArrayList<>(result.diagnostics().size());
        int error = 0;
        int warning = 0;
        int info = 0;
        int hint = 0;
        int other = 0;
        for (Diagnostic diagnostic : result.diagnostics()) {
            views.add(new LintDiagnosticView(diagnostic.ruleId(), diagnostic.severity(),
                    diagnostic.message(), lines.startLine(diagnostic.range()),
                    lines.endLine(diagnostic.range())));
            switch (diagnostic.severity()) {
                case "error" -> error++;
                case "warning" -> warning++;
                case "info" -> info++;
                case "hint" -> hint++;
                default -> other++;
            }
        }
        return new LintCheckResult(views, error, warning, info, hint, other, views.size());
    }

    /**
     * The path grammar (plan Decision 5): (a) any namespace-prefixed path —
     * a {@code ':'} in the first segment — is rejected before it can reach a
     * namespace handler (the {@code file:} handler admits arbitrary
     * absolute paths with no default allowlist); (b) {@code /}-rooted plain
     * paths resolve through the VFS; (c) everything else resolves as a disk
     * path that must stay inside the working directory after
     * {@code toRealPath()} resolves symlinks.
     */
    private String readControlled(String path) {
        String firstSegment = path;
        int slash = path.indexOf('/');
        if (slash >= 0) {
            firstSegment = path.substring(0, slash);
        }
        if (firstSegment.indexOf(':') >= 0) {
            throw new NopLintException("checkFile path must not carry a resource namespace"
                    + " prefix (rejected '" + path + "'; only plain VFS paths and paths inside"
                    + " the working directory are allowed)");
        }

        if (path.startsWith("/")) {
            IResource resource = VirtualFileSystem.instance().getResource(path);
            if (resource == null || !resource.exists() || resource.isDirectory()) {
                throw new NopLintException("checkFile path does not resolve to a VFS file: "
                        + path);
            }
            // the cap must gate the READ, not follow it: a length-known
            // resource is rejected before its content occupies memory.
            // length() < 0 (unknown) falls through to the post-read
            // character check, which stays as the backstop
            if (resource.length() >= 0) {
                checkPreReadCap(resource.length(), path);
            }
            return resource.readText(StandardCharsets.UTF_8.name());
        }

        try {
            Path real = Path.of(path).toRealPath();
            Path workdir = Path.of("").toRealPath();
            if (!real.startsWith(workdir)) {
                throw new NopLintException("checkFile path escapes the working directory: "
                        + path);
            }
            checkPreReadCap(Files.size(real), path);
            return Files.readString(real, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NopLintException("checkFile path cannot be read: " + path
                    + " (" + e.getMessage() + ")");
        }
    }

    /**
     * The byte-granularity pre-read gate of the source cap (plan 07, audit
     * finding C6): oversized targets are rejected BEFORE their content is
     * read into memory — the cap's whole purpose. Bytes are never fewer than
     * characters, so this is strictly stricter than the post-read character
     * check, which {@code checkFile} retains as the backstop for
     * length-unknown resources.
     */
    private static void checkPreReadCap(long bytes, String path) {
        long cap = CFG_MAX_SOURCE_SIZE.get();
        if (bytes > cap) {
            throw new NopLintException("checkFile target exceeds the configured cap "
                    + "nop.lint.graphql.max-source-size=" + cap + " (file '" + path
                    + "' is " + bytes + " bytes; rejected before read)");
        }
    }

    private static void checkSourceCap(int length) {
        int cap = CFG_MAX_SOURCE_SIZE.get();
        if (length > cap) {
            throw new NopLintException("source exceeds the configured cap "
                    + "nop.lint.graphql.max-source-size=" + cap + " (got " + length
                    + " characters)");
        }
    }

    private static String fileNameOf(String path) {
        String withoutSlash = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = withoutSlash.lastIndexOf('/');
        return slash < 0 ? withoutSlash : withoutSlash.substring(slash + 1);
    }

    private static String normalizeLanguage(String language) {
        return language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The lazily-built engine bundle: registry, fast engine, loaded rules.
     */
    private static final class Runtime {
        final LanguageRegistry registry;
        final LintEngine engine;
        final RuleSetLoader.LoadedRuleSet loaded;

        Runtime(LanguageRegistry registry, LintEngine engine, RuleSetLoader.LoadedRuleSet loaded) {
            this.registry = registry;
            this.engine = engine;
            this.loaded = loaded;
        }

        /**
         * The rule-id whitelist (plan Decision 4): unknown ids fail naming
         * the offending id; a selection keeps the loaded rule order.
         */
        List<RuleDslModel> select(String languageId, List<String> requested) {
            List<RuleDslModel> forLanguage = loaded.rulesByLanguage().getOrDefault(languageId,
                    List.of());
            if (requested == null || requested.isEmpty()) {
                return forLanguage;
            }
            Set<String> loadedIds = new HashSet<>();
            loaded.rulesByLanguage().values().forEach(rs -> rs.forEach(r -> loadedIds.add(r.getId())));
            for (String id : requested) {
                if (!loadedIds.contains(id)) {
                    throw new NopLintException("unknown rule id '" + id + "' (the loaded rule"
                            + " library declares: " + loadedIds + ")");
                }
            }
            Set<String> wanted = new HashSet<>(requested);
            List<RuleDslModel> kept = new ArrayList<>(forLanguage.size());
            for (RuleDslModel rule : forLanguage) {
                if (wanted.contains(rule.getId())) {
                    kept.add(rule);
                }
            }
            return kept;
        }
    }
}
