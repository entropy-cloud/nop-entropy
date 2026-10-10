package io.nop.find.core.topk;

import io.nop.find.core.NopFindException;
import io.nop.find.core.model.FindMatch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-K 收集器（nop-find design 决策 4）。
 *
 * <p>契约：
 * <ul>
 *   <li>内部维护按 {@code (score, index)} 严格全序升序排列的定长列表（K 固定）。</li>
 *   <li>{@link #offer} 快速路径：已满且候选不优于末尾（即 {@code >=} 末尾）直接跳过，O(1)。</li>
 *   <li>否则二分查找插入点后插入，超长弹出末尾，O(log K) + 元素搬移。</li>
 *   <li>{@code index} 是候选在遍历结果中的全局序号（遍历列表已按路径字典序排序），
 *       因此同分按 index 升序 == 同分按路径字典序，且跨线程归并结果与单线程完全一致。</li>
 *   <li><b>非线程安全</b>：每个工作线程持有一个实例，最终由 {@link #mergeFrom(TopKCollector)} 归并。</li>
 * </ul>
 */
public class TopKCollector {

    private final int k;
    private final Entry[] entries;
    private int size;

    public TopKCollector(int k) {
        if (k <= 0) {
            throw new NopFindException("topK must be positive, got " + k);
        }
        this.k = k;
        this.entries = new Entry[k];
    }

    public int getK() {
        return k;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 提交一个候选。
     *
     * @param score  分值（越小越优）
     * @param index  全局序号（tie-break，越小越优）
     * @return true 表示候选进入 top-K
     */
    public boolean offer(int score, int index, String relativePath, Path absolutePath) {
        if (size == k && !better(score, index, entries[size - 1].score, entries[size - 1].index)) {
            return false; // 快速路径：已满且不优于末尾
        }
        int lo = 0;
        int hi = size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (better(score, index, entries[mid].score, entries[mid].index)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        int insertAt = lo;
        if (insertAt >= k) {
            // 不变式守卫：快速路径已排除"不优于末尾"，二分必然落在 [0, k-1]。
            // 一旦可达即表示不变式被破坏——显式失败，不静默丢弃候选。
            throw new IllegalStateException(
                    "top-K insertion invariant broken: insertAt=" + insertAt + " k=" + k + " size=" + size);
        }
        if (size < k) {
            size++;
        }
        for (int i = size - 1; i > insertAt; i--) {
            entries[i] = entries[i - 1];
        }
        entries[insertAt] = new Entry(score, index, relativePath, absolutePath);
        return true;
    }

    /**
     * 归并另一个收集器的内容（保留全局 top-K，顺序仍为 (score, index) 全序）。
     * 允许合并同一个收集器（幂等）。
     */
    public void mergeFrom(TopKCollector other) {
        if (other == null) {
            throw new NopFindException("merge source must not be null");
        }
        if (other == this) {
            return;
        }
        for (int i = 0; i < other.size; i++) {
            Entry e = other.entries[i];
            offer(e.score, e.index, e.relativePath, e.absolutePath);
        }
    }

    /**
     * 导出当前 top-K（已排序），顺序为 (score, index) 全序。
     */
    public List<FindMatch> toMatches() {
        List<FindMatch> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Entry e = entries[i];
            result.add(new FindMatch(e.relativePath, e.absolutePath, e.score));
        }
        return result;
    }

    /**
     * 导出当前 top-K 的 (score, index) 视图（测试/诊断用）。
     */
    public List<ScoredIndex> toScoreIndexPairs() {
        List<ScoredIndex> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new ScoredIndex(entries[i].score, entries[i].index));
        }
        return result;
    }

    /**
     * (score, index) 全序键视图；record 提供值相等语义，便于断言。
     */
    public record ScoredIndex(int score, int index) {
    }

    private static boolean better(int scoreA, int indexA, int scoreB, int indexB) {
        if (scoreA != scoreB) {
            return scoreA < scoreB;
        }
        return indexA < indexB;
    }

    private static final class Entry {
        final int score;
        final int index;
        final String relativePath;
        final Path absolutePath;

        Entry(int score, int index, String relativePath, Path absolutePath) {
            this.score = score;
            this.index = index;
            this.relativePath = relativePath;
            this.absolutePath = absolutePath;
        }
    }
}