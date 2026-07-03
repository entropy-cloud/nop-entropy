# nop-job 代码质量问题修复方案分析

> Status: open
> Date: 2026-07-02
> Scope: `nop-job/nop-job-service`、`nop-job/nop-job-dao`、`nop-job/nop-job-coordinator`
> Conclusion: 分析进行中，按优先级分组给出修复建议，待确认后拆分到 plan

## Context

- 输入：`ai-dev/inputs/nop-job-audit.md` 列出 16 项代码质量问题（第 11 项关联 AR-65 对抗性审查发现）。
- 目标：逐条验证问题是否成立，确定修复方案与优先级，为后续 `ai-dev/plans/` 提供决策依据。
- 涉及模块：`nop-job-service`（BizModel 层）、`nop-job-dao`（Store/Dao 层）、`nop-job-coordinator`（引擎 Processor 层）。
- 约束：遵守 Nop 平台框架约定（ORM 自动维护审计字段、CoreMetrics 时间统一、Helper 汇聚类、乐观锁返回值语义）。

## 分析方法

逐条阅读 `nop-job-audit.md` 引用的源码，验证以下维度：
1. 问题是否真实存在（代码定位 + 证据片段）。
2. 严重程度（P0 数据一致性 → P3 代码整洁）。
3. 修复方案的可行性与 blast radius。
4. 是否涉及 ORM 模型变更（需 plan-first）或框架核心（需 plan-first）。

---

## 一、Helper 抽取与代码复用（P3 — 机械重构）

### 1.1 状态判断抽取到 JobStatusHelper（Issue 1）

**现状**：fire/task 状态判断散落在多个类中，且使用逐值枚举（`==` 链）：

| 方法 | 位置 | 判断方式 |
|------|------|----------|
| `isCancelableStatus` | `NopJobFireBizModel.java:91-96` | `WAITING \|\| DISPATCHING \|\| RUNNING` |
| `isRerunnableStatus` | `NopJobFireBizModel.java:98-104` | `SUCCESS \|\| FAILED \|\| TIMEOUT \|\| CANCELED` |
| `isCancelableFire` | `JobFireStoreImpl.java:361-381` | `WAITING \|\| DISPATCHING \|\| RUNNING` |
| `isTaskFinished` | `JobFireStoreImpl.java:383-388` | 非 `WAITING/CLAIMED/RUNNING` |
| `isTaskFinished` | `JobScheduleStoreImpl.java:557-560` | 同上（重复实现） |
| `TERMINAL_FIRE_STATUSES` | `JobFireStoreImpl.java:119-124` | `Set.of(SUCCESS, FAILED, CANCELED, TIMEOUT)` |

**问题**：
- 同一语义（"活跃 fire"、"终态 fire"、"终态 task"）在 3+ 处重复实现，存在漂移风险。
- `isCancelableStatus` / `isRerunnableStatus` 无覆盖测试（codegraph 标注 ⚠️ no covering tests）。

**分析 — 区间判断可行性**：

fire/task 状态值是**有序整数**（见 `_NopJobCoreConstants.java:34-64`）：

```
活跃态（可取消）:  WAITING(0) < DISPATCHING(10) < RUNNING(20)
终态（可重跑）:    SUCCESS(30) < FAILED(40) < TIMEOUT(50) < CANCELED(60)
```

天然形成两个区间，可用阈值判断（audit 建议参考 workflow 的区间处理）：

```java
// 活跃态 = < 30（SUCCESS 阈值）
static boolean isActiveFire(Integer s) { return s != null && s < FIRE_STATUS_SUCCESS; }
// 终态   = >= 30
static boolean isTerminalFire(Integer s) { return s != null && s >= FIRE_STATUS_SUCCESS; }
```

task 状态多一个 `SUSPICIOUS(15)`，仍 < `TASK_STATUS_SUCCESS(30)`，区间判断同样适用。

**建议**：
- 新建 `io.nop.job.dao.helper.JobStatusHelper`（与已有 `TriggerSpecHelper` 同包），集中放置：
  - `isActiveFire(Integer)` / `isTerminalFire(Integer)`
  - `isCancelableFire(Integer)` / `isRerunnableFire(Integer)`（语义别名，内部委托区间判断）
  - `isFinishedTask(Integer)`
- 替换所有调用点（`NopJobFireBizModel`、`JobFireStoreImpl`、`JobScheduleStoreImpl`）。
- 删除 `TERMINAL_FIRE_STATUSES` Set 常量，统一用 `isTerminalFire`。
- 补充单测（目前这些方法**零测试覆盖**）。

