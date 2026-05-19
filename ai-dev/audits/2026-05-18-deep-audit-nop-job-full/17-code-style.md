# 维度17：代码风格与规范

## 第 1 轮（初审）

**审计日期**: 2026-05-18
**审计基线**: `nop-job/` 手写 Java 源码（排除 `_gen/` 和 `_*.java`），覆盖 nop-job-core、nop-job-dao、nop-job-service、nop-job-coordinator、nop-job-worker、nop-job-api、nop-job-retry-adapter 七个子模块的 `src/main/java/` 目录。
**误报校准声明**: `_gen/` 目录和 `_*.java` 文件为代码生成产物，不在本维度审计范围内。检查项包括：命名规范、import 分组、行宽缩进、过度注释、System.out/System.err、未使用 import、不必要的 public 修饰符。

---

### F-17-1: Import 分组顺序违规——io.nop.* 位于 jakarta.* 之前

**文件**: `nop-job/nop-job-coordinator/src/main/java/io/nop/job/coordinator/engine/JobPlannerScannerImpl.java`
**行号**: L1–L30
**严重程度**: **P2**
**现状**: io.nop.* 的 import 出现在 jakarta.* 和 java.* 之前，违反 `java.* → jakarta.* → third-party → io.nop.*` 的分组顺序规范。
**证据**:
```java
package io.nop.job.coordinator.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.IntRangeSet;
import io.nop.api.core.config.AppConfig;
// ... io.nop.* imports (lines 3-17)
import jakarta.inject.Inject;       // jakarta 出现在 io.nop 之后
import org.slf4j.Logger;             // third-party 出现在 jakarta 之后
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;           // java 出现在最后
import java.util.Collections;
```
**风险**: 违反仓库统一规范，降低代码可读性；新开发者难以判断 import 应插入的位置。
**建议**: 调整为 `java.* → jakarta.* → third-party → io.nop.*`，各组之间保留空行。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-2: Import 分组顺序违规——io.nop.* 位于 jakarta.* 之前

**文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`
**行号**: L1–L31
**严重程度**: **P2**
**现状**: io.nop.* 的 import 出现在 jakarta.* 之前，违反分组顺序规范。
**证据**:
```java
package io.nop.job.worker.engine;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
// ... io.nop.* imports (lines 3-20)
import jakarta.inject.Inject;       // jakarta 出现在 io.nop 之后
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;           // java 出现在最后
import java.util.LinkedHashMap;
```
**风险**: 同 F-17-1。
**建议**: 同 F-17-1。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-3: Import 分组顺序违规——io.nop.* 位于 jakarta.* 之前

**文件**: `nop-job/nop-job-service/src/main/java/io/nop/job/service/executor/RpcJobInvoker.java`
**行号**: L1–L19
**严重程度**: **P2**
**现状**: io.nop.* 的 import 出现在 jakarta.* 之前。
**证据**:
```java
package io.nop.job.service.executor;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.rpc.IRpcServiceInvoker;
import io.nop.job.api.execution.IJobExecutionContext;
import io.nop.job.api.execution.IJobInvoker;
import io.nop.job.api.execution.JobFireResult;
import jakarta.inject.Inject;       // jakarta 在 io.nop 之后

import java.util.Collections;
```
**风险**: 同 F-17-1。
**建议**: 同 F-17-1。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-4: Import 分组顺序违规——io.nop.* 位于 jakarta.* 之前

**文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`
**行号**: L1–L27
**严重程度**: **P2**
**现状**: io.nop.* 的 import 出现在 jakarta.* 之前。
**证据**:
```java
package io.nop.job.dao.store;

import io.nop.api.core.annotations.txn.TransactionPropagation;
import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.beans.FilterBeans;
// ... io.nop.* imports (lines 3-12)
import io.nop.orm.dao.IOrmEntityDao;
import jakarta.inject.Inject;       // jakarta 在 io.nop 之后

import java.util.ArrayList;
import java.util.List;
```
**风险**: 同 F-17-1。
**建议**: 同 F-17-1。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-5: Import 分组顺序违规——io.nop.* 位于 java.* 和 third-party 之前

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java`
**行号**: L1–L31
**严重程度**: **P2**
**现状**: io.nop.* import 在 java.* 和 third-party 之前；且 third-party (org.slf4j) 混在 io.nop.* 与 java.* 之间。
**证据**:
```java
package io.nop.job.core;

