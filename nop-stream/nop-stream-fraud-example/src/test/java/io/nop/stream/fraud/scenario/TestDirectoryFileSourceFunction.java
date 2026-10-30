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

    /** Multi-file fixture: writes each {@code name -> content} pair into a fresh sorted-name directory. */
    private Path writeInputParts(String name, String content, String... nameContentPairs) throws IOException {
        Path input = tempDir.resolve("input-" + System.nanoTime());
        Files.createDirectories(input);
        Files.write(input.resolve(name), content.getBytes());
        for (int i = 0; i + 1 < nameContentPairs.length; i += 2) {
            Files.write(input.resolve(nameContentPairs[i]), nameContentPairs[i + 1].getBytes());
        }
        return input;
    }

    private TaskStateSnapshot snapshotWith(Map<String, Long> cursors) {
        TaskStateSnapshot state = new TaskStateSnapshot(null, 1L);
        state.putOperatorState(DirectoryFileSourceFunction.CURSORS_KEY, new TreeMap<>(cursors));
        return state;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> cursorsOf(OperatorSnapshotResult snapshot) {
        return (Map<String, Long>) snapshot.getOperatorStates()
                .get(DirectoryFileSourceFunction.CURSORS_KEY);
    }

    /** Source whose run cancels itself after emitting {@code stopAfter} lines — once, on the first run only. */
    private DirectoryFileSourceFunction cancelAfterLines(Path input, int stopAfter) {
        return new DirectoryFileSourceFunction(input.toString()) {
            boolean armed = true;

            @Override
            public void run(SourceContext<String> ctx) throws Exception {
                super.run(new SourceContext<String>() {
                    int seen;

                    @Override
                    public void collect(String element) {
                        ctx.collect(element);
                        if (armed && ++seen == stopAfter) {
                            armed = false;
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
        // 确定性信号：等 first 真正进入读取阻塞点后再启动 second（子任务互斥语义）
        long deadline = System.currentTimeMillis() + 60_000;
        while (first.isAlive() && first.getState() != Thread.State.WAITING
                && first.getState() != Thread.State.TIMED_WAITING
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        secondReader.start();
        secondReader.join(3000);
        first.join(3000);
        assertTrue(firstCtx.get().collected.isEmpty(),
                "the second concurrent subtask must not re-emit the directory");
        assertFalse(Thread.currentThread().isInterrupted());
    }

    // ---- Plan 2512 C3-008 / C10-002: incremental (merge-max) restore ----

    /**
     * Red 2 / C10-002 closure gate: region-restart Phase-3 timing simulation with
     * the "completed file" variant. The shared instance already ran to completion
     * (both per-file cursors at end-of-file, all lines emitted); a LAGGING
     * checkpoint is then replayed into the same shared instance, as
     * SupervisionLoop.restartRegion does when the checkpoint predates sibling
     * progress. The old clear+putAll REPLACE wiped the advanced/completed-file
     * cursors (part-001 rewound to 0), so later checkpoints lagged emitted
     * progress and recovery replayed already-reflected windows. With merge-max
     * restore the snapshot positions must never regress and a re-run must not
     * re-emit anything.
     */
    @Test
    public void c3_008_restoreVsRunInterleaveDoesNotRewindAdvancedCursors() throws Exception {
        // part-000: 16 bytes, part-001: 27 bytes
        Path input = writeInputParts("part-000.txt", "a,1,100\nb,2,200\n",
                "part-001.txt", "c,3,300\nd,4,400\ne,5,500\n");
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString());

        // the run advanced (and completed) both files
        CollectingContext ctx = new CollectingContext();
        source.run(ctx);
        assertEquals(5, ctx.collected.size());
        Map<String, Long> advanced = cursorsOf(source.snapshotState(1L));
        Path p0 = input.resolve("part-000.txt");
        Path p1 = input.resolve("part-001.txt");
        assertEquals(Files.size(p0), advanced.get(p0.toString()), "file 0 fully consumed");
        assertEquals(Files.size(p1), advanced.get(p1.toString()), "file 1 fully consumed");

        // region restart Phase-3: a LAGGING checkpoint (only the first line of
        // part-000 was durable) is restored into the SAME shared instance
        Map<String, Long> lagging = new TreeMap<>();
        lagging.put(p0.toString(), 8L);
        source.initializeState(snapshotWith(lagging));

        // checkpoint positions must not regress behind emitted progress
        Map<String, Long> after = cursorsOf(source.snapshotState(2L));
        assertEquals(Files.size(p0), after.get(p0.toString()),
                "advanced cursor must survive a lagging restore (no rewind)");
        assertEquals(Files.size(p1), after.get(p1.toString()),
                "completed-file cursor must not be wiped by a lagging restore");

        // and a subsequent run of the same instance must not replay emitted lines
        CollectingContext rerun = new CollectingContext();
        source.run(rerun);
        assertEquals(List.of(), rerun.collected,
                "merge-max restore must not cause duplicate replay of emitted windows");
    }

    // ---- Plan 2512 C6-007 / C10-004: cancelled volatile reset on run entry ----

    /**
     * Red 4 / C10-004 closure gate (variant 1: cancel between runs): a region
     * restart reuses the same source-function instance that a failed deploy
     * attempt cancelled before any run of the new epoch, restoring the epoch
     * checkpoint (cursor after the first line) before the restart run. Phase-2
     * {@code waitForTerminal} guarantees teardown cancel happens-before the new
     * run. The old code never reset {@code cancelled}, so the restart run
     * silently emitted zero events and completed as a false EOS success despite
     * the restored checkpoint carrying an unemitted remainder. After the fix the
     * re-run must emit the remainder and the snapshot cursors must stay
     * monotonic. (Note: with the merge-max restore of C3-008, a lagging restore
     * can never rewind already-emitted progress — so a cancel AFTER a fully
     * consumed run re-runs legitimately at zero events; the defect shape needs a
     * checkpoint with an unemitted remainder, as here.)
     */
    @Test
    public void c6_007_cancelThenRerunReemitsAllWithMonotonicCursors() throws Exception {
        // "r1\nr2\nr3" is 8 bytes; cursor 3 = after the first line ("r1\n")
        Path input = writeInput("r1", "r2", "r3");
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString());

        source.cancel(); // teardown of a failed epoch: teardown always cancels
        // restart: restore the epoch checkpoint (first line already durable)
        // into the SAME shared instance before the restart run
        Map<String, Long> checkpoint = new TreeMap<>();
        checkpoint.put(input.resolve("part-000.txt").toString(), 3L);
        source.initializeState(snapshotWith(checkpoint));

        CollectingContext second = new CollectingContext();
        source.run(second);
        assertEquals(List.of("r2", "r3"), second.collected,
                "reused instance must not silently emit zero events after cancel (false EOS)");

        Map<String, Long> after = cursorsOf(source.snapshotState(2L));
        for (Map.Entry<String, Long> e : checkpoint.entrySet()) {
            assertTrue(after.get(e.getKey()) >= e.getValue(),
                    "snapshot cursors must stay monotonic across the restart re-run");
        }
    }

    /**
     * Red 4 / C10-004 closure gate (variant 2: cancel DURING the run — the form
     * that is red in the defect state per the plan): a run cancelled mid-stream
     * must be resumable on the same instance; the restart run emits the remaining
     * lines. The old code left {@code cancelled} set, so the restart exited at the
     * first loop check with zero events.
     */
    @Test
    public void c6_007_cancelDuringRunThenRerunEmitsRemaining() throws Exception {
        Path input = writeInput("l1", "l2", "l3", "l4", "l5");
        DirectoryFileSourceFunction partial = cancelAfterLines(input, 2);

        CollectingContext firstCtx = new CollectingContext();
        partial.run(firstCtx);
        assertEquals(List.of("l1", "l2"), firstCtx.collected);
        Map<String, Long> mid = cursorsOf(partial.snapshotState(1L));
        assertTrue(mid.values().iterator().next() > 0, "mid-run cancel must have checkpointed progress");

        // restart on the SAME instance: teardown-cancelled state must not starve it
        // (region restart restores the mid-run checkpoint before the restart run)
        partial.initializeState(snapshotWith(mid));
        CollectingContext resumedCtx = new CollectingContext();
        partial.run(resumedCtx);
        assertEquals(List.of("l3", "l4", "l5"), resumedCtx.collected,
                "run cancelled mid-stream must resume remaining lines on the reused instance");
    }

    // ---- Plan 2512 C10-007 hitchhiker probes (design adjudication 5) ----

    /** C10-007 MultiFile probe: >= 3 files are streamed in stable sorted cross-file order. */
    @Test
    public void c10_007_multiFileCrossFileOrderProbe() throws Exception {
        Path input = writeInputParts("part-000.txt", "a1\na2\n",
                "part-001.txt", "b1\nb2\n", "part-002.txt", "c1\nc2\n");
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString());
        CollectingContext ctx = new CollectingContext();
        source.run(ctx);
        assertEquals(List.of("a1", "a2", "b1", "b2", "c1", "c2"), ctx.collected,
                "files must stream in sorted cross-file order, all lines of each file contiguous");
    }

    /**
     * C10-007 BufferBoundary probe: a file larger than the 8KB read buffer, with
     * line boundaries straddling block boundaries, resumes without loss or
     * duplication after a mid-run cancel + checkpoint restore.
     */
    @Test
    public void c10_007_blockBoundaryResumeProbe() throws Exception {
        // 400 lines x 46 bytes = 18400 bytes: three 8192-byte reads, line
        // terminators land inside and across the 8KB block boundaries
        StringBuilder content = new StringBuilder();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            String line = String.format("row-%03d-%037d", i, i);
            content.append(line).append('\n');
            expected.add(line);
        }
        assertTrue(content.length() > 8192 * 2, "fixture must exceed two read blocks");
        Path input = tempDir.resolve("input-" + System.nanoTime());
        Files.createDirectories(input);
        Files.write(input.resolve("part-000.txt"), content.toString().getBytes());

        DirectoryFileSourceFunction partial = cancelAfterLines(input, 150);
        CollectingContext firstCtx = new CollectingContext();
        partial.run(firstCtx);
        assertEquals(expected.subList(0, 150), firstCtx.collected);

        DirectoryFileSourceFunction resumed = new DirectoryFileSourceFunction(input.toString());
        resumed.initializeState(snapshotWith(cursorsOf(partial.snapshotState(1L))));
        CollectingContext resumedCtx = new CollectingContext();
        resumed.run(resumedCtx);

        List<String> all = new ArrayList<>(firstCtx.collected);
        all.addAll(resumedCtx.collected);
        assertEquals(expected, all, "resume across the 8KB block boundary must neither lose nor duplicate lines");
    }

    /** C10-007 empty-directory probe: a bounded source over an empty directory completes as EOS with zero events. */
    @Test
    public void c10_007_emptyDirectoryEosProbe() throws Exception {
        Path input = tempDir.resolve("empty-" + System.nanoTime());
        Files.createDirectories(input);
        DirectoryFileSourceFunction source = new DirectoryFileSourceFunction(input.toString());
        CollectingContext ctx = new CollectingContext();
        source.run(ctx); // must not throw
        assertEquals(List.of(), ctx.collected, "empty directory must yield a clean zero-event EOS");
        Map<String, Long> cursors = cursorsOf(source.snapshotState(1L));
        assertTrue(cursors.isEmpty(), "empty run must snapshot an empty cursor map");
    }
}
