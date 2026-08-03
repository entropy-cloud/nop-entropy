/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.connector.file;

import io.nop.stream.core.source.SimpleVersionedSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Stage 49 Phase 3: verifies that the FLIP-27 source enumerator state survives a
 * checkpoint/restore cycle (manifest section round-trip), proving the D2 coordinator-state
 * checkpoint mechanism end-to-end at the contract layer.
 *
 * <p>Full env.execute() checkpoint/restore E2E is exercised in TestFileSourceE2E; this
 * test focuses on the state-serialization round-trip through the manifest.
 */
class TestFileSourceCheckpointRestore {

    @TempDir
    Path tempDir;

    @Test
    void enumeratorStateSurvivesCheckpointRestoreRoundTrip() throws Exception {
        Path dir = tempDir.resolve("ckpt");
        Files.createDirectories(dir);
        Files.write(dir.resolve("a.txt"), Collections.singletonList("lineA"));
        Files.write(dir.resolve("b.txt"), Collections.singletonList("lineB"));

        // Initial enumerator discovers files, marks one as finished (simulating read progress)
        io.nop.stream.core.source.SplitEnumeratorContext<FileSplit> ctx1 =
                new io.nop.stream.core.source.SplitEnumeratorContext<>(1, null);
        FileSplitEnumerator orig = new FileSplitEnumerator(dir.toString());
        orig.start(ctx1);
        orig.markSplitFinished(dir.resolve("a.txt").toString());

        FileSplitEnumeratorState state1 = orig.snapshotState(1L);
        assertEquals(2, state1.getDiscoveredFiles().size());
        assertEquals(1, state1.getFinishedFiles().size());

        // Serialize via the source's serializer (this is what LocalSourceCoordinator does)
        FileSource source = new FileSource(dir.toString());
        SimpleVersionedSerializer<FileSplitEnumeratorState> ser = source.getEnumeratorStateSerializer();
        byte[] bytes = ser.serialize(state1);
        assertNotNull(bytes);

        // Deserialize and restore into a fresh enumerator (simulating recovery)
        FileSplitEnumeratorState restored = ser.deserialize(ser.getVersion(), bytes);
        FileSplitEnumerator recovered = new FileSplitEnumerator(dir.toString());
        recovered.restoreState(restored);

        FileSplitEnumeratorState state2 = recovered.snapshotState(2L);
        assertEquals(state1.getDiscoveredFiles(), state2.getDiscoveredFiles(),
                "discovered files must round-trip exactly");
        assertEquals(state1.getFinishedFiles(), state2.getFinishedFiles(),
                "finished files must round-trip exactly (recovered enumerator must NOT re-read finished splits)");
        assertEquals(state1.getAssignedFiles(), state2.getAssignedFiles(),
                "assigned files must round-trip exactly");
    }

    @Test
    void readerSplitCursorSurvivesRoundTrip() throws Exception {
        Path dir = tempDir.resolve("reader-ckpt");
        Files.createDirectories(dir);
        Files.write(dir.resolve("seq.txt"), Arrays.asList("a", "b", "c", "d"));

        // Snapshot a partially-consumed split
        FileSplit partial = new FileSplit(dir.resolve("seq.txt").toString(), 0L,
                Files.size(dir.resolve("seq.txt")), 4L /* cursor advanced */);

        FileSource source = new FileSource(dir.toString());
        SimpleVersionedSerializer<FileSplit> splitSer = source.getSplitSerializer();
        byte[] bytes = splitSer.serialize(partial);
        FileSplit restored = splitSer.deserialize(splitSer.getVersion(), bytes);

        assertEquals(partial.getFilePath(), restored.getFilePath());
        assertEquals(partial.getStartOffset(), restored.getStartOffset());
        assertEquals(partial.getEndOffset(), restored.getEndOffset());
        assertEquals(partial.getCurrentOffset(), restored.getCurrentOffset(),
                "cursor (currentOffset) must round-trip exactly — reader must resume from here");
    }

    @Test
    void sourceEnumeratorSnapshotSectionMatchesManifestContract() throws Exception {
        Path dir = tempDir.resolve("manifest");
        Files.createDirectories(dir);
        Files.write(dir.resolve("x.txt"), Collections.singletonList("hello"));

        FileSource source = new FileSource(dir.toString());
        FileSplitEnumerator enumerator = new FileSplitEnumerator(dir.toString());
        io.nop.stream.core.source.SplitEnumeratorContext<FileSplit> ctx =
                new io.nop.stream.core.source.SplitEnumeratorContext<>(1, null);
        enumerator.start(ctx);
        FileSplitEnumeratorState state = enumerator.snapshotState(7L);

        SimpleVersionedSerializer<FileSplitEnumeratorState> ser = source.getEnumeratorStateSerializer();
        byte[] bytes = ser.serialize(state);

        // Construct the manifest-section entry that CheckpointCoordinator writes.
        io.nop.stream.core.checkpoint.SourceEnumeratorSnapshot manifestEntry =
                new io.nop.stream.core.checkpoint.SourceEnumeratorSnapshot(ser.getVersion(), bytes);

        // Round-trip back through manifest entry — simulates the D2 manifest path.
        FileSplitEnumeratorState recovered = ser.deserialize(
                manifestEntry.getVersion(), manifestEntry.getStateBytes());
        assertNotNull(recovered);
        assertEquals(1, recovered.getDiscoveredFiles().size());
        assertEquals(dir.resolve("x.txt").toString(), recovered.getDiscoveredFiles().iterator().next());
    }
}
