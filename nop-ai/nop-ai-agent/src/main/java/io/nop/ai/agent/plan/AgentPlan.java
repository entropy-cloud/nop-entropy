package io.nop.ai.agent.plan;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class AgentPlan {
    // 在文件系统中的存储路径，可以用于plan的唯一id
    private String path;

    private String title;
    private String description;
    private AgentPlanStatus planStatus;
}
