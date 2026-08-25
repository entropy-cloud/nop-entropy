package io.nop.plugin.test;

/**
 * MockPluginStopFail 的 stop 失败开关：放在 io.nop.plugin.test 包（经 importPackages
 * 路由到宿主 classloader），jar 内插件类与测试代码共享同一静态字段。
 */
public class MockStopControl {
    public static volatile boolean failStop = false;
}
