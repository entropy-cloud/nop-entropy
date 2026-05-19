# 维度08：IoC 与 Bean 配置

## 第 1 轮（初审）

### [维度08-01] JobTimeoutCheckerImpl 中 setAlarmHandler 缺少 @Inject 注解

- **文件**: nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:71-73
- **证据片段**:
```java
71:     public void setAlarmHandler(IJobAlarmHandler alarmHandler) {
72:         this.alarmHandler = alarmHandler;
73:     }
```
对比同文件其他 setter 的标准模式：
```java
51:     @Inject
52:     public void setTaskStore(IJobTaskStore taskStore) {
53:         this.taskStore = taskStore;
54:     }
```
- **严重程度**: P2（维护成本）
- **现状**: beans.xml 中通过 property 配置了 alarmHandler（app-engine.beans.xml:47），setter 方法本身不需要 @Inject 注解。但代码风格不一致：同文件中 taskStore、fireStore、scheduleStore、cancelHandler 使用了 @Inject 注解，而 alarmHandler 和 namingService 没有。
- **风险**: 1) 代码不一致：部分依赖使用 @Inject 注解，部分依赖通过 beans.xml property 配置，开发者难以判断注入方式；2) 混淆注入语义：@Inject 标注的 setter 表示"必须注入"，而无注解的 setter 表示"可选配置"，但这个区分在当前代码中没有文档化。
- **建议**: 1) 如果 alarmHandler 确实通过 beans.xml 显式配置注入（当前就是如此），在 setter 上添加注释说明注入方式；2) 或者统一风格——所有依赖都通过 @Inject 注入，beans.xml 只用于覆盖特殊场景。
- **误报排除**: 这不是 NopIoC 的"protected 字段注入"误报。这是 setter 方法的注解一致性问题。beans.xml 中确实配置了 alarmHandler 的 property 注入（ref="io.nop.job.api.alarm.IJobAlarmHandler"），所以功能不受影响。问题是代码风格一致性。

---

### [维度08-02] JobTimeoutCheckerImpl 中 setNamingService 缺少 @Inject 注解

- **文件**: nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobTimeoutCheckerImpl.java:75-77
- **证据片段**:
```java
75:     public void setNamingService(INamingService namingService) {
76:         this.namingService = namingService;
77:     }
```
- **严重程度**: P2（维护成本）
- **现状**: beans.xml 中没有配置 namingService（app-engine.beans.xml 中 TimeoutChecker bean 定义没有 namingService property），setter 方法也没有 @Inject 注解。这意味着 namingService 字段始终为 null，JobTimeoutChecker 的集群感知分区功能不工作。
- **风险**: 1) namingService 为 null 时，partitionResolver 无法获取集群实例列表，影响集群环境下的任务分区；2) 依赖注入不明确——从代码看不出 namingService 是否应该被注入。
- **建议**: 1) 如果集群功能需要 namingService，在 beans.xml 中添加 property 配置或添加 @Inject 注解；2) 如果 namingService 确实可选（单机模式不需要），在 setter 上添加注释说明其可选性。
- **误报排除**: 这不是 Nop 平台的"protected 字段注入"误报。这是 setter 注入配置缺失问题，影响集群环境的功能正确性。

---

### [维度08-03] JobCompletionProcessorImpl 中 setCompletionMetrics 缺少 @Inject 注解

