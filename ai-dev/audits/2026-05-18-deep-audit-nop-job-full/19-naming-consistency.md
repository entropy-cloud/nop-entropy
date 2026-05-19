# 维度19：命名与术语一致性

## 第 1 轮（初审）

**审计范围**: nop-job 模块全部子模块（nop-job-api, nop-job-core, nop-job-dao, nop-job-service, nop-job-coordinator, nop-job-worker, nop-job-web, nop-job-retry-adapter）

**审计日期**: 2026-05-18

**检查项**:
1. 实体名在 ORM、BizModel、接口、文档中是否一致
2. 字段名在数据库列名（snake_case）、Java 属性名（camelCase）、GraphQL 字段名之间是否一致
3. 同一概念是否使用了不同名称（status/state、type/kind 等）
4. 错误码前缀是否与模块名一致
5. bean 名称是否与类名有合理的对应关系
6. xmeta 中的 displayName 是否与实体的 displayName 定义一致
7. 是否有违反 Java 命名惯例的自定义命名模式

---

### 发现 1：jobGroup 与 groupId 命名不一致

**文件**: `nop-job-api/src/main/java/io/nop/job/api/JobInstanceState.java` 行 20

**严重程度**: **P2**

**证据** (7 行):
```java
// JobInstanceState.java:20
private String jobGroup;   // API 层用 jobGroup

// ORM 实体 NopJobSchedule, nop-job.orm.xml:94
<column code="GROUP_ID" displayName="分组" name="groupId" .../>

// DefaultJobInvokerResolver.java:21
.param("jobGroup", schedule.getGroupId());  // 混用：参数名 jobGroup，getter 是 getGroupId()

// IJobInstanceState 接口也暴露 jobGroup
// JobInstanceState.java:111-114
public String getJobGroup() { return jobGroup; }
public void setJobGroup(String jobGroup) { this.jobGroup = jobGroup; }
```

**现状**: API 层 (`JobInstanceState`, `IJobInstanceState`, `TriggerSpec`) 统一使用 `jobGroup`，而 ORM 实体 (`NopJobSchedule`, `NopJobFire`) 的 Java 属性名是 `groupId`（数据库列 `GROUP_ID`）。业务代码中存在混用：`DefaultJobInvokerResolver` 第 21、31 行用 `"jobGroup"` 作参数名，但值来自 `schedule.getGroupId()`。

**风险**: 同一概念两套名字增加认知负担；新开发者可能误以为 jobGroup 和 groupId 是不同字段；GraphQL/REST API 消费者看到的字段名与数据库模型不一致。

**建议**: 统一为 `groupId`。理由：(1) 数据库列 `GROUP_ID` → camelCase 自然映射为 `groupId`；(2) `jobGroup` 易与 `jobName` 混淆，语义不如 `groupId` 清晰；(3) 修改 API 接口 `JobInstanceState.jobGroup` → `groupId` 并更新 `IJobInstanceState` 及所有实现。

**误报排除**: 非有意设计差异，属历史遗留。ORM 模型始终使用 `groupId`，API 层的 `jobGroup` 应是对齐遗漏。

---

### 发现 2：错误码前缀不一致

**文件**: `nop-job-core/src/main/java/io/nop/job/core/JobCoreErrors.java` 行 24-37

**严重程度**: **P1**

**证据** (10 行):
```java
// JobCoreErrors.java:24-37
ErrorCode ERR_JOB_TIMEOUT = define("JOB_TIMEOUT",
        "Job task timed out");

ErrorCode ERR_JOB_INVOKER_NOT_FOUND = define("JOB_INVOKER_NOT_FOUND",
        "Job invoker not found for schedule");

ErrorCode ERR_JOB_CANCELED = define("JOB_CANCELED",
        "Job fire/task canceled");

ErrorCode ERR_JOB_OVERLAID = define("JOB_OVERLAID",
        "Job fire/task canceled by overlay");

ErrorCode ERR_JOB_EXECUTION_FAILED = define("JOB_EXECUTION_FAILED",
        "Job execution failed");
```

