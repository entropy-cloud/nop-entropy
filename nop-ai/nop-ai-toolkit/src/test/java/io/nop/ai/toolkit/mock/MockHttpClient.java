package io.nop.ai.toolkit.mock;

import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import io.nop.api.core.util.ICancelToken;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class MockHttpClient implements IHttpClient {
    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
        MockHttpResponse response = new MockHttpResponse(200, "{}");
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public IHttpResponse fetch(HttpRequest request, ICancelToken cancelToken) {
        return new MockHttpResponse(200, "{}");
    }

    @Override
    public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, 
            io.nop.http.api.client.IHttpOutputFile targetFile, 
            io.nop.http.api.client.DownloadOptions options, ICancelToken cancelToken) {
        return CompletableFuture.completedFuture(new MockHttpResponse(200, ""));
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, 
            io.nop.http.api.client.IHttpInputFile inputFile, 
            io.nop.http.api.client.UploadOptions options, ICancelToken cancelToken) {
        return CompletableFuture.completedFuture(new MockHttpResponse(200, "{}"));
    }
}
