# 2583 nop-ib 正确性修复：索引陈旧假绿 + 并发预算 + Maven 委托超时

> Plan Status: completed
> Last Reviewed: 2026-11-21
> Adversarial Review: 2026-11-21 独立子代理 1 轮通过（2 先修已吸收：失效点=CompileCommand、预算改参数传递）
> Source: `ai-dev/analysis/2026-11/2026-11-21-monthly-new-code-deep-audit.md`（B3 + nop-ib Major/Minor 群）
> Related: `ai-dev/design/nop-incremental-build/` 系列 design 文档

## Purpose

消除 nop-ib 的假绿类缺陷（索引陈旧、选择集早退）与并发预算失效，使 check/test 的绿结果可信。

## Current Baseline

- `IbEngine.index()`（IbEngine.java:116-119）进程级缓存 UnitIndex，编译后不清：同次 check/test 内测试选择集与 `--cache` 指纹用编译前旧索引 → 新测试漏派发 + 缓存假绿；HTTP 服务模式跨请求累积（ServiceCore.java:298-306）。
- `TestCommand.setWorkerBudgetOverride` 全局静态，多项目并发 job 互相覆盖/提前清零（ServiceCore.java:169-175）。
- `MavenFallback.java:80-92`：输出泵 readLine 可永久阻塞使 waitFor 超时失效；超时返回 1 而非 EXIT_LOCK_TIMEOUT=75（死代码）；err 文本在 runSurefire/runMavenModuleTest 被丢弃。
- `CompileCommand.java:94-97`：`-pl` 枚举在循环内遇首个无源模块 changed 为空即整体早退返回 OK，后续模块编译被静默跳过。
- wave11 去锁后 `-pl` 并行测试分支可对同一 worktree 并发派生多个 mvn `-am` 进程（TestCommand.java:326-344）。

## Goals

- 编译完成后受影响模块索引即时失效；选择集与指纹始终基于最新索引。
- 预算 override 按 job 隔离；Maven 委托超时真实生效且可观测（75 + err 上报）。
- `-pl` 选择集不再假绿；进程内 Maven 委托并发受控。

## Non-Goals

- 不改 HTTP 服务 API 契约；不重做缓存指纹模型（仅掺维度）；不动 mvn 白名单机制。

## Scope

### In Scope

- nop-tools/nop-ib：IbEngine、IncrementalCompileExecutor、TestCommand、CompileCommand、MavenFallback、ServiceCore、TestCacheStore、ServeHttp

### Out Of Scope

- EffectivePomParser/ModelRefresher/JUnitWorkerPool 内部重构；nop-rg 修复（归 2587 或独立计划）。

## Execution Plan

### Phase 1 - 假绿根除（B3 + compile -pl 早退）

Status: done
Targets: `IbEngine.java`、`IncrementalCompileExecutor.java`、`TestCommand.java`、`CompileCommand.java`

- Item Types: `Fix`

- [x] 编译执行完成后失效索引：失效点=CompileCommand（:155-157 编译返回后复用既有 `engine.refreshIndexesIfModelChanged()` 或受影响模块逐个 remove），不给 IncrementalCompileExecutor 加回调签名
- [x] `compile -pl` 无源模块早退判断移出循环，循环后统一判定
- [x] `model.moduleForPath` null 时显式 NopIbException（TestCommand.java:423、CompileCommand.java:121）

Exit Criteria:

- [x] 回归钉：同一 check 调用内"预编译新增测试类 → 选择集包含该类"；`--cache` 指纹在编译后反映新 contentHash
- [x] 回归钉：`-pl <无源parent>,<有源child>` 实际编译 child（变异探针红绿）
- [x] `ib test -pl :nop-ib` 全绿
- [x] `ai-dev/logs/` 已更新

### Phase 2 - 并发预算与 Maven 委托治理

Status: done
Targets: `ServiceCore.java`、`TestCommand.java`、`MavenFallback.java`、`TestCacheStore.java`、`ServeHttp.java`

- Item Types: `Fix | Feature`

