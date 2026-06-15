package io.nop.ai.agent.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import io.nop.ai.agent.engine.NopAiAgentException;

/**
 * Functional in-memory {@link IAiMemoryStore} implementation covering all 8
 * interface methods, including the 4 Phase 2 default methods
 * ({@code readBudgeted} / {@code update} / {@code remove} / {@code batchAdd})
 * that the interface declares as {@link UnsupportedOperationException}.
 *
 * <p>Each instance represents one session's working memory (per-session
 * isolation). A {@link io.nop.ai.agent.memory.IMemoryStoreProvider} resolves
 * instances per {@code sessionId}.
 *
 * <p>Thread-safe: a {@link ConcurrentHashMap} backs the key→item map, and
 * each mutation is guarded by {@code synchronized(this)} on the compound
 * read-modify-write sequences that the budgeted/sort/iterate operations need.
 *
 * <p>Out of scope (deferred to L4-3 IMemoryAdapter successor): DB persistence,
 * embedding-based semantic search, vector retrieval, retention / TTL / capacity
 * limits, and {@code AgentSession} serialization of memory state.
 */
public class InMemoryAiMemoryStore implements IAiMemoryStore {

    private final Map<String, AiMemoryItem> items = new ConcurrentHashMap<>();

    @Override
    public List<AiMemoryItem> getAll(Map<String, Object> filters) {
        String typeFilter = filters != null && filters.get("type") != null
                ? filters.get("type").toString()
                : null;
        return items.values().stream()
                .filter(item -> typeFilter == null || typeFilter.equals(item.getType()))
                .sorted(Comparator.comparing(AiMemoryItem::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public List<AiMemoryItem> getLastN(int n) {
        if (n <= 0) {
            return List.of();
        }
        return items.values().stream()
                .sorted(Comparator.comparing(AiMemoryItem::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                        .reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    @Override
    public List<AiMemoryItem> search(String query) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        String lower = query.toLowerCase();
        return items.values().stream()
                .filter(item -> matches(item, lower))
                .sorted(Comparator.comparing(AiMemoryItem::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public void add(AiMemoryItem item) {
        if (item == null) {
            throw new NopAiAgentException("AiMemoryItem must not be null");
        }
        AiMemoryItem normalized = normalize(item);
        items.put(resolveKey(normalized), normalized);
    }

    @Override
    public List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
        if (maxTokens <= 0) {
            return List.of();
        }
        synchronized (this) {
            List<AiMemoryItem> pinned = new ArrayList<>();
            List<AiMemoryItem> others = new ArrayList<>();
            for (AiMemoryItem item : items.values()) {
                if (item.isPinned()) {
                    pinned.add(item);
                } else {
                    others.add(item);
                }
            }
            others.sort(Comparator.comparingInt(AiMemoryItem::getPriority).reversed()
                    .thenComparing(AiMemoryItem::getCreateTime,
                            Comparator.nullsFirst(Comparator.naturalOrder())));

            List<AiMemoryItem> selected = new ArrayList<>(pinned);
            int used = pinned.stream().mapToInt(AiMemoryItem::getTokenEstimate).sum();

            for (AiMemoryItem item : others) {
                int estimate = item.getTokenEstimate();
                if (used + estimate > maxTokens) {
                    continue;
                }
                selected.add(item);
                used += estimate;
            }
            return selected;
        }
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
        if (key == null) {
            return;
        }
        items.remove(key);
    }

    @Override
    public void batchAdd(List<AiMemoryItem> newItems) {
        if (newItems == null) {
            return;
        }
        for (AiMemoryItem item : newItems) {
            add(item);
        }
    }

    /**
     * Remove all items (used by {@code write-memory} {@code clear} action).
     */
    public synchronized void clear() {
        items.clear();
    }

    public int size() {
        return items.size();
    }

    public AiMemoryItem findByKey(String key) {
        if (key == null) {
            return null;
        }
        return items.get(key);
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
        return java.util.UUID.randomUUID().toString();
    }

    private boolean matches(AiMemoryItem item, String lower) {
        String content = item.getContent();
        if (content != null && content.toLowerCase().contains(lower)) {
            return true;
        }
        String type = item.getType();
        if (type != null && type.toLowerCase().contains(lower)) {
            return true;
        }
        String key = item.getKey();
        return key != null && key.toLowerCase().contains(lower);
    }
}
