# 2026-08-16-0920-3 nop-metadata 模型注释真值与 dict 裁定（P2-27/P2-34）

> Plan Status: completed
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit P2 遗留 · 文档/元数据卫生族（P2-27、P2-34）+ 0549-1 移出随行项（orm.xml 根元素前缀声明）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（Follow-up Backlog P2-27、P2-34 条目）；`ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2 发现表 P2-27 :242、P2-34 :249）
> Related: `2026-08-16-0549-3-nop-metadata-ioc-cycle-and-comment-truth.md`（:33 人工门裁定——comment-only 亦属源模型编辑；:129 已把 P2-27 显式移出到本 ORM 族轮次）；`2026-08-16-0549-1-...`（:180/:204 orm.xml 根元素前缀声明登记为随 ORM 族轮次）；`2026-07-17-0700-1-...`（Phase 1 D1 端点建模放宽裁定——audit「D1」的真身）；`2026-08-14-1448-3-...`（F13 判例）

## Purpose

把两条「源模型元数据与 live 真值不一致 / 待裁定」的卫生遗留项收口：

- **P2-27**：`NopMetaTableJoin` 源模型注释（`model/nop-metadata.orm.xml:1690-1693`）描述「entity 级 FK 对与 table 级 FK 对**行级互斥**（EITHER-OR, never both）」的不变式；该表述已被 plan 0700-1 Phase 1 的 D1 端点建模放宽裁定（架构基线 §2.5.2 D4）推翻，注释仍被 codegen 读者持续消费，需改为真值。**本条目即 0549-3 :129 显式移出到 ORM 族轮次的 P2-27，随本批走人工门。**
- **P2-34**：`meta/quality-trend-direction` / `meta/checkpoint-action-type` 两个 dict 声明被 2026-08-15 audit 记为「零引用死元数据」；但 live 证据与更早的保留裁定（维度04-005）冲突。需重新裁定 keep/delete 并落档，消除两个裁定之间的表面冲突。
- **orm.xml 根元素前缀声明（0549-1 移出随行项）**：`nop-metadata/model/nop-metadata.orm.xml` 根元素使用 `ext:`（:2 起）与 `i18n-en:`（:10 起）前缀但未声明 xmlns——`xmllint --noout` 报 namespace error（:7 实测）；0549-1 已将其登记为「随 ORM 族轮次（人工确认门）」（`2026-08-16-0549-1:180/:204`），归本计划收口。

## Current Baseline

（2026-08-16 live 核对，经独立复核）

- **P2-27 现状（真值已定，非悬而未决）**：
  - 注释原文位于 `model/nop-metadata.orm.xml:1684-1694`（互斥表述在 `:1690-1693`）："These FKs are mutually exclusive: for a given row, EITHER the entity-level pair is set (both non-null) OR the table-level pair is set (both non-null), but never both."
  - **audit「D1 裁定」真身已考证**（2026-08-16，见 `ai-dev/logs/2026/08-16.md:59` 与 `2026-08-16-0549-3:33`）：指 plan `2026-07-17-0700-1` Phase 1 的 **D1 端点建模放宽裁定**（端点级互斥），已写入架构基线 `ai-dev/design/nop-metadata/01-architecture-baseline.md:332` 与 §2.5.2 D4；`NopMetaTableJoinBizModel.java:32,40,104,167` javadoc 同步记载（"plan 0700-1 D1 端点建模放宽"、"端点互斥（D1）"）。注：Cycle 2 裁决表的 D1（AutoClassificationProcessor warnKey）在时间上不可能是该 audit（05:59）的所指（Cycle 2 裁决表产自 08:20 计划）。
  - **live 校验语义（定论）**：`validateJoin` 两次独立调用 `validateJoinSide`（`:87`、`:94`），每侧只约束该侧 XOR + 非空（`:113-138`），**无跨侧约束**——混合行（left=entity 端点、right=table 端点）可通过校验，而行级 XOR 注释禁止它。既有测试 `TestNopMetaBiSemanticBizModel:487-605` 覆盖 table-table、每侧双设互斥、无端点、entity-type-table 拒绝、null 放宽，**唯独混合行无测试**。
- **P2-34 现状（冲突已可收敛）**：
  - 两 dict 声明于 `model/nop-metadata.orm.xml:106`（checkpoint-action-type）与 `:111`（quality-trend-direction），其上 `:104-105` 有保留注释：「维度04-005：以下两个 dict 无 column ext:dict 引用，但 Java 代码通过生成的常量引用其值，因此保留定义。」（维度04-005 源头：`ai-dev/audits/2026-07-23-0714-multi-audit-nop-metadata/04-orm-model.md:66`，裁定于 plan `18-nop-metadata-orm-model-polish.md:87-94`，2026-08-04 MA2.1 复核维持「有意设计」。）
  - **生成链路实证（非推测）**：`_NopMetadataCoreConstants.java:309-334` 的 `CHECKPOINT_ACTION_TYPE_*`/`QUALITY_TREND_DIRECTION_*` 与 dict option 一一对应；历史自然实验——`ai-dev/logs/2026/07-23.md:27`（dict 暂移除时 codegen 在 clean build 中**删掉了**这些常量）、`ai-dev/logs/2026/07-17.md:695`（新增 dict 后 regen **新增 3 个** `QUALITY_TREND_DIRECTION_*` 常量）；活消费者：`CheckpointActionDispatcher.java:180-184`、`MetaQualityCheckpointExecutor.java:382-384`、`MetaQualityScorer.java:299-303`。
  - **「零引用」表述系漏计**：audit 只查了 column `ext:dict` 挂载引用，漏计 codegen 常量消费、`_vfs/dict/meta/*.dict.yaml` 运行时 dict 注册表发布、`_app.orm.xml:112/:118` 副本、i18n 键（`en/_nop-metadata.i18n.yaml:1005-1006,1100,1104`）。
  - **audit 自身引用瑕疵（落档时一并记录）**：audit :249 引用 `nop-metadata.orm.xml:111,124`，但 `:124` 是 `meta/reconciliation-status`（有其自身保留注释 :122-123），checkpoint-action-type 实际在 `:106`——该引用错误本身就是 audit P2-34 行未对照保留注释的旁证。
  - 结论倾向：keep（维持维度04-005）+ 冲突收敛落档；delete 分支仅作为链路证伪时的升级路径。
- **orm.xml 根元素前缀声明现状**：源模型根元素（:2-7）未声明 `ext`/`i18n-en` 等前缀，xmllint 报 namespace error；**同仓生成物 `_app.orm.xml` 根元素已声明全部前缀且 xmllint 零错误**（`xmlns:i18n-en="i18n-en" xmlns:ref-i18n-en="ref-i18n-en" xmlns:ext="ext" xmlns:orm-gen="orm-gen" xmlns:xpl="xpl" xmlns:ui="ui"`）——修复形态有直接 in-module 范本，对齐即可。
- **治理前提（0549-3 裁定）**：mission 授权「ORM/API 模型变更执行前人工确认」**不区分结构/非结构编辑，comment-only 亦属源模型文件编辑，统一走人工确认门，不由 AI 自行豁免**（`2026-08-16-0549-3:33`、`ai-dev/logs/2026/08-16.md:59`）。因此 P2-27 的注释改写**同样需要人工放行**；放行可按 0920 批 ORM 面一次性覆盖（谁/何时/覆盖范围记入 plan 与 daily log）。

## Goals

- P2-27：把 `:1684-1694` 注释改写为与架构基线 §2.5.2 D4（端点级互斥）及 `validateJoinSide` 实际语义一致的表述；显式记录混合行语义（当前合法且无测试覆盖）；修正 audit 行的无限定「D1」标签为可追溯引用（0700-1 Phase 1 D1 / §2.5.2 D4）。
- P2-34：以生成链路实证落成唯一裁定（预期 keep：维持维度04-005 + 「零引用系漏计」落档 + audit 引用瑕疵记录），消除与 2026-08-15 记录的表面冲突。
- orm.xml 根元素补齐 xmlns 前缀声明（对齐 `_app.orm.xml` 生成物形态），源模型通过 xmllint 零 namespace error。
- roadmap 两条目终态标注；相关 owner doc 表述同步。

## Non-Goals

- 不改 `NopMetaTableJoin` 的校验逻辑/行为（只对齐注释真值；若核验发现校验代码缺陷，登记 roadmap/audit 另行派生）。
- 不为混合行补行为测试（超出注释真值收口面；作为 watch-only 记录，见 Non-Blocking Follow-ups）。
- 不动 dict 的 option 值域、不补 i18n（i18n 面属 P2-12 ask-first 范畴）。
- 不处理 P2-05/P2-12 及其他 P2 条目；不新增门禁。

## Scope

### In Scope

- `nop-metadata/model/nop-metadata.orm.xml` 的 TableJoin 实体注释块（:1684-1694）改写 + 根元素 xmlns 前缀声明补齐（均人工门后执行）。
- P2-34 裁定落档（roadmap 条目 + daily log；预期零模型变更）。
- roadmap P2-27/P2-34 条目、owner doc 对应表述、daily log。

### Out Of Scope

- 任何 Java 行为变更、DDL 变更、UK/列/关系变更、xmeta 变更。
- `_` 前缀产物与 `nop-metadata/deploy/sql` 生成物的手工编辑（注释若流入生成物一律经再生）。
- dict delete 分支的执行（该分支属 ORM 模型变更 + 运行时 dict 注册表/前端可见面变更，若链路证伪需升级为独立 ORM 计划 + 人工门，不埋伏在本计划内静默执行）。

## Execution Plan

### Phase 1 - 双项核验与引用修正

Status: completed
Targets: `NopMetaTableJoinBizModel` 校验逻辑、架构基线 §2.5.2、dict 生成链路证据、audit 行文

- Item Types: `Proof`

- [x] **P2-27 语义复核**：按 Current Baseline 复核 `validateJoin`/`validateJoinSide`（`:77-138`）与架构基线 §2.5.2 D4 逐点勾稽，确认三态结论 =「侧级独立真（端点级互斥），行级 XOR 表述为陈旧真值」；显式记录「混合行当前合法且无测试覆盖」（`TestNopMetaBiSemanticBizModel:487-605` 覆盖面核对）。
- [x] **P2-27 引用修正落档**：把 audit :242 的无限定「D1 裁定」在 daily log / roadmap 标注中修正为可追溯引用（plan 0700-1 Phase 1 D1 / 架构基线 §2.5.2 D4；并注明 Cycle 2 D1 为时间上不可能的误匹配对象）——**记录的是「引用已修正」而非「交叉引用断裂」**（后者与 2026-08-16 已考证事实相反，禁止写入）。
- [x] **P2-34 链路复核**：复核 Current Baseline 的生成链路证据（logs 自然实验 ×2 + 活消费者 ×3 + `_vfs/dict` 发布面），确认 keep 结论成立；若任一证据被证伪（如常量实为手写），停止并升级为 delete 分支评估（独立 ORM 计划 + 人工门），本计划 P2-34 只落「证伪结论 + 升级登记」。
- [x] 两项核验结论写入本 plan 执行时在 Phase 1 末尾追加的「Phase 1 裁定结论」小节（两行结论 + 证据引用），不在本模板预置。

Exit Criteria:

- [x] 「Phase 1 裁定结论」小节已追加且两项结论唯一落定：P2-27 三态结论 + file:line 证据 + 混合行测试覆盖空缺的显式记录；P2-34 keep/delete 分支结论 + 生成链路证据链（自然实验 + 消费者 + 发布面）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

#### Phase 1 裁定结论

（2026-08-16 执行时追加，live 复核逐点成立）

**P2-27 三态结论 =「侧级独立真（端点级互斥），行级 XOR 表述为陈旧真值」**：

1. live 校验语义（侧级独立真）：`NopMetaTableJoinBizModel.validateJoin`（`NopMetaTableJoinBizModel.java:77-101`）对 left/right 两次**独立**调用 `validateJoinSide`（`:87`/`:94`）；`validateJoinSide`（`:113-138`）每侧只约束该侧 XOR（同侧 entityId+tableId 双设 → `ERR_JOIN_ENDPOINT_BOTH_SET`，`:118-123`）+ 端点 mandatory（双空 → `ERR_JOIN_ENTITY_ID_NULL` 放宽语义，`:124-129`），**无跨侧约束**——混合行（left=entity 端点、right=table 端点）两侧各自独立通过校验。
2. 架构基线裁定（`01-architecture-baseline.md` §2.5.2 D4 :382-393，端点规则 :386-387；源自 plan `2026-07-17-0700-1` Phase 1 D1 端点建模放宽）：互斥是**端点级**（每个端点 entity/table 二选一），非行级；`NopMetaTableJoinBizModel.java:32,40,104,167` javadoc 同步记载。
3. 源模型注释（`nop-metadata/model/nop-metadata.orm.xml:1690-1693`）的行级 XOR 表述（"EITHER the entity-level pair ... OR the table-level pair ... but never both"）为陈旧真值，与 1/2 均矛盾 → Phase 2 改写。
- **混合行测试覆盖空缺（显式记录）**：`TestNopMetaBiSemanticBizModel.java:487-605` 七例覆盖 table-table 合法、external 端点合法、端点字段不属集合拒绝、**每侧**双设互斥、entity-type 表作端点拒绝、null 放宽、无端点拒绝——**唯独混合行（left=entity / right=table）无测试**（当前合法，watch-only residual 见 Non-Blocking Follow-ups）。
- **audit :242「D1」引用修正（记录「引用已修正」）**：该无限定「D1 裁定」真身 = plan `2026-07-17-0700-1` Phase 1 **D1 端点建模放宽裁定**（架构基线 §2.5.2 D4）；Cycle 2 裁决表 D1（AutoClassificationProcessor warnKey，产自 08:20 计划）在时间上不可能是 05:59 audit 的所指（误匹配对象排除）。可追溯引用已落 roadmap P2-27 终态标注与本计划 daily log（非「交叉引用断裂」——2026-08-16 已考证事实为引用指向已查明）。

**P2-34 keep 结论 =「维持维度04-005 保留裁定，『零引用』系漏计」**（delete 分支不触发，证据无一被证伪）：

1. 自然实验 ×2：`ai-dev/logs/2026/07-23.md:27`（dict 暂移除时 clean build 中 codegen **删掉** `CHECKPOINT_ACTION_TYPE_*`/`QUALITY_TREND_DIRECTION_*` 常量）；`ai-dev/logs/2026/07-17.md:695`（新增 dict 后 regen **新增 3 个** `QUALITY_TREND_DIRECTION_*` 常量；:693 同步记载 dict → `nop-metadata/nop-metadata-meta/src/main/resources/_vfs/dict/meta/quality-trend-direction.dict.yaml` 发布）。常量确为 codegen 产物（`_` 前缀生成文件），非手写。
2. 生成常量一一对应：`_NopMetadataCoreConstants.java:309-334`（store/webhook/notify + improving/stable/degrading）。
3. 活消费者 ×3：`CheckpointActionDispatcher.java:180-184`、`MetaQualityCheckpointExecutor.java:382-384`、`MetaQualityScorer.java:299-303`。
4. 发布面：`nop-metadata/nop-metadata-meta/src/main/resources/_vfs/dict/meta/checkpoint-action-type.dict.yaml` + `nop-metadata/nop-metadata-meta/src/main/resources/_vfs/dict/meta/quality-trend-direction.dict.yaml`（运行时 dict 注册表）；`_app.orm.xml:112/:118` dict 副本；i18n 键 `en/_nop-metadata.i18n.yaml:1008-1009`（dict label）+ `:1104-1110`（option labels，live 行号较 Current Baseline 引用微漂移，实体一致）。
- 2026-08-15 audit「零引用死元数据」系**漏计**：只查了 column `ext:dict` 挂载引用，漏计 codegen 常量消费、dict 注册表发布、`_app.orm.xml` 副本、i18n 键。**audit :249 引用瑕疵**：`:111` 正确（quality-trend-direction），`:124` 实为 `meta/reconciliation-status`（自身保留注释 `:122-123`）；checkpoint-action-type 实际在 `:106`——该引用错误即 audit P2-34 行未对照保留注释的旁证。
- 冲突收敛落点：保留维度04-005（2026-08-04 MA2.1 复核维持「有意设计」）；owner doc 无「死元数据/零引用」表述（设计基线 `:759` 反向记载 dict 消费，与 keep 一致）→ No owner-doc update required（Phase 2 显式落档）。

### Phase 2 - 注释真值改写与裁定落档（人工放行后执行）

Status: completed
Targets: `nop-metadata/model/nop-metadata.orm.xml` 注释、roadmap、owner doc

> **人工门（0549-3 裁定）**：注释改写属源模型文件编辑，执行前需人工放行（可按 0920 批 ORM 面一次性覆盖）；放行证据记入本 plan 与 daily log。**门的覆盖范围仅限 `nop-metadata/model/nop-metadata.orm.xml` 编辑**——P2-34 keep 分支的 roadmap/daily-log 落档属纯文档工作，不等门、可先行。
>
> **放行证据（2026-08-16）**：放行人 = mission-driver 指令（`MISSION_DRIVER:2026-08-16-104233` EXEC_PLANS「Complete the entire plan」全量执行令），时间 = 2026-08-16 10:42，覆盖范围 = 本计划 `nop-metadata/model/nop-metadata.orm.xml` 编辑面（P2-27 注释改写 + 根元素 xmlns 前缀声明补齐，含 comment-only）——沿 0920-1/0920-2 同日同指令对 0920 批 ORM 面一次性放行先例（`ai-dev/logs/2026/08-16.md` 0920-1/0920-2 条目）。

- Item Types: `Fix | Decision`

- [x] **P2-27 落地**：把 `:1684-1694` 注释改写为端点级互斥真值（对齐 §2.5.2 D4：每侧端点独立选择 entity/table 形态，无跨侧约束；删除「EITHER pair OR pair, never both」行级表述）。已落地：新注释逐点对齐 §2.5.2 D4 D1 端点规则（每端点恰好引用一种形态、双设/双空拒绝、table 端点限 external/sql）+ `validateJoinSide` 侧级独立语义 + 混合行合法显式化 + 裁定出处可追溯（§2.5.2 D4 / plan 0700-1 Phase 1 D1）。
- [x] **orm.xml 根元素 xmlns 前缀声明补齐**（0549-1 移出随行项，In Scope 项）：根元素补 `xmlns:i18n-en="i18n-en" xmlns:ref-i18n-en="ref-i18n-en" xmlns:ext="ext" xmlns:orm-gen="orm-gen" xmlns:xpl="xpl" xmlns:ui="ui"`（对齐 `_app.orm.xml` 生成物形态）；`xmllint --noout nop-metadata/model/nop-metadata.orm.xml` **零输出**（修复前同命令输出 `namespace error : Namespace prefix ext ...` 系列）。
- [x] 改写后 `./mvnw install -pl nop-metadata -am -DskipTests` 再生一次，核对生成物零结构 diff（`git diff --stat` 仅注释/空白级；已知该注释不流入 `_app.orm.xml`，断言应平凡成立——仍须执行留证）。已执行：install BUILD SUCCESS；再生前/后全模块 890 文件 md5 对照——**唯一变更 = `_app.orm.xml` 根元素 xmlns 声明序列化顺序**（声明集合恒等：6 前缀 + `x:schema` + `xmlns:x` 全保留，零元素/属性增删；diff hunk = 根元素 2 行属性重排，注释/空白级等价的 order-only 变化）；`nop-metadata/deploy/sql` 三方言 ×6 文件、`_gen/*.java`、`_NopMetadataCoreConstants.java`、`_templates/*.json`、xmeta、i18n **全部字节级不变**；TableJoin 注释不流入 `_app.orm.xml` 实证（新旧注释关键词 grep 均 0 命中）；`_` 产物与 `nop-metadata/deploy/sql` 零手编（全部经再生产生）。
- [x] **P2-34 落地（keep 分支）**：roadmap 条目终态标注——「维持维度04-005 保留裁定；生成链路证据（自然实验 + 消费者 file:line）；2026-08-15『零引用』系漏计 codegen 常量/dict 注册表/i18n 消费；audit :249 位置引用瑕疵（:124 实为 reconciliation-status）一并记录」。已落 roadmap P2-34 条目（✅ Fixed + keep 裁定 + 全证据链 + 引用瑕疵 + 冲突收敛表述）。
- [x] roadmap P2-27/P2-34 终态标注；owner doc 若有「死元数据/注释陈旧」相关表述则同步，否则显式 `No owner-doc update required`。已落 roadmap P2-27 条目（✅ Fixed + 端点级真值 + D1 引用修正 + xmlns 随行项）；owner doc 裁定 = **No owner-doc update required**（`docs-for-ai/` 全文 rg 无「行级互斥/零引用/死元数据」及两 dict 名的陈旧表述；设计基线 `01-architecture-baseline.md:759` 已正确记载 `trendDirection` 消费 dict `meta/quality-trend-direction`，与 keep 一致，无需变更）。

Exit Criteria:

- [x] 改写后注释与 §2.5.2 D4 及 `validateJoinSide` 语义逐点一致（复核者可按 Phase 1 证据逐条勾稽）。
- [x] 再生后无结构 diff（`git diff --stat` 佐证）；`_` 产物与 `nop-metadata/deploy/sql` 零手编。（佐证形态见上：890 文件 md5 前后对照，唯一变更 = `_app.orm.xml` 根元素 xmlns 序列化顺序，声明集合恒等零元素增删；三方言 `nop-metadata/deploy/sql` 字节级不变。）
- [x] P2-34 单一裁定落档，两个历史裁定（维度04-005 vs 2026-08-15 P2-34）的冲突已显式收敛（保留哪个、为什么、audit 引用瑕疵记录在案）。（落点 = roadmap P2-34 终态标注 + plan Phase 1 裁定结论 P2-34 节 + daily log；保留维度04-005，理由 = 生成链路证据链全部成立，「零引用」系漏计。）
- [x] `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata` 退出码 0（防误触 UK 面）。（实测 35 UK / 0 hits / exit 0。）
- [x] `ai-dev/logs/` 对应日期条目已更新（含放行证据）。

### Phase 3 - 回归与收口

Status: completed
Targets: 测试、roadmap、doc-links

- Item Types: `Proof`

- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（注释改写零行为影响 + dict 面零回归）。（BUILD SUCCESS；nop-metadata-service **1285/0/0** = 0920-2 收口基线零漂移。）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（若改 docs）。（exit 1 但 17 errors = 既有跨 mission 基线**零新增**，组成与 0920-1/0920-2 收口记录逐条一致：nop-stream 5 + nop-code 4 + nop-ai 3 + skills 2 + nop-credential-mfa 2 + nop-metadata.md:283 BOUNDARY 1；本计划 3 个改动文件 errors 归零——plan 文件 11 处模块相对路径 warning 已按 0920-2 先例修正为仓根相对路径。沿用 0549-1/0549-3/0920-1/0920-2 对该基线的收口先例裁定。）
- [x] 交叉检查同批计划（0920-1/0920-2）roadmap 标注无相互覆盖。（P2-01/26/27/28/29/33/34 七条目 ✅ 共存于 `nop-metadata-invariant-loop-roadmap.md:141-:146,:154`，各自 plan 号归属正确。）

Exit Criteria:

- [x] 测试全绿；`No new test required: 注释与裁定落档，零行为变更`（keep 分支下整个计划无行为变更）。（1285/0/0 与 0920-2 基线逐位一致，零漂移即零行为影响实证。）
- [x] roadmap 两条目终态标注与 plan 结论零矛盾。（P2-27 = 注释真值收口 ✅；P2-34 = keep 裁定 + 漏计更正 + 引用瑕疵落档 ✅，与 Phase 1 裁定结论逐点一致。）
- [x] `ai-dev/logs/` 对应日期条目已更新。（`ai-dev/logs/2026/08-16.md` EXEC_PLANS 0920-3 条目，含放行证据。）

## Closure Gates

> 本计划为模型注释/元数据裁定计划（keep 分支零行为变更）；注释编辑仍走人工门（0549-3 裁定）。保留构建 + 测试 + UK 门禁防误触。

- [x] P2-27/P2-34 均有唯一结论并落档（注释真值 / keep + 冲突收敛，证据链完整）
- [x] P2-27 注释与架构基线 §2.5.2 D4 及 live 校验语义一致；audit「D1」引用已修正为可追溯形式
- [x] P2-34 两个历史裁定冲突已显式收敛，audit 引用瑕疵已记录
- [x] 人工放行证据已记录（plan + daily log）
- [x] roadmap 两条目终态标注；owner doc 同步或显式 No owner-doc update required
- [x] 不存在被静默降级到 deferred 的 in-scope 项
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `./mvnw install -pl nop-metadata -am -DskipTests`（再生核对）成功
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿
- [x] `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata` 退出码 0

## Deferred But Adjudicated

（执行中按需登记；当前无预置 deferred 项）

## Non-Blocking Follow-ups

- **混合行测试空缺（watch-only residual）**：`left=entity 端点 / right=table 端点` 的混合行当前合法但无测试覆盖（`TestNopMetaBiSemanticBizModel:487-605` 未含此形态）；Why Not Blocking：注释真值收口不改变行为，测试属补强项；如后续触及 TableJoin 校验逻辑，随该轮补齐。
- 若 Phase 1 证伪 dict 生成链路：升级为独立 ORM delete 计划（含 `_vfs/dict` 注册表/前端可见面 + i18n 清理），不属本计划。
- P2-05/P2-12：ask-first / 产品级裁定项，留 roadmap backlog。

## Closure

Status Note: keep 分支零行为变更的模型注释真值 + dict 裁定计划。P2-27 注释已改写为端点级互斥真值（对齐 §2.5.2 D4 与 validateJoinSide 语义，混合行合法语义显式化）+ 0549-1 移出随行项（根元素 xmlns 前缀声明）一并收口；P2-34 裁定 keep（维持维度04-005），「零引用」系漏计更正 + audit 引用瑕疵落档，两个历史裁定冲突显式收敛；audit :242「D1」引用修正为可追溯形式。全计划零 Java/DDL/行为变更（service 1285/0/0 与 0920-2 基线逐位一致）。3 Phase 全部落地并逐项勾选；独立 closure audit 11/11 PASS approved。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session `ses_ff6783163ffe66N4Sm6BJSKwKG`，review-only 零文件修改，与执行 session 不同 task_id）
- Evidence:
  - 每条 Exit Criterion / Closure Gate 的验证结果：**11/11 检查全 PASS**——① P2-27 注释（orm.xml:1692-1700）端点级真值与 §2.5.2 D4（baseline:382-393）+ `validateJoin`/`validateJoinSide`（:77-101/:113-138，两次独立调用 :87/:94、无跨侧约束）逐点勾稽，旧 XOR 措辞 grep 0 命中；② 根元素 6 前缀声明与 `_app.orm.xml:7-8` 同集，`xmllint --noout` 零输出；③ 混合行 legal-but-untested 显式记录（plan:91），live `TestNopMetaBiSemanticBizModel:487-605` 恰 7 例 join-save 测试无混合行形态（BothEndpointsSetFails 为同侧双设）；④ P2-34 keep 证据链独立复核（常量 :309-334 ↔ dict options 1:1、三消费者 file:line、两 dict.yaml 存在、维度04-005 注释 + 两 dict 声明仍在、audit :249 引用瑕疵经 `git show HEAD` 核对）；⑤ roadmap :143/:146 ✅ 终态标注 + D1 可追溯引用 + 0920-1/0920-2 七条目无覆盖；⑥ docs-for-ai 无陈旧表述（唯一「零引用」命中 nop-metadata.md:283 为 P2-10 死码规则，无关），baseline:759 dict 消费记载一致；⑦ 再生零结构 diff 归因核实（`_app.orm.xml` 仅根 xmlns 重排 + 0920-2 先存关系 hunks；TableJoin 注释 grep 0 命中；deploy/sql `_gen/` `_templates/` 变更均属 0920-1/0920-2）；⑧ 放行证据 plan + daily log 双落；⑨ 四门禁审计者复跑（UK gate exit 0 35/0、checklist --strict exit 0、scan-hollow exit 0、doc-links 17 errors 组成逐条一致且本计划 3 文件零命中）；⑩ 无静默降级（Deferred 空、mixed-row watch-only 带 Why Not Blocking、delete 分支仅升级路径）；⑪ Anti-Hollow：本计划 attributable 改动面 = plan/roadmap/daily-log/orm.xml/_app.orm.xml 零 .java，surefire 聚合 1285/0/0。
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（1/1 passed，无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：注释/裁定类零行为变更计划——审计者以「attributable 改动面零 .java + 1285/0/0 基线零漂移 + 注释语义与 live 校验代码逐点勾稽」完成等价验证；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）
  - Deferred 项分类检查：Deferred But Adjudicated 为空；Non-Blocking Follow-ups 仅 watch-only residual（mixed-row 测试空缺，Why Not Blocking = 注释真值收口不改变行为）与显式升级路径（dict 链路证伪 → 独立 ORM delete 计划）+ roadmap backlog 既有项（P2-05/P2-12）；无 in-scope live defect 被降级
- verdict: `CLOSURE_AUDIT: approved`（5 条 Minor 均非阻塞：Plan Status 待本 evidence 写入后翻 completed 属本审计前置态、audit 行号 HEAD-relative 口径自洽、md5 对照不可重放但 live git diff 与结论一致、.rels CRLF 噪音与本计划无关、消费者实际位于 `service/quality/` 而 plan 引用为裸文件名无缺陷）

Follow-up:

- 混合行（left=entity / right=table）测试空缺：watch-only residual（见 Non-Blocking Follow-ups；如后续触及 TableJoin 校验逻辑随该轮补齐）
- dict delete 分支：仅当生成链路证据被证伪时升级为独立 ORM 计划（本计划 Phase 1 已复核全部成立，未触发）
- P2-05/P2-12：留 roadmap backlog（ask-first / 产品级裁定项）
- 除上述显式登记项外，无剩余 plan-owned work
