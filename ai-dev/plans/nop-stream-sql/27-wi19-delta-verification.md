# 27 WI19 Delta 定制验证

> Plan Status: completed
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI19 行）、`ai-dev/design/nop-stream/00-vision.md` §八 10
> Related: `ai-dev/plans/nop-stream-sql/25-wi17-sql-compiler.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

验证编译产物（StreamSqlCompiler 产出的流模型）可被 Delta 机制覆盖定制且行为可观察地不同；显式验证 §八 10（Delta 只改模型、不 patch runtime object）。单一确定结论 + 证据。

## Current Baseline（2026-10-03 实测）

- WI17 交付 StreamSqlCompiler.compile（确定性产物）；WI13/WI17 的 E2E 证明产物可 build/execute。
- Delta 机制先例在案（flow TestStreamModelDeltaExtends）：x:extends 显式路径 + `_delta/default/` 分层两种形态，transform 级增删与边重定向有完整先例——但先例的 base 是手写模型，**编译产物作为 base 的 Delta 验证不存在**（本 WI 缺口）。
- §八 10 原文：「Delta 只能修改模型，不能 patch runtime object 来改变语义」。

## Goals

- 单一确定结论：**编译产物可被 Delta 覆盖定制**——编译产物落为模型资源，Delta 以 x:extends 引用之，增删 transform 并重定向边；合并模型结构断言 + delta 单次执行行为断言（b 行缺席证新增过滤器在编译拓扑上运行）。
- §八 10 证据：Delta 生效全程为模型层操作（xdef 校验的 XML 解析与合并）；base 与 delta 各自从全新 env 构建+执行（无 runtime object 复用/patch）；防陈旧钉——提交的编译产物资源与当前 StreamSqlCompiler 现编译输出全等（编译器演进后该测试即红，强制重生成）。

## Non-Goals

- `_delta/default/` 分层自动激活形态（x:extends 显式路径形态已足证 §八 10；分层先例既有）；运行时对象 patch 的反证测试（机制上不存在该通路——模型层全流程即证据）。

## Scope

### In Scope

- `nop-stream/nop-stream-sql`：TestDeltaOverCompiledProduct（base 资源 = 提交的编译产物；delta 资源 = x:extends 覆盖；防陈旧钉 + 双执行行为断言）
- 日志

### Out Of Scope

- WI20/22/23/24；`_delta/default/` 新用例。

## Execution Plan

### Phase 1 - Delta 覆盖编译产物验证

Status: completed
Targets: `nop-stream/nop-stream-sql`

- Item Types: `Proof`

- [x] 生成并提交编译产物资源（SELECT item, amount FROM orders WHERE amount > 0 的 compile 输出——确定性）；TestDeltaOverCompiledProduct 断言 fresh compile == 提交资源（防陈旧钉）
- [x] Delta 资源（x:extends 指向产物资源）：新增 filter（drop 某记录）+ 边重定向（join/projection 后插入、sink 前收口）
- [x] 执行断言（执行期修正——本地 runner 一 JVM 一 execute 限制使双执行不可行，改判别性等价设计）：合并模型结构断言（wi19DropB 在场 + 编译产物四 transform 存活 + e_out 重定向 + 新边）+ delta 单次执行断言（b 行缺席即证新增过滤器在编译拓扑上运行，[a=1,a=3,c=1]）；§八 10 记录——Delta 全程模型层（xdef 解析与合并），base/delta 均为全新 env 构建

Exit Criteria:

- [x] 防陈旧钉 + 执行断言绿（防陈旧钉红态：变体查询 normalize 不等——audit 独立探针复现）
- [x] sql 模块全量零退化
- [x] `ai-dev/logs/` 条目更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（fresh session）：**PASS**（2 Minor 文字措辞收口修复——plan/日志「双执行」与「base/delta 均为全新 env 构建」措辞按实况改齐）；证据落 ai-dev/audits/nop-stream-sql/wi19-closure-audit.md——三成分实证（防陈旧钉独立探针复现红态/结构断言三反事实各自必红/delta 执行逐行核过 orders 定数）
- [x] audit 通过后 roadmap WI19 `todo` → `done`；解析器断言成立：items=31/milestones=7/done=27/WI19=done
- [x] Plan Status → `completed`；check-plan-checklist --strict 0；check-doc-links --strict 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI19 = done + 解析器断言成立
- [x] 双门禁退出码 0

## Closure Gates

- [x] 编译产物 Delta 覆盖用例齐备且实跑绿（防陈旧钉 + 合并结构断言 + delta 执行判别）
- [x] §八 10 单一确定结论落档（模型层全程证据）
- [x] sql 模块全量零退化（74/74）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/27-wi19-delta-verification.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Current Baseline

（见上——gap 为编译产物作 base 的 Delta 验证不存在。）

## Closure

Status Note: 编译产物可被 Delta 覆盖定制的单一确定结论成立（防陈旧钉 + 合并结构断言 + delta 执行判别三重证据）；§八 10 模型层全程验证。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Evidence: ai-dev/audits/nop-stream-sql/wi19-closure-audit.md——PASS；sql 全量 74/74（audit 独立实跑，简报 77 系预估误差以实跑为准）；doc-links 0；check-plan-checklist 0

Follow-up:

- no remaining plan-owned work