import io.nop.api.core.exceptions.NopException;
// ... io.nop.* imports (lines 3-16)
import org.slf4j.Logger;             // third-party 混入
import org.slf4j.LoggerFactory;

import java.util.ArrayList;          // java 在最后
import java.util.List;
```
**风险**: 同 F-17-1，且 third-party 分组边界不清。
**建议**: 同 F-17-1。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-6: Import 分组顺序违规——io.nop.* 位于 jakarta.* 之前

**文件**: `nop-job/nop-job-api/src/main/java/io/nop/job/api/IJobScheduler.java`
**行号**: L8–L22
**严重程度**: **P2**
**现状**: io.nop.* 的 import 出现在 jakarta.* 之前。
**证据**:
```java
package io.nop.job.api;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.NopException;
import io.nop.job.api.spec.JobSpec;
import jakarta.annotation.Nullable;   // jakarta 在 io.nop 之后

import java.util.ArrayList;
import java.util.Collection;
```
**风险**: 同 F-17-1。
**建议**: 同 F-17-1。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-7: 通配符 import `java.util.*`

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`
**行号**: L23
**严重程度**: **P2**
**现状**: 使用通配符 `import java.util.*;`，导入整个 java.util 包。
**证据**:
```java
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import jakarta.annotation.Nullable;

import java.util.*;                  // 通配符 import
```
**风险**: 引入不必要的类依赖，降低代码可读性；IDE 自动优化时可能误删或误加。Checkstyle 通常禁止通配符 import。
**建议**: 展开为具体的 import 语句（如 `java.util.ArrayList`, `java.util.BitSet`, `java.util.Calendar`, `java.util.Date`, `java.util.GregorianCalendar`, `java.util.List`, `java.util.Map`, `java.util.SortedSet`, `java.util.StringTokenizer`, `java.util.TimeZone`, `java.util.TreeMap`）。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-8: 中文 Javadoc 注释——违反 i18n 准备原则

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/_NopJobCoreConstants.java`
**行号**: L6–L124（全部常量的 Javadoc）
**严重程度**: **P2**
**现状**: 所有常量的 Javadoc 注释均使用中文描述。该文件虽为生成文件（前缀 `_`），但它是 `NopJobCoreConstants` 的直接父接口，所有子模块的状态常量语义由此文件定义。
**证据**:
```java
/**
 * 调度状态: 已禁用
 */
int SCHEDULE_STATUS_DISABLED = 0;

/**
 * 调度状态: 已启用
 */
int SCHEDULE_STATUS_ENABLED = 10;

/**
 * 触发批次状态: 等待分发
 */
int FIRE_STATUS_WAITING = 0;

/**
 * 执行任务状态: 等待执行
 */
int TASK_STATUS_WAITING = 0;

/**
 * 触发来源: 定时触发
 */
int TRIGGER_SOURCE_SCHEDULE = 1;
```
**风险**: 违反仓库规范中"Do not hardcode Chinese in code"的要求。虽为常量接口，但其注释是开发者理解状态码语义的主要来源。
**建议**: 将所有中文 Javadoc 替换为英文注释。例如 `调度状态: 已禁用` → `Schedule status: disabled`。此文件为生成文件，需在 ORM 模型或 codegen 模板的源头修改。
**误报排除**: 若项目明确规定常量接口注释使用中文以便国内开发者理解，可排除。但根据 AGENTS.md 中"Do not hardcode Chinese error messages in code"的原则，建议统一为英文。
**审查状态**: 待确认。

---

### F-17-9: 常量命名不符合 UPPER_SNAPE_CASE——`EXECUTOR_KIND_test`

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/_NopJobCoreConstants.java`
**行号**: L124
**严重程度**: **P2**
**现状**: 常量名 `EXECUTOR_KIND_test` 末尾使用小写 `test`，违反 `UPPER_SNAKE_CASE` 命名规范。
**证据**:
```java
/**
 * 执行器类型: 测试执行器
 */
String EXECUTOR_KIND_test = "test";
```
**风险**: 违反命名规范，降低代码一致性。可能造成开发者困惑——是不小心用了小写，还是有特殊含义。
**建议**: 重命名为 `EXECUTOR_KIND_TEST`。注意此常量如有引用方需同步修改。由于此文件为生成文件，需在 ORM 模型的 `displayName` 或 `name` 字段处修改。
**误报排除**: 若值 `"test"` 必须精确匹配数据库中存储的字符串，重命名常量不影响值。检查是否因为有代码引用了该常量名。经搜索该常量名在整个项目中仅被 `_NopJobCoreConstants` 定义，`NopJobCoreConstants` 继承，未见手写代码直接引用——可安全重命名。
**审查状态**: 待确认。

