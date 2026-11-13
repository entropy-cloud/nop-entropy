/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.js.engine;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.io.stream.LogOutputStream;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResourceTextLoader;
import io.nop.js.JsConstants;
import io.nop.js.exceptions.ScriptError;
import io.nop.js.fs.ResourceFileSystem;
import io.nop.js.fs.ScriptLoader;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.nop.js.JsErrors.ARG_MAX_WAITING_TASKS;
import static io.nop.js.JsErrors.ERR_JS_CONTEXT_ALREADY_CLOSED;
import static io.nop.js.JsErrors.ERR_JS_ERROR;
import static io.nop.js.JsErrors.ERR_JS_WORKER_QUEUE_FULL;

/**
 * 单线程执行任务队列中的任务
 */
public class JavaScriptWorker implements Runnable, AutoCloseable {
    static final Logger LOG = LoggerFactory.getLogger(JavaScriptWorker.class);

    /**
     * 每个 worker 等待任务数的准入软上界（CRA 8.40 W-01 失效层③）：execute 准入检查拒绝超量提交，
     * 封住失控异步提交者的无界积压。物理队列维持 JDK 无界 LinkedBlockingQueue，close 的 pill 直送
     * 队列、永不经准入检查（close 语义零劣化）。软上界的越界幅度至多为并发提交者数，非精确配额。
     */
    public static final int MAX_WAITING_TASKS = 1000;

    private final BlockingQueue<Consumer<Boolean>> taskQueue;
    private final LongAdder completedTasks = new LongAdder();
    private volatile boolean closed;
    private volatile boolean shutdownGracefully;
    private volatile boolean processing;
    private String initScriptPath;

    private Context context;
    private Value globals;
    private ResourceFileSystem fileSystem;
    private IResourceTextLoader jsLibLoader;
    private LogOutputStream outStream;
    private LogOutputStream errStream;

    private volatile Thread executeThread;

    public JavaScriptWorker() {
        this(new LinkedBlockingQueue<>());
    }

    /**
     * 队列注入构造缝（CRA 8.40 测试缝 S1，包内可见）：测试以闩控队列在提交线程首次入队前阻塞，
     * 确定性构造「终局排空后入队」的搁浅窗口。生产路径走无参构造器。
     */
    JavaScriptWorker(BlockingQueue<Consumer<Boolean>> taskQueue) {
        this.taskQueue = taskQueue;
    }

    public Thread getExecuteThread() {
        return executeThread;
    }

    public boolean isProcessing() {
        return processing;
    }

    public void setJsLibLoader(IResourceTextLoader jsLibLoader) {
        this.jsLibLoader = jsLibLoader;
    }

    public void setInitScriptPath(String initScriptPath) {
        this.initScriptPath = initScriptPath;
    }

    public int getWaitingTasks() {
        return taskQueue.size();
    }

    public long getCompletedTasks() {
        return completedTasks.longValue();
    }

    public boolean isClosed() {
        return closed;
    }

    public void execute(Consumer<Boolean> task) {
        if (closed)
            throw new NopException(ERR_JS_CONTEXT_ALREADY_CLOSED);

        // 准入软上界（W-01 失效层③）：达界同步拒绝，物理队列不受界（close pill 直送永不经此检查）
        if (taskQueue.size() >= MAX_WAITING_TASKS)
            throw new NopException(ERR_JS_WORKER_QUEUE_FULL).param(ARG_MAX_WAITING_TASKS, MAX_WAITING_TASKS);

        taskQueue.add(task);

        // 入队后复查（W-01 失效层①，Dekker 闭合）：若 worker 已在本线程入队与复查之间完成终局序列
        // （closed=true 先于排空 poll），复查必见 true——
        // * 同引用移除成功：任务从未对消费方可见，按前置检查同码同步拒绝（调用方不可区分，有意为之）；
        // * 移除失败：任务已被消费方取走（run 循环或终局排空），由 invokeFunction 读字段走
        //   CancellationException 交付（或先于关闭被正常执行）。
        // 两分支覆盖全部交错，无残窗。
        if (closed) {
            if (taskQueue.remove(task))
                throw new NopException(ERR_JS_CONTEXT_ALREADY_CLOSED);
        }
    }

