package io.nop.integration.api.channel;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: OutboundChannelMessage is the identity-anchored
 * business payload — its attachments list is contractually never null
 * (setters normalize null to an empty list) so connectors can iterate it
 * without a null check.
 */
public class TestOutboundChannelMessage {

    @Test
    public void testAttachmentsDefaultToEmptyList() {
        OutboundChannelMessage msg = new OutboundChannelMessage();
        assertNotNull(msg.getAttachments(), "attachments default must be non-null");
        assertTrue(msg.getAttachments().isEmpty());
    }

    @Test
    public void testSetAttachmentsNullIsNormalizedToEmptyList() {
        OutboundChannelMessage msg = new OutboundChannelMessage();
        msg.setAttachments(null);
        assertNotNull(msg.getAttachments(), "null attachments must be normalized, never stored");
        assertTrue(msg.getAttachments().isEmpty());
    }

    @Test
    public void testSetTextAndMarkdownBodies() {
        OutboundChannelMessage msg = new OutboundChannelMessage();
        msg.setText("plain body");
        msg.setMarkdown("**bold** body");
        msg.setBusinessRef("notify-42");
        assertEquals("plain body", msg.getText());
        assertEquals("**bold** body", msg.getMarkdown());
        assertEquals("notify-42", msg.getBusinessRef(), "businessRef is the opaque correlation key");
    }

    @Test
    public void testAttachmentFieldsRoundTrip() {
        OutboundChannelMessage.Attachment att = new OutboundChannelMessage.Attachment();
        att.setName("report.pdf");
        att.setMimeType("application/pdf");
        att.setUrl("https://example.com/report.pdf");
        byte[] content = "raw".getBytes(StandardCharsets.UTF_8);
        att.setContent(content);

        OutboundChannelMessage msg = new OutboundChannelMessage();
        msg.getAttachments().add(att);

        assertSame(att, msg.getAttachments().get(0));
        assertEquals("report.pdf", msg.getAttachments().get(0).getName());
        assertEquals("application/pdf", msg.getAttachments().get(0).getMimeType());
        assertEquals("https://example.com/report.pdf", msg.getAttachments().get(0).getUrl());
        assertEquals(content, msg.getAttachments().get(0).getContent());
    }
}
