# I2+I3 — nop-metadata 不变式驱动审计与裁决（Invariant-Driven Audit & Adjudication）

> Plan Status: completed
> Last Reviewed: 2026-08-13
> Mission: nop-metadata-invariant-loop
> Work Item: Cycle 1 / I2（按不变式审计）+ Cycle 1 / I3（发现裁决与工作项拟制）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（I2、I3）；方法论 `ai-dev/skills/invariant-loop-audit-prompt.md`
> Related: 前置 `2026-08-13-1930-2-...`（I1 门禁 + 初始 red list 快照）；后继 I4（修复，待本计划裁决后起草）

## Purpose

用 I1 沉淀的 4 条门禁**正式审计** nop-metadata 全部 service/processor/bizmodel 方法 + ORM 模型，产出**确定性 red list**，并对**盲区做对抗性探查**（新增 processor / bizmodel、跨模块调用链的 catch 吞异常与 limit 校验漏洞）。随后对 red list **逐条裁决**：P0/P1 派发 I4 修复、新失败族派发 Cycle 2 / I1、可延期项附 non-blocking 理由。收口条件 = **裁决表零悬挂**（每条 red-list 条目都有明确归属，无"待定"）。

## Current Baseline

> 本计划依赖 I1 完成（门禁已实现 + 初始 red list 快照已记录）。I1 完成前，下列为预期基线。

- **门禁就绪**（I1 交付后）：4 条可运行门禁 —— silent-swallow 扫描器、unique-key constraint 扫描器、sensitive-literal 扫描器、limit 负值 JUnit 参数化穷举。
- **初始 red list 快照**（I1 交付）：`ai-dev/audits/nop-metadata-invariants/initial-red-list.md` 记录门禁首次运行的全部命中 + 棘轮零点。
- **预期确定性命中**：unique-key guard 预期 ≥1（缺 constraint 的那 1 个）；其余 3 族实际命中数由本计划正式跑出。
- **审计目标集**（I0 交付）：`audit-target-set.md`（方法全集 + ORM 全集），为对抗探查提供"应覆盖但未覆盖"的比对基准。
- **历史先例链**（裁决时的归类依据）：silent-swallow 族（P2-06/07/09、P2-01/02/04、AR-21）；limit 族（AR-09→AR-23④）；敏感字面量族（R6.2 P2-12→R8.2 AR-16）；unique-key 族（Lesson 09）。
- **虚假关闭先例**（裁决时的防呆）：AR-06 声称已修但 diff=0。裁决不得凭 commit message 判定"已修"，必须以门禁实测命中为准。

## Goals

- 跑 4 条门禁跨全部审计目标集，产出**正式 red list**（确定性、可复跑、含 `文件:行` + 不变式编号）。
- 对门禁覆盖盲区做**对抗性探查**：手动核查新增 processor / bizmodel、跨模块调用链上的同类失败模式，将新发现并入 red list。
- 对 red list **逐条裁决**并产出裁决表，**零悬挂**：每条要么 P0/P1→I4，要么新族→Cycle 2/I1（附触发证据），要么附 non-blocking 理由的 deferred。
- 为 I4 产出**类别清扫指令**：每族标注"修任一实例必 grep 全类兄弟"的具体 grep 范围。

## Non-Goals

- **修复违规** —— 那是 I4（本计划只产出 red list + 裁决 + 工作项描述，不动产品代码）。
- **把门禁提升为 hard CI gate** —— 那是 I5。
- **实现 Cycle 2 新族门禁** —— 本计划只"登记新族 + 派发"，不实现。
- **重新设计不变式** —— 不变式已由 I0 定稿；本计划只审计与裁决。

## Scope

### In Scope

- 正式 red list（门禁复跑结果 + 对抗探查新增）。
- 裁决表（零悬挂，每条有明确归属）。
- I4 工作项描述（每族一份"实例清单 + 类别清扫 grep 范围 + 测试要求"）。
- 新族登记（如对抗探查发现 I0 目录外的新失败族）。

### Out Of Scope

- 任何源码 / ORM 模型 / DDL 修改（I4）。
- 门禁 hard-gate 提升（I5）。
- I4 计划的详细 phase 编写（I4 计划待本计划裁决后由引擎起草）。

## Execution Plan

### Phase 1 — 门禁正式审计（Formal Red List）

Status: completed
Targets: `ai-dev/audits/nop-metadata-invariants/formal-red-list.md`

- Item Types: `Proof`

> 复跑 I1 的 4 条门禁（命令清单见 I1 Phase C），将结果汇总为正式 red list。与 I1 初始快照比对：若命中集一致则确认门禁确定性；若不一致，优先归因于 I1→I2 期间的 commit 漂移（属正常，记录涉及的 commit 即可），仅在排除漂移后仍不一致时才视为门禁非确定性缺陷并深查。

- [x] 复跑 4 条门禁，逐条记录命中（`文件:行` + 不变式编号 + 门禁名）
- [x] 将每条命中回溯到历史 audit-finding-ID（如属已知族）或标记"新发现"
- [x] 与 I1 初始 red list 快照比对：命中集一致则注明；不一致则按上述优先级归因（漂移→记录 commit；非漂移→深查门禁缺陷）
- [x] 按"族"分组 red list（4 族 + 可能的新族），便于 Phase 3 裁决与 I4 类别清扫

