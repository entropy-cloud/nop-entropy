# 日志配置

## 日志格式

在Quarkus中，日志格式  `%d{yyyy-MM-dd HH:mm:ss,SSS} %h %N[%i] %-5p [%c{3.}] (%t) %s%e%n`  表示以下内容：

- %d{yyyy-MM-dd HH:mm:ss,SSS} : 日期和时间的格式，例如 "2022-01-01 12:34:56,789"，其中 SSS 表示毫秒。
- %h : 主机名。
- `%N[%i]` : 线程名称和线程ID。
- %-5p : 日志级别，左对齐并且最多占据5个字符的宽度。
- `[%c{3.}]` : 类名，最多显示3个字符。
- (%t) : 线程上下文。
- %s : 日志消息。
- %e : 异常信息。
- %n : 换行符。

这个日志格式的含义是，每条日志记录将包含日期、时间、主机名、线程名称和ID、日志级别、类名、线程上下文、日志消息和异常信息，并以换行符结束。

## 配置示例

````yaml

quarkus:
  log:
    level: INFO

    category:
      "io.nop":
        level: DEBUG

    file:
      enable: true
      path: log/app-demo.log
      #      # 输出格式
      #      format: %d{yyyy-MM-dd HH:mm:ss,SSS} %h %N[%i] %-5p [%c{3.}] (%t) %s%e%n
      #      # Indicates whether to log asynchronously
      async: true
      rotation:
        max-file-size: 100M
        max-backup-index: 300
        file-suffix: .yyyy-MM-dd

````

要开启TRACE级别的日志，必须同时配置 quarkus.log.min-level=TRACE, 否则最多只输出DEBUG级别