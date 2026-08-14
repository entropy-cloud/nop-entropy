package io.nop.plugin.test;

/**
 * 测试用配置读取接口（代理测试夹具）：ConfigReaderBean 的唯一实现接口——
 * 唯一实现分支（无 primary 且单实现）测试与代理生命周期矩阵测试的接口载体。
 */
public interface IConfigReader {
    String getTimeout();

    String getMode();

    String getGlobalValue();

    String getOnlyInstance();
}
