/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.api;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public interface IRetryService {

    IRetryTask newRetryTask(String serviceName, String serviceMethod);

    CompletionStage<ApiResponse<?>> retryFromDeadLetter(String deadLetterId, ICancelToken cancelToken);

    void pause(String recordId);

    void resume(String recordId);
}