    public void shutdownGracefully() {
        this.shutdownGracefully = true;
    }

    @Override
    public void close() {
        if (closed)
            return;

        closed = true;
        taskQueue.add(b -> {
            IoHelper.safeClose(context);
            context = null;
        });
    }

    public CompletableFuture<Object> schedule(String funcName, Object... args) {
        CompletableFuture<Object> future = new CompletableFuture<>();

        Consumer<Boolean> task = closed -> {
            invokeFunction(funcName, args, future);
        };
        execute(task);

        return future;
    }

    public CompletableFuture<Object> invokeFunction(String funcName, Object... args) {
        CompletableFuture<Object> future = new CompletableFuture<>();
        invokeFunction(funcName, args, future);
        return future;
    }

    private void invokeFunction(String funcName, Object[] args, CompletableFuture<Object> future) {
        if (closed) {
            future.completeExceptionally(new CancellationException("closed"));
        } else {
            if (funcName.equals(JsConstants.FUNC_REINIT)) {
                reinit(future);
            } else {
                long beginTime = CoreMetrics.currentTimeMillis();
                try {
                    Value ret = globals.invokeMember(funcName, args);
                    asyncReturn(future, ret);
                } catch (Exception e) {
                    LOG.debug("nop.js.invoke-func-error:funcName={},args={}", funcName, args, e);
                    future.completeExceptionally(e);
                } finally {
                    outStream.flush();
                    LOG.debug("nop.js.invoke-func:funcName={},usedTime={}", funcName, CoreMetrics.currentTimeMillis() - beginTime);
                }
            }
        }
    }

    /**
     * FUNC_REINIT = build-then-swap（CRA 8.40 CL-201）：先完整构建新执行单元（context + globals +
     * 构建副产物），成功后一次性换绑全部字段并关闭旧 context，future 正常完成；构建失败则关闭半成品
     * 新 context、旧单元全部字段保持不变且可继续服务，future 以失败原因异常完成——禁止
     * 「先关旧再建新」的半状态（旧 init 失败后 globals 悬挂指向已关闭 context），也禁止 reinit
     * future 永不完成。
     */
    private void reinit(CompletableFuture<Object> future) {
        try {
            ContextUnit unit = buildContextUnit();
            Context old = this.context;
            this.context = unit.context;
            this.globals = unit.globals;
            this.fileSystem = unit.fileSystem;
            this.outStream = unit.outStream;
            this.errStream = unit.errStream;
            IoHelper.safeClose(old);
            future.complete(null);
        } catch (Exception e) {
            LOG.error("nop.js.reinit-fail", e);
            future.completeExceptionally(e);
        }
    }

    private void asyncReturn(CompletableFuture<Object> future, Value value) {
        if (value.canInvokeMember("then")) {
            Consumer<Object> fnResolve = v -> {
                complete(future, v);
            };
            Consumer<Object> fnReject = e -> {
                reject(future, e);
            };
            value.invokeMember("then", fnResolve);
            value.invokeMember("catch", fnReject);
        } else if (value.isException()) {
            try {
                future.completeExceptionally(value.throwException());
            } catch (Exception err) {
                future.completeExceptionally(err);
            }
        } else {
            future.complete(toJavaValue(value));
        }
    }

    private void complete(CompletableFuture<Object> future, Object v) {
        if (v instanceof Value) {
            asyncReturn(future, (Value) v);
        } else {
            future.complete(v);
        }
    }

