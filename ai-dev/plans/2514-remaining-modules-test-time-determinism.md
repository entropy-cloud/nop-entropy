# 2514 其余模块测试时间确定化（roadmap WI-8 收官）

> Plan Status: completed
> Last Reviewed: 2026-11-08
> Source: `ai-dev/backlog/test-timing-determinism-roadmap.md` WI-8；范式沿用 plan 2510–2513。
> Related: `ai-dev/plans/2513-kernel-serviceframework-test-time-determinism.md`（completed）；`ai-dev/plans/2508`；`ai-dev/plans/2509`
> 编号说明：沿用仓库当前 4 位序列（最近为 2513）。
> Review: R1 对抗审查（独立子 agent，守卫核对 + 6 代表文件 seam 实读）1 Major + 4 Minor 全改（F1 code-service 计数 14→11；F2 残余预算预裁定清单 + 负向窗确定性判据；F3 生产零改动硬门 21 模块（nop-ioc 纯测试侧核实）；F4 无依赖环核实记录；F5 负向观察窗族判据入手册），判定可执行。
> 验证环境：`<repo>` = `-Dmaven.repo.local=$PWD/.m2-repo`（逐命令展开）。
> 跨平台约束：改造面纯 JDK/JUnit；socket/netty/http 面优先进程内桩与 JDK 标准 API，不新增 OS 专有假设。

## Purpose

WI-8 收官：把尚未被 WI-1 ~ WI-7 覆盖（或覆盖后未清欠）的其余模块时间等待清欠，完成 roadmap 全部工作项。

## Current Baseline

- 守卫基线（2513 收口后）全仓 94 处，其中 **38 处为先前 WI 已具名裁定的残余**（不再重改）：nop-stream 16（WI-4：fraud gated 9 / runtime 3 / core canonical+中断收尾 2 / debezium gated 1 / flow 1）、nop-ai 1（WI-5：SSE seam 候选）、nop-dev-tools 调试子系统 10（WI-1/2507 收口时逐处裁定为条件等待辅助的轮询间隔与挂死上限——awaitClockReads/awaitSuspended 等允许形态）+ debug-web 1（同）。
- **WI-8 真实待清欠面 = 56 处 / 21 模块**：nop-code-service 11、nop-datav-service 9、nop-job-local 5、nop-rpc-core 5、nop-http-client-jdk 4、nop-netty 4、nop-cluster-core 3、nop-code-core 3、nop-ioc 3、nop-socket 3、nop-batch-core 2、nop-dyn-service 2、nop-metadata-service 2、nop-orm-migration 2、nop-report-service 2、nop-integration-feishu 1、nop-job-coordinator 1、nop-orm-data 1、nop-search-lucene 1、nop-sys-dao 1、nop-task-ext 1。最大文件：TestNopCodeIndexIdempotencyInvariant 5、TestIncrementalIndexWithDb 4、TestLocalJobScheduler 4、TestLettuce 系已清。
- 生产时间读取：各模块 main 源改造前逐模块盘点（以测试需虚拟化的判定读为准；仅诊断/数据戳保留并记录）。
- 全 reactor 基线：2513 收口时 456 模块 BUILD SUCCESS。

## Goals

- 21 模块 56 处清欠至具名裁定残余，总降幅 ≥ 90%（56 → ≤ 6）。
- 全 reactor 测试保持 0 失败 0 错误。

## Non-Goals

- 不重改先前 WI 已裁定的 38 处残余（各 WI plan 为其裁定 owner）。
- 不改各模块生产行为语义（判定性读取迁移 CoreMetrics 除外，且默认行为不变）。

## 改造手册

沿用 2510–2513 判定表（ManualClock/ManualScheduler 注入、latch + FutureHelper.waitUntil、重新求值证据、结构性判据、时钟卫生注册/恢复）；socket/netty 面优先进程内桩；外部/真实环境面条件等待机械化 + 具名裁定。

## Execution Plan

### Phase 1 - 全量盘点与模块改造

Status: completed
Targets: 21 模块测试源

- Item Types: `Fix`

