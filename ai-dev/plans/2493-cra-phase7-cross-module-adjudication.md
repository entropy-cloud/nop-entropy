# 2493 CRA Phase 7.1 — 跨模块合并裁定与修复批次划分提案（F 族聚类 + P2 逐项裁决 + R1..Rn 提案）

> Plan Status: active
> Last Reviewed: 2026-10-23
> Source: roadmap 工作项 7.1；方法论 `ai-dev/skills/audit-remediation-roadmap-authoring-prompt.md` 步骤 2（未闭包发现汇聚）/步骤 5（P2 裁决通道）；跨报告合并裁定口径沿 `ai-dev/skills/deep-audit-prompts.md`
> Related: plan 2461–2492（WI 1.1–6.5 全部 done，32 审计单元）；本工作项 = **初审审计阶段收官后的全 campaign 合并裁定**，产出直接喂给 7.2 R1.0 展开器
> Draft Review: R1 agent_1648f228（2 Blocker[P1 留 7.1 终裁通道缺失/P2 规模基线失真]+5 Major+9 Minor 全修）；R2 agent_b1e908a0（R1 七问题全部已解决；新增 N1[CL 域留档 7.1 候选无消费通道]/N2[P1 台账裁定批次列无回填]必修 + N3–N8 措辞级全修——本轮全量采纳，判定可 active，无第三轮）

## Purpose

对 CRA campaign 全部 32 个审计单元的发现做跨模块合并裁定：同族缺陷按 F1–F11 × 模块聚类、跨报告重复发现消解、单元显式移交 7.1 的判级分歧/开放裁定终裁、历史「已知未修复」并入台账、P2 逐项裁决（修 / deferred + successor）、P3 归档声明，产出**修复批次划分提案**（批次边界、依赖顺序、每批 exit criteria、finding ID 清单）——7.2 R1.0 展开器据此向 roadmap 追加 Phase 8 修复批次工作项。

## Current Baseline

- 前置：roadmap WI 1.1–6.5 全部 done @ 017f3d9e1f（分支 concurrency-audit-remediation）；7.1 依赖「1.1–6.5 全部 done」已满足。
- 台账规模（R1/R2 实测 cra-index 2026-10-23 @ 017f3d9e1f，Phase 1 以盘点数为准）：**P1 发现汇总表 179 行（30 单元有 P1，6.1/6.4 零 P1）**；**P2 缺陷实体 ≈ 560（工单级；5.5 实体级口径 564）**；**C10 缺保护单列 505**（验收前置类，裁决跟随缺陷本体，不独立裁决）；P0×3 全部已修复（1.2/4.3/2.5，即时通道闭环）。cra-index 存在既有口径差（如 5.2 报告行 C10=6 vs 口径块 15；「### WI 2.2 nop-core-framework（CL-001~021：」截断空节出现两处）——Phase 1 预登记为已知差并在同任务内修复截断节。
- **既有去重基线（7.1 不重复劳动的前提）**：各单元合并裁定已完成单元内去重与跨单元查重——cra-index 各单元计数口径块已记载零同站点重复立项结论及家族关联链（future 族 6 例/socket-fd 族/F4 账本族/超时族/Error 绝缘族等 40+ 组已记序；个别跨单元双登记/同站点发现均有已消解记录，如 2.1 双登记 1 处、2.4 X4 同站点、2.5 台账级同案 2 处）；使用方↔设施两面切分先例（WI 1.7 C4-003↔2.1 C4-003、CL-203→WI 2.1 C4-003）；移交闭环先例（2.1 C6-CL-031→5.6 W-04、G10/G11 承接回填）。
- **单元显式移交 7.1 的待裁项（B1 通道，Phase 2 终裁）**：≥10 条 P1 行的判级分歧/开放裁定——2.2 C4-003（门控 P1/P2 双口径）、2.5 MA-02（开放裁定暂计 P1）、3.2 MA-06（判级分歧暂计 P1）+ 沙箱族跨单元 P1/P2 分叉（3.1 MA-17 P2 vs 3.2 P1）、4.1 MA-02 + 判级三案嵌入方口径、4.3 C3-004（双判级 apache P1/jdk,okhttp P2）、4.3 C1-009/C6-021、4.4 C6-008（随族）、5.5 W-01（C4 侧 P2 分歧）、3.4 四张条件化 P2、3.3 条件化潮（13 张条件化工单携激活回升条款）；**加 CL 域留档显式标记 7.1 消费的项**——6.4 CL-001/002/102「域留档 7.1 聚类候选」与 CL-101「watch-only 附升 P1 条款」（Phase 1 建全量清单，Phase 2 逐项终裁）。
- 历史承接：2026-09 全仓审计 G 系列移交项已由各单元三态裁定并回填 cra-index（G10-01-02/03、G11-15-02、G11-03-01 已修等）；7.1 只做**汇总核对**（全部移交项状态收敛确认），不重新裁定。
- P2 现状：cra-index「P2 裁决台账」节为空表（表头：Finding ID/报告/单元/一句话描述/裁决/Why Not Blocking·批次）；各单元 P2 以「P2 裁决台账待 7.1」形态挂起。
- P3 现状：roadmap 规则「P3：记录在审计报告，不立项」——各单元审计报告已归档，7.1 出归档声明即可。
- Protected Area 修复面预勘：涉及 `nop-core`/`nop-xlang`/`nop-xdef` 内部、`model/*.orm.xml`、`nop-*-api`、`nop-api-core`、`nop-auth` 的工单在 P1/P2 台账描述文本中已有内联标注（如「Route-B 触 nop-core plan-first」「nop-auth ask-first」）——批次提案必须把这些工单的授权来源写入批次 schema。
- 输入文件全集：`ai-dev/audits/cra-index.md`、32 单元目录的 merge-adjudication.md 与 summary.md、`ai-dev/backlog/security-audit-roadmap.md`、`ai-dev/backlog/full-code-review-roadmap.md`、`ai-dev/backlog/nop-stream-independent-audit-roadmap.md`（跨 roadmap 重叠核对输入）、`missions/concurrency-audit.json`（mission 消费路径核对）。

