package io.nop.ai.core.prompt;

/**
 * 定义Prompt中使用的XML标签常量
 */
public interface PromptConstants {
    /**
     * 定义AI需要扮演的专家角色或身份
     */
    String TAG_ROLE = "role";

    /**
     * 清晰、简洁地描述本次任务的核心目标是什么
     */
    String TAG_OBJECTIVE = "objective";

    /**
     * 包含所有提供给AI的原始信息、数据和上下文
     */
    String TAG_INPUT_CONTEXT = "input_context";

    /**
     * 专门用来描述AI在本次任务中可以使用的外部工具、函数或API
     */
    String TAG_TOOLS = "tools";

    /**
     * 当提示词中包含需要特别解释的分类、术语或概念时使用
     */
    String TAG_DEFINITIONS = "definitions";

    /**
     * 描述AI完成任务需要遵循的思考步骤或工作流程
     */
    String TAG_PROCESS = "process";

    /**
     * 指导AI如何思考、如何决策、如何执行任务。它们是关于工作流程和逻辑的指南
     */
    String TAG_RULES = "rules";

    /**
     * 定义AI必须遵守的硬性限制，规定了输出的绝对边界，以及什么事情是“禁止”的
     */
    String TAG_CONSTRAINTS = "constraints";

    /**
     * 详细定义AI最终输出的格式、结构和必须包含的字段
     */
    String TAG_OUTPUT_SPEC = "output_spec";

    /**
     * 提供一个或多个期望的输出示例
     */
    String TAG_OUTPUT_EXAMPLES = "output_examples";
}
