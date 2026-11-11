package io.nop.http.client.jdk;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.api.core.util.ResolvedPromise;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.IoHelper;
import io.nop.http.api.client.AbstractServerEventSubscription;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.utils.HttpHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

import static io.nop.http.api.HttpApiErrors.ARG_BODY;
import static io.nop.http.api.HttpApiErrors.ARG_EXCEPTION;
import static io.nop.http.api.HttpApiErrors.ARG_HTTP_STATUS;
import static io.nop.http.api.HttpApiErrors.ARG_RESPONSE_HEADERS;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_RESPONSE_ERROR;

public class ServerEventPublisher implements Flow.Publisher<IServerEventResponse> {
    static final Logger LOG = LoggerFactory.getLogger(ServerEventPublisher.class);

    private final HttpClient client;
    private final HttpRequest request;
    private final ICancelToken cancelToken;

    public ServerEventPublisher(HttpClient client, HttpRequest request, ICancelToken cancelToken) {
        this.client = client;
        this.request = request;
        this.cancelToken = cancelToken;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super IServerEventResponse> subscriber) {
        ServerEventSubscription subscription = new ServerEventSubscription(
                subscriber
        );
        subscriber.onSubscribe(subscription);
    }

    protected Executor executor() {
        return client.executor().orElseGet(GlobalExecutors::globalWorker);
    }

    // 内部Subscription实现
    protected class ServerEventSubscription extends AbstractServerEventSubscription {

        /**
         * 4.3-C3-003（plan 2505）：已登记的底层 body 流。cleanup 时显式关闭——
         * {@code future.cancel(true)} 对 headers 后已完成的 sendAsync future 是 no-op，
         * 显式关流是取消能解除 readLine 阻塞的唯一出口（基类 isCancelled 仅约束行间检查）。
         */
        private volatile InputStream bodyIn;

        /**
         * 4.3-C3-002/CL-026：body 阶段 idle 读看门狗（parse 期间逐行推进进度）。
         */
        private volatile BodyIdleWatchdog watchdog;

        ServerEventSubscription(
                Flow.Subscriber<? super IServerEventResponse> subscriber
        ) {
            super(subscriber);
        }

        @Override
        public synchronized void cleanup(String reason) {
            // 先基类置位取消（等待者被唤醒、行间检查可见），再关闭底层流——
            // 阻塞中的 readLine 抛 IOException，解析任务按取消契约静默退出（无补发终态义务）
            super.cleanup(reason);
            IoHelper.safeClose(bodyIn);
        }

        @Override
        protected Future<?> startRequest() {
            // 发送异步HTTP请求
            CompletableFuture<HttpResponse<InputStream>> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

            // 4.3-C3-003（plan 2505）：promise 不再等价于「headers 已到达」——它在 body 阶段
            // 结束（parse 任务终态）时完结。bindCancelToken 的监听器生命周期因此覆盖整个
            // body 阶段：headers 后的取消仍能命中 cleanup（显式关闭底层流），修复
            // 「future.cancel 对已完成 future 为 no-op」的失效形态；监听器在 body 终态按
            // promise 配对移除，维持 C3-012 无累积契约。
            CompletableFuture<?> promise = new CompletableFuture<>();
            future.whenComplete((response, ex) -> {
                if (ex != null) {
                    // 4.3-C3-033（plan 2561 裁定 I）④：取消不再派发假 onError——cleanup
                    // （取消/停滞终态/[DONE] 终态）经 future.cancel 触发本回调，取消是调用方
                    // 主动终态而非传输错误。JDK 侧取消形态两种都出现：裸 CancellationException
                    // 或 CompletionException 包裹（sendAsync 内部链完成），故解开包裹判定；
                    // isCancelled() 兜底覆盖「取消后到达的其他异常」——取消/终态后不再派发
                    // 任何信号（与 body 阶段 {@code if (!isCancelled()) onError} 对齐）。
                    // promise 仍须完结以保持 C3-012 取消监听器配对移除。
                    if (isCancellation(ex) || isCancelled()) {
                        promise.complete(null);
                        return;
                    }
                    promise.completeExceptionally(ex);
                    onError(wrapException(ex));
                    return;
                }

                try {
                    processResponse(response, promise);
                } catch (Exception e) {
                    // 非成功响应（如非 2xx）由 processResponse 抛出；同步路径的异常
                    // 必须路由到 onError，否则异常会被 whenComplete 吞掉（订阅者
                    // 既收不到 onError 也收不到 onComplete）。executor 块内的异常
                    // 自带 try/catch→onError，不会走到这里。
                    promise.completeExceptionally(e);
                    onError(wrapException(e));
                }
            });

            FutureHelper.bindCancelToken(cancelToken, this::cleanup, promise);
            return future;
        }

