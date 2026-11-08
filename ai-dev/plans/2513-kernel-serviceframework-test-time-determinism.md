# 2513 nop-kernel / nop-service-framework 测试时间确定化（roadmap WI-7）

> Plan Status: completed
> Last Reviewed: 2026-11-08
> Source: `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-7；范式沿用 plan 2510–2512。
> Related: `ai-dev/plans/2512-auth-nosql-message-test-time-determinism.md`（completed）；`ai-dev/plans/2508`（工具 owner）；`ai-dev/plans/2509`（守卫纪律）
> 编号说明：沿用仓库当前 4 位序列（最近为 2512）。
> Review: R1 对抗审查（独立子 agent，逐文件核实 seam：RateLimitExecutorImpl 走 IScheduledExecutor 注入、LocalResourceLockManager 生产读 CoreMetrics、graphql 为 mock 无真实端口、gateway 拦截器读 CoreMetrics）2 Major + 3 Minor 全改（api-core test-jar 成环例外、TestXplTask 预裁定、-am 补齐、生产源硬门精确化、时钟卫生条），判定可执行。
> 验证环境：`<repo>` = `-Dmaven.repo.local=$PWD/.m2-repo`（逐命令展开）。
> 跨平台约束：改造面纯 JDK/JUnit；nop-kernel 为框架核心（Protected Area）——本 plan 只改测试源与测试代码假件，不触 nop-kernel 生产源；如改造确需生产 seam，先在 plan 批注裁定再动手。
> 范围注记：roadmap WI-7 列名「nop-kernel（commons / xlang / xlang-truffle）、nop-service-framework（graphql-core / gateway）」；基线另含 nop-api-core TestContextTaskQueue 1 处（同属 kernel 层测试，一并承接）。

## Purpose

WI-7 模块组 28 处守卫基线（nop-api-core 1、nop-commons 10、nop-xlang 4〔含 test model fixture〕、nop-xlang-truffle 1、nop-graphql-core 11、nop-gateway 1）收口到虚拟时间/显式同步，守卫切面消号至具名裁定残余。

## Current Baseline

- 守卫基线（2512 收口后）WI-7 切面 **28 处**：TestContextTaskQueue 1（POLL）；TestRateLimitExecutor 5（OTHER 3/STA 2）、TestSequentialTaskExecutor 1、TestLocalLockManager 4（OTHER 2/STA 2）——commons；TestXplTask 2、test model/ExprAST.java 2——xlang；TestXLangContextPool 1——truffle；graphql-core 11（TestGraphQLEngineWithWebSocket 5、SubscriptionMockSchemaLoader 2、TestGraphQLSubscriptionPublisher 3、TestJsonRpcWebSocketHandler 1）；gateway AiRateLimitGatewayInterceptorTest 1。
- 生产时间读取：commons `StandardThreadPoolExecutor:109/:110/:178` 裸读 currentTimeMillis（线程续期判定）——本 plan **不改**（nop-kernel 核心生产源受 Protected Area 约束，线程续期非本组测试的被测判定——TestRateLimitExecutor 消费的是 RateLimitExecutor）；graphql/gateway main 无裸读命中需迁移。
- 测试工具：nop-commons 测试源内直接用同模块的 ManualClock/ManualScheduler（无需 pom 变更）；nop-api-core/xlang/truffle/graphql/gateway 消费 ManualClock 需各自 pom 加 nop-commons test-jar（按消费面硬前置；graphql-core 已可能经传递——逐 pom 核对）。
- 全 reactor 基线：2512 收口时 456 模块 BUILD SUCCESS。

## Goals

- WI-7 切面四类禁止写法清零（残余逐处具名裁定，总降幅 ≥ 90%，28 → ≤ 3）。
- 全 reactor 测试保持 0 失败 0 错误；nop-kernel 生产源零改动。

## Non-Goals

- 不改 nop-kernel / nop-service-framework 生产行为语义（Protected Area）。
- 不处理 WI-8 模块。

## 改造手册

沿用 2510–2512 判定表；**nop-kernel 生产源禁改**（测试假件/Fake 可改）；限频器/锁管理/订阅发布等待优先虚拟时钟（生产读 CoreMetrics 时）或注入 seam；WebSocket/订阅测试用 latch + 条件等待（等订阅建立/消息到达为确定性事实）。

**时钟卫生**：`CoreMetrics.registerClock` 为 JVM 全局静态——逐测试注册、`@AfterEach` try/finally 恢复 `CoreMetrics.defaultClock()`（surefire parallel=classes / forkCount=4 下同 fork 类串行，漏恢复冻结同 fork 后续全部类）。

## Execution Plan

### Phase 1 - 模块改造

Status: completed
Targets: 上述 6 模块测试源

- Item Types: `Fix`

- [x] 逐模块 pom 加 nop-commons test-jar（按消费面硬前置；nop-commons 自身无需）。**nop-api-core 例外**：nop-commons 编译依赖 nop-api-core，api-core 测试不能依赖 nop-commons test-jar（reactor 构建序成环）——TestContextTaskQueue 用局部 latch/锁辅助改造，无需时钟工具（R1 已核实）。
- [x] 28 处基线逐文件改造（独立实现子 agent，按 R1 已核实的 seam）：
      - commons：RateLimitExecutorImpl **无时钟读**、经构造器接收 IScheduledExecutor → 注入 `ManualScheduler`（与 ManualClock 同线）；LocalResourceLockManager 生产读 CoreMetrics（7 处）→ 注册 ManualClock 虚拟化；TestSequentialTaskExecutor 同法。
      - api-core：TestContextTaskQueue POLL → 纯 latch 时序（标记任务证明 flusher 进入退出窗口；无时钟工具）。
      - xlang：model/ExprAST.java 为 main() 草稿 fixture（GC 稳定 sleep）→ 平移改造；TestXLangContextPool sleep(200) 借出阻塞断言 → 池内状态/短上限条件断言。
      - **TestXplTask 预裁定**：sleep 等待真实 WatchService 文件事件（OS 延迟不可控、生产 watcher 路径无测试可观测 latch）——优先尝试钩住 xtask 重跑回调的确定性化；不可达则具名裁定为残余（预算 ≤3 内）。
      - graphql-core：mock IWebSocketSession、无真实服务器/端口；订阅建立/消息到达 → 既有 waitForMessages/latch；testUnsubscribe/testOnClose 类负向窗口 → waitForMessages(...)==false 形态或具名裁定；MockSchemaLoader sleeps 在可编辑 mock 内 → latch。
      - gateway：拦截器生产读 CoreMetrics（:158/:171）→ registerClock 虚拟化（nop-gateway 已直依 nop-commons，加 test-jar 即可）。
- [x] 变异探针 ≥ 2 组（commons 限频窗口、graphql 订阅发布就绪）撤行为即红、恢复 grep 校验后复跑绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 守卫切面残余 0（目标 ≤ 3 ✓；TestXplTask 预裁定候选被转化为条件等待确定性化）。
- [x] 触碰模块测试全绿（`./mvnw <repo> test -pl <modules> -am`，单模块测试约定须带 -am）；nop-kernel 生产源零改动硬门 = `git diff --stat -- 'nop-kernel/*/src/main/*'` 为空（nop-commons 测试源为预期改动面）。
- [x] 探针证据齐全。
- [x] `ai-dev/logs/2026/11-08.md` 已更新。

### Phase 2 - 守卫消号与全量门

Status: completed
Targets: 守卫基线、全 reactor

- Item Types: `Proof`

- [x] 全仓 `--update` 消号（WI-7 切面 28 → 具名残余）；diff 限定 WI-7 面；全仓 check exit 0。
- [x] 满载稳定性证明：满载期间 `./mvnw <repo> -o test -pl nop-kernel/nop-commons,nop-service-framework/nop-graphql/nop-graphql-core -am` 连续 2 轮全绿。
- [x] 全 reactor `./mvnw <repo> test -T 1C -fae` 0 失败 0 错误。
- [x] roadmap WI-7 → done；logs 收口条目。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 消号证据写入 logs（122→94，diff 非 WI-7 面零漂移）；全仓 check exit 0。
- [x] 满载 2 轮证据写入 logs。
- [x] 全 reactor 证据写入 logs（第 3 轮 BUILD SUCCESS 456 模块；前 2 轮暴露并修复 3 处先前批次遗留时敏断言/竞态）。
- [x] roadmap 状态块已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 测试源四类依赖清零（残余 ≤ 3 且逐处具名裁定记录于 logs）
- [x] nop-kernel 生产源零改动（`git diff` 硬门）
- [x] 守卫基线 WI-7 切面消号完成（降幅 ≥ 90%），全仓 check exit 0
- [x] 变异探针 ≥ 2 组证据齐全（撤行为即红、恢复 grep 校验、复跑绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：虚拟时钟推进在生产判定路径被消费（探针即证明）；hollow 扫描如实记录 HEAD 既有项
- [x] `./mvnw <repo> compile`（由 test 生命周期传递满足）
- [x] `./mvnw <repo> test`（全 reactor）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2513-kernel-serviceframework-test-time-determinism.md --strict` 退出 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] **跨平台核查**：改造面纯 JDK/JUnit；WebSocket/订阅测试不引入 OS 专有等待；结论入 logs

