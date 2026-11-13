package io.nop.ai.core.persist;

import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.secure.SecureDefault;
import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;
import io.nop.ai.core.prompt.ChatExchange;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.ai.core.NopAiCoreErrors.ARG_MSG;

/**
 * File-backed {@link IAiChatResponseCache} with a disk envelope (3.1-C5-002,
 * plan 2556 裁定 D):
 * <ul>
 *   <li><b>Entry-count bound</b> — {@code nop.ai.service.cache-max-entries}
 *       (default 1000, 0 = unbounded, not recommended). An LRU index (access-
 *       order map) evicts the least-recently-used entry when the bound is
 *       exceeded; the eviction callback deletes the corresponding cache file,
 *       so the disk footprint is bounded together with the index.</li>
 *   <li><b>Per-entry size bound</b> — {@code nop.ai.service.cache-max-entry-bytes}
 *       (default 2MB). The exchange is serialized <i>before</i> the write and
 *       measured; an oversized entry is skipped with a WARN (never written).
 *       On load, an existing file larger than the bound is treated as a miss
 *       and deleted (read cap).</li>
 *   <li><b>Path-traversal guard (CL-041)</b> — {@code promptName} is validated
 *       against path components before file-path construction: {@code ..} /
 *       {@code .} components, absolute paths, backslashes and NUL are rejected
 *       with a typed {@link NopAiCoreException}.</li>
 * </ul>
 *
 * <p>TTL semantics unchanged: {@code nop.ai.service.cache-ttl} default 0 means
 * entries never expire (lazy expiry on read only).
 *
 * <p><b>Why a hand-rolled LRU instead of Caffeine</b>: the eviction callback
 * must delete the cache file synchronously and deterministically before the
 * replacement file is written (same key → same path). Caffeine's removal
 * listener fires asynchronously on an executor, which would race the write and
 * could delete a freshly written entry. The access-order map keeps eviction
 * inline in the save path.
 */
@SecureDefault
public class DefaultAiChatResponseCache implements IAiChatResponseCache {
    static final Logger LOG = LoggerFactory.getLogger(DefaultAiChatResponseCache.class);

    private String cacheDir;

    private long cacheTtlSeconds;

    /**
     * 3.1-C5-002 (plan 2556 裁定 D): max number of disk entries; LRU eviction
     * deletes the evicted entry's cache file. 0 = unbounded (not recommended).
     */
    private int maxEntries = 1000;

    /**
     * 3.1-C5-002 (plan 2556 裁定 D): per-entry serialized size cap in bytes;
     * over-cap entries are skipped on write and treated as misses (deleted) on
     * load. 0 = unbounded.
     */
    private long maxEntryBytes = 2_097_152L;

    private IChatExchangePersister chatExchangePersister;

    /**
     * LRU index: cache key (request hash + prompt) → absolute cache file path,
     * access-order iteration (get relocates to most-recently-used).
     * Guarded by {@code lruLock}.
     */
    private final LinkedHashMap<String, String> lruIndex = new LinkedHashMap<>(64, 0.75f, true);
    private final Object lruLock = new Object();

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

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntryBytes(long maxEntryBytes) {
        this.maxEntryBytes = maxEntryBytes;
    }

    public long getMaxEntryBytes() {
        return maxEntryBytes;
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

        // 3.1-C5-002 (plan 2556 裁定 D) ③: load read cap — an existing entry
        // over the per-entry bound is treated as a miss and deleted.
        if (maxEntryBytes > 0 && resource.length() > maxEntryBytes) {
            LOG.warn("nop.ai.cache-entry-oversize:promptName={},cachedPath={},size={},cap={}",
                    promptName, resource.getPath(), resource.length(), maxEntryBytes);
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

        // 3.1-C5-002 (plan 2556 裁定 D) ②: serialize-then-measure before the
        // write — an oversized entry is skipped entirely (never half-written).
        if (maxEntryBytes > 0) {
            String text = chatExchangePersister.serialize(exchange);
            long sizeBytes = text.getBytes(StandardCharsets.UTF_8).length;
            if (sizeBytes > maxEntryBytes) {
                LOG.warn("nop.ai.cache-entry-oversize-skip:promptName={},size={},cap={}",
                        promptName, sizeBytes, maxEntryBytes);
                return;
            }
        }

        chatExchangePersister.save(resource, exchange);
        recordInLru(resource);
    }

    private void recordInLru(IResource resource) {
        if (maxEntries <= 0)
            return;
        synchronized (lruLock) {
            String key = resource.getPath();
            // Store the concrete filesystem path (not the possibly-virtual
            // resource path) so the eviction callback deletes the right file.
            String filePath = resource.toFile().getAbsolutePath();
            lruIndex.put(key, filePath);
            // LRU eviction: drop the least-recently-used entries over the
            // bound; the eviction callback deletes the evicted cache file so
            // the disk envelope follows the index envelope.
            Iterator<Map.Entry<String, String>> it = lruIndex.entrySet().iterator();
            while (lruIndex.size() > maxEntries && it.hasNext()) {
                Map.Entry<String, String> eldest = it.next();
                it.remove();
                deleteQuietly(eldest.getValue());
            }
        }
    }

    private static void deleteQuietly(String path) {
        try {
            File file = new File(path);
            if (file.exists() && !file.delete()) {
                LOG.warn("nop.ai.cache-evict-delete-fail:path={}", path);
            } else {
                LOG.info("nop.ai.cache-evicted:path={}", path);
            }
        } catch (Exception e) {
            LOG.warn("nop.ai.cache-evict-delete-fail:path={}", path, e);
        }
    }

    String makeRequestHash(ChatRequest request) {
        return chatExchangePersister.calcRequestHash(request);
    }

    IResource getCacheResource(ChatRequest request, ChatOptions options, String promptName) {
        if (promptName == null)
            promptName = "unnamed";
        // CL-041 (3.1-C5-002, plan 2556 裁定 D): path-traversal guard — the
        // prompt name must be a safe relative path (its components are spliced
        // into the cache file path below). Namespaced names like "a/b" are
        // fine; "..", "." components, absolute paths, backslashes and NUL are
        // rejected with a typed error before any path construction.
        validatePromptName(promptName);

        String hash = makeRequestHash(request);
        String cachePath = StringHelper.safeFileName(options.getProvider()) + "/" + StringHelper.safeFileName(options.getModel()) + "/" + promptName;
        cachePath += "/" + hash.substring(0, 2) + '/' + hash.substring(2, 4) + '/' + hash + "-exchange.json";

        return new FileResource(new File(cacheDir, cachePath));
    }

    static void validatePromptName(String promptName) {
        if (promptName.startsWith("/") || promptName.indexOf('\\') >= 0 || promptName.indexOf('\0') >= 0) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT).param(ARG_MSG,
                    "promptName must be a relative path without backslashes: " + promptName);
        }
        for (String part : promptName.split("/")) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part)) {
                throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_CORE_INVALID_ARGUMENT).param(ARG_MSG,
                        "promptName must not contain empty, '.' or '..' path components: " + promptName);
            }
        }
    }
}
