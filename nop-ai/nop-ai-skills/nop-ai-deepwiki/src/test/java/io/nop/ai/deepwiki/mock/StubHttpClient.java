package io.nop.ai.deepwiki.mock;

import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test-only stub (plan 2415): the deepwiki test classpath has no
 * nopRawHttpClient implementation, so the conditional default bean
 * nopHttpClient never activates and the auto-assembled ai-defaults graph
 * fails at startup when building nopChatService.httpClient
 * (not-find-bean-with-type IHttpClient). This stub satisfies the by-type
 * wiring and fails loudly if any code path actually tries to fetch — a
 * silent no-op would mask accidental network use in tests.
 */
public class StubHttpClient implements IHttpClient {

    private static UnsupportedOperationException notExpected(String op) {
        return new UnsupportedOperationException(
                "StubHttpClient: HTTP " + op + " is not expected in deepwiki resource smoke tests");
    }

    @Override
    public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelTokens) {
        CompletableFuture<IHttpResponse> future = new CompletableFuture<>();
        future.completeExceptionally(notExpected("fetchAsync(" + request.getUrl() + ")"));
        return future;
    }

    @Override
    public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                        DownloadOptions options, ICancelToken cancelToken) {
        CompletableFuture<IHttpResponse> future = new CompletableFuture<>();
        future.completeExceptionally(notExpected("downloadAsync(" + request.getUrl() + ")"));
        return future;
    }

    @Override
    public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                      UploadOptions options, ICancelToken cancelToken) {
        CompletableFuture<IHttpResponse> future = new CompletableFuture<>();
        future.completeExceptionally(notExpected("uploadAsync(" + request.getUrl() + ")"));
        return future;
    }
}
