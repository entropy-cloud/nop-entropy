package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultPathAccessChecker implements IPathAccessChecker {

    private static final String HOME = System.getProperty("user.home", "");

    private static final Set<String> SENSITIVE_FILENAMES = Set.of(
            "id_rsa",
            "id_ed25519",
            ".netrc",
            ".bash_history",
            ".zsh_history"
    );

    private static final String[] SENSITIVE_PREFIX_PATTERNS;

    static {
        List<String> prefixes = new ArrayList<>();
        if (!HOME.isEmpty()) {
            prefixes.add(HOME + "/.ssh/");
            prefixes.add(HOME + "/.aws/");
            prefixes.add(HOME + "/.azure/");
            prefixes.add(HOME + "/.config/gcloud/");
            prefixes.add(HOME + "/.kube/");
        }
        prefixes.add("/etc/");
        prefixes.add("/boot/");
        prefixes.add("/sys/");
        prefixes.add("/proc/");
        prefixes.add("/root/");
        SENSITIVE_PREFIX_PATTERNS = prefixes.toArray(new String[0]);
    }

    @Override
    public PathAccessResult checkAccess(String path, AgentExecutionContext ctx) {
        if (path == null || path.trim().isEmpty()) {
            return PathAccessResult.allow();
        }

        String normalized = normalizePath(path);
        if (normalized == null) {
            return PathAccessResult.deny("Path normalization failed (contains invalid traversal): " + path);
        }

        if (containsTraversal(path)) {
            return PathAccessResult.denyByRule("path_traversal_defense", path);
        }

        String lower = normalized.toLowerCase();

        for (String prefix : SENSITIVE_PREFIX_PATTERNS) {
            if (lower.startsWith(prefix.toLowerCase())) {
                return PathAccessResult.denyByRule("sensitive_path_prefix", normalized);
            }
        }

        String fileName = extractFileName(lower);
        if (fileName != null) {
            if (fileName.equals(".env") || fileName.startsWith(".env.")) {
                return PathAccessResult.denyByRule("sensitive_path_env_file", normalized);
            }
            if (SENSITIVE_FILENAMES.contains(fileName)) {
                return PathAccessResult.denyByRule("sensitive_path_filename", normalized);
            }
        }

        // Plan 270 (finding 13-1): symlink protection. A symlink whose
        // lexical name looks safe but which resolves to a restricted
        // directory (e.g. /tmp/link -> ~/.ssh) must not bypass the checks
        // above. Resolve the real path (symlinks followed) and re-apply the
        // sensitive-prefix / sensitive-filename checks against it.
        String realPath = resolveSymlinkRealPath(normalized);
        if (realPath == null) {
            // Fail-closed (Minimum Rules #24): when the real path cannot be
            // resolved at all, never silently allow a path whose true target
            // is unknown.
            return PathAccessResult.deny(
                    "Path real-path resolution failed (symlink check): " + normalized);
        }
        PathAccessResult symlinkDeny = checkSensitiveRealPath(realPath);
        if (symlinkDeny != null) {
            return symlinkDeny;
        }

        return PathAccessResult.allow();
    }

    /**
     * Apply the sensitive-prefix and sensitive-filename checks against the
     * symlink-resolved real path. Returns a denial if the real path hits a
     * sensitive rule, or {@code null} if the real path is allowed.
     */
    private static PathAccessResult checkSensitiveRealPath(String realPath) {
        String realLower = realPath.toLowerCase();
        for (String prefix : SENSITIVE_PREFIX_PATTERNS) {
            if (realLower.startsWith(prefix.toLowerCase())) {
                return PathAccessResult.denyByRule("sensitive_path_symlink", realPath);
            }
        }
        String realFileName = extractFileName(realLower);
        if (realFileName != null) {
            if (realFileName.equals(".env") || realFileName.startsWith(".env.")) {
                return PathAccessResult.denyByRule("sensitive_path_env_file", realPath);
            }
            if (SENSITIVE_FILENAMES.contains(realFileName)) {
                return PathAccessResult.denyByRule("sensitive_path_filename", realPath);
            }
        }
        return null;
    }

    /**
     * Resolve the symlink-free real path of {@code normalized} (design §4.3
     * "符号链接绕过" defense, plan 270 finding 13-1).
     *
     * <p>Uses {@link Path#toRealPath()} so symlinks are followed to their
     * targets. When the full path does not exist (the common case for a file
     * an agent is about to create), {@code toRealPath} throws
     * {@link IOException}; in that case the real path of the deepest
     * <em>existing</em> ancestor is resolved and the non-existent tail is
     * re-appended. This still catches symlinks pointing at restricted
     * directories while allowing brand-new files inside legitimate
     * directories.
     *
     * <p>Returns {@code null} when no existing ancestor can be resolved at
     * all — the caller treats this as fail-closed (deny), never as a silent
     * allow.
     *
     * <p>Protected so focused tests can simulate an unresolvable path
     * (i.e. {@code toRealPath()} throwing {@link IOException} on every
     * component) by overriding this method to return {@code null} and
     * asserting the caller denies — no mocking framework required.
     */
    protected String resolveSymlinkRealPath(String normalized) {
        Path p = Paths.get(normalized);
        try {
            return p.toRealPath().toString().replace("\\", "/");
        } catch (IOException fullPathMissing) {
            return resolveViaExistingAncestor(p);
        }
    }

    private static String resolveViaExistingAncestor(Path p) {
        Path absolute = p.isAbsolute() ? p : p.toAbsolutePath();
        Path root = absolute.getRoot();
        if (root == null) {
            return null;
        }
        // Walk from the root, capturing the deepest component that exists.
        Path existingAncestor = root;
        for (Path component : absolute) {
            Path candidate = existingAncestor.resolve(component);
            if (Files.exists(candidate)) {
                existingAncestor = candidate;
            } else {
                break;
            }
        }
        try {
            String realAncestor = existingAncestor.toRealPath().toString().replace("\\", "/");
            String tail = existingAncestor.relativize(absolute).toString().replace("\\", "/");
            return tail.isEmpty() ? realAncestor : realAncestor + "/" + tail;
        } catch (IOException e) {
            return null;
        }
    }

    private static String extractFileName(String normalizedPath) {
        int lastSlash = normalizedPath.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < normalizedPath.length() - 1) {
            return normalizedPath.substring(lastSlash + 1);
        }
        if (lastSlash < 0 && !normalizedPath.isEmpty()) {
            return normalizedPath;
        }
        return null;
    }

    private boolean containsTraversal(String path) {
        String[] parts = path.replace("\\", "/").split("/");
        for (String part : parts) {
            if ("..".equals(part)) {
                return true;
            }
        }
        return false;
    }

    private String normalizePath(String path) {
        return normalizePathStatic(path);
    }

    /**
     * Normalize a path string: backslash → forward slash, tilde expansion,
     * {@code Paths.get(p).normalize()}, return forward-slash form. Returns
     * {@code null} when normalization fails (e.g. invalid traversal that
     * escapes normalization, or tilde expansion with no known home dir).
     *
     * <p>Public static so that
     * {@link ParentConstrainedPathAccessChecker} and
     * {@link io.nop.ai.agent.engine.ReActAgentExecutor} can reuse the exact
     * same normalization algorithm for parent-root matching without
     * duplicating logic (design §4.4 path-permission inheritance).
     */
    public static String normalizePathStatic(String path) {
        String p = path.replace("\\", "/");

        if (p.startsWith("~")) {
            if (HOME.isEmpty()) {
                return null;
            }
            if (p.equals("~") || p.startsWith("~/")) {
                p = HOME + p.substring(1);
            }
        }

        try {
            Path normalized = Paths.get(p).normalize();
            return normalized.toString().replace("\\", "/");
        } catch (Exception e) {
            return null;
        }
    }
}
