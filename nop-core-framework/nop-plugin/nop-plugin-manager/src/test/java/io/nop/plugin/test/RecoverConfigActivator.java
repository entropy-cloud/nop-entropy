package io.nop.plugin.test;

import io.nop.api.core.config.AppConfig;
import io.nop.plugin.api.Disposable;
import io.nop.plugin.api.IPluginActivator;
import io.nop.plugin.api.IPluginScope;

import java.util.Map;

/**
 * R2 时间静止窗口 (b) 探针：effect 回退（deactivate 展开期间）将本插件的 if-property
 * 门控配置恢复为 true（条件恢复发生在 deactivate() 内部），回退正常完成——验证"回退完成后
 * 重激活"。
 */
public class RecoverConfigActivator implements IPluginActivator {

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
        return () -> {
            // deactivate 展开期间条件恢复（effect 回退内）
            AppConfig.getConfigProvider().assignConfigValue(configKey, "true");
            OrderRecorder.event("deactivate:" + eventName);
        };
    }
}
