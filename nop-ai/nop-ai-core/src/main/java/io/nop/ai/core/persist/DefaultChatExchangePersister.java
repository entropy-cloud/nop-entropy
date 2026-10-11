package io.nop.ai.core.persist;

import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.secure.SecureDefault;
import io.nop.ai.core.AiCoreConfigs;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.commons.crypto.ITextCipher;
import io.nop.commons.crypto.impl.AESTextCipher;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;

@SecureDefault
public class DefaultChatExchangePersister implements IChatExchangePersister {
    static final String ENCRYPTED_MARKER = "### Encrypted ###\n";

    private ITextCipher textCipher = new AESTextCipher();

    private boolean encryptEnabled = AiCoreConfigs.CFG_AI_PERSIST_EXCHANGE_ENCRYPT.get();

    public void setTextCipher(ITextCipher textCipher) {
        this.textCipher = textCipher;
    }

    @InjectValue("@cfg:nop.ai.persist.exchange-encrypt|false")
    public void setEncryptEnabled(boolean encryptEnabled) {
        this.encryptEnabled = encryptEnabled;
    }

    public boolean isEncryptEnabled() {
        return encryptEnabled;
    }

    @Override
    public String serialize(ChatExchange exchange) {
        return maybeEncrypt(JsonTool.serialize(exchange, true));
    }

    @Override
    public ChatExchange deserialize(String text) {
        text = maybeDecrypt(text);
        if (StringHelper.isEmpty(text))
            return null;
        return JsonTool.parseBeanFromText(text, ChatExchange.class);
    }

    @Override
    public String calcRequestHash(ChatRequest request) {
        return StringHelper.md5Hash(JsonTool.serialize(request.getMessages(), true));
    }

    protected String maybeEncrypt(String text) {
        if (!encryptEnabled)
            return text;
        return ENCRYPTED_MARKER + textCipher.encrypt(text);
    }

    protected String maybeDecrypt(String text) {
        if (text != null && text.startsWith(ENCRYPTED_MARKER)) {
            return textCipher.decrypt(text.substring(ENCRYPTED_MARKER.length()));
        }
        return text;
    }
}
