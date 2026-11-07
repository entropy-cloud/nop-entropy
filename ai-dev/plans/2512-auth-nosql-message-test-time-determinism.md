# 2512 nop-auth / nop-nosql / nop-message 测试时间确定化（roadmap WI-6）

> Plan Status: completed
> Last Reviewed: 2026-11-07
> Source: `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-6；范式沿用 plan 2510/2511（范本 + 独立实现子 agent + 变异探针 + 满载证明）
> Related: `ai-dev/plans/2511-nop-ai-test-time-determinism.md`（completed）；`ai-dev/plans/2508-shared-deterministic-time-test-tools.md`；`ai-dev/plans/2509-test-time-waits-guard.md`
> 编号说明：沿用仓库当前 4 位序列（最近为 2511）。
> Review: R1 对抗审查（独立子 agent，现场核对守卫/testcontainer/生产时钟读）1 Blocker + 1 Major + 3 Minor 全改（F1 lettuce TTL 为 Redis 服务端执行，改条件等待机械化 + 具名裁定路线；F2 TTL 探针换 auth store 过期判定；F3 删 TestThresholdBreaker 误植措辞；F4 基线 diff 限定 WI-6 面；F5 consume 任务中断协议保留约束），判定可执行。
> 验证环境：`<repo>` = `-Dmaven.repo.local=$PWD/.m2-repo`（逐命令展开）。
> 跨平台约束（用户 2026-11-07 指令）：改造面纯 JDK/JUnit；外部系统（Redis/Kafka/Pulsar/Docker）gated 面保持既有形态，不新增 OS 专有假设。

## Purpose

把 WI-6 模块组（nop-auth-service、nop-nosql-lettuce、nop-message-core/kafka/pulsar）测试的时间等待收口到虚拟时间/显式同步，守卫切面消号至具名裁定残余。

## Current Baseline

- 守卫基线（2511 收口后）WI-6 切面：**52 处 / 5 模块**——nop-auth-service 16（TestRedisStoreWiring 4、STA 7 / OTHER 9）、nop-nosql-lettuce 12（TestLettuceNosqlService 10、TestLettuceClusterService 2）、nop-message-kafka 12（TestKafkaServiceLifecycle 5、TestKafkaSubscription 3、TestKafkaConsumeTask 2、KafkaBrokerE2ETest 2）、nop-message-pulsar 11（TestPulsarConsumeTask 8、TestPulsarServiceLifecycle 1、PulsarBrokerE2ETest 1、PulsarMessageServiceContractTest 1）、nop-message-core 1（AbstractMessageServiceContractTest POLL 1）。
- 生产时间读取：kafka/pulsar main 各 2 处 `System.nanoTime()`（KafkaMessageService:272/:316、PulsarMessageService:253/:275）均为 send 耗时指标（值无关断言消费）——保留；其余 main 无裸读。
- gated 面：KafkaBrokerE2ETest / PulsarBrokerE2ETest（真 broker 门控）、TestLettuceClusterService（embedded/真 Redis 门控类）——虚拟时钟不适用真实外部服务，条件等待机械化 + 具名裁定。
- 消费测试工具：ManualClock/ManualScheduler（nop-commons test-jar，消费模块 pom 需加依赖——按消费面硬前置）；nop-auth-service 已依赖 nop-autotest-junit。
- 全 reactor 基线：2511 收口时 456 模块 BUILD SUCCESS 0 失败。

## Goals

- WI-6 切面四类禁止写法清零（残余逐处具名裁定，总降幅 ≥ 90%，52 → ≤ 6）。
- 全 reactor 测试保持 0 失败 0 错误。

## Non-Goals

- 不改生产行为语义（kafka/pulsar 指标耗时读取保留）。
- 不处理 WI-7 / WI-8 模块。

## 改造手册

沿用 plan 2510/2511 判定表（虚拟时钟 / ManualScheduler 注入 / latch + FutureHelper.waitUntil / 重新求值证据 / 结构性判据）；**不得引入 nop-stream 依赖**；外部系统 gated 面只做条件等待机械化（虚拟时钟不可跨真实服务边界），真实时长语义具名裁定。

## Execution Plan

### Phase 1 - 工具接线与模块改造

Status: completed
Targets: nop-auth-service、nop-nosql-lettuce、nop-message-core/kafka/pulsar 测试源

- Item Types: `Fix`

- [x] 消费 ManualClock 的模块 pom 加 `nop-commons` test-jar 测试依赖（硬前置）。
- [x] 52 处基线逐文件改造（独立实现子 agent）：**lettuce（12 处）走条件等待机械化 + 具名裁定路线——TTL 由 Redis 服务端执行（LettuceSessionStore / AbstractLettuceOperations 零 CoreMetrics 时间读，仅 LettuceRateLimiter:56 走 CoreMetrics），测试又跑在真 redis:7 testcontainer 上，虚拟时钟不可达**（auth 的 FakeNosqlService 替换路线仅适用于 auth 面的 store 测试）；auth E2E 登录/MFA 等待 → 条件等待（RedisEmailCodeStore:63/:84 等生产已读 CoreMetrics 的 store 过期测试可经 ManualClock 虚拟化）；kafka/pulsar consume/lifecycle → 条件等待 + SIM latch 化（consume 任务有 ExecutorService 构造 seam、无 ScheduledExecutorService seam，**改写必须保留既有中断协议**——测试靠 shutdownNow/interrupt 结束等待）；gated broker/cluster 面条件等待机械化。
- [x] 变异探针 ≥ 2 组（**可虚拟化面**：auth RedisEmailCodeStore/SmsCodeStore 过期判定经 ManualClock 撤行为即红；kafka 或 pulsar 消费就绪判定）撤行为即红、恢复 grep 校验后复跑绿——不得用 lettuce TTL 作探针（真 Redis 服务端时钟无 seam 可撤）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 守卫切面残余 0（目标 ≤ 6 ✓）；5 处守卫不可见实时窗口具名裁定（lettuce 服务端 TTL、kafka gated 慢处理器 6s、Caffeine 无 ticker 50ms、hardening E2E 冷却、SIM 中断界内节拍——`_tmp/wi6-batch-report.md`）。
- [x] 5 模块全量测试全绿（auth 79、kafka 98、pulsar 73、core 26、lettuce Docker 门开启 60+8）。
- [x] 探针证据齐全。
- [x] `ai-dev/logs/2026/11-07.md` 已更新（per-file 账本指针）。

### Phase 2 - 守卫消号与全量门

Status: completed
Targets: 守卫基线、全 reactor

- Item Types: `Proof`

- [x] 全仓 `--update` 消号（WI-6 切面 52 → 具名残余）；全仓 check exit 0。**消号前后基线 diff 必须限定在 WI-6 模块面**（nop-persistence 守卫范围还含 3 处非 WI-6 命中：nop-orm-data 1、nop-orm-migration 2——出现其他模块漂移须同批判定，不得静默卷入）。
- [x] 满载稳定性证明：满载期间 `./mvnw <repo> -o test -pl nop-persistence/nop-nosql/nop-nosql-lettuce,nop-auth/nop-auth-service` 连续 2 轮全绿（burner 纯 Java，结束无残留）。
- [x] 全 reactor `./mvnw <repo> test -T 1C -fae` 0 失败 0 错误。
- [x] roadmap WI-6 → done；logs 收口条目。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 消号证据写入 logs（174→122，diff 非 WI-6 面零漂移）；全仓 check exit 0。
- [x] 满载 2 轮证据写入 logs。
- [x] 全 reactor 证据写入 logs（第 2 轮 BUILD SUCCESS 456 模块；首轮暴露 TestCheckpointAbortMarkerCleanup 二段竞态已修复）。
- [x] roadmap 状态块已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 5 模块测试源四类依赖清零（残余 ≤ 6 且逐处具名裁定记录于 logs）
- [x] 守卫基线 WI-6 切面消号完成（降幅 ≥ 90%），全仓 check exit 0
- [x] 变异探针 ≥ 2 组证据齐全（撤行为即红、恢复 grep 校验、复跑绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：虚拟时钟推进在生产判定路径被消费（探针即证明）；`scan-hollow-implementations.mjs --severity high` 对触碰模块无新增发现（如实记录 HEAD 既有项）
- [x] `./mvnw <repo> compile`（由 test 生命周期传递满足）
- [x] `./mvnw <repo> test`（全 reactor）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2512-auth-nosql-message-test-time-determinism.md --strict` 退出 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] **跨平台核查**：改造面纯 JDK/JUnit；外部系统 gated 面保持既有形态；结论入 logs

