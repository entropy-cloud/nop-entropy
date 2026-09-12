/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_STATE;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

/**
 * Provides topic naming convention for cross-TaskManager data exchange.
 *
 * <p>Format: {@code nop-stream.{jobId}.{edgeId}.{sourceSubtask}.{targetSubtask}}
 *
 * <p><strong>AR-12 (plan 2026-09-04-1326-3): transport-name sanitization.</strong>
 * Kafka topic names only allow {@code [a-zA-Z0-9._-]} and at most 249 characters, and
 * edge ids are built as {@code source->target} (the {@code >} is illegal in a topic
 * name). {@link #buildTopic} therefore sanitizes every segment at this single choke
 * point (producers and consumers converge on the same deterministic output):
 *
 * <ul>
 *   <li><b>Identity for legal input</b>: when every segment already matches
 *       {@code [a-zA-Z0-9._-]+} and the assembled name fits the length limit, the
 *       output is byte-for-byte the legacy format (existing literal-topic tests and
 *       deployments keep working).</li>
 *   <li><b>Illegal-character mapping + hash disambiguation</b>: each segment that
 *       contains illegal characters has them mapped to {@code -} and receives a
 *       deterministic {@code .h<8-hex>} suffix (SHA-256 of the ORIGINAL segment) —
 *       two different original inputs that collide after the character mapping stay
 *       distinguishable, and the same input always yields the same topic.</li>
 *   <li><b>Length cap</b>: if the assembled candidate exceeds 249 characters it is
 *       deterministically truncated and suffixed with {@code .<8-hex>} of the full
 *       candidate, so truncation never silently merges two distinct channels.</li>
 * </ul>
 *
 * <p>Note: the <em>edge config map key</em> ({@code "A->B"} form,
 * {@code DeploymentPlan.getEdgeConfigs()}) is NOT a topic and keeps its raw form —
 * only the topic produced from it is sanitized here.
 */
public final class StreamTopicNaming {

    public static final String TOPIC_PREFIX = "nop-stream";

    /**
     * Kafka's legal topic charset: {@code [a-zA-Z0-9._-]}, length 1..249
     * (Pulsar's free-form names accept anything legal for Kafka).
     */
    static final Pattern LEGAL_TOPIC_PATTERN = Pattern.compile("[a-zA-Z0-9._-]{1,249}");
    private static final Pattern LEGAL_SEGMENT_PATTERN = Pattern.compile("[a-zA-Z0-9._-]+");
    private static final int MAX_TOPIC_LENGTH = 249;

    private StreamTopicNaming() {
    }

    /**
     * Builds the topic name for a point-to-point data channel between two subtasks.
     *
     * @param jobId          the job identifier
     * @param edgeId         the edge identifier
     * @param sourceSubtask  the source subtask index
     * @param targetSubtask  the target subtask index
     * @return the sanitized, transport-legal topic name
     */
    public static String buildTopic(String jobId, String edgeId,
                                     int sourceSubtask, int targetSubtask) {
        String candidate = TOPIC_PREFIX + "." + sanitizeSegment(jobId) + "." + sanitizeSegment(edgeId)
                + "." + sourceSubtask + "." + targetSubtask;
        if (candidate.length() <= MAX_TOPIC_LENGTH) {
            return candidate;
        }
        // Deterministic truncation with hash disambiguation: distinct over-long inputs
        // must not silently merge into one topic.
        String hash = sha256Hex8(candidate);
        String head = candidate.substring(0, MAX_TOPIC_LENGTH - 9);
        while (head.endsWith(".")) {
            head = head.substring(0, head.length() - 1);
        }
        return head + "." + hash;
    }

    /**
     * Sanitizes one name segment. Legal segments pass through unchanged (identity —
     * the assembled topic stays byte-for-byte the legacy format); illegal ones get
     * illegal characters mapped to {@code -} plus a {@code .h<8-hex>} hash suffix of
     * the original value for deterministic disambiguation.
     */
    static String sanitizeSegment(String segment) {
        if (segment != null && LEGAL_SEGMENT_PATTERN.matcher(segment).matches()) {
            return segment;
        }
        String original = segment == null ? "" : segment;
        String replaced = original.replaceAll("[^a-zA-Z0-9._-]", "-");
        if (replaced.isEmpty()) {
            replaced = "x";
        }
        return replaced + ".h" + sha256Hex8(original);
    }

    private static String sha256Hex8(String value) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JVM spec — unreachable
            throw new StreamException(ERR_STREAM_INVALID_STATE, e).param(ARG_DETAIL, "SHA-256 unavailable");
        }
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%02x", hash[i]));
        }
        return sb.toString();
    }
}
