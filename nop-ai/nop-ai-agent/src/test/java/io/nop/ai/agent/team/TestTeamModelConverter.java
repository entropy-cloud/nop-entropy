package io.nop.ai.agent.team;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.TeamMemberModel;
import io.nop.ai.agent.model.TeamModel;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 231 Phase 1: focused unit tests for {@link TeamModelConverter}.
 * Covers field fidelity (round-trip), {@link MemberRole} mapping, the
 * lead-in-roster guarantee (Design Decision #3), empty-member edge cases,
 * and fail-fast on missing required fields (Minimum Rules #24 — no silent
 * default / null return).
 */
public class TestTeamModelConverter {

    @Test
    void toTeamSpecMapsAllFieldsAndMembers() {
        TeamModel model = baseLeadModel("research-team", "Research squad", "lead-1", 4);
        model.setMembers(Arrays.asList(
                member("lead-1", "lead-agent", MemberRole.LEAD),
                member("alice", "alice-agent", MemberRole.MEMBER),
                member("bob", "bob-agent", MemberRole.MEMBER)));

        TeamSpec spec = TeamModelConverter.toTeamSpec(model, "lead-1");

        assertEquals("research-team", spec.getTeamName());
        assertEquals("Research squad", spec.getDescription());
        assertEquals("lead-1", spec.getLeadAgentName());
        assertEquals(4, spec.getMaxParallelMembers());
        assertEquals(3, spec.getMemberSpecs().size());
        assertMember(spec.getMemberSpecs().get(0), "lead-1", "lead-agent", MemberRole.LEAD);
        assertMember(spec.getMemberSpecs().get(1), "alice", "alice-agent", MemberRole.MEMBER);
        assertMember(spec.getMemberSpecs().get(2), "bob", "bob-agent", MemberRole.MEMBER);
    }

    @Test
    void descriptionDefaultsToNullWhenAbsent() {
        TeamModel model = baseLeadModel("t", null, "lead", 0);
        TeamSpec spec = TeamModelConverter.toTeamSpec(model, "lead");
        assertNull(spec.getDescription());
    }

    @Test
    void maxParallelMembersNullMeansUnlimitedZero() {
        TeamModel model = baseLeadModel("t", null, "lead", null);
        TeamSpec spec = TeamModelConverter.toTeamSpec(model, "lead");
        assertEquals(0, spec.getMaxParallelMembers());
    }

    @Test
    void emptyRosterStillRegistersLeadAsLeadMember() {
        // Design Decision #3: lead not in roster -> converter appends a
        // synthetic LEAD member so the lead self-binding succeeds.
        TeamModel model = baseLeadModel("solo-team", null, "solo-lead", 0);
        model.setMembers(Collections.emptyList());

        TeamSpec spec = TeamModelConverter.toTeamSpec(model, "solo-agent");

        assertEquals(1, spec.getMemberSpecs().size());
        TeamMemberSpec lead = spec.getMemberSpecs().get(0);
        assertEquals("solo-lead", lead.getMemberName());
        assertEquals(MemberRole.LEAD, lead.getRole());
        // synthetic lead member's agentModel defaults to the lead agent's
        // own name (no separate lead-agent-model field in <team>).
        assertEquals("solo-agent", lead.getAgentModel());
    }

    @Test
    void leadDeclaredInRosterWithMemberRoleIsNormalisedToLead() {
        // Even if the roster lists the lead with role=MEMBER, the converter
        // normalises it to LEAD (leadAgentName always maps to LEAD).
        TeamModel model = baseLeadModel("t", null, "lead", 0);
        model.setMembers(Collections.singletonList(
                member("lead", "lead-agent", MemberRole.MEMBER)));

        TeamSpec spec = TeamModelConverter.toTeamSpec(model, "lead");

        assertEquals(1, spec.getMemberSpecs().size());
        assertEquals(MemberRole.LEAD, spec.getMemberSpecs().get(0).getRole());
        assertEquals("lead-agent", spec.getMemberSpecs().get(0).getAgentModel());
    }

    @Test
    void roleDefaultsToMemberWhenAbsent() {
        TeamMemberModel m = member("x", "x-agent", null);
        assertEquals(MemberRole.MEMBER, TeamModelConverter.toMemberSpec(m).getRole());
    }

    @Test
    void toTeamSpecFailsFastWhenTeamNameMissing() {
        TeamModel model = new TeamModel();
        model.setLeadAgentName("lead");
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> TeamModelConverter.toTeamSpec(model, "lead"));
        assertTrue(ex.getMessage().contains("teamName"));
    }

    @Test
    void toTeamSpecFailsFastWhenLeadAgentNameMissing() {
        TeamModel model = new TeamModel();
        model.setTeamName("t");
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> TeamModelConverter.toTeamSpec(model, "lead"));
        assertTrue(ex.getMessage().contains("leadAgentName"));
    }

    @Test
    void toMemberSpecFailsFastWhenNameMissing() {
        TeamMemberModel m = new TeamMemberModel();
        m.setAgentModel("a");
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> TeamModelConverter.toMemberSpec(m));
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void toMemberSpecFailsFastWhenAgentModelMissing() {
        TeamMemberModel m = new TeamMemberModel();
        m.setName("n");
        NopAiAgentException ex = assertThrows(NopAiAgentException.class,
                () -> TeamModelConverter.toMemberSpec(m));
        assertTrue(ex.getMessage().contains("agentModel"));
    }

    @Test
    void toTeamSpecRejectsNullModel() {
        assertThrows(NopAiAgentException.class,
                () -> TeamModelConverter.toTeamSpec(null, "lead"));
    }

    @Test
    void toMemberSpecRejectsNullModel() {
        assertThrows(NopAiAgentException.class,
                () -> TeamModelConverter.toMemberSpec(null));
    }

    @Test
    void nullMembersListTreatedAsEmpty() {
        TeamModel model = baseLeadModel("t", null, "lead", 0);
        model.setMembers(null);
        TeamSpec spec = TeamModelConverter.toTeamSpec(model, "lead");
        // lead is auto-registered even when members list is null
        assertNotNull(spec.getMemberSpecs());
        assertEquals(1, spec.getMemberSpecs().size());
        assertEquals("lead", spec.getMemberSpecs().get(0).getMemberName());
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private static void assertMember(TeamMemberSpec actual, String name,
                                     String agentModel, MemberRole role) {
        assertEquals(name, actual.getMemberName(), "memberName");
        assertEquals(agentModel, actual.getAgentModel(), "agentModel");
        assertEquals(role, actual.getRole(), "role");
    }

    private TeamModel baseLeadModel(String teamName, String description,
                                    String leadAgentName, Integer maxParallel) {
        TeamModel model = new TeamModel();
        model.setTeamName(teamName);
        model.setDescription(description);
        model.setLeadAgentName(leadAgentName);
        model.setMaxParallelMembers(maxParallel);
        return model;
    }

    private TeamMemberModel member(String name, String agentModel, MemberRole role) {
        TeamMemberModel m = new TeamMemberModel();
        m.setName(name);
        m.setAgentModel(agentModel);
        m.setRole(role);
        return m;
    }
}
