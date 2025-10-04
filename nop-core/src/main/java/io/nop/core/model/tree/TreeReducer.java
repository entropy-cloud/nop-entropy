package io.nop.core.model.tree;

import io.nop.api.core.util.FutureHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 通用的树结构处理器，支持同步和异步处理
 *
 * @param <T> 树节点类型
 * @param <R> 处理结果类型
 */
public class TreeReducer<T, R> {

    private final ITreeChildrenAdapter<T> childrenAdapter;

    public TreeReducer(ITreeChildrenAdapter<T> childrenAdapter) {
        this.childrenAdapter = childrenAdapter;
    }

    /**
     * 异步处理树结构
     *
     * @param root          根节点
     * @param nodeProcessor 节点处理器（异步）
     * @param resultMerger  结果合并器（异步）
     * @return 根节点的处理结果
     */
    public CompletionStage<R> processAsync(
            T root,
            Function<T, ?> nodeProcessor,
            BiFunction<R, List<R>, ?> resultMerger) {

        return processNodeAsync(root, nodeProcessor, resultMerger);
    }

    /**
     * 递归处理单个节点及其子节点
     */
    private CompletionStage<R> processNodeAsync(
            T node,
            Function<T, ?> nodeProcessor,
            BiFunction<R, List<R>, ?> resultMerger) {

        // 获取子节点
        Collection<? extends T> children = childrenAdapter.getChildren(node);

        if (children.isEmpty()) {
            // 叶子节点：直接处理当前节点
            return FutureHelper.toCompletionStage(nodeProcessor.apply(node));
        } else {
            // 非叶子节点：异步处理所有子节点
            List<CompletionStage<R>> childrenFutures = new ArrayList<>();
            for (T child : children) {
                CompletionStage<R> childFuture = processNodeAsync(child, nodeProcessor, resultMerger);
                childrenFutures.add(childFuture);
            }

            // 等待所有子节点处理完成
            return FutureHelper.waitAll(childrenFutures)
                    .thenCompose(v -> {
                        // 收集子节点结果
                        List<R> childrenResults = FutureHelper.getResults(childrenFutures);

                        // 处理当前节点，然后与子节点结果异步合并
                        return FutureHelper.<R>toCompletionStage(nodeProcessor.apply(node))
                                .thenCompose(nodeResult ->
                                        FutureHelper.toCompletionStage(resultMerger.apply(nodeResult, childrenResults)));
                    });
        }
    }

    /**
     * 同步处理树结构
     *
     * @param root          根节点
     * @param nodeProcessor 节点处理器（同步）
     * @param resultMerger  结果合并器（同步）
     * @return 根节点的处理结果
     */
    public R process(
            T root,
            Function<T, ? extends R> nodeProcessor,
            BiFunction<R, List<R>, ? extends R> resultMerger) {

        return processNode(root, nodeProcessor, resultMerger);
    }

    /**
     * 递归同步处理单个节点及其子节点
     */
    private R processNode(
            T node,
            Function<T, ? extends R> nodeProcessor,
            BiFunction<R, List<R>, ? extends R> resultMerger) {

        // 获取子节点
        Collection<? extends T> children = childrenAdapter.getChildren(node);

        if (children.isEmpty()) {
            // 叶子节点：直接处理当前节点
            return nodeProcessor.apply(node);
        } else {
            // 非叶子节点：递归处理所有子节点
            List<R> childrenResults = new ArrayList<>(children.size());
            for (T child : children) {
                R childResult = processNode(child, nodeProcessor, resultMerger);
                childrenResults.add(childResult);
            }

            // 处理当前节点
            R nodeResult = nodeProcessor.apply(node);

            // 合并当前节点结果与子节点结果
            return resultMerger.apply(nodeResult, childrenResults);
        }
    }
}