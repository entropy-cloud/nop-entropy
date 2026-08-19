# W3-supplement 按定稿设计回填阶段二 work items + mission.json 更新

> Plan Status: completed
> Last Reviewed: 2026-08-19
> Mission: xlang-execution-optimization
> Work Item: W3-supplement
> Source: ai-dev/backlog/xlang-execution-optimization-roadmap.md（W3-supplement 条目）
> Related: 2026-08-19-2050-2-w2-design-review-gate.md（前置）、2026-08-19-2050-1-w1-design-docs.md

## Purpose

按 W2-review PASS 后定稿的三组设计文档，回填 roadmap 阶段二实现 work items（I1-I7 的增删拆并、验收标准与依赖定稿），并裁定 `missions/xlang-execution-optimization.json` commands 纳入新模块的方式，使 roadmap 达到可提交 W4-audit 的状态。

## Current Baseline

- 前置条件：W2-review 已 `done`（设计定稿且 PASS）。若 W2 未完成，本 plan 不得启动（各 Phase 保持 `planned`，plan 挂起等待）
- roadmap 阶段二 I1-I7 为预列占位（条目自带"占位，W3 定稿"标记），验收标准未定稿；Stages 表第 5-11 行与 Dependency graph 为占位对应的预列版本
- `missions/xlang-execution-optimization.json` 的 commands 目前仅覆盖 `:nop-xlang`（test/build/lint/typecheck 均为 `-pl :nop-xlang -am`），未含新模块
- 新模块 `nop-kernel/nop-xlang-java`、`nop-kernel/nop-xlang-truffle` 尚不存在（I1/I3 才创建）
- W4-audit 的审核口径（roadmap 条目原文）：粒度（单 plan 可完成，5-15 文件/200-500 行/1-4 phases）、依赖图无环且与 stage 表一致、验收标准可验证、复用标注准确、与定稿设计无冲突
- roadmap 冻结纪律 2 保持有效：W4-audit done 前 I 系列不得起草 plan——本 plan 只定稿条目文本，不解冻

## Goals

- 阶段二条目定稿：I1-I7 逐项按定稿设计核对并完成增/删/拆/并裁定，每项具备 repo-observable 验收标准（必含三后端对拍不变式断言）、明确依赖声明、粒度符合 W4 口径
- roadmap 内部自洽：Work Items 阶段二块、Stages 表、Dependency graph 三处与定稿条目完全一致，依赖图无环
- mission.json commands 纳入新模块的方式落定（见 Phase 2 的机制裁定），且 live commands 在当前仓库上始终可执行
- 冻结纪律原文保持有效：W4-audit done 前 I 系列仍不得起草 plan

## Non-Goals

- 不启动任何 I 系列实现（不建模块、不写代码、不改 pom）
- 不执行 W4-audit（后继 gate）
- 不修改 W1/W2 已定稿的设计文档内容（若回填过程中发现设计缺陷，记为 finding 移交，不顺手改设计）
- 不改 mission.json 的 description/roadmapPath/plansDir/prompts 等其他字段（commands 之外的字段一律不动；plansDir 当前已正确指向本 mission 计划目录）

## Scope

### In Scope

- `ai-dev/backlog/xlang-execution-optimization-roadmap.md`：阶段二 Work Items 块、Stages 表、Dependency graph 的定稿修订，以及 `W3-supplement` 条目正文的 commands 机制裁定备注（记录"模块落盘即切换"策略，消除条目字面 mandate 与执行结果的矛盾）
- `missions/xlang-execution-optimization.json`：commands 字段的机制落定（裁定与目标 commands 的归属；live commands 的实际切换发生在 I1/I3 模块落盘时）
- roadmap `W3-supplement` 条目状态同步

### Out Of Scope

- `ai-dev/design/` 三组设计文档（W2 已定稿）
- 任何代码/构建文件
- W4-audit 的执行

## Execution Plan

### Phase 1 - 阶段二 work items 按定稿设计回填

Status: completed
Targets: `ai-dev/backlog/xlang-execution-optimization-roadmap.md`（Work Items 阶段二块 + Stages 表 + Dependency graph）

- Item Types: `Decision`

