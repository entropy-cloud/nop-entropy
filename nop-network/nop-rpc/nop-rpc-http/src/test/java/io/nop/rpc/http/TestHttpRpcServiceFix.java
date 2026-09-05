package io.nop.rpc.http;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.graphql.GraphQLRequestBean;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpInputFile;
import io.nop.http.api.client.IHttpOutputFile;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.UploadOptions;
import io.nop.http.api.support.DefaultHttpResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestHttpRpcServiceFix {

    static class StubClient implements IHttpClient {
        volatile String bodyText;

        @Override
        public CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelToken) {
            DefaultHttpResponse response = new DefaultHttpResponse();
            response.setHttpStatus(200);
            response.setBodyAsText(bodyText);
            return CompletableFuture.completedFuture((IHttpResponse) response);
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
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    @Test
    public void testGraphqlRequestErrorsNotDropped() {
        StubClient client = new StubClient();
        // GraphQL 响应：带 errors
        client.bodyText = "{\"data\":null,\"errors\":[{\"message\":\"boom\"}]}";

        HttpRpcService service = new HttpRpcService(client, (request, serviceMethod) -> "http://localhost/graphql");

        GraphQLRequestBean graphQlRequest = new GraphQLRequestBean();
        graphQlRequest.setQuery("{ user { id } }");
        ApiRequest<Object> request = new ApiRequest<>();
        request.setData(graphQlRequest);

        ApiResponse<?> response = service.callAsync("query", request, null)
                .toCompletableFuture().join();

        // GraphQL errors 不应被静默丢弃成成功响应
        assertFalse(response.isBizSuccess(), "graphql errors must not be dropped: " + response);
        assertTrue(response.getMsg() != null && response.getMsg().contains("boom"),
                "error message must be preserved: " + response);
    }
}
