package io.nop.ai.agent.engine;

import io.nop.ai.agent.security.AllowAllPathAccessChecker;
import io.nop.ai.agent.security.AllowAllPermissionProvider;
import io.nop.ai.agent.security.AllowAllToolAccessChecker;
import io.nop.ai.agent.security.NoOpAuditLogger;
import io.nop.ai.api.chat.IChatService;
import io.nop.ai.toolkit.api.IToolManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestReActAgentExecutorBuilder {

    @Test
    void missingChatServiceThrows() {
        IToolManager toolManager = new TestReActAgentExecutor.NoOpToolManager();

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                ReActAgentExecutor.builder().toolManager(toolManager).build());
        assertTrue(ex.getMessage().contains("chatService"));
    }

    @Test
    void missingToolManagerThrows() {
        IChatService chatService = new TestReActAgentExecutor.StubChatService(null);

        NopAiAgentException ex = assertThrows(NopAiAgentException.class, () ->
                ReActAgentExecutor.builder().chatService(chatService).build());
        assertTrue(ex.getMessage().contains("toolManager"));
    }

    @Test
    void allDefaultsAppliedWhenOnlyRequiredSet() {
        IChatService chatService = new TestReActAgentExecutor.StubChatService(null);
        IToolManager toolManager = new TestReActAgentExecutor.NoOpToolManager();

        ReActAgentExecutor executor = assertDoesNotThrow(() ->
                ReActAgentExecutor.builder()
                        .chatService(chatService)
                        .toolManager(toolManager)
                        .build());

        assertTrue(executor instanceof ReActAgentExecutor);
    }

    @Test
    void fullyConfiguredBuilderProducesInstance() {
        IChatService chatService = new TestReActAgentExecutor.StubChatService(null);
        IToolManager toolManager = new TestReActAgentExecutor.NoOpToolManager();
        DefaultAgentEventPublisher publisher = new DefaultAgentEventPublisher();

        ReActAgentExecutor executor = ReActAgentExecutor.builder()
                .chatService(chatService)
                .toolManager(toolManager)
                .eventPublisher(publisher)
                .permissionProvider(new AllowAllPermissionProvider())
                .toolAccessChecker(new AllowAllToolAccessChecker())
                .pathAccessChecker(new AllowAllPathAccessChecker())
                .auditLogger(new NoOpAuditLogger())
                .build();

        assertTrue(executor instanceof ReActAgentExecutor);
    }
}
