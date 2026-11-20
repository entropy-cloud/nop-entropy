package io.nop.git.policy;

import java.util.List;
import java.util.Map;

/**
 * Typed view of one merged policy configuration (design 03 section 2 YAML
 * contract). Field semantics follow the design doc; the loader maps the YAML
 * keys 1:1.
 */
public record GitPolicyConfig(
        GitPolicyMode mode,
        String defaultEffect,          // allow | deny | review
        String auditLog,               // relative to the repository root
        Integer reviewTimeoutSeconds,
        String gitVersionString,
        Operations operations,
        List<OptionRule> optionRules,
        ProtectedBranches protectedBranches,
        List<PathRule> pathRules,
        RemoteRules remoteRules,
        List<Scope> scopes) {

    public record Operations(List<String> allow, List<String> deny, List<String> review) {
    }

    public record OptionRule(String operation, List<String> denyFlags, List<String> reviewFlags) {
    }

    public record ProtectedBranches(List<String> patterns, BranchRules rules) {
    }

    public record BranchRules(Boolean allowDirectPush, Boolean allowForcePush, Boolean allowDelete) {
    }

    public record PathRule(List<String> operations, List<String> denyPaths, List<String> reviewPaths) {
    }

    public record RemoteRules(List<String> allowRemotes, List<String> denyProtocols,
                              List<String> allowUrlPatterns) {
    }

    public record Scope(String match, Operations operations) {
    }

    static final GitPolicyConfig BUILTIN = new GitPolicyConfig(
            GitPolicyMode.OBSERVE,
            "deny",
            ".nop-git/audit.log",
            30,
            null,
            new Operations(
                    List.of("status", "diff", "log", "add", "commit", "rev-parse",
                            "branch", "config"),
                    List.of("gc", "reflog"),
                    List.of("push", "reset", "clean", "checkout")),
            List.of(),
            new ProtectedBranches(
                    List.of("main", "master", "release/*"),
                    new BranchRules(false, false, false)),
            List.of(),
            new RemoteRules(List.of(), List.of("http"), List.of()),
            List.of());

    /**
     * Strict merge: rules from all layers are unioned (deny always wins during
     * evaluation); scalar settings take the most restrictive value of any
     * layer (enforce beats observe; deny beats review beats allow).
     */
    static GitPolicyConfig merge(List<GitPolicyConfig> layers) {
        if (layers.isEmpty())
            return BUILTIN;

        GitPolicyMode mode = GitPolicyMode.OBSERVE;
        String defaultEffect = null; // only explicit layers decide; builtin is the fallback
        String auditLog = BUILTIN.auditLog();
        Integer reviewTimeout = BUILTIN.reviewTimeoutSeconds();
        String version = BUILTIN.gitVersionString();

        var allow = new java.util.LinkedHashSet<String>();
        var deny = new java.util.LinkedHashSet<String>();
        var review = new java.util.LinkedHashSet<String>();
        allow.addAll(BUILTIN.operations().allow());
        deny.addAll(BUILTIN.operations().deny());
        review.addAll(BUILTIN.operations().review());

        var optionRules = new java.util.LinkedHashMap<String, Map<List<String>, List<String>>>();
        var protectedPatterns = new java.util.LinkedHashSet<String>(BUILTIN.protectedBranches().patterns());
        Boolean allowDirect = BUILTIN.protectedBranches().rules().allowDirectPush();
        Boolean allowForce = BUILTIN.protectedBranches().rules().allowForcePush();
        Boolean allowDelete = BUILTIN.protectedBranches().rules().allowDelete();
        var pathRules = new java.util.ArrayList<PathRule>();
        var allowRemotes = new java.util.LinkedHashSet<String>();
        var denyProtocols = new java.util.LinkedHashSet<String>(BUILTIN.remoteRules().denyProtocols());
        var allowUrlPatterns = new java.util.LinkedHashSet<String>();
        var scopes = new java.util.ArrayList<Scope>();

        for (GitPolicyConfig layer : layers) {
            if (layer.mode() == GitPolicyMode.ENFORCE)
                mode = GitPolicyMode.ENFORCE;
            if (layer.defaultEffect() != null) {
                defaultEffect = defaultEffect == null
                        ? layer.defaultEffect()
                        : stricterEffect(defaultEffect, layer.defaultEffect());
            }
            if (layer.auditLog() != null)
                auditLog = layer.auditLog();
            if (layer.reviewTimeoutSeconds() != null)
                reviewTimeout = layer.reviewTimeoutSeconds();
            if (layer.gitVersionString() != null)
                version = layer.gitVersionString();

            if (layer.operations() != null) {
                allow.addAll(layer.operations().allow());
                deny.addAll(layer.operations().deny());
                review.addAll(layer.operations().review());
            }
            if (layer.optionRules() != null) {
                for (OptionRule rule : layer.optionRules()) {
                    optionRules.putIfAbsent(rule.operation(),
                            new java.util.LinkedHashMap<>());
                    var flags = optionRules.get(rule.operation());
                    List<String> denyKey = rule.denyFlags() != null ? rule.denyFlags() : List.of();
                    List<String> reviewKey = rule.reviewFlags() != null ? rule.reviewFlags() : List.of();
                    flags.merge(denyKey, reviewKey, (a, b) -> {
                        var merged = new java.util.ArrayList<String>(a);
                        merged.addAll(b);
                        return List.copyOf(merged);
                    });
                }
            }
            if (layer.protectedBranches() != null) {
                protectedPatterns.addAll(layer.protectedBranches().patterns());
                BranchRules rules = layer.protectedBranches().rules();
                if (rules != null) {
                    allowDirect = leastPrivilege(allowDirect, rules.allowDirectPush());
                    allowForce = leastPrivilege(allowForce, rules.allowForcePush());
                    allowDelete = leastPrivilege(allowDelete, rules.allowDelete());
                }
            }
            if (layer.pathRules() != null)
                pathRules.addAll(layer.pathRules());
            if (layer.remoteRules() != null) {
                if (layer.remoteRules().allowRemotes() != null)
                    allowRemotes.addAll(layer.remoteRules().allowRemotes());
                if (layer.remoteRules().denyProtocols() != null)
                    denyProtocols.addAll(layer.remoteRules().denyProtocols());
                if (layer.remoteRules().allowUrlPatterns() != null)
                    allowUrlPatterns.addAll(layer.remoteRules().allowUrlPatterns());
            }
            if (layer.scopes() != null)
                scopes.addAll(layer.scopes());
        }

        // the operation sets stay mutually exclusive across layers: an
        // explicit review/deny in a higher-priority layer removes the
        // operation from the merged allow list (design 03 section 2)
        allow.removeAll(deny);
        review.removeAll(deny);
        allow.removeAll(review);

        if (defaultEffect == null)
            defaultEffect = BUILTIN.defaultEffect();
        return new GitPolicyConfig(mode, defaultEffect, auditLog, reviewTimeout, version,
                new Operations(List.copyOf(allow), List.copyOf(deny), List.copyOf(review)),
                optionRules.entrySet().stream()
                        .flatMap(e -> e.getValue().entrySet().stream()
                                .map(f -> new OptionRule(e.getKey(), f.getKey(), f.getValue())))
                        .toList(),
                new ProtectedBranches(List.copyOf(protectedPatterns),
                        new BranchRules(allowDirect, allowForce, allowDelete)),
                List.copyOf(pathRules),
                new RemoteRules(List.copyOf(allowRemotes), List.copyOf(denyProtocols),
                        List.copyOf(allowUrlPatterns)),
                List.copyOf(scopes));
    }

    private static String stricterEffect(String current, String candidate) {
        return severity(candidate) > severity(current) ? candidate : current;
    }

    private static int severity(String effect) {
        return switch (effect) {
            case "deny" -> 2;
            case "review" -> 1;
            case "allow" -> 0;
            default -> throw new IllegalArgumentException("unknown effect: " + effect);
        };
    }

    private static Boolean leastPrivilege(Boolean current, Boolean candidate) {
        // false (forbid) always wins over true/null
        if (candidate == null)
            return current;
        if (current == null)
            return candidate;
        return current && candidate;
    }
}