        private void processResponse(HttpResponse<InputStream> response, CompletableFuture<?> promise) {
            int status = response.statusCode();
            Map<String, String> headers = JdkHttpClientHelper.getHeaders(response.headers());
            InputStream in = response.body();
            // 4.3-C3-003：从本点起登记底层流，cleanup（取消/停滞终态）可显式关闭
            this.bodyIn = in;

            // SSE 规范要求 UTF-8 编码，不依赖平台默认字符集
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            if (!HttpHelper.isOk(status)) {
                try (reader) {
                    ResolvedPromise<String> result = FutureHelper.safeInvoke(() -> IoHelper.readText(reader));
                    throw new NopException(ERR_HTTP_RESPONSE_ERROR)
                            .param(ARG_HTTP_STATUS, status)
                            .param(ARG_BODY, result.getResult())
                            .param(ARG_RESPONSE_HEADERS, headers)
                            .param(ARG_EXCEPTION, result.getException());
                } catch (IOException e) {
                    throw new NopException(ERR_HTTP_RESPONSE_ERROR)
                            .param(ARG_HTTP_STATUS, status)
                            .param(ARG_RESPONSE_HEADERS, headers)
                            .param(ARG_EXCEPTION, e);
                }
            }

            executor().execute(() -> {
                if (isCancelled()) {
                    // 4.3-C6-002②（plan 2505）：cancel-at-entry 早退分支必须关闭遗弃的响应体
                    // （连接/socket 归还），不得静默 return 遗弃流
                    IoHelper.safeClose(in);
                    return;
                }

                // 4.3-C3-002/CL-026：body 阶段 idle 读看门狗——静默流上的 readLine
                // 不再无限阻塞（预算独立于 headers 阶段 request timeout）
                watchdog = BodyIdleWatchdog.start(in, cancelToken,
                        JdkHttpConfigs.CFG_HTTP_BODY_IDLE_TIMEOUT.get());

                try (reader) {
                    onStart(status, headers);

                    parseEvents(reader);

                    if (isCancelled())
                        return;

                    onComplete();
                } catch (Exception e) {
                    if (!isCancelled()) onError(wrapException(e));
                } finally {
                    if (watchdog != null)
                        watchdog.stop();
                    // body 阶段终态：完结 promise（bindCancelToken 按 promise 配对移除取消监听器）
                    promise.complete(null);
                }
            });
        }

        protected void parseEvents(BufferedReader reader) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                if (watchdog != null)
                    watchdog.progress();
                if (isCancelled())
                    return;
                processLine(line);
            }
        }
    }

    /**
     * 4.3-C3-033（plan 2561 裁定 I）④：判定异常是否为取消终态（裸 CancellationException 或
     * 经 CompletionException/ExecutionException 包裹）。
     */
    static boolean isCancellation(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof java.util.concurrent.CancellationException)
                return true;
            cur = (cur instanceof java.util.concurrent.CompletionException
                    || cur instanceof java.util.concurrent.ExecutionException)
                            ? cur.getCause() : null;
        }
        return false;
    }

    protected RuntimeException wrapException(Throwable exp) {
        if (exp instanceof NopException) {
            return (NopException) exp;
        } else {
            RuntimeException e = JdkHttpClientHelper.wrapException(exp);
            NopException.logIfNotTraced(LOG, "nop.err.http.error", e);
            return e;
        }
    }
}