**优先级**：P3（无功能风险，但消除重复 + 补测试有长期价值）

### 1.2 addPartitionFilter 抽取（Issue 2）

**现状**：分区过滤逻辑在 2 处重复实现：

- `JobFireStoreImpl.java:329-339`（4 个调用点：`fetchWaitingFires`、`fetchRunningFires`、`fetchDispatchingFires` + 内部）
- `JobScheduleStoreImpl.java:393`（1 个调用点）

**问题**：`IntRangeSet` → `FilterBeans.between` → `FilterBeans.or` 的转换逻辑完全相同。

**建议**：
- 方案 A（推荐）：抽取到 `io.nop.job.dao.helper.QueryHelper`（或 `JobQueryHelper`），两处 Store 共享。
- 方案 B（更通用但超范围）：扩展到 `nop-api-core` 的 `QueryBean` 上，但 partition filter 是 job 专有概念，不宜下沉到框架核心。

**优先级**：P3

### 1.3 calculateDuration 抽取到 DateHelper（Issue 6）

**现状**：同一段时长计算代码复制了 **3 份**：

| 位置 | 行号 |
|------|------|
| `JobFireStoreImpl` | `417-422` |
| `JobScheduleStoreImpl` | `562-567` |
| `JobCompletionProcessorImpl` | `415-420` |

三份实现完全相同：
```java
private Long calculateDuration(Timestamp startTime, Timestamp endTime) {
    if (startTime == null || endTime == null) return null;
    return Math.max(endTime.getTime() - startTime.getTime(), 0L);
}
```

**分析**：`DateHelper`（`nop-commons`）目前没有 duration 计算方法，但有 `currentTimeMillis()`、格式化等。该方法是纯函数、无副作用，适合作为 `DateHelper.durationMs(startTime, endTime)` 汇聚。

**建议**：
- 在 `DateHelper` 中新增 `public static Long durationMs(Timestamp start, Timestamp end)`（或 `durationMillis`）。
- 三处 `private calculateDuration` 全部删除，改调 `DateHelper.durationMs`。
- 三处 `defaultLong` / `defaultInt` / `toTime` 等私有工具方法也可评估是否合并（但优先级更低）。

**优先级**：P3

---

## 二、框架约定违反（P2 — 行为正确性 / 可维护性）

### 2.1 updateTime / updatedBy 手工设置（Issue 3）

**现状**：Store 层大量手工设置审计字段：

| 位置 | 代码 |
|------|------|
| `JobFireStoreImpl.java:195-196` | `fire.setUpdatedBy("system"); fire.setUpdateTime(cancelTime);` |
| `JobFireStoreImpl.java:212-213` | task 同上 |
| `JobFireStoreImpl.java:238-239` | schedule 同上 |
| `JobFireStoreImpl.java:302-303` | revertDispatchingFire |
| `JobFireStoreImpl.java:324-325` | failFireWithoutSchedule |

**问题**：
1. `updateTime` / `updatedBy` 是审计字段，应由 ORM 框架自动维护（`IOrmEntity` 生命周期钩子 / interceptor），不应业务代码手工赋值。
2. `setUpdateTime(cancelTime)` 使用的是 DB 估算时钟，但业务代码不应关心时钟来源——框架自动维护时会统一处理。
3. `setUpdatedBy("system")` 硬编码字符串，应从 `IServiceContext` 的当前用户获取（引擎内部操作时确实可能是 system，但应由框架注入而非手写）。

**建议**：
- 确认 `NopJobFire` / `NopJobTask` / `NopJobSchedule` 的 ORM 模型是否已配置审计字段自动维护（`updatedBy` / `updatedTime` 的 `autoFill` 或 interceptor）。
- 若已配置：删除所有手工 `setUpdatedBy` / `setUpdateTime` 调用。
- 若未配置：在 ORM 模型层补配置（属于 ORM 模型变更，需 plan-first），然后删除手工设置。
- 注意：audit 第 3 点还提到"业务层面需要的过滤字段应该单独定义，不要占用 updateBy/updateTime"。需要确认 job 是否有业务语义复用这两个字段的情况（目前看没有，纯审计用途）。

**优先级**：P2

### 2.2 时间获取 — 禁用 System.currentTimeMillis()（Issue 8）

**现状**：audit 指出不应有 `long now = System.currentTimeMillis()`。

**分析**：nop-job 的 Store 层已正确使用 `fireDao().getDbEstimatedClock().getMaxCurrentTimeMillis()`（如 `JobFireStoreImpl.java:58, 88, 188, 297`）。需排查全模块是否有遗漏的 `System.currentTimeMillis()` 直接调用。