---

### F-17-10: 行宽超过 120 字符——方法签名

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/DailyCalendar.java`
**行号**: L105, L147, L183, L444
**严重程度**: **P3**
**现状**: 多处方法签名的参数列表单行超过 120 字符。
**证据**:
```java
// Line 105:
int rangeStartingMillis, int rangeEndingHourOfDay, int rangeEndingMinute, int rangeEndingSecond,

// Line 147:
int rangeStartingMillis, int rangeEndingHourOfDay, int rangeEndingMinute, int rangeEndingSecond,

// Line 183:
int rangeStartingSecond, int rangeStartingMillis, int rangeEndingHourOfDay, int rangeEndingMinute,

// Line 444:
int rangeStartingMillis, int rangeEndingHourOfDay, int rangeEndingMinute, int rangeEndingSecond,
```
**风险**: 影响代码在 120 列限制编辑器中的可读性。
**建议**: 将长参数列表拆分为多行，每行一个或几个参数。
**误报排除**: DailyCalendar 来源于 Quartz 的代码移植，保留原始风格有一定合理性。但既然已纳入本项目，应遵循本项目规范。
**审查状态**: 待确认。

---

### F-17-11: Javadoc 行宽超过 120 字符

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CronCalendar.java`
**行号**: L47–L48, L59–L60, L62–L63
**严重程度**: **P3**
**现状**: Javadoc `@param` 描述行超过 120 字符。
**证据**:
```java
// Line 47-48:
* @param baseCalendar the base calendar for this calendar instance &ndash; see {@link BaseCalendar} for more information on
*                     base calendar functionality

// Line 59-60:
* @param baseCalendar the base calendar for this calendar instance &ndash; see {@link BaseCalendar} for more information on

// Line 62-63:
* @param timeZone     Specifies for which time zone the <code>expression</code> should be interpreted, i.e. the expression 0
*                     0 10 * * ?, is resolved to 10:00 am in this time zone. If <code>timeZone</code> is <code>null</code>
```
**风险**: 影响代码在 120 列限制编辑器中的可读性。
**建议**: 重构 Javadoc 注释，将长描述拆行。
**误报排除**: 同 F-17-10，来源于 Quartz 移植代码。
**审查状态**: 待确认。

---