- [x] 逐模块 pom 加 nop-commons test-jar（按消费面硬前置；R1 已核实 21 模块无 api-core 式依赖环——nop-commons 对 nop 模块仅依赖 nop-api-core）。
- [x] 56 处逐文件改造（独立实现子 agent，按模块分批）：code 索引/分析器等待、datav 异步提交/导出、job 调度、rpc 轮询、http/netty/socket 重连与流、cluster 选举/注册、ioc 死锁探测、batch/dyn/metadata/report 等 → 虚拟时钟或显式同步。
- [x] **残余预算预裁定（R1 实测分类）**：负向观察窗族（「X 毫秒内未发生」）的确定性化判据 = 定时器条目从注册表移除 / onceTask 取消等结构性证据；真实环境面具名裁定清单（虚拟时间不可达理由）——netty TestNettyTcpClientReconnect 重连停止窗（真实事件循环定时器 :73）、job-local sleep(300)+fireCount 负向窗（~3 处 :142/:163/:193/:544）、nop-integration-feishu 远程面 1、nop-orm-migration quiesce 窗口语义（:57/:76）——预计 4-6 处，为 ≤6 预算的主要构成；其余面须全清。
- [x] **生产零改动硬门**：21 模块 `git diff --stat -- '<module>/src/main/*'` 均为空（本 plan 只改测试源与测试假件；R1 已核实 nop-ioc 死锁测试可纯测试侧改造、LocalJobScheduler 构造器已可注入定时器，均无需生产 seam——nop-core-framework Protected Area 不触发）。
- [x] 变异探针 ≥ 2 组（如 job 调度触发、netty 重连判定）撤行为即红、恢复 grep 校验后复跑绿。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 守卫切面残余 5（目标 ≤ 6 ✓，逐处在文件内裁定注释 + batch report 账本）：netty 重连停止窗 / SSE post-cancel 负向窗 / sys-dao + datav Caffeine TTL / task-ext 异步态；预裁定的 feishu 与 orm-migration 残余实际被虚拟化消号（超预期）。
- [x] 21 模块测试全绿（分批 `-pl ... -am`）。
- [x] 探针证据齐全。
- [x] `ai-dev/logs/2026/11-08.md` 已更新。

### Phase 2 - 守卫消号与全量收官

Status: completed
Targets: 守卫基线、全 reactor、roadmap

- Item Types: `Proof`

