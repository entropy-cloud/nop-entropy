# 2573 未推送提交独立评审确认缺陷统一修复

> Plan Status: completed
> Last Reviewed: 2026-11-14
> Source: `ai-dev/analysis/2026-11/2026-11-14-unpushed-commits-deep-independent-review.md`（对 origin/master..HEAD 853 提交的独立深度评审）
> Related: plan 2549/2551/2566（被评审变更的原始计划群，仅作背景，不回写）

## Purpose

把 2026-11-14 独立评审确认的 4 个 live defect（A1/A3/A4/A5）与 1 个待裁定的默认值翻转（A2）收敛到修复完成或显式裁定状态；B/C 类行为变更不回退代码，只补部署侧文档登记。

## Current Baseline

- A1：`JWKPublicKeyLocator.getPublicKey` 的 `lastRequestTime` 仅在拉取成功后推进（:120-122）；`minTimeBetweenJwksRequests` 缺省 60s。JWKS 失败期间对未知 `kid` 的每次请求都会发起新拉取（无时间节流，仅单飞合并）。
- A2：`CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT` 缺省已从 `null` 翻转为 `Duration.ofMinutes(5)`；`ResourceLoadingCache`（expireAfterWrite）与 `ResourceCacheEntry.shouldReloadObject`（maxAge 强制重载）双通道消费该缺省，全部未显式配置的组件缓存每 5 分钟冷重建。
- A3：`GraphQLSubscriptionManager.pushToClient` 在 `session == null` 分支调用 `sendPumps.remove(session)`（CHM.remove(null) → NPE）。
- A4：`PartitionResolver` 负缓存写点只置 `negativeCached=true; lastResolveTime=now`，不清 `cachedPartitions`；`negativeTtlMs`（缺省 5000）< `CACHE_TTL_MS`（10000）时，负缓存过期后首查回吐陈旧正缓存。
- A5：`NopAuthLedgerCleaner.purgeExpiredSessions` pick（limit 1000）与 delete（`sessionId IN`）间无谓词复验，30 天不活跃会话在 pick→delete 窗口内被访问仍会被删。
- 上述均为 origin/master..HEAD 未推送变更中引入/暴露的面；相关模块现有测试全绿（各批次提交声明）。

## Goals

- A1：失败路径恢复有界节流——JWKS 拉取失败后进入短退避窗（不占用成功路径的 60s 速率窗，但封住逐请求放大）。
- A2：形成显式裁定并落地——缺省值二选一（恢复 null / 保留 5min + 文档与启动声明），消除"静默默认性能翻转"。
- A3：null-session 分支不再 NPE（判空后仅 unregisterSubscription）。
- A4：负缓存写点同步置空正缓存；负缓存过期后首查必走真实 resolve。
- A5：session 族删除改带 pick 同谓词（delete 复用 lastAccessTime/logoutTime 过滤），封住 TOCTOU。
- B 类：release-notes 风格的升级行为变更登记（B1 池上界、B2 weight=0 语义、B3 等待上界族、B4 事务/绑定语义、B5 fetchSize WARN 噪音）落在 owner doc。

## Non-Goals

- 不回退 853 提交中的任何结构性加固（绝缘层/退役队列/事务状态机/迁移等）。
- 不处理 C 类 watch-only 项（CachedPoolRejectHandler 错误码载体、static offender map、BatchProcessorConsumer 上界竞态等——见 analysis C 节，非本计划 scope）。
- 不为 B 类行为变更改代码（除 A2 缺省值裁定项外）。

## Scope

### In Scope

- `nop-auth/nop-auth-sso`（A1）
- `nop-kernel/nop-core`（A2 缺省值 + 可能的 owner doc）
- `nop-service-framework/nop-graphql-core`（A3）
- `nop-cluster/nop-cluster-core`（A4）
- `nop-auth/nop-auth-service`（A5）
- `docs-for-ai/` 对应 owner docs（B 类登记 + A2 裁定落档）

### Out Of Scope

- nop-ai / nop-metadata / nop-datav / nop-code（用户指令排除）。
- B1-B5 的代码面调整（仅文档登记；若部署侧后续要求调参，走 successor）。

## Execution Plan

### Phase 1 - A 类缺陷修复（A1/A3/A4/A5）