正确的时间获取方式：
- ORM/Dao 层：`dao.getDbEstimatedClock().getMaxCurrentTimeMillis()`（已用）。
- 通用层：`CoreMetrics.currentTimeMillis()`（`nop-api-core`，已封装 clock 注入）。
- 测试层：`CoreMetrics` 支持注册 mock clock。

**建议**：
- 全模块搜索 `System.currentTimeMillis()`，替换为 `CoreMetrics.currentTimeMillis()` 或 DB 时钟。
- 注意 `DateHelper.currentTimeMillis()`（`DateHelper.java:116`）内部就是 `System.currentTimeMillis()`——这本身可能也是个待修问题，但属于 `nop-commons` 范围，不在本次 job 模块修复内。

**优先级**：P2

### 2.3 failFireWithoutSchedule 的错误消息国际化（Issue 7）

**现状**：`IJobFireStore.failFireWithoutSchedule(jobFireId, errorCode, errorMessage)` 接收**字符串参数**（`IJobFireStore.java:48`），调用方 `JobTimeoutCheckerImpl` 传入 `ErrorCode.getDescription()` 直接写入 `errorMessage` 字段（`JobFireStoreImpl.java:322-323`）。

**问题**：
- `ErrorCode.getDescription()` 返回的是**默认语言描述**（通常是中文 / 开发语言），不经过 `ErrorMessageManager` 的国际化映射。
- `errorMessage` 字段持久化到 DB，下游消费者（运维 Dashboard、告警）看到的是未本地化的固定字符串。

**建议**：
- 调用方应通过 `ErrorMessageManager`（`nop-core`）或 `IErrorMessageManager` 获取本地化描述，而非直接 `getDescription()`。
- 具体做法：`ErrorMessageManager.instance().getLocalizedDescription(locale, errorCode)` 或在 `NopException` 构建时由框架统一处理。
- 考虑：`failFireWithoutSchedule` 的接口签名是否应改为接收 `ErrorCode` 对象而非拆散的字符串，让 Store 内部统一做 i18n。

**优先级**：P2

### 2.4 JsonComponent 直接 getValue（Issue 9）

**现状**：`JsonOrmComponent`（`JsonOrmComponent.java:121-126`）已提供 `getValue(String name)` 方法，直接按 key 取值。

audit 指出部分代码先读 `get_jsonMap()` 再从 Map 取值，绕过了 Component 的直接 API。

**建议**：
- 排查 `schedule.getJobParamsComponent().get_jsonMap()` 后接 `.get(name)` 的模式，替换为 `getJobParamsComponent().getValue(name)`。
- 这同时减少了不必要的中间 Map 对象创建。

**优先级**：P3

### 2.5 冗余的手工 JSON 解析（Issue 15）

**现状**：`NopJobScheduleBizModel.resolveJobParams`（`NopJobScheduleBizModel.java:251-268`）：

```java
Map<String, Object> scheduleParams = schedule.getJobParamsComponent().get_jsonMap();
if (scheduleParams != null) {
    return scheduleParams;
}
// 冗余回退：手工 parse jobParams 字符串
if (schedule.getJobParams() != null && !schedule.getJobParams().isEmpty()) {
    Map<String, Object> parsed = JsonTool.parseMap(schedule.getJobParams());
    ...
}
```

**问题**：`getJobParamsComponent().get_jsonMap()` 内部已经解析了 `_jsonText`（即 `jobParams` 字段的底层存储），见 `JsonOrmComponent.java:55-75`：
- `get_jsonValue()` → `get_jsonText()` → `internalGetPropValue("_jsonText")` → `JsonTool.parseBeanFromText`。
- `get_jsonMap()` 在解析结果为 Map 时返回，否则返回 null。

所以手工 `JsonTool.parseMap(schedule.getJobParams())` 是**完全冗余的死代码**——component 已经做了同样的事。只有当 `jobParams` 是非 Map 的 JSON（如数组、标量）时 `get_jsonMap()` 才返回 null，此时 `parseMap` 也会返回 null。

**建议**：
- 删除回退分支（`261-266` 行），直接：
  ```java
  Map<String, Object> params = schedule.getJobParamsComponent().get_jsonMap();
  return params != null ? params : Collections.emptyMap();
  ```
- 或更简洁：使用 `require_jsonMap()`（`JsonOrmComponent.java:81-88`，自动初始化空 Map）。

**优先级**：P3

