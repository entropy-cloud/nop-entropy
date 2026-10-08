# 2338 WI11 维表本地物化 + 后台有界并发刷新（lookup 缓存分层）

> Plan Status: completed
> Last Reviewed: 2026-10-08
> Source: `ai-dev/backlog/nop-stream-multi-engine-comparison-roadmap.md` WI11（M2）+ 裁定 §三 R5（复用既有 lookup 路径不新增异步算子；per-record 同步，异步性收敛在物化刷新层）
> Related: sql roadmap WI14 承载 plan = `ai-dev/plans/nop-stream-sql/18-wi14-dim-lookup-verification.md`（ITableLookup 桥接基线）；裁定 P2 #18（维表本地物化折入本 WI）

## 形态裁定（R5 引用，roadmap Deliverable 三元组映射）

- **异步模式** → 后台有界并发刷新（扫描与执行分离：单线程调度器 fixed-delay 扫描到期条目，刷新 job 派发至大小 = refreshMaxConcurrency 的 worker 池执行——delegate 调用并发度真实 >1 且以池大小为硬上界）。
- **刷新策略** → TTL 过期判 miss（访问时）+ 刷新间隔到期后台重查 + maxEntries 最旧淘汰 + **刷新失败语义：保留旧值 + WARN + 下轮重试（TTL 到期回落同步重查为自愈出口）**——移除条目会把 DB 抖动转化为同步查库风暴，拒绝。
- **形态裁定（Feature + Decision 的 Decision 项）** → 引用裁定文档 §三 R5 全文，不重开（vision「异步算子」Non-Goal 零解除）。

## Purpose

在既有 `ITableLookup` 桥接上交付 delta：本地物化缓存层 + 后台有界并发刷新 + TTL/失效策略，消除「每记录一条真实 DB 查询」。per-record 处理保持同步（R5），异步性收敛在刷新层的函数内部后台资源；不新增异步算子、不改算子线程模型。

## Current Baseline（2026-10-08 研究核实）

- `ITableLookup`（core connector/lookup，唯一文件）：`Object lookup(Object key)` 同步 per-record，实现须 Serializable；core 零 dao 依赖。**接口层零缓存**——每次调用即真查。
- 承载形态全在 runtime 测试态：JdbcTemplate 适配器（transient template + subtask 侧重解析）、DimLookupEnrichFunction（keyed ValueState 缓存先例：miss 委派 + update）、DSL 全链 E2E（.stream.xml + IoC beans）、checkpoint 判别测试（代际戳 stub）。
- 后台设施先例：`PollingJdbcMessageService`（ScheduledExecutor + scheduleWithFixedDelay + 优雅关闭）为周期+IO+关闭的最贴近先例；`BufferPool`（fair Semaphore）为有界并发先例；ProcessingTimeServiceDriver 只投 mail 不执行 IO，不适用刷新。
- TTL 设施参照：keyed-state `StateTtlConfig`（语义参照，不直接复用——缓存层非 keyed state）。
- 无表版本机制；表标识可用 adapter 持有的 table 字符串/DSL bean id。

## Goals