Status: completed
Targets: `nop-auth/nop-auth-sso/.../JwkPublicKeyLocator 路径见下`；准确模块路径 = `nop-auth/nop-auth-sso`、`nop-service-framework/nop-graphql/nop-graphql-core`、`nop-cluster/nop-cluster-core`、`nop-auth/nop-auth-service`

- Item Types: `Fix`

- [x] A1：`JWKPublicKeyLocator` 增加失败退避——失败终态后记录 `lastFailureTime`，后续拉取需 `now > lastFailureTime + failureBackoffMillis`（取 `minTimeBetweenRequests * 1000 / 10`，即缺省 6s，下限 1s；成功后清零）。保持成功路径 60s 速率窗与单飞语义不变。
- [x] A1 回归钉：`TestJwkLocatorSingleflight.pin_b`（改写旧「立即可重试」钉为退避窗抑制钉）+ `pin_d`（ManualClock 驱动：窗内抑制 fetch==1 / 窗外可重试 fetch==2 / 成功后 60s 速率窗抑制 / 越窗后 fetch==3）。变异探针（去掉退避门）pin_b+pin_d 双红（expected 1 but was 2），恢复后 4/4 绿。
- [x] A3：`pushToClient` null-session 分支改为 `if (session != null) sendPumps.remove(session);` 后统一 `unregisterSubscription`。
- [x] A3 回归钉：`TestPushToClientNullSession_A3`（不抛异常 + 注销 + 注销后重复推送幂等 no-op）。变异探针（恢复 remove(null)）NPE 红，恢复后绿。
- [x] A4：负缓存两个写点（空列表/未命中）同步内联 `cachedPartitions = null`；正缓存写点保持现状。
- [x] A4 回归钉：`TestPartitionResolver.testNegativeResolutionClearsStalePositiveCache_A4`（ManualClock：正写→越正 TTL→负写→越负 TTL→重解析异常→恢复后必真实查询 getCalls==4，不得回吐 T0 陈旧正缓存）+ `testNegativeExpiryFallsThroughToRealResolve_A4`（既有语义零回退）。变异探针（去掉清正缓存）红（expected 4 but was 3），恢复后 16/16 绿。
- [x] A5：`purgeExpiredSessions` 的删除改复用 pick 谓词（`FilterBeans.in` + `expiredSessionPredicate(cutoffMillis)` 同判据；删除腿提取为包内可见 `deleteSessionRows` 供确定性直驱）。
- [x] A5 回归钉：`TestAuthLedgerRetention_MA_01.testMA_01_sessionDeleteRevalidatesPredicateAfterPick_A5`（直驱删除腿：pick 后刷新的行存活、仍过期的行删除 deleted==1）。变异探针（去掉 delete 谓词）红（expected 1 but was 2），恢复后 8/8 绿。
- [x] **X-1（收口审计追加，Phase 1 范围扩展）**：`JWKPublicKeyLocator.getPublicKey` 的 `!allowed` 分支不再消费已完成的旧 future——修复前该分支仍把 fetch 指向上次成功拉取，等待段以其成功结果把 `lastRequestTime` 刷新到本次请求时刻，未知 kid 请求到达间隔 < 60s 时成功速率窗被无限滑动，JWKS 永不重拉（IdP 密钥轮换后新 kid 持续到达 = 认证自锁死；`lookupCachedKey` TTL 同源滑动）。修复：锁内三分支重排——有在飞 → 加入在飞（发起时已过速率门）；窗内且无在飞 → `fetch=null` 不消费旧 future、不滑动窗口；过窗 → 新拉取。
- [x] X-1 回归钉：`TestJwkLocatorSingleflight.pin_e_successWindowNotSlidByStaleFuture_KeyRotationRefetch_X1`（ManualClock：T0 拉取成功 → T0+30s 窗内调用 fetch==1 不滑动 → T0+61.001s 距上次真实拉取 >60s 必重拉 fetch==2）。变异探针（恢复消费旧 future）红（expected 2 but was 1），恢复后 5/5 绿。

Exit Criteria:

