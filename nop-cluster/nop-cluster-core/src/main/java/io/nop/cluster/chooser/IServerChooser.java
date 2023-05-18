/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.cluster.chooser;

import io.nop.api.core.util.FutureHelper;
import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IServerChooser<R> {
    ServiceInstance chooseServer(String serviceName, R request);

    default CompletionStage<ServiceInstance> chooseServerAsync(String serviceName, R request) {
        return FutureHelper.futureCall(() -> chooseServer(serviceName, request));
    }

    CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName, R request);

    List<ServiceInstance> getServers(String serviceName, R request);
}
