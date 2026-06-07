# 维度 09：错误处理与错误码

## 第 1 轮（初审）

### [维度09-01] 日历工具类使用 IllegalArgumentException 而非 NopException + ErrorCode

- **文件**: `BaseCalendar.java:147,170`, `MonthlyCalendar.java:86,100,104,121`, `DailyCalendar.java:392,411,463,501,504,507,510`, `CronCalendar.java:184`
- **证据片段** (BaseCalendar.java:144-148):
```java
public boolean isTimeIncluded(long timeStamp) {
    if (timeStamp <= 0) {
        throw new IllegalArgumentException("timeStamp must be greater 0");
    }
```
- **严重程度**: P3
- **现状**: 日历类在参数校验失败时抛出原生 `IllegalArgumentException`，非 NopException + ErrorCode。这些类可能从 Quartz 移植而来。
- **风险**: 用户配置无效日历参数时，异常缺少结构化 ErrorCode 和上下文参数。但外层 scanner 有 catch(Exception) 兜底。
- **建议**: 将 IllegalArgumentException 替换为 NopException + ErrorCode。优先级较低。
- **信心水平**: 高
- **误报排除**: 非合规抛出，但触发路径有限且外层有兜底。
- **复核状态**: 未复核

### [维度09-02] Store 实现类中重复定义状态常量，未复用 _NopJobCoreConstants

- **文件**: `JobFireStoreImpl.java:43-50`, `JobScheduleStoreImpl.java:37-49`, `JobTaskStoreImpl.java:28-31`
- **证据片段** (JobFireStoreImpl.java:43-50):
```java
private static final int FIRE_STATUS_WAITING = 0;
private static final int FIRE_STATUS_DISPATCHING = 10;
private static final int FIRE_STATUS_RUNNING = 20;
private static final int FIRE_STATUS_CANCELED = 60;
private static final int TASK_STATUS_WAITING = 0;
private static final int TASK_STATUS_CLAIMED = 10;
private static final int TASK_STATUS_RUNNING = 20;
private static final int TASK_STATUS_CANCELED = 60;
```
- **严重程度**: P3
- **现状**: 三个 Store 实现类各自定义了与 `_NopJobCoreConstants` 完全相同的常量值。部分 Store 已 import _NopJobCoreConstants 但未使用。
- **风险**: 状态值变化需在 4 个地方同步修改，容易遗漏。
- **建议**: 替换为 `import static io.nop.job.core._NopJobCoreConstants.*`。
- **信心水平**: 高
- **误报排除**: 当前值完全对齐无运行时错误。
- **复核状态**: 未复核

### [维度09-03] RpcBroadcastTaskBuilder 存在未使用的私有方法

- **文件**: `RpcBroadcastTaskBuilder.java:101-103`
- **证据片段**:
```java
private Map<String, Object> emptyIfNull(Map<String, Object> map) {
    return map == null ? Map.of() : map;
}
```
- **严重程度**: P3
- **现状**: `emptyIfNull` 是 private 方法，整个类中无任何调用者。
- **建议**: 删除。
- **信心水平**: 高
- **误报排除**: 搜索确认无调用。
- **复核状态**: 未复核

### 正向确认

- JobApiErrors.java: 3 个 ErrorCode 使用 define() + ARG_* 参数声明，合规
- NopJobErrors.java: 8 个 ErrorCode + 1 个 ARG 常量，合规
- JobCoreErrors.java: 10 个 ErrorCode，注释清晰，合规
- 所有 ErrorCode 的 description 均为英文
- 所有 throw 语句（日历类除外）均使用 NopException + ErrorCode + .param()
- CronExpression.java:96 正确使用 .cause(e) 保留异常链
