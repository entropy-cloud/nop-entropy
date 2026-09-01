/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.fraud.scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import io.nop.stream.core.checkpoint.OperatorSnapshotResult;
import io.nop.stream.core.checkpoint.TaskStateSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for the S2 bounded file source adapter: byte-accurate cursors,
 * checkpoint/restore resume semantics, empty-map restore guard (rescale) and
 * missing-directory fail-fast.
 */
public class TestDirectoryFileSourceFunction {

    @TempDir
    Path tempDir;

    private static final class CollectingContext implements io.nop.stream.core.common.functions.source.SourceFunction.SourceContext<String> {
        final List<String> collected = new ArrayList<>();

        @Override
        public void collect(String element) {
            collected.add(element);
        }

        @Override
        public void collectWithTimestamp(String element, long timestamp) {
            collected.add(element);
        }

        @Override
        public void emitWatermark(long mark) {
        }

        @Override
        public void markAsTemporarilyIdle() {
        }

        @Override
        public long getProcessingTime() {
            return System.currentTimeMillis();
        }
    }

    private Path writeInput(String... lines) throws IOException {
        Path input = tempDir.resolve("input-" + System.nanoTime());
        Files.createDirectories(input);
        Files.write(input.resolve("part-000.txt"), String.join("\n", lines).getBytes());
        return input;
    }

