package io.nop.ai.toolkit.api;

/**
 * Per-session compaction archive that stores original content addressable by
 * content hash. The write side (reference-style compaction strategy in
 * {@code nop-ai-agent}) PUTs original content here before replacing it with a
 * {@code shortRef} pointer in the chat message; the read side ({@code read-ref}
 * tool in {@code nop-ai-toolkit}) GETs it back via the read-only view
 * {@link ICompactionArchiveReader}.
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.2 (Decision B: interface in toolkit, impl in agent/compact; Decision G:
 * per-session instance hosted on {@code AgentSession}).
 *
 * <p>Content-addressed storage: the same content always yields the same hash
 * (single stored copy). {@link #put(String)} returns the hash that becomes the
 * read-back key embedded in the {@code shortRef}.
 *
 * <p>Fail-fast: {@code null} or empty content MUST be rejected with an explicit
 * exception — never silently stored or substituted (Minimum Rules #24).
 */
public interface ICompactionArchive extends ICompactionArchiveReader {

    /**
     * Archive the original content and return the content hash (the read-back
     * key). The same content always yields the same hash; implementations MAY
     * store it only once (deduplicated).
     *
     * @param content the original content to archive; must not be null or empty
     * @return the content hash (e.g. {@code "sha256:<hex>"})
     * @throws IllegalArgumentException if content is null or empty
     */
    String put(String content);
}