- [x] 逐项核对 I1-I7 与定稿设计（三组 architecture-baseline：xlang-execution 与 xlang-java 的 `01-architecture-baseline.md`、xlang-truffle 的 `02-architecture-baseline.md`）：范围、依赖、验收标准与设计无冲突；需要增/删/拆/并的项逐条给出裁定与理由（写入 roadmap 条目或当日 log）
- [x] 每个 I 项定稿验收标准：repo-observable（指向具体模块/文件/测试的可核查结果），且必含对拍不变式断言（"同一 Executable 树多后端执行结果一致"，对齐 roadmap 纪律 3）；例外口径——I7 收口回归直接运行全量三后端对拍套件（不得以引用形式弱化），I5 选择机制以对拍框架结果为验收输入并显式说明引用关系
- [x] 每个 I 项定稿粒度：单 plan 可完成（约 5-15 文件 / 200-500 行 / 1-4 phases 量级）；超粒度项必须拆分，欠粒度项合并并更新依赖
- [x] 复用标注与定稿设计的复用先例一致（`ScriptCompilerRegistry`、`ResourceComponentManager`、`nop-javac`/janino EvalMethod、`GraalvmConfigGenerator`、SL 参考实现等，锚点以 live repo 为准）
- [x] 同步 Stages 表（第 5-11 行）与 Dependency graph（mermaid）：条目、依赖、关键路径与定稿后的 Work Items 一致，依赖图无环

Exit Criteria:

- [x] 阶段二每个条目（定稿后的全集）含定稿验收标准 + 依赖声明，且逐项核对记录（与设计无冲突）存在于 roadmap 或当日 log
- [x] 每个条目验收标准含对拍不变式断言（例外口径与执行项一致：I7 直接运行全量三后端对拍套件，I5 显式说明对拍结果的引用关系）
- [x] Work Items 阶段二块、Stages 表、Dependency graph 三处一致：从 mermaid 图遍历可复现 Stages 表的依赖列，且无环
- [x] 定稿条目均保留"占位，W3 定稿"标记的移除（即占位字样清除，改为定稿态），且定稿后全部 I 条目状态保持 `todo`（不起草 plan、不标 `planned`——`planned` 语义是"已有通过 review 的 plan"，阶段二冻结下标 `planned` 等于变相解冻；解冻点仍是 W4-audit done）
- [x] 冻结纪律 2 原文保持有效（W4-audit done 前 I 系列不得起草 plan）
- [x] `ai-dev/logs/` 当日条目已更新

### Phase 2 - mission.json commands 裁定与收口

Status: completed
Targets: `missions/xlang-execution-optimization.json`、`ai-dev/backlog/xlang-execution-optimization-roadmap.md`

- Item Types: `Decision` | `Proof`

- [x] 机制裁定（Decision）：Maven `-pl :<module>` 对尚不存在的模块会直接报错，因此在 I1/I3 落盘前把新模块写进 commands 会让引擎在此期间的任何构建验证失败。采用**模块落盘即切换**策略——W3 在 roadmap 对应 I 条目的验收标准中写明目标 commands（I1 时切换为含 `:nop-xlang-java`，I3 时追加 `:nop-xlang-truffle`，I5/I7 汇总口径），由创建模块的 plan 在模块落盘的同一次变更中完成 mission.json 切换
- [x] 按裁定落盘：目标 commands 写入 roadmap 对应 I 条目验收标准；mission.json 当前 commands 保持仅引用已存在模块（若需调整格式保持语义不变）
- [x] roadmap `W3-supplement` 条目正文追加裁定备注：commands 纳入新模块采用"模块落盘即切换"，目标 commands 与切换时机落在对应 I 条目验收标准——使条目字面 mandate（"同步更新 commands 纳入新模块"）与执行结果不再矛盾
- [x] roadmap `W3-supplement` 状态与 plan 状态一致（closure audit 通过后 `done`）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过

Exit Criteria:

