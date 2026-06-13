package io.nop.ai.agent.compact;

import io.nop.ai.agent.session.CompactionResult;
import io.nop.ai.agent.session.CompactConfig;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMicroCompressionCompactor {

    private final MicroCompressionCompactor compactor = new MicroCompressionCompactor();

    private CompactionContext makeContext(List<ChatMessage> messages) {
        return new CompactionContext(messages, CompactConfig.defaults(), "s1", "agent1", null);
    }

    private CompactionContext makeContext(List<ChatMessage> messages, int maxRecent) {
        CompactConfig config = new CompactConfig(0, null, true, maxRecent, 8000);
        return new CompactionContext(messages, config, "s1", "agent1", null);
    }

    private ChatToolResponseMessage toolResponse(String toolCallId, String toolName, String content) {
        return new ChatToolResponseMessage(toolCallId, toolName, content);
    }

    private ChatAssistantMessage assistantWithToolCalls(String... toolCallIds) {
        ChatAssistantMessage msg = new ChatAssistantMessage();
        List<ChatToolCall> calls = new ArrayList<>();
        for (String id : toolCallIds) {
            ChatToolCall call = new ChatToolCall();
            call.setId(id);
            call.setName("bash");
            calls.add(call);
        }
        msg.setToolCalls(calls);
        return msg;
    }

    @Test
    void compressionReducesTotalContentLength() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));

        for (int i = 0; i < 10; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "bash", "X".repeat(5000)));
        }

        CompactionResult result = compactor.compact(makeContext(messages));

        assertNotNull(result.getCompactedMessages());
        assertTrue(result.getTokensAfter() < result.getTokensBefore(),
                "tokensAfter (" + result.getTokensAfter() + ") should be less than tokensBefore (" + result.getTokensBefore() + ")");
    }

    @Test
    void systemMessagesPreserved() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system prompt"));
        messages.add(new ChatUserMessage("hello"));
        messages.add(assistantWithToolCalls("tc-1"));
        messages.add(toolResponse("tc-1", "bash", "A".repeat(5000)));

        CompactionResult result = compactor.compact(makeContext(messages, 0));

        ChatMessage firstMsg = result.getCompactedMessages().get(0);
        assertTrue(firstMsg instanceof ChatSystemMessage);
        assertEquals("system prompt", firstMsg.getContent());
    }

    @Test
    void firstUserMessagePreserved() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("first user message"));
        messages.add(assistantWithToolCalls("tc-1"));
        messages.add(toolResponse("tc-1", "bash", "A".repeat(5000)));
        messages.add(new ChatUserMessage("second user message"));

        CompactionResult result = compactor.compact(makeContext(messages, 0));

        boolean foundFirst = false;
        for (ChatMessage msg : result.getCompactedMessages()) {
            if (msg instanceof ChatUserMessage && "first user message".equals(msg.getContent())) {
                foundFirst = true;
                break;
            }
        }
        assertTrue(foundFirst, "First user message should be preserved");
    }

    @Test
    void recentNToolResultsPreserved() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));

        for (int i = 0; i < 10; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "bash", "content-" + i));
        }

        CompactionResult result = compactor.compact(makeContext(messages, 3));

        List<ChatMessage> compacted = result.getCompactedMessages();
        assertNotNull(compacted);

        ChatMessage lastToolResp = compacted.get(compacted.size() - 1);
        assertTrue(lastToolResp instanceof ChatToolResponseMessage);
        assertEquals("content-9", lastToolResp.getContent(), "Last tool result should be preserved");

        ChatMessage secondLast = compacted.get(compacted.size() - 3);
        assertTrue(secondLast instanceof ChatToolResponseMessage);
        assertEquals("content-8", secondLast.getContent(), "Second to last tool result should be preserved");
    }

    @Test
    void nonCompressibleToolsPreserved() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));
        messages.add(assistantWithToolCalls("tc-1"));
        messages.add(toolResponse("tc-1", "bash", "A".repeat(5000)));
        messages.add(assistantWithToolCalls("tc-2"));
        messages.add(toolResponse("tc-2", "ask-oracle", "B".repeat(5000)));

        CompactionResult result = compactor.compact(makeContext(messages, 0));

        assertNotNull(result.getCompactedMessages());
        ChatMessage bashMsg = result.getCompactedMessages().get(3);
        assertTrue(bashMsg instanceof ChatToolResponseMessage);
        assertTrue(bashMsg.getContent().contains("COMPRESSED"), "bash tool result should be compressed");

        ChatMessage oracleMsg = result.getCompactedMessages().get(5);
        assertTrue(oracleMsg instanceof ChatToolResponseMessage);
        assertEquals("B".repeat(5000), oracleMsg.getContent(),
                "Non-compressible tool results should be preserved");
    }

    @Test
    void noCompressionWhenAllMessagesRecent() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));
        messages.add(assistantWithToolCalls("tc-1"));
        messages.add(toolResponse("tc-1", "bash", "short content"));

        CompactionResult result = compactor.compact(makeContext(messages, 10));

        assertNull(result.getCompactedMessages(), "Should return null when nothing to compress");
        assertEquals(result.getTokensBefore(), result.getTokensAfter());
    }

    @Test
    void compactionResultCountsAccurate() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));

        for (int i = 0; i < 8; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "bash", "A".repeat(4000)));
        }

        CompactionResult result = compactor.compact(makeContext(messages, 2));

        assertNotNull(result.getCompactedMessages());
        assertEquals(messages.size(), result.getCompactedMessages().size(),
                "Message list size should be unchanged (in-place replacement)");
        assertEquals(result.getTokensAfter(),
                NoOpContextCompactor.resolveEstimator(makeContext(result.getCompactedMessages()))
                        .estimateTokens(result.getCompactedMessages()));
    }

    @Test
    void messageListSizeUnchanged() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));

        for (int i = 0; i < 20; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "bash", "A".repeat(1000)));
        }

        int originalSize = messages.size();
        CompactionResult result = compactor.compact(makeContext(messages, 3));

        assertNotNull(result.getCompactedMessages());
        assertEquals(originalSize, result.getCompactedMessages().size(),
                "Message list size must be unchanged (in-place replacement)");
    }

    @Test
    void toolCallIdsStillPairedAfterCompression() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));

        for (int i = 0; i < 10; i++) {
            String id = "tc-" + i;
            messages.add(assistantWithToolCalls(id));
            messages.add(toolResponse(id, "bash", "content-" + i));
        }

        CompactionResult result = compactor.compact(makeContext(messages, 3));
        List<ChatMessage> compacted = result.getCompactedMessages();

        Set<String> assistantToolCallIds = new HashSet<>();
        for (ChatMessage msg : compacted) {
            if (msg instanceof ChatAssistantMessage) {
                ChatAssistantMessage asm = (ChatAssistantMessage) msg;
                if (asm.getToolCalls() != null) {
                    for (ChatToolCall tc : asm.getToolCalls()) {
                        assistantToolCallIds.add(tc.getId());
                    }
                }
            }
        }

        Set<String> responseToolCallIds = new HashSet<>();
        for (ChatMessage msg : compacted) {
            if (msg instanceof ChatToolResponseMessage) {
                responseToolCallIds.add(((ChatToolResponseMessage) msg).getToolCallId());
            }
        }

        assertEquals(assistantToolCallIds, responseToolCallIds,
                "All tool_call IDs should still have matching tool_response IDs");
    }

    @Test
    void emptyMessagesReturnNullCompactedMessages() {
        List<ChatMessage> messages = Collections.emptyList();
        CompactionResult result = compactor.compact(makeContext(messages));
        assertNull(result.getCompactedMessages());
    }

    @Test
    void compressedPlaceholderContainsToolInfo() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));
        messages.add(assistantWithToolCalls("tc-1"));
        messages.add(toolResponse("tc-1", "bash", "A".repeat(5000)));

        CompactionResult result = compactor.compact(makeContext(messages, 0));
        ChatMessage toolMsg = result.getCompactedMessages().get(3);

        String content = toolMsg.getContent();
        assertTrue(content.contains("COMPRESSED"), "Should contain COMPRESSED marker");
        assertTrue(content.contains("bash"), "Should contain tool name");
        assertTrue(content.contains("tc-1"), "Should contain toolCallId");
        assertTrue(content.contains("5000"), "Should contain original length");
    }

    @Test
    void assistantMessagesPreserved() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatSystemMessage("system"));
        messages.add(new ChatUserMessage("hello"));
        ChatAssistantMessage assistantMsg = new ChatAssistantMessage("thinking...");
        messages.add(assistantMsg);
        messages.add(assistantWithToolCalls("tc-1"));
        messages.add(toolResponse("tc-1", "bash", "A".repeat(5000)));

        CompactionResult result = compactor.compact(makeContext(messages, 0));
        List<ChatMessage> compacted = result.getCompactedMessages();

        boolean foundThinking = compacted.stream()
                .anyMatch(m -> m instanceof ChatAssistantMessage && "thinking...".equals(m.getContent()));
        assertTrue(foundThinking, "Non-tool-call assistant messages should be preserved");
    }

    @Test
    void compressibleToolsSetContainsExpectedTools() {
        assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("read_file"));
        assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("bash"));
        assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("grep"));
        assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("search"));
        assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("list_directory"));
        assertTrue(MicroCompressionCompactor.COMPRESSIBLE_TOOLS.contains("cat"));
    }
}
