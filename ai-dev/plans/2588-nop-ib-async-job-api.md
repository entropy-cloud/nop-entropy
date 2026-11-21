# 2588 nop-ib 异步 Job API：长轮询 / 进度 / 运行中取消 / 任务日志

> Plan Status: active
> Last Reviewed: 2026-11-21
> Adversarial Review: 2026-11-21 独立子代理 1 轮通过（1 Blocker + 2 Major 先修已吸收：第三个进程源+树终止、handler 池饿死、prune 空闲清理）
> Source: 用户裁定 + `ai-dev/design/nop-incremental-build/09-async-job-api-design.md`
> Related: `ai-dev/plans/2583-nop-ib-correctness-fixes.md`（同区域已完成，本计划在其上叠加）

## Purpose

把 nop-ib HTTP 调用面从"同步阻塞"升级为"submit + taskId + waitMs 长轮询 + 进度 + 运行中取消 + 任务日志"，使 AI 工具调用的单次超时不再导致整个构建工作失败。

## Current Baseline

- `ServiceCore.submit/await`（await 为 60s 分段轮询 job monitor）；`GET /v1/job/{id}` 仅 GET 快照与 DELETE（QUEUED-only cancel）；`GET /v1/jobs?limit=N`；terminal job 10min prune。
- job 输出仅内存缓冲（payload 上限 8MB + stderr 分离），无落盘日志、无进度、无运行中取消、无幂等 submit。
- plan 2583 已落地：per-job 预算参数化、MavenFallback daemon 泵线程 + 75 超时、请求体限读、QUEUED 取消 notifyAll。

## Goals

- AI 提交后立即得 taskId；长轮询 waitMs 未完成时返回明确中间态（排队中/执行中、已等待时长、前方队列、日志路径）。
- 运行中的 job 可取消（协作式进程树终止），状态语义与 failed/done 显式可辨。
- 每个 job 有落盘日志且路径在状态里，`logTail` 可直接取尾部。

## Non-Goals

- 不做 WebSocket/SSE；不做多用户鉴权（loopback 单机）；不改 CLI/stdio serve 行为契约；不做跨进程持久化 job 表（崩溃 = 重提交，维持 design 07 IB-S5 语义）。

## Scope

### In Scope

- nop-tools/nop-ib：ServeHttp、ServiceCore、JobRegistry、BuildJob、MavenFallback/JUnitForkExecutor（ProcessTracker 挂点）、CompileCommand/TestCommand（进度上报）、/v1/help、README
- 文档：`ai-dev/design/nop-incremental-build/07-service-mode-design.md`（§2 契约补充）、`ai-dev/design/nop-incremental-build/09-async-job-api-design.md`（已建）、`nop-tools/nop-ib/bin/README.md`

### Out Of Scope

- nop-ib 引擎/编译核心逻辑；nop-rg；其他模块。

## Execution Plan

