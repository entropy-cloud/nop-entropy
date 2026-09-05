package io.nop.http.api.client;

import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

public abstract class AbstractServerEventSubscription implements Flow.Subscription {
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
        if (data != null) {
            if (!isCancelled) {
                waitForDemand();
                subscriber.onNext(newServerEvent(id, event, data));
            }
            id = null;
            event = null;
            data = null;
        }
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

    protected synchronized void waitForDemand() {
        while (demand <= 0 && !isCancelled) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cleanup(null);
            }
        }
        demand--;
    }

    public synchronized void cleanup(String reason) {
        isCancelled = true;
        if (future != null) {
            future.cancel(true);
        }
        notifyAll();
    }
}
