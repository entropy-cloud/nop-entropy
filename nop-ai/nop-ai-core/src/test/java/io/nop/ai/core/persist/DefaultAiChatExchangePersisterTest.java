package io.nop.ai.core.persist;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}