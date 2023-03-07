/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.rpc;

import io.nop.api.core.util.FutureHelper;

import java.util.concurrent.CompletionStage;


/**
 * RpcService的创建工厂
 */
public interface IRpcServiceLocator {
    CompletionStage<IRpcService> getServiceAsync(String serviceName);

    default IRpcService getService(String serviceName) {
        return FutureHelper.syncGet(getServiceAsync(serviceName));
    }
}