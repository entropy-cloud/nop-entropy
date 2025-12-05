package io.nop.biz.impl;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.api.core.rpc.IRpcService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestRpcServiceOnGraphQL extends JunitBaseTestCase {
    @Test
    public void testRpcService() {
        IRpcService rpcService = (IRpcService) BeanContainer.instance().getBean("nopRpcServiceOnGraphQL");
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(Map.of("id", "123"));
        ApiResponse<Map<String, Object>> response = (ApiResponse<Map<String, Object>>) rpcService.call("MyObject__get", request, null);
        assertEquals("{name=ret_123, status=null, extValue=ext_ret_123}", response.getData().toString());
    }
}
