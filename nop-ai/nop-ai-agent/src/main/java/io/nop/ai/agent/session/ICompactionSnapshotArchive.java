package io.nop.ai.agent.session;

import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.List;

/**
 * Per-session snapshot archive that stores the **complete pre-compaction
 * message history** for a single compaction event, addressable by the
 * {@code snapshotId} returned from {@link #put(List)}. This is the
 * reversibility / fail-safety side of the compaction pipeline (design §8.3):
 * before the coordinator hands the messages to the (lossy) compaction
 * pipeline, it archives the original list here so the pre-compaction history
 * can be retrieved later for audit / debugging, and so a failed compaction
 * leaves the original verifiably intact.
 *
 * <p><b>Per-compaction-event addressing</b> (NOT content-hash addressing):
 * every {@link #put} returns a fresh {@code snapshotId} keyed to that one
 * compaction event, even if two compactions happen to archive identical
 * message lists. This deliberately differs from the reference-style
 * {@code ICompactionArchive} (design §8.2), which is content-addressed and
 * deduplicates identical content. The two archives are independent
 * (design §8.3 Decision B): this one archives whole message lists per event,
 * the §8.2 one archives individual content blobs per hash.
 *
 * <p><b>Boundary (design §8.3 / reliability §5.4)</b>: this archive is an
 * in-pipeline original copy for reversibility / fail-safety. It is NOT the
 * checkpoint subsystem's {@code snapshot.json} (the resume-point persistence
 * cache used by crash/restart restore). compaction-triggered
 * {@code snapshot.json} file generation remains an independent successor.
 *
 * <p><b>Fail-fast</b> (Minimum Rules #24): {@code null} or empty message
 * lists MUST be rejected with an explicit exception — never silently stored
 * (an empty archive entry would masquerade as "compaction happened but
 * produced nothing", hiding a real failure). Missing-key lookups return
 * {@code null} (a legitimate "no such snapshot" answer, not a failure).
 *
 * <p>Design ref: {@code ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md}
 * §8.3 (Decision B: interface + impl in agent/session, no toolkit consumer).
 */
public interface ICompactionSnapshotArchive {

    /**
     * Archive the complete pre-compaction message list and return the
     * per-event {@code snapshotId} that becomes the read-back key. Each call
     * returns a fresh id (per-compaction-event addressing).
     *
     * @param messages the complete pre-compaction message history; must not
     *                 be null or empty
     * @return the per-event snapshot id (e.g. {@code "snap:<sessionId>:<ts>:<n>"})
     * @throws IllegalArgumentException if messages is null or empty
     */
    String put(List<ChatMessage> messages);

    /**
     * Retrieve the archived pre-compaction message list by its
     * {@code snapshotId}.
     *
     * @param snapshotId the id returned from {@link #put}; must not be null
     * @return the archived message list, or {@code null} if no snapshot is
     *         archived under this id (legitimate "no such snapshot")
     */
    List<ChatMessage> get(String snapshotId);
}
