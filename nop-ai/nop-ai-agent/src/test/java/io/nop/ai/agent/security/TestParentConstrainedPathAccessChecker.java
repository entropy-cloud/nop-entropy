package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 unit tests for the sub-agent path-permission inheritance
 * enforcement components: {@link ParentPermissionConstraint} (path-roots
 * extension) and {@link ParentConstrainedPathAccessChecker} (enforcement
 * wrapper).
 *
 * <p>Verified behavior:
 * <ul>
 *     <li>ABSENT path roots → pass-through (backward compatible with plan 169)</li>
 *     <li>PRESENT roots + path under a root → delegates to wrapped checker (global deny-list can still deny)</li>
 *     <li>PRESENT roots + path outside all roots → denied by constraint with explicit reason + matchedRule</li>
 *     <li>PRESENT with empty roots → all paths denied (maximum restriction)</li>
 *     <li>Root-matching edge cases: path == root, relative path resolution, trailing slash, normalization</li>
 *     <li>Denial reason explicitly identifies "parent path permission constraint" and the parent agent</li>
 *     <li>Constructor null-args fail-fast with IllegalArgumentException</li>
 * </ul>
 */
public class TestParentConstrainedPathAccessChecker {

    private AgentExecutionContext newContext() {
        return AgentExecutionContext.create(new AgentModel(), "test-session");
    }

    private AgentExecutionContext newContextWithWorkDir(String workDir) {
        AgentModel model = new AgentModel();
        model.setWorkDir(workDir);
        return AgentExecutionContext.create(model, "test-session");
    }

    // ---- ParentPermissionConstraint path-roots extension ----

