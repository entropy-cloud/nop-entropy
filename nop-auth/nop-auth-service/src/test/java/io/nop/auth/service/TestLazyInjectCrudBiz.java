package io.nop.auth.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@NopTestConfig(localDb = true,initDatabaseSchema = OptionalBoolean.TRUE)
public class TestLazyInjectCrudBiz extends JunitBaseTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    /**
     * biz_NopAuthUser是根据IBizObjectManager生成，因此如果要在BizModel中inject这种bean，则会出现循环依赖，很难处理，
     * 比如要设置为ioc:lazy-property，这种属性会等待所有bean都创建并组装成功后再执行注入。
     */
    @Test
    public void testLazyInject() {
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(null,
                "DemoAuth__testBiz", new ApiRequest<>(), null);
        graphQLEngine.executeRpc(ctx).get();
    }
}