    @Test
    public void emitsAllLinesInOrder() throws Exception {
        Path input = writeInput("a,1,100", "b,2,200", "c,3,300");
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString());
        CollectingContext ctx = new CollectingContext();
        source.run(ctx);
        assertEquals(List.of("a,1,100", "b,2,200", "c,3,300"), ctx.collected);
    }

    @Test
    public void handlesFinalLineWithoutTerminator() throws Exception {
        Path input = writeInput("a,1,100", "b,2,200");
        // rewrite without trailing newline already ensured by String.join
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString());
        CollectingContext ctx = new CollectingContext();
        source.run(ctx);
        assertEquals(2, ctx.collected.size());
        assertEquals("b,2,200", ctx.collected.get(1));
    }

    @Test
    public void cursorCheckpointResumeSkipsConsumedPrefix() throws Exception {
        Path input = writeInput("l1", "l2", "l3", "l4", "l5");
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString());

        // consume two lines
        CollectingContext first = new CollectingContext();
        source.cancel(); // consume exactly two lines via a bounded probe below
        // probe: run on a fresh source but stop after 2 lines via cancel hook is
        // cumbersome; instead verify the cursor math via snapshot after partial
        // read using the emit callback
        DirectoryFileSourceFunction partial = new DirectoryFileSourceFunction(input.toString()) {
            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                // consume only the first two lines by cancelling from the context
                super.run(new SourceContext<String>() {
                    int seen;

                    @Override
                    public void collect(String element) {
                        ctx.collect(element);
                        if (++seen == 2) {
                            cancel();
                        }
                    }

                    @Override
                    public void collectWithTimestamp(String element, long timestamp) {
                        collect(element);
                    }

                    @Override
                    public void emitWatermark(long mark) {
                    }

                    @Override
                    public void markAsTemporarilyIdle() {
                    }

                    @Override
                    public long getProcessingTime() {
                        return System.currentTimeMillis();
                    }
                });
            }
        };
        CollectingContext partialCtx = new CollectingContext();
        partial.run(partialCtx);
        assertEquals(List.of("l1", "l2"), partialCtx.collected);

        OperatorSnapshotResult snapshot = partial.snapshotState(1L);
        @SuppressWarnings("unchecked")
        Map<String, Long> cursors = (Map<String, Long>) snapshot.getOperatorStates()
                .get(DirectoryFileSourceFunction.CURSORS_KEY);
        assertTrue(cursors.size() == 1);
        long midCursor = cursors.values().iterator().next();
        assertTrue(midCursor > 0 && midCursor < Files.size(input.resolve("part-000.txt")),
                "cursor must be mid-file after consuming 2 of 5 lines");

        // restore into a fresh source: must resume AFTER the two consumed lines
        DirectoryFileSourceFunction resumed = new DirectoryFileSourceFunction(input.toString());
        TaskStateSnapshot state = new TaskStateSnapshot(null, 1L);
        state.putOperatorState(DirectoryFileSourceFunction.CURSORS_KEY, new TreeMap<>(cursors));
        resumed.initializeState(state);
        CollectingContext resumedCtx = new CollectingContext();
        resumed.run(resumedCtx);
        assertEquals(List.of("l3", "l4", "l5"), resumedCtx.collected,
                "restore must resume from the checkpointed byte cursor, not re-read");
    }

    @Test
    public void emptyRestoredMapMustNotWipeSiblingRestoredCursors() {
        // rescale guard: a scale-up subtask restores an EMPTY cursor map; it must
        // not clear cursors another subtask already restored into the shared map
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction("/whatever");
        TaskStateSnapshot seeded = new TaskStateSnapshot(null, 1L);
        TreeMap<String, Long> cursors = new TreeMap<>();
        cursors.put("/data/part-000.txt", 42L);
        seeded.putOperatorState(DirectoryFileSourceFunction.CURSORS_KEY, cursors);
        source.initializeState(seeded);

        TaskStateSnapshot empty = new TaskStateSnapshot(null, 1L);
        empty.putOperatorState(DirectoryFileSourceFunction.CURSORS_KEY, new TreeMap<String, Long>());
        source.initializeState(empty);

        OperatorSnapshotResult snapshot = source.snapshotState(2L);
        @SuppressWarnings("unchecked")
        Map<String, Long> after = (Map<String, Long>) snapshot.getOperatorStates()
                .get(DirectoryFileSourceFunction.CURSORS_KEY);
        assertEquals(42L, after.get("/data/part-000.txt"),
                "empty restored map must not wipe the previously restored cursors");
    }

    @Test
    public void missingDirectoryFailsFast() {
        DirectoryFileSourceFunction source =
                new DirectoryFileSourceFunction(tempDir.resolve("no-such-dir").toString());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> source.run(new CollectingContext()));
        assertTrue(ex.getMessage().contains("does not exist"));
    }

    @Test
    public void declaresReplayableConsistency() {
        DirectoryFileSourceFunction source =
                new DirectoryFileSourceFunction(tempDir.toString());
        assertEquals(io.nop.stream.core.common.functions.source.SourceConsistencyCapability.REPLAYABLE,
                source.getSourceConsistency());
    }

    @Test
    public void malformedCursorStateFailsFast() {
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(tempDir.toString());
        TaskStateSnapshot bad = new TaskStateSnapshot(null, 1L);
        bad.putOperatorState(DirectoryFileSourceFunction.CURSORS_KEY, "not-a-map");
        assertThrows(io.nop.stream.core.exceptions.StreamException.class,
                () -> source.initializeState(bad));
    }

    @Test
    public void runEnteredGuardPreventsDoubleEmission() throws Exception {
        Path input = writeInput("x,1,1", "y,2,2");
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString(), 50L, 0L);
        AtomicReference<CollectingContext> firstCtx = new AtomicReference<>(new CollectingContext());
        Thread secondReader = new Thread(() -> {
            CollectingContext ctx = new CollectingContext();
            firstCtx.set(ctx);
            try {
                source.run(ctx);
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamException("second subtask run failed", e);
            }
        });
        // occupy the reader slot, then run a second "subtask": it must emit nothing
        Thread first = new Thread(() -> {
            try {
                source.run(new CollectingContext());
            } catch (Exception e) {
                throw new io.nop.stream.core.exceptions.StreamException("first subtask run failed", e);
            }
        });
        first.start();
        Thread.sleep(50);
        secondReader.start();
        secondReader.join(3000);
        first.join(3000);
        assertTrue(firstCtx.get().collected.isEmpty(),
                "the second concurrent subtask must not re-emit the directory");
        assertFalse(Thread.currentThread().isInterrupted());
    }
}
