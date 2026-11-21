package io.nop.git.policy;

import io.nop.git.api.NopGitException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

/**
 * Default policy engine: strict three-layer merge, five-dimension evaluation
 * in the pinned design 03 order, review channel, JSON Lines audit.
 * The clock and stdin are injectable so tests exercise the review timeout
 * without real waiting.
 */
public final class DefaultGitPolicyEngine implements IGitPolicyEngine {

    private final GitPolicyConfig config;
    private final Path auditFile;      // absolute, resolved against the repo root
    private final BufferedReader stdin;
    private final LongSupplier clockNanos;
    private final Appendable reviewOut;
    // session-scoped approvals: when the CLI guard resolves a review, the
    // facade re-evaluation of the same request must not prompt again
    private final java.util.Set<String> approvedRequests = new java.util.HashSet<>();
    // how each review was resolved (env|interactive), recorded in the audit
    // line so an environment-driven approval stays visible (plan 2582)
    private final java.util.Map<String, String> reviewSources = new java.util.HashMap<>();

    public DefaultGitPolicyEngine(GitPolicyConfig config, Path projectRoot,
                                  java.io.Reader input, LongSupplier clockNanos,
                                  Appendable reviewOut) {
        this.config = config;
        this.auditFile = projectRoot == null ? null
                : projectRoot.resolve(config.auditLog());
        this.stdin = input instanceof BufferedReader buffered
                ? buffered : new BufferedReader(input);
        this.clockNanos = clockNanos;
        this.reviewOut = reviewOut;
    }

    /**
     * Load from the standard layer chain; inactive when no policy file exists
     * (no interception, no audit).
     */
    public static IGitPolicyEngine load(Path projectRoot) {
        return load(projectRoot, System.out);
    }

    /**
     * @param reviewOut where the interactive review prompt goes (CLI: stderr,
     *                  so AI tools parsing stdout see only git output)
     */
    public static IGitPolicyEngine load(Path projectRoot, Appendable reviewOut) {
        List<GitPolicyConfig> layers = GitPolicyLoader.loadLayers(projectRoot);
        if (layers.isEmpty())
            return new InactiveEngine();
        return new DefaultGitPolicyEngine(GitPolicyConfig.merge(layers), projectRoot,
                new BufferedReader(new java.io.InputStreamReader(System.in,
                        StandardCharsets.UTF_8)),
                System::nanoTime,
                reviewOut);
    }

    /**
     * Load with a custom project-layer policy file (plan 2582: custom policy
     * paths, e.g. the nop-git-ioc policyPath configuration, must be honored);
     * the user-home layer stays in the chain.
     */
    public static IGitPolicyEngine load(Path projectRoot, Path projectPolicyFile,
                                        Appendable reviewOut) {
        List<GitPolicyConfig> layers = GitPolicyLoader.loadLayers(projectRoot,
                Path.of(System.getProperty("user.home"), ".nop-git", "policy.yaml"),
                projectPolicyFile);
        if (layers.isEmpty())
            return new InactiveEngine();
        return new DefaultGitPolicyEngine(GitPolicyConfig.merge(layers), projectRoot,
                new BufferedReader(new java.io.InputStreamReader(System.in,
                        StandardCharsets.UTF_8)),
                System::nanoTime,
                reviewOut);
    }

