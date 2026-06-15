package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 1 unit tests for the sub-agent permission inheritance enforcement
 * components: {@link ParentPermissionConstraint} (immutable value object) and
 * {@link ParentConstrainedToolAccessChecker} (enforcement wrapper).
 *
 * <p>Verified behavior:
 * <ul>
 *     <li>Tool in both parent's effective set and child's declared set → delegates to child checker (child can still deny)</li>
 *     <li>Tool only in child's declared set → denied by parent constraint with explicit reason</li>
 *     <li>No constraint present → pass-through (backward compatible)</li>
 *     <li>Empty parent allowed set → all tools denied (maximum restriction)</li>
 *     <li>Denial reason explicitly identifies "parent permission constraint"</li>
 *     <li>Immutability of the constraint object</li>
 * </ul>
 */
public class TestParentConstrainedToolAccessChecker {

    private AgentExecutionContext newContext() {
        return AgentExecutionContext.create(new AgentModel(), "test-session");
    }

    // ---- ParentPermissionConstraint ----

    @Test
    void constraintIsImmutableAndCarriesAuditMetadata() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file", "call-agent"), "parent-agent", "parent-sess-1");

        assertEquals(Set.of("read-file", "call-agent"), c.getAllowedTools());
        assertEquals("parent-agent", c.getParentAgentName());
        assertEquals("parent-sess-1", c.getParentSessionId());
    }

    @Test
    void constraintNullAllowedToolsThrows() {
        assertThrows(NopAiAgentException.class,
                () -> new ParentPermissionConstraint(null, "agent", "sess"));
    }

    @Test
    void constraintCopiesAllowedToolsDefensively() {
        java.util.Set<String> mutable = new java.util.HashSet<>();
        mutable.add("read-file");
        ParentPermissionConstraint c = new ParentPermissionConstraint(mutable, "agent", "sess");

        mutable.add("sneaky-tool");
        assertFalse(c.getAllowedTools().contains("sneaky-tool"),
                "Constraint must not be affected by later mutation of the source set");
    }

    @Test
    void constraintGetAllowedToolsIsUnmodifiable() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file"), "agent", "sess");
        assertThrows(UnsupportedOperationException.class,
                () -> c.getAllowedTools().add("write-file"));
    }

    @Test
    void constraintAllowsChecksMembership() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of("read-file", "call-agent"), "agent", "sess");
        assertTrue(c.allows("read-file"));
        assertTrue(c.allows("call-agent"));
        assertFalse(c.allows("write-file"));
        assertFalse(c.allows(null));
    }

    @Test
    void constraintEmptyAllowedSetMeansMaxRestriction() {
        ParentPermissionConstraint c = new ParentPermissionConstraint(
                Set.of(), "agent", "sess");
        assertFalse(c.allows("read-file"));
        assertTrue(c.getAllowedTools().isEmpty());
    }

    @Test
    void constraintEqualsHashCode() {
        ParentPermissionConstraint a = new ParentPermissionConstraint(
                Set.of("read-file"), "agent", "sess");
        ParentPermissionConstraint b = new ParentPermissionConstraint(
                Set.of("read-file"), "agent", "sess");
        ParentPermissionConstraint diff = new ParentPermissionConstraint(
                Set.of("write-file"), "agent", "sess");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(diff));
    }

    @Test
    void constraintMetadataKeyIsWellKnown() {
        assertEquals("parentPermissionConstraint", ParentPermissionConstraint.METADATA_KEY);
    }

    // ---- ParentConstrainedToolAccessChecker ----

    @Test
    void toolInBothSetsDelegatesToChildChecker() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file", "call-agent"), "parent-agent", "parent-sess-1");

        IToolAccessChecker childAllows = (toolName, ctx) -> ToolAccessResult.allow();

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, childAllows);

        ToolAccessResult result = wrapper.checkAccess("read-file", newContext());
        assertTrue(result.isAllowed(),
                "Tool in parent's effective set should delegate to child checker; child allows it");
    }

    @Test
    void toolInBothSetsChildCanStillDeny() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess-1");

        IToolAccessChecker childDenies = (toolName, ctx) ->
                ToolAccessResult.deny("child rule denies read-file");

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, childDenies);

        ToolAccessResult result = wrapper.checkAccess("read-file", newContext());
        assertFalse(result.isAllowed(),
                "Even though parent allows, child checker's deny must still apply (intersection)");
        assertTrue(result.getReason().contains("child rule denies"));
    }

    @Test
    void toolOnlyInChildSetDeniedByParentConstraintWithExplicitReason() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess-1");

        IToolAccessChecker childAllows = (toolName, ctx) -> ToolAccessResult.allow();

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, childAllows);

        ToolAccessResult result = wrapper.checkAccess("write-file", newContext());
        assertFalse(result.isAllowed(),
                "Tool not in parent's effective set must be denied by parent constraint (fail-closed)");
        assertNotNull(result.getReason());
        assertTrue(result.getReason().contains("parent permission constraint"),
                "Denial reason must explicitly identify 'parent permission constraint'. Got: "
                        + result.getReason());
        assertTrue(result.getReason().contains("write-file"),
                "Denial reason must name the denied tool. Got: " + result.getReason());
        assertTrue(result.getReason().contains("parent-agent"),
                "Denial reason must name the parent agent. Got: " + result.getReason());
    }

    @Test
    void emptyParentAllowedSetDeniesAllTools() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of(), "parent-agent", "parent-sess-1");

        IToolAccessChecker childAllows = (toolName, ctx) -> ToolAccessResult.allow();

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, childAllows);

        ToolAccessResult r1 = wrapper.checkAccess("read-file", newContext());
        ToolAccessResult r2 = wrapper.checkAccess("bash", newContext());
        ToolAccessResult r3 = wrapper.checkAccess("call-agent", newContext());

        assertFalse(r1.isAllowed(), "empty parent set denies all tools");
        assertFalse(r2.isAllowed(), "empty parent set denies all tools");
        assertFalse(r3.isAllowed(), "empty parent set denies all tools");
        assertTrue(r1.getReason().contains("parent permission constraint"));
    }

    @Test
    void wrapperConstructorRejectsNullConstraint() {
        IToolAccessChecker delegate = (toolName, ctx) -> ToolAccessResult.allow();
        assertThrows(NopAiAgentException.class,
                () -> new ParentConstrainedToolAccessChecker(null, delegate));
    }

    @Test
    void wrapperConstructorRejectsNullDelegate() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "agent", "sess");
        assertThrows(NopAiAgentException.class,
                () -> new ParentConstrainedToolAccessChecker(constraint, null));
    }

    @Test
    void wrapperAccessorMethods() {
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");
        IToolAccessChecker delegate = (toolName, ctx) -> ToolAccessResult.allow();

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, delegate);

        assertEquals(constraint, wrapper.getConstraint());
        assertEquals(delegate, wrapper.getDelegate());
    }

    @Test
    void integrationWithDefaultToolAccessChecker_parentAllowsChildRuleAlsoDenies() {
        // Parent allows 'write-file' (hypothetically), child (DefaultToolAccessChecker) denies it.
        // Parent constraint passes, child rule denies.
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("write-file"), "parent-agent", "parent-sess");

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, new DefaultToolAccessChecker());

        ToolAccessResult result = wrapper.checkAccess("write-file", newContext());
        assertFalse(result.isAllowed(),
                "Child DefaultToolAccessChecker hardcoded deny should still apply");
        assertTrue(result.getReason().contains("write-file"));
    }

    @Test
    void integrationWithDefaultToolAccessChecker_parentDeniesBeforeChildChecked() {
        // Parent does NOT allow 'bash'; child (DefaultToolAccessChecker) would also deny it.
        // Parent constraint fires first → denial reason identifies parent constraint, not child rule.
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, new DefaultToolAccessChecker());

        ToolAccessResult result = wrapper.checkAccess("bash", newContext());
        assertFalse(result.isAllowed());
        assertTrue(result.getReason().contains("parent permission constraint"),
                "Parent constraint must fire first for tools not in parent set, producing parent-constraint reason. Got: "
                        + result.getReason());
    }

    @Test
    void integrationWithAllowAllChecker_parentDeniesEvenWhenChildAllowsAll() {
        // Parent does NOT allow 'write-file'; child (AllowAllToolAccessChecker) would allow it.
        // Parent constraint must still deny (fail-closed).
        ParentPermissionConstraint constraint = new ParentPermissionConstraint(
                Set.of("read-file"), "parent-agent", "parent-sess");

        ParentConstrainedToolAccessChecker wrapper =
                new ParentConstrainedToolAccessChecker(constraint, new AllowAllToolAccessChecker());

        ToolAccessResult result = wrapper.checkAccess("write-file", newContext());
        assertFalse(result.isAllowed(),
                "Parent constraint must deny even when child is AllowAll (cannot escalate)");
        assertTrue(result.getReason().contains("parent permission constraint"));
    }
}
