package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Functional {@link ILevelHintsProducer} used as the shipped default. Produces
 * semantically-distinct hints (not an all-false stub):
 *
 * <ul>
 *   <li>{@code trustedSource} — delegates to {@link IContentTrustEvaluator} with
 *       {@link ContentOrigin#AGENT_GENERATED} (the agent's own reasoning chain
 *       produced the tool call). Under {@link DefaultContentTrustEvaluator} this
 *       is {@code true} — a conservative trusted default, overridable by
 *       registering a custom evaluator.</li>
 *   <li>{@code writesOutsideWorkspace} — extracts path values from the
 *       tool-call arguments via {@link ToolPathArgKeys}, resolves each against
 *       the working directory (or JVM CWD when workDir is absent), and returns
 *       {@code true} when any path resolves outside the workspace.</li>
 *   <li>{@code needsNetwork} — {@code true} when the tool name matches a known
 *       network-tool category (web fetch / http request / network fetch / web
 *       search / curl).</li>
 *   <li>{@code highImpact} — {@code true} when the tool name matches a known
 *       high-impact category (shell / code exec / file delete / rm / git push /
 *       git reset / db drop / system).</li>
 *   <li>{@code crossesTrustBoundary} — conservatively {@code false}; precise
 *       evaluation is a future enhancement (needs tool metadata or
 *       cross-system call tracing).</li>
 * </ul>
 *
 * <p>Tool-name matching normalises both the input name and the category sets to
 * a dotted lower-case form, so {@code shell_exec}, {@code shell.exec}, and
 * {@code SHELL_EXEC} all match the high-impact category.
 *
 * <p><b>Robustness</b>: tolerates {@code null} tool name, {@code null} / empty
 * arguments, and {@code null} workDir — returns a conservative
 * {@link LevelHints} rather than throwing.
 */
public class DefaultLevelHintsProducer implements ILevelHintsProducer {

    private static final Set<String> NETWORK_TOOLS = Set.of(
            "web.fetch", "http.request", "network.fetch", "web.search", "curl", "http.get", "http.post"
    );

    private static final Set<String> HIGH_IMPACT_TOOLS = Set.of(
            "shell.exec", "code.exec", "bash", "sh", "file.delete", "rm", "rmdir",
            "git.push", "git.reset", "db.drop", "system"
    );

    private final IContentTrustEvaluator contentTrustEvaluator;

    public DefaultLevelHintsProducer() {
        this(new DefaultContentTrustEvaluator());
    }

    public DefaultLevelHintsProducer(IContentTrustEvaluator contentTrustEvaluator) {
        this.contentTrustEvaluator = contentTrustEvaluator != null
                ? contentTrustEvaluator
                : new DefaultContentTrustEvaluator();
    }

    @Override
    public LevelHints produce(String toolName, Map<String, Object> arguments, File workDir,
                              AgentExecutionContext ctx) {
        boolean trustedSource = evaluateTrustedSource(ctx);
        boolean writesOutsideWorkspace = evaluateWritesOutside(arguments, workDir);
        boolean needsNetwork = matchesToolCategory(toolName, NETWORK_TOOLS);
        boolean highImpact = matchesToolCategory(toolName, HIGH_IMPACT_TOOLS);
        // Conservative: no reliable runtime heuristic for trust-boundary crossing.
        boolean crossesTrustBoundary = false;

        return new LevelHints(trustedSource, writesOutsideWorkspace, crossesTrustBoundary,
                needsNetwork, highImpact);
    }

    private boolean evaluateTrustedSource(AgentExecutionContext ctx) {
        // The tool call originates from the agent's own reasoning chain.
        return contentTrustEvaluator.isTrustedSource(ContentOrigin.AGENT_GENERATED, ctx);
    }

    private boolean evaluateWritesOutside(Map<String, Object> arguments, File workDir) {
        if (arguments == null || arguments.isEmpty()) {
            return false;
        }
        File base = workDir != null ? workDir : new File(".").getAbsoluteFile();
        String normalizedBase = normalizeAbsolutePath(base);
        if (normalizedBase == null) {
            return false;
        }
        String baseWithSlash = normalizedBase.endsWith("/") ? normalizedBase : normalizedBase + "/";

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            if (!ToolPathArgKeys.KEYS.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (!(value instanceof String)) {
                continue;
            }
            String pathValue = (String) value;
            if (pathValue == null || pathValue.trim().isEmpty()) {
                continue;
            }
            if (isOutsideBase(pathValue, base, normalizedBase, baseWithSlash)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOutsideBase(String pathValue, File base, String normalizedBase, String baseWithSlash) {
        File resolved = new File(pathValue);
        if (!resolved.isAbsolute()) {
            resolved = new File(base, pathValue);
        }
        String normalizedPath = normalizeAbsolutePath(resolved);
        if (normalizedPath == null) {
            return false;
        }
        if (normalizedPath.equals(normalizedBase)) {
            // The workspace directory itself is not "outside".
            return false;
        }
        return !normalizedPath.startsWith(baseWithSlash);
    }

    private static String normalizeAbsolutePath(File file) {
        File absolute = file.getAbsoluteFile();
        return DefaultPathAccessChecker.normalizePathStatic(absolute.getAbsolutePath());
    }

    private static boolean matchesToolCategory(String toolName, Set<String> category) {
        if (toolName == null || toolName.isEmpty()) {
            return false;
        }
        String normalized = normalizeToolName(toolName);
        return category.contains(normalized);
    }

    private static String normalizeToolName(String toolName) {
        return toolName.replace('_', '.').toLowerCase(Locale.ROOT);
    }
}
