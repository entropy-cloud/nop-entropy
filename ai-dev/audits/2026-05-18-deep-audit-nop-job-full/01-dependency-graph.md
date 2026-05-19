# 维度01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] DAO 层反向依赖 Core 层，违反标准分层架构

- **文件**: nop-job/nop-job-dao/pom.xml:31-34
- **证据片段**:
```xml
31:         <dependency>
32:             <groupId>io.github.entropy-cloud</groupId>
33:             <artifactId>nop-job-core</artifactId>
34:         </dependency>
```
附加证据（实际代码使用）：
```java
// nop-job-dao/.../store/JobFireStoreImpl.java:12-14
12: import io.nop.job.core.ITriggerEvalContext;
13: import io.nop.job.core._NopJobCoreConstants;
14: import io.nop.job.core.trigger.JobTriggerCalculator;
// nop-job-dao/.../store/JobFireStoreImpl.java:32
32: import static io.nop.job.core.JobCoreErrors.ERR_JOB_CANCELED;

// nop-job-dao/.../store/JobScheduleStoreImpl.java:11
11: import io.nop.job.core._NopJobCoreConstants;
// nop-job-dao/.../store/JobScheduleStoreImpl.java:30
30: import static io.nop.job.core.JobCoreErrors.ERR_JOB_OVERLAID;
```
- **严重程度**: P1
- **现状**: nop-job-dao 模块在编译时直接依赖 nop-job-core，标准分层规则要求"dao 层只依赖 api 和 nop-persistence 框架"，此处是反向依赖。实际代码中 JobFireStoreImpl.java 和 JobScheduleStoreImpl.java 确实使用了 core 包中的类（ITriggerEvalContext、JobTriggerCalculator、_NopJobCoreConstants、JobCoreErrors）。
- **风险**: 1) 循环依赖风险：如果 future core 层需要复用 DAO 实体类，将形成 A→B→A 的循环依赖导致 Maven 构建失败；2) 职责边界模糊：DAO 层包含了触发器计算等业务逻辑，违背了 DAO 层只负责数据持久化的单一职责原则；3) 模块重用困难：其他模块无法独立使用 nop-job-dao 而不引入 core 层依赖；4) 测试隔离差：DAO 单元测试需要加载 core 层，增加了测试复杂度和运行时间。
- **建议**: 1) 将触发器计算逻辑（JobTriggerCalculator、ITriggerEvalContext）移至 core 层独立包，或新建独立的 job-calculation 模块；2) DAO 层的 Store 实现类中移除触发器计算逻辑，改为调用 core 层提供的计算服务；3) 触发器时间计算应在 DAO 外部（coordinator 或 core 层）完成，DAO 层只负责存储和查询。
- **误报排除**: 这不是"多模块 pom.xml 中存在必要的传递依赖声明"的误报。core 层包含业务逻辑（触发器计算、错误码常量），DAO 层依赖业务逻辑层是标准分层规则的明确违规。JobFireStoreImpl.java 在 DAO 层直接调用 JobTriggerCalculator.calculateNextFireTime()，这是业务逻辑调用，不属于数据持久化职责。

---

### [维度01-02] nop-job-meta 仅有 test scope 依赖，无编译时依赖

- **文件**: nop-job/nop-job-meta/pom.xml:15-27
- **证据片段**:
```xml
15:     <dependencies>
16:         <dependency>
17:             <artifactId>nop-job-codegen</artifactId>
18:             <groupId>io.github.entropy-cloud</groupId>
19:             <version>2.0.0-SNAPSHOT</version>
20:             <scope>test</scope>
21:         </dependency>
22:         <dependency>
23:             <artifactId>nop-job-dao</artifactId>
24:             <groupId>io.github.entropy-cloud</groupId>
25:             <scope>test</scope>
26:         </dependency>
27:     </dependencies>
```
- **严重程度**: P2
- **现状**: nop-job-meta 模块的所有业务依赖均为 test scope。这意味着该模块的编译产物（jar）不包含任何运行时依赖，仅通过 precompile/postcompile 的 Maven exec 插件在构建时运行代码生成脚本。这在 Nop 平台的 meta 模块中是标准模式——meta 模块的任务是在构建期生成 XMeta 文件，运行时不需要这些依赖。
- **风险**: 如果未来需要在运行时动态生成或刷新 meta 文件，当前配置不支持。但以当前平台设计（构建时生成），这是正确的。
- **建议**: 无需修改。这符合 Nop 平台 meta 模块的标准模式。meta 模块通过 Maven exec 插件的 `classpathScope=test` 配置（pom.xml:35）来确保测试 classpath 包含 codegen 和 dao 依赖，用于构建时代码生成。
- **误报排除**: 初审标记为问题，但经复核确认为 Nop 平台标准模式。meta 模块在构建期运行 gen-meta.xgen 脚本，依赖 test scope + `classpathScope=test` 的 Maven 配置即可满足需求。

---

### [维度01-03] nop-job-web 仅以 test scope 依赖 nop-job-service

