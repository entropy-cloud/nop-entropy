# 17 WI15 设计文档产出

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: `ai-dev/backlog/nop-stream-sql-roadmap.md`（WI15 行、Purpose 门控表）
> Related: `ai-dev/plans/nop-stream-sql/13-wi6-union-multi-input.md`、`ai-dev/plans/nop-stream-sql/16-wi8d-parameterized-join.md`
> Owner: 仓库 owner（2026-10-02 执行指令委托）

## Purpose

在 ai-dev/design/nop-stream/ 下产出 SQL 子系统实现所需的四份设计文档（SQL 编译契约、多输入模型、join 算子、参数化面），各含 index 层与实现层分离，并被 WI8a、WI12、WI13、WI17 引用——为 Phase 4 算子与 Phase 5 编译器的实现提供设计依据。

## Current Baseline

- **已存在并覆盖部分义务**：sql-compiler-contract.md（D7/D8/D13 落档 + WI8c 回改注记）、sql-landing-decision.md（D14 落档 + IJoinResolver 作废注记）、sql-subset-and-semantics.md（D1/D3/D4/D5/D6/D15）、window-failfast-decisions.md（D9-D12）、sql-window-dialect-matrix.md（WI3）、sql-vision-conflict-resolution.md（D2）——裁定层文档齐备。
- **缺口（WI15 完成判定要求的四份中两份半）**：(1) 多输入模型设计——WI6 的 union 顶点/平行边/IdentityHashMap 键控/gate 配置语义散在 graph-model-design.md 注记与 WI6 plan，无独立 index 文档；(2) join 算子设计——WI13 将实现的 hash join/window join/有序缓冲消费，仅有 roadmap 行内约束与 WI8d 声明面，无设计文档；(3) 参数化面设计——WI8b/c/d 的 schemas/aggregators/joins 三注册表 + SPI 消费 + 模块发现布局，散在两份 plan 与 contract 注记，无汇总 index。
- **设计文档纪律**（plan guide 规则 14）：只记录最终设计状态与决策理由，不写 Proposed-vs-Current、不写类签名清单（源码是代码层唯一事实）、不写演进叙事。
- 引用义务：WI8a（已 done，引用 sql-landing-decision）、WI12/WI13/WI17（todo，其 plan 起草时引用新文档）——「被引用」的证明=文档存在且内容覆盖对应 WI 的设计面； WI12/13/17 起草时按此路由。
- design 文档惯例（ai-dev/design/00-design-writing-guide.md）：写前必读。

## Goals

- 新增三份设计文档（index 层 + 实现层分离，每份含「本文覆盖什么/读者是谁/实现锚点 file:line」头块）：
  1. `multi-input-model.md`——union 顶点模型（真实执行顶点/StreamUnionOperator 语义/平行边与顶点对去重边界/IdentityHashMap matrix 键控/gate 配置一致性语义/约束 4 二输入上界/约束 3 数据元素不溯源的裁定与 join 的 keyBy-after-union 形态）。
  2. `join-operator.md`——WI13 实现依据：join 声明面消费（joins/joinRef 校验后契约）、hash join 形态（union 后 keyBy + process + keyed state）、window join 形态（windowStrategyRef + timeout）、WI12 有序缓冲复用义务、A6 parallelism 假设验证义务、JoinType.isOuter 消费断言、FULL 窗口 join 评估项。
  3. `parameterized-declarations.md`——参数化声明面总纲：schemas（WI8b）/aggregators（WI8c）/joins（WI8d）三注册表形状与校验义务、SPI 消费模式（IAggregatorFunctionResolver + 模块 app-beans 自动装配）、模块发现双 _module 布局的机制约束（两段 `*/*/_module` + moduleId 无连字符往返）、D13 回改裁定引用。
- `sql-compiler-contract.md` 补「文档族 index」小节：列出 SQL 子系统全部设计文档及其覆盖面（含三份新文档），作为 WI17 起草时的路由入口。
- 引用核对：WI12/WI13/WI17 的设计依赖在新文档中各有明确章节可指。

## Non-Goals

- 不实现任何代码；不重写既有裁定文档（sql-compiler-contract 等保持原样，仅补 index 节）；不写 Proposed/演进叙事（规则 14）；不做 docs-for-ai 用户文档（归 WI18）。