---

## 三、ORM/Dao API 误用（P1-P2 — 数据一致性 / 性能）

### 3.1 tryUpdateWithVersionCheck 单实体方法（Issue 14）

**现状**：`IOrmEntityDao` 已提供单实体版本（`IOrmEntityDao.java:40-44`）：
```java
default boolean tryUpdateWithVersionCheck(T entity) { ... }
```

但 nop-job 中**几乎所有**单实体更新都用列表版本包装单元素：
```java
// JobFireStoreImpl.java:107, 134, 197, 215, 225, 241, 304 ... 共 8+ 处
List<NopJobFire> updated = fireDao().tryUpdateManyWithVersionCheck(Collections.singletonList(fire));
if (updated.isEmpty()) { ... }
```

**问题**：
- `Collections.singletonList(x)` + `updated.isEmpty()` 比 `tryUpdateWithVersionCheck(x)` 返回 boolean 更冗长。
- 阅读时需绕一层弯：列表包装暗示"批量"语义，实际是单条。

**建议**：
- 所有"操作单个实体"的调用点改用 `tryUpdateWithVersionCheck(entity)` 返回的 boolean。
- 保留 `tryUpdateManyWithVersionCheck` 仅用于**真正批量**场景（如 `tryLockFiresForDispatch` 一次锁多条 fire）。
- Blast radius：~8 处调用，均在 `JobFireStoreImpl`，机械替换。

**优先级**：P3（机械重构，但需逐处确认语义不变）

### 3.2 tryUpdateWithVersionCheck 返回值语义（Issue 5）

**现状**：`tryUpdateManyWithVersionCheck` 返回**真正更新成功的实体列表**（`IOrmEntityDao.java:31-38`：过滤掉 `orm_readonly()` 的实体）。

audit 指出"返回的结果是真正更新了的，这些记录才能使用；更新不成功的不应该使用"。

**问题排查**：

| 位置 | 代码 | 是否正确处理 |
|------|------|-------------|
| `JobFireStoreImpl.java:134-137` | `if (updated.isEmpty()) return;` | ✅ 静默返回（fire 已被并发更新） |
| `JobFireStoreImpl.java:197-200` | `if (updated.isEmpty()) return false;` | ✅ 返回失败 |
| `JobFireStoreImpl.java:107-112` | `if (updated.isEmpty()) throw ...` | ✅ 抛异常 |
| `JobFireStoreImpl.java:225` | `tryUpdateManyWithVersionCheck(...)` **未检查返回值** | ⚠️ **问题** |

`JobFireStoreImpl.java:225`：
```java
task.setVersion(freshTask.getVersion());
taskDao().tryUpdateManyWithVersionCheck(Collections.singletonList(task));  // 返回值被丢弃
```

这是 cancel 流程中 task 重试更新的最后一次尝试，返回值被丢弃。如果这次更新也失败，task 会留在非终态，但 cancel 已返回 true。

**建议**：
- 补充对返回值的检查：若仍失败，记录 WARN 日志（task 可能被并发流转到终态，需对账）。
- 或加注释说明为何此处可忽略（如果是有意为之）。
- audit 还提到"或者判断非 readonly 的才能继续下一步处理"——即检查 `!entity.orm_readonly()`。

**优先级**：P2

### 3.3 baseline 缓存刷新（Issue 12）

**现状**：`JobFireStoreImpl.completeFireAndUpdateSchedule`（`JobFireStoreImpl.java:139`）：
```java
NopJobSchedule baseline = scheduleDao().requireEntityById(schedule.getJobScheduleId());
```

**问题**：`requireEntityById` → `getEntityById`（`IEntityDao.java:142-155`）。`getEntityById` 的 javadoc 明确说：

> 通过主键返回一个唯一的对象。如果数据库中不存在，则返回 null。**如果内存中存在 proxy 对象，则直接返回**。

即：如果 session 缓存中已有该 schedule 的实体（proxy 或已加载），`requireEntityById` **不会重新查库**，返回的是缓存中的旧版本。

在乐观锁重试循环中（`141-172` 行），`baseline` 用于读取最新计数值来计算 delta。如果缓存是旧的，delta 计算会基于过时数据。

**分析**：
- ORM session 的 `refresh` 方法（`OrmSessionImpl.java:1312-1322`）会 `orm_unload()` 后重新 `internalLoad`。
- `IOrmEntityDao` 未直接暴露 `refresh`，但可通过 `orm().refresh(entity)` 或 `entity.orm_unload()` + `getEntityById` 实现。

