package io.nop.http.apache;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.DefaultServerEventResponse;
import io.nop.http.api.client.IServerEventResponse;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.config.CharCodingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

import static io.nop.http.api.HttpApiErrors.ARG_BODY;
import static io.nop.http.api.HttpApiErrors.ARG_HTTP_STATUS;
import static io.nop.http.api.HttpApiErrors.ERR_HTTP_RESPONSE_ERROR;

public class ServerEventPublisher implements Flow.Publisher<IServerEventResponse> {
    static final Logger LOG = LoggerFactory.getLogger(ServerEventPublisher.class);

    private final CloseableHttpAsyncClient client;
    private final SimpleHttpRequest request;
    private final HttpClientContext context;
    private final ICancelToken cancelToken;

    public ServerEventPublisher(CloseableHttpAsyncClient client, SimpleHttpRequest request,
                                HttpClientContext context, ICancelToken cancelToken) {
        this.client = client;
        this.request = request;
        this.context = context;
        this.cancelToken = cancelToken;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super IServerEventResponse> subscriber) {
        ServerEventSubscription subscription = new ServerEventSubscription(
                subscriber
        );
        subscriber.onSubscribe(subscription);
    }

    protected class ServerEventSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super IServerEventResponse> subscriber;

        private volatile boolean isCancelled;
        private long demand;
        private Future<SimpleHttpResponse> future;

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
            CompletableFuture<SimpleHttpResponse> promise = new CompletableFuture<>();

            // 发送异步HTTP请求
            future = client.execute(SimpleRequestProducer.create(request), new ServerEventResponseConsumer(this::newServerEventConsumer),
                    context, new FutureCallback<>() {
                        @Override
                        public void completed(SimpleHttpResponse result) {
                            promise.complete(result);
                            subscriber.onComplete();
                        }

                        @Override
                        public void failed(Exception ex) {
                            promise.completeExceptionally(ex);
                            subscriber.onError(ApacheHttpClientHelper.wrapException(ex));
                        }

                        @Override
                        public void cancelled() {
                            promise.cancel(false);
                        }
                    });

            FutureHelper.bindCancelToken(cancelToken, this::cleanup, promise);
        }

        ServerEventConsumer newServerEventConsumer() {
            return new ServerEventConsumer(2048, CharCodingConfig.DEFAULT, 2048);
        }

        class ServerEventConsumer extends LineAsyncDataConsumer {
            private String event = null;

            public ServerEventConsumer(int bufSize, CharCodingConfig charCodingConfig, int capacityIncrement) {
                super(bufSize, charCodingConfig, capacityIncrement);
            }

            @Override
            protected void onLine(String line) {
                if (line.startsWith("event:")) {
                    event = line.substring("event:".length()).trim();
                } else if (line.startsWith("data:")) {
                    waitForDemand();
                    if (isCancelled)
                        return;

                    subscriber.onNext(newServerEvent(event, line.substring("data:".length()).trim()));
                } else if (!line.isEmpty()) {
                    waitForDemand();
                    if (isCancelled)
                        return;

                    subscriber.onNext(newServerEvent(null, line));
                }
            }

            protected DefaultServerEventResponse newServerEvent(String event, String data) {
                DefaultServerEventResponse ret = new DefaultServerEventResponse();
                ret.setHttpStatus(response.getCode());
                ret.setHeaders(headers);
                ret.setEvent(event);
                ret.setData(data);
                return ret;
            }

            @Override
            public void failed(Exception cause) {
                subscriber.onError(ApacheHttpClientHelper.wrapException(cause));
            }

            @Override
            protected void completed() throws IOException {
                if (!success)
                    throw new NopException(ERR_HTTP_RESPONSE_ERROR)
                            .param(ARG_HTTP_STATUS, response.getCode())
                            .param(ARG_BODY, getContentBody());
                subscriber.onComplete();
            }
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
                future.cancel(false);
            }
            notifyAll();
        }
    }
}