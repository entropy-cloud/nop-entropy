package io.nop.find.core.topk;

import io.nop.find.core.NopFindException;
import io.nop.find.core.model.FindMatch;
import io.nop.find.core.topk.TopKCollector.ScoredIndex;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TopKCollector} 二分插入 + 快速路径 + 全序确定性测试（plan 2379 Phase 1）。
 */
public class TopKCollectorTest {

    private static final Path ROOT = Paths.get("/root");

    private static String rel(int i) {
        return "file" + i + ".java";
    }

    private static Path abs(int i) {
        return ROOT.resolve(rel(i));
    }

    private static void fill(TopKCollector collector, int count) {
        for (int i = 0; i < count; i++) {
            collector.offer(1000 - i, i, rel(i), abs(i));
        }
    }

    @Test
    public void testKBoundaryRejectsExtraBeyondCapacity() {
        TopKCollector collector = new TopKCollector(3);
        fill(collector, 5);
        assertEquals(3, collector.getK());
        assertEquals(3, collector.size());
        // score 最小（最优）的三条：996/4、997/3、998/2
        assertEquals(List.of(new ScoredIndex(996, 4), new ScoredIndex(997, 3), new ScoredIndex(998, 2)),
                collector.toScoreIndexPairs());
    }

    @Test
    public void testUnderCapacityAccumulates() {
        TopKCollector collector = new TopKCollector(10);
        fill(collector, 4);
        assertEquals(4, collector.size());
        assertFalse(collector.isEmpty());
    }

    @Test
    public void testFastPathRejectsNonBetterCandidateWhenFull() {
        TopKCollector collector = new TopKCollector(2);
        fill(collector, 4); // top-2 = (999,1),(998,2)
        // score 更差 → 快速路径跳过
        assertFalse(collector.offer(5000, 0, "late.java", ROOT.resolve("late.java")));
        assertEquals(2, collector.size());
    }

    @Test
    public void testOfferReplacesWorstWhenBetter() {
        TopKCollector collector = new TopKCollector(2);
        fill(collector, 4);
        assertTrue(collector.offer(1, 99, "great.java", ROOT.resolve("great.java")));
        List<ScoredIndex> pairs = collector.toScoreIndexPairs();
        assertEquals(2, pairs.size());
        assertEquals(1, pairs.get(0).score());
        assertEquals(997, pairs.get(1).score());
    }

    @Test
    public void testEqualScoreTieBreakByIndex() {
        TopKCollector collector = new TopKCollector(2);
        collector.offer(50, 5, "b.java", abs(5));
        collector.offer(50, 2, "a.java", abs(2));
        assertEquals(List.of(new ScoredIndex(50, 2), new ScoredIndex(50, 5)), collector.toScoreIndexPairs());
    }

    @Test
    public void testInsertionOrderDoesNotAffectResult() {
        TopKCollector forward = new TopKCollector(5);
        fill(forward, 10);

        TopKCollector backward = new TopKCollector(5);
        for (int i = 9; i >= 0; i--) {
            backward.offer(1000 - i, i, rel(i), abs(i));
        }

        TopKCollector shuffled = new TopKCollector(5);
        int[] order = {7, 0, 3, 9, 1, 8, 2, 5, 4, 6};
        for (int i : order) {
            shuffled.offer(1000 - i, i, rel(i), abs(i));
        }

        assertEquals(forward.toScoreIndexPairs(), backward.toScoreIndexPairs());
        assertEquals(forward.toScoreIndexPairs(), shuffled.toScoreIndexPairs());
    }

    @Test
    public void testDeterministicAcrossRepeatedRuns() {
        TopKCollector a = new TopKCollector(7);
        TopKCollector b = new TopKCollector(7);
        fill(a, 50);
        fill(b, 50);
        assertEquals(a.toScoreIndexPairs(), b.toScoreIndexPairs());
        assertEquals(a.toMatches().toString(), b.toMatches().toString());
    }

    @Test
    public void testMergeFromProducesGlobalTopK() {
        TopKCollector t1 = new TopKCollector(3);
        TopKCollector t2 = new TopKCollector(3);
        // t1：score 高质量（值小）
        t1.offer(10, 0, rel(0), abs(0));
        t1.offer(30, 1, rel(1), abs(1));
        t1.offer(50, 2, rel(2), abs(2));
        // t2：全局最优
        t2.offer(1, 3, rel(3), abs(3));
        t2.offer(20, 4, rel(4), abs(4));
        t2.offer(40, 5, rel(5), abs(5));

        TopKCollector global = new TopKCollector(3);
        global.mergeFrom(t1);
        global.mergeFrom(t2);
        assertEquals(List.of(new ScoredIndex(1, 3), new ScoredIndex(10, 0), new ScoredIndex(20, 4)),
                global.toScoreIndexPairs());
    }

    @Test
    public void testMergeEqualsSingleThreadedFullScan() {
        List<TopKCollector> collectors = new ArrayList<>();
        for (int t = 0; t < 4; t++) {
            TopKCollector c = new TopKCollector(6);
            for (int i = 0; i < 20; i++) {
                int index = t * 20 + i;
                c.offer(100 - (index % 37), index, rel(index), abs(index));
            }
            collectors.add(c);
        }
        TopKCollector single = new TopKCollector(6);
        for (int i = 0; i < 80; i++) {
            single.offer(100 - (i % 37), i, rel(i), abs(i));
        }
        TopKCollector merged = new TopKCollector(6);
        for (TopKCollector c : collectors) {
            merged.mergeFrom(c);
        }
        assertEquals(single.toScoreIndexPairs(), merged.toScoreIndexPairs());
        assertEquals(6, merged.size());
    }

    @Test
    public void testMergeFromSelfIsNoOp() {
        TopKCollector collector = new TopKCollector(3);
        fill(collector, 3);
        List<ScoredIndex> before = collector.toScoreIndexPairs();
        collector.mergeFrom(collector);
        assertEquals(before, collector.toScoreIndexPairs());
    }

    @Test
    public void testToMatchesCarriesRelativePathAndScore() {
        TopKCollector collector = new TopKCollector(2);
        collector.offer(7, 0, "src/A.java", ROOT.resolve("src/A.java"));
        List<FindMatch> matches = collector.toMatches();
        assertEquals(1, matches.size());
        assertEquals("src/A.java", matches.get(0).getRelativePath());
        assertEquals(7, matches.get(0).getScore());
        assertEquals(ROOT.resolve("src/A.java"), matches.get(0).getAbsolutePath());
    }

    @Test
    public void testMergeFromNullRejected() {
        TopKCollector collector = new TopKCollector(2);
        assertThrows(NopFindException.class, () -> collector.mergeFrom(null));
    }

    @Test
    public void testInvalidKRejected() {
        assertThrows(NopFindException.class, () -> new TopKCollector(0));
        assertThrows(NopFindException.class, () -> new TopKCollector(-1));
    }
}