- **文件**: nop-job/nop-job-web/pom.xml:21-25
- **证据片段**:
```xml
21:         <dependency>
22:             <artifactId>nop-job-service</artifactId>
23:             <groupId>io.github.entropy-cloud</groupId>
24:             <scope>test</scope>
25:         </dependency>
```
- **严重程度**: P2
- **现状**: nop-job-web 模块对 nop-job-service 的依赖是 test scope。编译时依赖为 nop-job-meta 和 nop-web。标准分层规则要求"web 层依赖 service"，但实际 nop-job-web 的编译产物不直接引用 service 层代码——它通过 nop-job-meta 传递的 XView/XPage 文件在运行时由框架解析。
- **风险**: 如果 web 层需要直接引用 service 层的类（如自定义 Controller），当前配置不支持，需要修改 scope。但 Nop 平台的 web 模块通常通过 xview 文件定义前端页面，不需要直接引用 service 层 Java 代码。
- **建议**: 无需修改。这符合 Nop 平台 web 模块的标准模式。web 模块通过 XView 文件间接引用 service，service 层依赖仅在测试时（autotest）需要。
- **误报排除**: 初审标记为问题，但经复核确认为 Nop 平台标准模式。Nop 的 web 层通过 XView + GraphQL 引擎间接调用 service，不需要编译时依赖 service 模块。

## 深挖第 2 轮追加

### [01-04] P2 — coordinator 和 worker 通过传递依赖隐式使用 `nop-core` 的工具类，未显式声明

**文件**：`nop-job/nop-job-coordinator/pom.xml` 和 `nop-job/nop-job-worker/pom.xml`

**证据**：
```xml
<!-- coordinator/pom.xml 编译时依赖 -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-job-dao</artifactId>
</dependency>
<!-- nop-job-dao 传递依赖 nop-job-core，nop-job-core 传递依赖 nop-api-core → nop-commons → nop-core -->
```

```java
// JobPlannerScannerImpl.java (coordinator 模块) 使用了 nop-core 的类：
import io.nop.commons.util.StringHelper;  // 来自 nop-core 传递
import io.nop.api.core.util.Guard;        // 来自 nop-api-core 传递
```

**严重程度**：P2

**现状**：coordinator 和 worker 模块依赖 nop-job-dao，而 nop-job-dao → nop-job-core → nop-api-core → nop-commons → nop-core 形成了长传递链。coordinator 和 worker 的代码中直接使用了 `StringHelper`、`Guard`、`ICancelToken` 等 nop-core 层的工具类，但 pom.xml 中未显式声明对 nop-core 的依赖。

**风险**：(1) Maven 依赖调解可能导致传递路径变化时编译失败；(2) 违反"显式声明直接使用的依赖"原则；(3) 模块独立编译时可能缺少必要的类。

**建议**：在 coordinator 和 worker 的 pom.xml 中显式声明对 `nop-api-core` 的依赖（不强制声明整个传递链，只声明直接使用的最远模块）。这是 Maven 最佳实践但不影响当前构建。

**误报排除**：不是误报。虽然当前构建通过传递依赖能正常工作，但这是 Maven 依赖管理的最佳实践违规。Nop 平台其他模块（如 nop-job-dao）也显式声明了对 nop-job-core 的依赖。

---

### [01-05] P2 — nop-job-retry-adapter 依赖 nop-ioc 可能过于重量级

**文件**：`nop-job/nop-job-retry-adapter/pom.xml`

**证据**：
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-ioc</artifactId>
</dependency>
```

**严重程度**：P2

**现状**：nop-job-retry-adapter 是一个轻量级的桥接模块（仅 `NopRetryJobRetryBridge` 一个类），将 nop-retry 的失败重试适配到 nop-job 的 fire 重发流程。它依赖 nop-ioc 仅用于 `@Inject` 注解和 Bean 查找。

**风险**：nop-ioc 是重量级依赖（包含 XDSL 解析、bean 容器实现等），仅用于注解和 Bean 查找过于重量级。如果将来需要独立使用 retry-adapter，会引入不必要的依赖。

**建议**：考虑将对 nop-ioc 的依赖降级为 nop-api-core（提供 `@Inject` 注解）+ nop-core（提供 BeanContainer 查找），或保持现状但标记为已知技术债。影响较小，低优先级。

**误报排除**：不是误报。NopRetryJobRetryBridge 确实只使用了 `@Inject` 和 `BeanContainer.tryGetBean()`，nop-ioc 提供的功能远超所需。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 01-01 | DAO层反向依赖Core层 | 保留 P1 | 证据准确。`nop-job-dao/pom.xml:31-34` 确实依赖 `nop-job-core`。`JobFireStoreImpl.java` 导入 `ITriggerEvalContext`、`JobTriggerCalculator`、`_NopJobCoreConstants`，`JobScheduleStoreImpl.java` 导入 `_NopJobCoreConstants` 和 `JobCoreErrors.ERR_JOB_OVERLAID`。DAO层包含触发器计算等业务逻辑，属于分层违规。 |
| 01-02 | nop-job-meta 仅有 test scope 依赖 | 驳回 | 审计自身已确认为 Nop 标准模式。meta 模块通过 `classpathScope=test` 的 Maven 配置在构建时运行 gen 脚本，运行时不需要这些依赖。 |
| 01-03 | nop-job-web 仅以 test scope 依赖 service | 驳回 | 审计自身已确认为 Nop 标准模式。web 层通过 XView + GraphQL 引擎间接调用 service，不需要编译时依赖 service 模块。 |
| 01-04 | coordinator/worker 隐式依赖 nop-core | 降级为 P3 | 传递链 `nop-job-dao → nop-job-core → nop-api-core` 稳定，当前构建无影响。属于 Maven 最佳实践问题，优先级应低于结构性问题。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 01-01 | P1 | `nop-job-dao/pom.xml` | DAO层反向依赖Core层，包含触发器计算等业务逻辑 |
| 01-04 | P3 | `nop-job-coordinator/pom.xml`, `nop-job-worker/pom.xml` | coordinator/worker 隐式依赖 nop-core（传递链稳定） |
