package io.nop.ai.agent.plan;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AgentPlanNote {
    private String name;
    private String content;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
