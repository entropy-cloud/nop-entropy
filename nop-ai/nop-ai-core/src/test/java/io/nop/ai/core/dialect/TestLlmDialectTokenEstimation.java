package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ApiStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ILlmDialect#estimateTokens(List)} default implementation
 * and {@link AbstractLlmDialect#estimateTokensBaseline(List)}.
 */
public class TestLlmDialectTokenEstimation {

    private final ILlmDialect dialect = LlmDialectFactory.getDialect(ApiStyle.openai);

    @Test
    void emptyListReturnsZero() {
        assertEquals(0, dialect.estimateTokens(Collections.emptyList()));
    }

    @Test
    void nullListReturnsZero() {
        assertEquals(0, dialect.estimateTokens(null));
    }

    @Test
    void nullContentSkippedSafely() {
        ChatUserMessage msg = new ChatUserMessage();
        msg.setContent(null);
        List<ChatMessage> messages = Collections.singletonList(msg);

        long result = dialect.estimateTokens(messages);
        assertEquals(AbstractLlmDialect.PER_MESSAGE_TOKEN_OVERHEAD, result,
                "Null content should only contribute the per-message overhead");
    }

    @Test
    void estimateIsMonotonic() {
        ChatUserMessage shortMsg = new ChatUserMessage("ab");
        ChatUserMessage longMsg = new ChatUserMessage("abcdefghijklmnopqrstuvwxyz0123456789");

        long shortEstimate = dialect.estimateTokens(Collections.singletonList(shortMsg));
        long longEstimate = dialect.estimateTokens(Collections.singletonList(longMsg));

        assertTrue(longEstimate > shortEstimate,
                "More content should yield strictly greater estimate");
    }

    @Test
    void addingMessageIncreasesEstimate() {
        ChatUserMessage msg1 = new ChatUserMessage("hello world");
        ChatUserMessage msg2 = new ChatUserMessage("another message");

        long one = dialect.estimateTokens(Collections.singletonList(msg1));
        long two = dialect.estimateTokens(Arrays.asList(msg1, msg2));

        assertTrue(two > one, "Adding a message should increase the estimate");
    }

    @Test
    void estimateIsDeterministic() {
        List<ChatMessage> messages = Arrays.asList(
                new ChatSystemMessage("system prompt"),
                new ChatUserMessage("user message")
        );

        long first = dialect.estimateTokens(messages);
        long second = dialect.estimateTokens(messages);

        assertEquals(first, second, "Same input should produce same output");
    }

    @Test
    void baselineMatchesContentLengthDividedByFourPlusOverhead() {
        String content = "12345678";
        ChatUserMessage msg = new ChatUserMessage(content);

        long expected = content.length() / AbstractLlmDialect.CHARS_PER_TOKEN
                + AbstractLlmDialect.PER_MESSAGE_TOKEN_OVERHEAD;
        long actual = dialect.estimateTokens(Collections.singletonList(msg));

        assertEquals(expected, actual);
    }

    @Test
    void allDialectsInheritDefault() {
        for (ApiStyle style : new ApiStyle[]{ApiStyle.openai, ApiStyle.anthropic,
                ApiStyle.gemini, ApiStyle.ollama}) {
            ILlmDialect d = LlmDialectFactory.getDialect(style);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatUserMessage("test message"));

            long estimate = d.estimateTokens(messages);
            assertTrue(estimate > 0, style + " dialect should estimate > 0 for non-empty content");
        }
    }
}