## Goals

- 产出 campaign 级合并裁定目录 `ai-dev/audits/2026-10/{执行日}-{HHMM}-cra-phase7-cross-module-adjudication/`：inventory.md（全量台账盘点）+ family-clustering.md（F1–F11 × 模块聚类 + 重复消解 + 留 7.1 终裁）+ p2-adjudication.md（裁决组依据详表）+ batch-proposal.md（R1..Rn 批次提案，7.2 直接输入）+ summary.md；目录登记进 cra-index 报告清单。
- **P2 裁决台账格式（B2/M5 裁定）**：cra-index「P2 裁决台账」为**唯一权威台账**，采用**裁决组行**聚合格式——每行 = 一个裁决组（同族且同批次、或同 deferred 理由的 P2 集合），行内**成员 finding ID 清单全列（分隔符统一「、」）**，每条 P2 ID 恰好出现在一行成员清单中（9.1 可机械对账）；成员级例外（同组不同裁决）必须单列子行**且从组行成员清单移出**（保证并集校验不双计）。p2-adjudication.md 为裁决依据详表（按组给理由、例外成员单列、C10 缺保护映射引用 inventory §二），不复制台账行。
- 历史「已知未修复」与跨 roadmap 重叠收敛确认（cross-reference 登记，不重复修复）。
- 独立 closure audit PASS 后 roadmap 7.1 → done。

## Non-Goals

- 不执行任何修复（修复归 Phase 8 批次，每批一 plan）。
- 不推翻各单元已终审的判级与三态裁定——**例外：单元显式登记「留 7.1 终裁」的判级分歧/开放裁定/条件化回升项（B1 清单），Phase 2 必须逐项终裁并记录理由**；发现单元间同站点残留重复时才消解。
- 不修改产品代码。

## Scope

### In Scope

- `ai-dev/audits/cra-index.md` 全部台账（P0×3 已修 / P1×179 / P2 全量 / CL 域留档 / 家族关联链）与「P2 裁决台账」逐组写回
- 32 个审计单元目录的 merge-adjudication.md 与 summary.md（工单清单、家族关联、验证通道约束、跨单元批次约束的权威来源）
- 跨 roadmap 重叠核对（security-audit / full-code-review / nop-stream-independent-audit 三 roadmap）
- 修复批次划分提案文档（7.2 展开器输入）

### Out Of Scope

- 产品代码与测试代码变更；owner doc 内容修订（批次执行时随批更新）
- 各单元审计目录内部内容的改写（只读输入）
- Phase 8 工作项行的追加（归 7.2）

## 执行策略声明（B2 裁定）

