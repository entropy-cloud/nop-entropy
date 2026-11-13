package io.nop.rpc.simple;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 应用类加载器加载的回调接口（Proxy.newProxyInstance 需要 non-null classloader）。 */
interface EchoService {
    String echo();
}

/**
 * C1-001 (plan 2523, MA-07 主，WI 2.1 CL-024 原始站点)：init 幂等——重复 init 不得累积
 * 拦截器或双建 timer/executor（当前即红）。destroy 后 newInstance fail-fast。
 */
public class TestSimpleRpcClientFactoryIdempotentInit_C1_001 {

    private SimpleRpcClientFactory newFactory() {
        SimpleRpcClientFactory factory = new SimpleRpcClientFactory();
        factory.setRpcInterface(EchoService.class);
        factory.setClientConfig(new io.nop.socket.ClientConfig());
        factory.getClientConfig().setLogBody(true);
        return factory;
    }

    @Test
    public void repeatedInitMustNotAccumulateInterceptors() throws Exception {
        SimpleRpcClientFactory factory = newFactory();
        factory.init();
        factory.init();

        Field f = SimpleRpcClientFactory.class.getDeclaredField("interceptors");
        f.setAccessible(true);
        List<?> interceptors = (List<?>) f.get(factory);
        assertEquals(1, interceptors.size(),
                "C1-001: repeated init must not accumulate the logBody interceptor"
                        + " (defect: prepend on every init — unbounded growth across newInstance calls)");
    }

    @Test
    public void timerFieldStaysSingletonAcrossInits() throws Exception {
        SimpleRpcClientFactory factory = newFactory();
        factory.init();
        Field f = SimpleRpcClientFactory.class.getDeclaredField("timer");
        f.setAccessible(true);
        Object timer1 = f.get(factory);
        assertNotNull(timer1);
        factory.init();
        Object timer2 = f.get(factory);

        assertEquals(timer1, timer2,
                "C1-001: timer must be created exactly once (check-then-create race = single-thread timer leak)");
    }

    @Test
    public void newInstanceAfterDestroyFailsFast() {
        SimpleRpcClientFactory factory = newFactory();
        factory.destroy();

        assertThrows(IllegalStateException.class, factory::newInstance,
                "destroyed factory must fail fast, not run on destroyed executor/timer");
    }
}
