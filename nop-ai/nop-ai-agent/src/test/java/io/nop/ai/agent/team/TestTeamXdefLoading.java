package io.nop.ai.agent.team;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.TeamMemberRefModel;
import io.nop.ai.agent.model.TeamModel;
import io.nop.ai.agent.team.MemberRole;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 231 Phase 1: verifies the {@code <team>} / {@code <team-member>}
 * schema additions to {@code agent.xdef} load correctly, and that existing
 * {@code .agent.xml} files without team declarations continue to load with
 * zero behaviour regression ({@code getTeam()}/{@code getTeamMember()}
 * return {@code null}).
 */
public class TestTeamXdefLoading {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void existingAgentWithoutTeamLoadsAndGetTeamReturnsNull() {
        // Zero-regression: a pre-231 agent.xml (no <team>/<team-member>)
        // must still load, and the new optional accessors must return null.
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-react-agent.agent.xml");

        assertNotNull(model, "existing agent.xml must still load");
        assertNull(model.getTeam(),
                "getTeam() must be null when <team> is absent (zero regression)");
        assertNull(model.getTeamMember(),
                "getTeamMember() must be null when <team-member> is absent (zero regression)");
    }

    @Test
    void teamLeadAgentXmlLoads() {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-team-lead.agent.xml");

        assertNotNull(model, "team-lead agent.xml must load");
        TeamModel team = model.getTeam();
        assertNotNull(team, "<team> must be parsed");
        assertEquals("test-team", team.getTeamName());
        assertEquals("test-team-lead", team.getLeadAgentName());
        assertEquals(3, team.getMembers().size());
        assertEquals("test-team-lead", team.getMembers().get(0).getName());
        assertEquals(MemberRole.LEAD, team.getMembers().get(0).getRole());
        assertEquals("member-a", team.getMembers().get(1).getName());
        assertEquals(MemberRole.MEMBER, team.getMembers().get(1).getRole());
    }

    @Test
    void teamMemberAgentXmlLoads() {
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-team-member-a.agent.xml");

        assertNotNull(model, "team-member agent.xml must load");
        TeamMemberRefModel ref = model.getTeamMember();
        assertNotNull(ref, "<team-member> must be parsed");
        assertEquals("test-team", ref.getTeamName());
        assertEquals("member-a", ref.getMemberName());
    }
}
