/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.retry.api;

import io.nop.api.core.rpc.IRpcCall;

public interface IRetryTask extends IRpcCall {

    String getServiceName();

    String getServiceMethod();

    String getExecutorId();

    IRetryTask withExecutorId(String executorId);

    String getPolicyId();

    IRetryTask withPolicyId(String policyId);

    String getIdempotentId();

    IRetryTask withIdempotentId(String idempotentId);

    String getCallbackService();

    String getCallbackMethod();

    IRetryTask withCallback(String callbackService, String callbackMethod);

    /**
     * 获取命名空间ID，用于多租户隔离
     */
    String getNamespaceId();

    /**
     * 设置命名空间ID
     */
    IRetryTask withNamespaceId(String namespaceId);

    /**
     * 获取分组ID，用于逻辑分组
     */
    String getGroupId();

    /**
     * 设置分组ID
     */
    IRetryTask withGroupId(String groupId);

}
