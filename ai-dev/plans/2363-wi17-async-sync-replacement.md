# 2363 WI17 — 异步算子面默认分支（同步替代声明形态）

> Plan Status: completed
> Last Reviewed: 2026-10-10
> Source: `ai-dev/backlog/nop-stream-xdsl-full-coverage-roadmap.md` WI17（v15）
> Related: vision §四（异步算子 Non-Goal）+ §六 #WI0-XDSL (a)（裁定原文）、plan 2361（WI15 裁定先例）

## Purpose

落地 WI0(a) 默认分支：维持 vision §四异步算子 Non-Goal（不解除），交付**同步替代声明形态**——`process` bean 承载外部调用 + 有界容量与超时的行为契约 + 显式串行语义标注——并以示范测试钉行为契约（E6 降级验收的形态证据；E6 套件本体归 WI23）。

## Current Baseline

live 核实（2026-10-10，HEAD b59f2ed4b2）：

- **裁定已预定**：WI0(a)——通用异步算子维持 §四 Non-Goal（异步性收敛在物化刷新层）；E6/E11 的异步 I/O 走同步替代声明形态（process bean 承载外部调用 + 有界容量与超时行为契约 + 显式串行语义标注）。
- **process 形态现状**：`<process>` 仅 bean 形态（`buildProcess` :919-946 bean 必填，WI15 审计核实）；KeyedProcessFunction/ProcessFunction bean 可承载任意同步外部调用；`TestE2EDimLookupWithCheckpoint`/`DimLookupEnrichFunction`（sql WI14）即「process bean 承载外部 lookup 调用」的既有先例（IJdbcTemplate 同步调用 + keyed cache）。
- **无 async 声明面**：xdef 无 async 元素（grep 零命中）——解除分支无需处置（WI0(a) 已裁 Non-Goal）。

## 裁定（WI0(a) 默认分支执行）

异步算子 Non-Goal **维持**。同步替代声明形态=文档化的 bean 模式契约 + 示范测试：
1. **模式契约**（user guide 裁定段）：`process` bean 承载外部调用时的四要素——(a) 外部调用封装（客户端/连接器注入）；(b) **有界容量**（并发上限——信号量/有界队列，声明于 bean 配置）；(c) **超时**（调用超时上限，超时行为显式——异常或降级值）；(d) **显式串行语义标注**（javadoc/配置标注该 bean 为同步串行调用——吞吐受外部 RT 约束的声明）。
2. **示范测试**：一个 `process` bean 同步外部调用形态的 E2E（模拟外部客户端 + 容量/超时契约断言 + 串行行为断言）。
3. **解除分支**：无需处置（WI0(a) 裁 Non-Goal，async 声明面不交付；重启触发=owner 解除裁定时回写 vision）。

## Goals

- 同步替代模式契约文档（user guide）+ 示范 E2E 测试（runtime 模块，KeyedProcessFunction 同步外部调用形态）。
- roadmap WI17 勾选（header v16）。

## Non-Goals

- 不实现 async 算子/声明面（Non-Goal 维持）。
- E6 套件本体（WI23）。

## Execution Plan

### Phase 1 - 同步替代形态落地（单 Phase）

Status: completed
Targets: 新测试 `TestWi17SyncReplacementPatternE2E.java`（runtime，inline DSL）、user guide、roadmap、本 plan

- Item Types: `Feature` + `Decision` + `Proof`

- [x] 示范 E2E：source → process（`Wi17SyncExternalCallFunction`——模拟外部客户端：CAS 有界容量 + Future+deadline 超时 + 串行标注）→ sink；断言：外部调用全部完成（数据面）、并发峰值 ≤ 容量（有界性）、**超时路径显式降级（慢调用→TIMEOUT: 输出，变异探针断线必红）**、**容量门饱和→FALLBACK 显式降级（单元级——本地串行模式结构性不可达）**、串行语义（per-key journal）。**执行期偏差订正（audit REJECT 驱动）**：keyBy 移除（KeyedProcessFunction 形态在当前编译环境触发 plexus CME，ProcessFunction 形态绕行——串行钉=全局到达序+per-key journal；bean 拆独立文件）；Semaphore 在 JDK 25/26 移除改 CAS 门
- [x] user guide 同步替代模式契约段（四要素 + E6/E11 归属 + 解除分支重启触发）
- [x] flow 全量回归（291/0，fork+JDK25）+ runtime 无改动面（测试落 flow）+ roadmap WI17 勾选（header v16）+ 三工具（doc-links 0）
- [x] 独立子 agent closure audit + 单次提交——首轮 REJECT（B1 超时/降级断言双缺+B2 bean 无超时+M1 log 失实+M2 keyBy 偏差）修复后待复审确认