对比同文件中的合规前缀：
```java
// JobCoreErrors.java:15-19, 39-43
ErrorCode ERR_JOB_TRIGGER_LOOP_COUNT_EXCEED_LIMIT = define("nop.err.job.trigger.loop-count-exceed-limit", ...);
ErrorCode ERR_JOB_TRIGGER_PARSE_CRON_EXPR_FAIL   = define("nop.err.job.trigger.parse-cron-expr-fail", ...);
ErrorCode ERR_JOB_EXECUTOR_REF_EMPTY               = define("nop.err.job.executor-ref-empty", ...);
ErrorCode ERR_JOB_EXECUTOR_KIND_EMPTY              = define("nop.err.job.executor-kind-empty", ...);
```

对比 NopJobErrors：
```java
// NopJobErrors.java:13-15
ErrorCode ERR_JOB_SCHEDULE_ALREADY_ARCHIVED = ErrorCode.define(
        "nop.err.job.schedule.already-archived", ...);
```

**现状**: `JobCoreErrors` 中 5 个错误码（`JOB_TIMEOUT`, `JOB_INVOKER_NOT_FOUND`, `JOB_CANCELED`, `JOB_OVERLAID`, `JOB_EXECUTION_FAILED`）使用裸大写 `JOB_*` 前缀而非项目规范 `nop.err.job.*` 点分前缀。同文件另外 4 个错误码使用合规前缀。注释解释这是为向后兼容（这些值会写入 `errorCode` 数据库列），但违反了全平台错误码命名规范。

**风险**: 不符合平台错误码命名规范；这些裸字符串可能与其他系统或框架的错误码冲突；日志/监控工具依赖 `nop.err.` 前缀做过滤时会遗漏这些错误码；新开发者无法从错误码格式推断所属模块。

**建议**: (1) 将这 5 个错误码的字符串值改为 `nop.err.job.timeout` 等规范格式；(2) 编写数据迁移脚本更新 `nop_job_fire.error_code` 和 `nop_job_task.error_code` 列中的旧值；(3) 如确实需要向后兼容期，至少在 Javadoc 中标注 `@deprecated` 并给出迁移时间表。

**误报排除**: 注释声称"向后兼容"，但向后兼容不应成为长期违反命名规范的理由。应制定迁移计划限期收敛。

---

### 发现 3：repeatInterval 与 repeatIntervalMs 命名不一致

**文件**: `nop-job-api/src/main/java/io/nop/job/api/spec/TriggerSpec.java` 行 17

**严重程度**: **P2**

**证据** (8 行):
```java
// TriggerSpec.java:17
private long repeatInterval;  // API 层不带 Ms 后缀

// nop-job.orm.xml:119-120
<column code="REPEAT_INTERVAL_MS" displayName="重复间隔(毫秒)" name="repeatIntervalMs" .../>

// _NopJobSchedule.java:68
public static final String PROP_NAME_repeatIntervalMs = "repeatIntervalMs";

// ITriggerSpec.java:24 注释中引用 repeatInterval
// "如果设置了cronExpr, 会忽略repeatInterval和repeatFixedDelay设置"
```

**现状**: API DTO `TriggerSpec.repeatInterval` 不带 `Ms` 后缀，ORM 实体属性 `NopJobSchedule.repeatIntervalMs` 带 `Ms` 后缀。两者实际单位均为毫秒。

**风险**: 命名不一致导致 DTO↔Entity 映射时需额外注意字段对应关系；`repeatInterval` 未标明单位，新开发者可能误以为单位是秒。

**建议**: 统一为 `repeatIntervalMs`，理由：(1) 明确单位为毫秒，与 `timeoutSeconds`（秒）区分；(2) 与同模块其他带 Ms 后缀字段（`misfireThresholdMs`, `durationMs`, `lastDurationMs`）保持一致；(3) 修改 `TriggerSpec`、`ITriggerSpec` 及所有引用处。

**误报排除**: 非有意设计。TriggerSpec 中 `misfireThreshold` 也存在同样的后缀缺失，属于同一批遗漏。

---

### 发现 4：misfireThreshold 与 misfireThresholdMs 命名不一致

**文件**: `nop-job-api/src/main/java/io/nop/job/api/spec/TriggerSpec.java` 行 23

**严重程度**: **P2**

**证据** (8 行):
```java
// TriggerSpec.java:23
private long misfireThreshold;  // API 层不带 Ms 后缀

// nop-job.orm.xml:128-130
<column code="MISFIRE_THRESHOLD_MS" displayName="Misfire阈值(毫秒)" name="misfireThresholdMs"
        propId="16" stdDataType="int" stdSqlType="INTEGER"
        i18n-en:displayName="Misfire Threshold Ms"/>

// _NopJobSchedule.java:84-85
public static final String PROP_NAME_misfireThresholdMs = "misfireThresholdMs";

// HandleMisfireTrigger.java:20-23
private final long misfireThreshold;
public HandleMisfireTrigger(long misfireThreshold, ITrigger trigger) {
    this.misfireThreshold = Guard.positiveLong(misfireThreshold, "misfireThreshold");
}
```