- **core 新类 `CachingTableLookup`**（`io.nop.stream.core.connector.lookup`，实现 ITableLookup + AutoCloseable，Serializable）：
  - 构造包装既有 `ITableLookup delegate` + 配置（ttlMs、refreshIntervalMs、refreshMaxConcurrency、maxEntries，全部有下界校验 typed 拒绝；refreshMaxConcurrency ≥1）。
  - **per-record 同步路径**：命中未过期条目直接返回（零 DB IO）；miss/过期 = 委派 delegate.lookup（同步，R5 语义）并装载缓存。null 结果同样缓存（防穿透）。
  - **后台刷新（审查 F-2 执行模型钉定）**：扫描与执行分离——懒启动单线程 daemon 调度器（scheduleWithFixedDelay 不重叠扫描）+ 大小 = refreshMaxConcurrency 的 worker 池承接刷新 job（delegate 调用并发度真实 >1，池大小即硬上界）。**刷新失败语义（审查 F-4）**：保留旧值 + WARN + 条目 age 增长自然下轮重试；TTL 到期回落同步重查（自愈出口，拒绝移除条目——防 DB 抖动转化为同步查库风暴）。
  - **失效策略（审查 F-8 语义钉定）**：TTL（age > ttlMs 访问时判 miss）+ maxEntries 最旧加载条目淘汰（**刷新续期 loadTime**，语义写进 docs）；可观测 victim = 顺序加载 maxEntries+1 key → 最早加载者被逐出、下次 lookup 重新委派。
  - **序列化边界（审查 F-7 单触发点）**：cache/pool/scheduler 全 transient——反序列化后由**同步 lookup 首次调用**懒重建全部资源（含调度器启动；线程安全防御）。null 防穿透：条目持 sentinel 或存在性判断（实现自由），null 与正条目同 TTL 语义。
  - **close 契约（审查 F-5 诚实落点）**：实现 AutoCloseable + **幂等 close**（重复调用安全，进测试面）；close 后 lookup = typed 拒绝（ERR_STREAM_INVALID_STATE，对齐 No-Silent-No-Op——「直查回落」会在资源关闭后继续 DB IO 掩盖生命周期 bug）。**生产 close 调用落点 = 宿主函数 close() override（WI17 接线）或 bean destroy-method，本 WI 内唯一调用方是测试**（process 路径 AutoCloseable 不自动传播——live 核实 FunctionUtils.closeFunction 仅对 RichFunction 生效）。
- 测试（core，stub lookup，无需 DB；**时间控制一律 stub 内 latch + 超时轮询，禁止裸 sleep 定成败**——审查 F-3）：① TTL 过期判 miss（短 ttl + 轮询）；② 后台刷新更新值（版本化 stub + latch 轮询）；③ **有界并发双断言（F-3）：permits=2 时观测 inFlight ≥ 2（重叠真实发生）且全程 ≤ 2**（串行实现必红）；④ close 幂等 + close 后 typed 拒绝 + 池终止；⑤ maxEntries 淘汰 victim（最早加载者）；⑥ null 防穿透（重复 null key 只委派一次 + TTL 过期重委派）；⑦ 负配置 typed 拒绝；⑧ 序列化往返后懒重建可用；⑨ 刷新失败保留旧值（stub 抛异常 → 旧值仍读 → 恢复后下轮成功）。
- docs：nop-stream.md（或 user-guide lookup 节）补「维表本地物化缓存层」语义（配置面/失效策略/序列化边界/close 契约）。

## Non-Goals

- per-record 异步 lookup 算子（vision Non-Goal 维持，R5）；keyed-state 缓存迁移（现有测试态先例保留）；失效信号源（CDC tableId 反向失效——本期 TTL/容量即可，CDC 驱动失效留后续）；nop-stream-sql/flow 接线（WI17）；debezium/kafka 连接器改动。

## Scope

### In Scope

- core CachingTableLookup + 配置校验 + 后台刷新 + 测试面。
- docs 语义节。

### Out Of Scope

- 异步算子；keyed-state 迁移；CDC 驱动失效；SQL/flow 接线。

## Execution Plan

### Phase 1 - 缓存层与测试

Status: completed
Targets: core connector/lookup、测试

- Item Types: `Feature + Proof`

- [x] CachingTableLookup（同步路径 + 扫描/执行分离的后台刷新 + TTL/容量失效 + 池上界并发 + 序列化边界 + 幂等 close + 刷新失败保留旧值）
- [x] 测试九面（TTL/后台刷新/**并发双断言**/close 幂等+typed/淘汰 victim/null 防穿透/配置校验/序列化/刷新失败）——9/9 全绿

Exit Criteria:

- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全绿（EXIT=0）
- [x] **接线验证**：后台刷新真实变更查询结果（版本化 stub 轮询）+ 有界并发双断言（holdKeys 栅栏证明 inFlight≥2 重叠 + ≤池上界——串行实现必红）
- [x] **无静默跳过**：负配置 typed 拒绝（4 断言）；close 后 lookup typed 拒绝（ERR_STREAM_INVALID_STATE——钉定「拒绝」而非直查，审查 F-5）
- [x] No owner-doc update required（docs 集中 Phase 2）
- [x] `ai-dev/logs/` 对应日期条目已更新（`ai-dev/logs/2026/10-08.md` plan 2338 条目）

### Phase 2 - 收口

Status: completed
Targets: docs、plan/roadmap

- Item Types: `Proof`

- [x] docs：`nop-stream-connectors.md` 新增「维表本地物化缓存层」节（审查 F-6 钉定目标文件；含配置面/失效策略/刷新失败语义/序列化边界/**delegate 线程安全契约——刷新与同步路径并发调用 delegate**/close 落点与幂等）
- [x] 独立子 agent closure audit；roadmap WI11 勾选；git commit

Exit Criteria:

- [x] owner docs 已更新
- [x] 独立 closure audit 通过且证据写入本 plan Closure 节
- [x] roadmap WI11 已勾选、提交完成

## Closure Gates

- [x] 缓存层落地且 per-record 同步语义不变（R5）
- [x] 后台刷新真实生效且有界并发可证
- [x] `./mvnw test -pl nop-stream/nop-stream-core -am` 全绿（EXIT=0）
- [x] **Anti-Hollow Check**：刷新/淘汰路径被测试真实消费（非仅配置存在）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-stream/nop-stream-core --severity high` 退出码 0
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

### CDC tableId 驱动的反向失效

- Classification: `watch-only residual`
- Why Not Blocking Closure: TTL/容量失效已满足首版物化诉求；CDC 变更流反向失效维表缓存需跨流关联（表标识匹配 + 失效广播），无真实场景前不建
- Successor Required: `no`

## Non-Blocking Follow-ups

- （closure 时填写）

## Closure

Status Note: WI11 维表本地物化缓存层（CachingTableLookup）落地：per-record 同步语义不变（R5 合规）、扫描/执行分离后台刷新（池上界真实并发双断言）、刷新失败保留旧值 + TTL 自愈、幂等 close + close 后 typed 拒绝；独立 closure audit 0 Blocker/0 Major，Minor M-1（勾选滞后）已修复、M-2（null 过期重委派由①组合覆盖）加注、M-3（import 分组）提交前调整。
Completed: 2026-10-08

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，task agent_88a46cbe-7d23-41c1-981f-3bb69ad2440f），全程只读
- Evidence:
  - 实现 vs Goals 六条 PASS（构造校验/per-record 含 null 防穿透/扫描执行分离+失败语义/失效策略+刷新续期/transient 懒重建单触发点/close 幂等+typed，逐条 file:line）
  - 测试真实覆盖 PASS（9/9 现场重跑 EXIT=0；并发双断言 holdKeys 栅栏 inFlight≥2 + ≤上界；失败用例 failing 模式无恢复竞态；序列化往返 loadCount==1）
  - R5 合规 PASS（lookup() 全文零异步泄漏；daemon 线程函数内部资源；无新增算子）
  - Anti-Hollow PASS（刷新②③/失败⑨/淘汰⑤/防穿透⑥全被消费；close 真实 shutdown 两池；scan-hollow exit 0）
  - owner doc PASS（connectors.md 八要素 + doc-links --strict 0）
  - Minor 处置：M-1 勾选滞后本次收口修复；M-2 plan 勾选加注「null 过期重委派由①组合覆盖」；M-3 import 分组提交前调整
  - 提交圈定：仅 WI11 文件（core 新类+测试 + connectors.md + plan + roadmap 勾选 + 10-08 日志），sibling survey 不混入

Follow-up:

- no remaining plan-owned work
