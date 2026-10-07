/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI4 (plan 2329): {@link FileSource#sample(int)} reads real lines read-only — no
 * split assignment, no cursor state (the sampling path never touches the enumerator /
 * reader lifecycle), limit is the global cap in discovery order.
 */
public class TestFileSourceSample {

    @TempDir
    Path dir;

    @Test
    public void sampleReadsRealLinesUpToLimitAcrossFiles() throws Exception {
        Files.write(dir.resolve("a.txt"), List.of("a1", "a2", "a3"));
        Files.write(dir.resolve("b.txt"), List.of("b1", "b2", "b3"));

        FileSource source = new FileSource(dir.toString());
        List<Object> all = source.sample(6);
        // No cross-file ordering promise (Files.walk discovery order) — assert the set.
        assertEquals(6, all.size());
        assertEquals(java.util.Set.of("a1", "a2", "a3", "b1", "b2", "b3"),
                java.util.Set.copyOf(all), () -> "real lines from both files: " + all);

        List<Object> capped = new FileSource(dir.toString()).sample(2);
        assertEquals(2, capped.size());
        assertTrue(java.util.Set.copyOf(capped).stream()
                        .allMatch(r -> java.util.Set.of("a1", "a2", "a3", "b1", "b2", "b3").contains(r)),
                () -> "capped sample must be a subset of the real lines: " + capped);
    }

    @Test
    public void sampleLimitAboveAvailableDataReturnsAllLines() throws Exception {
        Files.write(dir.resolve("only.txt"), List.of("x1", "x2"));
        List<Object> records = new FileSource(dir.toString()).sample(100);
        assertEquals(2, records.size());
    }

    @Test
    public void sampleRejectsNonPositiveLimitTyped() {
        assertThrows(Exception.class, () -> new FileSource(dir.toString()).sample(0));
        assertThrows(Exception.class, () -> new FileSource(dir.toString()).sample(-1));
    }

    @Test
    public void sampleFailsTypedOnMissingDirectory() {
        assertThrows(Exception.class,
                () -> new FileSource(dir.resolve("missing").toString()).sample(5));
    }

    @Test
    public void sampleEnforcesLineByteBoundLikeTheReader() throws Exception {
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < FileSourceReader.DEFAULT_MAX_LINE_BYTES; i++) {
            huge.append('x');
        }
        Files.write(dir.resolve("huge.txt"), List.of("tiny", huge.toString()));
        Exception e = assertThrows(Exception.class,
                // explicit small bound: the second line is way over it
                () -> new FileSource(dir.toString(), 8).sample(5));
        assertTrue(String.valueOf(e).contains("maxLineBytes"),
                "oversized line must fail with the reader-bound message: " + e);
    }
}
