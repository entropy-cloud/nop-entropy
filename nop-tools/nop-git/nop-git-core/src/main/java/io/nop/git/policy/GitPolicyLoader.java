package io.nop.git.policy;

import io.nop.git.api.NopGitException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code policy.yaml} layers (project root, user home) into typed
 * {@link GitPolicyConfig}s. Malformed configuration fails loudly
 * (NopGitException), never silently ignored (plan 2578 no-silent-noop rule).
 */
public final class GitPolicyLoader {

    private GitPolicyLoader() {
    }

    /**
     * @return the configs of the layers that exist, lowest priority first
     * (user home before project root); empty when no policy file exists
     */
    public static List<GitPolicyConfig> loadLayers(Path projectRoot) {
        return loadLayers(projectRoot,
                Path.of(System.getProperty("user.home"), ".nop-git", "policy.yaml"));
    }

    /**
     * Layer loading with an explicit user-level file (test isolation: never
     * touch the developer's real ~/.nop-git).
     */
    public static List<GitPolicyConfig> loadLayers(Path projectRoot, Path userFile) {
        List<GitPolicyConfig> layers = new ArrayList<>();
        if (Files.isRegularFile(userFile))
            layers.add(load(userFile));
        if (projectRoot != null) {
            Path projectFile = projectRoot.resolve(".nop-git").resolve("policy.yaml");
            if (Files.isRegularFile(projectFile))
                layers.add(load(projectFile));
        }
        return layers;
    }

    public static GitPolicyConfig load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            Yaml yaml = new Yaml();
            Object root = yaml.load(in);
            if (root == null)
                throw new NopGitException("policy file is empty: " + file);
            if (!(root instanceof Map<?, ?> map))
                throw new NopGitException("policy file root must be a mapping: " + file);
            return fromMap(castMap(map), file.toString());
        } catch (IOException e) {
            throw new NopGitException("cannot read policy file " + file + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new NopGitException("malformed policy file " + file + ": " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static GitPolicyConfig fromMap(Map<String, Object> map, String source) {
        GitPolicyMode mode = GitPolicyMode.OBSERVE;
        if (map.get("mode") != null)
            mode = parseMode(str(map.get("mode")), source);

        String defaultEffect = map.get("default_effect") == null
                ? null : str(map.get("default_effect"));
        String auditLog = map.get("audit_log") == null ? null : str(map.get("audit_log"));
        Integer reviewTimeout = map.get("review_timeout_seconds") == null
                ? null : ((Number) map.get("review_timeout_seconds")).intValue();
        String version = map.get("git_version_string") == null
                ? null : str(map.get("git_version_string"));

        GitPolicyConfig.Operations operations = map.get("operations") == null
                ? null : parseOperations(castMap((Map<?, ?>) map.get("operations")), source);

        List<GitPolicyConfig.OptionRule> optionRules = new ArrayList<>();
        if (map.get("option_rules") != null) {
            for (Object o : (List<Object>) map.get("option_rules")) {
                Map<String, Object> m = castMap((Map<?, ?>) o);
                optionRules.add(new GitPolicyConfig.OptionRule(
                        str(m.get("operation")),
                        strings(m.get("deny_flags")),
                        strings(m.get("review_flags"))));
            }
        }

        GitPolicyConfig.ProtectedBranches protectedBranches = null;
        if (map.get("protected_branches") != null) {
            Map<String, Object> m = castMap((Map<?, ?>) map.get("protected_branches"));
            GitPolicyConfig.BranchRules rules = null;
            if (m.get("rules") != null) {
                Map<String, Object> r = castMap((Map<?, ?>) m.get("rules"));
                rules = new GitPolicyConfig.BranchRules(
                        bool(r.get("allow_direct_push")),
                        bool(r.get("allow_force_push")),
                        bool(r.get("allow_delete")));
            }
            protectedBranches = new GitPolicyConfig.ProtectedBranches(
                    strings(m.get("patterns")), rules);
        }

        List<GitPolicyConfig.PathRule> pathRules = new ArrayList<>();
        if (map.get("path_rules") != null) {
            for (Object o : (List<Object>) map.get("path_rules")) {
                Map<String, Object> m = castMap((Map<?, ?>) o);
                pathRules.add(new GitPolicyConfig.PathRule(
                        strings(m.get("operation")),
                        strings(m.get("deny_paths")),
                        strings(m.get("review_paths"))));
            }
        }

        GitPolicyConfig.RemoteRules remoteRules = null;
        if (map.get("remote_rules") != null) {
            Map<String, Object> m = castMap((Map<?, ?>) map.get("remote_rules"));
            remoteRules = new GitPolicyConfig.RemoteRules(
                    strings(m.get("allow_remotes")),
                    strings(m.get("deny_protocols")),
                    strings(m.get("allow_url_patterns")));
        }

        List<GitPolicyConfig.Scope> scopes = new ArrayList<>();
        if (map.get("scopes") != null) {
            for (Object o : (List<Object>) map.get("scopes")) {
                Map<String, Object> m = castMap((Map<?, ?>) o);
                scopes.add(new GitPolicyConfig.Scope(
                        str(m.get("match")),
                        m.get("operations") == null
                                ? null : parseOperations(castMap((Map<?, ?>) m.get("operations")),
                                source)));
            }
        }

        return new GitPolicyConfig(mode, defaultEffect, auditLog, reviewTimeout, version,
                operations, optionRules, protectedBranches, pathRules, remoteRules, scopes);
    }

    private static GitPolicyConfig.Operations parseOperations(Map<String, Object> m, String source) {
        return new GitPolicyConfig.Operations(
                strings(m.get("allow")),
                strings(m.get("deny")),
                strings(m.get("review")));
    }

    private static GitPolicyMode parseMode(String value, String source) {
        try {
            return GitPolicyMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NopGitException("malformed policy file " + source
                    + ": unknown mode '" + value + "' (expected observe|enforce)");
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static Boolean bool(Object value) {
        return value == null ? null : Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        if (value == null)
            return List.of();
        List<String> result = new ArrayList<>();
        for (Object o : (List<Object>) value) {
            result.add(o.toString());
        }
        return result;
    }
}
