package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;

/**
 * agent-instance 插件激活器：scope + config 双参数传递验证——scope.getService 取 primary bean、
 * getServices 集合版；scope.effect 注册 effect；返回值 = disposer（自动注册为实例 effect）。
 */
public class AgentInstanceActivator implements IPluginActivator {

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        AgentInstanceRecorder.onActivated(config);
        AgentInstanceRecorder.serviceTool = scope.getService(ITool.class);
        AgentInstanceRecorder.serviceCount = scope.getServices(ITool.class).size();
        scope.effect(() -> AgentInstanceRecorder.event("effect-disposed"));
        return () -> AgentInstanceRecorder.event("return-disposed");
    }
}