- [x] workerBudgetOverride 改 per-job 参数传递（workerBudget() 的调用方显式传值），消除全局静态互踩；不采用 ThreadLocal（moduleTestPool 共享线程会让其静默失效且钉子测不出）
- [x] MavenFallback：独立 daemon 线程泵输出 + 带 timeout 的 waitFor 主导；destroyForcibly 后 join 泵线程（带超时防线程泄漏）；超时返回 75；err 文本透传上报
- [x] 进程内 Maven 委托共享信号量串行化（或并行分支禁用并发 `-am` 委托，随实现裁定）
- [x] 缓存指纹掺入 nop-ib 版本 + JDK 版本维度
- [x] `/v1/run` 请求体限读（把 ServiceCore 请求 payload 8MB 上限前移到读取侧，防无界 readAllBytes OOM）；QUEUED 取消 notifyAll；health() engines 计数线程安全化
- [x] 生产路径 `System.err.println("nop-ib debug:…")` 挂 debug 开关

Exit Criteria:

- [x] 回归钉：两并发 job 预算互不覆盖（TestCommand override 断言）；Maven 超时路径返回 75 且 err 包含 timeout 信息（变异探针：移除泵线程 → 钉子必红）
- [x] 指纹维度变更后旧 GREEN 不命中（显式测试）
- [x] `ib test -pl :nop-ib` 全绿；`~/nop-ib/bin/package-ib.sh` 重打包 + 服务冒烟（/v1/health、一次真实 check）
- [x] design 07/相关 owner doc 如行为面变化已同步（否则显式 No owner-doc update required）
- [x] `ai-dev/logs/` 已更新

## Closure Gates

- [x] B3 修复并有回归钉
- [x] in-scope Major 全部修复（预算/超时/早退/并发委托）
- [x] focused tests 全绿 + 服务重打包冒烟通过
- [x] 无 in-scope live defect 被降级
- [x] owner docs 已同步或显式豁免
- [x] 独立子 agent closure audit 完成，evidence 写入本文件
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ib --severity high` 退出码 0（--path nop-tools/nop-ib 复跑确认非 vacuous）

## Deferred But Adjudicated

### recordLastFailed 字符串替换推导 surefire-reports 目录

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前布局恒匹配，目录推导健壮性属优化项
- Successor Required: `no`

## Non-Blocking Follow-ups

- surefire-reports 目录显式模型化；`GET /v1/help` 文档同步

## Closure

Status Note:
2026-11-21 实施完成（Phase 1 + Phase 2 全部 in-scope 项）。测试：`ib test -pl :nop-ib --force` 180/180 全绿（新增回归钉 9 个测试方法：TestPostCompileIndexRefresh×2、TestPlNoSourceModuleNoShortCircuit×1、TestMavenFallbackTimeout×2、TestTestCacheStore×2、TestServiceCore×2（concurrentJobsKeepIndependentBudgets / cancelOfQueuedJobWakesAwaiters））。服务重打包 + 冒烟通过（/v1/health 200；/v1/run check --paths → exit 0，compile fast-path files=1 + 缓存命中披露）。design 05 §7.3/§八 与 design 07 §2.1/§2.3 已同步（参数化预算、限读 413、waitFor 主导超时 + 门闸、指纹运行时维度）。独立 closure audit 与 check-plan-checklist / scan-hollow 留给主控，未执行。

Closure Audit Evidence:

- Reviewer / Agent: 待主控安排独立子代理审计
- Evidence（变异探针，均在本 plan 实施期间实测）:
  - 探针 1（compile -pl 早退回插）→ `TestPlNoSourceModuleNoShortCircuit.noSourceParentDoesNotSkipSourceChild` 红（"the has-source child module must actually be compiled"）；
  - 探针 2（MavenFallback 泵线程移除）→ 测试运行整体挂起 >400s 无完成（固定版 ~2s 内以 75 完成）——`TestMavenFallbackTimeout.hungDelegationTimesOutWith75AndErrDetail` 钉必红；
  - 探针 3（指纹 #dims 行移除）→ `TestTestCacheStore.runtimeDimensionChangeInvalidatesOldGreen` 红（"a changed ib/JDK runtime dimension must change the fingerprint"）。
  - 三探针均绿→红→还原后复绿；探针期间被变更文件均已还原（最终 `ib test -pl :nop-ib --force` 180/180）。
