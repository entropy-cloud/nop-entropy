/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.source;

import io.nop.stream.core.checkpoint.SourceEnumeratorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import io.nop.stream.core.exceptions.StreamException;

class TestSourceEnumerator {

    private SourceEnumerator enumerator;

    @BeforeEach
    void setup() {
        enumerator = new SourceEnumerator(4);
    }

    @Test
    void testDiscoverSplits() {
        List<SourceSplit> splits = Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1"),
                new SourceSplit("split-2"),
                new SourceSplit("split-3")
        );

        enumerator.discoverSplits(splits);

        assertEquals(4, enumerator.getDiscoveredSplits().size());
        assertEquals(4, enumerator.getUnassignedSplitCount());
        assertEquals(0, enumerator.getAssignedSplitCount());
    }

    @Test
    void testDiscoverSplitsIgnoresDuplicates() {
        List<SourceSplit> batch1 = Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1")
        );
        List<SourceSplit> batch2 = Arrays.asList(
                new SourceSplit("split-1"),
                new SourceSplit("split-2")
        );

        enumerator.discoverSplits(batch1);
        enumerator.discoverSplits(batch2);

        assertEquals(3, enumerator.getDiscoveredSplits().size());
        assertEquals(3, enumerator.getUnassignedSplitCount());
    }

    @Test
    void testAssignAllSplitsRoundRobin() {
        List<SourceSplit> splits = Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1"),
                new SourceSplit("split-2"),
                new SourceSplit("split-3"),
                new SourceSplit("split-4"),
                new SourceSplit("split-5")
        );

        enumerator.discoverSplits(splits);
        Map<Integer, List<String>> assignment = enumerator.assignAllSplits();

        // 6 splits across 4 subtasks: [0,1,2,3,0,1]
        assertEquals(2, assignment.get(0).size());
        assertEquals(2, assignment.get(1).size());
        assertEquals(1, assignment.get(2).size());
        assertEquals(1, assignment.get(3).size());

        assertTrue(assignment.get(0).contains("split-0"));
        assertTrue(assignment.get(0).contains("split-4"));
        assertTrue(assignment.get(1).contains("split-1"));
        assertTrue(assignment.get(1).contains("split-5"));
        assertTrue(assignment.get(2).contains("split-2"));
        assertTrue(assignment.get(3).contains("split-3"));

        assertEquals(0, enumerator.getUnassignedSplitCount());
        assertEquals(6, enumerator.getAssignedSplitCount());
    }

    @Test
    void testAssignSplitsToSpecificSubtask() {
        List<SourceSplit> splits = Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1"),
                new SourceSplit("split-2"),
                new SourceSplit("split-3")
        );

        enumerator.discoverSplits(splits);

        // First call assigns split-0 to subtask 0
        List<String> assigned0 = enumerator.assignSplits(0);
        assertEquals(1, assigned0.size());
        assertEquals("split-0", assigned0.get(0));

        // Next call assigns split-1 to subtask 1
        List<String> assigned1 = enumerator.assignSplits(1);
        assertEquals(1, assigned1.size());
        assertEquals("split-1", assigned1.get(0));

        // Next call assigns split-2 to subtask 2
        List<String> assigned2 = enumerator.assignSplits(2);
        assertEquals(1, assigned2.size());
        assertEquals("split-2", assigned2.get(0));
    }

    @Test
    void testMarkSplitFinished() {
        List<SourceSplit> splits = Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1")
        );

        enumerator.discoverSplits(splits);
        Map<Integer, List<String>> assignment = enumerator.assignAllSplits();

        String split0 = assignment.get(0).get(0);
        enumerator.acknowledgeSplit(split0);
        enumerator.markSplitFinished(split0);

        assertTrue(enumerator.getFinishedSplits().contains(split0));
        assertEquals(1, enumerator.getFinishedSplitCount());
        assertEquals(0, enumerator.getAssignedSubtask(split0));
        assertFalse(enumerator.allSplitsFinished());
    }

    @Test
    void testAllSplitsFinished() {
        List<SourceSplit> splits = Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1")
        );

        enumerator.discoverSplits(splits);
        Map<Integer, List<String>> assignment = enumerator.assignAllSplits();

        for (List<String> assigned : assignment.values()) {
            for (String splitId : assigned) {
                enumerator.acknowledgeSplit(splitId);
                enumerator.markSplitFinished(splitId);
            }
        }

        assertTrue(enumerator.allSplitsFinished());
        assertEquals(2, enumerator.getFinishedSplitCount());
    }

    @Test
    void testSnapshotAndRestore() {
        List<SourceSplit> splits = Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1"),
                new SourceSplit("split-2"),
                new SourceSplit("split-3")
        );

        enumerator.discoverSplits(splits);
        enumerator.assignAllSplits();

        // Finish one split
        enumerator.acknowledgeSplit("split-0");
        enumerator.markSplitFinished("split-0");

        // Snapshot state
        SourceEnumeratorState state = enumerator.snapshotState();
        assertNotNull(state);
        assertEquals(4, state.getDiscoveredSplits().size());
        assertEquals(1, state.getFinishedSplits().size());
        assertTrue(state.getFinishedSplits().contains("split-0"));

        // Restore into a new enumerator
        SourceEnumerator restored = new SourceEnumerator(4);
        restored.restoreState(state);

        assertEquals(4, restored.getDiscoveredSplits().size());
        assertEquals(0, restored.getUnassignedSplitCount()); // All were assigned
        assertEquals(4, restored.getAssignedSplitCount()); // 4 assigned (finished splits are still tracked in assignedSplits)
        assertEquals(1, restored.getFinishedSplitCount()); // 1 finished split in snapshot
        assertTrue(restored.getFinishedSplits().contains("split-0"));
    }

    @Test
    void testRestoreFromEmptyState() {
        SourceEnumerator empty = new SourceEnumerator(2);
        empty.restoreState(null);
        assertEquals(0, empty.getDiscoveredSplits().size());

        empty.restoreState(new SourceEnumeratorState());
        assertEquals(0, empty.getDiscoveredSplits().size());
    }

    @Test
    void testIncrementalDiscovery() {
        // First batch
        enumerator.discoverSplits(Arrays.asList(
                new SourceSplit("split-0"),
                new SourceSplit("split-1")
        ));
        enumerator.assignAllSplits();

        // Second batch discovered later
        enumerator.discoverSplits(Arrays.asList(
                new SourceSplit("split-2"),
                new SourceSplit("split-3")
        ));

        assertEquals(4, enumerator.getDiscoveredSplits().size());
        assertEquals(2, enumerator.getUnassignedSplitCount());

        Map<Integer, List<String>> secondAssignment = enumerator.assignAllSplits();
        assertEquals(1, secondAssignment.get(2).size());
        assertEquals(1, secondAssignment.get(3).size());
    }

    @Test
    void testDiscoveryCursor() {
        enumerator.setDiscoveryCursor("cursor-42");
        SourceEnumeratorState state = enumerator.snapshotState();
        assertEquals("cursor-42", state.getDiscoveryCursor());

        SourceEnumerator restored = new SourceEnumerator(2);
        restored.restoreState(state);
        assertEquals("cursor-42", restored.getDiscoveryCursor());
    }

    @Test
    void testInvalidParallelism() {
        assertThrows(StreamException.class, () -> new SourceEnumerator(0));
        assertThrows(StreamException.class, () -> new SourceEnumerator(-1));
    }

    @Test
    void testInvalidSubtaskIndex() {
        enumerator.discoverSplits(Arrays.asList(new SourceSplit("split-0")));
        assertThrows(StreamException.class, () -> enumerator.assignSplits(-1));
        assertThrows(StreamException.class, () -> enumerator.assignSplits(4));
    }
}
