package io.nop.ai.agent.engine;

import io.nop.ai.agent.reliability.Checkpoint;
import io.nop.ai.agent.reliability.CheckpointType;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatToolCallMessage;
import io.nop.ai.api.chat.messages.ChatToolResponseMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2284 (WI3 invariant 3): open tool_call set = snapshot messages ×
 * TOOL_EXECUTION checkpoint (callId idempotency key) set difference, with the
 * three-state restore semantics — (a) open + no checkpoint = never-committed
 * orphan (alarm); (b) closed (any result, incl. deny error results) = terminal,
 * silent; (c) open + checkpoint = committed-but-unsynced crash window (owned by
 * the messageCount warning, not re-alarmed).
 */
public class TestOpenToolCallResolver {

    private static ChatToolCallMessage call(String id) {
        return new ChatToolCallMessage(id, "read-file", Map.of());
    }

    private static Checkpoint toolCheckpoint(String callId) {
        return Checkpoint.of("s1", "s1:tool:" + callId + ":0:0", 0, 0L,
                CheckpointType.TOOL_EXECUTION, "read-file", callId, "{}", "ok", 3, 0L);
    }

    @Test
    void callWithoutResultIsOpen() {
        List<ChatMessage> messages = List.of(
                new ChatUserMessage("run"),
                new ChatAssistantMessage(""),
                call("1"));
        assertEquals(List.of("1"), List.copyOf(OpenToolCallResolver.findOpenCallIds(messages)));
        assertEquals(1, OpenToolCallResolver.findOpenToolCalls(messages).size());
    }

    @Test
    void callWithResultIsClosed() {
        List<ChatMessage> messages = List.of(
                new ChatAssistantMessage(""),
                call("1"),
                new ChatToolResponseMessage("1", "read-file", "done"));
        assertTrue(OpenToolCallResolver.findOpenCallIds(messages).isEmpty());
        assertTrue(OpenToolCallResolver.findOpenToolCalls(messages).isEmpty());
    }

    @Test
    void denyErrorResultAlsoClosesTheCall() {
        List<ChatMessage> messages = List.of(
                call("1"),
                ChatToolResponseMessage.error("1", "read-file", "denied"),
                call("2"));
        // state (b): closed by a deny error result — not alarm-worthy
        Set<String> open = OpenToolCallResolver.findOpenCallIds(messages);
        assertEquals(Set.of("2"), open);
        assertTrue(OpenToolCallResolver.filterNeverCommitted(open, List.of()).contains("2"));
        assertTrue(OpenToolCallResolver.filterCommittedButUnsynced(open, List.of()).isEmpty());
        assertTrue(!OpenToolCallResolver.filterNeverCommitted(open, List.of()).contains("1"));
    }

    @Test
    void threeStateDiffNeverCommittedVsCommittedUnsynced() {
        List<ChatMessage> messages = List.of(
                call("1"), // state (a): open, no checkpoint → never-committed
                call("2"), // state (c): open, checkpoint present → committed-unsynced
                call("3"));
        List<Checkpoint> checkpoints = List.of(toolCheckpoint("2"));

        Set<String> open = OpenToolCallResolver.findOpenCallIds(messages);
        assertEquals(Set.of("1", "2", "3"), open);
        assertEquals(Set.of("1", "3"), OpenToolCallResolver.filterNeverCommitted(open, checkpoints));
        assertEquals(Set.of("2"), OpenToolCallResolver.filterCommittedButUnsynced(open, checkpoints));
    }

    @Test
    void nonToolExecutionCheckpointsAreIgnored() {
        List<ChatMessage> messages = List.of(call("1"));
        Checkpoint llmTurn = Checkpoint.of("s1", "s1:llm:0", 0, 0L, CheckpointType.LLM_TURN,
                null, null, null, null, 2, 0L);
        Set<String> open = OpenToolCallResolver.findOpenCallIds(messages);
        assertEquals(Set.of("1"), OpenToolCallResolver.filterNeverCommitted(open, List.of(llmTurn)));
    }

    @Test
    void mixedClosedAndOpenAcrossTurns() {
        List<ChatMessage> messages = List.of(
                new ChatUserMessage("turn 1"),
                new ChatAssistantMessage(""),
                call("1"),
                new ChatToolResponseMessage("1", "read-file", "done"),
                new ChatUserMessage("turn 2"),
                new ChatAssistantMessage(""),
                call("2"));
        List<ChatToolCallMessage> openCalls = OpenToolCallResolver.findOpenToolCalls(messages);
        assertEquals(1, openCalls.size());
        assertEquals("2", openCalls.get(0).getCallId());
    }
}