- [x] 四个缺陷各有先红后绿的 focused 回归测试（A1 fetch 计数 / A3 无 NPE / A4 getCalls==4 真实 resolve / A5 deleted==1 存活断言；四项均跑变异探针确认回退必红）。
- [x] X-1 修复含先红后绿钉（pin_e + 变异探针 expected 2 but was 1）。
- [x] `./mvnw test` 分模块通过：cluster-core 82/82、nop-core 608/608（skip 2 既有）、auth-sso 25/25（skip 1 既有；X-1 后 5 测试类内 5/5）、graphql-core 206/206、auth-service 483/483（skip 3 既有）。
- [x] **接线验证**：A1 钉经 `getPublicKey` 公共入口驱动（fetch 计数在 FakeHttpClient.fetch 内）；A5 钉经包内删除腿 `deleteSessionRows` 直驱（pick 腿谓词与删除腿共用 `expiredSessionPredicate` 单源，谓词等价性由单源保证）。
- [x] **无静默跳过**：A1 退避窗命中时走既有 stale-grace 返回路径（未新增静默分支，仅收紧 allowed 双门）。
- [x] owner-doc：A1 失败退避 + X-1 速率窗不滑动语义已登记 `docs-for-ai/03-modules/nop-auth.md`（本地只读缓存约定小节，JWK 拉取节流双门条目）；A3/A4/A5 为缺陷修复恢复正确语义，`No owner-doc update required`（无文档曾描述错误行为）。
- [x] `ai-dev/logs/` 2026-11-14 条目已更新。

### Phase 2 - A2 默认值裁定与落地

Status: completed
Targets: `nop-kernel/nop-core/.../CoreConfigs.java`, `docs-for-ai/`（组件缓存 owner doc）

- Item Types: `Decision` + `Fix`（按裁定结果）

- [x] Decision（选项甲）：恢复缺省 `null`（不启用；5min 漏检自愈 opt-in）。裁定理由：5min 缺省使全部组件缓存默认每 5 分钟周期性冷重建（expireAfterWrite 逐出 + entry maxAge 双通道，周期性重解析/重编译风暴），属默认性能翻转且升级方无提示；漏检自愈价值经判据双窗口修复（plan 2500 裁定①）后仅覆盖残余漏检面，按部署风险画像显式启用优于默认常开。裁定已落权威 owner `ai-dev/design/concurrency/cache-invalidation-single-point.md`（§一.3/§3.3 终裁/§3.3 source-compat/§3.4 门契约 3/§3.4 使用方声明，5 处原地修订并注明 A2/plan 2573 修订缘由）；否决选项乙：保留 5min 即保留默认周期性重载风暴，仅去掉驱逐通道不消除 maxAge 侧周期重载。
- [x] Fix：`CoreConfigs.CFG_COMPONENT_RESOURCE_CACHE_TIMEOUT` 缺省 `Duration.ofMinutes(5)` → `null`；`@Description` 同步（null=不启用 / 正值=opt-in 启用双通道 / 负值=显式关闭档）。命名键 `. {name}.timeout` 优先序不变。
- [x] Proof：`TestResourceCacheTimeoutDefault_A2`（清 override 后断言生效值即缺省 null，防再次静默翻转）；判据/gate 既有钉族（`TestResourceCacheEntryGate` 6/6、`TestResourceCacheEntryMonotonicClock_C7_004` 2/2、`TestTenantAwareCacheDelegation`）零改码全绿（它们显式设置键值，不依赖缺省）。
- [x] owner doc 同步：`cache-invalidation-single-point.md`（权威，5 处）+ `docs-for-ai/04-reference/source-anchors.md`（CACHE-001 行）+ `docs-for-ai/03-modules/nop-rule.md`（配置键行）。

Exit Criteria:

- [x] 裁定已记录（选项甲，含否决选项乙的理由），缺省值与裁定一致（null）。
- [x] focused 测试钉住缺省行为（`TestResourceCacheTimeoutDefault_A2` 断言缺省 null；nop-core 全量 608/608 绿——既有 gate/TTL 钉族显式设值不受影响）。
- [x] `./mvnw test -pl nop-kernel/nop-core` 通过（608/608，BUILD SUCCESS）。
- [x] owner doc 已更新（设计文档 5 处 + source-anchors CACHE-001 + nop-rule 配置键行；live baseline 变更全部落档）。
- [x] `ai-dev/logs/` 2026-11-14 条目已更新。

### Phase 3 - B 类升级行为变更登记（纯文档）

