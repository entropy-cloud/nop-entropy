# 动态插件

Nop平台内置了一个具有支持动态加载和卸载的插件管理机制。

## 实现插件

1. 引入`nop-plugin-api`包
2. 实现IPlugin接口

```java
public interface IPlugin {
  String getPluginGroupId();

  String getPluginArtifactId();

  String getPluginVersion();

  Timestamp getLastChangeTime();

  Timestamp getLoadTime();

  CompletionStage<Map<String, Object>> invokeCommandAsync(String command, Map<String, Object> args,
                                                          IPluginCancelToken cancelToken);

  Map<String, Object> invokeCommand(String command, Map<String, Object> args, IPluginCancelToken cancelToken);

  void start();

  void stop();
}
```

一般情况下可以选择引入`nop-plugin-support`包，然后从`AbstractPlugin`类继承。

AbstractPlugin的作用是通过NopIoC实现bean的生成周期管理。