## Scope

### In Scope

- 三份新设计文档 + sql-compiler-contract.md index 节 + 当日日志

### Out Of Scope

- 代码实现；用户文档；既有裁定文档内容改写。

## Execution Plan

### Phase 1 - 三份设计文档

Status: completed
Targets: `ai-dev/design/nop-stream/`

- Item Types: `Proof`

- [x] 读 ai-dev/design/00-design-writing-guide.md 并按其纪律撰写
- [x] multi-input-model.md：WI6 落地设计的最终状态汇总（含实现锚点）
- [x] join-operator.md：WI13 实现设计（声明面消费契约 + 双形态 + 复用义务 + 评估项）
- [x] parameterized-declarations.md：三注册表 + SPI + 模块布局总纲
- [x] sql-compiler-contract.md 补文档族 index 节（§0，九份文档路由表）

Exit Criteria:

- [x] 三份文档各含 index 层（覆盖面/读者/实现锚点）与实现层分离结构，无 Proposed/演进叙事
- [x] 文档内容与 live 代码一致（锚点为 WI6/8c/8d 已 audit 实现的类/文件，audit 抽查复核）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] ai-dev/logs/ 当日条目已更新

### Phase 2 - 收口

Status: completed
Targets: plan 与 roadmap

- Item Types: `Proof`

- [x] 独立子 agent closure audit（不同 task_id）：判定 PASS——19 处锚点实测一致（超 6 处下限）、结构纪律合规、路由完整性可指、四份义务文档齐备性成立、引用义务按「产出可被引用的设计」语义达成；证据落 ai-dev/audits/nop-stream-sql/wi15-closure-audit.md
- [x] audit 通过后 roadmap WI15 `todo` → `done`（括注单层无嵌套）；`parseRoadmapMarkdown` 复核 31 工作项 + 7 里程碑、17 done、无静默丢弃
- [x] Plan Status → `completed`；check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

Exit Criteria:

- [x] 独立 audit 证据落档两处
- [x] roadmap WI15 = done + 解析器 31 + 7 复核通过
- [x] check-plan-checklist --strict 退出码 0；check-doc-links --strict 退出码 0

## Closure Gates

- [x] 四份义务文档齐备（sql-compiler-contract 既有 + 三份新增）且 index/实现分层
- [x] 多输入模型文档覆盖 WI6 全部裁定（真实顶点/平行边/键控/gate 语义/约束 3/4 处置）
- [x] join-operator 文档覆盖 WI13 全部设计依赖（双形态/有序缓冲复用/A6/isOuter/FULL 评估）
- [x] 参数化面文档覆盖三注册表 + SPI + 模块布局机制
- [x] sql-compiler-contract 文档族 index 指向全部 SQL 子系统设计文档
- [x] 文档内容与 live 代码一致（audit 19 处锚点实测）
- [x] 独立子 agent closure-audit 已完成并记录证据（不同 task_id，判定 PASS）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/17-wi15-design-docs.md --strict` 退出码 0
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0

## Closure

Status Note: 四份设计文档义务达成——SQL 编译契约面由既有裁定文档承载（重写违反 guide 规则 14），新增三份汇总文档（multi-input-model/join-operator/parameterized-declarations）各有头块三层结构与实测锚点，contract §0 文档族 index 作为 WI17 路由入口。独立 closure audit 判定 PASS（19 处锚点实测一致，3 项 Minor 非阻塞）。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session）
- Audit Session: 证据落档 ai-dev/audits/nop-stream-sql/wi15-closure-audit.md（判定 PASS）
- Evidence:
  - 19 处锚点 live 一致（UnionTransformation/八项校验/三注册表/SPI 全序/双 _module 布局/九类型名）
  - 结构纪律合规（无 Proposed/演进叙事/类签名展开）
  - 路由完整性：WI12→join-operator §5、WI13→join-operator 全档、WI17→contract §0 + parameterized §2/§5
  - 门禁：doc-links strict 0、roadmap 解析 31+7、零代码变更（git status 实证）
- `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-stream-sql/17-wi15-design-docs.md --strict` 退出码 0

Follow-up:

- no remaining plan-owned work；M-1 design README 补录为可选项
