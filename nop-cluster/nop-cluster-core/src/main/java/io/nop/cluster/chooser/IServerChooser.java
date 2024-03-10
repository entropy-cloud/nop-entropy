/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.cluster.chooser;

import io.nop.cluster.discovery.ServiceInstance;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface IServerChooser<R> {
    default ServiceInstance chooseServer(String serviceName, R request) {
        return chooseFromCandidates(getServers(serviceName, request), request);
    }

    default CompletionStage<ServiceInstance> chooseServerAsync(String serviceName, R request) {
        return getServersAsync(serviceName, request).thenApply(candidates -> chooseFromCandidates(candidates, request));
    }

    /**
     * 执行负载均衡算法从备选列表中随机选择一个实例
     *
     * @param candidates 备选列表
     * @param request    请求对象
     * @return 从备选列表中选择的实例。如果列表非空，则此对象不应该为null
     */
    ServiceInstance chooseFromCandidates(List<ServiceInstance> candidates, R request);

    CompletionStage<List<ServiceInstance>> getServersAsync(String serviceName, R request);

    /**
     * 每次都返回一个新的List，返回的结果列表允许被修改
     *
     * @param serviceName 服务名
     * @param request     请求对象
     * @return 满足匹配条件的服务实例列表
     */
    List<ServiceInstance> getServers(String serviceName, R request);
}
