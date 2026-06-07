# 维度 15：类型安全与泛型使用

## 第 1 轮（初审）

### [维度15-01] `daoProvider.daoFor()` 返回值强制转换缺少泛型推断辅助

- **文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobScheduleStoreImpl.java:413-422`、`JobFireStoreImpl.java:315-324`、`JobTaskStoreImpl.java:119-121`
- **证据片段**:
  ```java
  return (IOrmEntityDao<NopJobSchedule>) daoProvider.daoFor(NopJobSchedule.class);
  ```
- **严重程度**: P3
- **现状**: 7 处 `IOrmEntityDao` 强制转换出现在 3 个 Store 实现类中。这是 Nop ORM API 的限制。
- **风险**: 低。类型不匹配时会有 ClassCastException。
- **建议**: 可在 Store 基类或工具方法中封装此模式。
- **信心水平**: 确定
- **误报排除**: Nop 框架 `IDaoProvider` 接口的泛型限制，不是 nop-job 的独立缺陷。
- **复核状态**: 未复核

---

### [维度15-02] `RpcJobInvoker` 中 unchecked cast

- **文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java:48-56,82-84`
- **证据片段**:
  ```java
  @SuppressWarnings("unchecked")
  Map<String, Object> headers = (Map<String, Object>) jobParams.get("headers");
  ```
- **严重程度**: P3
- **现状**: 从 `Map<String, Object>` 取出 value 后 cast 为 `Map<String, Object>`。`@SuppressWarnings("unchecked")` 使用合理。
- **风险**: 低。
- **建议**: 保留现状。
- **信心水平**: 确定
- **误报排除**: 从 Object 到 Map 的 cast 确实是 unchecked。
- **复核状态**: 未复核

---

### [维度15-03] `defaultLong`/`defaultInt` 工具方法在 5 个文件中重复定义

- **文件**: `JobScheduleStoreImpl.java:561-566`、`JobFireStoreImpl.java:402-408`、`JobCompletionProcessorImpl.java:445-451`、`DefaultJobExecutionContextBuilder.java:167-173`、`TriggerSpecHelper.java:89-95`
- **证据片段**:
  ```java
  private static long defaultLong(Long value) {
      return value == null ? 0L : value;
  }
  private static int defaultInt(Integer value) {
      return value == null ? 0 : value;
  }
  ```
- **严重程度**: P3
- **现状**: 两个方法在 5 个不同文件中重复定义，逻辑完全相同。
- **风险**: 维护成本。修改默认值逻辑需同步 5 个位置。
- **建议**: 提取到 `nop-job-core` 模块的工具类中。
- **信心水平**: 确定
- **误报排除**: 5 处完全相同代码的重复。
- **复核状态**: 未复核

---

### [维度15-04] `DailyCalendar.split()` 方法使用过时的 `toArray` 模式

- **文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/DailyCalendar.java:367-376`
- **证据片段**:
  ```java
  private String[] split(String string, String delim) {
      ArrayList<String> result = new ArrayList<String>();
      StringTokenizer stringTokenizer = new StringTokenizer(string, delim);
      while (stringTokenizer.hasMoreTokens()) {
          result.add(stringTokenizer.nextToken());
      }
      return (String[]) result.toArray(new String[result.size()]);
  }
  ```
- **严重程度**: P3
- **现状**: 使用了过时的 `StringTokenizer` 和 `toArray(new String[size])` 模式。
- **风险**: 低。功能正确但代码量不必要。
- **建议**: 可简化为 `string.split(delim)`。
- **信心水平**: 确定
- **误报排除**: 已移植自 Quartz 的遗留实现。
- **复核状态**: 未复核