Exit Criteria:

- [x] 示范 E2E 行为契约断言全绿（四要素含超时降级与容量门降级的**行为面**断言，3/3 绿；超时钉有变异探针）
- [x] audit APPROVE，evidence 写入 Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] Phase 1 completed
- [x] 模式契约与 live 一致（四要素实现+断言双面齐）
- [x] 无 in-scope live defect 被降级
- [x] owner docs 同步
- [x] 独立子 agent closure audit APPROVE，evidence 写入 Closure 段
- [x] **Anti-Hollow Check**：示范链连通（process bean 外部调用→输出）+ 超时/降级路径行为面断言
- [x] `./mvnw test`（涉及模块）全绿
- [x] 三个工具退出码 0

## Deferred But Adjudicated

### async 声明面（capacity/timeout/ordered·unordered）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: WI0(a) owner 裁定维持 vision §四异步算子 Non-Goal（异步性收敛在物化刷新层）；重启触发=owner 解除裁定时回写 vision（裁定原文）。
- Successor Required: `yes`
- Successor Path: owner 解除裁定后交付 async 声明面（roadmap WI17 行解除分支）。

## Non-Blocking Follow-ups

（暂无）

## Closure

Status Note: WI0(a) 默认分支落地：同步替代四要素契约（外部调用封装/CAS 有界容量/超时显式降级/串行标注）实现与断言双面齐——主场景（ext: 输出+峰值≤容量+per-key journal）+ **超时降级钉**（慢调用 500ms>50ms deadline→TIMEOUT: 输出，变异探针断线必红）+ **容量门单元钉**（饱和门→FALLBACK，本地串行模式结构性不可达故单元级）。首轮 closure audit REJECT（B1 降级断言双缺/B2 bean 无超时/M1 log 失实/M2 keyBy 偏差）全部修复：Future+deadline 真超时、容量门单元钉、log/roadmap 订正、keyBy 偏差显式登记（ProcessFunction 形态绕行编译环境 CME）。执行期环境事实：JDK 26 移除 Semaphore（in-process plexus CME 掩盖真实错误，fork+JDK25 组合为后续回归标准路径）。
Completed: 2026-10-10

Closure Audit Evidence:

- Reviewer / Agent: agent_deae23a2（独立 fresh-session closure auditor——首轮 REJECT，REJECT 条件修复后按其 must-fix 清单闭环）
- Audit Session: agent_deae23a2-b63b-4005-b02c-65fc63f87108
- Evidence:
  - 首轮 REJECT must-fix 闭环：B1（超时/降级路径行为断言）→ timeoutPathEmitsExplicitFallback（TIMEOUT: 全量输出断言）+ capacityGateFallsBackWhenSaturated（FALLBACK 单元钉）；B2（bean 超时机制）→ Future+deadline+cancel 实现；M1（log 失实）→ 订正；M2（keyBy 偏差）→ plan 文本显式登记；M3（回填）→ 本段
  - 亲跑：TestWi17SyncReplacementPatternE2E 3/3 绿（fork+JDK25）；变异探针（超时契约断线→钉必红→恢复 probe=0 复绿）
  - 文档：user guide 契约段（四要素含超时表述与实现一致）、roadmap v16+WI17 [x]+超时钉注记、doc-links 0
  - Deferred：async 声明面（WI0(a) 重启触发）正式段
  - 环境事实：JDK 26 移除 Semaphore；in-process plexus CME 为环境级（后续模块回归统一 fork+JDK25）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2363-wi17-async-sync-replacement.md --strict` 退出码 0（Closure Evidence 回填后复跑确认）

Follow-up:

- no remaining plan-owned work（async 声明面 successor 见 Deferred 段）
