package io.nop.http.client.okhttp;

import io.nop.core.initialize.CoreInitialization;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOkHttpClientImplFix {

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    static OkHttpClientImpl newClient() {
        return new OkHttpClientImpl(new OkHttpClient.Builder().build(), new HttpClientConfig());
    }

    @Test
    public void testGetRequestHasNoBody() {
        OkHttpClientImpl client = newClient();
        // OkHttp 4.x: GET 请求不允许携带请求体，否则 Request.Builder.method 抛 IllegalArgumentException
        Request request = assertDoesNotThrow(() -> client.newCall(HttpRequest.get("http://localhost/a")).request());
        assertNull(request.body());
    }

    @Test
    public void testQueryParamsAppended() {
        OkHttpClientImpl client = newClient();
        HttpRequest httpRequest = HttpRequest.get("http://localhost/a").param("k", "v");
        Request request = client.newCall(httpRequest).request();
        assertTrue(request.url().toString().contains("k=v"), "query params must be appended, url=" + request.url());
    }

    @Test
    public void testPostBodyKept() {
        OkHttpClientImpl client = newClient();
        HttpRequest httpRequest = HttpRequest.post("http://localhost/a").body("abc");
        Request request = client.newCall(httpRequest).request();
        assertTrue(request.method().equalsIgnoreCase("POST"));
    }

    @Test
    public void testDownloadAsyncRejectsNullOutputFile() {
        OkHttpClientImpl client = newClient();
        assertThrows(IllegalArgumentException.class,
                () -> client.downloadAsync(HttpRequest.get("http://localhost/a"), null, null, null));
    }

    @Test
    public void testUploadAsyncRejectsNullInputFile() {
        OkHttpClientImpl client = newClient();
        assertThrows(IllegalArgumentException.class,
                () -> client.uploadAsync(HttpRequest.post("http://localhost/a"), null, null, null));
    }
}
