/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.graphql.core.rpc;

import io.nop.api.core.annotations.ioc.BeanMethod;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.rpc.api.IRpcServiceInterceptor;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

/**
 * 将GraphQL引擎的调用执行封装为Rest RPC服务接口
 */
public class GraphQLRpcProxyFactoryBean {
    private IGraphQLEngine engine;
    private String bizObjName;
    private List<IRpcServiceInterceptor> interceptors = Collections.emptyList();
    private Class<?> serviceClass;

    @Inject
    public void setGraphQLEngine(IGraphQLEngine engine) {
        this.engine = engine;
    }

    public void setBizObjName(String bizObjName) {
        this.bizObjName = bizObjName;
    }

    @InjectValue("@bean:type")
    public void setServiceClass(Class<?> serviceClass) {
        this.serviceClass = serviceClass;
    }

    @BeanMethod
    public Object build() {
        return new RpcServiceOnGraphQL(engine, bizObjName, interceptors).asProxy(serviceClass);
    }
}
