/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.client.jdk;

import io.nop.api.core.util.FutureHelper;
import io.nop.http.api.client.HttpClientConfig;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpResponse;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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
}