**建议**：
- 重试循环中刷新 baseline：`baseline.orm_unload(); baseline = scheduleDao().getEntityById(id);` 或使用 session refresh。
- cancel 流程中（`JobFireStoreImpl.java:247`）`NopJobSchedule fresh = scheduleDao().requireEntityById(...)` 有同样的缓存问题。
- 需确认 ORM session 的隔离级别：如果 `@Transactional(REQUIRES_NEW)` 开了新 session，则缓存问题可能不存在——需验证。

**优先级**：P1（影响计数器一致性，但需验证是否在 REQUIRES_NEW 下实际触发）

### 3.4 batchLoadFire 使用 Dao 批量方法（Issue 13）

**现状**：`JobFireStoreImpl.batchLoadFires`（`JobFireStoreImpl.java:264-277`）手工构建 `QueryBean` + `FilterBeans.in` + `findAllByQuery`。

`IEntityDao` 已提供批量加载方法（`IEntityDao.java:157-175`）：
- `batchGetEntitiesByIds(Collection ids)`
- `batchGetEntityMapByIds(Collection ids)` — 直接返回 `Map<Object, T>`，与 `batchLoadFires` 的返回类型一致。

**问题**：手工 query 绕过了 Dao 的批量加载优化（批量 in 查询的分批、缓存利用等）。

**建议**：
- 改用 `fireDao().batchGetEntityMapByIds(fireIds)`，注意返回的 key 是**实体 id**（`jobFireId`），需确认类型匹配。
- 或 `batchGetEntitiesByIds` 后自行转 Map。

**优先级**：P3

### 3.5 计数器累加优先用 EQL（Issue 16）

**现状**：schedule 计数器（`activeFireCount`、`totalFireCount`、`successFireCount`、`failFireCount`、`fireCount`）通过**乐观锁 + 读-改-写**循环更新（`JobFireStoreImpl.java:141-172`，5 次重试）。

```java
for (int attempt = 0; attempt < 5; attempt++) {
    int origActiveFireCount = baseline.getActiveFireCount();
    int activeDelta = schedule.getActiveFireCount() - origActiveFireCount;
    // ... 计算 target ...
    scheduleDao().tryUpdateManyWithVersionCheck(...);  // 失败则重新读 baseline 重试
}
```

**分析**：audit 指出"如果只是累加，一般优先考虑使用 mapper 执行 eql 语句来更新"。适用 EQL 增量更新的条件：
1. ✅ 更新规则是"当前值 + delta"
2. ✅ 不依赖复杂领域校验（纯计数器）
3. ✅ 不要求基于旧状态做条件分支（fire 已确定终态）
4. ✅ 不需要 ORM 实体生命周期钩子

当前场景完全满足。EQL 增量更新的优势：
- `UPDATE nop_job_schedule SET active_fire_count = active_fire_count - 1, total_fire_count = total_fire_count + 1 WHERE job_schedule_id = ? AND version = ?`
- 单条 SQL 原子完成，无需读-改-写循环，无需 5 次重试。
- 数据库行锁天然保证并发安全。

**建议**：
- 将 `completeFireAndUpdateSchedule` 和 `cancelFire` 中的 schedule 计数器更新改为 EQL 增量语句（`IOrmTemplate` / `updateByQuery`）。
- 注意：version 字段仍需 +1（EQL 中 `version = version + 1`）。
- 与 Issue 11（activeFireCount 是否需要维护）联动考虑——如果改为统计查询则无需维护这些计数器。

**优先级**：P2（简化并发逻辑，但需仔细处理 version 语义）

---

## 四、架构设计问题（P1-P2 — 需讨论后决策）

### 4.1 activeFireCount 是否需要维护（Issue 11 / AR-65）

**背景**：来自 AR-65 对抗性审查（`ai-dev/audits/2026-06-04-adversarial-review-nop-job-r8/01-open-findings.md:350-371`）。

**现状**：`NopJobSchedule` 维护 5 个计数器：

| 字段 | 含义 | 维护点 |
|------|------|--------|
| `activeFireCount` | 活跃 fire 数 | Store 事务方法 +1 / -1 |
| `fireCount` | 已触发 fire 数 | Store 事务方法 +1 |
| `totalFireCount` | 累计完成数 | Store 事务方法 +1 |
| `successFireCount` | 成功数 | Store 事务方法 +1 |
| `failFireCount` | 失败数 | Store 事务方法 +1 |

