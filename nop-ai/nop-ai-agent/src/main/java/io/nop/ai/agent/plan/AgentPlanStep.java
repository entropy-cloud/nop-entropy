package io.nop.ai.agent.plan;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AgentPlanStep {
    private String name;
    private String description;
    private AgentPlanStepStatus stepStatus;
    private String errorReason;
    private String resultMessage;
    private String instructions;
}