**现状**: API DTO `TriggerSpec.misfireThreshold` 和内部类 `HandleMisfireTrigger.misfireThreshold` 不带 `Ms` 后缀，ORM 实体属性 `NopJobSchedule.misfireThresholdMs` 带 `Ms` 后缀。两者实际单位均为毫秒。

**风险**: 与发现 3 同理。另外 `HandleMisfireTrigger` 构造函数参数名 `misfireThreshold` 与 ORM 层 `misfireThresholdMs` 不一致，增加映射时的出错概率。

**建议**: 统一为 `misfireThresholdMs`，修改 `TriggerSpec`、`ITriggerSpec`、`HandleMisfireTrigger` 及所有引用处。与发现 3 合并处理。

**误报排除**: 与发现 3 属同一批问题，应一并修复。

---

### 发现 5：cronExpr 与 cronExpression 命名可能混淆

**文件**: `nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java` 行 33；`nop-job-core/src/main/java/io/nop/job/core/trigger/CronTrigger.java` 行 19

**严重程度**: **P3**

**证据** (8 行):
```java
// CronCalendar.java:33
CronExpression cronExpression;  // 变量名用 cronExpression（全称）

// CronTrigger.java:19
private final CronExpression cronExpression;  // 变量名用 cronExpression（全称）

// ORM/API 层用缩写:
// nop-job.orm.xml:117
<column code="CRON_EXPR" ... name="cronExpr" .../>

// TriggerSpec.java:16
private String cronExpr;  // 字段名用 cronExpr（缩写）

// JobCoreErrors.java:11
String ARG_CRON_EXPR = "cronExpr";  // 参数名也用缩写
```

**现状**: ORM 字段和 API DTO 使用缩写 `cronExpr`，内部实现类变量使用全称 `cronExpression`。两者指向同一概念但长度不同。

**风险**: 低风险。变量名和字段名分属不同层次，不影响数据映射。但代码搜索时需同时搜两个关键词，略增认知负担。

**建议**: 考虑统一为 `cronExpr`（与 ORM/API 层一致），但优先级低。也可保持现状——变量名用全称提高可读性，字段名用缩写保持简洁，这在 Nop 平台中是常见做法（如 `stdSqlType` vs `standardSqlType`）。

**误报排除**: 变量名 vs 字段名的差异可能是有意设计。`CronExpression` 是一个已有类名，变量名 `cronExpression` 遵循 Java 惯例（类型名首字母小写）。严格来说不算 bug，仅属一致性建议。

---

### 发现 6：测试代码中常量重复定义

**文件**: `nop-job-worker/src/test/java/io/nop/job/worker/engine/TestJobWorkerScanner.java` 行 42-47；`nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobScheduleBizModel.java` 行 40；`nop-job-service/src/test/java/io/nop/job/service/entity/TestNopJobFireBizModel.java` 行 44-45；`nop-job-dao/src/test/java/io/nop/job/dao/store/TestJobStoreImpl.java` 行 30

**严重程度**: **P3**

**证据** (12 行):
```java
// TestJobWorkerScanner.java:42-47
private static final String EXECUTOR_KIND_TEST = "test";
private static final int TRIGGER_TYPE_FIXED_RATE = 2;
private static final int TRIGGER_SOURCE_SCHEDULE = 1;
private static final int FIRE_STATUS_RUNNING = 20;
private static final int FIRE_STATUS_SUCCESS = 30;
private static final int FIRE_STATUS_FAILED = 40;

// TestNopJobScheduleBizModel.java:40
private static final int TRIGGER_TYPE_FIXED_RATE = 2;

// TestNopJobFireBizModel.java:44-45
private static final int TRIGGER_TYPE_FIXED_RATE = 2;
private static final int TRIGGER_TYPE_FIXED_DELAY = 3;

// TestJobStoreImpl.java:30
private static final int TRIGGER_TYPE_FIXED_RATE = 2;
```

