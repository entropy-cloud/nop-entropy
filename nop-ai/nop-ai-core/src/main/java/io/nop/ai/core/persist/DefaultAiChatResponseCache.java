package io.nop.ai.core.persist;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.secure.SecureDefault;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

@SecureDefault
public class DefaultAiChatResponseCache implements IAiChatResponseCache {
    static final Logger LOG = LoggerFactory.getLogger(DefaultAiChatResponseCache.class);

    private String cacheDir;

    private long cacheTtlSeconds;

    private IChatExchangePersister chatExchangePersister;

    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    /**
     * Cache entry time-to-live in seconds. 0 (default) means entries never
     * expire (backward compatible). Expiry is lazy: a cached entry whose
     * file mtime is older than the TTL is treated as a cache miss on read
     * and deleted.
     */
    public void setCacheTtlSeconds(long cacheTtlSeconds) {
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public void setChatExchangePersister(IChatExchangePersister chatExchangePersister) {
        this.chatExchangePersister = chatExchangePersister;
    }

    @Override
    public ChatExchange loadCachedResponse(ChatRequest request, ChatOptions options, String promptName) {
        IResource resource = getCacheResource(request, options, promptName);
        if (!resource.exists())
            return null;

        if (cacheTtlSeconds > 0 && isExpired(resource)) {
            LOG.info("nop.ai.cache-expired:promptName={},cachedPath={},ttlSeconds={}",
                    promptName, resource.getPath(), cacheTtlSeconds);
            resource.delete();
            return null;
        }

        LOG.info("nop.ai.use-cached-response:promptName={},cachedPath={}", promptName, resource.getPath());
        ChatExchange exchange = chatExchangePersister.load(resource);
        if (exchange != null && exchange.getRequest() != null)
            exchange.getRequest().makeOptions().setSessionId(options.getSessionId());
        return exchange;
    }

    private boolean isExpired(IResource resource) {
        long ageMs = CoreMetrics.currentTimeMillis() - resource.lastModified();
        return ageMs > cacheTtlSeconds * 1000L;
    }

    @Override
    public void saveCachedResponse(ChatExchange exchange, String promptName) {
        IResource resource = getCacheResource(exchange.getRequest(), exchange.getRequest().makeOptions(), promptName);
        chatExchangePersister.save(resource, exchange);
    }

    String makeRequestHash(ChatRequest request) {
        return chatExchangePersister.calcRequestHash(request);
    }

    IResource getCacheResource(ChatRequest request, ChatOptions options, String promptName) {
        if (promptName == null)
            promptName = "unnamed";

        String hash = makeRequestHash(request);
        String cachePath = StringHelper.safeFileName(options.getProvider()) + "/" + StringHelper.safeFileName(options.getModel()) + "/" + promptName;
        cachePath += "/" + hash.substring(0, 2) + '/' + hash.substring(2, 4) + '/' + hash + "-exchange.json";

        return new FileResource(new File(cacheDir, cachePath));
    }
}
