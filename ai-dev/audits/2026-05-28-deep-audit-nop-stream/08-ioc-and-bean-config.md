# 维度 08: IoC 与 Bean 配置

## 适用性
适用

## 检查范围
- 搜索所有 `beans.xml`、`*.beans.xml` 文件
- 搜索所有 `@Inject`、`@InjectValue` 注解
- 检查 NopIoC 配置

## 发现

### 零实质性发现

nop-stream 模块没有任何 beans.xml 文件，没有使用 `@Inject` 或 `@InjectValue` 注解。

模块的组件组装方式是：
1. **手动构造**：所有核心对象（CheckpointCoordinator、TaskManager、JobCoordinator）通过构造函数创建，依赖通过构造参数注入。
2. **工厂方法**：GraphModelCheckpointExecutor 使用静态工厂方法组装整个执行管线。
3. **无 IoC 容器依赖**：引擎模块不依赖 NopIoC 或 Spring 容器。

这种模式对于引擎/基础设施模块是合理的——引擎需要精确控制对象生命周期和依赖关系，不适合通过 IoC 容器管理。

## 维度总结
引擎模块不使用 IoC 容器，全部依赖通过构造函数手动注入。这是合理的架构选择。