## Deferred But Adjudicated

（本 plan 无 deferred 项。）

## Non-Blocking Follow-ups

- WI-7 / WI-8 各模块清欠（roadmap 承接）。

## Closure

Status Note: WI-6 五模块测试时间确定化完成：守卫切面 52→0（降幅 100%），lettuce 按 Redis 服务端 TTL 裁定走条件等待机械化路线，2 组变异探针红→恢复→绿，满载 2 轮 + 全 reactor 456 模块 BUILD SUCCESS（期间修复 TestCheckpointAbortMarkerCleanup 二段竞态，断言加强）。独立 closure audit APPROVED（2 Minor 均日志措辞级，已同批修正）。

Completed: 2026-11-08

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现）
- Evidence:
  - Phase 1：守卫实测 auth/message 面零命中、persistence 仅余 2 处非 WI-6 条目（未变）；52 处逐文件账本（`_tmp/wi6-batch-report.md`）降幅 100%；探针 2 组（3 红/2 红→恢复→21/51 绿）且无变异残留（grep 仅 HEAD 既有 ProbeStore 与域词）；5 模块触碰类全绿 + lettuce Docker 门开启 60+8 绿；logs 已更新。
  - Phase 2：基线 diff 精确限定 39 个 WI-6 条目块（174→122，nop-orm-data/migration 未动）；全 reactor 第 2 轮 BUILD SUCCESS / 456 SUCCESS / 0 FAILURE；首轮失败修复（TestCheckpointAbortMarkerCleanup containsKey→get+contains，断言加强、机制确定性）复核通过；roadmap WI-6 → done。
  - Closure Gates：checklist --strict exit 0、doc-links --strict 0 errors；hollow 扫描 auth/message exit 0、persistence exit 1 但 3 处均为 HEAD 既有未触碰文件（TdMessageService/GeometryTypeHandler UOE 桩），符合门措辞「如实记录 HEAD 既有项」；跨平台零 OS 专有新增。
  - 抽查 5 测试断言强度 vs HEAD：全部加强或等强度（TestRedisStoreWiring 虚拟化、TestLettuceNosqlService 重求值证据、TestPulsarConsumeTask 计数器证据、TestGraphQLLogger DB 可观测等待、TestCheckpointAbortMarkerCleanup 点查）。
  - 5 处具名残余逐处读码复核：机制与裁定一致（服务端时钟/broker 逐出语义/无 ticker/冻结时钟会挂死超时路径/中断界内节拍）。
  - 审计发现处理：Minor#1「5 模块 pom」→「仅 auth-service pom」已修正；Minor#2「3 连跑全 reactor」→「3 连跑单模块 + 1 轮全 reactor」已修正；Info 跨模块修复（nop-stream）已在 plan/log 披露。
  - 审计判定：**APPROVED**。

Follow-up:

- WI-7 / WI-8 各模块清欠（roadmap 承接）。
- 无剩余 plan-owned work。
