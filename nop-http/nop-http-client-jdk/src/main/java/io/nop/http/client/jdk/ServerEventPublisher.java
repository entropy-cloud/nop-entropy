package io.nop.http.client.jdk;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.http.api.client.DefaultServerEventResponse;
import io.nop.http.api.client.IServerEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

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
    protected class ServerEventSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super IServerEventResponse> subscriber;

        private volatile boolean isCancelled;
        private long demand;
        private CompletableFuture<HttpResponse<InputStream>> future;

        private Consumer<String> onCancel = this::cleanup;

        ServerEventSubscription(
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
                    startRequest();
                } else {
                    notifyAll();
                }
            }
        }

        @Override
        public void cancel() {
            cleanup(null);
        }

        private void startRequest() {
            // 注册取消回调
            if (cancelToken != null)
                cancelToken.appendOnCancel(onCancel);

            // 发送异步HTTP请求
            future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

            future.whenComplete((response, ex) -> {
                if (ex != null) {
                    subscriber.onError(wrapException(ex));
                    return;
                }

                processResponse(response);
            });
        }

        private void processResponse(HttpResponse<InputStream> response) {
            int status = response.statusCode();
            Map<String, String> headers = JdkHttpClientHelper.getHeaders(response.headers());
            InputStream in = response.body();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            executor().execute(() -> {
                if (isCancelled)
                    return;

                try {
                    DefaultServerEventResponse ret = new DefaultServerEventResponse();
                    ret.setHttpStatus(status);
                    ret.setHeaders(headers);
                    waitForDemand();
                    if (isCancelled) return;

                    subscriber.onNext(ret);

                    parseEvents(status, headers, reader, this);

                    if (isCancelled)
                        return;

                    subscriber.onComplete();
                } catch (Exception e) {
                    if (!isCancelled) subscriber.onError(wrapException(e));
                } finally {
                    if (cancelToken != null)
                        cancelToken.removeOnCancel(onCancel);
                }
            });
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

        private synchronized void cleanup(String reason) {
            isCancelled = true;
            if (future != null) {
                future.cancel(true);
            }
            notifyAll();
        }
    }

    protected void parseEvents(int status, Map<String, String> headers, BufferedReader reader,
                               ServerEventSubscription subscription) throws IOException {
        Flow.Subscriber<? super IServerEventResponse> subscriber = subscription.subscriber;

        String event = null;
        StringBuilder data = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                if (data.length() > 0) {

                    subscription.waitForDemand();
                    if (subscription.isCancelled())
                        return;

                    subscriber.onNext(newResponse(status, headers, event, data.toString()));
                    event = null;
                    data.setLength(0);
                }
                continue;
            }

            if (line.startsWith("event:")) {
                event = line.substring("event:".length()).trim();
            } else if (line.startsWith("data:")) {
                String content = line.substring("data:".length());
                if (data.length() > 0) {
                    data.append("\n");
                }
                data.append(content.trim());
            } else {
                subscription.waitForDemand();
                if (subscription.isCancelled())
                    return;

                subscriber.onNext(newResponse(status, headers, event, line));
            }
        }

        if (data.length() > 0) {
            subscription.waitForDemand();
            if (subscription.isCancelled())
                return;
            subscriber.onNext(newResponse(status, headers, event, data.toString()));
        }
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

    protected DefaultServerEventResponse newResponse(int status, Map<String, String> headers,
                                                     String event, String data) {
        DefaultServerEventResponse ret = new DefaultServerEventResponse();
        ret.setHttpStatus(status);
        ret.setHeaders(headers);
        ret.setEvent(event);
        ret.setData(data);
        return ret;
    }
}
