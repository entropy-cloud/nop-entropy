# 维度02：模块职责与文件边界

## 第 1 轮（初审）

### 发现 1：JobScheduleStoreImpl 重复定义状态常量

**文件路径：** `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java`

**行号范围：** 33–45

**证据代码片段：**
```java
public class JobScheduleStoreImpl implements IJobScheduleStore {
    private static final int SCHEDULE_STATUS_ENABLED = 10;
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_DISPATCHING = 10;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int FIRE_STATUS_FAILED = 40;
    private static final int FIRE_STATUS_TIMEOUT = 50;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CLAIMED = 10;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_FAILED = 40;
    private static final int TASK_STATUS_TIMEOUT = 50;
    private static final int TASK_STATUS_CANCELED = 60;
```

**严重程度：** P2（维护成本）

**现状：** 文件第 11 行已 `import io.nop.job.core._NopJobCoreConstants`，且第 203 行使用了 `_NopJobCoreConstants.FIRE_STATUS_CANCELED`，但第 33–45 行又重复定义了 13 个同名同值常量。文件内部混合使用了局部常量和 `_NopJobCoreConstants` 常量。

**风险：**
- 常量定义存在两处单一真实来源，未来新增状态值时可能遗漏 Store 中的私有副本
- 同一文件混用两套常量来源，降低可读性和维护性

**建议：** 删除第 33–45 行的全部私有常量定义，将所有引用统一改为 `_NopJobCoreConstants.XXX`。

**误报排除：** 不适用。`_NopJobCoreConstants` 是 ORM 代码生成的标准常量位置，Store 实现应直接引用而非重新定义。

---

### 发现 2：JobFireStoreImpl 重复定义状态常量

**文件路径：** `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobFireStoreImpl.java`

**行号范围：** 35–42

**证据代码片段：**
```java
public class JobFireStoreImpl implements IJobFireStore {
    private static final int FIRE_STATUS_WAITING = 0;
    private static final int FIRE_STATUS_DISPATCHING = 10;
    private static final int FIRE_STATUS_RUNNING = 20;
    private static final int FIRE_STATUS_CANCELED = 60;
    private static final int TASK_STATUS_WAITING = 0;
    private static final int TASK_STATUS_CLAIMED = 10;
    private static final int TASK_STATUS_RUNNING = 20;
    private static final int TASK_STATUS_CANCELED = 60;
```

**严重程度：** P2（维护成本）

**现状：** 与发现 1 同类问题。文件第 13 行已 `import io.nop.job.core._NopJobCoreConstants`，第 35–42 行又重复定义了 8 个同名同值常量，这些值在 `_NopJobCoreConstants` 中均已存在。

**风险：** 与发现 1 相同。两处 Store 存在私有常量副本，未来修改时容易遗漏。

**建议：** 删除第 35–42 行的全部私有常量定义，统一使用 `_NopJobCoreConstants.XXX`。

**误报排除：** 不适用。原因同发现 1。

---

## 无发现的检查项确认

以下检查项经过逐项验证，**未发现问题**：

| 检查项 | 验证结论 |
|--------|----------|
| **子模块职责单一性** | dao（Entity + Store + I\*Biz 接口）、service（BizModel + executor + beans）、meta（XMeta + i18n，无 Java 文件）、web（view/page 资源）、app（启动类）、coordinator（调度协调 engine）、worker（任务执行 engine）、core（trigger/calendar 常量）—— 层次清晰 |
| **超大手写类职责混合（>500行）** | `DailyCalendar`（512 行）和 `CronExpression`（451 行）虽接近阈值，但均是从 Quartz 移植的工具类，职责单一（日历计算 / cron 解析），不存在混合职责问题 |
| **手写代码误放生成目录** | 未发现。所有 `_gen/` 和 `_` 前缀文件均为生成代码 |
| **`_` 前缀生成文件手写修改** | 未发现。生成的 `_NopJobSchedule.java`、`_NopJobFire.java`、`_NopJobTask.java` 均无手写痕迹 |
| **跨层功能错放** | 未发现。coordinator/worker 中的业务逻辑属于调度引擎和任务执行引擎，放置合理 |
