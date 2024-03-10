/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.makerchecker;

import java.util.concurrent.CompletionStage;

/**
 * 启用maker checker机制需要同时满足如下条件：
 * <ul>
 * 1. 全局开关打开
 * </ul>
 * <ul>
 * 2. method上具有@BizMakerChecker注解
 * </ul>
 * <ul>
 * 3. isMakerCheckerEnabled返回true
 * </ul>
 * <p>
 * 如果启用MakerChecker，则通过GraphQL执行bizMethod的时候实际会执行tryMethod来校验请求是否合法。 如果校验失败，则tryMethod应抛出异常，GraphQL调用返回失败。
 * 如果校验通过，则调用sendForCheck保存到审批表中，GraphQL调用不抛出异常（避免事务回滚）， 但是仍然返回失败，错误码为已提交审批申请
 */
public interface IMakerCheckerProvider {
    /**
     * 针对指定的业务方法启用maker checker机制
     */
    boolean isMakerCheckerEnabled(String bizObjName, String bizMethod);

    /**
     * 保存到审批表中之后，返回对应的审批编号
     */
    CompletionStage<String> sendForCheckAsync(SendForCheckRequest sendRequest);
}