- **文件**: nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobCompletionProcessorImpl.java:66-68
- **证据片段**:
```java
66:     public void setCompletionMetrics(IJobCompletionMetrics completionMetrics) {
67:         this.completionMetrics = completionMetrics;
68:     }
```
对比默认初始化：
```java
42:     private IJobCompletionMetrics completionMetrics = new EmptyJobCompletionMetrics();
```
- **严重程度**: P2（维护成本）
- **现状**: beans.xml 中没有配置 completionMetrics（app-engine.beans.xml:37-42），setter 方法也没有 @Inject 注解。类中使用默认实现 `new EmptyJobCompletionMetrics()`。这意味着完成指标收集功能默认不工作（EmptyJobCompletionMetrics 是空实现）。
- **风险**: 1) 代码不一致：其他 scanner 的 metrics 通过 beans.xml property 配置注入（如 plannerMetrics、dispatcherMetrics），但 completionMetrics 使用默认空实现；2) 如需切换 metrics 实现，需要修改代码而非配置。
- **建议**: 1) 如果 completionMetrics 确实不需要可配置性，保持现状即可（EmptyJobCompletionMetrics 是合理的默认值）；2) 如果需要与其他 metrics 保持一致的配置方式，在 beans.xml 中添加配置。
- **误报排除**: 不是误报。虽然功能不受影响（有空实现兜底），但代码风格不一致。

---

### [维度08-04] JobPlannerScannerImpl 中 setPlannerMetrics 缺少 @Inject 注解

- **文件**: nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:48-50
- **证据片段**:
```java
48:     public void setPlannerMetrics(IJobPlannerMetrics plannerMetrics) {
49:         this.plannerMetrics = plannerMetrics;
50:     }
```
对比 beans.xml 配置：
```xml
<!-- app-engine.beans.xml:17-23 -->
17:     <bean id="io.nop.job.coordinator.engine.IJobPlannerScanner"
18:           class="io.nop.job.coordinator.engine.JobPlannerScannerImpl">
19:         <property name="plannerMetrics">
20:             <bean class="io.nop.job.coordinator.metrics.JobPlannerMetricsImpl"/>
21:         </property>
22:         <property name="partitionResolver" ref="jobPartitionResolver"/>
23:     </bean>
```
- **严重程度**: P2（维护成本）
- **现状**: beans.xml 中通过 property 配置了 plannerMetrics（app-engine.beans.xml:19-21），但 Java setter 方法缺少 @Inject 注解。与 partitionResolver（有 @Inject 注解，行 52-53）风格不一致。
- **风险**: 代码风格不一致——同文件中 scheduleStore 和 partitionResolver 使用 @Inject 注解，但 plannerMetrics 通过 beans.xml property 配置。开发者难以判断注入方式。
- **建议**: 统一风格——要么所有依赖都使用 @Inject，要么所有依赖都通过 beans.xml property 配置。当前混用增加了理解成本。
- **误报排除**: 不是误报。beans.xml 配置确实注入了 plannerMetrics，功能不受影响。但代码风格不一致。

---

### [维度08-05] JobDispatcherScannerImpl 中 setDispatcherMetrics 缺少 @Inject 注解

- **文件**: nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobDispatcherScannerImpl.java:47-49
- **证据片段**:
```java
47:     public void setDispatcherMetrics(IJobDispatcherMetrics dispatcherMetrics) {
48:         this.dispatcherMetrics = dispatcherMetrics;
49:     }
```
- **严重程度**: P2（维护成本）
- **现状**: 与发现 04 相同的模式——beans.xml 中通过 property 配置了 dispatcherMetrics，但 Java setter 方法缺少 @Inject 注解。与同文件中 fireStore、defaultTaskBuilder、partitionResolver（均有 @Inject 注解）风格不一致。
- **风险**: 代码风格不一致，增加理解成本。
- **建议**: 同发现 04，统一风格。
- **误报排除**: 不是误报。功能不受影响，但代码风格不一致。

---

### [维度08-06] JobPartitionResolver 中 setNamingService 缺少 @Inject 注解

