# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-job-dao 依赖 nop-job-core：dao 层向上依赖 core 层

- **文件**: `nop-job/nop-job-dao/pom.xml:31-34`
- **证据片段**:
  ```xml
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-job-core</artifactId>
  </dependency>
  ```
- **严重程度**: P2
- **现状**: `nop-job-dao` 显式依赖 `nop-job-core`，dao 层手写代码 `TriggerSpecHelper`、`JobScheduleStoreImpl`、`JobFireStoreImpl`、`JobTaskStoreImpl` 均引用 `io.nop.job.core` 包中的类型（`ITriggerEvalContext`、`_NopJobCoreConstants`、`JobTriggerCalculator`、`JobCoreErrors`）。
- **风险**: 按严格分层规则（规则 2），dao 层不应依赖 core 层。但在 nop-job 中，core 层是纯计算逻辑（trigger/calendar），不涉及持久化或框架核心。dao 层的 Store 实现需要使用 core 层的常量和 trigger 计算逻辑来构建查询条件并推进调度状态。若将 core 常量和 trigger 计算拆入 dao 或 api，则会在 api 层引入不必要的计算复杂度。
- **建议**: 这是 nop-job 模块的特殊架构决策。core 层的常量和 trigger 逻辑被 dao 层的 Store 实现深度使用，将其视为"dao 的计算扩展"而非"业务层向上反依赖"更合理。建议在模块文档中明确记录这一设计决策。不修复也可接受。
- **信心水平**: 确定
- **误报排除**: 这不是"看起来不优雅"的误报。dao→core 确实违反了严格分层规则。但经分析实际代码，core 是纯计算工具层，无框架依赖，且被 dao 的 Store 实现深度耦合使用。作为架构例外有充分理由。
- **复核状态**: 未复核

---

### [维度01-02] nop-job-dao 手写代码使用 nop-core 但未显式声明依赖

- **文件**: `nop-job/nop-job-dao/pom.xml`（缺失依赖声明）
- **严重程度**: P3
- **现状**: `TriggerSpecHelper.java`（第 3-4 行）import `io.nop.core.lang.json.JsonTool` 和 `io.nop.core.type.utils.JavaGenericTypeBuilder`，但 `nop-job-dao` 的 pom.xml 未声明对 `nop-core` 的依赖。该依赖通过 `nop-orm → nop-dao → nop-core` 传递获得，编译和运行时不会报错。
- **风险**: 传递依赖路径较长，若未来 nop-persistence 层重构移除对 nop-core 的传递，nop-job-dao 的 `TriggerSpecHelper` 会编译失败。维护者不易追踪此隐性依赖。
- **建议**: 在 nop-job-dao 的 pom.xml 中显式添加 `nop-core` 作为 compile scope 依赖，或将 `TriggerSpecHelper` 中对 `JsonTool` 和 `JavaGenericTypeBuilder` 的使用替换为 nop-api-core 或 nop-orm 层的等效 API。
- **信心水平**: 确定
- **误报排除**: **误报（过度严格）。** `nop-core` 通过 `nop-orm → nop-dao → nop-core` 稳定传递，`nop-orm` 是 dao 层核心固定依赖，传递链不会被打断。在 Nop 项目中，`nop-core` 是平台基础层，几乎所有模块都通过传递路径使用其类（`JsonTool`、`IServiceContext` 等），要求每个模块显式声明属形式主义。
- **复核状态**: 已复核（误报，关闭）

**证据** (`nop-job-dao/src/main/java/io/nop/job/dao/helper/TriggerSpecHelper.java` 第 3-4 行):
```java
import io.nop.core.lang.json.JsonTool;
import io.nop.core.type.utils.JavaGenericTypeBuilder;
```

---

### [维度01-03] nop-job-coordinator 和 nop-job-worker 手写代码使用 nop-core 但未显式声明依赖

- **文件**: `nop-job/nop-job-coordinator/pom.xml`（缺失）、`nop-job/nop-job-worker/pom.xml`（缺失）
- **严重程度**: P3
- **现状**: 
  - `JobCompletionProcessorImpl.java`（coordinator, 第 8 行）import `io.nop.core.lang.json.JsonTool`
  - `JobWorkerScannerImpl.java`（worker, 第 10 行）import `io.nop.core.lang.json.JsonTool`
  两个模块的 pom.xml 均未声明 `nop-core` 依赖，通过 `nop-job-dao → nop-orm → nop-dao → nop-core` 传递获得。
- **风险**: 与发现 02 相同，传递依赖链路脆弱。
- **建议**: 在 coordinator 和 worker 的 pom.xml 中显式声明 `nop-core` 依赖，或替换 `JsonTool` 的使用为 `nop-api-core` 中的等效工具。
- **信心水平**: 确定
- **误报排除**: 与发现 02 同理，手写代码直接 import `io.nop.core.*`，应显式声明。
- **复核状态**: 未复核

---

### [维度01-04] nop-job-dao 的 I*Biz 接口使用 nop-core 的 IServiceContext

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/biz/INopJobScheduleBiz.java:7`、`INopJobFireBiz.java:6`
- **严重程度**: P3
- **现状**: `INopJobScheduleBiz` 和 `INopJobFireBiz` 接口 import `io.nop.core.context.IServiceContext`，但 nop-job-dao 未显式声明 nop-core 依赖。
- **风险**: 与发现 02 相同类型的隐性传递依赖问题。I*Biz 接口作为跨模块契约，隐性依赖更难追踪。
- **建议**: 显式声明 nop-core 依赖。或者按 Nop 平台惯例（I*Biz 接口放在 dao 层是标准模式），这属于平台约定的可接受隐性依赖，维持现状也可接受。
- **信心水平**: 很可能
- **误报排除**: **误报。** `nop-core` 通过 `nop-job-dao → nop-orm → nop-dao → nop-core` 稳定传递，`nop-orm` 是 dao 层的核心固定依赖，此传递链不会被打断。`IServiceContext` 是平台基础接口，被几乎所有模块使用。要求每个模块都显式声明 `nop-core` 是形式主义，不符合项目实际实践。维持现状即可。
- **复核状态**: 已复核（误报，关闭）

---

## 依赖图

```
nop-job-api
  └── nop-api-core

nop-job-core
  ├── nop-commons
  └── nop-job-api

nop-job-codegen
  ├── nop-ooxml-xlsx
  └── nop-orm

nop-job-dao
  ├── nop-api-core, nop-orm, nop-job-api, nop-job-core

nop-job-coordinator
  ├── nop-job-dao, nop-job-core, nop-job-api, nop-config, nop-ioc, nop-cluster-core

nop-job-worker
  ├── nop-job-dao, nop-job-api, nop-job-core, nop-config, nop-ioc

nop-job-meta
  └── [test only: nop-job-codegen, nop-job-dao]

nop-job-service
  ├── nop-job-dao, nop-job-meta, nop-job-core, nop-biz, nop-config, nop-ioc

nop-job-web
  └── nop-job-meta

nop-job-app
  ├── nop-quarkus-web-orm-starter, nop-job-service, nop-job-coordinator, nop-job-worker, nop-job-web, nop-auth-web, nop-auth-service, quarkus-jdbc-mysql, quarkus-jdbc-h2

nop-job-retry-adapter
  ├── nop-job-api, nop-retry-engine, nop-ioc
```

**循环依赖**: 无。所有依赖箭头均指向 api 方向，无反向依赖，无环路。
