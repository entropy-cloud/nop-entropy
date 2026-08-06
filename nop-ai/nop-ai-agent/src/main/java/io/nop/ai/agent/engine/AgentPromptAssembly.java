package io.nop.ai.agent.engine;

import io.nop.ai.agent.contribution.Contribution;
import io.nop.ai.agent.contribution.ContributionType;
import io.nop.ai.agent.contribution.IContributionRegistry;
import io.nop.ai.agent.contribution.NoOpContributionRegistry;
import io.nop.ai.agent.guardrail.GuardrailDirection;
import io.nop.ai.agent.guardrail.GuardrailResult;
import io.nop.ai.agent.guardrail.IContentGuardrail;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.agent.session.AgentSession;
import io.nop.ai.agent.skill.ISkillProvider;
import io.nop.ai.agent.skill.NoOpSkillProvider;
import io.nop.ai.agent.skill.SkillAssemblyResult;
import io.nop.ai.agent.skill.SkillResolver;
import io.nop.ai.agent.talent.ITalent;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolDefinition;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.toolkit.api.IToolManager;
import io.nop.ai.toolkit.model.AiToolModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import java.util.ArrayList;
import java.util.List;

/**
 * Execution-setup prompt assembly for the ReAct loop (extracted from
 * {@link ReActAgentExecutor}, MA4.2-05). Consults talents, skills and
 * PROMPT contributions, injects system instructions, checks the input /
 * output content guardrails and builds the {@link ChatOptions} for the
 * iteration. Tool-model resolution goes through the injected
 * {@link AgentToolPlanResolver}.
 */
public class AgentPromptAssembly {
    private static final Logger LOG = LoggerFactory.getLogger(AgentPromptAssembly.class);

    private final List<ITalent> talents;
    private final ISkillProvider skillProvider;
    private final IContributionRegistry contributionRegistry;
    private final IContentGuardrail contentGuardrail;
    private final IToolManager toolManager;
    private final AgentToolPlanResolver toolPlanResolver;

    public AgentPromptAssembly(List<ITalent> talents,
                               ISkillProvider skillProvider,
                               IContributionRegistry contributionRegistry,
                               IContentGuardrail contentGuardrail,
                               IToolManager toolManager,
                               AgentToolPlanResolver toolPlanResolver) {
        this.talents = talents;
        this.skillProvider = skillProvider;
        this.contributionRegistry = contributionRegistry;
        this.contentGuardrail = contentGuardrail;
        this.toolManager = toolManager;
        this.toolPlanResolver = toolPlanResolver;
    }

