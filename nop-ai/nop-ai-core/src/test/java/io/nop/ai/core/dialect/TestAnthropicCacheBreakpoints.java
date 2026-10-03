package io.nop.ai.core.dialect;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.messages.ChatAssistantMessage;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.messages.ChatReasoningMessage;
import io.nop.ai.api.chat.messages.ChatSystemMessage;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.core.model.ApiStyle;
import io.nop.ai.core.model.LlmModel;
import io.nop.ai.core.model.LlmModelModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 2302 (WI20): the Anthropic request-side cache breakpoint engine —
 * deterministic content-block-level placement, TTL variants, off-by-default
 * byte identity, and the autonomous-WI8 placement correction (the
 * providerHints top-level passthrough no longer leaks cache_control into the
 * message/turn maps).
 */
public class TestAnthropicCacheBreakpoints {

    private static LlmModel config() {
        LlmModel config = new LlmModel();
        config.setApiStyle(ApiStyle.anthropic);
        return config;
    }

    private static LlmModelModel modelConfig(int breakpoints, String ttl) {
        LlmModelModel modelConfig = new LlmModelModel();
        modelConfig.setName("test-model");
        modelConfig.setCacheBreakpoints(breakpoints);
        if (ttl != null) {
            modelConfig.setCacheBreakpointTtl(ttl);
        }
        return modelConfig;
    }

    private static ChatRequest request(ChatMessage... messages) {
        List<ChatMessage> list = new ArrayList<>();
        for (ChatMessage m : messages) {
            list.add(m);
        }
        ChatRequest request = new ChatRequest(list);
        request.setOptions(new ChatOptions());
        return request;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> turns(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("messages");
    }

    @Test
    public void offByDefaultLeavesRequestShapeUntouched() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatSystemMessage("sys"), new ChatUserMessage("u1"),
                new ChatAssistantMessage("a1"), new ChatUserMessage("u2"));

