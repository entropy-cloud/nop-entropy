package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.util.Set;

/**
 * Enforcement wrapper that confines a sub-agent's file access to the parent
 * agent's <b>effective (clamped)</b> allowed path roots.
 *
 * <p>When a parent constraint ({@link ParentPermissionConstraint}) is present
 * and its {@code allowedPathRoots} is PRESENT (non-null), any path that —
 * after normalization – does NOT fall under one of the allowed roots is
 * <b>denied</b> (fail-closed) with a reason that explicitly identifies "parent
 * path permission constraint", BEFORE the wrapped checker is ever consulted.
 * When the requested path IS under an allowed root, the check delegates to the
 * wrapped checker (the global deny-list and the sub-agent's own rules still
 * apply on top). When the constraint's path roots are ABSENT (null — no
 * declared path scope, backward compatible with plan 169), the wrapper is a
 * no-op pass-through.
 *
 * <p>Path-root matching: a path P is "under" a root R iff the normalized
 * absolute form of P equals R or starts with {@code R + "/"}. Normalization
 * reuses {@link DefaultPathAccessChecker#normalizePathStatic(String)} (tilde
 * expansion, backslash→slash, {@code Paths.get(p).normalize()}). Relative
 * paths are resolved against the sub-agent's declared {@code workDir} (from
 * {@link io.nop.ai.agent.model.AgentModel#getWorkDir()}) before root matching.
 *
 * <p>PRESENT with an empty root set means deny ALL paths (maximum restriction
 * — e.g. when clamping collapses to nothing during nested delegation).
 *
 * <p>Design contract (security-and-permissions.md §4.4):
 * "文件权限 = 父权限 ∩ 子配置". "未明确授权的提升行为一律拒绝".
 *
 * <p>This wrapper is applied at executor-resolution time in
 * {@link io.nop.ai.agent.engine.DefaultAgentEngine} — it does NOT mutate the
 * engine's own {@code pathAccessChecker} field. The wrapping is scoped to the
 * sub-agent execution only.
 */
public final class ParentConstrainedPathAccessChecker implements IPathAccessChecker {

    /**
     * Stable rule token tagging denials produced by this wrapper, flowing
     * through the existing audit path ({@link AuditDecision#DENY} +
     * {@code PATH_ACCESS_DENIED} event).
     */
    public static final String MATCHED_RULE = "parent_path_permission_constraint";

    private final ParentPermissionConstraint constraint;
    private final IPathAccessChecker delegate;

    /**
     * @param constraint the parent permission constraint; must not be null
     * @param delegate   the wrapped (global / sub-agent's own) path access
     *                   checker; must not be null
     */
    public ParentConstrainedPathAccessChecker(ParentPermissionConstraint constraint, IPathAccessChecker delegate) {
        if (constraint == null) {
            throw new IllegalArgumentException(
                    "ParentConstrainedPathAccessChecker: constraint must not be null");
        }
        if (delegate == null) {
            throw new IllegalArgumentException(
                    "ParentConstrainedPathAccessChecker: delegate checker must not be null");
        }
        this.constraint = constraint;
        this.delegate = delegate;
    }

    public ParentPermissionConstraint getConstraint() {
        return constraint;
    }

    public IPathAccessChecker getDelegate() {
        return delegate;
    }

    @Override
    public PathAccessResult checkAccess(String path, AgentExecutionContext ctx) {
        Set<String> roots = constraint.getAllowedPathRoots();

        // ABSENT (null roots) → pass-through, backward compatible with plan 169
        if (roots == null) {
            return delegate.checkAccess(path, ctx);
        }

        // null/blank path → delegate to wrapped checker (same as
        // DefaultPathAccessChecker which allows null/blank paths)
        if (path == null || path.trim().isEmpty()) {
            return delegate.checkAccess(path, ctx);
        }

        // Resolve relative paths against the sub-agent's workDir, then normalize
        String normalized = resolveAndNormalize(path, ctx);

        // Normalization failed → deny (fail-closed). The wrapped checker would
        // also deny such paths, but we deny first with the parent-constraint
        // reason rather than silently passing a suspicious path through.
        if (normalized == null) {
            return deny(path);
        }

        // PRESENT roots: check if the normalized path is under any allowed root
        if (isUnderAnyRoot(normalized, roots)) {
            return delegate.checkAccess(path, ctx);
        }

        // Not under any root → deny (fail-closed). PRESENT({}) reaches here for
        // every path (maximum restriction).
        return deny(path);
    }

    private PathAccessResult deny(String path) {
        String parentAgent = constraint.getParentAgentName();
        return PathAccessResult.deny(
                "denied by parent path permission constraint: path '" + path
                        + "' outside parent agent '" + parentAgent + "' allowed roots",
                MATCHED_RULE);
    }

    /**
     * Resolve a relative path against the sub-agent's declared {@code workDir}
     * (from the execution context's agent model), then normalize using the
     * shared {@link DefaultPathAccessChecker#normalizePathStatic(String)}
     * algorithm. Tilde paths and absolute paths are normalized as-is.
     */
    private String resolveAndNormalize(String path, AgentExecutionContext ctx) {
        String p = path.replace("\\", "/");

        boolean isTilde = p.startsWith("~");
        boolean isAbsolute = p.startsWith("/")
                || (p.length() >= 2 && p.charAt(1) == ':');

        if (!isTilde && !isAbsolute) {
            String workDir = resolveWorkDir(ctx);
            if (workDir != null && !workDir.trim().isEmpty()) {
                String wd = workDir.replace("\\", "/");
                p = wd.endsWith("/") ? wd + p : wd + "/" + p;
            }
        }

        return DefaultPathAccessChecker.normalizePathStatic(p);
    }

    private String resolveWorkDir(AgentExecutionContext ctx) {
        if (ctx == null || ctx.getAgentModel() == null) {
            return null;
        }
        return ctx.getAgentModel().getWorkDir();
    }

    /**
     * A path P is "under" a root R iff the normalized form of P equals the
     * normalized form of R, or starts with {@code normalizedRoot + "/"}.
     * Roots are normalized defensively (idempotent) to handle any non-normalized
     * root values that may have been propagated.
     */
    private boolean isUnderAnyRoot(String normalizedPath, Set<String> roots) {
        if (roots.isEmpty()) {
            // PRESENT({}) = deny all paths (maximum restriction)
            return false;
        }

        for (String root : roots) {
            if (root == null || root.trim().isEmpty()) {
                continue;
            }
            String normalizedRoot = DefaultPathAccessChecker.normalizePathStatic(root);
            if (normalizedRoot == null) {
                continue;
            }

            if (normalizedPath.equals(normalizedRoot)) {
                return true;
            }
            String rootWithSlash = normalizedRoot.endsWith("/")
                    ? normalizedRoot : normalizedRoot + "/";
            if (normalizedPath.startsWith(rootWithSlash)) {
                return true;
            }
        }
        return false;
    }
}
