package io.nop.rpc.core.reflect;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.rpc.IRpcService;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancelToken;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 MultiRpcService 的服务路由语义：缺 svc 头报错、未知服务报错、
 * 已注册服务按头路由分发；错误以正常响应（status!=0）返回而非异常。
 */
public class TestMultiRpcService {

    static class EchoService implements IRpcService {
        final String tag;

        EchoService(String tag) {
            this.tag = tag;
        }

        @Override
        public java.util.concurrent.CompletionStage<ApiResponse<?>> callAsync(
                String serviceMethod, ApiRequest<?> request, ICancelToken cancelToken) {
            return java.util.concurrent.CompletableFuture.completedFuture(ApiResponse.success(tag));
        }
    }

    static ApiRequest<Object> requestTo(String serviceName) {
        ApiRequest<Object> request = new ApiRequest<>();
        if (serviceName != null)
            ApiHeaders.setSvcName(request, serviceName);
        return request;
    }

    @Test
    public void testMissingSvcHeaderReturnsError() throws Exception {
        MultiRpcService service = new MultiRpcService(Map.of("a", new EchoService("a")));

        ApiResponse<?> response = service.callAsync("m", requestTo(null), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(!response.isOk(), "missing svc header must yield an error response");
    }

    @Test
    public void testUnknownServiceReturnsError() throws Exception {
        MultiRpcService service = new MultiRpcService(Map.of("a", new EchoService("a")));

        ApiResponse<?> response = service.callAsync("m", requestTo("no-such"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(!response.isOk(), "unknown service must yield an error response");
    }

    @Test
    public void testKnownServiceReceivesCall() throws Exception {
        EchoService echo = new EchoService("hit");
        MultiRpcService service = new MultiRpcService(Map.of("svc-a", echo));

        ApiResponse<?> response = service.callAsync("m", requestTo("svc-a"), null)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals("hit", response.getData(), "call must be routed to the named service");
    }

    @Test
    public void testNullServicesMapRejected() {
        // Guard.notEmpty(null) 在构造期拒绝 null 服务映射
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MultiRpcService(null));
    }

    @Test
    public void testEmptyServicesMapRejectedAtConstruction() {
        // 回归覆盖 wi11#2（plan 2306 项 29）：Guard.notEmpty 对 Map 只判 null，
        // 修复前空 map 构造成功、所有调用延迟到运行期 unknown-service 失败；
        // 修复后必须在构造期 fail-fast
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new MultiRpcService(Map.of()));
    }
}