- 规模：P1×179 工单行 + P2×~560 实体 + C10×505 前置条目。**单 plan 可交付的依据**：①P2 裁决以「裁决组行」聚合（同族同判同批归一组，预计 60–120 组），逐项语义由行内成员清单完整承载；②C10 缺保护跟随本体、不独立裁决；③Phase 1 机械盘点与 Phase 2/3 裁决**草稿**允许按 wave 分片并行派发子代理，**终裁口径一致性由主 agent 收口**（Phase 2/3 的三态终裁、组行合并、冲突消解是主 agent 单点动作）；本 plan 的并行条款仅覆盖只读提取与草稿，盲审纪律不适用（裁定汇聚阶段全部输入为已归档公开台账）。
- 跨日执行时每个 Phase 收口在 `ai-dev/logs/` 当日条目留锚点。
- Phase 3 裁决先定「修 + 目标阶段（R4+ / 按依赖提前）」，批次号由 Phase 5 提案定案后一次性回填（避免前向引用）。

## 批次覆盖口径定义（M4 裁定）

1. **批次键 = cra-index P1 台账工单行**（179 行）；族主行内联的吸收成员（`←C7-002` 等）随族主行归属，不单独计批。
2. **C10 缺保护条目**跟随其缺陷本体工单行归属（作为该批 exit criteria 的「验收前置：补测与修复同批」字段），不独立计批；R4+ 的「测试保护力缺口」仅收①本体在 R1–R3 修复但补测遗留的条目②无对应在案缺陷的独立补测项。
3. **移交他维度/他 roadmap 的行**（如 2.3 C9-012 移交功能正确性维度）：以 cross-reference 关闭，**不入批次**，在 batch-proposal 附录「豁免清单」显式列出（含移交去向）。
4. **P2 裁决为修的项**按其裁决组行分配批次（R4+ 或按依赖提前）；**deferred 项**不入批次，successor 指针登记。
5. 全覆盖判定：P1 179 行 − 豁免行 = 批次分配行数；每行恰好一批；P2 全部 ID 在裁决组行成员清单中恰好出现一次。

## successor 合法目标集（M7 裁定）

deferred 的 successor 指针只允许指向以下载体之一：① cra-index CL/域留档条目；② `ai-dev/backlog/` 既有 roadmap 的具体行；③ **batch-proposal 附录「治理批 R-G」**（本提案显式组成部分——承载 demo 治理轨、条件化激活监控等无既有主体轨的治理项）；④ owner doc 治理节（须具体到文档与节名）。C10 watch-only 条目的 successor = 对应缺陷本体工单（补测随修复同 diff）。

## Execution Plan

### Phase 1 - 全量台账盘点（inventory）

Status: planned
Targets: 审计目录 `inventory.md`

- Item Types: `Proof`

- [ ] P1 输入表：cra-index P1 台账逐行提取（Finding ID/单元/工单/一句话/判级/验证通道约束/Protected Area 标注/吸收成员），机械盘点可按 wave 分片并行派发子代理提取，主 agent 汇总
- [ ] P2 全量清单：从 32 单元 merge-adjudication/summary 提取全部 P2 实体（ID/单元/一句话/所属族/验证通道），与各单元计数口径块数字对账；C10 缺保护条目映射到缺陷本体行（不独立计数），映射表落档
- [ ] 「留 7.1 终裁」待裁项全量清单（B1）：判级分歧/开放裁定/条件化回升项 + **CL 域留档中显式标记 7.1 消费的聚类候选与 watch-only 升级条款**（6.4 CL-001/002/102/101 等）逐条登记（来源行 + 分歧双方判级或候选去向 + 单元裁定理由）
- [ ] CL 域留档清单 + 跨单元家族关联链清单（族名/成员/记序）+ 历史「已知未修复」/移交项/跨 roadmap 重叠登记项清单
- [ ] 批次约束清单：各单元登记的跨单元修复顺序约束与验证通道约束（6.4 反向约束注记、6.5 Gradle 通道[CL-301]、3.4 反向钉死、6.2 反向保护 4 处等）
- [ ] 数字对账与已知差预登记：P1 行数、P2 实体数、C10 单列数逐单元核对；既有口径差（5.2 C10 6 vs 15 等）与 cra-index 截断空节（「### WI 2.2」×2）登记，截断节同任务修复
- [ ] inventory.md 章节结构：§一 P1 输入表 / §二 P2 全量清单+C10 映射 / §三 留 7.1 待裁项 / §四 CL 与家族链 / §五 历史承接与跨 roadmap 重叠 / §六 批次约束 / §七 对账与已知差

Exit Criteria:

- [ ] inventory.md 七节齐备；数字与 cra-index 逐单元对账闭合（差异全部落在已预登记的已知差清单内）
- [ ] cra-index 截断空节已修复

### Phase 2 - F1–F11 × 模块聚类、重复消解与留 7.1 终裁

Status: planned
Targets: 审计目录 `family-clustering.md`

- Item Types: `Decision`

