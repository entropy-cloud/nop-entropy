package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.PathRuleModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 unit tests for {@link RuleBasedPathAccessChecker}: per-agent glob
 * path-rules evaluated first-match-wins within the agent's own rule-set.
 */
public class TestRuleBasedPathAccessChecker {

    private AgentExecutionContext newContext() {
        return AgentExecutionContext.create(new AgentModel(), "test-session");
    }

    private AgentExecutionContext newContextWithWorkDir(String workDir) {
        AgentModel model = new AgentModel();
        model.setWorkDir(workDir);
        return AgentExecutionContext.create(model, "test-session");
    }

    private PathRuleModel rule(String pattern, String access) {
        PathRuleModel r = new PathRuleModel();
        r.setPattern(pattern);
        r.setAccess(access);
        return r;
    }

    private IPathAccessChecker allowAll() {
        return (path, ctx) -> PathAccessResult.allow();
    }

    // ---- (a) first-match-wins ----

    @Test
    void firstMatchWinsWhenTwoRulesMatch() {
        List<PathRuleModel> rules = Arrays.asList(
                rule("/workspace/**", "deny"),
                rule("/workspace/**", "allow"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("/workspace/file.txt", newContext());
        assertFalse(result.isAllowed(), "First matching rule (deny) should decide");
        assertEquals(RuleBasedPathAccessChecker.MATCHED_RULE_DENY, result.getMatchedRule());
    }

    @Test
    void firstMatchWinsAllowBeforeDeny() {
        List<PathRuleModel> rules = Arrays.asList(
                rule("/workspace/src/**", "allow"),
                rule("/workspace/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("/workspace/src/Main.java", newContext());
        assertTrue(result.isAllowed(),
                "First matching rule (allow) delegates to wrapped checker which allows");
    }

    // ---- (b) DENY rule matches → denied ----

    @Test
    void denyRuleMatchesProducesExplicitDenial() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("**/secrets/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("/workspace/secrets/key", newContext());
        assertFalse(result.isAllowed());
        assertNotNullReason(result);
        assertTrue(result.getReason().contains("**/secrets/**"),
                "Reason must identify the matched rule pattern. Got: " + result.getReason());
        assertTrue(result.getReason().contains("/workspace/secrets/key"),
                "Reason must name the offending path. Got: " + result.getReason());
        assertEquals(RuleBasedPathAccessChecker.MATCHED_RULE_DENY, result.getMatchedRule(),
                "matchedRule must be the agent path-rule deny token");
    }

    // ---- (c) ALLOW rule matches → delegates ----

    @Test
    void allowRuleDelegatesToWrappedChecker() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/**", "allow"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("/workspace/file.txt", newContext());
        assertTrue(result.isAllowed(),
                "ALLOW rule delegates to wrapped checker which allows");
    }

    @Test
    void allowRuleDelegatesAndWrappedCheckerCanStillDeny() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/**", "allow"));
        IPathAccessChecker denyingDelegate = (path, ctx) ->
                PathAccessResult.denyByRule("sensitive_path_env_file", path);
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, denyingDelegate);

        PathAccessResult result = checker.checkAccess("/workspace/.env", newContext());
        assertFalse(result.isAllowed(),
                "Wrapped checker must still apply on top of an ALLOW rule");
        assertTrue(result.getReason().contains("sensitive_path_env_file"),
                "Denial must come from the wrapped checker. Got: " + result.getReason());
    }

    // ---- (d) no rule matches → delegates ----

    @Test
    void noRuleMatchDelegatesToWrappedChecker() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("/other/file.txt", newContext());
        assertTrue(result.isAllowed(),
                "No rule matched → delegate to wrapped checker which allows");
    }

    // ---- (e) glob wildcards ----

    @Test
    void doubleStarWildcardMatchesMultipleDirectories() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/**/secret/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        assertFalse(checker.checkAccess("/workspace/a/b/secret/key", newContext()).isAllowed(),
                "** should match multiple directory levels");
        assertFalse(checker.checkAccess("/workspace/secret/key", newContext()).isAllowed(),
                "** should match zero directory levels");
        assertTrue(checker.checkAccess("/workspace/a/b/public", newContext()).isAllowed(),
                "Non-matching path delegates to wrapped checker");
    }

    @Test
    void singleStarWildcardMatchesWithinDirectory() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/*.log", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        assertFalse(checker.checkAccess("/workspace/app.log", newContext()).isAllowed(),
                "* should match within a single directory level");
        assertTrue(checker.checkAccess("/workspace/sub/app.log", newContext()).isAllowed(),
                "* should NOT cross directory boundaries");
    }

    @Test
    void questionMarkWildcardMatchesOneChar() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/file?.txt", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        assertFalse(checker.checkAccess("/workspace/file1.txt", newContext()).isAllowed(),
                "? should match exactly one character");
        assertTrue(checker.checkAccess("/workspace/file12.txt", newContext()).isAllowed(),
                "? should NOT match two characters");
    }

    // ---- (f) path normalization + workDir-relative resolution ----

    @Test
    void relativePathResolvedAgainstWorkDirBeforeMatching() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/project-a/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        AgentExecutionContext ctx = newContextWithWorkDir("/workspace/project-a");
        PathAccessResult result = checker.checkAccess("src/Main.java", ctx);
        assertFalse(result.isAllowed(),
                "Relative path resolved against workDir under a DENY pattern should be denied");
    }

    @Test
    void relativePatternMatchesRelativePathWhenWorkDirAbsent() {
        // When workDir is ABSENT, a relative path stays relative, and a relative
        // pattern matches it (same form).
        List<PathRuleModel> rules = Collections.singletonList(
                rule("src/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        AgentExecutionContext ctx = newContext();
        PathAccessResult result = checker.checkAccess("src/Main.java", ctx);
        assertFalse(result.isAllowed(),
                "Relative pattern 'src/**' should match relative path 'src/Main.java' (same form)");
    }

    @Test
    void backslashPathNormalizedBeforeMatching() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("/workspace\\file.txt", newContext());
        assertFalse(result.isAllowed(),
                "Backslash separators should be normalized to forward slashes before matching");
    }

    @Test
    void relativePatternWithAbsolutePathDoesNotMatch() {
        // Relative pattern + absolute resolved path → form mismatch → no match → delegate
        List<PathRuleModel> rules = Collections.singletonList(
                rule("src/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("/workspace/src/Main.java", newContext());
        assertTrue(result.isAllowed(),
                "Relative pattern + absolute path → form mismatch → delegate");
    }

    @Test
    void absolutePatternWithRelativePathDoesNotMatch() {
        // Absolute pattern + relative resolved path (no workDir) → form mismatch → delegate
        List<PathRuleModel> rules = Collections.singletonList(
                rule("/workspace/**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        PathAccessResult result = checker.checkAccess("workspace/file.txt", newContext());
        assertTrue(result.isAllowed(),
                "Absolute pattern + relative path → form mismatch → delegate");
    }

    @Test
    void nullAndBlankPathDelegatesToWrappedChecker() {
        List<PathRuleModel> rules = Collections.singletonList(
                rule("**", "deny"));
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, allowAll());

        assertTrue(checker.checkAccess(null, newContext()).isAllowed(),
                "Null path delegates to wrapped checker (not matched by rules)");
        assertTrue(checker.checkAccess("", newContext()).isAllowed(),
                "Empty path delegates to wrapped checker");
        assertTrue(checker.checkAccess("   ", newContext()).isAllowed(),
                "Blank path delegates to wrapped checker");
    }

    // ---- (g) empty rule list → always delegates ----

    @Test
    void emptyRuleListAlwaysDelegates() {
        RuleBasedPathAccessChecker checker =
                new RuleBasedPathAccessChecker(Collections.emptyList(), allowAll());

        assertTrue(checker.checkAccess("/any/path", newContext()).isAllowed(),
                "Empty rules → pure pass-through to delegate");
        assertTrue(checker.checkAccess("/workspace/.env", newContext()).isAllowed(),
                "Empty rules → pure pass-through (delegate allows all here)");
    }

    @Test
    void emptyRuleListWithDenyingDelegateDenies() {
        IPathAccessChecker denyingDelegate = (path, ctx) ->
                PathAccessResult.denyByRule("sensitive_path_env_file", path);
        RuleBasedPathAccessChecker checker =
                new RuleBasedPathAccessChecker(Collections.emptyList(), denyingDelegate);

        assertFalse(checker.checkAccess("/workspace/.env", newContext()).isAllowed(),
                "Empty rules → delegate's decision stands");
    }

    // ---- (h) null constructor args → IllegalArgumentException ----

    @Test
    void constructorRejectsNullRules() {
        assertThrows(NopAiAgentException.class,
                () -> new RuleBasedPathAccessChecker(null, allowAll()));
    }

    @Test
    void constructorRejectsNullDelegate() {
        assertThrows(NopAiAgentException.class,
                () -> new RuleBasedPathAccessChecker(Collections.emptyList(), null));
    }

    // ---- accessors ----

    @Test
    void accessorMethods() {
        List<PathRuleModel> rules = Collections.singletonList(rule("/workspace/**", "deny"));
        IPathAccessChecker delegate = allowAll();
        RuleBasedPathAccessChecker checker = new RuleBasedPathAccessChecker(rules, delegate);

        assertEquals(1, checker.getRules().size());
        assertSame(delegate, checker.getDelegate());
    }

    @Test
    void matchedRuleTokenIsStable() {
        assertEquals("agent_path_rule_deny",
                RuleBasedPathAccessChecker.MATCHED_RULE_DENY,
                "Stable rule token for audit categorization");
    }

    // ---- global deny-list not overridable by ALLOW ----

    @Test
    void allowRuleCannotOverrideGlobalDenyList() {
        // **-leading ALLOW pattern is form-adjusted to match the absolute
        // (tilde-expanded) resolved path, then delegates to the global
        // DefaultPathAccessChecker which denies ~/.ssh/.
        List<PathRuleModel> rules = Collections.singletonList(
                rule("**/.ssh/**", "allow"));
        RuleBasedPathAccessChecker checker =
                new RuleBasedPathAccessChecker(rules, new DefaultPathAccessChecker());

        PathAccessResult result = checker.checkAccess("~/.ssh/id_rsa", newContext());
        assertFalse(result.isAllowed(),
                "ALLOW rule delegates to global DefaultPathAccessChecker which denies ~/.ssh/");
        assertEquals("sensitive_path_prefix", result.getMatchedRule(),
                "Global deny-list rule must be the deny source, not the agent ALLOW");
    }

    private void assertNotNullReason(PathAccessResult result) {
        assertTrue(result.getReason() != null && !result.getReason().isEmpty(),
                "Denial reason must not be null or blank");
    }
}
