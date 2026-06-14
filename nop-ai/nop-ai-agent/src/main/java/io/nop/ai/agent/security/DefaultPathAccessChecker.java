package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;

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

        return PathAccessResult.allow();
    }

    private String extractFileName(String normalizedPath) {
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
