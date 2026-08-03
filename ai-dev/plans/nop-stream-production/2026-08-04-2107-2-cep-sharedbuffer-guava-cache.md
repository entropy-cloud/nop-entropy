# 54. CEP SharedBuffer 缓存改进（G65）

> Plan Status: active
> Last Reviewed: 2026-08-04
> Source: `ai-dev/backlog/nop-stream-production-roadmap.md` Stage 54; `ai-dev/analysis/nop-stream/08-gap-analysis.md` G65; `ai-dev/design/nop-stream/cep-design.md`
> Mission: nop-stream-production
> Work Item: 54. CEP SharedBuffer 缓存改进（G65）
> Related: Stage 11（CEP 状态后端接入，Item 11 — G65 原始归属）

## Purpose

用 Guava `Cache` 替换 `SharedBuffer` 中的自定义 `LruCache`，消除双结构竞态（`ConcurrentHashMap` + `LinkedHashMap` 非原子操作），获得内建原子 LRU + 统计能力。同时接线 cache 统计周期输出（当前 `releaseCacheStatisticsTimer()` 为 no-op），关闭 G65 gap。

## Current Baseline

经 live 仓库核对（2026-08-04，含独立子 agent 对抗性审查验证）：

- **Guava 已是仓库管理的依赖**：`nop-kernel/nop-commons/pom.xml:70-71` 直接声明 `com.google.guava:guava`。`nop-commons` 生产代码中 5+ 文件使用 `com.google.common.*`（`StringHelper`、`ByteHelper`、`CollectionHelper`、`HashHelper`、`DefaultRateLimiter` 等）。`nop-stream-cep` 经 `nop-stream-cep → nop-stream-core → nop-commons` 传递获得 Guava compile classpath。D6 决策点「是否引入 Guava」实际上已由仓库现状回答：**Guava 已可用**。
- **`SharedBuffer<V>`**（`nop-stream-cep/.../nfa/sharedbuffer/SharedBuffer.java:59`，388 行）：两个缓存实例：
  - `eventsBufferCache`（`:79`）：`LruCache<EventId, Lockable<V>>`，slot 数由 `CEP_SHARED_BUFFER_EVENT_CACHE_SLOTS`（默认 1024）控制。
  - `entryCache`（`:84`）：`LruCache<NodeId, Lockable<SharedBufferNode>>`，slot 数由 `CEP_SHARED_BUFFER_ENTRY_CACHE_SLOTS`（默认 1024）控制。
  - **缓存模式混合**：`upsertEvent`/`upsertEntry`/`getEvent`/`getEntry` 为 write-through（同时操作 cache + backing `MapState`）。但 `flushCache()`（`:336-359`）为 **write-back flush + clear-on-success**——快照 cache → `putAll` 到 state → **清空 cache**（`keySetRemoveAll`）→ 异常时回填（`putAll` 恢复）。调用方为 `SharedBufferAccessor.close()`。**替换时必须保留 flushCache 的 clear-on-success 行为**。
- **`LruCache<K,V>`**（`:17`，137 行）：自定义线程安全 LRU。
  - **双结构竞态隐患**：`map`（`ConcurrentHashMap`）与 `accessTracker`（`LinkedHashMap` access-order）分离维护。`put()`（`:34`）先 `map.put` 后 `synchronized(accessTracker.put)`——两者间存在窗口，另一线程的 `evictOverflow()`（`:126`）可能 over-eviction。
  - `evictOverflow()`：O(n) worst case。
  - **注意**：`SharedBuffer` 单线程访问（per-key 经 `SharedBufferAccessor` try-with-resources in `CepOperator`），竞态在实践中可能不触发，但代码结构仍脆弱。