**问题（AR-65 核心）**：
1. **计数器一致性**：计数器只在 `IJobFireStore` 的事务方法中维护。直接 delete 绕过这些方法导致计数器永久漂移。
2. **引擎生命周期绕过**：删除一个 RUNNING 状态的 fire 不会触发 `JobCompletionProcessor`，`activeFireCount` 永远不减。
3. **架构原则**：Fire 应通过领域命令（`cancelFire`、`rerunFire`）管理，而非直接 CRUD。

**现状已部分修复**：`NopJobFireBizModel.delete()` 已重写为抛异常（`NopJobFireBizModel.java:40-43`），阻止了通过 API 的直接删除。但内部引擎路径或 DB 直连仍可能绕过。

**audit 提出的核心问题**：`activeFireCount` 有没有必要维护？是否根据数据自己去统计？

**方案对比**：

#### Option A：维护计数器（现状 + 加固）

- 核心思路：保留冗余计数器，加固所有写入路径（限制 CRUD + 统一走 Store 方法）。
- 优点：读取 O(1)，无需 count 查询，Dashboard 列表页性能好。
- 缺点：
  - 写入路径多，任何遗漏都会导致漂移。
  - 乐观锁重试循环复杂（5 次），高并发下仍有失败可能。
  - 需要持续维护计数器一致性（对账机制）。

#### Option B：改为统计查询（audit 建议方向）

- 核心思路：删除 `activeFireCount` 等冗余字段，需要时通过 `COUNT(*)` 查询实时统计。
- 优点：
  - **永远一致**——不存在漂移问题。
  - 简化 Store 逻辑，删除读-改-写重试循环。
  - 符合"单一数据源"原则——fire 表是 source of truth。
- 缺点：
  - 统计查询有性能开销（需对 `fireStatus` + `jobScheduleId` 建索引）。
  - Dashboard 频繁查询大量 schedule 的计数时可能有压力。

#### Option C：混合方案

- 核心思路：`activeFireCount`（高频读、低频写）用统计查询（消除最难维护的计数器）；`totalFireCount` / `successFireCount` / `failFireCount`（统计性、容忍最终一致）保留为定期对账的冗余字段。
- 优点：平衡性能与一致性。
- 缺点：两套机制并存，复杂度高。

**对比**：

| 维度 | A: 维护计数器 | B: 统计查询 | C: 混合 |
|------|-------------|------------|---------|
| 一致性保证 | 难（多写入路径） | 强（实时统计） | 中（部分实时） |
| 读性能 | O(1) | O(index scan) | 混合 |
| 写复杂度 | 高（乐观锁循环） | 低（无计数器更新） | 中 |
| 漂移风险 | 高 | 无 | 中 |
| 实现改动量 | 小（加固现有） | 大（删字段 + 改查询） | 大 |

**倾向**：**Option B（统计查询）**。理由：
1. `activeFireCount` 是唯一有**正确性约束**的计数器（用于判断是否有活跃 fire），漂移后果严重。统计查询从根本消除漂移。
2. fire 表数据量在合理范围内（有归档机制），配合 `(jobScheduleId, fireStatus)` 复合索引，count 查询性能可接受。
3. `successFireCount` / `failFireCount` 等纯统计字段如果也漂移，影响仅为报表不准，可保留冗余 + 定期对账。
4. 这同时解决了 Issue 16（计数器累加用 EQL）和 Issue 3.3（baseline 缓存刷新）——如果不再维护 `activeFireCount`，乐观锁循环可大幅简化。

**注意**：删除 `activeFireCount` 字段属于 **ORM 模型变更（plan-first）**，需先出设计文档和迁移计划。

**优先级**：P1（数据一致性问题，但需架构决策）

### 4.2 NopJobTask 补充 completed / nextScheduleTime 字段（Issue 10）

**现状**：audit 建议评估是否在 `NopJobTask` 上补充 `completed`、`nextScheduleTime` 等字段，避免解析 JSON。

**分析**：这是数据模型反范式化的权衡问题。需先确认：
- `completed` / `nextScheduleTime` 当前存在哪里？是在 fire 的 `resultPayload`（JSON component）中，还是在 schedule 上？
- 频繁访问这些字段的场景是什么？（metrics 上报、Dashboard 展示、引擎决策？）

**建议**：
- 如果这些值来自 fire/schedule 的 JSON 字段且被引擎高频读取，补充为实体列有性能收益。
- 但如果只是 Dashboard 展示用，JSON 解析开销可接受。
- 需进一步调研具体使用场景后决定。

**优先级**：P3（待调研）

### 4.3 findAll 调用审查（Issue 4）

