package io.nop.ai.core.persist;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AbstractTextMessage;
import io.nop.ai.core.api.messages.AiAssistantMessage;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.AiChatUsage;
import io.nop.ai.core.api.messages.AiMessage;
import io.nop.ai.core.api.messages.AiMessageAttachment;
import io.nop.ai.core.api.messages.AiUserMessage;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.ai.core.api.tool.ToolSpecification;
import io.nop.api.core.beans.ErrorBean;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.JavaGenericTypeBuilder;

import java.sql.Timestamp;
import java.util.ArrayList;
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

    static final String TITLE_THINK = "### Think\n";

    static final String MARKER_THINK_END = "\n-----**think**------\n";

    static final String TITLE_MESSAGE_META = "### Metadata\n";

    static final String TITLE_ATTACHMENTS = "### Attachments\n";

    static final String TITLE_CONTENT = "### Content\n";

    static final String TITLE_TOOLS = "### Tools\n";

    static final String JSON_BLOCK_BEGIN = "```json\n";

    static final String JSON_BLOCK_END = "\n```";

    static final String MARKER_CHAT_END = "=======**==**=======\n";

    static final IGenericType ATTACHMENTS_TYPE = JavaGenericTypeBuilder.buildListType(AiMessageAttachment.class);
    static final IGenericType TOOLS_TYPE = JavaGenericTypeBuilder.buildListType(ToolSpecification.class);

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

        // chatOptions不参与cache计算，这样可以对比不同的model对于同一个prompt的请求效果

        writeRequest(sb, prompt, null, 0, null);
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

        if (prompt.getTools() != null && !prompt.getTools().isEmpty()) {
            sb.append(TITLE_TOOLS);
            appendJson(sb, prompt.getTools());
        }

        List<AiMessage> messages = prompt.getMessages();
        for (AiMessage message : messages) {
            appendMessage(sb, TITLE_MESSAGE, message);
        }
    }

    @Override
    public String serialize(AiChatExchange exchange) {
        StringBuilder sb = new StringBuilder();
        writeExchange(sb, exchange);
        return sb.toString();
    }

    @Override
    public String serializeList(List<AiChatExchange> exchangeList) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0, n = exchangeList.size(); i < n; i++) {
            if (i > 0)
                sb.append("\n\n");
            AiChatExchange exchange = exchangeList.get(i);
            writeExchange(sb, exchange);
        }
        return sb.toString();
    }

    protected void writeExchange(StringBuilder sb, AiChatExchange exchange) {
        writeRequest(sb, exchange.getPrompt(), exchange.getChatOptions(),
                exchange.getBeginTime(), exchange.getExchangeId());

        if (exchange.isInvalid()) {
            sb.append(TITLE_ERROR);
            ErrorBean errorBean = exchange.getInvalidReason();
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
    }

    void appendJson(StringBuilder sb, Object bean) {
        sb.append('\n');
        sb.append(JSON_BLOCK_BEGIN);
        if (bean != null)
            sb.append(JsonTool.serialize(bean, true));
        sb.append(JSON_BLOCK_END);
        sb.append("\n\n");
    }

    void appendMessage(StringBuilder sb, String title, AiMessage message) {
        sb.append(title);
        if (message.getRole() != null) {
            sb.append('[').append(message.getRole()).append("]");
        }
        sb.append('\n');

        if (message instanceof AiAssistantMessage) {
            AiAssistantMessage assistant = (AiAssistantMessage) message;
            if (assistant.getThink() != null) {
                sb.append("\n");
                sb.append(TITLE_THINK);
                sb.append(assistant.getThink());
                sb.append(MARKER_THINK_END);
                sb.append("\n");
            }
        }

        if (message.getMetadata() != null) {
            sb.append("\n");
            sb.append(TITLE_MESSAGE_META);
            appendJson(sb, message.getMetadata());
            sb.append("\n");
        }

        if (message instanceof AiUserMessage) {
            AiUserMessage userMessage = (AiUserMessage) message;
            if (userMessage.getAttachments() != null && !userMessage.getAttachments().isEmpty()) {
                sb.append('\n');
                sb.append(TITLE_ATTACHMENTS);
                appendJson(sb, ((AiUserMessage) message).getAttachments());
                sb.append("\n");
            }
        }

        sb.append("\n");
        sb.append(TITLE_CONTENT);
        if (message.getContent() != null) {
            sb.append(message.getContent());
        }
        sb.append(MARKER_CONTENT_END);
        sb.append("\n");
    }

    @Override
    public AiChatExchange deserialize(String text) {
        text = StringHelper.replace(text, "\r\n", "\n");

        TextScanner scanner = TextScanner.fromString(null, text);

        AiChatExchange response = readExchange(scanner);
        scanner.checkEnd();
        return response;
    }

    @Override
    public List<AiChatExchange> deserializeList(String text) {
        text = StringHelper.replace(text, "\r\n", "\n");

        TextScanner scanner = TextScanner.fromString(null, text);
        scanner.skipBlank();

        List<AiChatExchange> ret = new ArrayList<>();
        while (!scanner.isEnd()) {
            AiChatExchange response = readExchange(scanner);
            ret.add(response);
        }
        scanner.checkEnd();
        return ret;
    }

    protected AiChatExchange readExchange(TextScanner scanner) {
        AiChatExchange response = new AiChatExchange();
        Prompt prompt = new Prompt();

        // 解析Chat标题行
        scanner.match(TITLE_CHAT);

        int retryTimes = scanner.nextInt();
        prompt.setRetryTimes(retryTimes);
        scanner.consume('-');
        String line = scanner.nextUntil('\n', true).toString();
        int pos = line.indexOf('[');
        String promptName = line;
        if (pos < 0) {
            if (!promptName.isEmpty())
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

        if (scanner.tryMatch(TITLE_TOOLS)) {
            String json = consumeJsonBlock(scanner);
            prompt.setTools(JsonTool.parseBeanFromText(json, TOOLS_TYPE));
        }

        // 解析Messages
        while (scanner.tryMatch(TITLE_MESSAGE)) {
            AiMessage message = parseMessage(scanner);
            prompt.addMessage(message);
        }

        response.setPrompt(prompt);

        // 解析Error
        if (scanner.tryMatch(TITLE_ERROR)) {
            response.setInvalid(true);
            String errorText = consumeJsonBlock(scanner);
            if (!StringHelper.isEmpty(errorText))
                response.setInvalidReason(JsonTool.parseBeanFromText(errorText, ErrorBean.class));
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
        return json.trim();
    }

    private AiMessage parseMessage(TextScanner scanner) {
        scanner.match('[');
        String role = scanner.nextUntil(']', false).trim().toString();
        scanner.match(']');
        scanner.skipBlank();

        AbstractTextMessage message = (AbstractTextMessage) AiMessage.create(role);
        if (scanner.tryMatch(TITLE_THINK)) {
            String think = scanner.nextUntil(MARKER_THINK_END, false).toString();
            message.setThink(think);
            scanner.consume(MARKER_THINK_END);
            scanner.skipBlank();
        }

        // 解析元数据
        if (scanner.tryMatch(TITLE_MESSAGE_META)) {
            String json = consumeJsonBlock(scanner);
            message.setMetadata(JsonTool.parseMap(json));
            scanner.skipBlank();
        }

        if (scanner.tryMatch(TITLE_ATTACHMENTS)) {
            String json = consumeJsonBlock(scanner);
            message.setAttachments(JsonTool.parseBeanFromText(json, ATTACHMENTS_TYPE));
        }

        scanner.match(TITLE_CONTENT);
        String content = scanner.nextUntil(MARKER_CONTENT_END, false).toString();
        message.setContent(content);
        scanner.consume(MARKER_CONTENT_END);
        scanner.skipBlank();

        return message;
    }
}
