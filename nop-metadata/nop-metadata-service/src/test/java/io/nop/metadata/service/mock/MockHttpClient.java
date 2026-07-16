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
 * Test mock for {@link IHttpClient}. Records all {@code fetch}/{@code fetchAsync} calls and returns configurable
 * responses. Tests configure behavior via {@code static} fields (the IoC bean is a singleton delegating to static state).
 *
 * <p>Usage in tests:
 * <ul>
 *   <li>{@link #reset()} — clear recorded calls + reset to default 200 response (call in @BeforeEach)</li>
 *   <li>{@link #responseStatus} — HTTP status to return (default 200)</li>
 *   <li>{@link #throwOnFetch} — if non-null, {@code fetch} throws this instead of returning a response</li>
 *   <li>{@link #fetchCallCount} — number of {@code fetch} calls since last reset</li>
 *   <li>{@link #lastRequest} — the last {@link HttpRequest} passed to {@code fetch}</li>
 *   <li>{@link #recordedRequests} — all requests in order</li>
 * </ul>
 */
public class MockHttpClient implements IHttpClient {

    public static int responseStatus = 200;
    public static String responseBody = "{}";
    public static RuntimeException throwOnFetch = null;

    public static int fetchCallCount = 0;
    public static HttpRequest lastRequest = null;
    public static final List<HttpRequest> recordedRequests = new ArrayList<>();

    public static void reset() {
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

    private static void recordCall(HttpRequest request) {
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
