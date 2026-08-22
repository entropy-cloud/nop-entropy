package io.nop.plugin.test;

import io.nop.api.core.config.AppConfig;
import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;

/**
 * R2 时间静止窗口 (a) 探针：激活展开期间将本插件的 if-property 门控配置翻为 false
 * （条件失效发生在 activate() 内部），激活正常完成并返回 Disposable——验证"不中途打断，
 * 完成后收敛去激活"。
 */
public class FlipConfigActivator implements IPluginActivator {

    private String eventName;
    private String configKey;

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    @Override
    public Disposable activate(IPluginScope scope, Map<String, Object> config) {
        OrderRecorder.event("activate:" + eventName);
        // 激活展开期间条件失效（activate() 尚未返回）
        AppConfig.getConfigProvider().assignConfigValue(configKey, "false");
        return () -> OrderRecorder.event("deactivate:" + eventName);
    }
}