- **`releaseCacheStatisticsTimer()`**（`SharedBuffer.java:230`）：**空方法体（no-op）**。唯一调用方是 `CepOperator.close()`（`:327`）——**close-time，非周期性**。`CEP_CACHE_STATISTICS_INTERVAL`（默认 30min）配置存在但统计从未被周期性记录或输出。
- **现有测试 `TestSharedBufferLruCache.java`**（`src/test/.../nfa/sharedbuffer/TestSharedBufferLruCache.java`，108 行，4 个 `@Test`）：测试 `SharedBuffer` 的 LRU 驱逐行为。替换 `LruCache` 后此文件会编译失败（引用 `SharedBuffer` 的 cache 行为，不直接引用 `LruCache` 类名——但测试名含 "LruCache" 且断言驱逐语义）。**必须迁移**。
- **`SharedBufferCacheConfig`**（`:31`）：`@DataBean`，3 字段（`eventsBufferCacheSlots`、`entryCacheSlots`、`cacheStatisticsInterval`），全部已配置化。
- **`NopCepConfigs`**（`:18`）：3 个 config reference（event-slots=1024、entry-slots=1024、statistics-interval=30min）。
- **G65 gap analysis 原文**（`08-gap-analysis.md:138`）：「SharedBuffer 缓存使用 ConcurrentHashMap 替代 Guava Cache，无 LRU 驱逐 | ExtractionDegradation/P3」——**注意**：此描述与 live code 不符（live code 已有 `LruCache` 含 LRU 驱逐），说明 LruCache 是 Item 11 后添加的部分修复，但 G65 从未显式关闭。

### 真正剩余的 gap

- 自定义 `LruCache` 的双结构竞态。
- `releaseCacheStatisticsTimer()` 为 no-op——统计配置无消费方，且无周期性调度。
- 现有测试需迁移。
- G65 从未显式关闭。

## Goals

- 用 Guava `Cache<K,V>` 替换 `LruCache<K,V>`，消除双结构竞态，获得内建原子 LRU + 统计。
- 接线 cache 统计**周期性**输出（按 `CEP_CACHE_STATISTICS_INTERVAL` 间隔注册 processing-time timer），非仅 close-time 单次。
- 迁移现有 `TestSharedBufferLruCache` 测试。
- 关闭 G65 gap。

## Non-Goals

- CEP 算法逻辑变更（NFA、pattern matching 语义不变）。
- SharedBuffer 的 backing state（`MapState`）结构变更。
- Cache 容量动态调整 / 自适应淘汰策略。
- 引入 Caffeine（已有可用但 roadmap D6 推荐与 Flink CEP 一致用 Guava；不拒绝 Caffeine 但不在本 plan 讨论）。
- Guava 除 Cache 以外的工具引入——仅用 `com.google.common.cache.Cache` / `CacheBuilder`。

## Scope

### In Scope

- `SharedBuffer` 中 `eventsBufferCache`/`entryCache` 从 `LruCache` 改为 Guava `Cache`（`CacheBuilder.newBuilder().maximumSize(slots).recordStats().build()`）。
- 删除 `LruCache.java`。
- 迁移 `TestSharedBufferLruCache.java` → `TestSharedBufferCache.java`（保留断言语义，适配 Guava Cache API）。
- `releaseCacheStatisticsTimer()` 实现：非空方法体——释放周期统计 timer handle（如果注册了）。
- 新增 `SharedBuffer.logCacheStatistics()` 方法：读取 `Cache.stats()`，输出 hit/miss/eviction/size 日志。
- `CepOperator.open()` 注册周期性 processing-time timer（按 `CEP_CACHE_STATISTICS_INTERVAL`），`onProcessingTime` 回调中调用 `logCacheStatistics()` 并 re-arm timer。
- `RemovalListener` 仅在 `RemovalCause.wasEvicted()`（SIZE 驱逐）时记录 debug 日志，不对手动 `invalidate`/`clear` 触发日志。
- `SharedBufferCacheConfig` 保持不变（slot 数语义映射到 Guava `maximumSize`）。
- 测试：LRU 驱逐行为验证 + 统计输出验证 + CEP E2E 回归。

### Out Of Scope

- CEP 算法变更。
- Backing `MapState` 结构变更。
- 自适应 cache 策略。
- Caffeine 迁移。

## Execution Plan

### Phase 1 - Guava Cache 替换 + 测试迁移

