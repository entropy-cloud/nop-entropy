package io.nop.code.web.mock;

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
 * WI12 test-only stub: page validation in nop-code-web never issues HTTP
 * calls, but the auto-assembled ai-defaults bean graph (nopChatService)
 * requires an IHttpClient at container startup. The real conditional bean
 * (nopHttpClient) is inactive because no nopRawHttpClient implementation is
 * on this module's test classpath, so this stub satisfies the by-type wiring
 * and fails loudly if any code path actually tries to fetch — a silent
 * no-op would mask accidental network use in tests.
 */
public class StubHttpClient implements IHttpClient {

    private static UnsupportedOperationException notExpected(String op) {
        return new UnsupportedOperationException(
                "StubHttpClient: HTTP " + op + " is not expected in nop-code-web page validation tests");
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
