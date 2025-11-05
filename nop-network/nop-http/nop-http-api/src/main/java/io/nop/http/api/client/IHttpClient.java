/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.client;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * k8s的java sdk使用了okhttp。aliyun sdk使用了apache httpcomponent。
 */
public interface IHttpClient {
    CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelTokens);

    default IHttpResponse fetch(HttpRequest request, ICancelToken cancelToken) {
        return FutureHelper.syncGet(fetchAsync(request, cancelToken));
    }

    default Flow.Publisher<IServerEventResponse> fetchServerEventFlow(HttpRequest request, ICancelToken cancelToken) {
        throw new UnsupportedOperationException();
    }

    CompletionStage<IHttpResponse> downloadAsync(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options,
                                                 ICancelToken cancelToken);

    default IHttpResponse download(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options, ICancelToken cancelToken) {
        return FutureHelper.syncGet(downloadAsync(request, targetFile, options, cancelToken));
    }

    CompletionStage<IHttpResponse> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                               ICancelToken cancelToken);
}