package io.nop.ai.core.persist;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.api.messages.Prompt;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class DefaultAiChatResponseCache implements IAiChatResponseCache {
    static final Logger LOG = LoggerFactory.getLogger(DefaultAiChatResponseCache.class);

    private String cacheDir;

    private long cacheTtlSeconds;

    private IAiChatExchangePersister chatExchangePersister;

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Cache entry time-to-live in seconds. 0 (default) means entries never
     * expire (backward compatible). Expiry is lazy: a cached entry whose
     * file mtime is older than the TTL is treated as a cache miss on read
     * and deleted. The cache file format is not modified.
     */
    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public void setChatExchangePersister(IAiChatExchangePersister chatExchangePersister) {
        this.chatExchangePersister = chatExchangePersister;
    }

    @Override
    public AiChatExchange loadCachedResponse(Prompt prompt, AiChatOptions options) {
        IResource resource = getCacheResource(prompt, options);
        if (!resource.exists())
            return null;

        if (cacheTtlSeconds > 0 && isExpired(resource)) {
            LOG.info("nop.ai.cache-expired:promptName={},cachedPath={},ttlSeconds={}",
                    prompt.getName(), resource.getPath(), cacheTtlSeconds);
            resource.delete();
            return null;
        }

        LOG.info("nop.ai.use-cached-response:promptName={},cachedPath={}", prompt.getName(), resource.getPath());
        AiChatExchange exchange = chatExchangePersister.load(resource);
        exchange.makeChatOptions().setSessionId(options.getSessionId());
        return exchange;
    }

    private boolean isExpired(IResource resource) {
        long ageMs = System.currentTimeMillis() - resource.lastModified();
        return ageMs > cacheTtlSeconds * 1000L;
    }

    @Override
    public void saveCachedResponse(AiChatExchange exchange) {
        IResource resource = getCacheResource(exchange.getPrompt(), exchange.getChatOptions());
        chatExchangePersister.save(resource, exchange);
    }

    String makeRequestHash(Prompt prompt, AiChatOptions options) {
        String hash = prompt.getRequestHash();
        if (hash == null) {
            hash = chatExchangePersister.calcRequestHash(prompt, options);
            prompt.setRequestHash(hash);
        }
        return hash;
    }

    IResource getCacheResource(Prompt prompt, AiChatOptions options) {
        String promptName = prompt.getName();
        if (promptName == null)
            promptName = "unnamed";

        String hash = makeRequestHash(prompt, options);
        String cachePath = StringHelper.safeFileName(options.getProvider()) + "/" + StringHelper.safeFileName(options.getModel()) + "/" + promptName;
        cachePath += "/" + hash.substring(0, 2) + '/' + hash.substring(2, 4) + '/' + hash + "-exchange.md";

        return new FileResource(new File(cacheDir, cachePath));
    }
}