- [x] `missions/xlang-execution-optimization.json` 为合法 JSON，commands 引用的 `-pl` 模块全部存在于当前聚合 reactor（与 `nop-kernel/pom.xml` 及根 `pom.xml` 的 modules 核对一致），即 live commands 可执行
- [x] 目标 commands（含 `nop-xlang-java` / `nop-xlang-truffle`）与切换时机已写入 roadmap 对应 I 条目的验收标准，链接/引用可核对
- [x] roadmap `W3-supplement` 条目正文已记录 commands 切换裁定，条目 mandate 与 live 实态（commands 仅含已存在模块）不再矛盾
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-3-w3-supplement-work-items.md --strict` 退出码 0（closure 时执行）
- [x] `ai-dev/logs/` 当日条目已更新

## Closure Gates

> 纯文档/配置计划：`./mvnw test` / `./mvnw compile` 等构建验证条目按 guide 规则移除（JSON 合法性用 `python3 -m json.tool` 或等价方式验证）。

- [x] 阶段二条目全部定稿（无占位标记残留），验收标准 repo-observable 且含对拍不变式（或显式引用关系）
- [x] 条目粒度符合 W4 审核口径（单 plan 可完成），依赖图无环且与 Stages 表一致
- [x] 复用标注与定稿设计及 live repo 锚点一致
- [x] mission.json 合法且当前 commands 可执行；目标 commands 与切换时机已落入 roadmap 条目
- [x] 冻结纪律 2 保持有效（I 系列起草解冻点仍为 W4-audit done）
- [x] 回填过程中发现的设计缺陷已记为 finding 移交（未顺手修改 W2 定稿的设计文档）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/xlang-execution-optimization/2026-08-19-2050-3-w3-supplement-work-items.md --strict` 退出码 0（closure 时执行）
- [x] roadmap `W3-supplement` 状态与 plan 状态一致
- [x] No docs-for-ai update required: 本 plan 仅改 roadmap 与 mission 配置
- [x] 独立子 agent closure audit 已完成且证据写入下方 Closure 段
- [x] `ai-dev/logs/` 收口条目已记录

## Deferred But Adjudicated

（无——commands 的"纳入新模块"不是延期，而是以切换时机裁定的方式在本 plan 内完成归属）

## Non-Blocking Follow-ups

- 无（W4-audit 是 roadmap 既定后继 gate）

## Closure

Status Note: 纯文档/配置计划（roadmap 回填 + mission.json 机制裁定，无代码变更，构建验证条目按 guide 纯文档规则移除）。两 Phase 全部完成且逐项勾选；独立 closure audit 判定 CAN CLOSE 后收口：roadmap W3-supplement `planned`→`done`、Plan Status→`completed`。W4-audit 为 roadmap 既定后继 gate。
Completed: 2026-08-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session，read-only）`ses_fe550bd24ffeNtgaF26CPk7pO7`
- Evidence:
  - Phase 1 Exit Criteria：A1-A5 全 PASS——I1-I12 定稿无占位残留、全部 `todo` 且依赖显式（roadmap L44-85）；每条验收含对拍不变式，I12 直接运行全量套件（L84）、I9 显式引用 I1 框架结果（L72）；Work Items/Stages 表（L131-142）/mermaid（L151-156）三处依赖逐边一致且为 DAG；冻结纪律 2 原文有效（L163）；当日 log 裁定记录完整（ai-dev/logs/2026/08-19.md 顶部条目：拆并裁定/无冲突核对/复用锚点 live 核对/17 项 handover 处置/finding 移交）
  - Phase 2 Exit Criteria：B1-B4 全 PASS——mission.json `python3 -m json.tool` 合法，commands 仅 `-pl :nop-xlang`（nop-kernel/pom.xml:462 + 根 pom.xml:537 reactor 内可执行）；目标 commands 与切换时机已写入 I2（L50）/I5（L60）/I12（L83）验收；W3 条目正文含"模块落盘即切换"裁定备注（L30）；`check-doc-links --strict` **EXIT=0**（0 errors，2 warnings 为本 plan 对未来模块的既知前向引用）
  - Closure Gates：逐条 PASS（纯文档计划 mvn 项按 guide 移除；scan-hollow N/A——无代码变更）；设计文档零修改（git status/diff 于 ai-dev/design/ 三目录均空）；复用表 I 引用与定稿一致（ScriptCompilerRegistry→I9 / GraalvmConfigGenerator→I11 / 依赖坐标→I5）+ 锚点 live 全在（含 EvalMethodInvoker@nop-core reflect/impl、exec/=137 文件实测）
  - `check-plan-checklist --strict` 退出码 0（closure 后复跑确认）
  - Anti-Hollow 检查：N/A（无代码/组件变更；文档链路核对=roadmap 三处依赖块+log 裁定记录+mission.json 均逐项对上）
  - Minor 3 项（W4-audit 输入，非阻塞）：① I6 无独立范围行（标题引用 I3 类别）；② I3/I4/I6/I7/I8 无复用行（内部 helper vs 平台复用区分隐式）；③ 冻结设计文档约 30 处旧 I 编号引用——已由 roadmap L42 编号映射表缓解，W4 按映射解读
  - Deferred 项分类检查：无 in-scope 缺陷被降级——finding（设计文档旧 I 编号 + guide 增补条款）均为移交项，归属见当日 log（W4-audit 按 mapping 解读 / 下次设计文档修订 / guide 修订）

Follow-up:

- no remaining plan-owned work（后继 W4-audit 由 roadmap 既定顺序接管；3 项 Minor 为 W4-audit 输入）