- **文件**: nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPartitionResolver.java:29-31
- **证据片段**:
```java
29:     public void setNamingService(INamingService namingService) {
30:         this.namingService = namingService;
31:     }
```
- **严重程度**: P2（维护成本）
- **现状**: beans.xml 中没有为 jobPartitionResolver bean 配置 namingService property。这意味着在集群模式下，partitionResolver 无法获取命名服务，分区分配功能不工作。
- **风险**: 1) 集群模式下分区分配功能不可用；2) namingService 为 null 时，resolvePartitions() 方法会跳过集群逻辑（代码行 59: `if (!enableCluster || namingService == null)`），降级为单机模式。当前行为是安全降级，但缺少文档说明。
- **建议**: 1) 如果需要集群分区功能，在 beans.xml 中为 jobPartitionResolver bean 添加 namingService property 配置；2) 或者通过 @Inject 注解自动注入 namingService。
- **误报排除**: 不是误报。集群分区功能在当前配置下不工作（namingService 始终为 null），虽然有安全降级（降级为单机模式）。

---

## 总结

IoC 与 Bean 配置审计共发现 6 个 P2 级别问题，无 P0 或 P1 缺陷。

所有发现的共同主题是 **setter 注入风格不一致**：
- 部分 setter 使用 `@Inject` 注解（如 taskStore、fireStore、scheduleStore）
- 部分 setter 仅通过 beans.xml property 配置注入（如 alarmHandler、plannerMetrics、dispatcherMetrics）
- 部分 setter 既无 @Inject 也无 beans.xml 配置（如 namingService），导致功能降级

**影响范围**: 功能层面影响较小——beans.xml 的 property 配置确保了核心依赖的注入。主要问题是代码一致性和可维护性。

**优先建议**: 统一 setter 注入风格，为可选依赖添加注释说明。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 08-01 | setAlarmHandler 缺少 @Inject | 降级 P3 | beans.xml L47 确认通过 property 配置注入，功能正常。同文件 4 个 setter 有 @Inject、2 个没有，纯代码风格不一致。 |
| 08-02 | setNamingService 缺少 @Inject（TimeoutChecker） | 保留 P2 | beans.xml 确认未配置 namingService，setter 也无 @Inject，字段始终 null。集群感知分区功能不工作，虽有 null 安全降级（L59 `if (namingService == null)`）但未文档化。 |
| 08-03 | setCompletionMetrics 缺少 @Inject | 降级 P3 | 有默认空实现 `EmptyJobCompletionMetrics` 兜底（L42），功能不受影响。配置风格不统一属维护成本问题。 |
| 08-04 | setPlannerMetrics 缺少 @Inject | 降级 P3 | beans.xml L19-21 确认通过 property 配置注入，功能正常。同文件 partitionResolver 有 @Inject 但 plannerMetrics 没有，纯风格问题。 |
| 08-05 | setDispatcherMetrics 缺少 @Inject | 降级 P3 | 同 08-04 模式，beans.xml 配置注入功能正常。 |
| 08-06 | JobPartitionResolver setNamingService 缺少 @Inject | 保留 P2 | 与 08-02 同一问题。namingService 既无 @Inject 也无 beans.xml 配置，集群分区功能完整链路不工作。应与 08-02 合并修复。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 08-02 | P2 | `JobTimeoutCheckerImpl.java` L75 | namingService 无 @Inject 且 beans.xml 未配置，集群感知分区功能不工作 |
| 08-06 | P2 | `JobPartitionResolver.java` L29 | 同 08-02，集群分区完整链路因 namingService 为 null 而失效 |
| 08-01 | P3 | `JobTimeoutCheckerImpl.java` L71 | setAlarmHandler 缺 @Inject，beans.xml 已配置 property 注入 |
| 08-03 | P3 | `JobCompletionProcessorImpl.java` L66 | completionMetrics 有空实现兜底，配置风格不统一 |
| 08-04 | P3 | `JobPlannerScannerImpl.java` L48 | plannerMetrics 缺 @Inject，beans.xml 已配置 property 注入 |
| 08-05 | P3 | `JobDispatcherScannerImpl.java` L47 | dispatcherMetrics 缺 @Inject，beans.xml 已配置 property 注入 |
