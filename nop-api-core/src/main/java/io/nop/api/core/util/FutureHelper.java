/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopTimeoutException;
import io.nop.api.core.time.CoreMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class FutureHelper {
    static final Logger LOG = LoggerFactory.getLogger(FutureHelper.class);

    private static final CompletionStage<Void> VOID_PROMISE = ResolvedPromise.of(null);
    private static final CompletionStage<Boolean> TRUE_PROMISE = ResolvedPromise.of(true);
    private static final CompletionStage<Boolean> FALSE_PROMISE = ResolvedPromise.of(false);
    private static final CompletionStage<Integer> ZERO_PROMISE = ResolvedPromise.of(0);
    private static final CompletionStage<Long> ZERO_LONG_PROMISE = ResolvedPromise.of(0L);

    private static final CompletableFuture<Void> NULL_JAVA_FUTURE = CompletableFuture.completedFuture(null);

    private static final Integer ZERO = 0;
    private static final Long ZERO_LONG = 0L;

    public static CompletionStage<Void> voidPromise() {
        return VOID_PROMISE;
    }

    @SuppressWarnings({"unchecked", "cast"})
    public static <T> CompletionStage<T> nullPromise() {
        return (CompletionStage<T>) VOID_PROMISE;
    }

    public static CompletionStage<Boolean> truePromise() {
        return TRUE_PROMISE;
    }

    public static CompletionStage<Boolean> falsePromise() {
        return FALSE_PROMISE;
    }

    public static CompletionStage<Integer> zeroPromise() {
        return ZERO_PROMISE;
    }

    public static CompletionStage<Long> zeroLongPromise() {
        return ZERO_LONG_PROMISE;
    }

    @SuppressWarnings({"unchecked", "cast"})
    public static <T> CompletionStage<T> success(Object o) {
        if (o == null)
            return (CompletionStage<T>) VOID_PROMISE;
        // 一般情况下用指针相等即可，只是一个性能优化
        if (o == Boolean.TRUE)
            return (CompletionStage<T>) TRUE_PROMISE;
        if (o == Boolean.FALSE)
            return (CompletionStage<T>) FALSE_PROMISE;

        // Integer.valueOf内部使用了缓存，小整数是指针相等的
        if (o == ZERO)
            return (CompletionStage<T>) ZERO_PROMISE;

        if (o == ZERO_LONG)
            return (CompletionStage<T>) ZERO_LONG_PROMISE;

        if (o instanceof CompletionStage)
            return (CompletionStage<T>) o;

        return ResolvedPromise.of((T) o);
    }

    public static <T> ResolvedPromise<T> reject(Throwable e) {
        return ResolvedPromise.exception(e);
    }

    public static <T> void complete(CompletableFuture<T> future, T result, Throwable exception) {
        if (exception != null) {
            future.completeExceptionally(exception);
        } else {
            future.complete(result);
        }
    }

    public static <R, T> CompletionStage<T> futureApply(Function<? super R, ?> task, R request) {
        try {
            Object o = task.apply(request);
            return toCompletionStage(o);
        } catch (Throwable t) {
            return reject(t);
        }
    }

    public static <T> CompletionStage<T> futureCall(Callable<?> task) {
        try {
            Object o = task.call();
            return toCompletionStage(o);
        } catch (Throwable t) {
            return reject(t);
        }
    }

    public static CompletionStage<Void> futureRun(Runnable task) {
        try {
            task.run();
            return success(null);
        } catch (Throwable t) {
            return reject(t);
        }
    }

    /**
     * 如果对象类型是CompletionStage类型则直接返回，否则包装为CompletableFuture返回
     *
     * @param value 待检查的对象
     * @param <T>   类型
     * @return 异步对象
     */
    @SuppressWarnings({"unchecked", "cast"})
    public static <T> CompletionStage<T> toCompletionStage(Object value) {
        return success(value);
    }

    /**
     * 同步等待CompletionStage异步对象返回，如果发现异步执行异常，则在这里重新抛出ExecutionException.getCause()
     *
     * @param future 待检查的异步对象
     * @return 同步等待返回的结果。相当于future.get()
     */
    public static <T> T syncGet(CompletionStage<T> future) {
        if (future == null)
            return null;

        if (future instanceof ResolvedPromise) {
            ResolvedPromise<T> promise = (ResolvedPromise<T>) future;
            return promise.syncGet();
        }

        if (future.getClass() == CompletableFuture.class) {
            CompletableFuture<T> promise = (CompletableFuture<T>) future;
            if (promise.isDone()) {
                return getFromFuture(promise);
            }
        }

        return ContextProvider.getOrCreateContext().syncGet(future);
    }

    public static Object tryResolve(Object value) {
        if (value == null)
            return null;

        if (value instanceof ResolvedPromise) {
            ResolvedPromise<?> promise = (ResolvedPromise<?>) value;
            return promise.syncGet();
        }

        if (value.getClass() == CompletableFuture.class) {
            CompletableFuture<?> promise = (CompletableFuture<?>) value;
            if (promise.isDone()) {
                return getFromFuture(promise);
            }
        }
        return value;
    }

    public static boolean isDone(Object value) {
        if (value == null)
            return false;

        if (value instanceof ResolvedPromise) {
            return true;
        }

        if (value.getClass() == CompletableFuture.class)
            return ((CompletableFuture<?>) value).isDone();

        return !(value instanceof CompletionStage);
    }

    public static boolean isFutureDone(CompletionStage<?> future) {
        if (future == null)
            return true;

        if (future instanceof ResolvedPromise) {
            return true;
        }

        if (future.getClass() == CompletableFuture.class)
            return ((CompletableFuture<?>) future).isDone();

        return false;
    }

    @SuppressWarnings({"unchecked", "cast"})
    public static <T> Future<T> toFuture(Object o) {
        if (o == null)
            return null;

        if (o.getClass() == CompletableFuture.class)
            return (Future<T>) o;

        if (o instanceof ResolvedPromise)
            return (Future<T>) o;

        if (o instanceof CompletionStage) {
            return ((CompletionStage<T>) o).toCompletableFuture();
        }

        if (o instanceof Future)
            return (Future<T>) o;

        return ResolvedPromise.of((T) o);
    }

    public static <T> T getFromFuture(Future<T> future) {
        if (future == null)
            return null;

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        } catch (ExecutionException | CompletionException e) {
            // 记录调用堆栈
            if (LOG.isDebugEnabled()) {
                LOG.debug("nop.err.api.async.get-return-exception", e);
            }
            throw NopException.adapt(e.getCause());
        }
    }

    public static <T> T waitResult(Future<T> future, long timeout) {
        if (future == null)
            return null;

        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw NopException.adapt(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw NopException.adapt(e);
        } catch (ExecutionException e) {
            // 记录调用堆栈
            if (LOG.isDebugEnabled()) {
                LOG.debug("nop.err.api.async.get-return-exception", e);
            }
            throw NopException.adapt(e.getCause());
        }
    }

    @SuppressWarnings({"cast"})
    public static Object getResult(Object result) {
        if (result instanceof CompletionStage) {
            return syncGet((CompletionStage<?>) result);
        }
        return result;
    }

    public static void cancel(CompletionStage<?> result) {
        result.toCompletableFuture().cancel(false);
    }

    public static <T> void bindResult(CompletionStage<T> promise, CompletableFuture<T> future) {
        promise.whenComplete((value, ex) -> {
            if (ex != null) {
                future.completeExceptionally(ex);
            } else {
                future.complete(value);
            }
        });
    }

    /**
     * 当promise被取消的时候，自动取消关联的future。例如当一个在线程池队列中排队的异步任务被取消后，自动将自己从线程池调度队列中删除
     *
     * @param promise 异步执行所对应的promise对象
     */
    public static void bindCancel(CompletionStage<?> promise, Future<?> future) {
        promise.whenComplete((value, ex) -> {
            if (!future.isDone())
                future.cancel(true);
        });
    }

    @SuppressWarnings({"unchecked", "cast"})
    public static <R> void completeAfterTask(CompletableFuture<R> promise, Callable<?> task) {
        try {
            Object ret = task.call();
            if (ret instanceof CompletionStage) {
                FutureHelper.bindResult((CompletionStage<R>) ret, promise);
            } else {
                promise.complete((R) ret);
            }
        } catch (Exception e2) {
            promise.completeExceptionally(e2);
        }
    }

    public static <T, R> CompletionStage<R> thenCompleteAsync(CompletionStage<T> future,
                                                              BiFunction<? super T, ? super Throwable, ?> handler) {
        CompletableFuture<R> promise = new CompletableFuture<>();
        future.whenComplete((ret, err) -> {
            try {
                Object ret2 = handler.apply(ret, err);
                if (ret2 instanceof CompletionStage) {
                    ((CompletionStage<R>) ret2).whenComplete((ret3, err3) -> {
                        complete(promise, ret3, err3);
                    });
                } else {
                    complete(promise, (R) ret2, null);
                }
            } catch (Exception e) {
                promise.completeExceptionally(e);
            }
        });
        return promise;
    }

    public static <T> CompletionStage<T> whenCompleteAsync(
            CompletionStage<T> future, BiFunction<? super T, ? super Throwable, CompletionStage<?>> handler) {
        if (future == null)
            future = success(null);

        CompletableFuture<T> promise = new CompletableFuture<>();
        future.whenComplete((ret, err) -> {
            try {
                handler.apply(ret, err).whenComplete((ret2, err2) -> {
                    complete(promise, ret, err2);
                });
            } catch (Exception e) {
                promise.completeExceptionally(e);
            }
        });
        return promise;
    }

    /**
     * 判断是否超时异常
     *
     * @param e 异常对象
     */
    public static boolean isTimeoutException(Throwable e) {
        return e instanceof NopTimeoutException || e instanceof TimeoutException;
    }

    /**
     * 判断是否取消动作产生的异常，这里的判断逻辑与java Future内部实现相同
     */
    public static boolean isCancellationException(Throwable e) {
        return e instanceof CancellationException;
    }

    @SuppressWarnings({"cast"})
    public static CompletableFuture<Void> waitAll(Collection<? extends CompletionStage> futures) {
        return (CompletableFuture<Void>) waitFutures(futures, true);
    }

    public static CompletableFuture<?> waitAny(Collection<? extends CompletionStage> futures) {
        return waitFutures(futures, false);
    }

    private static CompletableFuture<?> waitFutures(Collection<? extends CompletionStage> futures, boolean all) {
        if (futures.isEmpty())
            return NULL_JAVA_FUTURE;

        if (futures.size() == 1) {
            return ((CompletionStage<Object>) futures.iterator().next()).thenRun(() -> {
            }).toCompletableFuture();
        }

        CompletableFuture<?>[] waits = new CompletableFuture[futures.size()];
        int i = 0;
        for (CompletionStage<?> stage : futures) {
            waits[i] = stage.toCompletableFuture();
            i++;
        }
        return all ? CompletableFuture.allOf(waits) : CompletableFuture.anyOf(waits);
    }

    public static CompletionStage<Void> bothSuccess(CompletionStage<?> f1, CompletionStage<?> f2) {
        if (isSuccess(f1) && isSuccess(f2))
            return voidPromise();
        return f1.runAfterBoth(f2, () -> {
        });
    }

    public static void collectWaiting(CompletionStage<?> f, List<CompletionStage<?>> all) {
        if (f != null && !isSuccess(f)) {
            all.add(f);
        }
    }

    public static <T> T thenRun(T result, Runnable task) {
        if (result instanceof CompletionStage) {
            return (T) ((CompletionStage<?>) result).whenComplete((v, err) -> {
                if (err != null) {
                    task.run();
                }
            });
        } else {
            task.run();
        }
        return result;
    }

    private static boolean isSuccess(CompletionStage<?> f) {
        if (f instanceof ResolvedPromise)
            return ((ResolvedPromise<?>) f).isSuccess();
        return false;
    }

    public static boolean isError(CompletionStage<?> f) {
        if (f instanceof ResolvedPromise)
            return ((ResolvedPromise<?>) f).getException() != null;
        if (f instanceof CompletableFuture) {
            return ((CompletableFuture<?>) f).isCompletedExceptionally();
        }
        return false;
    }

    public static CompletableFuture<?> waitAnySuccess(Collection<? extends CompletionStage> futures) {
        if (futures.isEmpty())
            return NULL_JAVA_FUTURE;

        if (futures.size() == 1) {
            return ((CompletionStage<Object>) futures.iterator().next()).toCompletableFuture();
        }

        CompletableFuture<Object> future = new CompletableFuture<>();
        AtomicInteger active = new AtomicInteger(futures.size());
        for (CompletionStage<?> promise : futures) {
            promise.whenComplete((ret, exp) -> {
                int count = active.decrementAndGet();
                if (exp == null) {
                    // 任何一个成功都返回
                    future.complete(ret);
                } else if (count == 0) {
                    // 最后一个失败才返回错误信息
                    future.completeExceptionally(exp);
                }
            });
        }
        return future;
    }

    public static <T> CompletableFuture<T> runOnExecutor(Executor executor, Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    T result = task.call();
                    future.complete(result);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public static boolean waitUntil(BooleanSupplier test, long timeout) {
        return waitUntil(test, timeout, 100);
    }

    public static boolean waitUntil(BooleanSupplier test, long timeout, long sleepInterval) {
        Guard.positiveLong(timeout, "timeout");

        long endTime = CoreMetrics.currentTimeMillis() + timeout;
        while (!test.getAsBoolean()) {
            try {
                Thread.sleep(sleepInterval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            if (CoreMetrics.currentTimeMillis() >= endTime)
                return false;
        }
        return true;
    }

    public static void bindCancelToken(ICancelToken cancelToken, CompletableFuture<?> future) {
        if (cancelToken != null) {
            Consumer<String> cancel = reason -> future.cancel(false);
            cancelToken.appendOnCancel(cancel);
            future.whenComplete((ret, err) -> {
                cancelToken.removeOnCancel(cancel);
            });
        }
    }

    public static <T> CompletionStage<T> executeWithThrottling(Supplier<CompletionStage<T>> task, Semaphore limit) {
        if (limit == null)
            return task.get();

        try {
            limit.acquire();
            return task.get().whenComplete((ret, err) -> {
                limit.release();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return reject(e);
        } catch (RuntimeException e) {
            limit.release();
            throw e;
        }
    }
}