- [ ] 同族聚类：P1×179 + P2 全量按 F1–F11 缺陷族 × 模块组聚类（F 族定义沿 `concurrency-resource-audit-prompt.md`；单元内已组族直接继承，跨单元同族归并到族级行）；聚类草稿可分片并行，**族行合并与归属终裁由主 agent 收口**
- [ ] 「留 7.1 终裁」逐项裁定（B1）：Phase 1 清单逐条给终审判级 + 理由（P1→批次 / P2→裁决组 / 条件化激活条款确认 / CL 聚类候选进族行或维持域留档+successor），结果落 family-clustering.md 终裁表
- [ ] 跨报告重复消解：对聚类中疑似重复（同站点/同根因跨单元）做三态终裁——同一缺陷（取主条目，其余别名）/ 同族相邻（族工单，修复覆盖各失效面）/ 不同（各自立案）；**使用方↔设施两面切分先例沿用（防双计不防登记）**
- [ ] 已知未修复并入：G 系列承接残留项、跨 roadmap 重叠项并入对应族行（cross-reference 登记后关闭）
- [ ] 产出族级矩阵：族 × 模块 × 成员工单 × 判级分布 × 修复方向复用注记（平台并发设施 IThreadPoolExecutor/IBlockingQueue/IRateLimiter/ICache 等复用对照）

Exit Criteria:

- [ ] family-clustering.md 族级矩阵完整：每条 P1/P2 实体可归属到唯一族行（或显式「无族」独立行）
- [ ] 留 7.1 终裁表零缺项（Phase 1 清单逐条有终裁+理由）；疑似重复三态裁定表闭合、零未决

### Phase 3 - P2 逐项裁决

Status: planned
Targets: cra-index「P2 裁决台账」（权威）+ 审计目录 `p2-adjudication.md`（依据详表）

- Item Types: `Decision`

- [ ] 按族分组逐项裁决全部 P2 实体（~560 + C10 跟随本体）：**修（in-scope，定目标阶段：R4+ / 按依赖提前）/ deferred（必须带 Why Not Blocking + successor 指针，successor 限于本 plan「successor 合法目标集」四类）**；已确认 live 的 must-fix P2 不得降级 deferred（Minimum Rule #16）；C10 缺保护条目行内标注「验收前置→本体工单」（映射 SSOT=inventory §二，p2-adjudication 仅引用）
- [ ] 裁决校准口径：demo 降口径条目默认 deferred（successor=治理批 R-G）；watch-only 条目默认 deferred（successor=触发条款/本体工单）；条件化工单按激活条款默认 deferred 并显式登记触发条件；留 7.1 终裁为 P2 的按终裁结果归组
- [ ] 裁决组行写回 cra-index「P2 裁决台账」（沿用现有表头六列；组行=成员 ID 清单全列；成员级例外单列子行）；p2-adjudication.md 按组记录裁决依据与例外理由
- [ ] 独立子代理抽检裁决一致性（must-fix 未降级 / deferred 均带合法 successor / 目标阶段与提案一致 / 每 P2 ID 恰好出现一次[例外子行成员已从组行清单移出，不双计]）

Exit Criteria:

- [ ] 全部 P2 实体（Phase 1 盘点数）逐项有裁决且三态合法；cra-index 台账成员清单并集 = P2 盘点全集（恰好一次）
- [ ] 抽检未发现 must-fix 降级、无理由 deferred 或非法 successor 指针

### Phase 4 - 历史「已知未修复」并入台账与 P3 归档声明

Status: planned
Targets: `family-clustering.md` 承接节 + `summary.md`

- Item Types: `Proof`

- [ ] G 系列承接/移交项全量状态收敛确认（每项：已并入工单 N / 已修 / 维持原轨），汇总表落 family-clustering.md
- [ ] 跨 roadmap（`security-audit-roadmap.md` / `full-code-review-roadmap.md` / `nop-stream-independent-audit-roadmap.md`）重叠登记核对：先完成修复方为准原则确认，cross-reference 清单落档
- [ ] P3 归档声明：全部 P3 已在各单元审计报告归档、不立项（含 C10 watch-only），声明落 summary.md

Exit Criteria:

- [ ] 承接项汇总表零悬空（每项有终态）；跨 roadmap cross-reference 清单完整
- [ ] P3 归档声明入 summary.md
- [ ] No owner-doc update required（纯裁定与提案，不改 owner doc 内容）

### Phase 5 - 修复批次划分提案（batch-proposal）

Status: planned
Targets: 审计目录 `batch-proposal.md`

- Item Types: `Decision`