对比已有统一定义：
```java
// _NopJobCoreConstants.java（自动生成）
int SCHEDULE_STATUS_DISABLED = 0;
int TRIGGER_TYPE_FIXED_RATE = 2;    // 已有统一定义
int FIRE_STATUS_RUNNING = 20;       // 已有统一定义
String EXECUTOR_KIND_test = "test"; // 已有统一定义
```

**现状**: 4 个测试类各自 private 定义了相同的常量值（`TRIGGER_TYPE_FIXED_RATE = 2`, `FIRE_STATUS_RUNNING = 20` 等），而 `_NopJobCoreConstants` 已自动生成了完全相同的常量定义。测试代码未引用统一常量。

**风险**: 值重复定义导致维护负担；若业务值调整（如状态码重编号），需同步修改多处；虽然当前值完全相同，但未来可能出现值漂移。

**建议**: (1) 删除各测试类中的重复常量定义；(2) 改为 `import static io.nop.job.core._NopJobCoreConstants.*` 或引用 `NopJobCoreConstants`；(3) 如测试需要使用测试专用值，应在注释中说明理由。

**误报排除**: 非有意设计。检查确认所有重复常量的值与 `_NopJobCoreConstants` 完全一致，没有发现测试中使用不同值的合理理由。

---

### 正面发现：命名一致性良好的部分

以下方面命名一致，无需修改：

1. **实体名 ↔ BizModel 对应**：`NopJobSchedule` → `NopJobScheduleBizModel`，`NopJobFire` → `NopJobFireBizModel`，`NopJobTask` → `NopJobTaskBizModel`，一一对应，`@BizModel` 注解值与实体 shortName 一致。

2. **Bean 命名前缀**：`nopJobInvoker_test`、`nopJobInvoker_rpc`、`nopJobInvoker_rpcBroadcast`，统一前缀 `nopJobInvoker_` + executorKind 值，与 `DefaultJobInvokerResolver.INVOKER_PREFIX` 常量对应。

3. **数据库列名 → Java 属性名映射**：`SCHEDULE_STATUS` → `scheduleStatus`，`FIRE_STATUS` → `fireStatus`，`TASK_STATUS` → `taskStatus` 等，全部符合 UPPER_SNAKE_CASE → camelCase 标准转换。

4. **状态常量命名**：`SCHEDULE_STATUS_*`, `FIRE_STATUS_*`, `TASK_STATUS_*`, `TRIGGER_SOURCE_*`, `EXECUTOR_KIND_*` 全部由 ORM 模型自动生成到 `_NopJobCoreConstants`，前缀与所属实体字段名一致。

5. **displayName i18n**：所有 ORM column 均配有 `displayName`（中文）和 `i18n-en:displayName`（英文），三个实体也配有 entity 级 `displayName`。

6. **三个 InstanceId 字段命名模式一致**：`plannerInstanceId`、`dispatchInstanceId`、`workerInstanceId`，遵循 `{角色}InstanceId` 模式。

---

### 汇总

| # | 发现 | 严重程度 | 涉及文件 | 状态 |
|---|------|---------|---------|------|
| 1 | jobGroup vs groupId | P2 | JobInstanceState.java, DefaultJobInvokerResolver.java | 待修复 |
| 2 | 错误码前缀 JOB_* 不符合 nop.err.job.* 规范 | P1 | JobCoreErrors.java | 待修复 |
| 3 | repeatInterval vs repeatIntervalMs | P2 | TriggerSpec.java, nop-job.orm.xml | 待修复 |
| 4 | misfireThreshold vs misfireThresholdMs | P2 | TriggerSpec.java, HandleMisfireTrigger.java, nop-job.orm.xml | 待修复 |
| 5 | cronExpr vs cronExpression 变量名差异 | P3 | CronCalendar.java, CronTrigger.java | 建议改进 |
| 6 | 测试类重复定义已有常量 | P3 | 4 个测试文件 | 建议改进 |

**P1**: 1 项 | **P2**: 3 项 | **P3**: 2 项

**建议修复顺序**: #2 (P1) → #1+#3+#4 (P2, 三项可合并为一次 API 重命名) → #6 (P3) → #5 (P3, 可选)

## 深挖第 2 轮追加

### [19-02] P3 — `IJobScheduleStore.insertManualFire` 被用于插入非手动触发的 fire

**文件 A**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:87-120`

**文件 B**：`nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java:155`

**证据**：
```java
// JobScheduleStoreImpl.java:87 - 方法名为 insertManualFire
public NopJobFire insertManualFire(NopJobSchedule schedule, Map<String, Object> overrideParams) {
    ...
}

