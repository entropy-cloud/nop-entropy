package io.nop.plugin.test;

/**
 * 模拟插件 start 失败的测试异常。置于 {@code io.nop.plugin.test} 包以便 jar 内插件类经
 * plugin.json 的 importPackages 路由到宿主 classloader 访问（PluginClassLoader 的 parent
 * 是 bootstrap loader，jar 内类只能解析 java.* 与路由包中的宿主类）。
 */
public class MockPluginStartException extends RuntimeException {
    public MockPluginStartException(String message) {
        super(message);
    }
}
