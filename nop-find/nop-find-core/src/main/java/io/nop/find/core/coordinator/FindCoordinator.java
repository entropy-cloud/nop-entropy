package io.nop.find.core.coordinator;

import io.nop.find.core.NopFindException;
import io.nop.find.core.model.FindCommand;
import io.nop.find.core.model.FindMatch;
import io.nop.find.core.score.FuzzyFileScorer;
import io.nop.find.core.topk.TopKCollector;
import io.nop.rg.core.glob.GlobMatcher;
import io.nop.rg.core.walk.ParallelFileWalker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 模糊查找编排器（nop-find design 决策 5）：
 *
 * <pre>
 *   find(FindCommand):
 *     1. ParallelFileWalker.walk(root)              复用 nop-rg 并行遍历 + gitignore 剪枝
 *     2. glob 过滤 → 候选列表（已按路径字典序，全局 index = 候选下标）
 *     3. 按线程数分片
 *     4. 每线程：预归一化 + FuzzyFileScorer 打分 → 线程局部 TopKCollector
 *     5. 归并各线程 collector → 全局 top-K（(score, index) 全序）
 *     6. 映射为 FindMatch 排序输出
 * </pre>
 *
 * <p>确定性契约：候选 index 取自排序后的遍历列表下标，与线程数无关；
 * 因此任意 {@code threads} 取值下返回结果逐元素一致。
 *
 * <p>与 {@code SearchCoordinator} 的边界：不读文件内容、不涉及字节搜索策略，两者互不依赖，
 * 仅共享 {@code ParallelFileWalker} 与线程池模式。
 */
public class FindCoordinator {

    private final int threads;
    private final boolean respectGitignore;
    private final boolean includeHidden;

    public FindCoordinator(int threads, boolean respectGitignore, boolean includeHidden) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }
        this.threads = threads;
        this.respectGitignore = respectGitignore;
        this.includeHidden = includeHidden;
    }

    public int getThreads() {
        return threads;
    }

    public boolean isRespectGitignore() {
        return respectGitignore;
    }

    public boolean isIncludeHidden() {
        return includeHidden;
    }

    public List<FindMatch> find(FindCommand command) {
        if (command == null) {
            throw new NopFindException("find command must not be null");
        }
        Path root = command.getRoot();
        GlobMatcher globs = GlobMatcher.of(command.getGlobs());
        FuzzyFileScorer scorer = new FuzzyFileScorer(command.getQuery(), command.isCaseSensitive());

        // 1) 并行遍历（gitignore / 隐藏文件 / 符号链接 / 错误快速失败契约全部由 walker 承担）
        List<Path> walked = newWalker(root).walk();

        // 2) glob 过滤；遍历结果已按路径字典序排序，候选下标即为确定性 tie-break 的 index
        List<Candidate> candidates = new ArrayList<>(walked.size());
        for (Path file : walked) {
            String rel = relativize(root, file);
            if (!globs.accept(rel)) {
                continue;
            }
            candidates.add(new Candidate(candidates.size(), rel, file));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 3) 按线程数分片（分片下标连续，index 单调）
        int workerCount = Math.min(threads, candidates.size());
        int k = Math.min(command.getLimit(), candidates.size());
        List<List<Candidate>> shards = shard(candidates, workerCount);

        // 4) 每线程打分 + 线程局部 top-K
        List<Callable<TopKCollector>> tasks = new ArrayList<>(workerCount);
        for (List<Candidate> shard : shards) {
            tasks.add(() -> scoreShard(shard, scorer, k));
        }
        List<TopKCollector> collectors = invokeAll(tasks);

        // 5) 归并线程局部 collector（(score, index) 全序 → 结果与线程数无关）
        TopKCollector global = new TopKCollector(k);
        for (TopKCollector collector : collectors) {
            global.mergeFrom(collector);
        }

        // 6) 输出
        return global.toMatches();
    }

    private TopKCollector scoreShard(List<Candidate> shard, FuzzyFileScorer scorer, int k) {
        TopKCollector collector = new TopKCollector(k);
        boolean caseSensitive = scorer.isCaseSensitive();
        for (Candidate candidate : shard) {
            String path = candidate.path.toString();
            String name = candidate.name();
            // 预归一化：每个字段只折叠一次，打分热路径零 toLowerCase
            String normName = caseSensitive ? name : FuzzyFileScorer.normalize(name);
            String normRel = caseSensitive ? candidate.relativePath : FuzzyFileScorer.normalize(candidate.relativePath);
            String normPath = caseSensitive ? path : FuzzyFileScorer.normalize(path);
            int score = scorer.scoreNormalized(normName, normRel, normPath);
            if (score != FuzzyFileScorer.NO_MATCH) {
                collector.offer(score, candidate.index, candidate.relativePath, candidate.path);
            }
        }
        return collector;
    }

    private static List<List<Candidate>> shard(List<Candidate> candidates, int workerCount) {
        List<List<Candidate>> shards = new ArrayList<>(workerCount);
        int size = candidates.size();
        int base = size / workerCount;
        int remainder = size % workerCount;
        int start = 0;
        for (int i = 0; i < workerCount; i++) {
            int len = base + (i < remainder ? 1 : 0);
            shards.add(candidates.subList(start, start + len));
            start += len;
        }
        return shards;
    }

    private List<TopKCollector> invokeAll(List<Callable<TopKCollector>> tasks) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, threads));
        try {
            List<TopKCollector> results = new ArrayList<>(tasks.size());
            for (Future<TopKCollector> future : pool.invokeAll(tasks)) {
                try {
                    results.add(future.get());
                } catch (ExecutionException e) {
                    throw new NopFindException("find shard failed", e.getCause());
                }
            }
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NopFindException("find interrupted", e);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 构造遍历器（单一调用点，测试可覆写以验证 walker 接线）。
     */
    ParallelFileWalker newWalker(Path root) {
        return new ParallelFileWalker(root, threads, respectGitignore, includeHidden);
    }

    /**
     * root → file 的相对路径，'/' 分隔（跨平台稳定形态）。
     */
    static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static final class Candidate {
        final int index;
        final String relativePath;
        final Path path;

        Candidate(int index, String relativePath, Path path) {
            this.index = index;
            this.relativePath = relativePath;
            this.path = path;
        }

        String name() {
            Path fileName = path.getFileName();
            return fileName == null ? "" : fileName.toString();
        }
    }
}