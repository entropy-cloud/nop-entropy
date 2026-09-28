package io.nop.code.core.cluster;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestIndexShardPlanner {

    @Test
    void testDeterministicDisjointComplete() {
        List<String> paths = List.of(
                "src/app/A.java", "src/app/B.java", "src/app/sub/C.java",
                "src/lib/D.java", "src/lib/E.java", "README.md");

        List<IndexShard> first = IndexShardPlanner.plan(paths, 3);
        List<IndexShard> second = IndexShardPlanner.plan(paths, 3);

        // determinism: same input → identical shard assignment
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getFilePaths(), second.get(i).getFilePaths(),
                    "shard " + i + " must be deterministic");
        }

        // disjoint + complete
        Set<String> union = new HashSet<>();
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (IndexShard shard : first) {
            for (String path : shard.getFilePaths()) {
                assertTrue(seen.add(path), "path must appear in exactly one shard: " + path);
                union.add(path);
                total++;
            }
        }
        assertEquals(paths.size(), total, "every input path must be assigned exactly once");
        assertEquals(new HashSet<>(paths), union);

        // in-shard order preserves input order
        for (IndexShard shard : first) {
            List<String> inInputOrder = new ArrayList<>(paths);
            inInputOrder.retainAll(shard.getFilePaths());
            assertEquals(inInputOrder, shard.getFilePaths(), "shard must preserve input order");
        }
    }

    @Test
    void testEmptyInputAndExplicitFailures() {
        assertTrue(IndexShardPlanner.plan(List.of(), 4).isEmpty()
                        || IndexShardPlanner.plan(List.of(), 4).stream().allMatch(s -> s.getFilePaths().isEmpty()),
                "empty input yields empty/void shards");
        assertThrows(IllegalArgumentException.class, () -> IndexShardPlanner.plan(List.of("a"), 0));
        assertThrows(IllegalArgumentException.class, () -> IndexShardPlanner.plan(List.of("a"), -1));
        assertThrows(IllegalArgumentException.class, () -> IndexShardPlanner.plan(java.util.Arrays.asList("a", null), 2));
    }
}
