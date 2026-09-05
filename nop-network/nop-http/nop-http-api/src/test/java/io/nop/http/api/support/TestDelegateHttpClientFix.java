package io.nop.http.api.support;

import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertSame;

public class TestDelegateHttpClientFix {

    static final Flow.Publisher<IServerEventResponse> MARKER = subscriber -> {
    };

    static class FakeClient implements IHttpClient {
        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile,
                                                            DownloadOptions options, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile,
                                                          UploadOptions options, ICancelToken cancelToken) {
            return null;
        }

        @Override
        public Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
            return MARKER;
        }
    }

    @Test
    public void testFetchServerEventFlowDelegated() {
        DelegateHttpClient delegate = new DelegateHttpClient(new FakeClient());
        assertSame(MARKER, delegate.fetchServerEventFlow(new HttpRequest(), null));
    }
}
