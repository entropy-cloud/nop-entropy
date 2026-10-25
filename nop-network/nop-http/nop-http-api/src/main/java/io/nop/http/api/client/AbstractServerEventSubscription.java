package io.nop.http.api.client;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;

import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

import static io.nop.http.api.HttpApiErrors.ARG_TIMEOUT;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_SSE_DEMAND_TIMEOUT;

public abstract class AbstractServerEventSubscription implements Flow.Subscription {
    /**
     * 4.3-C6-004 面①（plan 2505 PA 裁定，设计权威来源
     * ai-dev/design/concurrency/sse-backpressure-and-body-phase-lifecycle.md §3.1）：
     * demand 等待的有界预算缺省值。SSE 长静默（&gt;30s）为常态，预算取 120s 避免
     * 误杀合法空闲流；叶/测试可经 {@link #demandWaitTimeoutMillis()} 收紧。
     */
    protected static final long DEFAULT_DEMAND_WAIT_TIMEOUT_MILLIS = 120_000L;

    private final Flow.Subscriber<? super IServerEventResponse> subscriber;

    private volatile boolean isCancelled;
    private long demand;
    private Future<?> future;

    private int httpStatus;
    private Map<String, String> headers;

    private String id;
    private String event;
    private String data;
    private volatile boolean completed;

    public AbstractServerEventSubscription(
            Flow.Subscriber<? super IServerEventResponse> subscriber
    ) {
        this.subscriber = subscriber;
        this.isCancelled = false;
        this.demand = 0;
    }

    /**
     * demand 等待停滞预算（毫秒）。缺省 {@link #DEFAULT_DEMAND_WAIT_TIMEOUT_MILLIS}；
     * 叶实现可覆盖收紧（如测试构造短预算）。
     */
    protected long demandWaitTimeoutMillis() {
        return DEFAULT_DEMAND_WAIT_TIMEOUT_MILLIS;
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            subscriber.onError(new IllegalArgumentException("request count must be positive"));
            return;
        }

        synchronized (this) {
            demand += n;
            if (future == null) {
                future = startRequest();
            } else {
                notifyAll();
            }
        }
    }

    @Override
    public void cancel() {
        cleanup(null);
    }

    protected abstract Future<?> startRequest();

    protected void onStart(int status, Map<String, String> headers) {
        this.httpStatus = status;
        this.headers = headers;
    }

    protected void onError(Exception e) {
        subscriber.onError(e);
    }

    protected void onComplete() {
        if (completed)
            return;
        completed = true;
        // 流结束时仍可能有未派发的累积数据
        dispatchEvent();
        subscriber.onComplete();
    }

    private String getValue(String line, String prefix) {
        int start = prefix.length();
        if (line.length() > start && line.charAt(start) == ' ')
            start++;
        return line.substring(start);
    }

    /**
     * 按 SSE 规范：字段累积到空行时才派发一个事件，未知字段名忽略。
     */
    protected void processLine(String line) {
        if (line.isEmpty()) {
            dispatchEvent();
            return;
        }

        if (line.startsWith(":"))
            return;

        if (line.startsWith("data:")) {
            String value = getValue(line, "data:");
            if ("[DONE]".equals(value)) {
                dispatchEvent();
                onComplete();
                return;
            }
            if (data == null) {
                data = value;
            } else {
                data = data + "\n" + value;
            }
            return;
        }

        if (line.startsWith("id:")) {
            id = getValue(line, "id:");
            return;
        }

        if (line.startsWith("event:")) {
            event = getValue(line, "event:");
        }
        // 其他字段（如 retry:）按规范忽略
    }

    private void dispatchEvent() {
        if (data == null)
            return;
        boolean stalled = false;
        if (!isCancelled) {
            if (waitForDemand()) {
                subscriber.onNext(newServerEvent(id, event, data));
            } else if (!isCancelled) {
                // 预算耗尽且未被取消 = 订阅者停滞终态（4.3-C6-004 面①）
                stalled = true;
            }
        }
        id = null;
        event = null;
        data = null;
        if (stalled)
            onDemandStall();
    }

    /**
     * 4.3-C6-004 面①：停滞订阅者终态化——先 cleanup（置位取消、唤醒等待者、释放承载资源，
     * jdk 叶经 cleanup 覆盖关闭底层流），再向订阅者派发显式错误（R21 纪律：异常终态非静默）。
     */
    private void onDemandStall() {
        cleanup(null);
        NopException err = new NopException(ERR_HTTP_SSE_DEMAND_TIMEOUT)
                .param(ARG_TIMEOUT, demandWaitTimeoutMillis());
        onError(err);
    }

    protected DefaultServerEventResponse newServerEvent(String id, String event, String data) {
        DefaultServerEventResponse ret = new DefaultServerEventResponse();
        ret.setHttpStatus(httpStatus);
        ret.setHeaders(headers);
        ret.setId(id);
        ret.setEvent(event);
        ret.setData(data);
        return ret;
    }

    protected boolean isCancelled() {
        return isCancelled;
    }

    /**
     * 4.3-C6-004 面①：有界 demand 等待——demand 到位消耗一个 demand 返回 true；
     * 取消返回 false（不消耗 demand，不产生停滞错误）；预算耗尽（订阅者停滞）返回 false。
     * 修复前为无限期 {@code wait()}，仅 cancel/notify 可唤醒，停滞订阅者会永久挂起承载线程。
     */
    protected boolean waitForDemand() {
        long deadline = CoreMetrics.currentTimeMillis() + demandWaitTimeoutMillis();
        synchronized (this) {
            while (demand <= 0 && !isCancelled) {
                long remaining = deadline - CoreMetrics.currentTimeMillis();
                if (remaining <= 0)
                    return false;
                try {
                    wait(Math.min(remaining, 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cleanup(null);
                }
            }
            if (demand <= 0)
                return false;
            demand--;
            return true;
        }
    }

    public synchronized void cleanup(String reason) {
        isCancelled = true;
        if (future != null) {
            future.cancel(true);
        }
        notifyAll();
    }
}
