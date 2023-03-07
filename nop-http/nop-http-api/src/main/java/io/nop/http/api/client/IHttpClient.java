/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.client;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

/**
 * k8s的java sdk使用了okhttp。aliyun sdk使用了apache httpcomponent。
 */
public interface IHttpClient {
    CompletionStage<IHttpResponse> fetchAsync(HttpRequest request, ICancelToken cancelTokens);

    default IHttpResponse fetch(HttpRequest request, ICancelToken cancelToken) {
        return FutureHelper.syncGet(fetchAsync(request, cancelToken));
    }

    CompletionStage<Void> downloadAsync(HttpRequest request, IHttpOutputFile targetFile, DownloadOptions options,
                                        ICancelToken cancelToken);

    CompletionStage<Void> uploadAsync(HttpRequest request, IHttpInputFile inputFile, UploadOptions options,
                                      ICancelToken cancelToken);
}