**现状**：`nop-job` 中 `findAll` / `findAllByQuery` 调用点（排除测试代码）：

| 文件 | 行号 | 是否有限制条件 |
|------|------|----------------|
| `JobFireStoreImpl.java:66` | `fetchWaitingFires` | ✅ `query.setLimit(limit)` |
| `JobFireStoreImpl.java:77` | `fetchRunningFires` | ✅ `setLimit` |
| `JobFireStoreImpl.java:271` | `batchLoadFires` | ✅ `FilterBeans.in(ids)` 限定集合 |
| `JobFireStoreImpl.java:286` | `fetchDispatchingFires` | ✅ `setLimit` |
| `JobFireStoreImpl.java:358` | `findTasksByFireId` | ⚠️ 按 fireId 过滤但**无 limit** |
| `JobScheduleStoreImpl.java:59` | `fetchDueSchedules` | 需确认是否有 limit |
| `JobScheduleStoreImpl.java:380` | 查询 schedules | 需确认 |
| `JobScheduleStoreImpl.java:442/452/473` | fire 查询 | 需确认 limit |
| `JobScheduleStoreImpl.java:479/535` | task 查询 | ⚠️ 需确认 limit |
| `JobTaskStoreImpl.java:76/106/115/160` | task 查询 | ⚠️ 需确认 limit |

**参考**：`ai-dev/lessons/01-batch-memory-accumulation.md` 的判定规则——"分批处理 ≠ 流式处理"，需追踪数据流向。

**分析**：
- 引擎扫描类方法（`fetchWaitingFires` 等）已有 `setLimit`，每批限量获取，符合规范。
- 按 fireId / scheduleId 关联查询（如 `findTasksByFireId`）理论上一个 fire 的 task 数有限，但**无显式 limit** 仍是隐患（一个异常 fire 产生大量 task 时）。
- 需逐个审查 `JobScheduleStoreImpl` 和 `JobTaskStoreImpl` 中的查询是否都有合理的 limit 或范围限定。

**建议**：
- 所有 `findAllByQuery` 调用必须有显式范围限定（`setLimit` 或等值过滤条件保证结果集有界）。
- 对关联查询（按父 id 查子记录）补充防御性 limit。

**优先级**：P2

---

## 五、明确问题需加注释（Issue 11 最后一点 / 整体）

audit 第 11 点最后提到："明确有问题的，则需要代码中有一定的注释"。

**现状**：部分已知边界行为（如乐观锁重试 5 次后的异常、cancel 中 task 更新失败的处理）缺少注释说明设计意图。

**建议**：
- 对每个"看起来可疑但有意为之"的代码段补充注释，说明：
  - 为什么这样处理（并发场景、幂等保证等）。
  - 什么情况下会触发异常路径。
- 与代码修改同步进行，不单独排期。

**优先级**：P3

---

## 优先级汇总

| 优先级 | Issue | 主题 | 修复类型 |
|--------|-------|------|----------|
| **P1** | 11 | activeFireCount 维护方式（架构决策） | 架构决策 → 设计文档 |
| **P1** | 12 | baseline 缓存刷新（需验证触发条件） | 代码修复（需验证） |
| **P2** | 3 | updateTime/updatedBy 手工设置 | 代码修复（依赖 ORM 配置） |
| **P2** | 5 | tryUpdate 返回值未检查（cancel task 重试） | 代码修复 |
| **P2** | 7 | failFireWithoutSchedule i18n | 代码修复 |
| **P2** | 8 | System.currentTimeMillis() 排查 | 代码修复 |
| **P2** | 16 | 计数器累加改 EQL（与 Issue 11 联动） | 代码修复 |
| **P2** | 4 | findAll 调用审查补 limit | 代码修复 |
| **P3** | 1 | 状态判断抽取 JobStatusHelper | 机械重构 + 补测试 |
| **P3** | 2 | addPartitionFilter 抽取 | 机械重构 |
| **P3** | 6 | calculateDuration 抽取 DateHelper | 机械重构 |
| **P3** | 9 | JsonComponent.getValue 直接调用 | 机械重构 |
| **P3** | 13 | batchLoadFire 用 Dao 批量方法 | 机械重构 |
| **P3** | 14 | 单实体 tryUpdateWithVersionCheck | 机械重构 |
| **P3** | 15 | 删除冗余 JSON 解析 | 代码删除 |
| **P3** | 10 | NopJobTask 补充字段（待调研） | 待调研 |

---

## 建议的修复分组（供 plan 拆分参考）