### F-17-12: Javadoc 行宽超过 120 字符

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/utils/CronExpression.java`
**行号**: L230, L232
**严重程度**: **P3**
**现状**: Javadoc `@param` 描述行超过 120 字符。
**证据**:
```java
// Line 230:
* @param field       the field to increment in the calendar (@see {@link Calendar} for the static constants defining valid

// Line 232:
* @param lowerOrders the Calendar field ids that should be reset (i.e. the ones of lower significance than the field of
```
**风险**: 同 F-17-11。
**建议**: 同 F-17-11。
**误报排除**: 同 F-17-10，来源于 Spring Framework 移植代码。
**审查状态**: 待确认。

---

### F-17-13: 行宽超过 120 字符——Lambda 表达式单行

**文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/DefaultJobExecutionContextBuilder.java`
**行号**: L146
**严重程度**: **P3**
**现状**: 包含泛型强转和 Lambda 的单行超过 120 字符。
**证据**:
```java
Map<String, Object> headers = (Map<String, Object>) jobParams.computeIfAbsent("headers", k -> new HashMap<String, Object>());
```
**风险**: 影响可读性。
**建议**: 拆为多行：
```java
Map<String, Object> headers =
        (Map<String, Object>) jobParams.computeIfAbsent("headers",
                k -> new HashMap<>());
```
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-14: 行宽超过 120 字符——三元表达式单行

**文件**: `nop-job/nop-job-worker/src/main/java/io/nop/job/worker/engine/JobWorkerScannerImpl.java`
**行号**: L239
**严重程度**: **P3**
**现状**: 复杂三元表达式单行超过 120 字符。
**证据**:
```java
long duration = task.getStartTime() != null ? Math.max(endTime.getTime() - task.getStartTime().getTime(), 0L) : 0L;
```
**风险**: 影响可读性。
**建议**: 拆为多行或提取局部变量。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-15: 行宽超过 120 字符——条件表达式

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/LocalJobScheduler.java`
**行号**: L87
**严重程度**: **P3**
**现状**: 条件组合表达式单行超过 120 字符。
**证据**:
```java
if (existing.state.internal == InternalState.WAITING || existing.state.internal == InternalState.SUSPENDED) {
```
**风险**: 影响可读性。
**建议**: 拆为多行：
```java
if (existing.state.internal == InternalState.WAITING
        || existing.state.internal == InternalState.SUSPENDED) {
```
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-16: 行宽超过 120 字符——方法签名

**文件**: `nop-job/nop-job-dao/src/main/java/io/nop/job/dao/store/JobTaskStoreImpl.java`
**行号**: L67
**严重程度**: **P3**
**现状**: 方法签名单行超过 120 字符。
**证据**:
```java
public List<NopJobTask> tryLockTasksForExecute(List<NopJobTask> tasks, String workerInstanceId, long lockTimeoutMs) {
```
**风险**: 影响可读性。
**建议**: 拆为多行。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-17: Javadoc `@author` 标签包含邮箱地址

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/ITrigger.java`
**行号**: L13
**严重程度**: **P3**
**现状**: Javadoc `@author` 标签包含完整邮箱地址。
**证据**:
```java
/**
 * 定时触发器
 *
 * @author canonical_entropy@163.com
 */
public interface ITrigger {
```
**风险**: 邮箱地址可能过时；暴露个人信息；此类标签在现代项目中通常不推荐使用。
**建议**: 移除 `@author` 标签，或仅保留作者名（不含邮箱）。版权信息已在文件头注释中覆盖。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-18: Javadoc `@author` 标签包含邮箱地址

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/calendar/CalendarBuilder.java`
**行号**: L31
**严重程度**: **P3**
**现状**: 类级别 Javadoc 仅含 `@author` 邮箱标签，缺少类功能描述。
**证据**:
```java
/**
 * @author canonical_entropy@163.com
 */
public class CalendarBuilder {
    public static ICalendar buildCalendar(List<CalendarSpec> calendars) {
```
**风险**: 同 F-17-17，且缺少类功能描述使 Javadoc 形同虚设。
**建议**: 移除 `@author`，添加类功能描述（如 "Builds an ICalendar chain from a list of CalendarSpec configurations."）。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-19: Javadoc `@author` 标签包含邮箱地址（批量）

**文件**: 以下 8 个文件均存在相同问题：
- `nop-job-core/src/main/java/io/nop/job/core/trigger/PauseCalendarTrigger.java` (L19)
- `nop-job-core/src/main/java/io/nop/job/core/trigger/TriggerBuilder.java` (L18)
- `nop-job-core/src/main/java/io/nop/job/core/trigger/LimitCountTrigger.java` (L14)
- `nop-job-core/src/main/java/io/nop/job/core/trigger/LimitTimeTrigger.java` (L14)
- `nop-job-core/src/main/java/io/nop/job/core/trigger/CronTrigger.java` (L16)
- `nop-job-core/src/main/java/io/nop/job/core/trigger/PeriodicTrigger.java` (L17)
- `nop-job-core/src/main/java/io/nop/job/core/trigger/HandleMisfireTrigger.java` (L15)
- `nop-job-api/src/main/java/io/nop/job/api/spec/ITriggerSpec.java` (L19)

**行号**: 见上
**严重程度**: **P3**
**现状**: 均包含 `@author canonical_entropy@163.com` 标签。
**证据**:
```java
// 典型模式：
/**
 * ... 功能描述 ...
 *
 * @author canonical_entropy@163.com
 */
```
**风险**: 同 F-17-17。
**建议**: 批量移除所有 `@author canonical_entropy@163.com` 标签。版权信息已在文件头部覆盖。
**误报排除**: 无。
**审查状态**: 待确认。

---

### F-17-20: 测试文件中使用 `System.out`

**文件**: `nop-job/nop-job-core/src/test/java/io/nop/job/core/trigger/TestTrigger.java`
**行号**: 文件内（grep 命中）
**严重程度**: **P3**
**现状**: 测试代码中使用 `System.out`/`System.err` 进行输出，而非使用 SLF4J 日志或 JUnit 断言。
**证据**:
```
grep 命中: System.out.print* 或 System.err.print*
```
**风险**: 违反仓库规范"Log with SLF4J (no System.out/System.err)"。测试输出不受日志级别控制，在 CI 环境中产生噪音。
**建议**: 替换为 `LOG.info()` 或 `LOG.debug()`，或使用 JUnit 的断言/验证方法。
**误报排除**: 此文件为测试代码，不在 `src/main/java` 审计范围内，但 AGENTS.md 的规范适用于整个仓库。保留为 P3（低优先级）。
**审查状态**: 待确认。

---

### F-17-21: 生成文件中空行过多

**文件**: `nop-job/nop-job-core/src/main/java/io/nop/job/core/_NopJobCoreConstants.java`
**行号**: L5, L10, L15, L20, ...（每个常量之间 3–4 个空行）
**严重程度**: **P3**
**现状**: 生成文件中每个常量之间有多余空行（Javadoc 结束后 `}` 之前有多行空白），造成代码密度过低。
**证据**:
```java
public interface _NopJobCoreConstants {
    
    /**
     * 调度状态: 已禁用
     */
    int SCHEDULE_STATUS_DISABLED = 0;
                     ← 此处有 3–4 个空行
    /**
     * 调度状态: 已启用
     */
    int SCHEDULE_STATUS_ENABLED = 10;
```
**风险**: 降低代码可读性，增加滚动负担。100 多行的常量定义中有效内容占比不足 40%。
**建议**: 调整代码生成模板，常量之间仅保留 1 个空行。需修改 codegen 模板源头。
**误报排除**: 此文件为生成文件（前缀 `_`），修改应作用于模板而非直接编辑此文件。但格式问题需在此记录以便追踪。
**审查状态**: 待确认。

---

## 审计总结

| 严重程度 | 数量 | 说明 |
|----------|------|------|
| **P0** | 0 | — |
| **P1** | 0 | — |
| **P2** | 9 | Import 分组违规 (×6)、通配符 import (×1)、中文 Javadoc (×1)、常量命名 (×1) |
| **P3** | 12 | 行宽超限 (×6)、@author 邮箱 (×3)、Javadoc 缺失 (×1)、System.out (×1)、空行过多 (×1) |
| **合计** | **21** | |

### 问题分布

| 问题类型 | 数量 | 涉及文件 |
|----------|------|----------|
| Import 分组顺序违规 (io.nop.* 在 jakarta.* 之前) | 6 | JobPlannerScannerImpl, JobWorkerScannerImpl, RpcJobInvoker, JobTaskStoreImpl, LocalJobScheduler, IJobScheduler |
| 行宽超过 120 字符 | 6 | DailyCalendar (×4), CronCalendar, CronExpression, DefaultJobExecutionContextBuilder, JobWorkerScannerImpl, LocalJobScheduler, JobTaskStoreImpl |
| Javadoc @author 邮箱标签 | 3+8 | ITrigger, CalendarBuilder, 及 8 个 trigger/api 文件 |
| 中文 Javadoc 注释 | 1 | _NopJobCoreConstants |
| 常量命名违规 (UPPER_SNAKE_CASE) | 1 | _NopJobCoreConstants (`EXECUTOR_KIND_test`) |
| 通配符 import | 1 | CronExpression (`import java.util.*`) |
| System.out 使用 | 1 | TestTrigger (测试文件) |
| 空行过多 | 1 | _NopJobCoreConstants |
| Javadoc 缺失功能描述 | 1 | CalendarBuilder |

### 优先处理建议

1. **Import 分组顺序**（P2, 6 处）—— 可通过 IDE 自动格式化批量修复，影响面最广
2. **常量命名 `EXECUTOR_KIND_test`**（P2）—— 语义混淆风险，需在 ORM 模型源头修改
3. **中文 Javadoc**（P2）—— 需在 ORM/codegen 模板源头修改
4. **通配符 import**（P2）—— IDE 一键展开
5. **@author 邮箱**（P3, 11 处）—— 批量移除
6. **行宽超限**（P3, 6 处）—— 手动拆行
7. **其余 P3 问题**—— 按便利性修复

### 无发现项

以下检查项在审计范围内**未发现违规**：
- ✅ 类名 PascalCase：全部手写类符合
- ✅ 方法名 camelCase：全部手写方法符合
- ✅ 常量 UPPER_SNAKE_CASE：除 F-17-9 外全部符合
- ✅ 接口名 I + PascalCase：`ICalendar`, `ITrigger`, `ICronExpression`, `IJobScheduler`, `IJobPlannerScanner` 等均符合；常量接口（`JobCoreErrors`, `NopJobConstants` 等）为项目惯例，不适用 I 前缀
- ✅ 包名 io.nop.\<module-name\>：全部符合
- ✅ 4 空格缩进：全部手写文件符合
- ✅ 未使用的 import：未在审计范围内发现
- ✅ System.out/System.err 在 main 源码中：未发现（仅测试文件 1 处）
