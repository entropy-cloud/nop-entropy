package io.nop.ai.core.persist;

import org.junit.jupiter.api.Test;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.core.unittest.BaseTestCase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultAiChatExchangePersisterTest extends BaseTestCase {

    @Test
    public void testSerializeAndDeserialize() {
        DefaultAiChatExchangePersister persister = DefaultAiChatExchangePersister.instance();
        String expectedText = attachmentText("persist-message.md");

        // 反序列化
        AiChatExchange response = persister.deserialize(expectedText);

        // 再次序列化
        String actualText = persister.serialize(response);

        // 验证结果
        assertEquals(normalizeCRLF(expectedText.trim()), normalizeCRLF(actualText.trim()));
    }

    @Test
    public void testSerializeList() {
        AiChatExchange exchange = new AiChatExchange();
        exchange.setExchangeId("123");
        exchange.setPrompt(Prompt.userText("abc"));
        exchange.setContent("123");
        exchange.setInvalid(true);

        AiChatExchange exchange1 = new AiChatExchange();
        exchange1.setExchangeId("1234");
        exchange1.setPrompt(Prompt.userText("abc4"));
        exchange1.setContent("1234");

        DefaultAiChatExchangePersister persister = DefaultAiChatExchangePersister.instance();
        String text = persister.serializeList(List.of(exchange, exchange1));

        List<AiChatExchange> ret = persister.deserializeList(text);
        assertTrue(ret.get(0).isInvalid());
        assertEquals(2, ret.size());
        assertEquals("123", ret.get(0).getContent());
        assertEquals("abc4", ret.get(1).getPrompt().getLastMessage().getContent());
    }

    @Test
    public void testEncryptedRoundTrip() {
        DefaultAiChatExchangePersister persister = new DefaultAiChatExchangePersister();
        persister.setEncryptEnabled(true);

        AiChatExchange exchange = new AiChatExchange();
        exchange.setExchangeId("enc-1");
        exchange.setPrompt(Prompt.userText("sensitive-payload-42"));
        exchange.setContent("response-42");

        String text = persister.serialize(exchange);
        assertTrue(text.startsWith(DefaultAiChatExchangePersister.ENCRYPTED_MARKER),
                "encrypted output must carry the encryption marker");
        assertFalse(text.contains("sensitive-payload-42"), "plaintext must not appear in encrypted output");
        assertNotEquals(normalizeCRLF(text), normalizeCRLF(exchange.getContent()), "content must be transformed");

        AiChatExchange roundTrip = persister.deserialize(text);
        assertEquals("enc-1", roundTrip.getExchangeId());
        assertEquals("sensitive-payload-42", roundTrip.getPrompt().getLastMessage().getContent());
        assertEquals("response-42", roundTrip.getContent());
    }

    @Test
    public void testEncryptedListRoundTrip() {
        DefaultAiChatExchangePersister persister = new DefaultAiChatExchangePersister();
        persister.setEncryptEnabled(true);

        AiChatExchange e1 = new AiChatExchange();
        e1.setExchangeId("enc-list-1");
        e1.setPrompt(Prompt.userText("secret-a"));
        AiChatExchange e2 = new AiChatExchange();
        e2.setExchangeId("enc-list-2");
        e2.setPrompt(Prompt.userText("secret-b"));

        String text = persister.serializeList(List.of(e1, e2));
        assertTrue(text.startsWith(DefaultAiChatExchangePersister.ENCRYPTED_MARKER));
        assertFalse(text.contains("secret-a"));
        assertFalse(text.contains("secret-b"));

        List<AiChatExchange> ret = persister.deserializeList(text);
        assertEquals(2, ret.size());
        assertEquals("secret-a", ret.get(0).getPrompt().getLastMessage().getContent());
        assertEquals("secret-b", ret.get(1).getPrompt().getLastMessage().getContent());
    }

    @Test
    public void testLegacyPlaintextStillReadable() {
        DefaultAiChatExchangePersister persister = new DefaultAiChatExchangePersister();
        persister.setEncryptEnabled(true);

        AiChatExchange exchange = new AiChatExchange();
        exchange.setExchangeId("legacy-1");
        exchange.setPrompt(Prompt.userText("legacy-plain"));

        String plaintext = new DefaultAiChatExchangePersister().serialize(exchange);
        assertFalse(plaintext.startsWith(DefaultAiChatExchangePersister.ENCRYPTED_MARKER));

        AiChatExchange ret = persister.deserialize(plaintext);
        assertEquals("legacy-plain", ret.getPrompt().getLastMessage().getContent());
    }
}