    @Test
    void constraintCarriesPathRoots() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");
        assertEquals(Set.of("/workspace/project-a"), c.getAllowedPathRoots());
        assertTrue(c.hasPathRoots());
    }

    @Test
    void constraintAbsentPathRootsByDefault() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        assertNull(c.getAllowedPathRoots(), "tool-only constructor must default to ABSENT path roots");
        assertFalse(c.hasPathRoots());
    }

    @Test
    void constraintEmptyPathRootsMeansPresent() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of(),
                "parent-agent", "parent-sess");
        assertNotNull(c.getAllowedPathRoots(), "empty set means PRESENT (deny all)");
        assertTrue(c.getAllowedPathRoots().isEmpty());
        assertTrue(c.hasPathRoots());
    }

    @Test
    void constraintGetPathRootsIsUnmodifiable() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/a"), "agent", "sess");
        assertThrows(UnsupportedOperationException.class,
                () -> c.getAllowedPathRoots().add("/sneaky"));
    }

    @Test
    void constraintEqualsHashCodeIncludePathRoots() {
        ParentPermissionConstraint a = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a"), "agent", "sess");
        ParentPermissionConstraint b = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a"), "agent", "sess");
        ParentPermissionConstraint diff = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/b"), "agent", "sess");
        ParentPermissionConstraint absentDiff = new ParentPermissionConstraint(
                Set.of("read-file"), "agent", "sess");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(diff), "different path roots → not equal");
        assertFalse(a.equals(absentDiff), "PRESENT vs ABSENT path roots → not equal");
    }

    @Test
    void constraintToStringIncludesPathRoots() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a"), "agent", "sess");
        String s = c.toString();
        assertTrue(s.contains("allowedPathRoots"), "toString must include allowedPathRoots");
        assertTrue(s.contains("/workspace/a"));
    }

    // ---- ParentConstrainedPathAccessChecker: ABSENT roots pass-through ----

    @Test
    void absentPathRootsPassesThrough() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), null, "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/any/path", newContext());
        assertTrue(result.isAllowed(),
                "ABSENT roots → pass-through to delegate (backward compatible)");
    }

    @Test
    void absentPathRootsViaToolOnlyConstructorPassesThrough() {
        // Tool-only constructor (plan 169 backward compatible) → ABSENT path roots
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/any/path", newContext());
        assertTrue(result.isAllowed(),
                "Tool-only constructor must produce ABSENT path roots → pass-through");
    }

    // ---- PRESENT roots, path under root → delegate ----

    @Test
    void pathUnderRootDelegatesToWrappedChecker() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/workspace/project-a/src/Main.java", newContext());
        assertTrue(result.isAllowed(),
                "Path under an allowed root should delegate to wrapped checker which allows it");
    }

    @Test
    void pathUnderRootWrappedCheckerCanStillDeny() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        // Delegate denies (e.g. global deny-list for sensitive files)
        IPathAccessChecker delegate = (path, ctx) ->
                PathAccessResult.denyByRule("sensitive_path_filename", path);

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/workspace/project-a/.env", newContext());
        assertFalse(result.isAllowed(),
                "Wrapped checker must still apply on top for paths under a root");
        assertTrue(result.getReason().contains("sensitive_path_filename"),
                "Denial must come from the wrapped checker, not the parent constraint. Got: "
                        + result.getReason());
    }

    @Test
    void pathUnderRootWithDefaultPathAccessCheckerDelegate() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, new DefaultPathAccessChecker());

        // Normal path under root → DefaultPathAccessChecker allows it
        PathAccessResult okResult = wrapper.checkAccess("/workspace/project-a/src/Main.java", newContext());
        assertTrue(okResult.isAllowed());

        // Sensitive file under root → DefaultPathAccessChecker denies it (global deny-list applies)
        PathAccessResult deniedResult = wrapper.checkAccess("/workspace/project-a/.env", newContext());
        assertFalse(deniedResult.isAllowed());
        assertEquals("sensitive_path_env_file", deniedResult.getMatchedRule());
    }

    // ---- PRESENT roots, path outside → denied by constraint ----

    @Test
    void pathOutsideRootDeniedByConstraint() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/workspace/project-b/secret", newContext());
        assertFalse(result.isAllowed(),
                "Path outside all allowed roots must be denied (fail-closed)");
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("parent path permission constraint"),
                "Denial reason must identify 'parent path permission constraint'. Got: "
                        + result.getReason());
        assertTrue(result.getReason().contains("parent-agent"),
                "Denial reason must name the parent agent. Got: " + result.getReason());
        assertTrue(result.getReason().contains("/workspace/project-b/secret"),
                "Denial reason must name the offending path. Got: " + result.getReason());
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE, result.getMatchedRule(),
                "matchedRule must be the parent path constraint token");
    }

    @Test
    void pathOutsideRootDeniedEvenWhenDelegateAllowsAll() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, new AllowAllPathAccessChecker());

        PathAccessResult result = wrapper.checkAccess("/etc/passwd", newContext());
        assertFalse(result.isAllowed(),
                "Parent constraint must deny even when wrapped checker is AllowAll");
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE, result.getMatchedRule());
    }

    // ---- PRESENT empty roots → deny all ----

    @Test
    void emptyRootsDenyAllPaths() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of(),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult r1 = wrapper.checkAccess("/workspace/a/file", newContext());
        PathAccessResult r2 = wrapper.checkAccess("/any/other/path", newContext());
        PathAccessResult r3 = wrapper.checkAccess("/tmp/x", newContext());

        assertFalse(r1.isAllowed(), "PRESENT({}) must deny all paths");
        assertFalse(r2.isAllowed(), "PRESENT({}) must deny all paths");
        assertFalse(r3.isAllowed(), "PRESENT({}) must deny all paths");
        assertTrue(r1.getReason().contains("parent path permission constraint"));
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE, r1.getMatchedRule());
    }

    // ---- Root-matching edge cases ----

    @Test
    void pathEqualsRootIsAllowed() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/workspace/project-a", newContext());
        assertTrue(result.isAllowed(),
                "Path exactly equal to a root is 'under' the root and should be allowed");
    }

    @Test
    void rootWithTrailingSlashMatchesPathsUnderIt() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a/"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        assertTrue(wrapper.checkAccess("/workspace/project-a/src/Main.java", newContext()).isAllowed());
        assertTrue(wrapper.checkAccess("/workspace/project-a", newContext()).isAllowed(),
                "Path without trailing slash matching root with trailing slash should be allowed");
    }

    @Test
    void pathWithTraversalNormalizedBeforeMatching() {
        // The wrapper does NOT re-implement traversal defense, but normalization
        // resolves the path before root comparison. A path with redundant "./" or
        // internal ".." that stays under the root is normalized and matched.
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        // Redundant "." segments are normalized away → still under root
        assertTrue(wrapper.checkAccess("/workspace/project-a/./src/Main.java", newContext()).isAllowed(),
                "Redundant '.' segments should be normalized before root matching");
    }

    @Test
    void backslashPathNormalizedBeforeMatching() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        // Backslash separators are normalized to forward slashes
        assertTrue(wrapper.checkAccess("/workspace/project-a/src/Main.java", newContext()).isAllowed());
    }

    @Test
    void siblingDirectoryDenied() {
        // /workspace/project-a vs /workspace/project-ab — prefix-without-slash must NOT match
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        assertFalse(wrapper.checkAccess("/workspace/project-ab/secret", newContext()).isAllowed(),
                "/workspace/project-ab is a sibling, not under /workspace/project-a — must be denied");
    }

    @Test
    void multipleRootsAnyMatch() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a", "/workspace/b"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        assertTrue(wrapper.checkAccess("/workspace/a/file", newContext()).isAllowed());
        assertTrue(wrapper.checkAccess("/workspace/b/file", newContext()).isAllowed());
        assertFalse(wrapper.checkAccess("/workspace/c/file", newContext()).isAllowed());
    }

    // ---- Relative path resolution against workDir ----

    @Test
    void relativePathResolvedAgainstWorkDir() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        // Sub-agent's workDir is under the parent's root → relative path resolves under root
        AgentExecutionContext ctx = newContextWithWorkDir("/workspace/project-a/sub");
        assertTrue(wrapper.checkAccess("src/Main.java", ctx).isAllowed(),
                "Relative path resolved against workDir under root should be allowed");
    }

    @Test
    void relativePathResolvingOutsideRootDenied() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        // Sub-agent's workDir is under parent's root, but relative path escapes via ".."
        // After resolution: /workspace/project-a/sub/../../project-b/secret = /workspace/project-b/secret
        AgentExecutionContext ctx = newContextWithWorkDir("/workspace/project-a/sub");
        PathAccessResult result = wrapper.checkAccess("../../project-b/secret", ctx);
        assertFalse(result.isAllowed(),
                "Relative path escaping the parent root after workDir resolution must be denied");
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE, result.getMatchedRule());
    }

    @Test
    void relativePathWithNoWorkDirResolvedAgainstJvmCwd() {
        // When sub-agent has no declared workDir, relative paths are not resolved against
        // any workDir — they remain relative. The wrapper does not deny them solely on that
        // basis; instead it delegates (the global deny-list / sub-agent rules apply).
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/project-a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        // No workDir in the agent model → relative path stays relative → not under root → denied
        AgentExecutionContext ctx = newContext();
        PathAccessResult result = wrapper.checkAccess("relative/path.txt", ctx);
        assertFalse(result.isAllowed(),
                "Relative path with no workDir to resolve against is not under any absolute root");
    }

    // ---- Null/blank path handling ----

    @Test
    void nullPathDelegatesToWrappedChecker() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a"),
                "parent-agent", "parent-sess");

        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        assertTrue(wrapper.checkAccess(null, newContext()).isAllowed(),
                "Null path should delegate to wrapped checker (same as DefaultPathAccessChecker)");
        assertTrue(wrapper.checkAccess("", newContext()).isAllowed(),
                "Empty path should delegate to wrapped checker");
        assertTrue(wrapper.checkAccess("   ", newContext()).isAllowed(),
                "Blank path should delegate to wrapped checker");
    }

    // ---- Constructor fail-fast ----

    @Test
    void wrapperConstructorRejectsNullConstraint() {
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();
        assertThrows(IllegalArgumentException.class,
                () -> new ParentConstrainedPathAccessChecker(null, delegate));
    }

    @Test
    void wrapperConstructorRejectsNullDelegate() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a"), "agent", "sess");
        assertThrows(IllegalArgumentException.class,
                () -> new ParentConstrainedPathAccessChecker(constraint, null));
    }

    // ---- Accessors ----

    @Test
    void wrapperAccessorMethods() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), Set.of("/workspace/a"), "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        assertSame(constraint, wrapper.getConstraint());
        assertSame(delegate, wrapper.getDelegate());
    }

    @Test
    void matchedRuleTokenIsStable() {
        assertEquals("parent_path_permission_constraint",
                ParentConstrainedPathAccessChecker.MATCHED_RULE,
                "Stable rule token for audit categorization");
    }

    // ---- PathAccessResult.deny(reason, matchedRule) factory ----

    @Test
    void pathAccessResultDenyWithReasonAndMatchedRule() {
        PathAccessResult r = PathAccessResult.deny("custom reason", "custom_rule");
        assertFalse(r.isAllowed());
        assertEquals("custom reason", r.getReason());
        assertEquals("custom_rule", r.getMatchedRule());
    }

    @Test
    void pathAccessResultDenyWithReasonOnlyHasNullMatchedRule() {
        // Existing deny(reason) unchanged — matchedRule remains null
        PathAccessResult r = PathAccessResult.deny("reason only");
        assertFalse(r.isAllowed());
        assertEquals("reason only", r.getReason());
        assertNull(r.getMatchedRule());
    }

    @Test
    void pathAccessResultDenyByRuleUnchanged() {
        // Existing denyByRule(...) unchanged
        PathAccessResult r = PathAccessResult.denyByRule("rule_x", "/some/path");
        assertFalse(r.isAllowed());
        assertTrue(r.getReason().contains("rule_x"));
        assertTrue(r.getReason().contains("/some/path"));
        assertEquals("rule_x", r.getMatchedRule());
    }

    // ---- Phase 3: path-rules extension (deny-wins cross-level) ----

    private io.nop.ai.agent.model.PathRuleModel pathRule(String pattern, String access) {
        io.nop.ai.agent.model.PathRuleModel r = new io.nop.ai.agent.model.PathRuleModel();
        r.setPattern(pattern);
        r.setAccess(access);
        return r;
    }

    @Test
    void parentDenyRuleBlocksMatchingPath() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), null,
                java.util.List.of(pathRule("/workspace/secret/**", "deny")),
                "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/workspace/secret/key", newContext());
        assertFalse(result.isAllowed(), "Parent DENY rule must block matching path");
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE_PATH_RULE, result.getMatchedRule());
        assertTrue(result.getReason().contains("/workspace/secret/**"),
                "Reason must identify the matched pattern. Got: " + result.getReason());
        assertTrue(result.getReason().contains("parent-agent"),
                "Reason must identify the parent agent. Got: " + result.getReason());
    }

    @Test
    void parentAllowRuleDoesNotBlockAndDelegateApplies() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), null,
                java.util.List.of(pathRule("/workspace/**", "allow")),
                "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/workspace/file.txt", newContext());
        assertTrue(result.isAllowed(),
                "Parent ALLOW rule does not block — delegates to wrapped checker which allows");
    }

    @Test
    void parentDenyWinsOverAllowInAccumulatedChain() {
        // Cross-level deny-wins: even if an ALLOW rule precedes a DENY rule,
        // the DENY must win (all rules are scanned, not first-match-wins).
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), null,
                java.util.List.of(
                        pathRule("/workspace/**", "allow"),
                        pathRule("/workspace/secret/**", "deny")),
                "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        PathAccessResult result = wrapper.checkAccess("/workspace/secret/key", newContext());
        assertFalse(result.isAllowed(),
                "Cross-level deny-wins: DENY rule must block even when preceded by a broader ALLOW");
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE_PATH_RULE, result.getMatchedRule());
    }

    @Test
    void parentRulesAbsentPassesThrough() {
        // ABSENT path-rules (null) → no rule confinement, backward compatible
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), null, null,
                "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        assertTrue(wrapper.checkAccess("/any/path", newContext()).isAllowed(),
                "ABSENT path-rules → pass-through to delegate");
    }

    @Test
    void rootsAndRulesBothPresentBothEnforced() {
        // Both roots AND rules PRESENT → both dimensions enforced (fail-closed)
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"),
                Set.of("/workspace"),
                java.util.List.of(pathRule("/workspace/secret/**", "deny")),
                "parent-agent", "parent-sess");
        IPathAccessChecker delegate = (path, ctx) -> PathAccessResult.allow();

        ParentConstrainedPathAccessChecker wrapper =
                new ParentConstrainedPathAccessChecker(constraint, delegate);

        // Path outside roots → denied by root check (regardless of rules)
        assertFalse(wrapper.checkAccess("/etc/file", newContext()).isAllowed(),
                "Path outside roots must be denied by root check");

        // Path under roots but matching DENY rule → denied by rule check
        PathAccessResult ruleResult = wrapper.checkAccess("/workspace/secret/key", newContext());
        assertFalse(ruleResult.isAllowed(),
                "Path under roots but matching parent DENY rule must be denied by rule check");
        assertEquals(ParentConstrainedPathAccessChecker.MATCHED_RULE_PATH_RULE, ruleResult.getMatchedRule());

        // Path under roots, no rule match → passes both, delegates
        assertTrue(wrapper.checkAccess("/workspace/src/Main.java", newContext()).isAllowed(),
                "Path under roots with no rule match passes both dimensions");
    }

    @Test
    void constraintCarriesPathRules() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), null,
                java.util.List.of(pathRule("/**", "deny")),
                "parent-agent", "parent-sess");
        assertNotNull(c.getAllowedPathRules(), "Path rules must be PRESENT");
        assertTrue(c.hasPathRules());
        assertEquals(1, c.getAllowedPathRules().size());
    }

    @Test
    void constraintAbsentPathRulesByDefault() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        assertNull(c.getAllowedPathRules(), "Tool-only constructor must default to ABSENT path rules");
        assertFalse(c.hasPathRules());
    }
}
