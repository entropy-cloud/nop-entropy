package io.nop.plugin.test;

/**
 * destroy-method 抛错的模拟 bean（C6-002 失败注入点）：容器 stop（BeanContainerImpl.stop →
 * BeanScope.close 逐 bean 销毁后上抛末个 destroy 异常——既有 IoC 行为消费）时确定性抛错，
 * 驱动插件 deactivate 失败 / activate 回滚失败的失败终态测试。
 */
public class FailingDestroyBean {

    public void onDestroy() {
        throw new IllegalStateException("failing-destroy-boom");
    }
}
