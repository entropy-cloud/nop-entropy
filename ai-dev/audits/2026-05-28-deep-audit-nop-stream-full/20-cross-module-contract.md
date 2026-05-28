# 维度 20：跨模块契约一致性

## 第 1 轮（初审）

### [维度20-01] CepOperator 状态后端 fallback 绕过统一 checkpoint

- **文件**: `nop-stream-cep/.../operator/CepOperator.java:178-200`
- **证据片段**:
  ```java
  if (getKeyedStateBackend() != null) { ... }
  else if (stateBackend != null) { ... }
  else { keyedStateStore = new MemoryKeyedStateBackend<>(Object.class); } // fallback
  ```
- **严重程度**: P1
- **现状**: CepOperator 在没有配置 state backend 时创建独立的 MemoryKeyedStateBackend，完全绕过统一 checkpoint 机制。
- **风险**: CEP 状态不参与 checkpoint，分布式恢复后状态丢失。
- **建议**: 移除 fallback 路径，强制要求使用通过 operator 生命周期注入的标准 IKeyedStateBackend。
- **误报排除**: 不是误报。component-roadmap.md 也标注了此问题。
- **复核状态**: 未复核

### [维度20-02] BatchConsumerSinkFunction 构造器中立即调用 consumerProvider.setup()

- **文件**: `connector/BatchConsumerSinkFunction.java:47-48`
- **严重程度**: P2
- **现状**: 构造时创建 consumer，与 BatchLoaderSourceFunction 的延迟初始化模式不一致。分布式部署时序列化/反序列化会丢失 consumer 状态。
- **建议**: 将 setup() 调用移到 consume() 首次调用时。

### [维度20-03] nop-dao provided scope 与 JdbcCheckpointStorage 紧耦合矛盾

- **文件**: `nop-stream-runtime/pom.xml:35-37`
- **严重程度**: P2
- **现状**: nop-dao 为 provided scope 但 JdbcCheckpointStorage 是核心组件，部署方未提供则 NoClassDefFoundError。
- **建议**: 改为 optional 或将 JdbcCheckpointStorage 拆分到独立子模块。

### [维度20-04] RPC 接口定义在 runtime 而非 core，限制跨模块引用

- **文件**: `runtime/rpc/IStreamTaskRpcService.java`, `IStreamCoordinatorRpcService.java`
- **严重程度**: P2
- **现状**: 控制面接口在 runtime 中，core 无法引用，外部 RPC 框架必须依赖 runtime。
- **建议**: 将 RPC 接口移到 core 或规划的 api 模块。

### [维度20-05] connector 直接实例化 BatchTaskContextImpl 具体实现类

- **文件**: `connector/BatchLoaderSourceFunction.java:53`
- **严重程度**: P2
- **现状**: 直接 new BatchTaskContextImpl()，强耦合到 nop-batch-core 内部实现。
- **建议**: 使用工厂方法或接口构建 IBatchTaskContext。
