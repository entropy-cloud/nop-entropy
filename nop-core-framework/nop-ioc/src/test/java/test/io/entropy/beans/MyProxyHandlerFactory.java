package test.io.entropy.beans;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * ioc:proxy + ioc:bean-method 组合回归测试用工厂。
 * checkProxy 要求 ioc:proxy 的 bean 结果类型实现 InvocationHandler，故本类同时实现该接口
 * （占位实现，不参与断言）；bean-method 返回真正的 InvocationHandler，容器将其回填到
 * ioc:proxy 创建的 DelegateInvocationHandler 后，代理调用委托到该 handler。
 */
public class MyProxyHandlerFactory implements InvocationHandler {

    public InvocationHandler getHandler() {
        return new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                return "hello:" + args[0];
            }
        };
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        throw new UnsupportedOperationException("placeholder handler, use getHandler()");
    }
}
