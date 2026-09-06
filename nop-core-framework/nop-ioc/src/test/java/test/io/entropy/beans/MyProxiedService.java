package test.io.entropy.beans;

/**
 * ioc:proxy + ioc:bean-method 组合回归测试用接口：作为 ioc:type 指定的代理接口。
 */
public interface MyProxiedService {
    String greet(String name);
}