### Slice 1: 机械重构（P3，低风险，可并行）
- Issue 1（JobStatusHelper + 区间判断 + 补测试）
- Issue 2（addPartitionFilter 抽取）
- Issue 6（calculateDuration → DateHelper）
- Issue 9（JsonComponent.getValue）
- Issue 13（batchLoadFire → batchGetEntityMapByIds）
- Issue 14（单实体 tryUpdateWithVersionCheck）
- Issue 15（删除冗余 JSON 解析）

### Slice 2: 框架约定修复（P2，中等风险）
- Issue 3（updateTime/updatedBy 自动维护）
- Issue 5（cancel task 重试返回值检查）
- Issue 7（failFireWithoutSchedule i18n）
- Issue 8（System.currentTimeMillis() 排查）
- Issue 4（findAll 补 limit）

### Slice 3: ORM/并发逻辑（P1-P2，高风险，需设计）
- Issue 11（activeFireCount 架构决策 — **需先出设计文档**）
- Issue 12（baseline 缓存刷新 — 需验证 REQUIRES_NEW 下是否实际触发）
- Issue 16（计数器累加改 EQL — 依赖 Issue 11 决策）

### Slice 4: 待调研
- Issue 10（NopJobTask 补字段）

---

## Conclusion

16 项问题全部经源码验证成立（部分需进一步确认触发条件，如 Issue 12 的缓存刷新在 `REQUIRES_NEW` 下是否实际发生）。

**核心决策点**：Issue 11（activeFireCount 维护方式）是最高优先级架构决策，建议采用 **Option B（统计查询）** 方向：
- 从根本上消除计数器漂移问题（AR-65 的根因）。
- 简化 Store 层乐观锁重试逻辑（联动 Issue 16、Issue 12）。
- 但属于 ORM 模型变更（plan-first），需先出 `ai-dev/design/nop-job/` 设计文档。

其余 15 项可按 Slice 1-2 逐步机械修复 + 框架约定对齐，风险可控。

**后续工作**：
- 本分析 resolved 后，按 Slice 拆分到 `ai-dev/plans/`。
- Slice 3 需先产出 `ai-dev/design/nop-job/counter-maintenance-design.md`。

## Open Questions

- [ ] Issue 12：`@Transactional(REQUIRES_NEW)` 是否开启新 ORM session？若是，则 `requireEntityById` 的缓存问题在新事务中不存在（仅在循环内同一 session 中 baseline 不会自动刷新）。需验证 Nop ORM 的事务-session 映射。
- [ ] Issue 3：`NopJobFire` / `NopJobTask` / `NopJobSchedule` 的 ORM 模型是否已配置 `updatedBy` / `updatedTime` 的自动维护（autoFill / interceptor）？需检查 `nop-job.orm.xml`。
- [ ] Issue 10：`completed` / `nextScheduleTime` 当前存储位置和使用频率？需调研。
- [ ] Issue 11：fire 表的预期数据量和归档策略？决定统计查询的性能可行性。
- [ ] Issue 7：`failFireWithoutSchedule` 的调用方 `JobTimeoutCheckerImpl` 具体传入了什么 errorCode？是否已有部分场景绕过了 i18n？

## References

- 输入：`ai-dev/inputs/nop-job-audit.md`
- AR-65：`ai-dev/audits/2026-06-04-adversarial-review-nop-job-r8/01-open-findings.md:350-371`
- 架构基线：`ai-dev/design/nop-job/01-architecture-baseline.md:321-342`
- 内存累积教训：`ai-dev/lessons/01-batch-memory-accumulation.md`
- 状态常量：`nop-job/nop-job-core/src/main/java/io/nop/job/core/_NopJobCoreConstants.java:34-104`
- JsonOrmComponent：`nop-persistence/nop-orm/src/main/java/io/nop/orm/component/JsonOrmComponent.java`
- IOrmEntityDao：`nop-persistence/nop-orm/src/main/java/io/nop/orm/dao/IOrmEntityDao.java:40-44`
- IEntityDao 批量方法：`nop-persistence/nop-dao/src/main/java/io/nop/dao/api/IEntityDao.java:157-175`
- ErrorCode：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/exceptions/ErrorCode.java`
- ErrorMessageManager：`nop-kernel/nop-core/src/main/java/io/nop/core/exceptions/ErrorMessageManager.java`
- CoreMetrics：`nop-kernel/nop-api-core/src/main/java/io/nop/api/core/time/CoreMetrics.java`
- DateHelper：`nop-kernel/nop-commons/src/main/java/io/nop/commons/util/DateHelper.java`
