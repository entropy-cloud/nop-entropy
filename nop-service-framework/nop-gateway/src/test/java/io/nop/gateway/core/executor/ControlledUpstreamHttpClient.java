package io.nop.gateway.core.executor;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventAggregator;
import io.nop.http.api.client.UploadOptions;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * C8-002 独立可控桩（plan 2508 裁定③：测试缝唯一许可缝）：
 * <ul>
 *   <li>可控挂起 future —— fetchAsync 返回未完成 future，由测试显式驱动完成时序；</li>
 *   <li>Atomic 在途计数 —— inflight/maxInflight 支撑「在途 ≤ permits（含挂起窗）」断言；</li>
 *   <li>请求录制 —— requests/callNanos/totalCalls 支撑零上游调用与重试计数断言。</li>
 * </ul>
 * 不触碰既有 RecordingHttpClient（AiFailoverGatewayInterceptorTest 的 private static final
 * 内部类，不可继承，见裁定③零回退约束）。
 */
class ControlledUpstreamHttpClient implements IHttpClient {

    final AtomicInteger totalCalls = new AtomicInteger();
    final AtomicInteger inflight = new AtomicInteger();
    final AtomicInteger maxInflight = new AtomicInteger();
    final List<HttpRequest> requests = new CopyOnWriteArrayList<>();
    final List<Long> callNanos = new CopyOnWriteArrayList<>();

    private final Queue<CompletableFuture<IHttpResponse>> pending = new ConcurrentLinkedQueue<>();

    private volatile int defaultStatus = 200;
    private volatile Map<String, String> defaultHeaders = Map.of();
    private volatile String defaultBody = "";

    void setDefaultResponse(int status, Map<String, String> headers, String body) {
        this.defaultStatus = status;
        this.defaultHeaders = headers;
        this.defaultBody = body;
    }

    int pendingCount() {
        return pending.size();
    }

    /** 等待挂起请求数达到 n（有界观察窗，防挂死） */
    boolean awaitPending(int n, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (pending.size() >= n) {
                return true;
            }
            Thread.sleep(5);
        }
        return pending.size() >= n;
    }

    /** 用默认响应完成最早一个挂起请求；无挂起时返回 false */
    boolean completeOnePending() {
        CompletableFuture<IHttpResponse> f = pending.poll();
        if (f == null) {
            return false;
        }
        f.complete(response(defaultStatus, defaultHeaders, defaultBody));
        return true;
    }

    /** 用显式响应完成最早一个挂起请求 */
    boolean completeOnePending(int status, Map<String, String> headers, String body) {
        CompletableFuture<IHttpResponse> f = pending.poll();
        if (f == null) {
            return false;
        }
        f.complete(response(status, headers, body));
        return true;
    }

    /** 用默认响应完成全部挂起请求（清理挂起腿，使许可归还） */
    void completeAllPending() {
        while (completeOnePending()) {
            // drain
        }
    }

    static IHttpResponse response(int status, Map<String, String> headers, String body) {
        return new StubHttpResponse(status, headers, body);
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest req, ICancelToken ct) {
        callNanos.add(System.nanoTime());
        requests.add(req);
        totalCalls.incrementAndGet();
        int now = inflight.incrementAndGet();
        maxInflight.accumulateAndGet(now, Math::max);
        CompletableFuture<IHttpResponse> future = new CompletableFuture<>();
        pending.add(future);
        future.whenComplete((r, e) -> {
            inflight.decrementAndGet();
            pending.remove(future);
        });
        return future;
    }

    @Override
    public CompletionStage<IHttpResponse> fetchStreamAsync(HttpRequest req, IServerEventAggregator agg,
                                                           ICancelToken ct) {
        throw new UnsupportedOperationException("not used by admission tests");
    }

    @Override
    public CompletionStage<IHttpResponse> downloadAsync(HttpRequest req, IHttpOutputFile target,
                                                        DownloadOptions options, ICancelToken ct) {
        throw new UnsupportedOperationException("not used by admission tests");
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest req, IHttpInputFile file,
                                                      UploadOptions options, ICancelToken ct) {
        throw new UnsupportedOperationException("not used by admission tests");
    }

    /** 最小 IHttpResponse 实现（状态码 + 头 + body） */
    static final class StubHttpResponse implements IHttpResponse {
        private final int status;
        private final Map<String, String> headers;
        private final String body;

        StubHttpResponse(int status, Map<String, String> headers, String body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        @Override public int getHttpStatus() {
            return status;
        }

        @Override public Map<String, String> getHeaders() {
            return headers;
        }

        @Override public String getBody() {
            return body;
        }

        @Override public <T> T getBodyAsBean(Class<T> cl) {
            return null;
        }

        @Override public String getBodyAsString() {
            return body;
        }

        @Override public byte[] getBodyAsBytes() {
            return body == null ? new byte[0] : body.getBytes();
        }

        @Override public String getContentType() {
            return "application/json";
        }

        @Override public String getCharset() {
            return "UTF-8";
        }
    }
}
