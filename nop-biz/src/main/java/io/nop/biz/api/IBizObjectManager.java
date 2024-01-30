/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.api;

import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.context.IServiceContext;
import io.nop.graphql.core.reflection.GraphQLBizModel;

import java.util.Map;
import java.util.Set;

public interface IBizObjectManager {
    /**
     * 根据名称返回BizActor。每个BizActor的名称都是全局唯一的，一般情况下与表名相同
     *
     * @param bizObjName actor的唯一标识
     * @return 如果对应名称的actor不存在，则抛出异常
     */
    IBizObject getBizObject(String bizObjName) throws NopException;

    Set<String> getBizObjNames();

    /**
     * 将BizActor.invoke调用的返回值包装为ApiResponse对象
     *
     * @param result IBizActor.invoke函数的返回值
     * @param rt     运行时上下文环境
     * @return 根据返回值以及IServiceRuntime中error/responseHeaders等信息构建
     */
    ApiResponse<?> buildResponse(String locale, Object result, IServiceContext rt);

    void clearCache();

    void updateDynBizModels(Map<String, GraphQLBizModel> dynBizModels);
}