package io.nop.ai.agent.memory;

import io.nop.ai.agent.engine.NopAiAgentException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Composite adapter-backed {@link IAiMemoryStore} implementation (plan 215 /
 * L4-3). Delegates the {@link IAiMemoryStore} 8-method contract to a triplet
 * of {@link IStorageAdapter} / {@link IEmbeddingAdapter} / {@link IVectorAdapter},
 * so an integrator can swap the working-memory backend to DB / vector stores
 * without touching the {@link IAiMemoryStore} contract.
 *
 * <p>Per-session isolation: each instance represents exactly one session (a
 * {@link AdapterBackedMemoryStoreProvider} resolves one instance per
 * sessionId), mirroring {@link InMemoryAiMemoryStore}.
 *
 * <p>Behaviour contract (设计裁定 2 + 5):
 * <ul>
 *   <li><b>CRUD</b> ({@code add} / {@code batchAdd} / {@code update} /
 *       {@code remove} / {@code getAll} / {@code getLastN}) delegate to
 *       {@link IStorageAdapter}.</li>
 *   <li><b>Indexing on write</b>: {@code add} / {@code batchAdd} / {@code update}
 *       first persist to storage, then — only when
 *       {@link IEmbeddingAdapter#isAvailable()} is {@code true} — embed the
 *       content and {@link IVectorAdapter#index} it. When embedding is
 *       unavailable the store still persists (graceful: storage works, vector
 *       index is skipped — keyword search remains usable).</li>
 *   <li><b>search</b>: capability-query first. When {@code isAvailable() == true}
 *       → embed the query, ask {@link IVectorAdapter#search} for the top-k
 *       keys, resolve each key to its item via {@link IStorageAdapter#loadByKey}
 *       (preserving similarity order, skipping orphan keys). When
 *       {@code isAvailable() == false} → fall back to keyword substring
 *       matching over content / type / key (identical to
 *       {@link InMemoryAiMemoryStore#search}), WITHOUT calling
 *       {@code embed()} and WITHOUT catching-and-swallowing an exception
 *       (Minimum Rules #24). Empty / null query returns an empty list in both
 *       paths (consistent with {@link InMemoryAiMemoryStore}).</li>
 *   <li><b>readBudgeted</b>: loads all items from storage and applies the
 *       same pinned-first / priority-desc / token-budget algorithm as
 *       {@link InMemoryAiMemoryStore#readBudgeted}.</li>
 *   <li><b>remove</b>: removes from storage, then (when embedding available)
 *       {@link IVectorAdapter#remove} so the index and storage stay
 *       consistent.</li>
 * </ul>
 *
 * <p>Shipped default is NOT this class — {@link InMemoryMemoryStoreProvider}
 * remains the engine default (设计裁定 4, zero behavioural regression). This
 * store is opt-in via {@link AdapterBackedMemoryStoreProvider} wired through
 * {@code DefaultAgentEngine.setMemoryStoreProvider}.
 */
public class AdapterBackedAiMemoryStore implements IAiMemoryStore {

    /**
     * Default top-K passed to {@link IVectorAdapter#search} for the semantic
     * search path. Generous enough to surface all relevant items for typical
     * short working-memory stores while bounding linear-scan cost in the
     * reference in-memory vector adapter.
     */
    public static final int DEFAULT_VECTOR_SEARCH_TOP_K = 10;

    private final IStorageAdapter storage;
    private final IEmbeddingAdapter embedding;
    private final IVectorAdapter vector;
    private final int vectorSearchTopK;

    public AdapterBackedAiMemoryStore(IStorageAdapter storage,
                                      IEmbeddingAdapter embedding,
                                      IVectorAdapter vector) {
        this(storage, embedding, vector, DEFAULT_VECTOR_SEARCH_TOP_K);
    }

    public AdapterBackedAiMemoryStore(IStorageAdapter storage,
                                      IEmbeddingAdapter embedding,
                                      IVectorAdapter vector,
                                      int vectorSearchTopK) {
        if (storage == null) {
            throw new NopAiAgentException("IStorageAdapter must not be null");
        }
        if (embedding == null) {
            throw new NopAiAgentException("IEmbeddingAdapter must not be null");
        }
        if (vector == null) {
            throw new NopAiAgentException("IVectorAdapter must not be null");
        }
        this.storage = storage;
        this.embedding = embedding;
        this.vector = vector;
        this.vectorSearchTopK = vectorSearchTopK > 0 ? vectorSearchTopK : DEFAULT_VECTOR_SEARCH_TOP_K;
    }

    public IStorageAdapter getStorage() {
        return storage;
    }

    public IEmbeddingAdapter getEmbedding() {
        return embedding;
    }

    public IVectorAdapter getVector() {
        return vector;
    }

    public boolean isSemanticSearchEnabled() {
        return embedding.isAvailable();
    }

    @Override
    public List<AiMemoryItem> getAll(Map<String, Object> filters) {
        String typeFilter = extractTypeFilter(filters);
        return storage.loadAll(typeFilter);
    }

    @Override
    public List<AiMemoryItem> getLastN(int n) {
        if (n <= 0) {
            return List.of();
        }
        return storage.loadAll(null).stream()
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
        if (embedding.isAvailable()) {
            return semanticSearch(query);
        }
        return keywordSearch(query);
    }

    @Override
    public void add(AiMemoryItem item) {
        String key = resolveKey(item);
        item.setKey(key);
        storage.save(item);
        indexIfEnabled(item);
    }

    @Override
    public List<AiMemoryItem> readBudgeted(int maxTokens, Map<String, Object> context) {
        if (maxTokens <= 0) {
            return List.of();
        }
        List<AiMemoryItem> all = storage.loadAll(null);
        List<AiMemoryItem> pinned = new ArrayList<>();
        List<AiMemoryItem> others = new ArrayList<>();
        for (AiMemoryItem it : all) {
            if (it.isPinned()) {
                pinned.add(it);
            } else {
                others.add(it);
            }
        }
        others.sort(Comparator.comparingInt(AiMemoryItem::getPriority).reversed()
                .thenComparing(AiMemoryItem::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())));

        List<AiMemoryItem> selected = new ArrayList<>(pinned);
        int used = pinned.stream().mapToInt(AiMemoryItem::getTokenEstimate).sum();
        for (AiMemoryItem it : others) {
            int estimate = it.getTokenEstimate();
            if (used + estimate > maxTokens) {
                continue;
            }
            selected.add(it);
            used += estimate;
        }
        return selected;
    }

    @Override
    public void update(String key, AiMemoryItem item) {
        if (key == null || key.isEmpty()) {
            throw new NopAiAgentException("key must not be null or empty");
        }
        if (item == null) {
            throw new NopAiAgentException("AiMemoryItem must not be null");
        }
        storage.update(key, item);
        // Refresh the vector index entry in place (upsert). The composite store
        // is the key authority for indexing; embed the content just persisted.
        if (embedding.isAvailable() && item.getContent() != null) {
            double[] vec = embedding.embed(item.getContent());
            vector.index(key, vec);
        }
    }

    @Override
    public void remove(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        storage.remove(key);
        if (embedding.isAvailable()) {
            vector.remove(key);
        }
    }

    @Override
    public void batchAdd(List<AiMemoryItem> items) {
        if (items == null) {
            return;
        }
        for (AiMemoryItem it : items) {
            add(it);
        }
    }

    /**
     * Clear all items (used by {@code write-memory} {@code clear} action). Even
     * when semantic search is disabled the vector index is cleared defensively
     * in case it was populated earlier.
     */
    public synchronized void clear() {
        List<AiMemoryItem> all = storage.loadAll(null);
        for (AiMemoryItem it : all) {
            if (it.getKey() != null) {
                storage.remove(it.getKey());
                if (embedding.isAvailable()) {
                    vector.remove(it.getKey());
                }
            }
        }
    }

    public int size() {
        return storage.loadAll(null).size();
    }

    // ----- internals -----

    private static String resolveKey(AiMemoryItem item) {
        if (item.getKey() != null && !item.getKey().isEmpty()) {
            return item.getKey();
        }
        return java.util.UUID.randomUUID().toString();
    }

    private void indexIfEnabled(AiMemoryItem item) {
        if (item == null || !embedding.isAvailable()) {
            return;
        }
        String key = item.getKey();
        if (key == null || key.isEmpty() || item.getContent() == null) {
            return;
        }
        double[] vec = embedding.embed(item.getContent());
        vector.index(key, vec);
    }

    private List<AiMemoryItem> semanticSearch(String query) {
        double[] queryVec = embedding.embed(query);
        List<String> keys = vector.search(queryVec, vectorSearchTopK);
        List<AiMemoryItem> result = new ArrayList<>(keys.size());
        for (String k : keys) {
            AiMemoryItem it = storage.loadByKey(k);
            if (it != null) {
                result.add(it);
            }
        }
        return result;
    }

    private List<AiMemoryItem> keywordSearch(String query) {
        String lower = query.toLowerCase();
        return storage.loadAll(null).stream()
                .filter(item -> matches(item, lower))
                .sorted(Comparator.comparing(AiMemoryItem::getCreateTime,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private static boolean matches(AiMemoryItem item, String lower) {
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

    private static String extractTypeFilter(Map<String, Object> filters) {
        if (filters == null) {
            return null;
        }
        Object t = filters.get("type");
        return t != null ? t.toString() : null;
    }
}
