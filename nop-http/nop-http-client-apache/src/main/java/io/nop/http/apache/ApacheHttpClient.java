package io.nop.http.apache;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.hc.client5.http.async.methods.SimpleBody;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

        Future<?> future = client.execute(req, new FutureCallback<>() {
            @Override
            public void completed(SimpleHttpResponse result) {
                promise.complete(fromSimpleResponse(result));
            }

            @Override
            public void failed(Exception ex) {
                promise.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
            }
        });

        if (cancelToken != null) {
            cancelToken.appendOnCancelTask(() -> future.cancel(false));
        }
        return promise;
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

        if (request.getTimeout() > 0) {
            RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig)
                    .setResponseTimeout(request.getTimeout(), TimeUnit.MILLISECONDS).build();

            builder.setRequestConfig(requestConfig);
        }
        return builder.build();
    }

    private DefaultHttpResponse fromSimpleResponse(SimpleHttpResponse httpResponse) {
        DefaultHttpResponse result = new DefaultHttpResponse();

        // status code
        result.setHttpStatus(httpResponse.getCode());
        httpResponse.getBodyBytes();
        SimpleBody body = httpResponse.getBody();
        if (body.isText()) {
            result.setBodyAsText(body.getBodyText());
        } else if (body.isBytes()) {
            result.setBodyAsBytes(body.getBodyBytes());
        }
        ContentType contentType = body.getContentType();
        if (contentType != null) {
            Charset charset = contentType.getCharset();
            if (charset != null) {
                result.setCharset(charset.name());
            }
            result.setContentType(contentType.getMimeType());
        }

        // headers
        Map<String, String> headers = new HashMap<>();
        for (Header header : httpResponse.getHeaders()) {
            headers.put(header.getName(), header.getValue());
        }
        result.setHeaders(headers);

        return result;
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
