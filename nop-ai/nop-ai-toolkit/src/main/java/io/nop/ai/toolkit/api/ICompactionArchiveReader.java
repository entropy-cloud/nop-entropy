package io.nop.ai.toolkit.api;

/**
 * Read-only view of the per-session compaction archive. Exposed to toolkit
 * tools (e.g. {@code read-ref}) via {@link IToolExecuteContext#getCompactionArchiveReader()}
 * so they can read back original content that was replaced by a {@code shortRef}
 * pointer during reference-style compaction.
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.2 (Decision B: archive interface ownership = toolkit; Decision C: only the
 * read-only view is exposed to tools).
 *
 * <p>Lookup is by content hash (the read-back key carried by a {@code shortRef}).
 * Implementations MUST be deterministic: the same hash always returns the same
 * content (or {@code null} when no entry exists). Implementations MUST NOT
 * silently substitute different content for a missing hash — callers rely on
 * the {@code null} return to detect "reference invalid" and fail loud.
 */
public interface ICompactionArchiveReader {

    /**
     * Read back the archived content for the given hash.
     *
     * @param hash the content hash (e.g. {@code "sha256:<hex>"}); never null
     * @return the archived original content, or {@code null} if no entry exists
     *         for this hash (caller treats null as "reference invalid / content
     *         changed" and surfaces an explicit error)
     */
    String getByHash(String hash);

    /**
     * @param hash the content hash; never null
     * @return {@code true} if the archive holds an entry for this hash
     */
    boolean contains(String hash);
}
