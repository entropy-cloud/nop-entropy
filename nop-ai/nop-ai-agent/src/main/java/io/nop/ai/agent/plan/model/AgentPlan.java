package io.nop.ai.agent.plan.model;

import io.nop.ai.agent.plan.model._gen._AgentPlan;
import io.nop.ai.agent.plan.runtime.AgentPlanValidator;
import io.nop.api.core.util.INeedInit;

public class AgentPlan extends _AgentPlan implements INeedInit {
    public AgentPlan() {
    }

    @Override
    public void init() {
        new AgentPlanValidator().validate(this);
    }
}