Exit Criteria:

- [x] `formal-red-list.md` 存在，含每条门禁的命中分节，每条命中可被独立复跑定位
- [x] 命中集与 I1 初始快照一致性已核对（一致则注明；不一致则查明并记录）→ 81=81 全一致，I1→I2 零 commit 漂移
- [x] 每条命中标注族归属与"已知/新发现"
- [x] **无静默跳过**：red list 不得人为剔除命中；全部原样记录，裁决归 Phase 2
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 盲区对抗探查（Adversarial Blind-Spot Probing）

Status: completed
Targets: `ai-dev/audits/nop-metadata-invariants/adversarial-probing-notes.md`

- Item Types: `Proof`

> 门禁覆盖不到的地方手动对抗探查（methodology: invariant-loop-audit-prompt.md 步骤 0 + open-ended 对抗审查精神）。聚焦：① 新增 processor / bizmodel（I0 之后或 I0 漏扫的）；② 跨模块调用链上的 catch 吞异常与 limit 校验漏洞；③ ORM 模型 unique-key 之外的其它 DDL 完整性盲区（如 index/外键声明与 DDL 产物漂移）。

- [x] 比对 I0 目标集与 live code：是否有 I0 漏扫的 processor/bizmodel 方法？（漏扫本身就是 I0 gap，记录并补入目标集）
- [x] 对每个族的"门禁未覆盖子模式"做手动核查（如 silent-swallow 门禁若只查 `getMessage()` 模式，手动核查"catch 后 log.warn 后继续"是否也属吞异常）
- [x] 跨模块调用链：追踪 service→core→dao 路径上是否有"上层 catch 后未传播 ErrorCode 到边界"的实例
- [x] 新发现并入 `formal-red-list.md`（标注"对抗探查新增"）→ 本轮 0 新增

Exit Criteria:

- [x] `adversarial-probing-notes.md` 存在，记录每个盲区探查方向、方法、结论（命中 or 无命中）
- [x] 探查新增项已并入 `formal-red-list.md` 并标注来源 → 本轮 0 新增（5 方向均无新族命中）
- [x] I0 目标集漏扫（如有）已记录，并产出"补扫目标集"的明确清单（反馈给 I0 或在 I4 前补齐）→ 无漏扫（全部计数口径与 I0 一致）
- [x] **无静默跳过**：探查方向不得因"看起来没问题"就跳过；每个方向必须有明确结论（命中/无命中 + 依据）
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — 裁决与工作项拟制（Adjudication & Work-Item Drafting）

Status: completed
Targets: `ai-dev/audits/nop-metadata-invariants/adjudication-table.md`

- Item Types: `Decision`

> 对 formal red list（含对抗探查新增）逐条裁决。裁决维度（来自 roadmap I3 + Anti-Slacking Rule）：P0/P1→派 I4；新族→Cycle 2/I1（附触发证据）；可延期→附 non-blocking 理由（仅允许 watch-only/optimization/out-of-scope）。收口 = 零悬挂。

- [x] 逐条裁决 formal red list 每条命中：`P0` / `P1` / `新族→Cycle2` / `deferred(non-blocking理由)`，禁止"待定"
- [x] 已确认的 live defect / contract drift 一律归 P0/P1，**不得**降级为 deferred/follow-up（Anti-Slacking + Non-Degradable Items）
- [x] 对每族产出 I4 工作项描述：实例清单 + **具体可执行的类别清扫 grep 命令**（如 `rg 'catch\s*\(' nop-metadata/nop-metadata-service/src/main/java --type java`，而非"grep 所有 processor"式泛指）+ test-first 要求 + 门禁复跑零命中验收
- [x] 对新族（如有）登记触发证据（先例链 PD-n 编号），派发 Cycle 2 / I1（Loop Rule 预授权，不需步骤 4 审查，但登记留痕）→ 本轮 0 新族
- [x] 裁决表零悬挂自检：每条 red-list 条目都有非"待定"的归属

Exit Criteria:

- [x] `adjudication-table.md` 存在，formal red list 的每条命中都有明确裁决（零"待定"）
- [x] 每族 I4 工作项描述含：实例清单 + 类别清扫 grep 范围 + test-first 要求 + 门禁复跑零命中验收点
- [x] 无已确认 live defect 被降级为 deferred/follow-up（逐条可核）
- [x] 新族（如有）已登记触发证据并派发 Cycle 2/I1 → 本轮无新族
- [x] **端到端验证**：从"门禁命中"到"裁决归属"到"I4 工作项可执行描述"链条完整，无断点
- [x] **无静默跳过**：裁决不得用"暂不处理"等模糊词；每条必须落到 P0/P1/新族/deferred-with-reason 之一
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划为审计/裁决计划（不改产品代码），故 `./mvnw test` 等构建验证可删除。保留文档链接与一致性检查。

