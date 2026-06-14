package io.nop.ai.agent.talent;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestNoOpTalent {

    @Test
    void admissionGateAlwaysReturnsFalse() {
        ITalent talent = NoOpTalent.noOp();

        assertFalse(talent.isSupported(null));

        AgentModel model = new AgentModel();
        AgentExecutionContext ctx = AgentExecutionContext.create(model, "test-session");
        assertFalse(talent.isSupported(ctx));
    }

    @Test
    void factoryReturnsSingleton() {
        ITalent a = NoOpTalent.noOp();
        ITalent b = NoOpTalent.noOp();
        assertSame(a, b);
    }

    @Test
    void implementsITalent() {
        assertTrue(ITalent.class.isAssignableFrom(NoOpTalent.class));
        assertTrue(NoOpTalent.noOp() instanceof ITalent);
    }

    @Test
    void inactiveMethodsAreConsistentWithNeverActivatedTalent() {
        ITalent talent = NoOpTalent.noOp();
        AgentExecutionContext ctx = AgentExecutionContext.create(new AgentModel(), "test-session");

        assertNull(talent.getInstruction(ctx));
        List<String> tools = talent.getTools(ctx);
        assertTrue(tools != null && tools.isEmpty());

        // onAttach must be callable without side effects (defined pass-through).
        talent.onAttach(ctx);
    }
}
