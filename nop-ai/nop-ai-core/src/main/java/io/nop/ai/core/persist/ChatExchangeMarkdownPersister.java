package io.nop.ai.core.persist;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AbstractTextMessage;
import io.nop.ai.core.api.messages.AiAssistantMessage;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.AiChatUsage;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.api.core.beans.ErrorBean;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;

import java.sql.Timestamp;
import java.util.List;

public class ChatExchangeMarkdownPersister implements IChatExchangePersister {
    static final String TITLE_CHAT = "# Chat: ";

    static final String TITLE_CHAT_OPTIONS = "## ChatOptions\n";

    static final String TITLE_METADATA = "## Metadata\n";

    static final String TITLE_VARIABLES = "## Variables\n";

    static final String TITLE_ERROR = "## Error\n";

    static final String TITLE_USAGE = "## Usage\n";

    static final String TITLE_MESSAGE = "## ****Message****: ";

    static final String MARKER_CONTENT_END = "\n-----**--**------\n";

    static final String TITLE_RESPONSE = "## ****Response****: ";

    static final String TITLE_MESSAGE_META = "### Metadata\n";

    static final String JSON_BLOCK_BEGIN = "```json\n";

    static final String JSON_BLOCK_END = "\n```";

    static final String MARKER_CHAT_END = "=======**==**=======\n";

    public static ChatExchangeMarkdownPersister s_instance = new ChatExchangeMarkdownPersister();

    public static ChatExchangeMarkdownPersister instance() {
        return s_instance;
    }

    public static void registerInstance(ChatExchangeMarkdownPersister persister) {
        s_instance = persister;
    }

    @Override
    public String serialize(AiChatExchange exchange) {
        StringBuilder sb = new StringBuilder();
        sb.append(TITLE_CHAT).append(exchange.getRetryTimes())
                .append('-').append(exchange.getChatId());
        sb.append('@').append(new Timestamp(exchange.getBeginTime()));
        sb.append("\n\n");

        if (exchange.getChatOptions() != null) {
            sb.append(TITLE_CHAT_OPTIONS);
            appendJson(sb, exchange.getChatOptions());
        }

        if (exchange.getMetadata() != null) {
            sb.append(TITLE_METADATA);
            appendJson(sb, exchange.getMetadata());
        }

        if (exchange.getVariables() != null) {
            sb.append(TITLE_VARIABLES);
            appendJson(sb, exchange.getVariables());
        }

        if (exchange.isInvalid()) {
            sb.append(TITLE_ERROR);
            ErrorBean errorBean = exchange.getInvalidReason();
            if (errorBean == null)
                errorBean = new ErrorBean("invalid");
            appendJson(sb, errorBean);
        }

        AiChatUsage usage = exchange.getUsage();
        if (usage != null) {
            sb.append(TITLE_USAGE);
            appendJson(sb, usage);
        }

        if (exchange.getPrompt() != null) {
            List<AiMessage> messages = exchange.getPrompt().getMessages();
            for (AiMessage message : messages) {
                appendMessage(sb, TITLE_MESSAGE, message);
            }
        }

        if (exchange.getResponse() != null) {
            appendMessage(sb, TITLE_RESPONSE, exchange.getResponse());
        }

        sb.append(MARKER_CHAT_END);

        return sb.toString();
    }

    void appendJson(StringBuilder sb, Object bean) {
        sb.append('\n');
        sb.append(JSON_BLOCK_BEGIN);
        sb.append(JsonTool.serialize(bean, true));
        sb.append(JSON_BLOCK_END);
        sb.append("\n\n");
    }

    void appendMessage(StringBuilder sb, String title, AiMessage message) {
        sb.append(title);
        if (message.getRole() != null) {
            sb.append('[').append(message.getRole()).append("]");
        }
        sb.append("\n\n");
        sb.append(message.getContent());
        sb.append(MARKER_CONTENT_END);
        sb.append("\n");

        if (message.getMetadata() != null) {
            sb.append(TITLE_MESSAGE_META);
            appendJson(sb, message.getMetadata());
        }
    }

    @Override
    public AiChatExchange deserialize(String text) {
        text = StringHelper.replace(text, "\r\n", "\n");

        AiChatExchange response = new AiChatExchange();
        TextScanner scanner = TextScanner.fromString(null, text);

        Prompt prompt = new Prompt();

        // 解析Chat标题行
        if (scanner.tryMatch(TITLE_CHAT)) {
            int retryTimes = scanner.nextInt();
            prompt.setRetryTimes(retryTimes);
            scanner.consume('-');
            String chatId = scanner.nextUntil("@", false).toString();
            response.setChatId(chatId);
            scanner.consume('@');
            String beginTime = scanner.nextLine().toString();
            response.setBeginTime(Timestamp.valueOf(beginTime).getTime());
            scanner.skipBlank();
            scanner.skipBlank();
        }

        // 解析ChatOptions
        if (scanner.tryMatch(TITLE_CHAT_OPTIONS)) {
            String json = consumeJsonBlock(scanner);
            response.setChatOptions(JsonTool.parseBeanFromText(json, AiChatOptions.class));
        }

        // 解析Metadata
        if (scanner.tryMatch(TITLE_METADATA)) {
            String json = consumeJsonBlock(scanner);
            prompt.setMetadata(JsonTool.parseMap(json));
        }

        // 解析Variables
        if (scanner.tryMatch(TITLE_VARIABLES)) {
            String json = consumeJsonBlock(scanner);
            prompt.setVariables(JsonTool.parseMap(json));
        }

        // 解析Error
        if (scanner.tryMatch(TITLE_ERROR)) {
            String errorText = consumeJsonBlock(scanner);
            response.setInvalidReason(JsonTool.parseBeanFromText(errorText, ErrorBean.class));
        }

        // 解析Usage
        if (scanner.tryMatch(TITLE_USAGE)) {
            String json = consumeJsonBlock(scanner);
            response.setUsage(JsonTool.parseBeanFromText(json, AiChatUsage.class));
        }

        response.setPrompt(prompt);

        // 解析Messages
        while (scanner.tryMatch(TITLE_MESSAGE)) {
            AiMessage message = parseMessage(scanner);
            prompt.addMessage(message);
        }

        // 解析Response
        if (scanner.tryMatch(TITLE_RESPONSE)) {
            response.setResponse((AiAssistantMessage) parseMessage(scanner));
        }

        scanner.match(MARKER_CHAT_END);

        return response;
    }

    private String consumeJsonBlock(TextScanner scanner) {
        scanner.consume(JSON_BLOCK_BEGIN);
        String json = scanner.nextUntil(JSON_BLOCK_END, false).toString();
        scanner.consume(JSON_BLOCK_END);
        scanner.skipBlank();
        return json;
    }

    private AiMessage parseMessage(TextScanner scanner) {
        scanner.match('[');
        String role = scanner.nextUntil(']', false).trim().toString();
        scanner.match(']');

        AbstractTextMessage message = (AbstractTextMessage) AiMessage.create(role);
        String content = scanner.nextUntil(MARKER_CONTENT_END, false).toString();
        message.setContent(content);
        scanner.consume(MARKER_CONTENT_END);
        scanner.skipBlank();

        // 解析元数据
        if (scanner.tryMatch(TITLE_MESSAGE_META)) {
            String json = consumeJsonBlock(scanner);
            message.setMetadata(JsonTool.parseMap(json));
        }

        return message;
    }
}