## Deferred But Adjudicated

（本 plan 无 deferred 项。）

## Non-Blocking Follow-ups

- WI-8 各模块清欠（roadmap 承接）。
- StandardThreadPoolExecutor 线程续期裸读（Protected Area，后续独立裁定）。
- 守卫工具补充 `LockSupport.parkNanos` 等待形态识别（审计 Minor，plan 2509 工具的检测面缺口；非阻塞）。
- nop-stream 两处既有负载敏感flake（TestJobCoordinatorStandbyStateMachine / TestE2EJdbcTwoPhaseCommitSink，2513 修复代理观察到偶发、多数轮次通过）登记观察（watch-only residual）。

## Closure

Status Note: WI-7 六模块测试时间确定化完成：守卫切面 28→0（含预裁定残余候选 TestXplTask 被转化为确定性条件等待）；nop-kernel 生产源零改动硬门过（diff 仅 HEAD 既有无关 TextScanner）；3 组变异探针红→恢复→绿；满载 2 轮 + 全 reactor 第 3 轮 456 模块 BUILD SUCCESS（前 2 轮暴露并修复 3 处先前批次遗留时敏断言/竞态 + 1 处守卫不可见阈值残留）。独立 closure audit APPROVED（3 Minor 非阻塞 follow-up）。

