package io.nop.http.apache;

import io.nop.api.core.json.JSON;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import io.nop.http.api.client.IServerEventResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;

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

    @Test
    public void testHttp2() {
        HttpClientConfig config = new HttpClientConfig();
        config.setHttp2(true);
        config.setIgnoreSslCerts(true);
        ApacheHttpClient client = new ApacheHttpClient(config);
        client.start();
        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost:8080");
        IHttpResponse response = FutureHelper.syncGet(client.fetchAsync(request, null));
        System.out.println(response.getBodyAsText());
        client.stop();
    }

    @Test
    public void testServerEvent() throws Exception {

        HttpClientConfig config = new HttpClientConfig();
        // ollama不支持http2连接
        config.setHttp2(false);
        ApacheHttpClient client = new ApacheHttpClient(config);
        client.start();

        JSON.registerProvider(JsonTool.instance());

        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost:11434/api/chat");
        request.setMethod("POST");
        Map<String, Object> map = new HashMap<>();
        map.put("stream", true);
        map.put("model", "deepseek-r1:8b");
        map.put("temperature", 0.7);
        map.put("messages", Arrays.asList(Map.of("content", "你的存在意义是什么", "role", "user")));
        request.setBody(map);

        CountDownLatch latch = new CountDownLatch(1);

        client.fetchServerEventFlow(request, null).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Integer.MAX_VALUE);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                System.out.println(Thread.currentThread().getName() + ":" + item.getData());
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("err:" + throwable);
                latch.countDown();
            }

            @Override
            public void onComplete() {
                System.out.println("complete");
                latch.countDown();
            }
        });

        latch.await();
        client.stop();
    }
}
