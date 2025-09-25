package io.nop.commons.util;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.AsyncJoinType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class AsyncHelper {
    static final Logger LOG = LoggerFactory.getLogger(AsyncHelper.class);

    public static CompletableFuture<Void> waitAsync(List<? extends CompletionStage<?>> promises, AsyncJoinType joinType) {
        Guard.checkArgument(!promises.isEmpty(), "promises");

        CompletableFuture<Void> future = new CompletableFuture<>();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        int n = promises.size();
        for (CompletionStage<?> promise : promises) {
            promise.whenComplete((ret, err) -> {
                if (err != null) {
                    LOG.error("nop.wait-async-err", err);

                    if (failure.get() == null)
                        failure.set(err);
                }
                if (joinType == AsyncJoinType.anyFailure) {
                    if (err != null) {
                        FutureHelper.complete(future, null, err);
                    }
                } else if (joinType == AsyncJoinType.anySuccess) {
                    if (err == null) {
                        FutureHelper.complete(future, null, null);
                    }
                }
                completedCount.incrementAndGet();
                if (completedCount.get() == n) {
                    if ((joinType == AsyncJoinType.allSuccess || joinType == null) && failure.get() != null) {
                        // 如果要求所有分支都成功，但是存在异常，则作为异常返回
                        FutureHelper.complete(future, null, failure.get());
                    } else {
                        // allComplete时总是成功返回
                        FutureHelper.complete(future, null, null);
                    }
                }
            });
        }
        return future;
    }

    /**
     * 并行执行一组异步任务
     *
     * @param tasks 异步任务列表，每个任务返回CompletionStage
     * @param <T>   任务结果类型
     * @return CompletableFuture<Void> 当所有任务完成时完成
     */
    public static <T> CompletableFuture<Void> parallelExecute(
            List<Supplier<CompletionStage<T>>> tasks, AsyncJoinType joinType) {

        if (tasks == null || tasks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // 将每个Supplier转换为CompletionStage并收集到列表中
        List<CompletionStage<T>> stages = tasks.stream()
                .map(Supplier::get)
                .collect(Collectors.toList());

        return waitAsync(stages, joinType);
    }

    /**
     * 顺序执行一组异步任务（前一个任务完成后才开始下一个）
     *
     * @param tasks 异步任务列表，每个任务返回CompletionStage
     * @param <T>   任务结果类型
     * @return CompletableFuture<Void> 当所有任务按顺序完成时完成
     */
    public static <T> CompletableFuture<Void> sequentialExecute(
            List<Supplier<CompletionStage<T>>> tasks) {

        if (tasks == null || tasks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        // 使用reduce来顺序链接任务
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);

        for (Supplier<CompletionStage<T>> task : tasks) {
            result = result.thenCompose(v -> task.get().thenApply(r -> null));
        }

        return result;
    }

    /**
     * 并行执行任务并收集所有结果
     *
     * @param tasks 异步任务列表
     * @param <T>   结果类型
     * @return CompletableFuture<List < T>> 包含所有任务结果的列表
     */
    public static <T> CompletableFuture<List<T>> parallelExecuteWithResults(
            List<Supplier<CompletionStage<T>>> tasks) {

        if (tasks == null || tasks.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        // 执行所有任务并收集结果
        List<CompletableFuture<T>> futures = tasks.stream()
                .map(task -> task.get().toCompletableFuture())
                .collect(Collectors.toList());

        // 等待所有任务完成并收集结果
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * 顺序执行任务并收集所有结果
     *
     * @param tasks 异步任务列表
     * @param <T>   结果类型
     * @return CompletableFuture<List < T>> 包含所有任务结果的列表（按执行顺序）
     */
    public static <T> CompletableFuture<List<T>> sequentialExecuteWithResults(
            List<Supplier<CompletionStage<T>>> tasks) {

        if (tasks == null || tasks.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        CompletableFuture<List<T>> result = CompletableFuture.completedFuture(
                new java.util.ArrayList<>());

        for (Supplier<CompletionStage<T>> task : tasks) {
            result = result.thenCompose(list ->
                    task.get().thenApply(item -> {
                        list.add(item);
                        return list;
                    })
            );
        }

        return result;
    }
}
