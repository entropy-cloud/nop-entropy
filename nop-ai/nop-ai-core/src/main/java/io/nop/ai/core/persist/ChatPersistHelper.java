package io.nop.ai.core.persist;

import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.commons.util.FileHelper;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class ChatPersistHelper {
    public static List<AiChatExchange> parseMessagesFromFile(File file) {
        String text = FileHelper.readText(file, "UTF-8");
        return DefaultAiChatExchangePersister.instance().deserializeList(text);
    }

    public static String serializeMessages(List<AiChatExchange> messages) {
        return DefaultAiChatExchangePersister.instance().serializeList(messages);
    }

    public static String getAggregateResults(List<AiChatExchange> messages) {
        return messages.stream().map(AiChatExchange::getResultText).collect(Collectors.joining("\n\n"));
    }
}