    private void reject(CompletableFuture<Object> future, Object e) {
        Throwable error = null;
        if (e instanceof Throwable) {
            error = (Throwable) e;
        } else if (e instanceof Value) {
            Value v = (Value) e;
            if (v.isHostObject()) {
                Object err = v.asHostObject();
                if (err instanceof Throwable) {
                    error = (Throwable) err;
                } else {
                    error = new IllegalStateException(e.toString());
                }
            } else if (v.isException()) {
                try {
                    error = v.throwException();
                } catch (Exception e2) {
                    error = e2;
                }
            }
        }
        if (error == null) {
            if (e instanceof Map) {
                LOG.error("nop.js.execute-fail:{}", e);

                Map<String, Object> map = (Map<String, Object>) e;
                error = buildException(map);
            }
            if (error == null)
                error = new IllegalStateException(e.toString());
        }

        LOG.error("nop.js.exec-fail", error);
        future.completeExceptionally(error);
    }

    protected Exception buildException(Map<String, Object> map) {
        return new NopException(ERR_JS_ERROR).params(map);

    }

    private Object toJavaValue(Value value) {
        if (value.isBoolean()) {
            return value.asBoolean();
        } else if (value.isString()) {
            return value.asString();
        } else if (value.isNull()) {
            return null;
        } else if (value.isHostObject()) {
            return value.asHostObject();
        } else if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            } else if (value.fitsInLong()) {
                return value.asLong();
            } else {
                return value.asDouble();
            }
        } else if (value.isException()) {
            try {
                return value.throwException();
            } catch (Exception err) {
                return err;
            }
        } else if (value.isDate()) {
            return value.asDate();
        } else if (value.isDuration()) {
            return value.isDuration();
        } else if (value.isInstant()) {
            return value.asDuration();
        } else if (value.isTime()) {
            return value.asTime();
        } else if (value.isTimeZone()) {
            return value.asTimeZone();
        } else if (value.hasArrayElements()) {
            int size = (int) value.getArraySize();
            List<Object> ret = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ret.add(toJavaValue(value.getArrayElement(i)));
            }
            return ret;
        } else {
            return value.as(Object.class);
        }
    }

    @Override
    public void run() {
        try {
            this.executeThread = Thread.currentThread();
            LOG.info("nop.js.worker-start");
            this.init();

            do {
                try {
                    Consumer<Boolean> task = taskQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        try {
                            this.processing = true;
                            task.accept(closed);
                        } catch (Exception e) {
                            LOG.error("nop.js.run-task-fail", e);
                        } finally {
                            this.processing = false;
                        }
                        completedTasks.increment();
                    } else {
                        // 如果没有任务需要执行，且已经标记待关闭，则自动关闭
                        if (shutdownGracefully) {
                            closed = true;
                            break;
                        }
                    }
                } catch (InterruptedException e) { //NOPMD
                    Thread.currentThread().interrupt();
                    throw NopException.adapt(e);
                }

            } while (!closed);
        } finally {
            closed = true;
            cancelAllTasks();
            IoHelper.safeClose(this.context);
            LOG.info("nop.js.worker-stop");
            this.executeThread = null;
        }
    }

    private void init() {
        ContextUnit unit = buildContextUnit();
        this.context = unit.context;
        this.globals = unit.globals;
        this.fileSystem = unit.fileSystem;
        this.outStream = unit.outStream;
        this.errStream = unit.errStream;
    }

    /**
     * 重建原子单元（CRA 8.40 CL-201）：完整构建 context/globals 及其副产物且不发布任何实例字段；
     * 构建中途失败时自负责关闭半成品 context 后原样上抛。
     */
    private ContextUnit buildContextUnit() {
        ResourceFileSystem newFileSystem = new ResourceFileSystem();
        newFileSystem.setCurrentPath(initScriptPath);
        LogOutputStream newOut = new LogOutputStream(LOG, false);
        LogOutputStream newErr = new LogOutputStream(LOG, true);
        Context newContext = Context.newBuilder("js").allowCreateProcess(false)
                .allowCreateThread(false).allowHostClassLoading(true)
                .allowHostAccess(HostAccess.ALL).allowIO(true).fileSystem(newFileSystem)
                .allowHostClassLookup(className -> true)
                .logHandler(new SLF4JBridgeHandler())
                .out(newOut).err(newErr)
                .allowExperimentalOptions(true)
                .option("js.esm-eval-returns-exports", "true")
                .option("js.console", "true")
                .option("js.interop-complete-promises", "false")
                .option("js.unhandled-rejections", "warn")
                // .option("js.function-cache-limit", "4096")
                // .option("js.function-constructor-cache-size", "4096")
                .build();
        try {
            Value bindings = newContext.getBindings("js");
            registerGlobals(bindings);

            String text = ScriptLoader.loadScript(initScriptPath);
            Source source = Source.newBuilder("js", text, initScriptPath)
                    .mimeType("application/javascript+module").build();
            long beginTime = CoreMetrics.currentTimeMillis();
            Value newGlobals = newContext.eval(source);
            Set<String> keys = newGlobals.getMemberKeys();
            LOG.info("nop.js.init-globals:usedTime={},names={}",
                    CoreMetrics.currentTimeMillis() - beginTime, keys);
            return new ContextUnit(newContext, newGlobals, newFileSystem, newOut, newErr);
        } catch (Exception e) {
            LOG.error("nop.js.run-init-script-fail:path={}", initScriptPath, e);
            IoHelper.safeClose(newContext);
            throw NopException.adapt(e);
        }
    }

    static final class ContextUnit {
        final Context context;
        final Value globals;
        final ResourceFileSystem fileSystem;
        final LogOutputStream outStream;
        final LogOutputStream errStream;

        ContextUnit(Context context, Value globals, ResourceFileSystem fileSystem,
                    LogOutputStream outStream, LogOutputStream errStream) {
            this.context = context;
            this.globals = globals;
            this.fileSystem = fileSystem;
            this.outStream = outStream;
            this.errStream = errStream;
        }
    }

    private void cancelAllTasks() {
        do {
            Consumer<Boolean> task = taskQueue.poll();
            if (task == null)
                break;

            try {
                task.accept(true);
            } catch (Exception e) {
                LOG.error("nop.err.js.cancel-task-fail", e);
            }
        } while (true);
    }

    private void registerGlobals(Value bindings) {
        Value promiseConstructor = bindings.getMember("Promise");
        bindings.putMember(JsConstants.VAR_JAVA_WORKER, this);

        Function<Object, Object> asPromise = future -> wrapPromise(
                promiseConstructor, FutureHelper.toCompletionStage(future));
        bindings.putMember(JsConstants.VAR_TO_JS_PROMISE, asPromise);

        Function<Object, Object> toJsMap = m -> ProxyHashMap.from((Map<Object, Object>) m);
        bindings.putMember(JsConstants.VAR_TO_JS_MAP, toJsMap);

        bindings.putMember(JsConstants.VAR_JS_LIB_LOADER, (Function<String, Object>) path -> {
            try {
                return jsLibLoader.loadText(path);
            } catch (NopException ex) {
                throw new ScriptError(ex);
            } catch (Exception e) {
                throw new ScriptError(ERR_JS_ERROR, e);
            }
        });

        Function<Object, Object> toJsArray = o -> {
            if (o instanceof Object[]) {
                return ProxyArray.fromArray((Object[]) o);
            } else {
                return ProxyArray.fromList((List<Object>) o);
            }
        };
        bindings.putMember(JsConstants.VAR_TO_JS_ARRAY, toJsArray);
    }

    static Value wrapPromise(Value promiseConstructor, CompletionStage<Object> javaFuture) {
        return promiseConstructor.newInstance((ProxyExecutable) arguments -> {
            Value resolve = arguments[0];
            Value reject = arguments[1];
            javaFuture.whenComplete((result, ex) -> {
                if (result != null) {
                    resolve.execute(result);
                } else {
                    reject.execute(ex);
                }
            });
            // return value of function(resolve,reject) is ignored by `new Promise()`.
            return null;
        });
    }
}