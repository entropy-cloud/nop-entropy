/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.core.checkpoint;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D1b (AR-1, Plan 2026-09-04-1326-1 Phase 1): job-name → storage-jobId mapping rules.
 */
public class TestStorageJobIds {

    @Test
    public void safeNamePassesThroughUnchanged() {
        assertEquals("fraud-s1-recovery", StorageJobIds.sanitizeJobId("fraud-s1-recovery"));
        assertEquals("job_2026", StorageJobIds.sanitizeJobId("job_2026"));
    }

    @Test
    public void nullAndBlankReturnNull() {
        assertNull(StorageJobIds.sanitizeJobId(null));
        assertNull(StorageJobIds.sanitizeJobId(""));
        assertNull(StorageJobIds.sanitizeJobId("   "));
    }

    @Test
    public void unsafeCharactersAreReplacedAndHashSuffixed() {
        // "Streaming Job" (env.execute() no-arg default) contains a space
        String sanitized = StorageJobIds.sanitizeJobId("Streaming Job");
        assertTrue(sanitized.matches("[a-zA-Z0-9_-]+"),
                "must match LocalFileCheckpointStorage SAFE_ID_PATTERN: " + sanitized);
        assertTrue(sanitized.startsWith("Streaming_Job-"), "replacement + hash suffix: " + sanitized);
    }

    @Test
    public void cjkNameIsStorageSafe() {
        String sanitized = StorageJobIds.sanitizeJobId("订单-监控 作业");
        assertTrue(sanitized.matches("[a-zA-Z0-9_-]+"), "CJK must map to safe chars: " + sanitized);
    }

    @Test
    public void distinctUnsafeNamesNeverCollide() {
        // two different names whose underscore patterns coincide must differ by hash
        String a = StorageJobIds.sanitizeJobId("订单一");
        String b = StorageJobIds.sanitizeJobId("订单二");
        assertNotEquals(a, b, "injectivity: distinct names must not share one storage namespace");
    }

    @Test
    public void deterministicAcrossCalls() {
        // the same name maps to the same id across runs — legal same-job recovery preserved
        assertEquals(StorageJobIds.sanitizeJobId("Streaming Job"), StorageJobIds.sanitizeJobId("Streaming Job"));
        assertEquals(StorageJobIds.sanitizeJobId("订单-监控 作业"), StorageJobIds.sanitizeJobId("订单-监控 作业"));
    }

    @Test
    public void largeNamePopulationIsInjectiveOnSafePattern() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String name = "job " + i + " ./#" + (i % 7);
            String id = StorageJobIds.sanitizeJobId(name);
            assertTrue(seen.add(id), "collision on: " + name + " -> " + id);
        }
    }
}
