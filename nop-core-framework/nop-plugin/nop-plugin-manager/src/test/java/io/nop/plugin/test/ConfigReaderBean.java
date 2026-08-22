package io.nop.plugin.test;

/**
 * 定义级配置域读取 bean：属性经 ${var} 占位符从插件的 DefinitionConfigProvider 解析
 * （定义级合并视图命中 / 全局配置回落 / 定义级键仅本插件可见、不污染全局）。
 *
 * <p>实现 {@link IConfigReader}——代理测试夹具接口（有状态 bean 保持具体类，
 * 消费方经接口类型获取代理）。
 */
public class ConfigReaderBean implements IConfigReader {
    private String timeout;
    private String mode;
    private String globalValue;
    private String onlyInstance;

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getGlobalValue() {
        return globalValue;
    }

    public void setGlobalValue(String globalValue) {
        this.globalValue = globalValue;
    }

    public String getOnlyInstance() {
        return onlyInstance;
    }

    public void setOnlyInstance(String onlyInstance) {
        this.onlyInstance = onlyInstance;
    }
}