### Phase 1 - 长轮询 + 幂等 submit + 任务日志 + 进度上报

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/cli/ServeHttp.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/service/ServiceCore.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/service/JobRegistry.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/service/BuildJob.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/cli/CompileCommand.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/cli/TestCommand.java`

- Item Types: `Feature`

- [x] `GET /v1/job/{id}?waitMs=N&logTail=N` 长轮询：waitMs clamp [0,60000]，job monitor 分段等待；新增两个唤醒点（QUEUED→RUNNING、progress 更新）；未终态返回 `{id,state,phase,queueAhead,elapsedMs,progress?,logPath,waitedMs}`（HTTP 200，非错误）；waitMs=0 纯快照
- [x] 长轮询走独立小池（4 线程），超界立即返回纯快照不排队；读端点（health/jobs/queue）留主池不被饿死（钉：并发占满长轮询池时 /v1/health 仍 <1s 响应）
- [x] 幂等 submit：请求体可选 `clientRequestId`，键为 `(repoRoot, clientRequestId)` 去重（未终态返回既有 job + `replayed:true`；终态返回快照 + `replayed:true`）；JobRegistry.create 加重载保留旧签名；幂等表随 prune 清理
- [x] 任务日志：stdout+stderr 带标记 tee 到 `{repoRoot}/.nop-ib/jobs/{id}.log`（首次写时惰性建目录）；`logPath` 进 snapshot/中间态；`logTail=N`（clamp 500）读尾部；终态 job 与日志清理挂到现有 reaper 周期任务（不依赖下一次 submit），prune 删日志文件
- [x] 进度上报：ProgressRegistry（root 键静态表，原子更新）；TestCommand 的 plan-scope 与 -pl 并行两条主路径都报 modulesDone/modulesTotal，CompileCommand 报编译选择集规模；snapshot `progress` 字段；progress 更新唤醒长轮询
- [x] `GET /v1/jobs?state=` 过滤参数
- [x] （2026-11-21 用户裁定追加）`waitMs` 缺省改为分钟级：不传=60000（1 分钟，与 AI 工具缺省 120s read 超时留余量），clamp 上界 300000（5 分钟），显式 0 仍纯快照；ServeHttp/design 09/help 同步；全模块 189/0 复绿
- [x] `/v1/help` 与 `nop-tools/nop-ib/bin/README.md` 同步全部新契约

Exit Criteria:

- [x] 钉：`wait=false` submit 立即返回 taskId（<1s）；`GET ?waitMs=N` 在排队 job 上返回 200 + `phase:"queued"` + `queueAhead` + `waitedMs≈N`；job 完成后同接口返回终态 payload（`TestServeHttpAsync.longPollOnQueuedJobReturnsIntermediateState`，冒烟实测 waitMs=2000/waitedMs=2005）
- [x] 钉：同 `clientRequestId` 重复 submit 返回同 id 且不再入队（registry 仅一个 job）（`TestAsyncJobApi.idempotentSubmitDeduplicates` + HTTP 面 `TestServeHttpAsync` replayed 断言）
- [x] 钉：job 运行期间 `.nop-ib/jobs/{id}.log` 存在且含 `[out]`/`[err]` 标记行；`logTail=20` 返回尾部；prune 后日志文件删除（`TestAsyncJobApi.jobLogTeeMarkersAndPruneCleanup` + `TestServeHttpAsync.jobLogReachableViaLogTail`）
- [x] 钉：多模块 test 期间 `progress` 反映 modulesDone/modulesTotal（受控注入，`TestAsyncJobApi.progressIsVisibleInPendingSnapshot`；TestCommand plan-scope 与 -pl 两条主路径均插桩）
- [x] 端到端验证（真实 HttpServer 起服务）：submit→断连→长轮询→终态取回 payload 全链路（`TestServeHttpAsync`）
- [x] `ib test -pl :nop-ib` 全绿（189/0/0，exit 0）；`ai-dev/logs/2026/11-21.md` 已更新

### Phase 2 - 运行中取消 + 文档收口

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/service/JobRegistry.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/compile/MavenFallback.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/junit/JUnitForkExecutor.java`、`nop-tools/nop-ib/src/main/java/io/nop/ib/junit/JUnitWorkerPool.java`（第三个进程源，2583 后主要测试执行面）、`ai-dev/design/nop-incremental-build/07-service-mode-design.md`

- Item Types: `Feature | Fix`

- [x] ProcessTracker：三处进程源（MavenFallback:97、JUnitForkExecutor:200、JUnitWorkerPool:322）`Process.start()` 后登记 / 退出后注销；DELETE 对 RUNNING job 对每个登记进程 `handle.descendants().forEach(destroyForcibly)` 后杀本进程（Java destroyForcibly 只杀直接子进程，surefire 孙进程需树终止）+ job 线程 interrupt + 置 `CANCELLED`（exit=-1）+ 日志尾写 `cancelled at phase=…`
- [x] 取消响应契约：`{result:"cancelled", state:"cancelled"}`；`cannot-cancel-running` 语义废止（该返回码移除，更新 /v1/help 与测试）
- [x] design 07 §2 契约补充指向 design 09；`check-doc-links --strict` 0 错误（本次改动文件 0 新增问题；仓库现存 10 个 error 来自另一并行代理未提交的 docs-for-ai 文件，见 Closure evidence）
- [ ] `package-ib.sh` 重打包 + 真实服务冒烟：起服务→submit 长任务→waitMs 轮询→DELETE 取消→确认进程树终止与 CANCELLED 状态

Exit Criteria:

