package io.nop.http.api.support;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpClientInterceptor;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;

import java.util.List;
import java.util.concurrent.CompletionStage;

public class WithInterceptorHttpClient implements IHttpClient {
    private final IHttpClient httpClient;
    private final List<IHttpClientInterceptor> interceptors;

    public WithInterceptorHttpClient(IHttpClient httpClient, List<IHttpClientInterceptor> interceptors) {
        this.httpClient = httpClient;
        this.interceptors = interceptors;
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelTokens) {
        if (interceptors.isEmpty())
            return httpClient.fetchAsync(request, cancelTokens);

        for (IHttpClientInterceptor interceptor : interceptors) {
            interceptor.onBeginFetch(this, request, cancelTokens);
        }

        return httpClient.fetchAsync(request, cancelTokens).whenComplete((response, ex) -> {
            for (int i = 0, n = interceptors.size(); i < n; i++) {
                IHttpClientInterceptor interceptor = interceptors.get(n - i - 1);
                interceptor.onEndFetch(this, request, ex, response);
            }
        });
    }

    @Override
    public CompletionStage<Void> downloadAsync(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options, ICancelToken cancelToken) {
        return httpClient.downloadAsync(request, targetFile, options, cancelToken);
    }

    @Override
    public CompletionStage<Void> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options, ICancelToken cancelToken) {
        return httpClient.uploadAsync(request, inputFile, options, cancelToken);
    }
}
