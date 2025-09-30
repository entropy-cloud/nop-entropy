# Dynamic Plugins

The Nop platform comes with a plugin management mechanism that supports dynamic loading and unloading.

## Implementing a Plugin

1. Include the `nop-plugin-api` package
2. Implement the IPlugin interface

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

In most cases, you can include the `nop-plugin-support` package and then extend the `AbstractPlugin` class.

The purpose of AbstractPlugin is to manage the bean lifecycle via NopIoC.
<!-- SOURCE_MD5:db5d69f13f6bd76a51848ce0baf6c733-->
