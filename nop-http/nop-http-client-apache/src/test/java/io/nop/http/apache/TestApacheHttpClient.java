package io.nop.http.apache;

import io.nop.api.core.util.FutureHelper;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class TestApacheHttpClient {
    @Test
    public void testHttps() {
        HttpClientConfig config = new HttpClientConfig();
        config.setIgnoreSslCerts(true);
        ApacheHttpClient client = new ApacheHttpClient(config);
        client.start();
        HttpRequest request = new HttpRequest();
        request.setUrl("https://www.baidu.com");
        IHttpResponse response = FutureHelper.syncGet(client.fetchAsync(request, null));
        System.out.println(response.getBodyAsText());
        client.stop();
    }
}
