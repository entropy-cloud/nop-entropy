package io.nop.integration.api.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: EmailMessage.getFullFrom is the single
 * RFC-5322-style address composition point consumed by every email
 * connector — the personalName face must degrade to the bare address when
 * unset (empty string counts as unset).
 */
public class TestEmailMessage {

    @Test
    public void testFullFromComposesPersonalNameAndAddress() {
        EmailMessage msg = new EmailMessage();
        msg.setPersonalName("Ops Bot");
        msg.setFrom("ops@example.com");
        assertEquals("Ops Bot <ops@example.com>", msg.getFullFrom());
    }

    @Test
    public void testFullFromFallsBackToBareAddressWhenPersonalNameEmpty() {
        EmailMessage msg = new EmailMessage();
        msg.setFrom("ops@example.com");
        assertEquals("ops@example.com", msg.getFullFrom());

        EmailMessage emptyName = new EmailMessage();
        emptyName.setPersonalName("");
        emptyName.setFrom("noreply@example.com");
        assertEquals("noreply@example.com", emptyName.getFullFrom());
    }

    @Test
    public void testRecipientAndBodyAccessorsRoundTrip() {
        EmailMessage msg = new EmailMessage();
        msg.setTo(java.util.List.of("a@example.com", "b@example.com"));
        msg.setCc(java.util.List.of("c@example.com"));
        msg.setText("<b>hi</b>");
        msg.setHtml(true);
        msg.setReply("reply@example.com");

        assertEquals(2, msg.getTo().size());
        assertEquals(1, msg.getCc().size());
        assertTrue(msg.isHtml());
        assertEquals("<b>hi</b>", msg.getText());
        assertEquals("reply@example.com", msg.getReply());
    }

    @Test
    public void testDefaultsAreBlank() {
        EmailMessage msg = new EmailMessage();
        assertNull(msg.getSubject());
        assertNull(msg.getAttachments());
        assertTrue(!msg.isHtml(), "html flag defaults to false (plain text)");
    }
}
