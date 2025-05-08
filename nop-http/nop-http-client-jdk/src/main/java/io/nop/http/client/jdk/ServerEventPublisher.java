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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;

import static io.nop.http.api.HttpApiErrors.ARG_BODY;
import static io.nop.http.api.HttpApiErrors.ARG_EXCEPTION;
import static io.nop.http.api.HttpApiErrors.ARG_HTTP_STATUS;
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

        ServerEventSubscription(
                Flow.Subscriber<? super IServerEventResponse> subscriber
        ) {
            super(subscriber);
        }

        @Override
        protected Future<?> startRequest() {
            // 发送异步HTTP请求
            CompletableFuture<HttpResponse<InputStream>> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());

            CompletableFuture<?> promise = future.whenComplete((response, ex) -> {
                if (ex != null) {
                    onError(wrapException(ex));
                    return;
                }

                processResponse(response);
            });

            FutureHelper.bindCancelToken(cancelToken, this::cleanup, promise);
            return future;
        }

        private void processResponse(HttpResponse<InputStream> response) {
            int status = response.statusCode();
            Map<String, String> headers = JdkHttpClientHelper.getHeaders(response.headers());
            InputStream in = response.body();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));

            if (!HttpHelper.isOk(status)) {
                ResolvedPromise<String> result = FutureHelper.safeInvoke(() -> IoHelper.readText(reader));
                throw new NopException(ERR_HTTP_RESPONSE_ERROR)
                        .param(ARG_HTTP_STATUS, status)
                        .param(ARG_BODY, result.getResult())
                        .param(ARG_EXCEPTION, result.getException());
            }

            executor().execute(() -> {
                if (isCancelled())
                    return;

                try {
                    onStart(status, headers);

                    parseEvents(reader);

                    if (isCancelled())
                        return;

                    onComplete();
                } catch (Exception e) {
                    if (!isCancelled()) onError(wrapException(e));
                }
            });
        }

        protected void parseEvents(BufferedReader reader) throws IOException {
            String line;
            while ((line = reader.readLine()) != null) {
                processLine(line);
            }
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
}
