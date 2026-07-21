package io.nop.metadata.service.mock;

import io.nop.api.core.util.ICancelToken;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test mock for {@link IHttpClient}. Instance-level state, safe for parallel test execution.
 * Each test creates or injects its own instance.
 */
public class MockHttpClient implements IHttpClient {

    public int responseStatus = 200;
    public String responseBody = "{}";
    public RuntimeException throwOnFetch = null;

    public int fetchCallCount = 0;
    public HttpRequest lastRequest = null;
    public final List<HttpRequest> recordedRequests = new ArrayList<>();

    public void reset() {
        responseStatus = 200;
        responseBody = "{}";
        throwOnFetch = null;
        fetchCallCount = 0;
        lastRequest = null;
        recordedRequests.clear();
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
        recordCall(request);
        if (throwOnFetch != null) {
            CompletableFuture<IHttpResponse> f = new CompletableFuture<>();
            f.completeExceptionally(throwOnFetch);
            return f;
        }
        return CompletableFuture.completedFuture(new MockHttpResponse(responseStatus, responseBody));
    }

    @Override
    public IHttpResponse fetch(HttpRequest request, ICancelToken cancelToken) {
        recordCall(request);
        if (throwOnFetch != null) {
            throw throwOnFetch;
        }
        return new MockHttpResponse(responseStatus, responseBody);
    }

    private void recordCall(HttpRequest request) {
        fetchCallCount++;
        lastRequest = request;
        recordedRequests.add(request);
    }

    @Override
    public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                         DownloadOptions options, ICancelToken cancelToken) {
        return CompletableFuture.completedFuture(new MockHttpResponse(200, ""));
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                       UploadOptions options, ICancelToken cancelToken) {
        return CompletableFuture.completedFuture(new MockHttpResponse(200, "{}"));
    }
}
