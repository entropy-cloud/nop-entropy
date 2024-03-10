/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.client.okhttp;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.convert.ConvertHelper;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OkHttpTimeoutInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String strTimeout = request.header(ApiConstants.HEADER_TIMEOUT);
        if(strTimeout != null){
            int timeout = ConvertHelper.toInt(strTimeout);
            if(timeout > 0){
                int connectTimeout = chain.connectTimeoutMillis();
                if(timeout < connectTimeout){
                    chain = chain.withConnectTimeout(timeout, TimeUnit.MILLISECONDS);
                }
            }
        }

        return chain.proceed(request);
    }
}
