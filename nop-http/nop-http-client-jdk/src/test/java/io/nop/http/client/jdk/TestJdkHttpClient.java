/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.jdk;

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
public class TestJdkHttpClient {
    @Test
    public void testHttps() {
        HttpClientConfig config = new HttpClientConfig();
        config.setIgnoreSslCerts(true);
        JdkHttpClient client = new JdkHttpClient(config);
        client.start();
        HttpRequest request = new HttpRequest();
        request.setUrl("https://www.baidu.com");
        IHttpResponse response = FutureHelper.syncGet(client.fetchAsync(request, null));
        System.out.println(response.getBodyAsText());
        client.stop();
    }

    @Test
    public void testServerEvent() throws Exception {

        HttpClientConfig config = new HttpClientConfig();
        config.setHttp2(true);
        JdkHttpClient client = new JdkHttpClient(config);
        client.start();

        JSON.registerProvider(JsonTool.instance());

        HttpRequest request = new HttpRequest();
        request.setUrl("http://localhost:11434/api/chat");
        request.setMethod("POST");
        Map<String, Object> map = new HashMap<>();
        map.put("stream", true);
        map.put("model", "deepseek-r1:8b");
        map.put("temperature", 0.7);
        map.put("messages", Arrays.asList(Map.of("content", "你的存在意义是什么","role", "user")));
        request.setBody(map);

        CountDownLatch latch = new CountDownLatch(1);

        client.fetchServerEventFlow(request, null).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Integer.MAX_VALUE);
            }

            @Override
            public void onNext(IServerEventResponse item) {
                System.out.println(Thread.currentThread().getName()+":"+item.getData());
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