package io.nop.http.apache;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.HttpApiConstants;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ApacheHttpClient implements IHttpClient, IConfigRefreshable {

    private final HttpClientConfig clientConfig;

    private CloseableHttpAsyncClient client;

    private RequestConfig defaultRequestConfig;

    public ApacheHttpClient(HttpClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void refreshConfig() {

    }

    @PostConstruct
    public void start() {

        IOReactorConfig ioReactorConfig = ApacheHttpClientHelper.createReactorConfig(clientConfig);

        this.client = clientConfig.isHttp2() ? ApacheHttpClientHelper.createHttp2(clientConfig, ioReactorConfig) :
                ApacheHttpClientHelper.createHttp(clientConfig, ioReactorConfig, initConnectionManager());

        this.defaultRequestConfig = ApacheHttpClientHelper.createRequestConfig(clientConfig);

        this.client.start();
    }

    @PreDestroy
    public void stop() {
        if (this.client != null)
            this.client.close(CloseMode.GRACEFUL);
    }


    private PoolingAsyncClientConnectionManager initConnectionManager() {
        return ApacheHttpClientHelper.createConnectionManager(clientConfig);
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
        SimpleHttpRequest req = toSimpleRequest(request);
        CompletableFuture<IHttpResponse> promise = new CompletableFuture<>();

        Future<?> future = client.execute(req, newHttpClientContext(request), new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                promise.complete(fromSimpleResponse(result));
            }

            @Override
            public void failed(Exception ex) {
                promise.completeExceptionally(ApacheHttpClientHelper.wrapException(ex));
            }

            @Override
            public void cancelled() {
                promise.cancel(false);
            }
        });

        if (cancelToken != null) {
            FutureHelper.bindCancelToken(cancelToken, reason -> future.cancel(false), promise);
        }

        return promise;
    }

    protected HttpClientContext newHttpClientContext(HttpRequest request) {
        return HttpClientContext.create();
    }

    @Override
    public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
        return new ServerEventPublisher(client, toSimpleRequest(request), newHttpClientContext(request), cancelToken);
    }

    private Method toMethod(String method) {
        if (ApiStringHelper.isEmpty(method))
            return Method.GET;
        return Method.normalizedValueOf(method);
    }

    private SimpleHttpRequest toSimpleRequest(HttpRequest request) {
        SimpleRequestBuilder builder = SimpleRequestBuilder.create(toMethod(request.getMethod()));
        builder.setUri(request.getUrl());
        if (request.getHeaders() != null) {
            for (Map.Entry<String, Object> entry : request.getHeaders().entrySet()) {
                Object value = entry.getValue();
                if (value == null)
                    continue;

                if (value instanceof List<?>) {
                    List<?> list = (List<?>) value;
                    for (Object item : list) {
                        if (item == null)
                            continue;
                        builder.addHeader(entry.getKey(), item.toString());
                    }
                } else {
                    String str = value.toString();
                    builder.addHeader(entry.getKey(), str);
                }
            }
        }

        if (request.getParams() != null) {
            request.getParams().forEach((name, value) -> {
                if (value == null)
                    return;

                if (value instanceof Collection) {
                    ((Collection<?>) value).forEach(item -> builder.addParameter(name, StringHelper.toString(item, "")));
                } else {
                    builder.addParameter(name, value.toString());
                }
            });
        }

        if (request.getTimeout() > 0) {
            RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig)
                    .setResponseTimeout(request.getTimeout(), TimeUnit.MILLISECONDS).build();

            builder.setRequestConfig(requestConfig);
        }

        addBody(builder, request.getBody(), getContentType(request));
        return builder.build();
    }

    private void addBody(SimpleRequestBuilder builder, Object body, ContentType contentType) {
        if (body instanceof String) {
            builder.setBody(body.toString(), contentType);
        } else if (body instanceof ApiRequest) {
            setApiRequestBody(builder, (ApiRequest) body, contentType);
        } else {
            builder.setBody(JsonTool.stringify(body), contentType);
        }
    }

    private void setApiRequestBody(SimpleRequestBuilder builder, ApiRequest request, ContentType contentType) {
        if (request.getHeaders() != null) {
            request.getHeaders().forEach((name, value) -> {
                if (value == null)
                    return;
                builder.setHeader(name, value.toString());
            });
        }
        if (request.getData() != null)
            builder.setBody(JsonTool.stringify(request.getData()), contentType);
    }

    private ContentType getContentType(HttpRequest request) {
        if (HttpApiConstants.DATA_TYPE_FORM.equals(request.getDataType()))
            return ContentType.APPLICATION_FORM_URLENCODED;
        if (HttpApiConstants.DATA_TYPE_MULTIPART.equals(request.getDataType()))
            return ContentType.MULTIPART_FORM_DATA;
        return ContentType.APPLICATION_JSON;
    }

    private DefaultHttpResponse fromSimpleResponse(SimpleHttpResponse httpResponse) {
        return ApacheHttpClientHelper.fromSimpleResponse(httpResponse);
    }

    @Override
    public CompletionStage<Void> downloadAsync(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options,
                                               ICancelToken cancelToken) {
        return null;
    }

    @Override
    public CompletionStage<Void> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                             ICancelToken cancelToken) {
        return null;
    }
}
