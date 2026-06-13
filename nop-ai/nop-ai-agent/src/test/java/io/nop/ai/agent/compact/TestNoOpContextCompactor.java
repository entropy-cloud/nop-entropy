package io.nop.ai.agent.compact;

import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.dialect.AbstractLlmDialect;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestNoOpContextCompactor {

    @Test
    void compactReturnsEqualTokenCounts() {
        List<ChatMessage> messages = Arrays.asList(
                new ChatUserMessage("hello"),
                new ChatUserMessage("world")
        );

        CompactionContext ctx = new CompactionContext(
                messages, null, "s1", "agent1", null
        );

        NoOpContextCompactor compactor = NoOpContextCompactor.INSTANCE;
        CompactionResult result = compactor.compact(ctx);

        assertEquals(result.getTokensBefore(), result.getTokensAfter());
    }

    @Test
    void compactUsesCalibratedEstimatorForTokens() {
        String content = "12345678";
        List<ChatMessage> messages = Collections.singletonList(
                new ChatUserMessage(content)
        );

        CompactionContext ctx = new CompactionContext(
                messages, null, "s1", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);

        long expected = content.length() / AbstractLlmDialect.CHARS_PER_TOKEN
                + AbstractLlmDialect.PER_MESSAGE_TOKEN_OVERHEAD;
        assertEquals(expected, result.getTokensBefore(),
                "Estimate should be content/4 + per-message overhead");
        assertEquals(expected, result.getTokensAfter());
    }

    @Test
    void compactReturnsMessageCountAsRetained() {
        List<ChatMessage> messages = Arrays.asList(
                new ChatUserMessage("a"),
                new ChatUserMessage("b"),
                new ChatUserMessage("c")
        );

        CompactionContext ctx = new CompactionContext(
                messages, null, "s1", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);

        assertEquals(3, result.getRetainedMessageCount());
    }

    @Test
    void compactReturnsNullSnapshotId() {
        CompactionContext ctx = new CompactionContext(
                Collections.singletonList(new ChatUserMessage("hello")),
                null, "s1", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);

        assertNull(result.getSnapshotId());
    }

    @Test
    void compactReturnsSessionId() {
        CompactionContext ctx = new CompactionContext(
                Collections.singletonList(new ChatUserMessage("hello")),
                null, "my-session", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);

        assertEquals("my-session", result.getSessionId());
    }

    @Test
    void compactReturnsNullCompactedMessages() {
        CompactionContext ctx = new CompactionContext(
                Collections.singletonList(new ChatUserMessage("hello")),
                null, "s1", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);

        assertNull(result.getCompactedMessages());
    }

    @Test
    void singletonInstanceExists() {
        assertNotNull(NoOpContextCompactor.INSTANCE);
    }

    @Test
    void emptyMessagesReturnZeroCounts() {
        CompactionContext ctx = new CompactionContext(
                Collections.emptyList(), null, "s1", "agent1", null
        );

        CompactionResult result = NoOpContextCompactor.INSTANCE.compact(ctx);

        assertEquals(0, result.getTokensBefore());
        assertEquals(0, result.getTokensAfter());
        assertEquals(0, result.getRetainedMessageCount());
    }
}