- [x] formal red list（含对抗探查新增）完整、可复跑、与 I1 快照一致性已核对
- [x] 裁决表零悬挂（每条命中有非"待定"归属）
- [x] 每族 I4 工作项描述含：实例清单 + **具体可执行的类别清扫 grep 命令**（非泛指）+ test-first 要求 + 门禁零命中验收点
- [x] 无已确认 live defect / contract drift 被降级为 deferred/follow-up
- [x] 新族（若有）已按 Loop Rule 派发并留痕 → 本轮 0 新族
- [x] 受影响 owner docs：No owner-doc update required（裁决为过程产物；owner-doc 同步留待 I4 修复落地后）
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证（a）red list 每条命中在 live code 可定位；（b）裁决非"全部 P1 了事"式敷衍（抽查 ≥3 条裁决理由可核）；（c）对抗探查每个方向有实质结论而非"未发现问题"空话
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（产出含链接的 .md）→ 18 errors **全部 pre-existing**（INDEX.md / 各 backlog roadmap / skills，均非本计划产出）；本计划新增 3 个 `.md`（`formal-red-list.md` / `adversarial-probing-notes.md` / `adjudication-table.md`）**0 broken links**（已逐文件核对 JSON 输出）。同 I1 计划先例处理。
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（closure 前必跑，见 Minimum Rules #26）

## Deferred But Adjudicated

（本计划本身不产生 deferred；red-list 条目的 deferred 归属记录在 `adjudication-table.md` 中，每条附 non-blocking 理由。若对抗探查发现 I0 目录外新族，按 Loop Rule 派发 Cycle 2 / I1，非本计划 deferred。）

## Non-Blocking Follow-ups

- I0 目标集漏扫（如 Phase 2 发现）反馈补扫，不阻断本计划 closure（补扫可在 I4 启动前并行完成）。

## Closure

Status Note: I2+I3 交付完成。4 条门禁由独立 closure audit 复跑：silent-swallow=80（退出 1）、unique-key=0（退出 0）、sensitive-literal=0（退出 0）、limit=4 tests/1 FAIL（queryTableData）—— 合计 81，与 `formal-red-list.md` 及 I1 `initial-red-list.md` 快照完全一致（I1→I2 源码零漂移，经 git log 确认）。对抗探查 5 方向 0 新族，方向 3"ORM index 平台级 by-design"由审计独立复核（63 ORM index、0 进 `_create` DDL、`ddl.xlib` create-table 流程只发射 PK+uniqueKeys）。裁决零悬挂（81/81：80→P1/I4，1→P1/I4+人工确认），零 deferred，无已确认 live defect 被降级；INV-LIMIT L1 契约冲突以 P1 派 I4+ask-first（附两条具体解决路径）。I4 工作项 grep 命令经审计执行验证可跑、预期计数可核。本计划为纯审计/裁决交付（不改产品代码），无需 owner-doc 更新。
Completed: 2026-08-13

Closure Audit Evidence:

- Reviewer / Agent: independent closure auditor subagent (fresh session, review-only, task `ses_004c8adbaffewmnLdpsEs3lz0d`); executor = opencode glm-5.2
- Evidence:
  - Phase 1 Exit Criteria: PASS — `formal-red-list.md` 存在，81 命中经独立复跑（扫描器 80 + 测试 1），I1 一致性 81=81 确认（零 git 漂移）。
  - Phase 2 Exit Criteria: PASS — `adversarial-probing-notes.md` 覆盖 5 方向，每方向含方法+证据+结论；0 新族；方向 3 by-design 结论经独立核查（全仓比对表 + `ddl.xlib` 生成器源码）。
  - Phase 3 Exit Criteria: PASS — `adjudication-table.md` 零悬挂（81/81），无 live-defect 降级，INV-LIMIT L1 = P1+I4+人工确认，I4 grep 命令具体可执行且经审计跑通。
  - Closure Gates: PASS — L138（独立审计）= 本审计；L141（checklist 工具）退出码 0；L140（doc-links）18 errors 全 pre-existing，3 交付文件 0 broken links。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0。
  - Anti-Hollow: (a) red list 命中可在 live code 定位 —— YES（80 条扫描器复跑 + 4 条抽查：`MetaDataSourceConnectionProcessor:99` / `MetaQualityCheckpointExecutor:156` / `NopMetaLineageEdgeQueryAction:165` / `MetaQualityRuleExecutor:702` 均实）；(b) 裁决非敷衍 —— YES（族 A 3 点理由 + 族 D 4 点理由可核）；(c) 对抗探查实质结论 —— YES（5/5 方向含证据，含方向 3 跨模块+生成器核查）。
  - Deferred 检查：无 deferred 项；唯一非阻塞观察（ORM index）裁定为 out-of-mission，非本计划拥有的 deferred 工作项。

Follow-up:

- I4（P0/P1 修复执行：silent-swallow 80 类别清扫 + limit L1 契约冲突人工确认解决）与 I5（门禁零命中后提升 hard gate）为后继 work item，非本审计/裁决计划的 closure 阻塞项。
- 无剩余 plan-owned work。