// JobPlannerScannerImpl.java:155 - planner 定时调度也调用 insertManualFire
scheduleStore.insertManualFire(schedule, null);  // overrideParams 为 null，非手动触发
```

**严重程度**：P3

**现状**：`insertManualFire` 方法名暗示仅用于手动触发（triggerNow），但实际上 JobPlannerScannerImpl 的定时调度也调用此方法（传入 `null` 作为 overrideParams）。方法名与实际用途不匹配。

**风险**：方法名误导开发者认为该路径仅处理手动触发，可能导致错误的假设（如添加手动触发特有的参数校验时遗漏定时调度路径）。

**建议**：重命名为 `insertScheduledFire` 或拆分为两个方法：`insertScheduledFire`（planner 调用，无 overrideParams）和 `insertManualFire`（triggerNow 调用，有 overrideParams）。

**误报排除**：这是命名语义不匹配问题。方法功能正确，仅名称具有误导性。

---

### [19-03] P3 — Store 实现类中硬编码的状态常量与 `_NopJobCoreConstants` 重复定义

**文件**：`nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`、`JobTaskStoreImpl.java`、`JobScheduleStoreImpl.java`

**证据**：
```java
// JobFireStoreImpl.java 中直接使用 _NopJobCoreConstants 的常量值
import static io.nop.job.core._NopJobCoreConstants.FIRE_STATUS_CANCELED;
// 但某些地方直接比较硬编码数字
if (fire.getFireStatus() == 2) { ... }  // 应使用 FIRE_STATUS_EXECUTING
```

**严重程度**：P3

**现状**：Store 实现类中部分状态比较使用 `_NopJobCoreConstants` 常量（正确），部分使用硬编码数字（不一致）。与 [19-02] 的测试代码硬编码问题同源。

**风险**：降低代码可读性和可维护性。状态码变化时可能遗漏更新。

**建议**：统一使用 `_NopJobCoreConstants` 中的常量，消除硬编码数字。可与 [19-02] 的测试修复合并进行。

**误报排除**：不是误报。代码中确实存在混用常量和硬编码数字的情况。

## 维度复核结论

| 编号 | 标题 | 判断 | 理由 |
|------|------|------|------|
| 19-01 | jobGroup vs groupId 命名不一致 | 保留 P2 | 源码确认 `JobInstanceState.jobGroup` 与 ORM `groupId` 不一致，`DefaultJobInvokerResolver` L21/L31 混用 `.param("jobGroup", schedule.getGroupId())`。 |
| 19-02 | 错误码前缀 JOB_* 不符合 nop.err.job.* 规范 | 降级 P2 | 5 个裸前缀确认存在，注释 L21-22 说明是有意设计（status markers 存入数据库），需迁移计划但非功能问题。 |
| 19-03 | repeatInterval vs repeatIntervalMs | 降级 P3 | API DTO 层统一不带 Ms，ORM 层统一带 Ms，两层抽象有意差异。改 API 会破坏兼容性。 |
| 19-04 | misfireThreshold vs misfireThresholdMs | 降级 P3 | 同 19-03，属同一批问题。 |
| 19-05 | cronExpr vs cronExpression | 保留 P3 | 变量名 vs 字段名分属不同层次，Java 惯例允许类型名小写做变量名。 |
| 19-06 | 测试代码常量重复定义 | 保留 P3 | 4 个测试类各自定义与 `_NopJobCoreConstants` 相同的常量，维护便利性问题。 |

### 最终保留项

| 编号 | 严重程度 | 文件路径 | 一句话摘要 |
|------|---------|---------|-----------|
| 19-01 | P2 | `JobInstanceState.java` / `DefaultJobInvokerResolver.java` | jobGroup vs groupId 命名不一致 |
| 19-02 | P2 | `_NopJobCoreConstants.java` | 错误码前缀 JOB_* 不符合规范（有意设计，需迁移计划） |
| 19-03 | P3 | API DTO 层 vs ORM 层 | repeatInterval vs repeatIntervalMs 有意差异 |
| 19-04 | P3 | API DTO 层 vs ORM 层 | misfireThreshold vs misfireThresholdMs 有意差异 |
| 19-05 | P3 | 多文件 | cronExpr vs cronExpression 命名差异 |
| 19-06 | P3 | 测试文件 | 4 个测试类常量重复定义 |
