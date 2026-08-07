package io.nop.ai.api.chat;

import io.nop.ai.api.chat.messages.ChatUserMessage;
import io.nop.ai.api.chat.messages.ContentPart;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Plan 326 Phase 1 — 验证 {@link ResponseFormat} 对象载体、{@link ChatOptions} 的 String 视图委托，
 * 以及 {@link ChatUserMessage#getParts()} 多模态与 {@link ChatUserMessage#getContent()} 委托。
 */
public class TestChatOptionsAndUserMessageParts {

    // ==================== ResponseFormat / ChatOptions ====================

    @Test
    public void responseFormatStringViewSetAndGetString() {
        ChatOptions options = new ChatOptions();
        options.setResponseFormat("json");

        assertEquals("json", options.getResponseFormat(),
                "legacy String round-trip must be preserved (WfAiHelper backward compat)");
    }

    @Test
    public void responseFormatStringViewFromObject() {
        ChatOptions options = new ChatOptions();
        options.setResponseFormatConfig(ResponseFormat.jsonObject());

        assertEquals("json_object", options.getResponseFormat(),
                "json_object object -> getResponseFormat() returns 'json_object'");
    }

    @Test
    public void responseFormatObjectCarrierWithSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        ChatOptions options = ChatOptions.builder()
                .responseFormatConfig(ResponseFormat.jsonSchema(schema))
                .build();

        ResponseFormat fmt = options.getResponseFormatConfig();
        assertNotNull(fmt);
        assertEquals(ResponseFormat.TYPE_JSON_SCHEMA, fmt.getType());
        assertEquals("object", fmt.getSchema().get("type"));
    }

    @Test
    public void responseFormatNullRoundTrip() {
        ChatOptions options = new ChatOptions();
        assertNull(options.getResponseFormat(), "null -> null");
        assertNull(options.getResponseFormatConfig());
    }

    @Test
    public void responseFormatCopyPreservesObject() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        ChatOptions original = new ChatOptions();
        original.setResponseFormatConfig(ResponseFormat.jsonSchema(schema));

        ChatOptions copy = original.copy();
        assertEquals(ResponseFormat.TYPE_JSON_SCHEMA, copy.getResponseFormatConfig().getType());
        assertEquals("object", copy.getResponseFormatConfig().getSchema().get("type"));

        copy.getResponseFormatConfig().getSchema().put("type", "string");
        assertEquals("object", original.getResponseFormatConfig().getSchema().get("type"),
                "copy() must deep-copy the schema map");
    }

    @Test
    public void responseFormatMergeOverrides() {
        ChatOptions base = new ChatOptions();
        base.setResponseFormatConfig(ResponseFormat.jsonObject());

        ChatOptions override = new ChatOptions();
        override.setResponseFormat("json");

        ChatOptions merged = base.merge(override);
        assertEquals("json", merged.getResponseFormat(),
                "merge() must let other's responseFormat override");
    }

    // ==================== ChatUserMessage parts ====================

    @Test
    public void userMessageGetContentUnchangedWithoutParts() {
        ChatUserMessage msg = new ChatUserMessage("hello");
        assertEquals("hello", msg.getContent(),
                "without parts, getContent() returns the legacy content field (unchanged behavior)");
    }

    @Test
    public void userMessagePartsTextConcatenation() {
        ChatUserMessage msg = new ChatUserMessage();
        msg.addPart(ContentPart.textOf("line1"));
        msg.addPart(ContentPart.textOf("line2"));

        assertEquals("line1\nline2", msg.getContent(),
                "getContent() delegates to text parts concatenation when parts is non-empty");
    }

    @Test
    public void userMessagePartsIncludeImage() {
        ChatUserMessage msg = new ChatUserMessage();
        msg.addPart(ContentPart.imageOfUrl("https://example.com/img.png", "high"));
        msg.addPart(ContentPart.textOf("describe this"));

        assertEquals("describe this", msg.getContent(),
                "getContent() only concatenates text parts, skipping image parts");
    }

    @Test
    public void userMessageCopySyncsParts() {
        ChatUserMessage msg = new ChatUserMessage();
        msg.addPart(ContentPart.textOf("a"));
        msg.addPart(ContentPart.imageOfData("data:image/png;base64,xxx"));

        ChatUserMessage copy = msg.copy();
        assertEquals(2, copy.getParts().size());
        assertEquals(ContentPart.TYPE_TEXT, copy.getParts().get(0).getType());
        assertEquals(ContentPart.TYPE_IMAGE, copy.getParts().get(1).getType());
    }

    @Test
    public void contentPartFactories() {
        ContentPart text = ContentPart.textOf("hi");
        assertEquals("text", text.getType());
        assertEquals("hi", text.getText());

        ContentPart img = ContentPart.imageOfUrl("u", "high");
        assertEquals("image", img.getType());
        assertEquals("u", img.getImageUrl());
        assertEquals("high", img.getDetail());

        ContentPart audio = ContentPart.audioOfData("data:audio/wav;base64,yyy");
        assertEquals("audio", audio.getType());
        assertEquals("data:audio/wav;base64,yyy", audio.getData());
    }

    @Test
    public void userMessageSetContentDoesNotTouchParts() {
        ChatUserMessage msg = new ChatUserMessage();
        msg.addPart(ContentPart.textOf("parts-text"));
        msg.setContent("legacy-text");

        assertEquals("parts-text", msg.getContent(),
                "parts takes precedence over content field when non-empty");
    }
}
