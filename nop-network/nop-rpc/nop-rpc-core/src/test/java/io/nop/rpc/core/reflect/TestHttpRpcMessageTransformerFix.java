package io.nop.rpc.core.reflect;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.ICancelToken;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.impl.MethodModelBuilder;
import io.nop.core.initialize.CoreInitialization;
import io.nop.rpc.core.utils.RpcHelper;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class TestHttpRpcMessageTransformerFix {

    @BizModel("TestApi")
    public interface TestApi {
        @GET
        @Path("/list")
        String listAll();

        String twoArgs(String first, ApiRequest<Object> request);

        String withCancelToken(String value, ICancelToken cancelToken);
    }

    @BeforeAll
    static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    static void destroy() {
        CoreInitialization.destroy();
    }

    static IFunctionModel method(String name) {
        for (Method m : TestApi.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return MethodModelBuilder.from(TestApi.class, m);
            }
        }
        throw new IllegalArgumentException("no method " + name);
    }

    @Test
    public void testZeroArgRestMethodKeepsUrl() {
        IFunctionModel model = method("listAll");
        ApiRequest<Object> request = HttpRpcMessageTransformer.INSTANCE.toRequest("TestApi", model,
                IFunctionModel.EMPTY_ARGS);

        assertEquals("/list", RpcHelper.getHttpUrl(request),
                "zero-arg @Path method must keep its REST url");
    }

    @Test
    public void testApiRequestAtNonFirstPosition() {
        IFunctionModel model = method("twoArgs");
        String firstName = model.getArgs().get(0).getName();
        ApiRequest<Object> body = new ApiRequest<>();
        ApiRequest<Object> request = HttpRpcMessageTransformer.INSTANCE.toRequest("TestApi", model,
                new Object[]{"first-arg", body});

        // ApiRequest 参数不在首位时不应把 args[0] 强转/误用
        assertEquals("first-arg", ((Map<?, ?>) request.getData()).get(firstName));
    }

    @Test
    public void testFromRequestFillsCancelToken() {
        IFunctionModel model = method("withCancelToken");
        ICancelToken cancelToken = new ICancelToken() {
            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public String getCancelReason() {
                return null;
            }

            @Override
            public void appendOnCancel(Consumer<String> task) {
            }

            @Override
            public void removeOnCancel(Consumer<String> task) {
            }
        };

        Map<String, Object> data = new HashMap<>();
        data.put(model.getArgs().get(0).getName(), "v");
        ApiRequest<Map<String, Object>> request = ApiRequest.build(data);

        Object[] args = DefaultRpcMessageTransformer.INSTANCE.fromRequest("TestApi", model, request, cancelToken);
        assertEquals("v", args[0]);
        assertSame(cancelToken, args[1], "ICancelToken args must be filled server-side");
    }
}
