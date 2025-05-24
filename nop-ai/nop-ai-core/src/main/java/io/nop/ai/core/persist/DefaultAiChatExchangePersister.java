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

public class DefaultAiChatExchangePersister implements IAiChatExchangePersister {
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

    public static DefaultAiChatExchangePersister s_instance = new DefaultAiChatExchangePersister();

    public static DefaultAiChatExchangePersister instance() {
        return s_instance;
    }

    public static void registerInstance(DefaultAiChatExchangePersister persister) {
        s_instance = persister;
    }

    @Override
    public String calcRequestHash(Prompt prompt, AiChatOptions options) {
        StringBuilder sb = new StringBuilder();

        // 仅使用provider和model属性，其他属性不参与结果缓存匹配
        AiChatOptions copyOptions = new AiChatOptions();
        copyOptions.setProvider(options.getProvider());
        copyOptions.setModel(options.getModel());

        writeRequest(sb, prompt, copyOptions, 0, null);
        return StringHelper.md5Hash(sb.toString());
    }

    protected void writeRequest(StringBuilder sb, Prompt prompt, AiChatOptions options,
                                long createTime, String exchangeId) {
        sb.append(TITLE_CHAT);
        sb.append(prompt.getRetryTimes());
        sb.append('-');
        String promptName = prompt.getName();
        if (promptName != null)
            sb.append(promptName);
        if (exchangeId != null)
            sb.append('[').append(exchangeId).append('@').append(new Timestamp(createTime)).append(']');
        sb.append("\n\n");

        if (options != null) {
            sb.append(TITLE_CHAT_OPTIONS);
            appendJson(sb, options);
        }

        if (prompt.getMetadata() != null) {
            sb.append(TITLE_METADATA);
            appendJson(sb, prompt.getMetadata());
        }

        if (prompt.getVariables() != null) {
            sb.append(TITLE_VARIABLES);
            appendJson(sb, prompt.getVariables());
        }

        List<AiMessage> messages = prompt.getMessages();
        for (AiMessage message : messages) {
            appendMessage(sb, TITLE_MESSAGE, message);
        }
    }

    @Override
    public String serialize(AiChatExchange exchange) {
        StringBuilder sb = new StringBuilder();
        writeRequest(sb, exchange.getPrompt(), exchange.getChatOptions(),
                exchange.getBeginTime(), exchange.getExchangeId());

        if (exchange.isInvalid()) {
            sb.append(TITLE_ERROR);
            ErrorBean errorBean = exchange.getInvalidReason();
            if (errorBean == null)
                errorBean = new ErrorBean("invalid");
            appendJson(sb, errorBean);
        }

        if (exchange.getResponse() != null) {
            appendMessage(sb, TITLE_RESPONSE, exchange.getResponse());
        }

        AiChatUsage usage = exchange.getUsage();
        if (usage != null) {
            sb.append(TITLE_USAGE);
            appendJson(sb, usage);
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
        if (message.getContent() != null)
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
            String line = scanner.nextUntil('\n', true).toString();
            int pos = line.indexOf('[');
            String promptName = line;
            if (pos < 0) {
                if (promptName.length() > 0)
                    prompt.setName(line);
            } else {
                promptName = line.substring(0, pos).trim();
                int pos2 = line.indexOf(']', pos);
                int pos3 = line.indexOf('@', pos);
                if (pos2 > 0 && pos3 > 0) {
                    String exchangeId = line.substring(pos + 1, pos3);
                    Timestamp createTime = Timestamp.valueOf(line.substring(pos3 + 1, pos2));
                    response.setBeginTime(createTime.getTime());
                    response.setExchangeId(exchangeId);
                }
                prompt.setName(promptName);
            }
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

        // 解析Usage
        if (scanner.tryMatch(TITLE_USAGE)) {
            String json = consumeJsonBlock(scanner);
            response.setUsage(JsonTool.parseBeanFromText(json, AiChatUsage.class));
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