Completed: 2026-11-08

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现）
- Evidence:
  - 守卫实测 nop-kernel / nop-service-framework 面均 0 命中；基线 122→94 精确限定 12 个 WI-7 文件条目，零越界漂移。
  - Protected-area 硬门独立复核：`git diff --stat -- 'nop-kernel/*/src/main/*'` 仅 HEAD 既有 TextScanner；nop-kernel src/main 变异残留 grep 空；RateLimitExecutorImpl diff vs HEAD 空；checkpoint seam 在 nop-stream 非 nop-kernel（volatile + 附加包私有 setter，默认路径不变）。
  - 12 文件逐个 vs HEAD 抽查（含 TestXplTask 转化真实性——仍 @Disabled 但断言结果确定化）；3 处全门修复 diff 复核（parked future + releaseAll；advance(399)/(+1) 精确虚拟时刻替换 elapsed>=350；同文件兄弟测试 elapsed<2000 删除 + 无 advance 完成即结构证明；回调证据条件等待）。
  - 探针 3 组记录于 batch report + logs（红相原始日志未留存——叙述证据完整，审计记 Minor）。
  - 满载 2 轮绿；全 reactor 第 3 轮 BUILD SUCCESS / 456 SUCCESS / 0 FAILURE（_tmp/wi7-full-test3.log）。
  - checklist --strict exit 0；doc-links --strict 0 errors；hollow nop-kernel 0 findings、nop-service-framework 3 处 HEAD 既有（ChunkFileUploadHandler，未触碰）。
  - 审计判定：**APPROVED**（3 Minor：parkNanos 检测面缺口、探针红相日志未留存、2 处既有负载 flake 建议登记——均已入 Non-Blocking Follow-ups）。

Follow-up:

- WI-8 各模块清欠（roadmap 承接）。
- StandardThreadPoolExecutor 线程续期裸读（Protected Area，后续独立裁定）。
- 守卫工具补充 parkNanos 等待形态识别（plan 2509 工具检测面缺口）。
- 无剩余 plan-owned work。
