package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Functional in-memory {@link IStorageAdapter} reference implementation
 * (test / non-production). Backed by a {@link ConcurrentHashMap} of
 * key→{@link AiMemoryItem}, mirroring the {@link InMemoryAiMemoryStore}
 * persistence shape so the composite adapter-backed store can be exercised
 * end-to-end without a DB.
 *
 * <p>Per-session isolation: each instance represents one session (no
 * {@code sessionId} parameter — same convention as {@link IAiMemoryStore}).
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code save} / {@code batchSave} normalize the item (auto-fill
 *       {@code createTime}, auto-generate a UUID key when absent) and store a
 *       defensive copy (the composite store must not alias caller-mutated
 *       items).</li>
 *   <li>{@code loadAll(typeFilter)} returns items sorted ascending by
 *       {@code createTime}; {@code typeFilter} == null / empty means no type
 *       filtering.</li>
 *   <li>{@code loadByKey} returns {@code null} for absent keys (NOT an
 *       exception).</li>
 *   <li>{@code update} sets the stored item's key to the supplied {@code key}
 *       (key normalized) and overwrites.</li>
 *   <li>{@code remove} is idempotent (missing key is a no-op).</li>
 * </ul>
 *
 * <p>Production DB-backed implementation (raw JDBC, e.g. a
 * {@code DbStorageAdapter} successor) follows the same contract.
 */
public class InMemoryStorageAdapter implements IStorageAdapter {

    private final Map<String, AiMemoryItem> items = new ConcurrentHashMap<>();

    @Override
    public void save(AiMemoryItem item) {
        if (item == null) {
            throw new NopAiAgentException("AiMemoryItem must not be null");
        }
        AiMemoryItem normalized = normalize(item);
        items.put(resolveKey(normalized), normalized);
    }

    @Override
    public List<AiMemoryItem> loadAll(String typeFilter) {
        boolean hasFilter = typeFilter != null && !typeFilter.isEmpty();
        return items.values().stream()
                .filter(item -> !hasFilter || typeFilter.equals(item.getType()))
                .sorted(Comparator.comparing(AiMemoryItem::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public AiMemoryItem loadByKey(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return items.get(key);
    }

    @Override
    public void update(String key, AiMemoryItem item) {
        if (key == null || key.isEmpty()) {
            throw new NopAiAgentException("key must not be null or empty");
        }
        if (item == null) {
            throw new NopAiAgentException("AiMemoryItem must not be null");
        }
        AiMemoryItem normalized = normalize(item);
        normalized.setKey(key);
        items.put(key, normalized);
    }

    @Override
    public void remove(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        items.remove(key);
    }

    @Override
    public void batchSave(List<AiMemoryItem> newItems) {
        if (newItems == null) {
            return;
        }
        for (AiMemoryItem item : newItems) {
            save(item);
        }
    }

    public synchronized void clear() {
        items.clear();
    }

    public int size() {
        return items.size();
    }

    private AiMemoryItem normalize(AiMemoryItem item) {
        AiMemoryItem copy = new AiMemoryItem();
        copy.setKey(item.getKey());
        copy.setType(item.getType());
        copy.setContent(item.getContent());
        copy.setCreateTime(item.getCreateTime() != null ? item.getCreateTime() : LocalDateTime.now());
        copy.setPriority(item.getPriority());
        copy.setTokenEstimate(item.getTokenEstimate());
        copy.setPinned(item.isPinned());
        copy.setChecksum(item.getChecksum());
        copy.setLastAccessTime(item.getLastAccessTime());
        copy.setAccessCount(item.getAccessCount());
        return copy;
    }

    private String resolveKey(AiMemoryItem item) {
        if (item.getKey() != null && !item.getKey().isEmpty()) {
            return item.getKey();
        }
        return UUID.randomUUID().toString();
    }
}
