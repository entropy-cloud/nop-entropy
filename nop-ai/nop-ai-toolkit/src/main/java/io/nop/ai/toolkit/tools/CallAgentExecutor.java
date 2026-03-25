package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiAgentCallResult;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.core.lang.xml.XNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class CallAgentExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "call-agent";

    private static final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        return context.getExecutor().submit(() -> doExecute(call, context));
    }

    private AiToolCallResult doExecute(AiToolCall call, IToolExecuteContext context) {
        try {
            String agentId = call.attrText("agentId");
            String sessionId = call.attrText("sessionId");
            Set<String> skills = parseSkills(call.attrText("skills"));
            boolean inheritContext = call.attrBoolean("inheritContext", false);
            String prompt = call.childText("input");
            int timeoutMs = call.attrInt("timeoutMs", call.getTimeoutMs() != null ? call.getTimeoutMs() : 30000);

            if (agentId == null || agentId.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "agentId is required");
            }

            AgentSession session;
            if (sessionId != null && !sessionId.isEmpty()) {
                session = sessions.get(sessionId);
                if (session == null) {
                    sessionId = generateSessionId();
                    session = new AgentSession(sessionId, agentId);
                    sessions.put(sessionId, session);
                }
            } else {
                sessionId = generateSessionId();
                session = new AgentSession(sessionId, agentId);
                sessions.put(sessionId, session);
            }

            AiAgentCallResult result = new AiAgentCallResult();
            result.setId(call.getId());
            result.setSessionId(sessionId);

            StringBuilder responseBuilder = new StringBuilder();
            if ("self".equals(agentId)) {
                responseBuilder.append("Sub-agent created with sessionId: ").append(sessionId).append("\n");
                if (prompt != null && !prompt.isEmpty()) {
                    responseBuilder.append("Prompt received: ").append(prompt.substring(0, Math.min(100, prompt.length())));
                    if (prompt.length() > 100) responseBuilder.append("...");
                    responseBuilder.append("\n");
                }
                List<XNode> inputFileNodes = getInputFileNodes(call);
                if (!inputFileNodes.isEmpty()) {
                    responseBuilder.append("Input files: ").append(inputFileNodes.size()).append(" file(s)\n");
                }
            } else {
                responseBuilder.append("Agent '").append(agentId).append("' called with sessionId: ").append(sessionId).append("\n");
                if (prompt != null && !prompt.isEmpty()) {
                    responseBuilder.append("Prompt: ").append(prompt.substring(0, Math.min(100, prompt.length())));
                    if (prompt.length() > 100) responseBuilder.append("...");
                    responseBuilder.append("\n");
                }
            }

            result.setStatus("success");
            AiToolOutput output = new AiToolOutput();
            output.setBody(responseBuilder.toString());
            result.setOutput(output);

            return result;
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e);
        }
    }

    private String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private Set<String> parseSkills(String skillsStr) {
        Set<String> skills = new HashSet<>();
        if (skillsStr != null && !skillsStr.isEmpty()) {
            for (String skill : skillsStr.split(",")) {
                skills.add(skill.trim());
            }
        }
        return skills;
    }

    @SuppressWarnings("unchecked")
    private List<XNode> getInputFileNodes(AiToolCall call) {
        XNode node = call.getNode();
        if (node == null) return List.of();
        XNode inputFilesNode = node.childByTag("input-files");
        if (inputFilesNode == null) return List.of();
        return inputFilesNode.getChildren();
    }

    private static class AgentSession {
        String sessionId;
        String agentId;
        long createdAt;

        AgentSession(String sessionId, String agentId) {
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.createdAt = System.currentTimeMillis();
        }
    }
}
