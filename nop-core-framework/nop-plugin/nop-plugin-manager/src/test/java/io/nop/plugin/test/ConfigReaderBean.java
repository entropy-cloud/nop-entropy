package io.nop.plugin.test;

/**
 * 实例配置域读取 bean：属性经 ${var} 占位符从实例容器的 config provider 解析
 * （实例合并视图命中 / 全局回落 / 实例覆盖全局 / 仅实例键）。
 */
public class ConfigReaderBean {
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
