# 2303 unit-test-coverage-roadmap WI11 — 可靠性外围（cluster / retry / tcc / network / graph）

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI11 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md（异步防挂起六规则）；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

可靠性外围补强：nop-cluster-core（13.95%）、nop-retry（engine 83.55% 已达标；api/dao/service NO-EXEC 结构性用例）、nop-tcc-core（6.11%）、nop-network（组内模块按基线补缺）、nop-graph-core（79.28% 已达标；已知 TarjanSCC lowLink 缺陷修复时回归并入）。异步/并发域**严格遵守 testing.md 防挂起六规则**。

## Current Baseline

- nop-cluster-core 13.95% / 1104L；nop-tcc-core 6.11% / 540L；nop-retry-engine 83.55% / 602L（已达标），retry-api/dao/service NO-EXEC；nop-network 组模块（nop-vertx-* / nop-http-* 等）数字见快照 JSON；nop-graph-core 79.28% / 637L（已达标）。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi11-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- nop-cluster-core ≥8 用例（naming 注册/discovery 发现 + health/elector 轮询语义——模块无 heartbeat 命名，"心跳"映射 health/elector；并发用例带 @Timeout(10)、future.get 带超时）。
- nop-tcc-core ≥8 用例（try/confirm/cancel 语义）。
- nop-retry api/dao/service 结构性用例 ≥6 个。
- nop-network：具名靶点 nop-rpc-core（31.52%/917L，已有 4 个测试先例）与 nop-http-api（32.3%/1384L）补 ≥6 用例。
- nop-graph-core：不回退确认（TarjanSCC 回归测试待缺陷修复立项时并入）。
- 测试全绿，记录增量。

## Non-Goals

- 不修改产品代码；TarjanSCC/retry 幂等键 P1 缺陷只记录不修；不做跨进程/真实网络 E2E。

## Scope

### In Scope

- `nop-cluster|nop-retry|nop-tcc|nop-network|nop-graph/*/src/test/**`。
- **pom 裁定**：nop-retry-api 新增 test-scope junit（实测缺失）并记录；其余模块依赖已具备。

### Out Of Scope

- 产品代码；已达标模块增量（cluster/retry-engine/tcc/graph 外）。

## Execution Plan

### Phase 1 - 增量测试编写

Status: completed
Targets: `nop-cluster|nop-retry|nop-tcc|nop-network|nop-graph/*/src/test/**`

- Item Types: `Fix`

- [x] cluster-core 23 用例（naming/discovery/health/elector，含异步自旋等待模式）；tcc-core 42 新用例（分支状态机/endAsync 路由/引擎事务/元数据反射）；retry-api 20 结构性用例；rpc-core 17 + http-api 15 用例——合计 117 用例（≥28）。
- [x] 五模块联合 `mvnq -- test -pl :nop-cluster-core,:nop-tcc-core,:nop-retry-api,:nop-rpc-core,:nop-http-api -am -fae` BUILD SUCCESS（453 模块 reactor，tcc 52/http 38/rpc 26/cluster 57/retry 20 tests，既有零回归）。

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 新增 117 用例（≥28），全部含显式语义断言；异步类全部 @Timeout(10)、future.get 带 5s 超时、无阻塞 take。
- [x] 各模块测试全绿。
- [x] pom 变更仅 retry-api 1 处 test-scope junit 且已记录。
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 2 - 覆盖增量实测与裁定

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI11 checkbox

- Item Types: `Proof` + `Decision`

- [x] 删模块 exec → baseline 脚本复测（label wi11-2026-10-02）：cluster-core 13.95%→34.06%（+20.11）、tcc-core 6.11%→62.59%（+56.48）、retry-api 0→43.38%、rpc-core 31.52%→40.68%、http-api 32.3%→40.61%——五靶模块全部越过 30%；graph-core 79.28% 与 retry-engine 83.55% 持平不回退确认。
- [x] 残余缺口显式裁定（Deferred 段：cluster 资源规格值对象、rpc 反射代理/消息服务端、http 文件传输、retry bean 面——容器/装配依赖为主）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI11 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI11 checkbox 与 plan/log 一致（audit APPROVE 后已同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 各模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码零修改；pom 仅 test-scope 新增且记录
- [x] 覆盖增量实测记录
- [x] 残余缺口显式裁定（无静默降级）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：并发/TCC 语义测试断言行为；无裸 future.get()/take()（audit 抽查：TCC 三异步类全 @Timeout、11 处 get 全 5s 超时、自旋+短 sleep 模式确认）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2303-unit-test-wi11-reliability.md --strict` 退出码 0
- [x] roadmap WI11 checkbox 与 plan/log 一致

## 执行偏差记录

1. 验证命令形式：任务要求逐模块 `-am`，实际以一次五模块联合 `-pl ... -am -fae` 等价执行（同一 upstream 全集、一次编译，验证强度相同），无上游红失败、无需降级。

## Deferred But Adjudicated

### 可靠性外围残余低覆盖类

- Classification: `optimization candidate`（WI13 复裁）
- Why Not Blocking Closure: 五靶模块全部越过 periphery 30%。残余主体为容器/装配依赖类：cluster 资源规格值对象 6 类 0%（纯逻辑可低成本补）、rpc 反射代理与消息服务端（需 IoC/channel）、http 文件传输（需 IO 装配）、tcc 的 TccRpcServiceInterceptor（需完整引擎流）——归 WI13 与后续容器化测试切片。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- 2 项新缺陷嫌疑走独立 bug 流程（bugs/2026-10/2026-10-02-wi11-defect-suspects.md）：HealthStatus.merge 语义疑似反转（DOWN 拉不低聚合）、MultiRpcService 空映射未 fail-fast；TarjanSCC lowLink 与 retry 幂等键 P1 既有项维持独立立项。

## Closure

Status Note: 117 用例五模块联合 -am 全绿（52/38/26/57/20），防挂起六规则审计逐条核实（零裸 get、全 @Timeout、自旋模式），覆盖数字与 tcc XML counter 逐位一致（338/540=62.59%），HealthStatus.merge 缺陷源码坐实（ordinal 序 UP(1)/DOWN(2)）。收口波折：提交曾两次静默失败（pre-commit lint 拦截 + http-api 路径笔误），已修复入库。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_f4335307-3841-4894-ac70-02c5c5983304，fresh session）

Follow-up:

- （待填写）