    public static IGitPolicyEngine fromConfig(GitPolicyConfig config, Path projectRoot,
                                              java.io.Reader input, LongSupplier clockNanos,
                                              Appendable reviewOut) {
        return new DefaultGitPolicyEngine(config, projectRoot, input, clockNanos, reviewOut);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public GitPolicyMode mode() {
        return config.mode();
    }

    // ------------------------------------------------------------------
    // evaluation (design 03 section 3, pinned semantics)
    // ------------------------------------------------------------------

    @Override
    public GitVerdict evaluate(IGitOperationRequest request) {
        var accumulated = new Object() {
            GitVerdict review = null;
        };

        // 1. scopes: highest priority deny; non-deny falls through
        for (GitPolicyConfig.Scope scope : config.scopes()) {
            if (!scopeMatches(scope.match(), request.repoPath()))
                continue;
            if (scope.operations() != null
                    && contains(scope.operations().deny(), request.operation())) {
                return GitVerdict.deny("scope:" + scope.match(),
                        "operation '" + request.operation() + "' is denied by scope "
                                + scope.match());
            }
        }

        // 2. operation-level deny
        if (contains(config.operations().deny(), request.operation())) {
            return GitVerdict.deny("operations.deny",
                    "operation '" + request.operation() + "' is denied by policy");
        }

        // 3. option level: deny flags win, review flags accumulate
        for (GitPolicyConfig.OptionRule rule : config.optionRules()) {
            if (!rule.operation().equals(request.operation()))
                continue;
            for (String flag : request.flags()) {
                if (rule.denyFlags() != null && contains(rule.denyFlags(), flag)) {
                    return GitVerdict.deny("option_rules:" + rule.operation() + ":" + flag,
                            "flag '" + flag + "' of '" + rule.operation()
                                    + "' is denied by policy");
                }
            }
            for (String flag : request.flags()) {
                if (rule.reviewFlags() != null && contains(rule.reviewFlags(), flag)) {
                    accumulated.review = accumulated.review == null
                            ? GitVerdict.allow().reviewAccumulated(
                            "option_rules:" + rule.operation() + ":" + flag,
                            "flag '" + flag + "' of '" + rule.operation()
                                    + "' requires review")
                            : accumulated.review.withExtraRule(
                            "option_rules:" + rule.operation() + ":" + flag);
                }
            }
        }

        // 4. branch level (protected branches)
        GitVerdict branchVerdict = evaluateBranch(request);
        if (branchVerdict.effect() == GitVerdict.Effect.DENY)
            return branchVerdict;

        // 5. path level
        for (GitPolicyConfig.PathRule rule : config.pathRules()) {
            if (rule.operations() != null && !rule.operations().contains(request.operation()))
                continue;
            for (String path : request.paths()) {
                if (anyGlob(rule.denyPaths(), path)) {
                    return GitVerdict.deny("path_rules:" + path,
                            "path '" + path + "' is denied by policy");
                }
            }
            for (String path : request.paths()) {
                if (anyGlob(rule.reviewPaths(), path)) {
                    accumulated.review = accumulated.review == null
                            ? GitVerdict.allow().reviewAccumulated("path_rules:" + path,
                            "path '" + path + "' requires review")
                            : accumulated.review.withExtraRule("path_rules:" + path);
                }
            }
        }

        // 6. remote level
        GitVerdict remoteVerdict = evaluateRemote(request);
        if (remoteVerdict.effect() == GitVerdict.Effect.DENY)
            return remoteVerdict;

        // 7. accumulated review wins over the allow whitelist (pinned)
        if (accumulated.review != null)
            return accumulated.review;

        // 8. allow whitelist, then the explicit review list (design 03: the
        // three operation sets are mutually exclusive)
        if (contains(config.operations().allow(), request.operation()))
            return GitVerdict.allow();
        if (contains(config.operations().review(), request.operation()))
            return new GitVerdict(GitVerdict.Effect.REVIEW, List.of("operations.review"),
                    "operation '" + request.operation() + "' requires review");

        // 9. default effect
        return switch (config.defaultEffect()) {
            case "deny" -> GitVerdict.deny("default_effect",
                    "operation '" + request.operation()
                            + "' is not allowed by policy (default_effect=deny)");
            case "review" -> new GitVerdict(GitVerdict.Effect.REVIEW, List.of("default_effect"),
                    "operation '" + request.operation() + "' requires review");
            default -> GitVerdict.allow();
        };
    }

    private GitVerdict evaluateBranch(IGitOperationRequest request) {
        String branch = request.targetBranch();
        if (branch == null || !isProtectedBranch(branch))
            return GitVerdict.allow();

        GitPolicyConfig.BranchRules rules = config.protectedBranches().rules();
        String operation = request.operation();
        boolean denied = false;
        String reason = null;

        if ("push".equals(operation) && !isTrue(rules.allowDirectPush())) {
            denied = true;
            reason = "direct push to protected branch '" + branch + "' is denied";
        }
        if (request.flags().contains("--force") && !isTrue(rules.allowForcePush())) {
            denied = true;
            reason = "force push to protected branch '" + branch + "' is denied";
        }
        if (("branch".equals(operation) && request.flags().contains("--delete"))
                && !isTrue(rules.allowDelete())) {
            denied = true;
            reason = "delete of protected branch '" + branch + "' is denied";
        }
        return denied ? GitVerdict.deny("protected_branches:" + branch, reason)
                : GitVerdict.allow();
    }

    private GitVerdict evaluateRemote(IGitOperationRequest request) {
        GitPolicyConfig.RemoteRules rules = config.remoteRules();
        if (rules == null)
            return GitVerdict.allow();
        if (request.remote() != null && !rules.allowRemotes().isEmpty()
                && !rules.allowRemotes().contains(request.remote())) {
            return GitVerdict.deny("remote_rules:remote",
                    "remote '" + request.remote() + "' is not in the allow list");
        }
        if (request.remoteUrl() != null) {
            for (String protocol : rules.denyProtocols()) {
                if (request.remoteUrl().toLowerCase().startsWith(protocol + "://")) {
                    return GitVerdict.deny("remote_rules:protocol",
                            "remote protocol '" + protocol + "://' is denied");
                }
            }
            if (!rules.allowUrlPatterns().isEmpty()
                    && !anyGlob(rules.allowUrlPatterns(), request.remoteUrl())) {
                return GitVerdict.deny("remote_rules:url",
                        "remote url '" + request.remoteUrl() + "' is not in the allow list");
            }
        }
        return GitVerdict.allow();
    }

    // ------------------------------------------------------------------
    // review channel
    // ------------------------------------------------------------------

    @Override
    public boolean resolveReview(IGitOperationRequest request, GitVerdict verdict) {
        String key = request.rawCommand() + "|" + verdict.reason();
        if (approvedRequests.contains(key))
            return true; // already approved in this engine session
        if ("1".equals(System.getenv("NOP_GIT_REVIEW_APPROVED"))) {
            // unattended approval via the external approval flow (plan 2582
            // hardening): single-request scope only — it is never added to
            // the session approval set, and the audit line records
            // reviewSource=env so the daemon cannot silently self-approve
            reviewSources.put(key, "env");
            return true;
        }

        int timeoutSeconds = config.reviewTimeoutSeconds() == null ? 30
                : config.reviewTimeoutSeconds();
        long deadline = clockNanos.getAsLong() + Duration.ofSeconds(timeoutSeconds).toNanos();

        try {
            reviewOut.append("review required: " + verdict.reason() + "\n");
            reviewOut.append("approve? type 'yes' to continue: ");
        } catch (IOException e) {
            return false;
        }

        while (clockNanos.getAsLong() < deadline) {
            try {
                if (!stdin.ready())
                    Thread.sleep(10);
                else {
                    String line = stdin.readLine();
                    boolean approved = "yes".equalsIgnoreCase(line == null ? "" : line.trim());
                    if (approved) {
                        approvedRequests.add(key);
                        reviewSources.put(key, "interactive");
                    }
                    return approved;
                }
            } catch (IOException e) {
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false; // review timed out, operation denied
    }

    // ------------------------------------------------------------------
    // audit (JSON Lines, single write with the final outcome)
    // ------------------------------------------------------------------

    @Override
    public void audit(IGitOperationRequest request, GitVerdict verdict, String outcome) {
        if (auditFile == null)
            return;
        StringBuilder json = new StringBuilder();
        json.append("{\"timestamp\":\"").append(java.time.Instant.now()).append('"');
        json.append(",\"repoPath\":\"").append(escape(request.repoPath())).append('"');
        json.append(",\"rawCommand\":\"").append(escape(request.rawCommand())).append('"');
        json.append(",\"normalizedOperation\":\"").append(escape(request.operation())).append('"');
        json.append(",\"flags\":").append(jsonArray(request.flags()));
        json.append(",\"paths\":").append(jsonArray(request.paths()));
        json.append(",\"remote\":\"").append(escape(request.remote())).append('"');
        json.append(",\"matchedRuleIds\":").append(jsonArray(verdict.matchedRuleIds()));
        json.append(",\"verdict\":\"").append(verdict.effect()).append('"');
        // plan 2582: how a required review was resolved stays in the audit
        String reviewSource = reviewSources
                .get(request.rawCommand() + "|" + verdict.reason());
        if (reviewSource != null)
            json.append(",\"reviewSource\":\"").append(reviewSource).append('"');
        if (mode() == GitPolicyMode.OBSERVE) {
            json.append(",\"hypotheticalEffect\":\"").append(enforceHypothetical(request)).append('"');
        }
        json.append(",\"outcome\":\"").append(escape(outcome)).append("\"}\n");
        try {
            Path parent = auditFile.getParent();
            if (parent != null)
                Files.createDirectories(parent);
            Files.writeString(auditFile, json.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new NopGitException("cannot write audit log "
                    + auditFile + ": " + e.getMessage(), e);
        }
    }

    private String enforceHypothetical(IGitOperationRequest request) {
        // observe mode records what enforce would decide: evaluation is
        // mode-independent, so re-evaluating under a hypothetical enforce
        // yields the same verdict
        return evaluate(request).effect().name();
    }

    private static String escape(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String jsonArray(List<String> values) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0)
                sb.append(',');
            sb.append('"').append(escape(values.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static boolean contains(List<String> list, String value) {
        return value != null && list.contains(value);
    }

    private static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    private boolean isProtectedBranch(String branch) {
        // plan 2582: a wildcard refspec target ("*") may touch any protected
        // branch, so it is treated as protected whenever any pattern exists
        // (strictest handling; no pattern means nothing can be protected)
        if ("*".equals(branch))
            return !config.protectedBranches().patterns().isEmpty();
        return config.protectedBranches().patterns().stream()
                .anyMatch(pattern -> globMatches(pattern, branch));
    }

    private static boolean scopeMatches(String match, String repoPath) {
        // gitdir: prefix semantics: match against the canonical repo path
        String pattern = match.startsWith("gitdir:") ? match.substring("gitdir:".length()) : match;
        if (repoPath == null)
            return false;
        String normalized = repoPath.endsWith("/") ? repoPath : repoPath + "/";
        String normalizedPattern = pattern.endsWith("/") ? pattern : pattern + "/";
        if (normalizedPattern.startsWith("/"))
            return normalized.startsWith(normalizedPattern);
        return normalized.contains(normalizedPattern);
    }

    private static boolean anyGlob(List<String> patterns, String value) {
        return value != null && patterns.stream().anyMatch(p -> globMatches(p, value));
    }

    /**
     * Glob with {@code **} (any path segments), {@code *} (one segment) and
     * {@code ?} (one character), matching native gitignore-style semantics
     * sufficiently for path rules.
     */
    static boolean globMatches(String pattern, String value) {
        // gitignore semantics: a pattern without a slash matches the basename
        // at any depth
        if (!pattern.contains("/")) {
            String basename = value.substring(value.lastIndexOf('/') + 1);
            return globToRegex(pattern).matcher(basename).matches();
        }
        return globToRegex(pattern).matcher(value).matches();
    }

    private static java.util.regex.Pattern globToRegex(String pattern) {
        var regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') {
                    regex.append(".*");
                    i++;
                    // consume a following slash so "**/x" also matches "x"
                    if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '/')
                        i++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return java.util.regex.Pattern.compile(regex.toString());
    }

    /**
     * Inactive engine when no policy layer exists: everything allowed, no
     * audit.
     */
    private static final class InactiveEngine implements IGitPolicyEngine {
        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public GitPolicyMode mode() {
            throw new NopGitException("policy engine is inactive (no policy.yaml found)");
        }

        @Override
        public GitVerdict evaluate(IGitOperationRequest request) {
            return GitVerdict.allow();
        }

        @Override
        public boolean resolveReview(IGitOperationRequest request, GitVerdict verdict) {
            return true;
        }

        @Override
        public void audit(IGitOperationRequest request, GitVerdict verdict, String outcome) {
            // inactive: no audit by design
        }
    }
}
