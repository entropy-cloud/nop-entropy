package io.nop.ai.agent.security;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.model.AgentModel;
import io.nop.ai.api.chat.messages.ChatToolCall;

public interface SecurityCheckpoint {
    enum Decision {
        ALLOW,
        DENY,
        DENY_AND_BREAK
    }

    Decision check(CheckContext ctx);

    final class CheckContext {
        private final String sessionId;
        private final String agentName;
        private final ChatToolCall chatToolCall;
        private final AgentExecutionContext executionContext;
        private final String fingerprintWorkDir;
        private final SecurityLevel layer2ResolvedLevel;
        private final AgentModel agentModel;

        public CheckContext(String sessionId, String agentName,
                            ChatToolCall chatToolCall,
                            AgentExecutionContext executionContext,
                            String fingerprintWorkDir,
                            SecurityLevel layer2ResolvedLevel,
                            AgentModel agentModel) {
            this.sessionId = sessionId;
            this.agentName = agentName;
            this.chatToolCall = chatToolCall;
            this.executionContext = executionContext;
            this.fingerprintWorkDir = fingerprintWorkDir;
            this.layer2ResolvedLevel = layer2ResolvedLevel;
            this.agentModel = agentModel;
        }

        public String sessionId() { return sessionId; }
        public String agentName() { return agentName; }
        public ChatToolCall chatToolCall() { return chatToolCall; }
        public AgentExecutionContext executionContext() { return executionContext; }
        public String fingerprintWorkDir() { return fingerprintWorkDir; }
        public SecurityLevel layer2ResolvedLevel() { return layer2ResolvedLevel; }
        public AgentModel agentModel() { return agentModel; }

        public String toolName() {
            return chatToolCall.getName();
        }

        public CheckContext withResolvedLevel(SecurityLevel level) {
            return new CheckContext(sessionId, agentName, chatToolCall, executionContext,
                    fingerprintWorkDir, level, agentModel);
        }

        public static CheckContext create(String sessionId, String agentName,
                                          ChatToolCall chatToolCall,
                                          AgentExecutionContext ctx,
                                          String fingerprintWorkDir,
                                          AgentModel agentModel) {
            return new CheckContext(sessionId, agentName, chatToolCall, ctx,
                    fingerprintWorkDir, null, agentModel);
        }
    }
}
