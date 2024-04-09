# 第三方框架集成

Nop平台的核心模块都是从零开始编写，不依赖任何第三方框架。但是为了提高开发效率，Nop平台也提供了一些第三方框架的集成支持。
它可以与Spring或者Quarkus框架集成在一起使用。基本做法都是先启动Spring或者Quarkus框架，然后监听Spring或者Quarkus的启动事件来启动Nop平台。

Nop平台的启动和停止使用如下调用：

```
CoreInitialization.initialize();
CoreInitialization.destroy();
```

## Spring集成

Spring集成配置参见[spring.md](spring.md)

也可以改造Spring和MyBatis，加入部分可逆计算支持，参见[spring-delta.md](spring/spring-delta.md)

## Quarkus集成

Quarkus集成配置参见[quarkus.md](quarkus.md)
