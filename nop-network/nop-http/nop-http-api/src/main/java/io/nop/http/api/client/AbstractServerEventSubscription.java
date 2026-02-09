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
        subscriber.onComplete();
    }

    protected void processLine(String line) {
        // 心跳
        if (line.startsWith(":"))
            return;

        if (line.startsWith("data:")) {
            String value = line.substring("data:".length());
            if (data == null) {
                data = value;
            } else {
                data = data + "\n" + value;
            }
            return;
        }

        if (data != null) {
            waitForDemand();
            subscriber.onNext(newServerEvent(id, event, data));
            id = null;
            event = null;
            data = null;
        }

        if (line.startsWith("id:")) {
            id = line.substring("id:".length()).trim();
        } else if (line.startsWith("event:")) {
            event = line.substring("event:".length()).trim();
        } else if (!line.isEmpty()) {
            waitForDemand();
            subscriber.onNext(newServerEvent(id, event, line));
            id = null;
            event = null;
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