Status: planned
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java`; `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/LruCache.java`（删除）; `nop-stream/nop-stream-cep/src/test/java/io/nop/stream/cep/nfa/sharedbuffer/TestSharedBufferLruCache.java`（重命名为 `TestSharedBufferCache.java`）; `nop-stream/nop-stream-cep/pom.xml`（显式声明 guava 依赖——传递可用，但声明为 direct dependency 是好实践）; `ai-dev/design/nop-stream/cep-design.md`

- Item Types: `Fix`

- [ ] `nop-stream-cep/pom.xml` 显式声明 `guava` compile 依赖（经 `nop-commons` 传递可用，但显式声明表意清晰——non-functional for compilation，declarative for dependency hygiene）。
- [ ] `SharedBuffer` 构造器（`:87-117`）：`eventsBufferCache`/`entryCache` 从 `new LruCache<>(slots)` 改为 `CacheBuilder.newBuilder().maximumSize(slots).recordStats().removalListener(...).build()`。RemovalListener 仅在 `RemovalCause.wasEvicted()` 时 log debug。
- [ ] `SharedBuffer` 所有缓存调用点适配 Guava `Cache` API（使用 `getIfPresent` 而非 `get(key, callable)`——后者引入 `ExecutionException` 且改变 null 返回语义）：

  | LruCache 方法 | 调用点（行号） | Guava 等价 |
  |---|---|---|
  | `get(key)` → nullable V | `getEntry:293`、`getEvent:316` | `cache.getIfPresent(key)` |
  | `put(key, value)` | `registerEvent:199`、`upsertEvent:240`、`upsertEntry:256`、`getEntry:299`、`getEvent:322` | `cache.put(key, value)` |
  | `remove(key)` | `removeEvent:271`、`removeEntry:281`、`registerEvent:203`（rollback）、`upsertEvent:244`（rollback）、`upsertEntry:260`（rollback） | `cache.invalidate(key)` |
  | `containsKey(key)` | `registerEvent:189`（`eventsBufferCache.containsKey`） | `cache.asMap().containsKey(key)` |
  | `isEmpty()` | `isEmpty:226`、`flushCache:337,348` | `cache.asMap().isEmpty()` |
  | `size()` | `getEventsBufferCacheSize:366`、`getSharedBufferNodeCacheSize:386` | `cache.size()` |
  | `putAll(map)` | `flushCache:344,355`（rollback 路径） | `cache.asMap().putAll(map)` |
  | `forEach(action)` | `flushCache:339,350`（快照） | `cache.asMap().forEach(action)` |
  | `removeIf(predicate)` | `advanceTime:179` | `cache.asMap().keySet().removeIf(predicate)` |
  | `keySetRemoveAll(set)` | `flushCache:342,353`（clear-on-success） | `cache.asMap().keySet().removeAll(set)` |
  | `clear()` | （当前无直接调用） | `cache.invalidateAll()` |

- [ ] **保留 `flushCache` 的 clear-on-success 行为**：`flushCache`（`:336-359`）快照 cache → `putAll` 到 state → `keySetRemoveAll`（清空 cache）→ 异常时 `putAll` 回填。替换后必须保持：`cache.asMap()` 快照 → `entries.putAll` → `cache.asMap().keySet().removeAll` → 异常时 `cache.asMap().putAll` 回填。
- [ ] 删除 `LruCache.java`。
- [ ] 重命名 `TestSharedBufferLruCache.java` → `TestSharedBufferCache.java`，保留 4 个测试的断言语义（驱逐行为不变——`SharedBuffer.getEventsBufferCacheSize` 经 `cache.size()` 仍然有效）。适配类名引用。
- [ ] `cep-design.md` 记录缓存选型（Guava `Cache`，理由：已有依赖 + 内建原子 LRU + 统计 + removal listener；拒绝 LruCache 因双结构竞态；Caffeine 不拒绝但不选因与 Flink CEP 一致 + Guava 已有）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `nop-stream-cep` 编译通过，全仓库 `grep -r "LruCache" nop-stream/nop-stream-cep/src/`（**含 src/test**）零命中。
- [ ] `LruCache.java` 已删除（文件不存在）。
- [ ] `SharedBuffer` 中 `eventsBufferCache`/`entryCache` 类型为 `com.google.common.cache.Cache`（编译期验证）。
- [ ] **缓存 API 全部适配**：`getIfPresent`（非 `get(key,callable)`）、`put`、`invalidate`、`asMap().containsKey/isEmpty/putAll/forEach/keySet().removeIf/keySet().removeAll` 均正确使用（有编译验证 + 测试验证驱逐行为不变）。
- [ ] **flushCache clear-on-success 保留**：`TestSharedBufferFlushCache`（现有测试）仍通过——验证 flush 后 cache 被清空、state 含数据、rollback 恢复 cache。
- [ ] **RemovalListener 仅记录 SIZE 驱逐**：`RemovalCause.wasEvicted()` 为 true 时 log debug，手动 `invalidate`/`clear` 不记录（有测试验证）。
- [ ] **无静默跳过**：`getIfPresent` 无 checked exception（不需要 `ExecutionException` 处理）；`flushCache` 异常路径回填而非吞异常。
- [ ] 新增/迁移功能均有对应测试（Rule #25）：`testGuavaCacheEvictsAtMaximumSize`（迁移自 `testCacheEvictsOldestEntryUnderCapacityPressure`）、`testOldestEntryEvictedFirst`（迁移）、`testNoCacheEvictionUnderCapacity`（迁移）、`testEntryCacheEviction`（迁移）、`testRemovalListenerOnlyLogsEvictions`（新增）、`testWriteThroughCacheAndStateConsistency`（新增）。
- [ ] `cep-design.md` 已记录缓存选型（最终设计状态）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - Cache 统计周期性接线

Status: planned
Targets: `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/nfa/sharedbuffer/SharedBuffer.java`（`releaseCacheStatisticsTimer` + 新增 `logCacheStatistics`）; `nop-stream/nop-stream-cep/src/main/java/io/nop/stream/cep/operator/CepOperator.java`（新增 `cacheStatsTimerFuture` 字段 + `onCacheStatisticsTimer` 回调 + `open` 注册 + `close` cancel）; `nop-stream/nop-stream-cep/src/test/`; `docs-for-ai/04-reference/source-anchors.md`（新增 SharedBuffer Guava cache 锚点）

- Item Types: `Fix`、`Proof`

> **关键设计约束（live code 实测）**：
> - `cepTimerService`（`CepOperator.java:164`，类型 `TimerService`）只有 `currentProcessingTime()` 方法——**无 `registerProcessingTimeTimer`**。
> - `timerService`（`InternalTimerService<VoidNamespace>`，`:268`）的 `registerProcessingTimeTimer(VoidNamespace.INSTANCE, ts)` 会路由到 `onProcessingTime(long)`（`:487-545`）——这是 **CEP 事件处理回调**（drain elementQueue / advanceTime / processEvent / updateNFA）。**绝不能复用此路径**做 cache 统计，否则会触发非预期的 CEP 事件处理。
> - `InternalTimerService.deleteProcessingTimeTimer`（`:278`）为 **no-op**——不能用于取消 timer。
> - 正确路径：使用 `getProcessingTimeService().registerTimer(timestamp, callback)`（`ProcessingTimeService` SPI，`AbstractStreamOperator.java:135`），返回 `ScheduledFuture<?>`，存储后在 `close()` 中 `cancel(false)`。

- [ ] 新增 `SharedBuffer.logCacheStatistics()`：从 `eventsBufferCache.stats()` / `entryCache.stats()` 读取 `hitCount`/`missCount`/`evictionCount`/`size`，输出 INFO 日志。
- [ ] `CepOperator` 新增字段 `private transient ScheduledFuture<?> cacheStatsTimerFuture;`。
- [ ] `CepOperator.open()`：注册周期性 cache 统计 timer。使用 `getProcessingTimeService().registerTimer(timestamp, this::onCacheStatisticsTimer)`（**非** `timerService.registerProcessingTimeTimer`，**非** `cepTimerService`）。间隔 = `CEP_CACHE_STATISTICS_INTERVAL`（经 `NopCepConfigs` 读取）。存储返回的 `ScheduledFuture<?>` 到 `cacheStatsTimerFuture`。
- [ ] 新增 `CepOperator.onCacheStatisticsTimer(long timestamp)`：**独立回调**，与 `onProcessingTime(long)`（CEP 事件处理）完全分离。调用 `partialMatches.logCacheStatistics()` + re-arm：`cacheStatsTimerFuture = getProcessingTimeService().registerTimer(timestamp + intervalMs, this::onCacheStatisticsTimer)`（anchored to fire time，非 current time，避免 drift）。
- [ ] `releaseCacheStatisticsTimer()` 修改：**不再是空方法体**——`if (cacheStatsTimerFuture != null) cacheStatsTimerFuture.cancel(false); cacheStatsTimerFuture = null;`。方法名语义 = "release/cancel timer resource"。
- [ ] `CepOperator.close()`：`releaseCacheStatisticsTimer()` 保持调用（cancel periodic timer，close-time 清理）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `releaseCacheStatisticsTimer()` 不再是空方法体——`cacheStatsTimerFuture.cancel(false)` + null out（有测试验证 future 被 cancel）。
- [ ] `logCacheStatistics()` 产出包含 hit/miss/eviction/size 的日志（有测试验证日志产出——`LogCaptor` 或等效断言）。
- [ ] **周期性调度接线**：`CepOperator.open()` 经 `getProcessingTimeService().registerTimer(ts, this::onCacheStatisticsTimer)` 注册周期统计 timer（**非** `timerService.registerProcessingTimeTimer`，**非** `cepTimerService`——这两者路由到 CEP 事件处理回调，会破坏行为）。`onCacheStatisticsTimer` 是**独立回调**，与 `onProcessingTime`（CEP 事件处理）完全分离（有测试验证 `onCacheStatisticsTimer` 被调用且 `onProcessingTime` **未**被统计 timer 触发——`testCacheStatisticsUsesDedicatedCallbackNotCepProcessing`）。
- [ ] **re-arm**：`onCacheStatisticsTimer` 调用 `logCacheStatistics()` + 重新 `registerTimer(ts + interval, this::onCacheStatisticsTimer)`（anchored 到 fire time），更新 `cacheStatsTimerFuture`（`testCacheStatisticsTimerReArmsOnFire`）。
- [ ] **端到端验证**：CEP pattern matching E2E 测试全部通过（`./mvnw test -pl nop-stream/nop-stream-cep -am`），pattern matching 结果与替换前一致（无行为回归——`testSharedBufferPatternMatchingRegression`）。
- [ ] **接线验证**：统计在运行时被周期性触发（非仅 close-time 单次——有测试验证 `logCacheStatistics` 被调用 > 0 次，证明 timer 在运行期间触发）。
- [ ] **无静默跳过**：`logCacheStatistics` 产出真实统计（非 placeholder null/0——有测试验证 `recordStats()` 启用后 hit/miss 反映实际访问）。
- [ ] 新增功能均有对应测试（Rule #25）：`testLogCacheStatisticsOutputsHitMissEvictionSize`、`testCacheStatisticsRegisteredViaProcessingTimeService`、`testCacheStatisticsUsesDedicatedCallbackNotCepProcessing`、`testCacheStatisticsTimerReArmsOnFire`、`testReleaseCacheStatisticsTimerCancelsFuture`、`testSharedBufferPatternMatchingRegression`。**测试基建**：CepOperator timer 测试需 fake/mock `ProcessingTimeService`（accept `registerTimer` registration + 可推进时间触发 callback + 返回可控 `ScheduledFuture`）；若无现成 mock，在 plan 执行时构建 minimal fake in test module（`TestProcessingTimeService`），或降级为 integration-level 验证（手动推进 + 断言日志）。
- [ ] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] `LruCache` 已删除，`SharedBuffer` 使用 Guava `Cache`，双结构竞态消除。
- [ ] Cache 统计**周期性**接线（非仅 close-time——timer 注册 + re-arm 在运行时触发）。
- [ ] CEP E2E 无回归（pattern matching 结果不变）。
- [ ] G65 gap 标记 ✅（`08-gap-analysis.md:138` 行末）。
- [ ] 受影响 owner docs（`cep-design.md`（Phase 1 Item）、`source-anchors.md`（Phase 2 Targets）新增 SharedBuffer Guava cache 锚点）已同步到 live baseline。
- [ ] 不存在被静默降级到 deferred 的 in-scope live defect。
- [ ] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据。
- [ ] **Anti-Hollow Check**：`SharedBuffer` 在运行时被 `CepOperator` 使用且 Guava Cache 驱逐/统计生效（非仅类型替换）；统计周期性触发（非仅 close-time）。
- [ ] `./mvnw compile`
- [ ] `./mvnw test -pl nop-stream/nop-stream-cep -am`
- [ ] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

（执行中如有裁定填入；不得把 in-scope live defect 放入此处。）

## Non-Blocking Follow-ups

- Weighted cache size（按内存字节而非 entry 数限流）——optimization candidate。
- Caffeine 迁移（如未来需要 async cache refresh）——out-of-scope improvement。
- Cache 统计经平台 metric 体系暴露（如 `IMeter`）——optimization candidate。

## Closure

Status Note: （关闭时填写）
Completed: YYYY-MM-DD