    // ---- moved verbatim from ReActAgentExecutor (MA4.2-05 split) ----
    public GuardrailResult checkInputGuardrail(AgentExecutionContext ctx) {
        String inputContent = extractLastUserContent(ctx);
        GuardrailResult result = contentGuardrail.check(GuardrailDirection.INPUT, inputContent, ctx);
        if (result.isModify()) {
            String modifiedContent = ((GuardrailResult.ModifyResult) result).getContent();
            List<ChatMessage> messages = ctx.getMessages();
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof ChatUserMessage) {
                    messages.get(i).setContent(modifiedContent);
                    break;
                }
            }
        }
        return result;
    }

    public String extractLastUserContent(AgentExecutionContext ctx) {
        List<ChatMessage> messages = ctx.getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof ChatUserMessage) {
                return messages.get(i).getContent();
            }
        }
        return "";
    }
    /**
     * Consult registered talents once at execution setup (before the first LLM
     * call). For each talent whose admission gate passes, fire its activation
     * callback, then merge its dynamic instruction fragment into the
     * system-prompt context and its dynamic tool set (resolved through the
     * existing {@code IToolManager} pipeline) into {@code toolDefs}. All merges
     * are additive; an inactive talent is excluded only because its gate
     * explicitly returned false.
     */
    public void consultTalents(AgentExecutionContext ctx, List<ChatToolDefinition> toolDefs) {
        if (talents.isEmpty()) {
            return;
        }

        List<String> instructions = new ArrayList<>();
        List<String> talentToolNames = new ArrayList<>();

        for (ITalent talent : talents) {
            if (talent.isSupported(ctx)) {
                talent.onAttach(ctx);
                String instruction = talent.getInstruction(ctx);
                if (instruction != null && !instruction.isEmpty()) {
                    instructions.add(instruction);
                }
                List<String> tools = talent.getTools(ctx);
                if (tools != null) {
                    talentToolNames.addAll(tools);
                }
            }
        }

        for (String toolName : talentToolNames) {
            AiToolModel toolModel = toolManager.loadTool(toolName);
            if (toolModel == null) {
                LOG.warn("Talent-provided tool not found in registry, skipping: toolName={} session={}",
                        toolName, ctx.getSessionId());
            } else {
                toolDefs.add(toolPlanResolver.toToolDefinition(toolModel));
            }
        }

        if (!instructions.isEmpty()) {
            injectSystemInstruction(ctx, String.join("\n\n", instructions));
        }
    }
    public void injectSystemInstruction(AgentExecutionContext ctx, String instruction) {
        List<ChatMessage> messages = ctx.getMessages();
        int insertAt = 0;
        while (insertAt < messages.size() && messages.get(insertAt) instanceof ChatSystemMessage) {
            insertAt++;
        }
        messages.add(insertAt, new ChatSystemMessage(instruction));
    }
    /**
     * Consult the skill resolver once at execution setup (before the first LLM
     * call, alongside {@link #consultTalents}). Resolves the agent's
     * {@code availableSkills} / {@code requiredSkills} declarations against the
     * registered {@link ISkillProvider}, then merges:
     * <ul>
     *   <li>Skill instruction fragments (goals) → system-prompt context via
     *       {@link #injectSystemInstruction} (additive to agent prompt and
     *       talent instructions).</li>
     *   <li>Skill tool-name dependencies → resolved through
     *       {@code IToolManager.loadTool()} and merged into {@code toolDefs}
     *       (additive to agent + talent tools, same access-check pipeline — no
     *       parallel tool type). Missing tools are skipped with a warning, same
     *       pattern as talent tools.</li>
     *   <li>resourceScope → logged at DEBUG for observability (not enforced in
     *       phase 1).</li>
     * </ul>
     *
     * <p>A missing {@code requiredSkill} propagates the resolver's
     * {@link NopAiAgentException} fail-fast before any LLM call. With the
     * default {@link NoOpSkillProvider} (or no skills declared), this method
     * resolves an empty assembly and injects nothing — backward compatible.
     */
    public void consultSkills(AgentExecutionContext ctx, AgentModel agentModel,
                               List<ChatToolDefinition> toolDefs) {
        SkillResolver resolver = new SkillResolver(skillProvider);
        SkillAssemblyResult assembly = resolver.resolve(agentModel);

        if (assembly.isEmpty()) {
            return;
        }

        for (String toolName : assembly.getToolDependencies()) {
            AiToolModel toolModel = toolManager.loadTool(toolName);
            if (toolModel == null) {
                LOG.warn("Skill-provided tool not found in registry, skipping: toolName={} skillNames={} session={}",
                        toolName, assembly.getActivatedSkillNames(), ctx.getSessionId());
            } else {
                toolDefs.add(toolPlanResolver.toToolDefinition(toolModel));
            }
        }

        List<String> instructions = assembly.getInstructions();
        if (!instructions.isEmpty()) {
            injectSystemInstruction(ctx, String.join("\n\n", instructions));
        }

        if (LOG.isDebugEnabled() && !assembly.getResourceScope().isEmpty()) {
            LOG.debug("Skill assembly resourceScope (collected for tracing, not enforced in phase 1): "
                    + "scope={} activatedSkills={} session={}",
                    assembly.getResourceScope(), assembly.getActivatedSkillNames(), ctx.getSessionId());
        }
    }
    /**
     * Plan 217 (L4-6): assembly-time PROMPT contribution resolution. Iterates
     * every {@link ContributionType#PROMPT} contribution in the wired
     * registry (returned in ascending priority order, stable), concatenates
     * their String fragments with a blank-line separator, and injects the
     * joined fragment into the system prompt context via
     * {@link #injectSystemInstruction} (additive, same mechanism as talent
     * and skill instructions). Runs once at execution setup, before the
     * first LLM call, alongside {@link #consultSkills}.
     *
     * <p>A PROMPT contribution whose payload is null or not a String is
     * logged at WARN and skipped — fail-visible, not a silent no-op
     * (Minimum Rules #24). A single bad contribution does not abort the
     * rest of the batch.
     *
     * <p>With the shipped {@link NoOpContributionRegistry} default the loop
     * iterates an empty list, so behaviour is unchanged.
     */
    public void consultPromptContributions(AgentExecutionContext ctx) {
        List<Contribution> prompts = contributionRegistry.getContributions(ContributionType.PROMPT);
        if (prompts.isEmpty()) {
            return;
        }
        List<String> fragments = new ArrayList<>(prompts.size());
        for (Contribution c : prompts) {
            Object payload = c.getPayload();
            if (!(payload instanceof String)) {
                LOG.warn("ReActAgentExecutor: skipping PROMPT contribution with non-String payload"
                        + " (expected String): type={}, id={}, source={}, payloadClass={}",
                        c.getType(), c.getId(), c.getSource(),
                        payload != null ? payload.getClass().getName() : "null");
                continue;
            }
            String fragment = (String) payload;
            if (!fragment.isEmpty()) {
                fragments.add(fragment);
            }
        }
        if (!fragments.isEmpty()) {
            injectSystemInstruction(ctx, String.join("\n\n", fragments));
        }
    }
    public ChatOptions buildChatOptions(ChatOptions model, List<ChatToolDefinition> toolDefs) {
        ChatOptions options = new ChatOptions();
        if (model != null) {
            if (model.getProvider() != null)
                options.setProvider(model.getProvider());
            if (model.getModel() != null)
                options.setModel(model.getModel());
            if (model.getTemperature() != null)
                options.setTemperature(model.getTemperature());
            if (model.getTopP() != null)
                options.setTopP(model.getTopP());
            if (model.getMaxTokens() != null)
                options.setMaxTokens(model.getMaxTokens());
            if (model.getTopK() != null)
                options.setTopK(model.getTopK());
            if (model.getStop() != null)
                options.setStop(model.getStop());
        }
        if (!toolDefs.isEmpty()) {
            options.setTools(toolDefs);
            options.autoToolChoice();
        }
        return options;
    }

    /**
     * One-shot execution-setup assembly (extracted from
     * ReActAgentExecutor.execute, MA4.2-05): consult talents, skills and
     * PROMPT contributions, then build the base ChatOptions carrying the
     * assembled tool definitions.
     *
     * @return the assembled options for this execution
     */
    public ChatOptions assembleExecutionSetup(AgentExecutionContext ctx,
                                              AgentModel agentModel,
                                              AgentSession agentSession,
                                              List<ChatToolDefinition> toolDefs) {
toolDefs.addAll(toolPlanResolver.buildToolDefinitions(agentModel, agentSession));
consultTalents(ctx, toolDefs);
consultSkills(ctx, agentModel, toolDefs);
consultPromptContributions(ctx);
ChatOptions options = buildChatOptions(ChatOptionsHelper.toChatOptions(agentModel.getChatOptions()), toolDefs);
        return options;
    }

    /**
     * Check the output content guardrail (extracted from
     * ReActAgentExecutor.execute, MA4.2-05). On BLOCK: injects an assistant
     * message (or per-tool error responses preserving the tool_call_id
     * pairing invariant) describing the block.
     *
     * @return {@code true} when the iteration must be skipped (blocked)
     */
    public boolean checkOutputGuardrail(AgentExecutionContext ctx, ChatAssistantMessage assistantMsg,
                                        List<ChatToolCall> toolCalls) {
        String outputContent = getOutputContent(assistantMsg);
        GuardrailResult outputGuardrailResult = contentGuardrail.check(GuardrailDirection.OUTPUT, outputContent, ctx);
        if (outputGuardrailResult.isBlock()) {
            String blockReason = ((GuardrailResult.BlockResult) outputGuardrailResult).getReason();
            String blockText = "Output blocked by content guardrail: "
                    + (blockReason != null ? blockReason : "unspecified");
            // AR-11 (plan 277): the assistant message is already committed
            // to ctx (added before this check). If it carries tool_calls,
            // every tool_call_id MUST receive a matching tool response —
            // otherwise the next LLM call sends an unpaired assistant
            // tool_call (HTTP 400 tool_call_id mismatch).
            // Plan 327: toolCalls source switched from the legacy
            // assistantMsg.getToolCalls() folded field to the
            // ChatToolCallMessage items extracted from response.getMessages()
            // (passed in by ReActAgentExecutor).
            if (toolCalls != null && !toolCalls.isEmpty()) {
                for (ChatToolCall tc : toolCalls) {
                    ctx.addMessage(ChatToolResponseMessage.error(
                            tc.getId(), tc.getName(), blockText));
                }
            } else {
                assistantMsg.setContent(blockText);
            }
            return true;
        }
        if (outputGuardrailResult.isModify()) {
            String modifiedContent = ((GuardrailResult.ModifyResult) outputGuardrailResult).getContent();
            assistantMsg.setContent(modifiedContent);
        }
        return false;
    }

    private static String getOutputContent(ChatAssistantMessage assistantMsg) {
        return assistantMsg.getContent() != null ? assistantMsg.getContent() : "";
    }

}
