# 2308 full-code-review 1.1 全仓依赖图与模块边界审计

> Plan Status: active
> Last Reviewed: 2026-10-03
> Source: `ai-dev/backlog/full-code-review-roadmap.md` 工作项 1.1（MA1）
> Related: `ai-dev/plans/2307-full-code-review-baseline-refresh.md`（0.5，已完成）、轮次目录 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/`
> Draft Review: agent_e3117a69 首轮（1 Blocker beans.xml 文件名 + 3 Major + 7 Minor，全部修复）；agent_cd0d24bc 复核 PASS（api 计数 25→26 勘误 + 本头行补齐，改后无需再审）
> Skill 适配注记：roadmap 1.1 指定 Skill 为 `ai-dev/skills/cross-module-dependency-audit-prompt.md`，该 skill 面向 ORM 数据依赖（refEntityName/机制 B/R-S-P）；本审计取其「DAG 合规性/依赖边提取/拓扑验证」方法论应用于 Maven 依赖面，severity 判级按 roadmap 通用约定改用 deep-audit 共享前缀 P0–P3。

## Purpose

执行 roadmap 工作项 1.1：全仓 Maven 依赖图与模块边界审计（root pom modules 与 module-groups.md 对齐、跨组依赖违规、循环依赖、`nop-*-api` 模块纯净性、`_vfs` beans 装配越界），产出带 P0–P3 发现的报告并存档轮次目录。**本工作项为只读审计，不改产品代码**（若发现 P0 走 roadmap「P0 即时通道」异步注入修复 plan）。

## Current Baseline

- 0.5 已完成（commit cf3c3d9c4d）：矩阵 §1 与 live 顶层组已对齐（45 组），module-groups.md 已补 nop-bytecode 行并勘正 nop-code/nop-lint 清单。
- 全仓 `pom.xml` 共 465 个（不含 target/_tmp）；平台模块 groupId 为 `io.github.entropy-cloud`（442 个，demo 系 `app.demo`×6 / `io.nop.demo`×3 除外）；顶层组 pom 数最多：nop-ai 25、nop-persistence 24、nop-demo 24、nop-network 21、nop-kernel 20。
- root pom `<modules>` 挂 47 个顶层模块（`tests` 与 `nop-commons-java21` 被注释）。
- 分层规则权威：`docs-for-ai/02-core-guides/architecture-principles.md` §二（依赖方向必须 DAG；core→dao→meta→service→web→app；跨模块经 `I*Biz` 接口）；模块职责权威：`docs-for-ai/01-repo-map/module-groups.md`。
- severity 判级统一用 `ai-dev/skills/deep-audit-prompts.md` 共享前缀 P0–P3 定义；其「常见误报校准」节（如 I*Biz 接口在 dao 模块属标准模式）在判定时必须套用。
- 绿色构建基线：2026-09-22 `./mvnw clean install -DskipTests -T 1C` BUILD SUCCESS（audit 只读，引用该基线）。
- 已知未闭包敞口（本项不重复深审，只登记与依赖/边界相关的部分）：check 系列发现无修复跟踪（归 6.4–6.9 标定）；本项只产出新的依赖图/边界面发现。

## Goals

- 产出报告 `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.1-dependency-graph-and-boundaries.md`：摘要段 + P0/P1/P2/P3 发现列表（每条含 file:line 证据与修复方向，编号 `P<级别>-1.1-<序号>`）+ 全仓组间依赖图 + 裁决。
- 覆盖五个审计面：①root pom modules 与 module-groups.md 对齐；②跨组依赖违规；③循环依赖（含组内环）；④`nop-*-api` 纯净性；⑤`_vfs` beans 装配越界。
- index.md 报告清单登记（P0/P1 计数），independent closure audit 通过后 roadmap 1.1 翻 done。

## Non-Goals

- 不修复任何发现（P0 异步注入修复 plan 除外，本 plan 不预设 P0 存在）。
- 不做 ORM `refEntityName` 级数据依赖审计（skill 中机制 B/DAG 数据面部分仅在 Maven 边判定需要时参考）。
- 不审计第三方/外部依赖版本健康（5.3 的版本健康面）。
- 不重复 6.4–6.9 的 check 系列标定。

## Scope

### In Scope

- 全部 465 个 pom.xml 的内部依赖边（`io.github.entropy-cloud` groupId）
- root pom modules、module-groups.md、architecture-principles.md 三方对齐
- Nop IoC bean 描述符实际为 `*.beans.xml`（全仓 274 个：src/main 165（其中 48 个 `_` 前缀生成物）、src/test 106）；本项扫描 `**/src/main/resources/_vfs/**/*.beans.xml`（src/test fixture 排除，仅异常时单列参考）；`_*.beans.xml` 属生成物，发现须追溯到模型/模板层（deep-audit 误报校准第 3 条）

### Out Of Scope

- 产品代码行为审计（归 1.2–3.x 各专项）
- 第三方依赖（pom 中非 io.github.entropy-cloud 的 dependency）

## Execution Plan

### Phase 1 - 机械扫描：依赖图构建与环检测（子 agent）

Status: completed
Targets: 全仓 pom.xml；扫描脚本与中间产物落 `_tmp/`

- Item Types: `Proof`

- [x] 动手前基线确认：`git status` 干净（无在途变更）后才开始扫描
- [x] 派发只读子 agent：解析全部 pom.xml，提取平台内部依赖边（module → module），**边记录携带 scope/type/optional**，输出节点/边全集清单（`_tmp/fcr-1.1-dep-edges.txt`）；节点全集含 reactor 与非 reactor（tests/demo 系），逐节点标注是否属于 root pom reactor 47 模块
- [x] 环检测：SCC 枚举强连通分量输出具体环路径，组间环与组内环分别列出；**环检测与后续违规判定默认限定 root pom reactor 47 模块**（非 reactor 节点显式标注、不计入违规发现）；`dependencyManagement`/BOM 管理面不构成真实依赖边，需排除
- [x] `*-api` 模块纯净性检查：选样口径 = 仓内 pom 自身 artifactId 以 `-api` 结尾的模块（实测 26 个，含 nop-graph-api/nop-tcc-api/nop-http-api/nop-rpc-api/nop-biz-auth-api/demo-ddd-api 等变体；排除依赖坐标级的第三方 `-api` 如 slf4j-api）；凡 api 模块依赖平台**实现**模块即记为污染候选；api → 平台基座（nop-api-core/nop-commons/nop-core/nop-xlang/nop-markdown，以 deep-audit 共享前缀校准清单为准）= 合法基座依赖，逐个标注依赖目标与用途
- [x] 跨组依赖边全集按顶层组聚合输出（供 Phase 2 语义判定）
- [x] 交叉验证：边总数用两种独立口径（如 DOM 解析 vs 正则）核对一致后才采信（deep-audit 共享前缀计数口径规则）

Exit Criteria:

- [x] 边清单、环清单、api 污染候选清单、跨组聚合边清单四份产物存在于 `_tmp/` 且两口径边总数一致
- [x] No owner-doc update required（机械扫描不改变 live baseline）
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 1 事实

### Phase 2 - 语义判定：违规分类与装配越界（主 agent + 子 agent）

Status: completed
Targets: Phase 1 产物 + `docs-for-ai/01-repo-map/module-groups.md` + `docs-for-ai/02-core-guides/architecture-principles.md` + `**/src/main/resources/_vfs/**/*.beans.xml`

- Item Types: `Proof`

- [x] root pom modules ↔ module-groups.md 逐组对齐核对（含 0.5 后的 45 组口径；不一致处登记为发现候选并区分「文档欠账」与「幻影模块」）
- [x] 跨组依赖边逐组判定：对照 module-groups.md 声明职责与 architecture-principles 分层规则，分类为 合规 / 违规（注明违反的规则条款） / 存疑（需人工裁定）；套用 deep-audit 误报校准（如 api→api 依赖、test scope 依赖不参与运行时边）
- [x] `*-api` 污染候选逐个复核：三分类「合法（api→api 或 api→平台基座）」/「违规（api→实现模块）」/「存疑」，违规 P1 起判
- [x] beans 装配越界扫描（文件模式 `**/src/main/resources/_vfs/**/*.beans.xml`）：提取 `<bean class="X">` 与 `<ioc:config class="...">` 两类类绑定，class 包名与所在模块归属不符的清单（app/web/装配模块引用业务实现属合法装配；库/内核模块硬编码注册其他组业务实现 bean 为越界候选）；全量机械清单落 `_tmp/` + 越界/存疑条目抽样人工判定；`_*.beans.xml` 命中的发现标注「须追溯生成源」
- [x] 组内循环依赖若存在，评估是否被 spring-maven-plugin/flatten 等构建手段豁免（如有则记录豁免机制）

Exit Criteria:

- [x] 每条违规/存疑边有规则条款引用与 file:line（pom 坐标）证据
- [x] api 纯净性结论有全量清单支撑（非抽样）
- [x] beans 装配越界全量机械清单与抽样判定结论已落盘 `_tmp/`，越界/存疑条目有 file:line（beans.xml 坐标）证据
- [x] 本 Phase 为只读判定：No owner-doc update required（root pom ↔ module-groups.md 不一致处仅作为发现进报告，不在本 plan 内改文档）
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 2 事实

### Phase 3 - 报告编制与 severity 判级

Status: completed
Targets: `ai-dev/audits/2026-09/2026-09-22-2020-code-review-full/1.1-dependency-graph-and-boundaries.md`

- Item Types: `Proof`

- [x] 按 deep-audit 共享前缀 P0–P3 定义逐条判级；发现编号 `P<级别>-1.1-<序号>`；每条含 现状/风险/建议 + file:line
- [x] 报告含：摘要段、组间依赖图（ASCII/文本）、五审计面结论、P0–P3 发现清单、裁决（通过/失败）
- [x] P0 即时通道：若判出 P0，当即异步注入修复 plan（`ai-dev/plans/NN-<描述>.md`，标题注明 finding ID）并在 index「P0 发现追踪」表登记
- [x] 报告内 P2/P3 按 roadmap 规则 #3 标注去向（P2 仅显式开辟裁决例外时进台账；P3 watch-only）

Exit Criteria:

- [x] 报告落盘且每条发现可由 file:line 复现；P0/P1 计数与发现清单一致
- [x] 如有 P0：修复 plan 已注入并登记 index
- [x] No owner-doc update required（报告本身即审计产物；owner doc 修正归 Wave 2 修复项）
- [x] `ai-dev/logs/2026/10-03.md` 已追加 Phase 3 事实

### Phase 4 - index 登记、独立 closure audit 与收口

Status: completed
Targets: index.md、roadmap、本 plan

- Item Types: `Proof`

- [x] index.md 报告清单登记 1.1 行（报告文件、P0/P1/P2/P3 计数、状态）
- [x] 派发独立子 agent closure audit（fresh session）：验证证据完整性（每条发现 file:line 真实可复现）、severity 标定符合 deep-audit 共享前缀、**index 登记的 P0/P1 计数与报告发现清单一一对应**、roadmap 1.1 状态回填前置条件成立
- [x] audit 通过后 roadmap 1.1 翻 `done`；本 plan 按 guide 关闭（checklist 工具 + Closure Evidence 写入）
- [x] commit（一次）

Exit Criteria:

- [x] index/roadmap/log 三处一致；closure audit 证据写入本 plan Closure 段
- [x] No owner-doc update required（1.1 为只读审计；如发现文档欠账，以 finding 形式进报告供 R1.0 展开）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出码 0

## Closure Gates

> 只读审计计划（无产品代码变更），按 plan guide 免除 `./mvnw` 构建验证项；引用 2026-09-22 绿色基线。审计纪律：不改产品代码（P0 即时通道例外，且走异步注入 plan，不在本 plan 内改代码）。

- [x] 报告 `1.1-dependency-graph-and-boundaries.md` 落盘，五审计面全部覆盖
- [x] 每条发现附 file:line 证据与修复方向，severity 判级依据 deep-audit 共享前缀
- [x] index 登记的 P0/P1 计数与报告发现清单一一对应
- [x] 独立子 agent closure audit 通过并留痕（本 plan Closure 段 + index）
- [x] roadmap 1.1 状态回填 `done`；`ai-dev/logs/2026/10-03.md` 收口记录已写
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 不存在被静默降级的 in-scope 项

## Deferred But Adjudicated

（无——1.1 范围五审计面全部 in scope）

## Non-Blocking Follow-ups

- 组内依赖的「构建豁免机制」（若 Phase 2 发现）如需深挖，归入后续相应模块组的深审工作项，不在 1.1 展开。

## Closure

Status Note: 1.1 五审计面全部完成，报告落盘（P0=0/P1=0/P2=2/P3=3，裁决通过）；初版 P2-1.1-003 经独立 closure audit 证伪撤回并登记方法学注记；P2×2（含 2 条 P1 再裁候选标注）进入 R1.0 输入；审计期间未改任何产品代码，P0 即时通道未触发。
Completed: 2026-10-03

Closure Audit Evidence:

- Reviewer / Agent: agent_2dfe253d-2e0b-4bd0-b636-fcb8285d8b85（独立 fresh-session closure auditor，只读，四轮）
- Evidence:
  - 第一轮 FAIL→修复：P2-1.1-003「INopBatch*Biz 双模块重复定义」被实证证伪（接口仅定义于 nop-batch-dao；AMBIGUOUS=4 系扫描器包名归属假象），报告撤回并登记方法学注记；P2-1.1-002 口径限定 13 组并补扩展面清单；2 处 Minor 路径勘正
  - 第二轮 FAIL→修复：裁决段 P2×2 同步（003 不进 R1.0）；P2-1.1-002 账目改为审计员独立复算分解（49=21+28=13 实现级+15 契约面），清单补 wf-scheduler→job-local:26、job-retry-adapter→retry-engine:21
  - 第三轮残留→修复 + 第四轮终核 **PASS**：标题/面②/摘要/log 计数 4 处替换逐一实证；报告计数链自洽（13+9 口径内外、49=21+28=13+15、P2=2/P3=3）
  - 四项验收标准：证据完整性（发现坐标逐条实证）、severity 标定（deep-audit 共享前缀对照）、引用路径真实性（pom/beans file:line）、index 计数对应（0/0/2/3 与发现清单一一对应）——全部 PASS
  - `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`node ai-dev/tools/check-plan-checklist.mjs` 退出码 0
  - 只读审计：`git status` 无产品代码变更；引用 2026-09-22 绿色基线
