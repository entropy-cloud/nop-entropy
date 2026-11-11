package io.nop.find.core.coordinator;

import io.nop.find.core.NopFindException;
import io.nop.find.core.model.FindCommand;
import io.nop.find.core.model.FindMatch;
import io.nop.find.core.score.FuzzyFileScorer;
import io.nop.find.core.topk.TopKCollector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-05 失效面 9 载体（plan 2564）：FindCoordinator shard 聚合——单 shard 失败不弃已完成
 * shard，全部 shard 跑完后汇总抛 {@link NopFindException}（含 shard 下标 + 候选区间上下文）；
 * 不引入 partial top-K（find 的 shard 是全量必要输入，index tie-break 确定性契约冻结）。
 *
 * <p>注入通道：覆写包私有 {@link FindCoordinator#scoreShard}（单一调用点，运行时分派确定性
 * 送指定 shard 失败），并统计调用次数验证「全部 shard 跑完再处置」。
 */
public class TestFindCoordinatorShardFailure {

    @TempDir
    Path tempDir;

    private void write(String rel) throws IOException {
        Path file = tempDir.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "// " + rel + "\n");
    }

    @Test
    public void singleShardFailureAggregatesWithShardContextAfterAllShardsComplete() throws IOException {
        // 候选数 > 线程数 → 多 shard；两个 marker 文件分列字典序首尾（→ 不同 shard）确定性失败。
        // 聚合语义：全部 shard 跑完，两个失败 shard 的上下文（下标 + 候选区间）都进入汇总
        // ——首 shard 即抛的弃全形态会丢掉第二个失败 shard 的上下文（变异链 4 的红证点）
        for (int i = 0; i < 11; i++) {
            write("bulk/dir" + (i % 3) + "/NopfindBulk" + i + ".java");
        }
        write("aaa-marker.txt");
        write("zzz-marker.txt");

        AtomicInteger scoreShardCalls = new AtomicInteger();
        FindCoordinator coordinator = new FaultyShardCoordinator(3, false, false,
                List.of("aaa-marker.txt", "zzz-marker.txt"), scoreShardCalls);

        NopFindException failure = assertThrows(NopFindException.class,
                () -> coordinator.find(FindCommand.of(tempDir, "nopfind")));
        assertTrue(failure.getMessage().contains("shards failed"),
                "汇总消息必须含 shard 标识: " + failure.getMessage());
        // NopException.getMessage 为装饰形态；候选区间上下文在顶层或 suppressed 消息中
        List<String> contexts = new java.util.ArrayList<>();
        collectContexts(failure, contexts);
        assertTrue(contexts.stream().anyMatch(c -> c.contains("candidates [")),
                "汇总必须含候选区间上下文: " + contexts);
        // 两个失败 shard 的上下文都必须保留（首错弃全会丢后者）
        assertTrue(contexts.stream().anyMatch(c -> c.contains("aaa-marker.txt")),
                "失败 shard A 上下文必须聚合: " + contexts);
        assertTrue(contexts.stream().anyMatch(c -> c.contains("zzz-marker.txt")),
                "失败 shard B 上下文必须聚合（首错弃全形态丢失）: " + contexts);
        // 全部 shard 跑完再处置：scoreShard 调用数 = shard 数（3）
        assertEquals(3, scoreShardCalls.get(), "首错不得中断后续 shard: " + scoreShardCalls.get());
    }

    private static void collectContexts(Throwable failure, List<String> out) {
        out.add(String.valueOf(failure.getMessage()));
        for (Throwable suppressed : failure.getSuppressed()) {
            collectContexts(suppressed, out);
        }
    }

    @Test
    public void noShardFailureKeepsNormalTopKResult() throws IOException {
        for (int i = 0; i < 11; i++) {
            write("bulk/dir" + (i % 3) + "/NopfindBulk" + i + ".java");
        }
        AtomicInteger scoreShardCalls = new AtomicInteger();
        FindCoordinator coordinator = new FaultyShardCoordinator(3, false, false,
                List.of("boom/none.txt"), scoreShardCalls);
        List<FindMatch> matches = coordinator.find(FindCommand.of(tempDir, "nopfind"));
        assertTrue(matches.size() >= 11);
        assertEquals(3, scoreShardCalls.get());
    }

    /**
     * 测试用 coordinator：marker 文件所在的 shard 打分时确定性失败（W-05 失效面 9 注入通道）。
     */
    private static final class FaultyShardCoordinator extends FindCoordinator {
        private final List<String> markerRelativePaths;
        private final AtomicInteger callCount;

        FaultyShardCoordinator(int threads, boolean respectGitignore, boolean includeHidden,
                               List<String> markerRelativePaths, AtomicInteger callCount) {
            super(threads, respectGitignore, includeHidden);
            this.markerRelativePaths = markerRelativePaths;
            this.callCount = callCount;
        }

        @Override
        TopKCollector scoreShard(List<Candidate> shard, FuzzyFileScorer scorer, int k) {
            callCount.incrementAndGet();
            for (Candidate candidate : shard) {
                if (markerRelativePaths.contains(candidate.relativePath)) {
                    throw new IllegalStateException("simulated shard fault on " + candidate.relativePath);
                }
            }
            return super.scoreShard(shard, scorer, k);
        }
    }
}