Status: completed
Targets: `docs-for-ai/`（B1 执行器/线程池、B2 集群负载均衡、B3 并发/限流、B4 事务、B5 batch jdbc reader 的 owner 章节）

- Item Types: `Follow-up`（文档登记；代码零改动）

- [x] B1：核实**既有登记已覆盖**——`docs-for-ai/03-modules/nop-batch.md`（:14 并发处理 / :211 concurrency 参数表 / :345 exp 双护栏）+ `docs-for-ai/02-core-guides/batch-dsl.md`（:36）：cached 池 200 上界、达界熔断 `nop.err.commons.concurrent.cached-pool-saturated`、`nop.batch.max-concurrency-cap=200` 装配期 fail-fast 三处联动均已可 grep。
- [x] B2：语义翻转已在 `docs-for-ai/04-reference/source-anchors.md` CLUSTER-CC-002（R25-1 下限语义）；本批补录 `docs-for-ai/03-modules/nop-network.md`「升级行为变更登记」——weight=0 不再摘流 + `defaultWeight` 属性移除（API 破坏面，仓内零调用方）。
- [x] B3：throttle 60s / rateLimit 5min 已在 `docs-for-ai/02-core-guides/ioc-and-config.md`（:335 阻塞等待必须有界）；本批补录 sentinel `nop.cluster.sentinel.task-timeout-ms`（缺省 60000，≤0=关闭）到 `nop-network.md`（合法慢调用 >60s 部署必须上调）。
- [x] B4：本批新增 `docs-for-ai/03-runbooks/transaction-boundaries.md`「事务对象状态机语义」节（rollbackOnly-commit 自动回滚+WARN / 二次 commit no-op+WARN / doCommit 失败 afterCompletion(UNKNOWN)+尽力 ROLLED_BACK / 迟到 addSubTransaction 拒绝 / bindCancel 仅取消终结收紧）+「锁租约/超时对象的时间语义」节（TimeOut/IResourceLockState 单调纳秒语义）；负值 skip/retry fail-fast 已在 `nop-batch.md`（:338/:356）既有登记。
- [x] B5：已有登记——`docs-for-ai/03-modules/nop-batch.md`（:273 jdbc-reader fetchSize 语义契约：一次性 WARN `nop.err.batch.jdbc-fetch-size-unset` + 显式配置建议）。

Exit Criteria:

- [x] 五组行为变更在对应 owner doc 可 grep 到（键名/错误码原文：cached-pool.maxPoolSize / max-concurrency-cap / task-timeout-ms / rate-limit-acquire-timeout-ms / throttle-acquire-timeout-ms / txn-commit-on-rollback-only / jdbc-fetch-size-unset / defaultWeight 等）。
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（No errors found）。
- [x] No code change in this Phase（显式声明：仅 docs-for-ai 3 个文件编辑）。
- [x] `ai-dev/logs/` 2026-11-14 条目已更新。

## Closure Gates

- [x] A1/A3/A4/A5 四个确认缺陷 + 收口审计追加的 X-1 已修复且各有 focused 回归测试（五项均跑变异探针确认回退必红：A1 expected 1→2 双钉红 / A3 NPE / A4 expected 4→3 / A5 expected 1→2 / X-1 expected 2→1）。
- [x] A2 已形成显式裁定并落地（缺省值 CoreConfigs=null / 双通道 null 短路 / 设计文档+source-anchors+nop-rule 三处一致；收口审计补齐第 4 处 rule-metadata 设计文档残留）。
- [x] B 类五组行为变更已登记进 owner docs（收口审计修正 2 处错误码原文：`nop.err.dao.txn.late-sub-transaction` / `nop.err.cluster.core.no-available-server-instance`）。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（C 类项不在本 plan scope，已在 analysis 登记 watch-only；X-1 由收口审计发现后按 Fix 项纳入 Phase 1 而非降级）。
- [x] `./mvnw compile`（涉及模块，随 test 阶段）通过。
- [x] `./mvnw test`（涉及模块）通过：cluster-core 82/82、nop-core 608/608、auth-sso 25/25、graphql-core 206/206、auth-service 483/483（skip 为既有）。
- [x] 代码规范检查：import 分组/4 空格/命名人工核对通过（仓库无 checkstyle 插件配置——收口审计确认）。
- [x] 独立子 agent closure audit 已完成并写入 Evidence（agent_714e52a2，4 项整改要求全部回填修复并复验）。

