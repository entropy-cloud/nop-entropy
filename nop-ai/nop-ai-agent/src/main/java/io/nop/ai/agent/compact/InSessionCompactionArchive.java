package io.nop.ai.agent.compact;

import io.nop.ai.toolkit.api.ICompactionArchive;
import io.nop.ai.toolkit.compact.ShortRefHasher;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-session, in-memory, content-addressed implementation of
 * {@link ICompactionArchive}. Lives for the duration of a single
 * {@code AgentSession}; the session holds the only reference and the archive
 * is garbage-collected when the session ends.
 *
 * <p><b>Content addressing</b>: {@link #put(String)} computes the SHA-256 hash
 * of the content via {@link ShortRefHasher} and uses it as the key. The same
 * content always yields the same hash, so it is stored only once
 * (deduplicated) — subsequent {@code put} calls for identical content are
 * idempotent and return the existing hash.
 *
 * <p><b>Fail-fast</b> (Minimum Rules #24): null/empty content is rejected
 * with {@link IllegalArgumentException} — never silently stored. Hash
 * computation failures propagate as {@link IllegalStateException} from
 * {@link ShortRefHasher} rather than being swallowed.
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.2 (Decision B: archive interface in toolkit, impl in agent/compact;
 * Decision G: per-session instance hosted on {@code AgentSession}).
 */
public final class InSessionCompactionArchive implements ICompactionArchive {

    private final Map<String, String> store = new HashMap<>();

    @Override
    public String put(String content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException(
                    "InSessionCompactionArchive.put requires non-null, non-empty content");
        }
        String hash = ShortRefHasher.hash(content);
        // Idempotent: identical content maps to the same hash key, so a
        // repeated put overwrites the same entry with identical content —
        // deduplicated by construction.
        store.putIfAbsent(hash, content);
        return hash;
    }

    @Override
    public String getByHash(String hash) {
        Objects.requireNonNull(hash, "hash must not be null");
        return store.get(hash);
    }

    @Override
    public boolean contains(String hash) {
        Objects.requireNonNull(hash, "hash must not be null");
        return store.containsKey(hash);
    }

    /**
     * @return the number of distinct archived entries (for test/diagnostics)
     */
    public int size() {
        return store.size();
    }
}
