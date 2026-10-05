package io.nop.ai.agent.session;

import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.core.lang.json.JsonTool;

/**
 * Plan 2295 (WI14): content fingerprints for persisted {@link ChatMessage}
 * instances — the mutation detector of the incremental snapshot write path.
 *
 * <p><b>Why fingerprints</b>: {@code ChatMessage} objects are fully mutable
 * ({@code setContent} et al.) and the engine mutates already-persisted
 * instances in place (output guardrail Block/Modify rewrites the assistant
 * message AFTER {@code saveLlmTurnCheckpoint} persisted it; input guardrail
 * rewrites persisted steering/injected user messages on the next iteration).
 * A reference-equality diff therefore cannot detect content drift — the
 * fingerprint of the message's serialized form can.
 *
 * <p><b>What it covers</b>: the fingerprint is computed over
 * {@link JsonTool#stringify(Object)} of the message — exactly the field set
 * that persistence would write. Any in-place mutation of a persisted message
 * changes its fingerprint, which the store detects on the next save and
 * answers with a fold (full snapshot rewrite). Unknown future mutation paths
 * are covered by construction, not by enumeration.
 *
 * <p><b>Cost</b>: fingerprinting is O(message size) CPU (serialization-grade).
 * The append fast-path recomputes fingerprints for the whole persisted prefix
 * on every save — deliberately traded I/O savings (the point of WI14) for
 * deterministic mutation detection. FNV-1a 64-bit: collision odds are
 * negligible for non-adversarial in-process mutation, and the replay-side
 * {@code totalMessageCount} guard plus the next fold bound any residual.
 */
public final class ChatMessageFingerprints {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private ChatMessageFingerprints() {
    }

    /**
     * Fingerprint of the message's persisted form (serialized shape), FNV-1a
     * 64-bit. Never zero for real content in practice; {@code 0} is reserved
     * for {@code null} messages.
     *
     * @param message the message to fingerprint; may be null (→ 0)
     * @return the 64-bit fingerprint of its serialized form
     */
    public static long fingerprint(ChatMessage message) {
        if (message == null) {
            return 0L;
        }
        String json = JsonTool.stringify(message);
        long hash = FNV_OFFSET_BASIS;
        for (int i = 0; i < json.length(); i++) {
            hash ^= json.charAt(i);
            hash *= FNV_PRIME;
        }
        return hash;
    }
}