- [ ] 批次记录 schema 定义（M6，与 roadmap Phase 8 六列映射）：批号/标题（→Work Item 列）/finding ID 清单（→Deps 列，分号分隔）/涉及模块与验证命令（`./mvnw test -pl <模块> -am` 或 idea-plugin Gradle/harness 通道）/Owner Doc 锚点路径（→Owner Doc 列）/Protected Area 授权来源/依赖与可并行标注/Skill 列（none——修复 plan）/exit criteria（全量构建绿 + focused 测试 + 验证通道 + 授权声明 + owner doc 更新清单 + C10 验收前置补测）
- [ ] 批次划分（roadmap Phase 8 排序原则）：R1 = P0 收尾确认 + 跨模块同族 P1（先修基础设施层：nop-kernel/nop-rpc/nop-commons 设施工单，下游一次受益）→ R2 = 基础设施/平台服务剩余 P1 → R3 = 业务模块 P1 → R4+ = P2 裁决为修的项 + 独立测试保护缺口（覆盖口径第 2 条）；单批规模以「单次 AI 交付可完成」为限（预计 8–15 批，以实际聚类结果为准）；附录「治理批 R-G」承接 deferred 治理项——**R-G 与修复批一同由 7.2 展开器追加为 Phase 8 工作项行（治理轨批次，非修复 plan 形态时在行内声明产出物）**
- [ ] **P1 台账回填（N2）**：批次分配定案后一次性回填 cra-index P1 台账「裁定批次」列（179 行：批次号 / deferred+successor / 豁免行标 cross-ref 去向），消除「待 7.1」挂起态
- [ ] Phase 3 的 P2「目标阶段」定案为批次号并回填裁决组行（执行策略声明第 3 条的回填动作）
- [ ] 覆盖性核对（按「批次覆盖口径定义」五条）：P1 行 − 豁免行 = 批次分配行、每行恰好一批、P2 修项全部入批
- [ ] 批次约束落位：Phase 1 批次约束清单（Gradle 通道/反向钉死/跨单元顺序约束）写入对应批次 exit criteria
- [ ] 提案交独立子代理对抗审查（schema 完备性/依赖闭环/验证通道完备性/覆盖口径机械可验），Blocker 修复后定稿

Exit Criteria:

- [ ] batch-proposal.md 按 schema 覆盖全部批次分配行（Phase 2/3 终裁结果），每条恰好一批；豁免清单完整
- [ ] 每批 exit criteria repo-observable 且含验证通道与 Protected Area 声明
- [ ] 独立审查无 Blocker
- [ ] No owner-doc update required

### Phase 6 - summary.md、cra-index 收口与 closure audit

Status: planned
Targets: 审计目录 `summary.md`、cra-index、roadmap

- Item Types: `Proof`

- [ ] 主 agent 写 summary.md（数字链：P1×179 → 族聚类 → 批次分配；P2 裁决组分布；留 7.1 终裁汇总；承接/cross-reference 汇总；P3 归档声明）
- [ ] cra-index 报告清单登记 phase7 目录行；roadmap 7.1 → done（closure audit PASS 后）；daily log 记录
- [ ] 独立子代理 closure audit（盘点对账、聚类完备性、P2 台账成员清单并集=盘点全集、批次全覆盖无遗漏无重复、豁免清单完整、doc-links/checklist 门禁）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；`check-plan-checklist.mjs --strict` 退出码 0

Exit Criteria:

- [ ] closure audit PASS 记录在本 plan Closure 段
- [ ] cra-index P2 台账成员清单并集与 p2-adjudication 映射一致；批次提案全 finding 覆盖
- [ ] 两个门禁退出码 0
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] inventory 对账闭合（P1/P2/C10/CL 数字与 cra-index 一致，差异均在已知差清单内）
- [ ] F1–F11 聚类矩阵完备，零未决重复；留 7.1 终裁零缺项
- [ ] 全部 P2 逐项裁决合法（must-fix 零降级、deferred 均带 Why Not Blocking + 合法 successor）
- [ ] 历史「已知未修复」与跨 roadmap 重叠零悬空
- [ ] batch-proposal 按 schema 全 finding 覆盖（每条恰好一批）且经独立审查无 Blocker
- [ ] 独立子 agent closure-audit 完成并记录证据
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] 本计划零产品代码变更（纯文档计划，按 plan guide「纯文档计划」条款豁免构建/测试门禁；owner doc 无内容修订）

## Deferred But Adjudicated

（无——P2 的修/deferred 裁决是本计划交付物本身，不属本计划自身的 deferred）

## Non-Blocking Follow-ups

（无）

## Closure

Status Note: （收口时填写）
Completed:

Closure Audit Evidence:

Follow-up:

- <<或者明确写 no remaining plan-owned work>>
