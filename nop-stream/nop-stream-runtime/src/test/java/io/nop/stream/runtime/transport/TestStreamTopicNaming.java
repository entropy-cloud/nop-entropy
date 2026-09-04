/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AR-12 (plan 2026-09-04-1326-3): {@link StreamTopicNaming#buildTopic} sanitization
 * unit proof. Kafka topics only allow {@code [a-zA-Z0-9._-]} and ≤249 chars; edge ids
 * are {@code source->target} (the {@code >} is illegal). Every topic produced must be
 * transport-legal, deterministic, disambiguable, and IDENTICAL to the legacy format
 * for already-legal inputs.
 */
class TestStreamTopicNaming {

    private static final String LEGAL = "^[a-zA-Z0-9._-]{1,249}$";

    @Test
    void legalInputsKeepLegacyFormatVerbatim() {
        // Identity requirement: already-legal segments produce the exact legacy string —
        // existing literal-topic tests and deployments keep working.
        assertEquals("nop-stream.job-1.edge-1.0.1", StreamTopicNaming.buildTopic("job-1", "edge-1", 0, 1));
        assertEquals("nop-stream.my_job.a-b.3.12", StreamTopicNaming.buildTopic("my_job", "a-b", 3, 12));
    }

    @Test
    void illegalInputsAlwaysProduceKafkaLegalTopics() {
        String[][] cases = {
                {"job-1", "src->tgt"},                    // '>' from the edge-key form
                {"作业一", "edge-1"},                       // CJK jobId
                {"job with spaces", "edge 1"},            // spaces
                {"job:1", "edge:1"},                      // ':' (Kafka namespace separator)
                {"job/1", "edge\\1"},                     // path separators
                {"", null},                               // empty / null segments
                {"a" + "b".repeat(400), "edge-1"},        // over-long jobId (length cap)
                {"a" + "b".repeat(400) + "c", "edge-1"},  // a DIFFERENT over-long jobId
        };
        for (String[] c : cases) {
            String topic = StreamTopicNaming.buildTopic(c[0], c[1], 0, 0);
            assertTrue(topic.matches(LEGAL),
                    "topic for jobId='" + c[0] + "' edgeId='" + c[1] + "' must be transport-legal: " + topic);
            assertTrue(topic.startsWith(StreamTopicNaming.TOPIC_PREFIX + "."));
        }
    }

    @Test
    void sanitizationIsDeterministic() {
        // Same input → same output across calls (producers and consumers in different
        // JVMs converge on the identical topic).
        String t1 = StreamTopicNaming.buildTopic("job-1", "src->tgt", 0, 1);
        String t2 = StreamTopicNaming.buildTopic("job-1", "src->tgt", 0, 1);
        assertEquals(t1, t2);
        String cjk1 = StreamTopicNaming.buildTopic("作业一", "A->B", 1, 0);
        String cjk2 = StreamTopicNaming.buildTopic("作业一", "A->B", 1, 0);
        assertEquals(cjk1, cjk2);
    }

    @Test
    void sanitizedCollisionsStayDisambiguable() {
        // "a>b" and "a<b" both map to "a-b" after character replacement — the hash of
        // the ORIGINAL segment keeps the two channels distinguishable.
        String t1 = StreamTopicNaming.buildTopic("job", "a>b", 0, 0);
        String t2 = StreamTopicNaming.buildTopic("job", "a<b", 0, 0);
        assertTrue(t1.matches(LEGAL));
        assertTrue(t2.matches(LEGAL));
        assertNotEquals(t1, t2, "different original edge ids must not collide after sanitization");

        // An already-legal segment that equals another segment's REPLACED form is still
        // distinct from the sanitized product (which carries the hash suffix).
        String legal = StreamTopicNaming.buildTopic("job", "a-b", 0, 0);
        assertNotEquals(legal, t1);
        assertNotEquals(legal, t2);

        // Two distinct over-long jobIds truncated to the same head stay distinguishable
        // via the appended hash of the full candidate.
        String long1 = StreamTopicNaming.buildTopic("a" + "b".repeat(400), "edge-1", 0, 0);
        String long2 = StreamTopicNaming.buildTopic("a" + "b".repeat(400) + "c", "edge-1", 0, 0);
        assertTrue(long1.length() <= 249);
        assertTrue(long2.length() <= 249);
        assertNotEquals(long1, long2, "over-long inputs must not merge into one topic after truncation");
    }

    @Test
    void truncatedTopicsStayWithinKafkaLengthLimit() {
        String hugeJob = "j" + "x".repeat(500);
        String topic = StreamTopicNaming.buildTopic(hugeJob, "e" + "y".repeat(500), 12345, 67890);
        assertTrue(topic.length() <= 249, "topic length " + topic.length() + " must be capped at 249");
        assertTrue(topic.matches(LEGAL));
        assertNotNull(topic);
    }
}
