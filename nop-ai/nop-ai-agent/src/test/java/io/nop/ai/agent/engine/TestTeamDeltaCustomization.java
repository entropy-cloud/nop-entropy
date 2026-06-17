package io.nop.ai.agent.engine;

import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.model.TeamModel;
import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.component.ResourceComponentManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Plan 231 Phase 3: verifies that the declarative {@code <team>} element
 * honours Delta customisation via the platform's standard {@code xdsl-loader}
 * mechanism. A delta override at {@code /delta/test-team-lead.agent.xml}
 * replaces the base {@code <team>} (teamName + roster) without any extra
 * wiring.
 */
public class TestTeamDeltaCustomization {

    @BeforeAll
    static void init() {
        CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT);
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    void deltaOverrideReplacesTeamDeclaration() {
        // The /delta/default/test-team-delta-base.agent.xml override replaces
        // the base <team> (teamName, description, roster) via x:extends="super"
        // + x:override="replace". Loading /test-team-delta-base.agent.xml must
        // yield the merged (delta-applied) model. A dedicated agent name is
        // used so the delta does not interfere with the team-binding fixtures.
        AgentModel model = (AgentModel) ResourceComponentManager.instance()
                .loadComponentModel("/test-team-delta-base.agent.xml");

        assertNotNull(model, "delta-overridden agent.xml must load");
        TeamModel team = model.getTeam();
        assertNotNull(team, "<team> must still be present after delta merge");
        assertEquals("delta-overridden-team", team.getTeamName(),
                "delta must override teamName");
        assertEquals("delta-overridden description", team.getDescription());
        assertEquals(2, team.getMembers().size(),
                "delta must override the roster (2 members, not the base 3)");
        assertEquals("gamma", team.getMembers().get(1).getName(),
                "delta roster member should be gamma, not the base beta");
    }
}
