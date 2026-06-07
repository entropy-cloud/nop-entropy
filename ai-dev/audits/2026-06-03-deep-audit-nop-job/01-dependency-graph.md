# 维度 01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-1] nop-job-dao 对 nop-cluster-core 的 compile 依赖无实际代码使用

- **文件**: `nop-job/nop-job-dao/pom.xml:36-39`
- **证据片段**:
```xml
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-cluster-core</artifactId>
        </dependency>
```
- **严重程度**: P2
- **现状**: nop-job-dao 在 pom.xml 中声明了对 nop-cluster-core 的 compile 依赖，但在 dao 模块的所有手写 Java 文件和 beans XML 中，没有任何 import 引用 `io.nop.cluster.*`。
- **风险**: 引入了不必要的传递依赖，会让所有依赖 dao 的上游模块（coordinator、worker、service）都引入 nop-cluster-core，增加了依赖膨胀。
- **建议**: 将 `nop-cluster-core` 从 nop-job-dao 的 compile 依赖移除（或改为 provided/test）。
- **信心水平**: 确定
- **误报排除**: 已通过 grep 搜索确认零引用。dao 层引入与持久化无关的 cluster 依赖，违反规则2。
- **复核状态**: 未复核

### [维度01-2] nop-job-service 对 nop-sys-dao 的 compile 依赖未被 Java 代码使用

- **文件**: `nop-job/nop-job-service/pom.xml:43-46`
- **证据片段**:
```xml
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-sys-dao</artifactId>
        </dependency>
```
- **严重程度**: P2
- **现状**: nop-job-service 声明了对 `nop-sys-dao` 的 compile 依赖。但 service 模块中所有 Java 代码均未 import 任何 `io.nop.sys.*` 包，XML 配置也未引用 sys-dao 的 bean。
- **风险**: 不必要的跨模块耦合。nop-sys-dao 引入了系统表的 ORM 实体定义。
- **建议**: 确认是否存在间接使用。如果否，移除。
- **信心水平**: 很可能
- **误报排除**: Java 代码搜索零命中，引入了另一个业务模块的 dao 层，属于跨模块边界问题。
- **复核状态**: 未复核

### [维度01-3] nop-job-service 对 nop-rpc-cluster 的 compile 依赖仅在 RpcJobInvoker 中间接使用

- **文件**: `nop-job/nop-job-service/pom.xml:47-50`
- **证据片段**:
```xml
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-rpc-cluster</artifactId>
        </dependency>
```
- **严重程度**: P3
- **现状**: `RpcJobInvoker.java` 使用了 `IRpcServiceInvoker`（来自 `nop-api-core`），但实际实现由 `nop-rpc-cluster` 提供。service 层仅通过 IoC 注入接口。
- **风险**: 理论上可将该依赖移至 nop-job-app，但当前在 service 中声明也可接受。
- **建议**: 保持现状可接受。
- **信心水平**: 很可能
- **误报排除**: 有明确的间接使用（IoC 注入 `IRpcServiceInvoker` 的实现），属于"接口在 api、实现在 rpc-cluster"的标准模式。
- **复核状态**: 未复核

### [维度01-4] nop-job-web compile 依赖正确：仅 meta + web

- **文件**: `nop-job/nop-job-web/pom.xml:15-25`
- **证据片段**:
```xml
        <dependency>
            <artifactId>nop-job-meta</artifactId>
            <groupId>io.github.entropy-cloud</groupId>
        </dependency>
        <dependency>
            <artifactId>nop-job-service</artifactId>
            <groupId>io.github.entropy-cloud</groupId>
            <scope>test</scope>
        </dependency>
```
- **严重程度**: 合规确认（非问题）
- **现状**: nop-job-web compile 依赖只有 `nop-job-meta` 和 `nop-web`，service 为 test scope。
- **信心水平**: 确定
- **复核状态**: 已保留（合规确认）

### [维度01-5] nop-job-codegen 不依赖 api/core

- **严重程度**: 合规确认（非问题）
- **现状**: codegen 只依赖 `nop-ooxml-xlsx`（读 Excel 模型）和 `nop-orm`（ORM 元模型），标准模式。
- **信心水平**: 确定
- **复核状态**: 已保留（合规确认）

### [维度01-6] coordinator/worker 显式声明传递依赖

- **严重程度**: P3（信息性）
- **现状**: coordinator 和 worker 显式声明了 `nop-job-dao` + `nop-job-core` + `nop-job-api`，但 dao 已传递依赖 core 和 api。显式声明是 Maven 惯用做法。
- **建议**: 保持现状。
- **信心水平**: 确定
- **复核状态**: 未复核

### [维度01-7] nop-job-dao 依赖 nop-job-core 但 Store 层需要 trigger 计算逻辑

- **文件**: `nop-job/nop-job-dao/pom.xml:31-34`
- **证据片段**:
```xml
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-job-core</artifactId>
        </dependency>
```
- **严重程度**: P3
- **现状**: dao 层的 Store 实现类大量使用了 `nop-job-core` 中的 trigger 计算逻辑。违反"dao 层只依赖 api 和 persistence 框架"的规则，但是有意的架构选择：Store 层需要在数据库操作时同步计算下一次触发时间。
- **风险**: dao 和 core 之间存在真实逻辑耦合，不是简单的边界疏忽。
- **建议**: 已知的架构折衷，当前不建议改动。可考虑将 trigger 接口提取到 api 中。
- **信心水平**: 确定
- **误报排除**: 有 3 个手写 Java 文件 import 了 core 包中的类，是真实使用场景。
- **复核状态**: 未复核

### [维度01-8] coordinator/worker 使用 nop-core 但无显式依赖

- **严重程度**: P3（信息性）
- **现状**: coordinator 和 worker 使用 `io.nop.core.lang.json.JsonTool`，通过传递链获得。
- **建议**: Nop 平台常见模式，不建议增加显式声明。
- **信心水平**: 确定
- **复核状态**: 未复核

## 依赖图

```
nop-job-api → nop-api-core
nop-job-core → nop-commons + nop-job-api
nop-job-codegen → nop-ooxml-xlsx + nop-orm
nop-job-dao → nop-api-core + nop-orm + nop-job-api + nop-job-core + nop-cluster-core
nop-job-meta → (test: nop-job-codegen + nop-job-dao)
nop-job-coordinator → nop-job-dao + nop-job-core + nop-job-api + nop-config + nop-ioc + nop-cluster-core
nop-job-worker → nop-job-dao + nop-job-api + nop-job-core + nop-config + nop-ioc
nop-job-service → nop-job-dao + nop-job-meta + nop-job-core + nop-biz + nop-config + nop-ioc + nop-sys-dao + nop-rpc-cluster
nop-job-web → nop-job-meta + nop-web (test: nop-job-service)
nop-job-app → nop-quarkus-web-orm-starter + all submodules + nop-auth-web + nop-auth-service
nop-job-retry-adapter → nop-job-api + nop-retry-engine + nop-ioc
```

无循环依赖。api→core→dao→coordinator/worker/service→web→app 方向正确。