        Map<String, Object> body = dialect.buildBody(request, config(), null, "claude", false);
        assertEquals("sys", body.get("system"), "no model config → string system form");
        for (Map<String, Object> turn : turns(body)) {
            assertFalse(turn.containsKey("cache_control"), "no turn-level cache_control key");
            for (Map<String, Object> block : (List<Map<String, Object>>) turn.get("content")) {
                assertFalse(block.containsKey("cache_control"), "no block-level cache_control");
            }
        }
    }

    @Test
    public void negativeBreakpointsEqualOff() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatUserMessage("hello"));

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig(-2, null), "claude", false);
        for (Map<String, Object> turn : turns(body)) {
            for (Map<String, Object> block : (List<Map<String, Object>>) turn.get("content")) {
                assertFalse(block.containsKey("cache_control"), "negative config = off");
            }
        }
    }

    @Test
    public void systemStringBecomesBlockArrayWithBreakpoint() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatSystemMessage("You are helpful."), new ChatUserMessage("hi"));

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig(1, null), "claude", false);
        List<Map<String, Object>> systemBlocks = (List<Map<String, Object>>) body.get("system");
        assertEquals(1, systemBlocks.size());
        assertEquals("text", systemBlocks.get(0).get("type"));
        assertEquals("You are helpful.", systemBlocks.get(0).get("text"));
        Map<String, Object> cacheControl = (Map<String, Object>) systemBlocks.get(0).get("cache_control");
        assertEquals("ephemeral", cacheControl.get("type"), "default TTL form has no ttl key");
        assertFalse(cacheControl.containsKey("ttl"));
    }

    @Test
    public void ttlVariantIsCarriedOnTheBreakpoint() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatSystemMessage("sys"), new ChatUserMessage("hi"));

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig(1, "1h"), "claude", false);
        List<Map<String, Object>> systemBlocks = (List<Map<String, Object>>) body.get("system");
        Map<String, Object> cacheControl = (Map<String, Object>) systemBlocks.get(0).get("cache_control");
        assertEquals("1h", cacheControl.get("ttl"), "TTL variant carried on the breakpoint");
    }

    @Test
    public void breakpointLandsOnLastBlockOfLastTurn() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatUserMessage("u1"), new ChatAssistantMessage("a1"),
                new ChatUserMessage("u2"));

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig(1, null), "claude", false);
        List<Map<String, Object>> messageTurns = turns(body);
        assertEquals(3, messageTurns.size());

        for (int i = 0; i < messageTurns.size() - 1; i++) {
            for (Map<String, Object> block : (List<Map<String, Object>>) messageTurns.get(i).get("content")) {
                assertFalse(block.containsKey("cache_control"), "earlier turns stay breakpoint-free");
            }
        }
        List<Map<String, Object>> lastBlocks = (List<Map<String, Object>>) messageTurns.get(2).get("content");
        Map<String, Object> lastBlock = lastBlocks.get(lastBlocks.size() - 1);
        assertTrue(lastBlock.containsKey("cache_control"), "the last block of the last turn carries the breakpoint");
    }

    @Test
    public void multipleBreakpointsWalkBackwardsOverNonEmptyTurns() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatUserMessage("u1"), new ChatAssistantMessage("a1"),
                new ChatUserMessage("u2"), new ChatAssistantMessage("a2"), new ChatUserMessage("u3"));

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig(2, null), "claude", false);
        List<Map<String, Object>> messageTurns = turns(body);
        assertNull(((List<Map<String, Object>>) messageTurns.get(0).get("content")).get(0).get("cache_control"),
                "the first turn is outside N=2");
        assertTrue(((List<Map<String, Object>>) messageTurns.get(3).get("content"))
                .get(0).containsKey("cache_control"));
        assertTrue(((List<Map<String, Object>>) messageTurns.get(4).get("content"))
                .get(0).containsKey("cache_control"));
    }

    @Test
    public void emptyAndThinkingOnlyTurnsAreSkippedAndDoNotCount() {
        AnthropicDialect dialect = new AnthropicDialect();
        // empty-content assistant (no blocks) + reasoning-only turn
        ChatAssistantMessage emptyAssistant = new ChatAssistantMessage("");
        ChatReasoningMessage reasoningOnly = new ChatReasoningMessage("thinking hard");
        ChatRequest request = request(new ChatUserMessage("u1"), emptyAssistant, reasoningOnly,
                new ChatUserMessage("u2"));

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig(2, null), "claude", false);
        List<Map<String, Object>> messageTurns = turns(body);

        // reasoning-only turn merges into the user turn before it? No — roles
        // differ (model vs user), so it is its own turn; its only block is
        // thinking → skipped, breakpoint falls to the u1 turn and the u2 turn
        int withBreakpoint = 0;
        for (Map<String, Object> turn : messageTurns) {
            for (Map<String, Object> block : (List<Map<String, Object>>) turn.get("content")) {
                if ("thinking".equals(block.get("type"))) {
                    assertFalse(block.containsKey("cache_control"),
                            "thinking blocks never carry cache_control (Anthropic contract)");
                } else if (block.containsKey("cache_control")) {
                    withBreakpoint++;
                }
            }
        }
        assertEquals(2, withBreakpoint, "both breakpoints land on carryable blocks (u1 + u2 turns)");
    }

    @Test
    public void placementIsDeterministic() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatSystemMessage("sys"), new ChatUserMessage("u1"),
                new ChatAssistantMessage("a1"));

        Map<String, Object> first = dialect.buildBody(request, config(), modelConfig(1, null), "claude", false);
        Map<String, Object> second = dialect.buildBody(request, config(), modelConfig(1, null), "claude", false);
        assertEquals(first, second, "same message sequence → same breakpoint positions");
    }

    @Test
    public void providerHintsCacheControlNoLongerLeaksTopLevel() {
        // autonomous WI8 acceptance: the corrected placement is content-block
        // level; the top-level message/turn key is gone even when a legacy
        // producer still sets the hint
        ChatUserMessage hinted = new ChatUserMessage("u1");
        hinted.setProviderHints(new java.util.LinkedHashMap<>(
                Map.of("cache_control", Map.of("type", "ephemeral"))));

        AnthropicDialect dialect = new AnthropicDialect();
        Map<String, Object> body = dialect.buildBody(request(hinted), config(), null, "claude", false);

        assertFalse(body.containsKey("cache_control"));
        for (Map<String, Object> turn : turns(body)) {
            assertFalse(turn.containsKey("cache_control"), "no turn-level cache_control (WI8 defect position)");
            for (Map<String, Object> block : (List<Map<String, Object>>) turn.get("content")) {
                assertFalse(block.containsKey("cache_control"),
                        "hints alone never place breakpoints — engine placement is config-gated");
            }
        }
    }

    @Test
    public void breakpointCombinesWithThinkingPromptOnSameTurn() {
        AnthropicDialect dialect = new AnthropicDialect();
        LlmModelModel modelConfig = modelConfig(1, null);
        modelConfig.setEnableThinkingPrompt("/think");
        ChatRequest request = request(new ChatSystemMessage("sys"), new ChatUserMessage("u1"),
                new ChatAssistantMessage("a1"), new ChatUserMessage("u2"));
        request.getOptions().setEnableThinking(true);

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig, "claude", false);
        List<Map<String, Object>> lastTurnBlocks =
                (List<Map<String, Object>>) turns(body).get(turns(body).size() - 1).get("content");
        assertEquals("u2\n/think", lastTurnBlocks.get(0).get("text"),
                "thinking prompt still applied to the first text block");
        assertTrue(lastTurnBlocks.get(lastTurnBlocks.size() - 1).containsKey("cache_control"),
                "breakpoint still lands on the last block of the same turn");
    }

    @Test
    public void noSystemMessageSkipsSystemForm() {
        AnthropicDialect dialect = new AnthropicDialect();
        ChatRequest request = request(new ChatUserMessage("hello"));

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig(1, null), "claude", false);
        assertFalse(body.containsKey("system"), "no system key → system form skipped");
        assertTrue(turns(body).get(0).get("content") instanceof List);
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) turns(body).get(0).get("content");
        assertTrue(blocks.get(blocks.size() - 1).containsKey("cache_control"),
                "the message breakpoint still applies");
    }

    @Test
    public void thinkingPromptStillAppliedWithoutBreakpoints() {
        AnthropicDialect dialect = new AnthropicDialect();
        LlmModelModel modelConfig = new LlmModelModel();
        modelConfig.setName("m");
        modelConfig.setEnableThinkingPrompt("/think");
        ChatRequest request = request(new ChatUserMessage("u1"));
        request.getOptions().setEnableThinking(true);

        Map<String, Object> body = dialect.buildBody(request, config(), modelConfig, "claude", false);
        List<Map<String, Object>> blocks = (List<Map<String, Object>>) turns(body).get(0).get("content");
        assertEquals("u1\n/think", blocks.get(0).get("text"));
    }
}
