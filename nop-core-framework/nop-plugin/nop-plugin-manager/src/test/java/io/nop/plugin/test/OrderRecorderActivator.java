package io.nop.plugin.test;

import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;

/**
 * R2 拓扑序测试探针 activator：激活记录 {@code activate:<name>}，effect 回退记录
 * {@code deactivate:<name>}（name 经 bean property 注入，静态 {@link OrderRecorder} 全局排序）。
 */
public class OrderRecorderActivator implements IPluginActivator {

    private String name;

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        OrderRecorder.event("activate:" + name);
        return () -> OrderRecorder.event("deactivate:" + name);
    }
}
