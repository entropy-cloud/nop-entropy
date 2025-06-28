package io.nop.ai.core.persist;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}