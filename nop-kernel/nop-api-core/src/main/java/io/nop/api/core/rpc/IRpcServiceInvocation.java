/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.rpc;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.ICancelToken;

import java.util.concurrent.CompletionStage;

public interface IRpcServiceInvocation {
    String getServiceName();

    String getServiceMethod();

    ApiRequest<?> getRequest();

    ICancelToken getCancelToken();

    CompletionStage<ApiResponse<?>> proceedAsync();

    default ApiResponse<?> proceed() {
        return FutureHelper.syncGet(proceedAsync());
    }

    boolean isInbound();
}