- [x] 全仓 `--update` 消号（WI-8 面 56 → 具名残余；diff 限定 WI-8 模块面 + 各 WI 既有残余不变）；全仓 check exit 0。
- [x] 满载稳定性证明：满载期间最大的 2 个模块面连续 2 轮全绿（burner 纯 Java，结束无残留）。
- [x] 全 reactor `./mvnw <repo> test -T 1C -fae` 0 失败 0 错误。
- [x] roadmap WI-8 → done（全部 WI 收官）；logs 收口条目（全仓基线终值、残余总清单指针）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 消号证据写入 logs（全仓 94→33，非 WI-8/adjudicated 面零漂移）；全仓 check exit 0。
- [x] 满载 2 轮证据写入 logs。
- [x] 全 reactor 证据写入 logs（第 3 轮 BUILD SUCCESS 456 模块；前 2 轮暴露并修复 8 处先前批次遗留时敏缺陷——含 SubmissionPublisher 无订阅者丢项实锤与 9 处同族加固）。
- [x] roadmap 状态块已更新。

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 21 模块测试源四类依赖清零（残余 ≤ 6 且逐处具名裁定记录于 logs）
- [x] 21 模块生产源零改动（`git diff --stat -- '<module>/src/main/*'` 全空硬门）
- [x] 守卫基线 WI-8 切面消号完成（56→5，降幅 91%；审计 F1 后补登记 TestStreamStateResetTool 轮询节拍为具名残余，全仓终值 34，check exit 0 复验）
- [x] 变异探针 ≥ 2 组证据齐全（撤行为即红、恢复 grep 校验、复跑绿）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope 项
- [x] 独立子 agent closure audit 已完成并记录证据
- [x] **Anti-Hollow Check**：虚拟时钟推进在生产判定路径被消费（探针即证明）；hollow 扫描如实记录 HEAD 既有项
- [x] `./mvnw <repo> compile`（由 test 生命周期传递满足）
- [x] `./mvnw <repo> test`（全 reactor）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2514-remaining-modules-test-time-determinism.md --strict` 退出 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 errors
- [x] **跨平台核查**：改造面纯 JDK/JUnit；socket/netty 面无 OS 专有假设；结论入 logs

## Deferred But Adjudicated

（本 plan 无 deferred 项。）

## Non-Blocking Follow-ups

- 守卫工具 parkNanos 形态识别（2513 审计 Minor）。
- 各 WI 既有具名残余的后续虚拟化 seam（fraud 多 JVM、SSE subscription-ready 等，watch-only）。

## Closure

Status Note: WI-8 收官完成：21 模块守卫切面 56→5（91%，5 处均为真实环境/无 seam 的具名裁定；预裁定的 feishu 与 orm-migration 残余实际被虚拟化消号）；21 模块生产源零新增改动；满载 2 轮 + 全 reactor 第 3 轮 456 模块 BUILD SUCCESS（前 2 轮暴露并修复 8 处先前批次遗留时敏缺陷，含 SubmissionPublisher 无订阅者丢项的根因实锤与 9 处同族订阅 gate 加固）。roadmap WI-1 ~ WI-8 全部完成。独立 closure audit APPROVED-WITH-FIX（F1：末次 --update 后新增的轮询节拍未登记——已补在文件裁定注释 + 复跑 --update + check exit 0 复验）。

Completed: 2026-11-08

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure-audit 子 agent（fresh session，未参与实现；两个实现代理接力——首个因用量上限中断，续批代理重建账本）
- Evidence:
  - Phase 1：守卫实测 WI-8 面残余恰 5（netty/http-sse/sys-dao/datav/task-ext），5 处在文件裁定注释逐处读码复核（真实 EventLoop 定时器 / 真实 socket 负向窗 / Caffeine nanoTime 无 ticker seam / 无注入钩子——均诚实）；56 文件 diff 中其余 sleep 全部映射到该 5 处；探针 2 组红→恢复→绿（含 root-cause 修复 readsBy 线程门）；预裁定残余 feishu/orm-migration 虚拟化超预期达成。
  - Phase 2：基线 diff 限定 WI-8 面（94→33→F1 后 34）；prior-WI adjudicated 面（nop-stream 16 / nop-ai 1 / nop-dev-tools 11）数值完整；gateway interEventDelayMs 残余被订阅 gate 族加固合法消号（日志有据）；满载 2 轮绿；全 reactor 第 3 轮 BUILD SUCCESS / 456 SUCCESS / 0 FAILURE。
  - 零生产硬门：21 模块 src/main 仅 4 个 HEAD 前既有在途文件（LocalJobScheduler + 3 datav——内容与时间确定化无关，batch report §7 已披露并留协调裁定标记），本批零新增。
  - 8 处全门修复逐一 diff 复核（断言全部加强）：TestAutoRegistration stop 前捕获、TestStreamStateResetTool awaitDurableCheckpointState ×5 站点、TestInjectionSemanticsTrichotomy second-done 产物等待、TestE2EJdbcTwoPhaseCommitSink 三观测合并收敛、TestChatServiceFailoverAdapterStreaming 订阅 gate ×10、TestTaskManager 状态收敛、TestCheckpointParticipantContraction elapsed<2000 删除。
  - 跨平台：56 测试文件 diff 零 OS 专有新增。
  - 文本一致性：checklist --strict exit 0；doc-links --strict 0 errors；hollow 抽查 3 模块 exit 0。
  - 审计发现处理：F1（Major）已修——裁定注释 + --update 复登记 + check exit 0 复验 + 日志修正；F2（既有在途文件预存在性靠披露——提交时保持标记）；F3（gate 计数 9 vs 10——叙述性差异）。
  - 审计判定：**APPROVED-WITH-FIX**（已修）。

Follow-up:

- 守卫工具 parkNanos 形态识别（2513 审计 Minor）。
- 5 处具名残余的后续 seam（netty 事件循环可注入定时器、Caffeine ticker、SSE 订阅终止回调、task-ext 注入钩子——watch-only）。
- 无剩余 plan-owned work。
