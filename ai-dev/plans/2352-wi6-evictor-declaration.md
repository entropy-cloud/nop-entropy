# 2352 WI6 evictor 声明面

> Plan Status: completed
> Last Reviewed: 2026-10-09
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md`（WI6 行，M2 窗口控制面）
> Related: plan 2351（WI5 trigger——resolveEvictor 镜像其解析模式）；WI5 的 trigger×evictor 组合校验 Deferred 在本 WI 收口

## Purpose

新增 evictor 声明面（xdef 现无任何注入口）：strategy `evictorId` 属性 + window 节点 `<evictorId>` 子元素，builder 解析接线到 core Evictor 家族（Count/Time/Delta），既有 SPI 时序契约在声明面文档显式化（不新增 SPI 参数），组合校验 + 判别性测试（物理驱逐断言）。

## Current Baseline（2026-10-09 live 核对，侦查实证）

- **Evictor 家族（core/windowing/evictors）**：`CountEvictor.of(N[, doEvictAfter])`（保留最新 N 条——从头部移除 size-N 条；N<=0 抛）；`TimeEvictor.of(Duration[, doEvictAfter])`（cutoff = pane 内元素自身 timestamp 最大值 − windowSize，`ts <= cutoff` 逐出，边界含等号；无时间戳不逐出）；`DeltaEvictor.of(threshold, DeltaFunction[, doEvictAfter])`（pane 最后元素为基准，delta>=threshold 移除——bean 形态）。均无 canMerge 面。
- **算子消费**：`WindowedStream.evictor(...)`（:88）→ WindowOperator **唯一消费点在 fire 路径** `emitWindowContents`（:1370 evictBefore → window fn :1375 → evictAfter :1377 → **writeBackEvictedWindow :1382 物理写回**）；两回调均收 pre-eviction 计数（Flink 语义）；evictor 非 null 时强制 list/map 状态 + 元素时间戳旁路（:688-699/:2183），appending descriptor fail-fast（:1329）；`WindowOperatorBuilder.evictor` :75。
- **既有组合先例**：`KeyedStreamImpl.countWindow(size, slide)` = GlobalWindows + CountEvictor + CountTrigger——官方入口；GlobalWindows+evictor 状态有界由 writeBack 保证（TestEvictorStateLifecycle :52-77 钉定）；G46 transient 裁定已被 AR-2 supersede。
- **声明面现状**：xdef 无 evictor 任何形态（strategy :49-55 属性区、window :203-206 子元素区）；无 fail-fast（无面即无拒绝）；L1/L2 均无 evictor face；矩阵表 A/B 无行。
- **再生成机制**：flow 的 `_gen` 模型类头注释 "generate from /nop/schema/stream/stream.xdef"；根 pom exec-maven-plugin 绑定 generate-sources 跑 CodeGenTask——xdef 改后 `mvn generate-sources -pl nop-stream/nop-stream-flow -am` 再生成。

## Goals

- xdef：strategy 级 `evictorId="string"` 属性 + window 节点 `<evictorId>string</evictorId>` 子元素（镜像 triggerId 形态）。
- builder `resolveEvictor`：bean 优先 → 目录 `count:N`/`time:<dur>` → 未知 `ERR_STREAM_REF_UNKNOWN`；节点覆盖 strategy（镜像 WI5）；Delta 走 bean 形态（需 DeltaFunction，无目录语法）；`windowed.evictor(resolved)` 接线。
- 组合校验与 SPI 契约显式化：正参守卫（复用类内既有抛错）；时序契约（fire 路径 evictBefore→fn→evictAfter→物理写回；pre-eviction 计数；元素自身事件时间；GlobalWindows+evictor 状态有界）进目录文档；trigger×evictor 组合注释位收口（WI5 Deferred）。

## Non-Goals

- 不改 core Evictor 实现/SPI 签名（不新增参数）；不做 session×evictor 合并行为的新验证（Evictor 无 merge 面，声明层不设限制——组合矩阵如实记录「合并行为由窗口 assigner 层承担，evictor 在 fire 路径操作 pane 副本」）；不升 error。

## Scope

### In Scope

- `stream.xdef`（strategy 属性 + window 子元素）→ flow `_gen` 再生成。
- `AdvancedTransforms.resolveEvictor` + buildWindow 接线。
- 测试：flow 声明测试（目录/precedence/未知 id）+ runtime 判别 E2E（物理驱逐的发射序列）。
- 矩阵表 A 新增行（含 SPI 时序契约与适用域）；roadmap v6 版本注记（WI5 Deferred 收口）。

### Out Of Scope

- core 实现变更；L1/L2 规则（无既有 face 需删）；session×evictor 合并语义新验证。

## Execution Plan

### Phase 1 - xdef 与 builder 接线

Status: completed
Targets: `stream.xdef`、`AdvancedTransforms`

- Item Types: `Feature`

- [x] xdef：strategy 属性区加 `evictorId="string"`（triggerId 旁）；window 节点子元素区加 `<evictorId>string</evictorId>`
- [x] **再生成（两步命令，实测幂等序列——禁止 `-am` 直跑 generate-sources：generate-sources 阶段 nop-xdefs 未重打包，flow 的 codegen classpath 解析到 m2 陈旧 jar（实测会静默删除 emit/slide/leftTimeout/rightTimeout 等近期字段））**：① `./mvnw install -pl nop-kernel/nop-xdefs -DskipTests` 刷 m2 jar；② `./mvnw generate-sources -pl nop-stream/nop-stream-flow`（无 -am）；③ 核验：`git status` 下 `_gen` 漂移**仅** `_WindowingStrategyModel.java` 与 `_StreamWindowModel.java` 两个文件且含 getEvictorId/setEvictorId——任何其他 `_gen` 文件漂移 = 陈旧 jar 再生成事故，立即还原排查
- [x] `WindowedStreamImpl.getEvictor()` 访问器（镜像 getTrigger() :158-160；加性访问器非语义变更——Out of Scope 的「core 实现变更」指 Evictor SPI/算子行为，此为测试可观测面）
- [x] `resolveEvictor(owner, t, evictorId)`：bean 优先（Evictor.class）→ `count:N`（正整数守卫）/`time:<dur>`（parseDurationMillis）→ ref-unknown；节点覆盖 strategy；无嵌套语法故无 depth 参数
- [x] buildWindow 接线：`windowed.evictor(resolved)`（置于 trigger 块与 emit=UPDATE 块之间皆可）；trigger×evictor 组合注释位（两声明面正交——trigger 管 fire 时机、evictor 管 fire 路径 pane 裁剪；`emit=UPDATE` 与 evictorId 可共存——UPDATE 只拥有 trigger 槽位，无互斥）；WI5 Deferred 收口

Exit Criteria:

- [x] `_gen` 类含 evictorId 字段（generate-sources 产出核验）
- [x] `./mvnw compile -pl nop-stream/nop-stream-flow -am` 通过
- [x] `ai-dev/logs/` 条目已更新

### Phase 2 - 声明测试与判别 E2E

Status: completed
Targets: flow 测试 + runtime 测试

- Item Types: `Feature` + `Proof`

- [x] `TestEvictorDeclaration`（flow，镜像 TestTriggerDeclaration harness）：`count:2`→CountEvictor、`time:5s`→TimeEvictor、bean 优先、节点覆盖 strategy、未知 id ref-unknown、count:0 拒绝
- [x] **判别 E2E（runtime）**：`windowFnId="global"`（legacy 白名单直取 GlobalWindows.create()，其 getDefaultTrigger 返回 NeverTrigger 非 null）+ `evictorId="count:2"` + `triggerId="count:1"`（GlobalWindows 无水位依赖、无 cleanup timer，E2E 无需 timestampsAndWatermarks 节点）+ ACCUMULATING + reduce(sum)——输入 [1,2,3,4] 单键断言发射 **[1,3,5,7]**（无 evictor 反事实 [1,3,6,10]）——物理驱逐判别
- [x] **状态有界断言**：E2E 后核验（或独立测试）XDSL 声明路径的窗口状态 ≤ evictor 容量（TestEvictorStateLifecycle 的 XDSL 对应物；若 runtime 测试内可达状态则断言，否则以发射序列 + 既有 Java 侧钉子共同承担并在 plan 记录）

Exit Criteria:

- [x] 两测试类全绿（E2E 断言物理驱逐判别）
- [x] 无静默跳过：未知 evictorId/count:0 均 typed 拒绝
- [x] `ai-dev/logs/` 条目已更新

### Phase 3 - 文档与收口

Status: completed
Targets: user-guide

- Item Types: `Proof`

- [x] 矩阵表 A 新增「窗口驱逐器（WI6）」行：目录语法、SPI 时序契约（fire 路径 evictBefore→fn→evictAfter→writeBack 物理写回、pre-eviction 计数、TimeEvictor 用元素自身事件时间、GlobalWindows+evictor 状态有界）、适用域（Delta=bean；session×evictor 合并由 assigner 层承担）
- [x] roadmap header v6 版本注记（evictorId 放行 + WI5 Deferred trigger×evictor 收口）
- [x] flow+runtime 回归 + 独立 closure audit

Exit Criteria:

- [x] 矩阵行落地与实现一致
- [x] 回归全绿
- [x] closure audit APPROVE 且证据入 plan

## Closure Gates

- [x] evictorId 双处声明面放行且接线（类型断言 + 物理驱逐 E2E 双证）
- [x] 目录语法落位（count:N/time:<dur>/bean；Delta=bean 文档化）
- [x] SPI 时序契约显式化（矩阵行）且与实现一致
- [x] trigger×evictor Deferred 收口（roadmap v6 注记）
- [x] 受影响 owner docs 已同步（矩阵表 A）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw test -pl nop-stream/nop-stream-flow,nop-stream/nop-stream-runtime -am` 通过
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2352-wi6-evictor-declaration.md --strict` 退出码 0
- [x] roadmap Work Item Status 的 WI6 checkbox 已勾选（audit 通过后）

## Deferred But Adjudicated

### session×evictor 合并行为专项验证

- Classification: `watch-only residual`
- Why Not Blocking Closure: Evictor SPI 无 merge 面（侦查实证零命中）；声明层不设限制；合并语义由 assigner 层（MergingWindowSet）承担，evictor 只在 fire 路径操作 pane 副本——E1-E12 场景无 session×evictor 依赖（E10 session 场景用合并断言非 evictor）
- Successor Required: no（出现真实 session×evictor 诉求时随高阶窗口语义 Follow-up 评估）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: evictorId 双处声明面放行（xdef→_gen 再生成恰 2 文件）+ resolveEvictor 目录（bean→count:N/time:<dur>→ref-unknown）+ fire 路径物理驱逐 E2E 判别 [1,3,5,7]；SPI 时序契约文档化；WI5 trigger×evictor Deferred 收口（roadmap v6 注记）。独立 closure audit APPROVE（0B/0M/4 Minor 已修或如实记档）。
Completed: 2026-10-09

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_c8f204c6-1b1f-4b89-8f23-779b3b95221a（fresh session，未参与实现）
- Audit Session: agent_c8f204c6-1b1f-4b89-8f23-779b3b95221a
- Evidence:
  - Phase 1：xdef 双处声明落地；_gen 漂移恰 2 文件且含 evictorId 访问器（审计 grep 实证）；resolveEvictor 逐点核验（无 depth、bean 优先、正参守卫、ref-unknown）；getEvictor 访问器存在
  - Phase 2：TestEvictorDeclaration 6/6 + TestEvictorCountE2E 1/1（audit 自行运行）；E2E 判别性成立（无 evictor 反事实 [1,3,6,10]；write-back 维度由 TestEvictorStateLifecycle 既有钉子承担——audit nit 4 已记档）
  - Phase 3：矩阵表 A 行逐点与实现一致（TimeEvictor 元素自身事件时间/物理写回/pre-eviction 计数/emit=UPDATE 可共存/session×evictor 由 assigner 层承担）；roadmap v6 注记在位
  - 回归：flow 246 + runtime 1346 全绿（audit 独立复跑；3 个 checkpoint/2PC E2E 全量时序抖动为 WI3-WI6 已知形态，隔离 12/12 绿）
  - core Evictor SPI 零变更（diff 仅 WindowedStreamImpl +8 行访问器）
  - 门禁：doc-links 0；plan-checklist 0
  - Deferred 项分类检查：session×evictor 专项验证为显式裁定 residual（SPI 无 merge 面 + 场景无依赖），非静默降级

Follow-up:

- no remaining plan-owned work
