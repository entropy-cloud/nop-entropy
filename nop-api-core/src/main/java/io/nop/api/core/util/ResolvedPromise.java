/*
 * Copyright (c) IBM Corporation 2017. All Rights Reserved.
 * Project name: java-async-util
 * This project is licensed under the Apache License 2.0, see LICENSE.
 */
// copy from com.ibm.async:asyncutils:0.1.0

package io.nop.api.core.util;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link CompletionStage} which is already complete at initialization.
 * <p>
 * The default execution facility used by this stage implementation is effectively the same as the
 * one in {@link CompletableFuture} (by the use of {@link CompletableFuture#runAsync(Runnable)} and
 * similar methods) except when calling compound methods involving another stage (like
 * {@link #runAfterBothAsync(CompletionStage, Runnable)}) which use the other stage's corresponding
 * Async method to execute the computation
 *
 * @author Renar Narubin
 */
public final class ResolvedPromise<T> implements CompletionStage<T>, Future<T> {
    //  static final CompletedStage<Void> VOID = CompletedStage.of(null);
    static final Logger LOG = LoggerFactory.getLogger(ResolvedPromise.class);

    private final T result;
    private final Throwable exception;

    private ResolvedPromise(final T result, final Throwable exception) {
        this.result = result;
        this.exception = exception;
    }

    public T getResult() {
        return result;
    }

    public Throwable getException() {
        return exception;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return true;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    public boolean isSuccess() {
        return exception == null;
    }

    @Override
    public T get() throws ExecutionException {
        if (exception == null)
            return result;
        throw new ExecutionException(exception);
    }

    @Override
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return get();
    }

    public T syncGet() {
        if (exception != null)
            throw NopException.adapt(exception);
        return result;
    }

    /**
     * return this exceptional stage as if its value type was U
     */
    @SuppressWarnings("unchecked")
    private <U> ResolvedPromise<U> typedException() {
        assert this.exception != null;
        return (ResolvedPromise<U>) this;
    }

    /**
     * Initialize a completed stage with a (non-exceptional) value
     */
    static <T> ResolvedPromise<T> of(final T t) {
        return new ResolvedPromise<>(t, null);
    }

    /**
     * Initialize a completed stage with an exception
     */
    static <T> ResolvedPromise<T> exception(Throwable e) {
        if (e instanceof CompletionException)
            e = e.getCause();
        return new ResolvedPromise<>(null, Objects.requireNonNull(e));
    }

    /**
     * Initialize a completed stage with an exception
     */
    private static <T> ResolvedPromise<T> completionException(final Throwable e) {
        return ResolvedPromise.exception(e);
    }

    private static <T> CompletionException wrapIfNecessary(final Throwable e) {
        return e instanceof CompletionException
                ? (CompletionException) e
                : new CompletionException(e);
    }

    public static <T> ResolvedPromise<T> success(T value) {
        return ResolvedPromise.of(value);
    }

    public static <T> ResolvedPromise<T> reject(Throwable e) {
        return ResolvedPromise.exception(e);
    }

    public static <T> ResolvedPromise<T> complete(T value, Throwable e) {
        return e == null ? success(value) : reject(e);
    }

    @Override
    public <U> CompletionStage<U> thenApply(final Function<? super T, ? extends U> fn) {
        Objects.requireNonNull(fn);
        if (this.exception == null) {
            final U u;
            try {
                u = fn.apply(this.result);
            } catch (final Throwable e) {
                return ResolvedPromise.completionException(e);
            }
            return FutureHelper.success(u);
        }
        return typedException();
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn) {
        Objects.requireNonNull(fn);
        if (this.exception == null) {
            return CompletableFuture.supplyAsync(() -> fn.apply(this.result));
        }
        return typedException();
    }

    @Override
    public <U> CompletionStage<U> thenApplyAsync(final Function<? super T, ? extends U> fn,
                                                 final Executor executor) {
        Objects.requireNonNull(fn);
        if (this.exception == null) {
            return CompletableFuture.supplyAsync(() -> fn.apply(this.result), executor);
        }
        return typedException();
    }

    @Override
    public CompletionStage<Void> thenAccept(final Consumer<? super T> action) {
        Objects.requireNonNull(action);
        if (this.exception == null) {
            try {
                action.accept(this.result);
            } catch (final Throwable e) {
                return ResolvedPromise.completionException(e);
            }
            return FutureHelper.voidPromise();
        }
        return typedException();
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action) {
        Objects.requireNonNull(action);
        if (this.exception == null) {
            return CompletableFuture.runAsync(() -> action.accept(this.result));
        }
        return typedException();
    }

    @Override
    public CompletionStage<Void> thenAcceptAsync(final Consumer<? super T> action,
                                                 final Executor executor) {
        Objects.requireNonNull(action);
        if (this.exception == null) {
            return CompletableFuture.runAsync(() -> action.accept(this.result), executor);
        }
        return typedException();
    }

    @Override
    public CompletionStage<Void> thenRun(final Runnable action) {
        Objects.requireNonNull(action);
        if (this.exception == null) {
            try {
                action.run();
            } catch (final Throwable e) {
                return ResolvedPromise.completionException(e);
            }
            return FutureHelper.voidPromise();
        }
        return typedException();
    }

    @Override
    public CompletionStage<Void> thenRunAsync(final Runnable action) {
        if (this.exception == null) {
            return CompletableFuture.runAsync(action);
        }
        return typedException();
    }

    @Override
    public CompletionStage<Void> thenRunAsync(final Runnable action, final Executor executor) {
        if (this.exception == null) {
            return CompletableFuture.runAsync(action, executor);
        }
        return typedException();
    }

    @Override
    public <U, V> CompletionStage<V> thenCombine(final CompletionStage<? extends U> other,
                                                 final BiFunction<? super T, ? super U, ? extends V> fn) {
        Objects.requireNonNull(fn);
        return other.thenApply(
                this.exception == null
                        ? u -> fn.apply(this.result, u)
                        : u -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                });
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                      final BiFunction<? super T, ? super U, ? extends V> fn) {
        Objects.requireNonNull(fn);
        return other.thenApplyAsync(
                this.exception == null
                        ? u -> fn.apply(this.result, u)
                        : u -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                });
    }

    @Override
    public <U, V> CompletionStage<V> thenCombineAsync(final CompletionStage<? extends U> other,
                                                      final BiFunction<? super T, ? super U, ? extends V> fn, final Executor executor) {
        Objects.requireNonNull(fn);
        return other.thenApplyAsync(
                this.exception == null
                        ? u -> fn.apply(this.result, u)
                        : u -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                },
                executor);
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBoth(final CompletionStage<? extends U> other,
                                                    final BiConsumer<? super T, ? super U> action) {
        Objects.requireNonNull(action);
        return other.thenAccept(
                this.exception == null
                        ? u -> action.accept(this.result, u)
                        : u -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                });
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                         final BiConsumer<? super T, ? super U> action) {
        Objects.requireNonNull(action);
        return other.thenAcceptAsync(
                this.exception == null
                        ? u -> action.accept(this.result, u)
                        : u -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                });
    }

    @Override
    public <U> CompletionStage<Void> thenAcceptBothAsync(final CompletionStage<? extends U> other,
                                                         final BiConsumer<? super T, ? super U> action, final Executor executor) {
        Objects.requireNonNull(action);
        return other.thenAcceptAsync(
                this.exception == null
                        ? u -> action.accept(this.result, u)
                        : u -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                },
                executor);
    }

    @Override
    public CompletionStage<Void> runAfterBoth(final CompletionStage<?> other, final Runnable action) {
        Objects.requireNonNull(action);
        return other.thenRun(
                this.exception == null
                        ? action
                        : () -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                });
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other,
                                                   final Runnable action) {
        Objects.requireNonNull(action);
        return other.thenRunAsync(
                this.exception == null
                        ? action
                        : () -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                });
    }

    @Override
    public CompletionStage<Void> runAfterBothAsync(final CompletionStage<?> other,
                                                   final Runnable action,
                                                   final Executor executor) {
        Objects.requireNonNull(action);
        return other.thenRunAsync(
                this.exception == null
                        ? action
                        : () -> {
                    throw ResolvedPromise.wrapIfNecessary(this.exception);
                },
                executor);
    }

    @Override
    public <U> CompletionStage<U> applyToEither(final CompletionStage<? extends T> other,
                                                final Function<? super T, U> fn) {
        return thenApply(fn);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                     final Function<? super T, U> fn) {
        return thenApplyAsync(fn);
    }

    @Override
    public <U> CompletionStage<U> applyToEitherAsync(final CompletionStage<? extends T> other,
                                                     final Function<? super T, U> fn, final Executor executor) {
        return thenApplyAsync(fn, executor);
    }

    @Override
    public CompletionStage<Void> acceptEither(final CompletionStage<? extends T> other,
                                              final Consumer<? super T> action) {
        return thenAccept(action);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                   final Consumer<? super T> action) {
        return thenAcceptAsync(action);
    }

    @Override
    public CompletionStage<Void> acceptEitherAsync(final CompletionStage<? extends T> other,
                                                   final Consumer<? super T> action, final Executor executor) {
        return thenAcceptAsync(action, executor);
    }

    @Override
    public CompletionStage<Void> runAfterEither(final CompletionStage<?> other,
                                                final Runnable action) {
        return thenRun(action);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other,
                                                     final Runnable action) {
        return thenRunAsync(action);
    }

    @Override
    public CompletionStage<Void> runAfterEitherAsync(final CompletionStage<?> other,
                                                     final Runnable action,
                                                     final Executor executor) {
        return thenRunAsync(action, executor);
    }

    @Override
    public <U> CompletionStage<U> thenCompose(
            final Function<? super T, ? extends CompletionStage<U>> fn) {
        Objects.requireNonNull(fn);
        if (this.exception == null) {
            try {
                return fn.apply(this.result);
            } catch (final Throwable e) {
                return ResolvedPromise.completionException(e);
            }
        }
        return typedException();
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(
            final Function<? super T, ? extends CompletionStage<U>> fn) {
        return thenComposeAsyncInternal(fn, CompletableFuture::runAsync);
    }

    @Override
    public <U> CompletionStage<U> thenComposeAsync(
            final Function<? super T, ? extends CompletionStage<U>> fn, final Executor executor) {
        return thenComposeAsyncInternal(fn, Objects.requireNonNull(executor));
    }

    private <U> CompletionStage<U> thenComposeAsyncInternal(
            final Function<? super T, ? extends CompletionStage<U>> fn, final Executor executor) {
        Objects.requireNonNull(fn);
        if (this.exception == null) {
            final CompletableFuture<U> ret = new CompletableFuture<>();
            executor.execute(() -> {
                try {
                    fn.apply(this.result)
                            .whenComplete((u, exc) -> {
                                if (exc == null) {
                                    ret.complete(u);
                                } else {
                                    ret.completeExceptionally(exc);
                                }
                            });
                } catch (final Throwable e) {
                    ret.completeExceptionally(ResolvedPromise.wrapIfNecessary(e));
                }
            });
            return ret;
        }
        return typedException();
    }

    @Override
    public CompletionStage<T> exceptionally(final Function<Throwable, ? extends T> fn) {
        Objects.requireNonNull(fn);
        if (this.exception == null) {
            return this;
        }

        final T t;
        try {
            t = fn.apply(this.exception);
        } catch (final Throwable e) {
            return ResolvedPromise.completionException(e);
        }
        return FutureHelper.success(t);
    }

    @Override
    public CompletionStage<T> whenComplete(final BiConsumer<? super T, ? super Throwable> action) {
        Objects.requireNonNull(action);
        try {
            action.accept(this.result, this.exception);
        } catch (final Throwable e) {
            if (this.exception == null) {
                LOG.error("nop.err.promise.whenComplete.action.fail", e);
                return ResolvedPromise.completionException(e);
            }
        }
        return this;
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(
            final BiConsumer<? super T, ? super Throwable> action) {
        return whenCompleteAsyncInternal(action, CompletableFuture::runAsync);
    }

    @Override
    public CompletionStage<T> whenCompleteAsync(final BiConsumer<? super T, ? super Throwable> action,
                                                final Executor executor) {
        return whenCompleteAsyncInternal(action, Objects.requireNonNull(executor));
    }

    private CompletionStage<T> whenCompleteAsyncInternal(
            final BiConsumer<? super T, ? super Throwable> action,
            final Executor executor) {
        Objects.requireNonNull(action);
        final CompletableFuture<T> ret = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                action.accept(this.result, this.exception);
            } catch (final Throwable e) {
                LOG.error("nop.err.promise.whenCompleteAsync.action.fail", e);
                ret.completeExceptionally(this.exception == null ? e : this.exception);
                return;
            }

            if (this.exception == null) {
                ret.complete(this.result);
            } else {
                ret.completeExceptionally(this.exception);
            }
        });
        return ret;
    }

    @Override
    public <U> CompletionStage<U> handle(final BiFunction<? super T, Throwable, ? extends U> fn) {
        Objects.requireNonNull(fn);
        final U u;
        try {
            u = fn.apply(this.result, this.exception);
        } catch (final Throwable e) {
            return ResolvedPromise.completionException(e);
        }
        return ResolvedPromise.of(u);
    }

    @Override
    public <U> CompletionStage<U> handleAsync(
            final BiFunction<? super T, Throwable, ? extends U> fn) {
        Objects.requireNonNull(fn);
        return CompletableFuture.supplyAsync(() -> fn.apply(this.result, this.exception));
    }

    @Override
    public <U> CompletionStage<U> handleAsync(final BiFunction<? super T, Throwable, ? extends U> fn,
                                              final Executor executor) {
        Objects.requireNonNull(fn);
        return CompletableFuture.supplyAsync(() -> fn.apply(this.result, this.exception), executor);
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        if (this.exception == null) {
            return CompletableFuture.completedFuture(this.result);
        }

        final CompletableFuture<T> ret = new CompletableFuture<>();
        ret.completeExceptionally(this.exception);
        return ret;
    }
}