## Deferred But Adjudicated

### C 类 watch-only 项（CachedPoolRejectHandler 错误码载体、static offender map、BatchProcessorConsumer 上界竞态、record_result 逐行删、Debezium stop-先于-start 边角、AbstractLeaderElector stop 后 newElection 悬挂窗等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 全部为纯观测面/极端边角/默认关闭路径，无确认生产损失面；已在 analysis C 节逐项登记，复发或升级时按项开 successor。
- Successor Required: no
- Successor Path: —（analysis 为登记面）

## Non-Blocking Follow-ups

- B1 cached 池缺省 200 的部署画像评估（是否上调）——analysis Open Question。
- B4 `DaoBatchStateStore` 裸 UPDATE 与 ORM VERSION 列契约漂移的长期收敛（若后续有第三方并发写 nop_batch_task 行的需求再启）。

## Closure

Status Note: 三个 Phase 全部完成 + 收口审计追加项（X-1 修复、2 处错误码勘误、1 处设计文档残留对账、日志补写）全部回填；五模块测试绿、checklist/链接检查通过，无 in-scope 遗留缺陷。
Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立收口审计子代理 agent_714e52a2-028b-4afb-8e66-9b795f0d8612（fresh session，66 tool uses）
- Evidence:
  - 逐条 Exit Criterion：A1 五要素（lastFailureTime 字段 / failureBackoffMillis 静态方法 / getPublicKey 双门 / sendRequest catch 记账 / 成功清零）+ pin_b/pin_d PASS；A3 分支形态（GraphQLSubscriptionManager null 守卫）+ 钉 PASS；A4 双写点（PartitionResolver 两处 cachedPartitions=null）+ 双钉 PASS；A5 谓词删除腿（NopAuthLedgerCleaner.deleteSessionRows，deleteByQuery=DB 层原子 DELETE）+ 钉 PASS；A2 缺省/描述/测试非空壳（makeDefault 包装链推演）+ 设计文档 5 处 PASS；B 类登记内容 PASS；Anti-Hollow（A1 双门 jshell 实测生效 / A5 接线 purgeExpiredLedgerRows→doStart 周期链 / A2 双通道 ResourceLoadingCache+ResourceCacheEntry null 短路）PASS；无静默跳过 PASS（grep TODO/FIXME 0 命中）。
  - 审计整改 4 项：①X-1 JWK 成功窗滑动自锁（Major 新缺陷）→ 已按 Fix 项纳入 Phase 1 修复（锁内三分支重排 + pin_e + 变异探针先红后绿，TestJwkLocatorSingleflight 5/5 绿）；②2 处错误码原文笔误 → 已修正（transaction-boundaries.md / nop-network.md）；③rule-metadata 设计文档 5min 残留 → 已对账为 null；④日志条目缺失 → 已补写（ai-dev/logs/2026/11-14.md plan 2573 执行条目）。整改后 auth-sso 复跑 25/25 绿。
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（主会话与审计双跑）。
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module <5 模块> --severity high` 全部退出码 0（审计跑）。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（Evidence 回填后复跑）。
  - Deferred 项分类检查：C 类 watch-only 全部附 Why Not Blocking，无 in-scope live defect 降级。

Follow-up:

- B1 cached 池缺省 200 的部署画像评估（analysis Open Question，非本 plan 义务）。
- B4 DaoBatchStateStore 裸 UPDATE 与 ORM VERSION 列契约漂移的长期收敛（已有登记）。
- 其余无 plan-owned 遗留工作。

## Risks And Rollback

- A1 改动面小（单类 + 退避字段）；若退避窗引入误伤（IdP 短暂抖动后恢复慢），可用 `minTimeBetweenRequests/10` 之外的更小值（≥1s）微调，无配置面扩张。
- A2 裁定若选乙（保留 5min + 去驱逐通道），需重验多租户缓存容器（TenantAwareResourceLoadingCache destroyOnRemove 链）不依赖驱逐通道触发销毁回调——Phase 2 exit criteria 的测试钉覆盖。
- A5 谓词删除依赖 `FilterBeans` OR 条件在 `deleteByQuery` 的方言生成（MySQL/PG 均支持布尔条件删除）；若有方言限制则退化为"delete 前按谓词复验二次查询"形态。