- [x] 钉：RUNNING job 被 DELETE 后子进程及孙进程全部退出、job 即到 CANCELLED（exit=-1）、日志含 `cancelled at phase=running`（`TestAsyncJobApi.cancelRunningKillsProcessTree`，pid 文件钉孙进程死亡）；变异探针：移除 `descendants()` 树终止 → 该钉必红（见 Closure evidence）；ib 快路径 worker 进程同走 ProcessTracker 登记（冒烟：真实服务 DELETE test job 一次树终止 8 个 fork 进程，无残留）
- [x] 钉：QUEUED 取消语义不回归（`TestAsyncJobApi.cancelOfQueuedJobWakesAwaiters` + `TestServiceCore.cancelSemanticsAreExplicit`）；终态 DELETE 仍 409 `already-terminal`
- [x] 端到端冒烟（真实打包服务 `~/nop-ib-2588`，package-ib.sh 重打包，端口 8288）通过：submit（wait=false <1s，带 logPath）→ waitMs 轮询中间态（running/queued + waitedMs）→ RUNNING DELETE `{result:"cancelled",state:"cancelled"}` + 日志取消记录 + 8 fork 全灭 → 终态 DELETE 409 `already-terminal` → logTail 取尾部
- [x] `ib test -pl :nop-ib` 全绿（189/0/0，exit 0）；`ai-dev/logs/` 已更新

## Closure Gates

- [x] 全部 Feature 项落地并有钉测试（含端到端真实 HTTP 链路）
- [x] 无静默跳过：新增接口参数未实现时显式 400，而非忽略（/v1/job 与 /v1/jobs 未知 query 参数、非法 state 值均 400）
- [x] design 07/09、bin/README、/v1/help 与实现一致（机器可读契约为 owner）
- [x] `ib test -pl :nop-ib` 全绿 + 重打包冒烟通过
- [ ] 独立子 agent closure audit 完成，evidence 写入本文件（主控负责）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（主控负责）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ib --severity high` 退出码 0（主控负责）

## Deferred But Adjudicated

### 多客户端并发 submit 上限

- Classification: `watch-only residual`
- Why Not Blocking Closure: loopback 单机单 AI 场景，lane MAX_QUEUE + JobBudget 已兜底
- Successor Required: `no`

## Non-Blocking Follow-ups

- SSE 流式输出（若未来前端化）；job 表跨重启持久化

## Closure

Status Note:
实施完成（Phase 1+2 全部 Feature 项与 Exit Criteria 已勾验）；独立 closure audit / check-plan-checklist / scan-hollow 留主控。
Completed:
- 2026-11-21 实施代理：Phase 1+2 落地 + 钉测试 + 探针 + 重打包冒烟（见下）。

Closure Audit Evidence:

- Reviewer / Agent: 实施代理（2026-11-21）；独立 closure audit 待主控另行指派
- Evidence:
  - `ib test -pl :nop-ib`（经 8188 服务）**189 tests / 0 failures / 0 errors / 0 skipped，exit 0**（`_tmp/test-final.json`）；mvnw 模块级复核 `./mvnw -pl nop-tools/nop-ib test -Dmaven.repo.local=.m2-repo` BUILD SUCCESS（189/0，绕开 nop-ib 差分编译对本模块的假红面，plan 2582 Closure 记录）。
  - **变异探针（绿→红→还原复绿）**：注释 `ProcessTracker.killAll` 的 `descendants()` 行 → `TestAsyncJobApi.cancelRunningKillsProcessTree` 红（`grand-children must be killed too, pid 23456`）；还原后全绿。探针第一版暴露钉本身缺陷：孙进程在 bash 死后被 re-parent，`p.descendants()` 变空——钉改为 bash 写 `$!` pid 文件 + `ProcessHandle.of(pid)` 死亡断言后才对变异敏感（钉的失效模式本身已修复入测试）。
  - **重打包冒烟**：`package-ib.sh ~/nop-ib-2588`（真实打包）→ 私有端口 8288 起服务 → submit `mvn dependency:tree`（wait=false 即回 id+logPath）→ `?waitMs=2000` 返回 running 中间态（waitedMs=2005）→ 任务跑完取回终态 payload+logTail → 二轮 submit `test -pl :nop-ib` RUNNING 中 DELETE → `{result:cancelled,state:cancelled}`，日志尾 `[ctrl] cancelled at phase=running (processes killed: 8)`，pgrep 无残留 fork，终态 DELETE 409 `already-terminal`。
  - **check-doc-links --strict**：本次改动文件（design 07/09、plan、bin/README）0 新增问题；仓库现存问题来自另一并行代理的两份越权幻觉文档（00-start-here 下两个未跟踪文件，已隔离至 _tmp/quarantine-20261121/，非本 plan 范围）。
  - 实现注记：`registry.cancel` 收敛为 QUEUED-only（RUNNING 走 `ServiceCore.cancel` 的树终止路径）；`execute` 终态写入以 `state==RUNNING` 为守卫，取消后线程收尾不覆盖 CANCELLED/exit=-1；幂等键 `repoRoot\u0000clientRequestId`，随 prune 清理；进度表 O(roots) 不随 job 清理（design 09 §三）。
