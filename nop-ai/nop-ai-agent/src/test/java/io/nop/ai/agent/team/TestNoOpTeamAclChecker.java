package io.nop.ai.agent.team;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link NoOpTeamAclChecker} shipped default (plan 228
 * Phase 1).
 *
 * <p>Verifies the contract from Design Decision §4: the shipped default is
 * "ACL not enabled = no extra restriction" — every {@code checkAccess}
 * returns an <em>explicit</em> {@code allow(null)} decision, not a silent
 * skip / null return. This is zero-regression behaviour (the team tools
 * proceed unchanged) and honours Minimum Rules #24 (no silent no-op).
 */
public class TestNoOpTeamAclChecker {

    private final NoOpTeamAclChecker checker = NoOpTeamAclChecker.noOp();

    @Test
    void checkAccessAlwaysAllows() {
        TeamAclDecision d = checker.checkAccess(
                "any-team", "any-session", "team-task-update", "abandon-unclaimed");

        assertTrue(d.isAllowed(),
                "NoOp checker must always allow (no extra restriction)");
    }

    @Test
    void checkAccessAllowsAllMatrixTuples() {
        // Sample every (toolName, action) tuple the matrix knows about —
        // NoOp must allow all of them with no discrimination.
        String[][] tuples = {
                {"team-send-message", "send"},
                {"team-status", "view"},
                {"team-task-create", "create"},
                {"team-task-update", "claim"},
                {"team-task-update", "complete"},
                {"team-task-update", "abandon-claimed"},
                {"team-task-update", "abandon-unclaimed"},
        };
        for (String[] t : tuples) {
            TeamAclDecision d = checker.checkAccess("team-1", "sess-1", t[0], t[1]);
            assertTrue(d.isAllowed(),
                    "NoOp must allow " + t[0] + "/" + t[1] + ": " + d);
        }
    }

    @Test
    void checkAccessResolvedRoleIsNull() {
        TeamAclDecision d = checker.checkAccess("t", "s", "team-status", "view");

        assertTrue(d.isAllowed());
        assertTrue(d.getResolvedRole() == null,
                "NoOp allow carries no role information (resolvedRole=null)");
        assertTrue(d.getReason() == null,
                "allow decision has null reason");
    }

    @Test
    void checkAccessAllowsUnknownTuplesToo() {
        // NoOp is "ACL not enabled" — it must not discriminate based on
        // whether the tuple is in the matrix. An unknown tuple is still
        // allowed (the executor's job to validate the action string).
        TeamAclDecision d = checker.checkAccess("t", "s", "team-unknown", "whatever");
        assertTrue(d.isAllowed());
    }

    @Test
    void noOpReturnsSingletonInstance() {
        assertSame(NoOpTeamAclChecker.noOp(), NoOpTeamAclChecker.noOp(),
                "noOp() must return the same singleton instance");
    }
}
