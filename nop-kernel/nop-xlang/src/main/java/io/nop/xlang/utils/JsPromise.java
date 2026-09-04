/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical_entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.utils;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.commons.concurrent.executor.ContinuationExecutor;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * JavaScript Promise 全局对象兼容：继承 {@link CompletableFuture} 提供 JS 风格 then/catch/finally API。
 * <p>
 * microtask queue 语义通过 {@link ContinuationExecutor} 实现：
 * {@code new Promise(executor)} 中的 executor 以及 {@code then/catch/finally} 回调
 * 都通过 {@link ContinuationExecutor#execute(Runnable)} 调度，在当前同步任务结束后才执行。
 * <p>
 * JS 关键字（catch / finally）无法作为 Java 方法名，通过 {@link Name} 注解重命名后 XScript 中可裸名调用。
 */
public class JsPromise extends CompletableFuture<Object> {

    /** new Promise(executor)：executor 通过 ContinuationExecutor 调度（microtask 语义） */
    public JsPromise(IEvalScope scope, BiFunction<JsPromise, JsPromise, Void> executor) {
        try {
            ContinuationExecutor.INSTANCE.execute(() -> executor.apply(this, this));
        } catch (Throwable e) {
            completeExceptionally(e);
        }
    }

    /** 无参构造：用于 resolve/reject 静态工厂 */
    JsPromise() {
    }

    /** new Promise(value)：直接 resolve(value) */
    public JsPromise(Object value) {
        complete(value);
    }

    public static JsPromise resolve(Object value) {
        JsPromise p = new JsPromise();
        p.complete(value);
        return p;
    }

    public static JsPromise reject(Object error) {
        JsPromise p = new JsPromise();
        p.completeExceptionally(new Rejected(error));
        return p;
    }

    @Name("then")
    @EvalMethod
    public JsPromise thenJs(IEvalScope scope, Object onFulfilled) {
        return thenJs(scope, onFulfilled, null);
    }

    @Name("then")
    @EvalMethod
    public JsPromise thenJs(IEvalScope scope, Object onFulfilled, Object onRejected) {
        BiFunction<Object, Throwable, Object> onF = asFunction(scope, onFulfilled);
        BiFunction<Object, Throwable, Object> onR = onRejected == null
                ? (v, e) -> v
                : asFunction(scope, onRejected);
        JsPromise next = new JsPromise();
        whenComplete((value, err) -> {
            ContinuationExecutor.INSTANCE.execute(() -> {
                try {
                    if (err == null) {
                        Object result = onF.apply(value, err);
                        next.complete(result);
                    } else {
                        Object reason = err instanceof Rejected ? ((Rejected) err).getReason() : err;
                        Object result = onR.apply(reason, null);
                        next.complete(result);
                    }
                } catch (Throwable e) {
                    next.completeExceptionally(e);
                }
            });
        });
        return next;
    }

    @Name("catch")
    @EvalMethod
    public JsPromise catchError(IEvalScope scope, Object onRejected) {
        return thenJs(scope, null, onRejected);
    }

    @Name("finally")
    @EvalMethod
    public JsPromise finallyDo(IEvalScope scope, Object onFinally) {
        JsPromise next = new JsPromise();
        whenComplete((value, err) -> {
            ContinuationExecutor.INSTANCE.execute(() -> {
                try {
                    if (onFinally instanceof Runnable) {
                        ((Runnable) onFinally).run();
                    } else if (onFinally instanceof Supplier) {
                        ((Supplier<?>) onFinally).get();
                    } else if (onFinally instanceof Function) {
                        ((Function<Object, Object>) onFinally).apply(value);
                    } else if (onFinally instanceof IEvalFunction) {
                        ((IEvalFunction) onFinally).call0(null, scope);
                    }
                } catch (Throwable e) {
                    // ignore
                }
                if (err == null) {
                    next.complete(value);
                } else {
                    next.completeExceptionally(err);
                }
            });
        });
        return next;
    }

    public static class Rejected extends RuntimeException {
        private final Object reason;

        public Rejected(Object reason) {
            super(String.valueOf(reason));
            this.reason = reason;
        }

        public Object getReason() {
            return reason;
        }
    }

    @SuppressWarnings("unchecked")
    private static BiFunction<Object, Throwable, Object> asFunction(IEvalScope scope, Object x) {
        if (x == null)
            return (v, e) -> v;
        if (x instanceof BiFunction) {
            return (BiFunction<Object, Throwable, Object>) x;
        }
        if (x instanceof Function) {
            Function<Object, Object> f = (Function<Object, Object>) x;
            return (v, e) -> f.apply(v);
        }
        if (x instanceof IEvalFunction) {
            IEvalFunction fn = (IEvalFunction) x;
            return (v, e) -> fn.call1(null, v, scope);
        }
        if (x instanceof Runnable) {
            return (v, e) -> {
                ((Runnable) x).run();
                return v;
            };
        }
        throw new IllegalArgumentException("Unsupported callback type: " + x.getClass());
    }
}