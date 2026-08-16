# 2026-08-16-0920-1 nop-metadata ORM unique-key 完整性收口（P2-01/P2-28/P2-29）

> Plan Status: completed
> Last Reviewed: 2026-08-16
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit P2 遗留 · 数据完整性 / ORM unique-key 族（P2-01、P2-28、P2-29）
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md`（Follow-up Backlog「数据完整性 / ORM 族」）；`ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2 发现表：P2-01 行 :216，P2-26~P2-29 行 :241-244）
> Related: `2026-08-14-1448-3-nop-metadata-orm-missing-index-creation.md`（ORM 源模型变更 + 人工放行 + 再生验证先例）；R4.2/R4.3 UK 变更先例（`deploy/sql/*/upgrade-nop-meta-table-uk.sql`、`upgrade-nop-meta-quality-result-uk.sql`，commit `4229d382e`/`9e57d6373`）

## Purpose

把 2026-08-15 multi-audit 遗留的 3 条 unique-key（UK）契约项收口到「裁定已落档、模型与写路径与裁定一致、门禁与测试守护」状态：

- **P2-01**：`NopMetaTagLabel` 的 `UK_NOP_META_TAG_LABEL (entityType,entityId,tagId,source)` 缺 `glossaryTermId`；`tagId`（:3304）与 `glossaryTermId`（:3306）均可空，GLOSSARY 来源行 `tagId=NULL` 时 NULL-distinct 使唯一约束失效，重复行可累积。
- **P2-28**：`NopMetaBusinessDomain` 的 `UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME (parentDomainId,name)` 对根域（`parentDomainId` NULL）不生效，仅根域重名失守。
- **P2-29**：`NopMetaGlossaryTerm` / `NopMetaTag` 各自存在「全局 FQN UK + per-scope FQN UK」双重唯一键，冗余索引 + 语义漂移陷阱。

## Current Baseline

（2026-08-16 对 live repo 逐条核对；行号经独立复核）

- **P2-01 现状**：`model/nop-metadata.orm.xml:3373` 声明 `UK_NOP_META_TAG_LABEL columns="entityType,entityId,tagId,source"`；TagLabel 实体（:3292 起）含 `glossaryTerm` to-one（:3364-3370），UK 列集不含 `glossaryTermId`。**写形态实证**：仓内 GLOSSARY 行写入点（如 `TestNopMetaGlossaryTermPropagation:283,330,342`）同时写两个 id；传播路径实际写的是 Classification 行（`NopMetaTagLabelBizModel:176`、`NopMetaGlossaryTermBizModel:99`、`LineageTagPropagationProcessor:182`、`AutoClassificationProcessor:268`、`NopMetaDataProductBizModel:77`）；`NopMetaTagLabelBizModel.save:89` 为 GLOSSARY 客户端 save 分支，其查重前置（`existingPropagatedLabel:195`）只覆盖 Derived/Classification 行——NULL-tagId 的 GLOSSARY 行仅来自显式客户端 save，正与 audit「触发面为显式构造的 save 调用」一致。
- **P2-01 的 NULL-distinct 约束（决定选项空间）**：标准 SQL 下复合 UK 任一列 NULL 即豁免唯一性——本模块自身在 `deploy/sql/mysql/upgrade-nop-meta-quality-result-uk.sql` 中记载过该语义。因此**裸扩列（只把 glossaryTermId 加进 UK）既约束不了目标行（tagId=NULL 的 GLOSSARY 行仍豁免），还会使现有受保护行（带 tagId、glossaryTermId=NULL 的 Classification/lineage 行）失去查重保护**——不是可行选项。
- **P2-28 现状**：`model/nop-metadata.orm.xml:3444-3446` 声明 `UK_NOP_META_BUSINESS_DOMAIN_PARENT_NAME columns="parentDomainId,name"`；`parentDomainId` 列（:3406-3407）可空、无 sentinel；`NopMetaBusinessDomainBizModel` 为裸 `CrudBizModel`（无 save override、无根域守卫）。
- **P2-29 现状**：`:3074-3077` 同时声明 `UK_NOP_META_GLOSSARY_TERM_FQN (fullyQualifiedName)` 与 `UK_NOP_META_GLOSSARY_TERM_G_FQN (glossaryId,fullyQualifiedName)`；`:3240-3243` 同时声明 `UK_NOP_META_TAG_FQN` 与 `UK_NOP_META_TAG_CLS_FQN`。**FQN 语义不对称（裁定输入）**：`GlossaryTerm.fullyQualifiedName` 可空（:3026）且无 BizModel 自动构建（用户手填）；`Tag.fullyQualifiedName` mandatory（:3207）且自动构建带 scope 前缀（`NopMetaTagBizModel:46` `cls.getName()+"."+tagName`；父前缀 :51），但用户手填非空 FQN 可绕过前缀（:37 仅在 null 时填充）；per-scope 语义存在活消费者 `AutoClassificationProcessor.findTagByFQN:251-255`（FQN+classificationId 过滤）。
- **DDL 再生面**：UK 变更会流入 `deploy/sql/{mysql,oracle,postgresql}/_create_nop-metadata.sql` + `_drop_` + `_add_tenant_`（codegen `nop-metadata-codegen/postcompile/gen-orm.xgen` → `DdlSqlCreator`，根 pom `generate-test-resources` 绑定）；6 个 UK 名当前都在这些文件中（如 `_create...sql:183,328-329,350-351,686`；`_add_tenant...sql:221-288`）。R4.2/R4.3 UK 变更先例均验证过三方言 + `_add_tenant` 再生与 git-diff 审计。
- **升级 SQL 先例**：本模块两次 UK 变更（R4.2/R4.3）都按方言提交了手写升级脚本（非 `_` 前缀保留文件），并显式交代存量库数据口径（R4.2 为 dedup 前置说明——"R3.19 之前建的库可能有重复行，先去重再 ADD CONSTRAINT"；R4.3 为"无需数据迁移"声明）。
- **门禁现状**：`ai-dev/tools/check-orm-unique-key-constraint.mjs`（**必须带 `--module nop-metadata`**，裸跑会扫全仓并因 nop-code/nop-job 既有命中而非零退出）；聚合链 `ai-dev/tools/run-nop-metadata-invariants.sh` / CI `invariant-gate`（`.github/workflows/maven.yml:45`）；6 族门禁零命中为当前基线。
- **测试可行性实证**：`TestNopMetaQualityCheckpointBizModel:620-623` 已有 H2 上复合 UK 重复插入被拒的断言先例（`@NopTestConfig(localDb=true, initDatabaseSchema=TRUE)`）；`TestNopMetaDdlUniqueKeyEmission` 提供模型级 + 三方言 DDL 断言模式。UK 名残留引用面很小（Java 侧仅 `TestNopMetaTagLabelReverseNavigation:138` 注释引用）。
- **授权约束**：mission 授权「ORM/API 模型变更执行前人工确认」**不区分结构/非结构编辑**（0549-3 裁定先例：comment-only 亦走人工门）；ORM 源模型属 AGENTS.md Protected Area（plan-first）。操作路径沿 1448-3 先例：只改 `model/nop-metadata.orm.xml` → `./mvnw install -pl nop-metadata -am -DskipTests` 再生 → 门禁 + 测试；**禁止手编任何 `_` 前缀产物与 deploy/sql 生成物**。
- **audit 优先级指示**：2026-08-15 audit 收尾建议（:283）明确「P2-01 …（UK 列集）… 建议在下一轮 invariant-loop 派生时优先裁决」。

## Goals

- P2-01/P2-28/P2-29 三项各自落成**明确裁定**：改 UK 列集（含配套写路径哨兵/守卫）/ 调整 UK 结构 / 应用层守卫 / 显式保留并文档化，各选项必居其一且留 live 证据；裁定的行为后果经测试钉死。
- 凡裁定为「改」的项：ORM 变更只落 `model/nop-metadata.orm.xml`、Java 变更只落对应 BizModel 写路径，经 `mvnw install` 再生后 `_app.orm.xml` 与 `deploy/sql` 三方言产物一致，UK 门禁与全模块测试全绿；凡涉及存量库约束变化的，按 R4.2/R4.3 先例交付升级 SQL（或在裁决表显式记录偏离先例的理由）。
- 凡裁定为「显式保留」的项：保留理由（含 NULL-distinct 等已知限制）写入源模型注释与 roadmap 条目，不留无主漂移。
- roadmap Follow-up Backlog 三条目 + mission 侧 UK 登记面（`audit-target-set.md`、`invariant-catalog.md` 的 UK 计数/清单）同步终态。

## Non-Goals

- 不处理 P2-05（行级数据权限，产品级裁定）、P2-12（ErrorCode i18n，ask-first）——留待人工裁定。
- 不处理 P2-26/P2-33（属 `2026-08-16-0920-2`）、P2-27/P2-34（属 `2026-08-16-0920-3`）。
- 不改任何 `_` 前缀生成产物与 `deploy/sql` 生成 SQL（只经再生产生）；不写整库迁移框架（升级 SQL 只按先例做本计划触发的 UK 面）。
- 不新增不变式门禁（消费既有 UK 门禁，不扩展门禁面）。

## Scope

### In Scope

- `model/nop-metadata.orm.xml` 中 TagLabel / BusinessDomain / GlossaryTerm / Tag 四实体的 UK 声明（列集、constraint、注释）。
- 为裁定与落地服务的**写路径变更**（条件性）：`NopMetaTagLabelBizModel` GLOSSARY save 分支（查重守卫或哨兵规范化）、`NopMetaBusinessDomainBizModel` 根域重名守卫——仅在 Phase 1 裁定选择对应分支时执行。
- UK/DDL 变更的再生核对（`_app.orm.xml` + `deploy/sql` 三方言 `_create_`/`_drop_`/`_add_tenant_`）与升级 SQL（按先例，条件性）。
- focused 测试、roadmap/audit 登记面/owner doc 同步。

### Out Of Scope

- 上述四实体以外的任何 ORM 结构；全模块 updatable/列治理。
- `_app.orm.xml`、`_gen/`、`deploy/sql` 生成物的手工编辑。
- 宿主侧数据迁移执行（只交付脚本与前置说明，不执行迁移）。

## Execution Plan

### Phase 1 - 三项 UK 裁决（Decision + 写路径证据）

Status: completed
Targets: `model/nop-metadata.orm.xml`（只读核对）、TagLabel/BusinessDomain/GlossaryTerm/Tag 全部写路径、`ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`

- Item Types: `Decision | Proof`

- [x] **P2-01 裁决**：以 Current Baseline 写形态实证为基础，在以下选项中裁定（明确排除裸扩列——NULL-distinct 下无效且有回退风险）：
  (i) UK 扩列 `(entityType,entityId,source,tagId,glossaryTermId)` + 写路径哨兵规范化（不适用侧写 `''` 而非 NULL，BizModel/全部写入口强制）；
  (ii) 应用层查重守卫（GLOSSARY save 分支补 existing 查重，沿 `existingPropagatedLabel` Derived/Classification 先例）+ 保留现 UK；
  (iii) 显式保留现状并文档化（须论证显式 save 触发面可接受）。
  裁定记录列级证据（file:line）与 NULL-distinct 影响分析。
- [x] **P2-28 裁决**：核对 BusinessDomain 写路径（裸 CrudBizModel 现状）与根域语义；在「应用层根域重名守卫 + 模型注释文档化 NULL-distinct 限制（不改 UK）」与「UK 结构改造（sentinel 机制，含对既有查询/GraphQL 契约影响面评估）」之间裁定。
- [x] **P2-29 裁决**：以 FQN 不对称事实（GlossaryTerm FQN 可空手填 / Tag FQN 自动构建可绕过）与 per-scope 活消费者（`AutoClassificationProcessor:251-255`）为输入，在三选项中裁定：「删冗余 per-scope UK」「删全局 UK 改 per-scope（**放宽约束**：同名 FQN 跨 glossary/classification 变合法）」「显式保留双 UK」；grep 四个 UK 名全仓引用（Java/测试/deploy-sql/文档）作影响面证据。
- [x] 裁决表写入本 plan（项 / 裁定 / 证据 file:line / 执行动作 / 升级 SQL 交付或偏离先例理由），并在 `ai-dev/logs/` 当日条目留痕。

Exit Criteria:

- [x] 裁决表存在且三项各有唯一裁定（无 optional/if-time-permits 措辞）；每项附 live 证据引用；P2-01 裁定显式陈述 NULL-distinct 影响分析。
- [x] P2-29 若选「删全局改 per-scope」，裁定记录了放宽约束的行为后果与消费者核对结论。（N/A——终裁为「删冗余 per-scope UK」，行为零变化；消费者核对结论仍已记录于裁决表）
- [x] `ai-dev/logs/` 对应日期条目已更新。

#### Phase 1 裁决表（2026-08-16 落档，行号经 live 复核）

**人工放行证据（Phase 2 前置）**：mission-driver EXEC_PLANS（2026-08-16 本次会话，指令 "Execute the plan ... Complete the entire plan"）构成本计划 ORM 源模型变更的人工放行，沿 `2026-08-14-1448-3` 先例（`ai-dev/logs/2026/08-15.md:193`：「mission-driver EXEC_PLANS，构成 plan-first ORM 变更的人工放行」）。覆盖范围：本计划 In Scope 的 `model/nop-metadata.orm.xml` 四实体 UK 面编辑（含 comment-only）；`_` 前缀产物与 `deploy/sql` 生成物仍禁止手编（只经再生）。

| 项 | 裁定 | 证据（file:line，2026-08-16 live） | 执行动作 | 升级 SQL |
|---|------|-----------------------------------|----------|----------|
| P2-01 | **选 (ii) 应用层查重守卫 + 保留现 UK** | UK 声明 `nop-metadata.orm.xml:3373`（`entityType,entityId,tagId,source`）；`tagId`(:3304)/`glossaryTermId`(:3306) 均可空。**NULL-distinct 影响分析**：(a) 裸扩列排除——复合 UK 任一列 NULL 即豁免，tagId=NULL 的 GLOSSARY 目标行仍不受约束，且 glossaryTermId=NULL 的现有 Classification/lineage 受保护行（带 tagId）将因新增 NULL 列整体豁免（现有查重保护回退，双输）；(b) 选项 (i) 哨兵排除——需全部写入口（save/update + 5 个传播/业务写点 + fixture）NULL→'' 归一，''/NULL 双键陷阱有模块内显式反面裁定（`NopMetaTableBizModel:178-180`「不做 null 或空串归一」），CrudBizModel.update 等非 save 入口绕过 save 内归一化，Oracle ''≡NULL（R4.2 oracle upgrade 脚本注记）哨兵跨方言不可移植；(c) 选项 (iii) 排除——重复行累积是 audit 复核确认的真实脏数据面，无补偿机制。**(ii) 成立论据**：缺口面唯一且窄——audit 定级理由（`2026-08-15-0559:216`「触发面为显式构造的 save 调用」）；仓内全部传播/业务写点写 tagId 承载行（`NopMetaTagLabelBizModel:176`、`NopMetaGlossaryTermBizModel:99`、`LineageTagPropagationProcessor:182`、`AutoClassificationProcessor:268`、`NopMetaDataProductBizModel:77`）受现 UK 保护；守卫先例 = `existingPropagatedLabel`（`NopMetaTagLabelBizModel:195-202`）+ `ERR_SQL_VIEW_TABLE_EXISTS` find-or-fail（`NopMetaTableBizModel:173-187`，含 `FilterBeans.isNull` 形态） | `NopMetaTagLabelBizModel` 增 GLOSSARY 查重守卫：`source=Glossary && tagId==NULL && glossaryTermId!=NULL` 的行按 `(entityType,entityId,source,glossaryTermId,tagId IS NULL)` 查重（save/update 带自身 id 时排除自身），命中 fail-loud 新 ErrorCode；save 与 update 双写入口共用同一守卫（audit 面为 save；update 为同形对称面，同守卫零额外语义）；tagId 非 NULL 的行继续由现 UK 数据库级拒绝。test-first 先红后绿 | **不交付**。偏离 R4.2/R4.3 先例理由：先例交付脚本皆因存量库约束形状改变（drop/add constraint）；本项零 DDL 变更，存量库无需动作 |
| P2-28 | **应用层根域重名守卫 + 模型注释文档化 NULL-distinct 限制（不改 UK）** | UK 声明 `nop-metadata.orm.xml:3444-3446`（`parentDomainId,name`）；`parentDomainId`(:3406-3407) 可空无哨兵；BizModel 为裸 CrudBizModel（`NopMetaBusinessDomainBizModel` 全文 15 行，无 override 无守卫）。**sentinel UK 改造否决（影响面评估）**：(a) 所有 `parentDomainId IS NULL` 消费面（GraphQL 查询契约、树装配、既有数据语义）需同步迁移；(b) 存量根域行需 UPDATE 数据迁移 + 升级脚本；(c) Oracle ''≡NULL（R4.2 oracle 注记）''哨兵不可移植；(d) 根域为有界小集合，守卫成本 << 结构改造成本 | `NopMetaBusinessDomainBizModel` 增 save+update 守卫：`parentDomainId==NULL` 时按 `(parentDomainId IS NULL, name)` 查重（排除自身），命中 fail-loud 新 ErrorCode；非根域由现 UK 保护。模型 UK 处注释补 NULL-distinct 限制说明（comment-only 属 ORM 源模型变更，随本 plan 人工放行覆盖）。test-first | **不交付**。理由同 P2-01（零 DDL 变更） |
| P2-29 | **删冗余 per-scope UK（保留全局 FQN UK；行为零变化）** | 双 UK 声明：GlossaryTerm `:3074-3077`（`UK_NOP_META_GLOSSARY_TERM_FQN` + `UK_NOP_META_GLOSSARY_TERM_G_FQN`）、Tag `:3240-3243`（`UK_NOP_META_TAG_FQN` + `UK_NOP_META_TAG_CLS_FQN`）。**蕴含关系**：非 NULL FQN 行 `(fullyQualifiedName)` 全局唯一 ⇒ `(scope,fullyQualifiedName)` 唯一——per-scope UK 是逻辑被蕴含约束，删除后约束集语义不变（无任何先前被拒写入变合法）；NULL FQN 行（仅 GlossaryTerm，:3026 可空）在两个 UK 下均豁免；Tag FQN mandatory(:3207) 全局 UK 全量生效。**消费者核对**：唯一 FQN 查询消费点 `AutoClassificationProcessor.findTagByFQN:251-255`（FQN+classificationId 过滤）——全局唯一 ⇒ per-scope 唯一，结果集恒同；查询由全局 FQN UK 索引前缀承载；scope 维度列举由既有 `IX_NOP_META_GLOSSARY_TERM_GLOSSARY`(:3117-3119)/`IX_NOP_META_TAG_CLASSIFICATION`(:3279-3281) 承载，无查询性能损失。**放宽选项否决**：跨 scope 同名 FQN 变合法与 FQN「全限定=全局唯一身份」语义冲突；seed（`SeedGlossaryData:55` "BuiltIn." 前缀）与 Tag 自动构建（`NopMetaTagBizModel:46/:51` scope 前缀）均按全局唯一约定；无跨 scope 重复 FQN 需求证据。**双保留否决**：冗余索引持续成本 + 语义漂移陷阱即 audit 发现本体。**残留引用面（rg 全仓 2026-08-16）**：Java/test 零命中（Java 侧唯一注释引用 `UK_NOP_META_TAG_LABEL` @ `TestNopMetaTagLabelReverseNavigation:138`，非本项）；命中面 = `audit-target-set.md:222/:225`（登记面，Phase 3 同步）+ deploy/sql 生成物（再生消除）+ 历史审计文档（plan guide 规则 20，历史记录不回写） | 模型删 `UK_NOP_META_GLOSSARY_TERM_G_FQN` / `UK_NOP_META_TAG_CLS_FQN` 两声明 + 注释记录裁定；`./mvnw install -pl nop-metadata -am -DskipTests` 再生（`_app.orm.xml` + deploy/sql 三方言 `_create_`/`_drop_`/`_add_tenant_`）；`TestNopMetaDdlUniqueKeyEmission` 增新 UK 集断言 + H2 全局 FQN 重复仍被拒测试 | **交付** `upgrade-nop-meta-fqn-uk.sql` 三方言（DROP CONSTRAINT ×2，无数据前置条件——drop 约束不因存量数据失败；「无需数据迁移」声明沿 R4.3 形态） |

### Phase 2 - 落地执行（源模型 + 写路径 + DDL 面；人工放行后执行）

Status: completed
Targets: `model/nop-metadata.orm.xml`、（条件性）`NopMetaTagLabelBizModel` / `NopMetaBusinessDomainBizModel`、再生产物 `_app.orm.xml` / `_gen` / `deploy/sql`

> **ORM 源模型变更 = plan-first + 人工确认（mission 授权，含 comment-only，0549-3 裁定）**。本 Phase 在获得人工放行后执行；放行证据（谁/何时/覆盖范围）记入本 plan 与 daily log。Phase 1 裁定为「显式保留」的子项跳过对应变更并注明。**Java 写路径守卫不属 ORM 模型变更，可先行落地（test-first）**。
>
> **放行证据（已落档）**：mission-driver EXEC_PLANS（2026-08-16，指令 "Execute the plan ... Complete the entire plan"）构成本计划 ORM 源模型变更的人工放行，沿 `2026-08-14-1448-3` 先例（`ai-dev/logs/2026/08-15.md:193`）。覆盖范围：本计划 In Scope 的四实体 UK 面编辑（含 comment-only）；`_` 前缀产物与 `deploy/sql` 生成物仅经再生。已记入 Phase 1 裁决表与 `ai-dev/logs/2026/08-16.md`。

- Item Types: `Fix`

- [x] （条件性，P2-01 选 i）UK 扩列落 `model/nop-metadata.orm.xml` + 全部写入口哨兵规范化（含客户端 save 分支与既有 fixture 数据口径同步——哨兵规范化后重复写入命中新 UK；存量 NULL 行依 NULL-distinct 保持豁免，属裁定中显式接受的既存状态）。**N/A——Phase 1 裁定为选项 (ii)，本条件项不适用**
- [x] （条件性，P2-01 选 ii）`NopMetaTagLabelBizModel` GLOSSARY save 分支查重守卫（test-first，先红后绿）。**已落地**：`rejectDuplicateGlossaryTermLabel`（save 分支 + update override 对称覆盖，super 之后对合并终态查重、自排除、fail-loud）；红态实证 = 守卫前 2 断言红（重复 GLOSSARY null-tag save 被接受 + update 撞名被接受），绿态 5/5
- [x] （条件性，P2-28 选守卫）`NopMetaBusinessDomainBizModel` 根域重名守卫；模型注释补 NULL-distinct 限制说明。**已落地**：save+update override + `rejectDuplicateRootDomainName`（`(parentDomainId IS NULL, name)` 查重自排除）；模型 UK 处注释已补；红态实证 = 守卫前 2 断言红，绿态 4/4
- [x] （条件性，P2-28 选 UK 改造）UK 结构变更落模型 + 写路径 sentinel 适配 + 查询/GraphQL 影响面核对。**N/A——裁定为守卫方案（sentinel 改造经影响面评估否决）**
- [x] （条件性，P2-29 选删除分支）删除对应 UK 声明；全仓 grep 被删 UK 名确认零残留（Java/测试；deploy/sql 只能经再生消除）。**已落地**：删 `UK_NOP_META_GLOSSARY_TERM_G_FQN` / `UK_NOP_META_TAG_CLS_FQN` + 裁定注释；rg 全仓：Java/test 正向引用零命中（2 个测试文件命中均为 assertFalse/NOT-emission 负向断言，钉死删除不复发），其余命中 = 本 plan/daily log 裁定记录 + 登记面（Phase 3 已同步）+ 历史审计文档（plan guide 规则 20 不回写）
- [x] `./mvnw install -pl nop-metadata -am -DskipTests` 再生；核对 `_app.orm.xml` 与 `deploy/sql` 三方言 `_create_`/`_drop_`/`_add_tenant_` 中 UK 变更已一致反映（沿 R4.2/R4.3 diff 审计口径）。**已核对**：BUILD SUCCESS；diff = deploy/sql ×6（纯 per-scope UK 行删除，三方言一致）+ `_app.orm.xml` −4 行 + 2 个 `_NopMeta*.xmeta` 各 −1 `<key>` 行；`_drop_` 无 UK 名不受影响；mysql unique 计数 `_create` 42→40、`_add_tenant` 41→39
- [x] （条件性）按 R4.2/R4.3 先例交付各方言 `upgrade-nop-meta-*.sql`（含存量库 dedup 前置说明）；或在裁决表记录偏离先例理由。**已交付**：`upgrade-nop-meta-fqn-uk.sql` 三方言（DROP ×2，无数据前置条件）；P2-01/P2-28 零 DDL 变更不交付（理由入裁决表）
- [x] `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata` 退出码 0。**实测 35 UK / 0 hits / exit 0**

Exit Criteria:

- [x] git diff 显示 `_` 前缀产物与 `deploy/sql` 仅由再生产生，无手编痕迹。（diff 全集 = 再生可解释面：纯 UK 行删除；无手编）
- [x] UK 门禁（带 `--module nop-metadata`）退出码 0；被删 UK 名在 Java/测试面零命中（`rg` 记录于 daily log）。（门禁 exit 0；Java/测试面正向引用零命中——2 个测试文件命中均为负向断言（钉死删除不复发），精度说明记入 daily log 与本项）
- [x] **接线验证**：`_app.orm.xml` 与 `deploy/sql` 三方言均与源模型 UK 一致（再生链路连通，非手编对齐）。（`_app.orm.xml` −4 行 / 三方言 `_create_`+`_add_tenant_` 均一致删除 per-scope UK；`TestNopMetaDdlUniqueKeyEmission.testFqnUniqueKeySetAfterP229Adjudication` 从 DdlSqlCreator 三方言断言双 UK 不再发射 + 全局 UK 仍发射）
- [x] 哨兵/守卫类 Java 变更均有前红后绿测试（见 Phase 3）。（红态：TagLabel 2 断言 + BusinessDomain 2 断言；绿态：5/5 + 4/4）
- [x] `ai-dev/logs/` 对应日期条目已更新（含人工放行证据）。

### Phase 3 - 防护测试与文档同步

Status: completed
Targets: `nop-metadata` 测试、roadmap、audit 登记面、owner doc

- Item Types: `Proof | Fix`

- [x] **P2-01（选 i）**：focused 测试——GLOSSARY 来源重复 TagLabel 插入被数据库唯一约束拒绝（沿 `TestNopMetaQualityCheckpointBizModel:620-623` H2 先例）；并清点/修复既有 fixture 中因新 UK + 哨兵而冲突的数据。**N/A——裁定为选项 (ii)，无 UK 变更；对偶覆盖见下一项（tagId 承载行 DB UK 拒绝路径在守卫测试中作非回归断言）**
- [x] **P2-01（选 ii）**：守卫测试——重复 GLOSSARY save 被拒且非 GLOSSARY 路径不回归（沿 `existingPropagatedLabel` 分支测试形态）。**已落地**：`TestNopMetaTagLabelGlossaryGuard` 5 例（重复 save 拒 + 事务回滚行数=1 / 不同 term 不误伤 / tagId 承载行仍 DB UK 拒 / update 撞名拒 / 幂等自更新不误伤）
- [x] **P2-01（选 iii）**：`No new test required: 裁定为文档化保留，无行为变更` + 模型注释落位。**N/A——裁定为选项 (ii)**
- [x] **P2-28（选守卫）**：根域重名守卫测试（守卫拒绝 + 非根域既有 UK 路径不回归）；（选 UK 改造）：对应约束/哨兵测试。**已落地**：`TestNopMetaBusinessDomainRootNameGuard` 4 例（根域重名拒 + 回滚行数=1 / 异名根域与异父同名子域合法 / 同父子域仍 DB UK 拒 / update rename 拒 + 幂等自更新不误伤）；UK 改造分支 N/A
- [x] **P2-29**：三分支各自映射——删冗余 per-scope：全局 FQN 重复仍被拒 + `TestNopMetaDdlUniqueKeyEmission` 模型/DDL 断言同步新 UK 集；删全局改 per-scope：**跨 scope 同名 FQN 变合法的行为测试 + `AutoClassificationProcessor.findTagByFQN` 消费路径回归**；显式保留：`No new test required: 无行为变更` + 注释。**已落地（删冗余 per-scope 分支）**：`TestNopMetaDdlUniqueKeyEmission.testFqnUniqueKeySetAfterP229Adjudication`（模型 UK 集 = 仅全局 + 三方言 DDL 双向断言：全局发射 / per-scope 名不再发射）+ `TestNopMetaFqnUniqueKeyBehavior` 3 例（Tag 同/跨 classification 重复 FQN 拒 + Term 同/跨 glossary 重复 FQN 拒 + 多行 NULL FQN 共存）；`findTagByFQN` 消费路径由既有 AutoClassification 测试族覆盖（全局唯一 ⇒ 结果集恒同，无行为变化）
- [x] roadmap Follow-up Backlog P2-01/P2-28/P2-29 三条目终态标注（✅ + plan 号 + 一句结论）；`ai-dev/audits/nop-metadata-invariants/audit-target-set.md:221-227`（该行段枚举了本计划全部 6 个 UK）与 `invariant-catalog.md`（UK 计数/清单）同步。（roadmap 三条目 ✅；audit-target-set §2.2 清单 37→35 + 注记 + §2.3 计数；invariant-catalog INV-UK 35/39 + 35/35；连带 formal-red-list.md INV-UK 段与 audit-target-set §2 标题/复现脚本预期值同步，I0 轮 37/37 历史口径保留为注记）
- [x] owner doc `docs-for-ai/03-modules/nop-metadata.md`：若 UK 契约变更则同步；三项均文档化保留则显式 `No owner-doc update required: 仅裁定落档，无契约变更`。**已同步**：新增「语义层 FQN 唯一性与 NULL-distinct 守卫（plan 2026-08-16-0920-1）」节（P2-29 全局 FQN 唯一契约 + 升级脚本指引 + P2-01/P2-28 守卫行为与错误码）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿。（BUILD SUCCESS；service 1277/0/0 = 基线 1264 + 净 13；首轮 `nop-stream-rocksdb` 计时基准 flaky（ratio=1.71，与本模块无关，0549-2 同款先例），复跑全绿）

Exit Criteria:

- [x] 每个落地行为变更均有「验证正确结果」的测试；每个纯裁定分支有映射的处理与 No-new-test 理由（P2-29 三分支无漏配）。（守卫 9 例 + P2-29 4 例全部断言正确结果（错误消息特征 + 落库行数/共行数），非仅「无异常」；三个 N/A 分支均显式标注裁定依据）
- [x] roadmap 三条目与裁决表、audit 登记面零矛盾。（三处均陈述：删 2 per-scope UK / 双守卫 + UK 不变 / 计数 35、42→40、41→39）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0（若改了 docs）。（17 errors = 既有跨 mission 基线零新增——唯一 nop-metadata.md 命中为既有 BOUNDARY（行号 263→271 平移，内容未变），沿 0226-1/0549-3 收口先例裁定）
- [x] **端到端验证**：源模型 → 再生产物（`_app.orm.xml` + 三方言 DDL）→ H2 约束生效（测试中真实插入被拒/守卫生效）全链路走通。（`TestNopMetaFqnUniqueKeyBehavior` 在 initDatabaseSchema=TRUE 的 H2 上真实插入同 FQN 行被 DB 全局 UK 拒绝 + `TestNopMetaDdlUniqueKeyEmission` 三方言 DDL 发射断言 + 守卫测试经 GraphQL 真实入口 save/update 被拒且行数断言）
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

> 本计划含条件性 ORM 源模型变更与条件性 Java 写路径变更；ORM 面 plan-first + 人工放行（mission 授权，含 comment-only），Java 守卫面 test-first 可先行。

- [x] P2-01/P2-28/P2-29 三项裁定全部落档且模型/写路径/文档与裁定一致，不存在未裁定的 in-scope 项（裁决表见 Phase 1；独立审计逐项核对 PASS）
- [x] 裁定为「改」的项已落地并经再生（含 `deploy/sql` 三方言）+ 门禁 + focused 测试验证；「保留」项有书面 Why-Not 与 NULL-distinct 等限制说明（P2-01/P2-28 = UK 保留 + 守卫，模型注释/裁决表含 NULL-distinct 分析与哨兵否决理由；P2-29 = 删冗余 per-scope，保留项为全局 UK 且注释落位）
- [x] 升级 SQL 按先例交付或偏离理由已记录（fqn-uk 三方言交付；P2-01/P2-28 零 DDL 变更不交付 + 理由入裁决表）
- [x] roadmap 三条目 + audit 登记面（audit-target-set / invariant-catalog）终态同步（连带 formal-red-list 同步）
- [x] 受影响 owner docs 已同步或显式 No owner-doc update required（owner doc 新增 UK 契约节）
- [x] 不存在被静默降级到 deferred 的 in-scope 裁定义务（3 个 N/A 条件项均对应未选中裁定分支并显式标注；独立审计 check #11 PASS）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session `ses_ff75fe74affelZfiZgtRsLmd17`，review-only，11/11 检查全 PASS，verdict `CLOSURE_AUDIT: approved`；4 条 Minor 均非阻塞，其中 formal-red-list :22 口径注记已顺手补齐，`.rels` EOL 伪差异为既有基线与本计划无关）
- [x] `./mvnw install -pl nop-metadata -am -DskipTests`（再生链路）成功（BUILD SUCCESS，-T 1C）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` 全绿（BUILD SUCCESS；service 1277/0/0 = 基线 1264 + 净 13；首轮 nop-stream-rocksdb 计时基准 flaky（ratio=1.71，与本模块无关，0549-2 同款先例），复跑全绿）
- [x] `node ai-dev/tools/check-orm-unique-key-constraint.mjs --module nop-metadata` 退出码 0（35 UK / 0 hits）
- [x] checkstyle / 代码规范检查通过（9174 条 = 既有全仓未接入门禁基线，nop-metadata 文件 0 条、本计划改动文件交集 0——沿 0549-2/0549-3 收口裁定口径）

## Deferred But Adjudicated

（执行中按需登记；当前无预置 deferred 项）

## Non-Blocking Follow-ups

- P2-05（行级数据权限）、P2-12（ErrorCode i18n）：ask-first / 产品级裁定项，留在 roadmap backlog 等人工触发（从未入本计划 scope）。

## Closure

Status Note: 三项 UK 契约裁定全部落档并落地：P2-01 选项 (ii)（守卫 + 保留 UK）、P2-28 守卫 + 注释（不改 UK）、P2-29 删冗余 per-scope UK（行为零变化）。模型变更经 `mvnw install` 再生（`_app.orm.xml` + 三方言 `_create_`/`_add_tenant_` 一致，mysql unique 计数 42→40 / 41→39），零手编生成物；升级 SQL 三方言交付（P2-01/P2-28 零 DDL 变更按裁决表理由不交付）；守卫 test-first 先红后绿（4 断言红 → 9 例绿）；端到端链路（源模型 → 再生 DDL → H2 真实约束/守卫生效）经测试走通；roadmap/audit 登记面/owner doc 同步终态；人工放行沿 mission-driver EXEC_PLANS 先例落档。无剩余 plan-owned work。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，review-only 零文件修改）
- Audit Session: `ses_ff75fe74affelZfiZgtRsLmd17`
- Evidence:
  - Phase 1 Exit Criteria：裁决表三项唯一裁定 + live 证据 file:line（PASS）；P2-29 未选放宽分支，消费者核对结论已记录（PASS）；daily log 已更新（PASS）
  - Phase 2 Exit Criteria：git diff 仅再生产物无手编（PASS）；UK 门禁 35/0/exit 0 + 被删 UK 名 Java/测试正向引用零命中（2 测试文件命中均为负向断言）（PASS）；接线验证 `_app.orm.xml`/三方言 DDL 与源模型一致 + DdlUniqueKeyEmission 三方言双向断言（PASS）；前红后绿（PASS）；daily log 含放行证据（PASS）
  - Phase 3 Exit Criteria：正确结果断言（错误消息特征 + 行数）（PASS）；roadmap/裁决表/登记面零矛盾（PASS）；doc-links 17 = 既有基线零新增（PASS）；端到端 H2 约束生效 + GraphQL 真实入口守卫（PASS）
  - Closure Gates：11 项逐条 PASS（见审计报告 check #1-#11：守卫接线 :95/:111 与 :33/:42、模型注释、UK 删除/保留全集、升级 SQL、focused 测试 20/20、双门禁 exit 0、登记面四处、daily log、Anti-Hollow 运行时链追踪、git sanity、无静默降级）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（见下）
  - Anti-Hollow 检查结果：守卫经 GraphQL save/update 真实入口触发、回滚经行数断言证明、早退条件仅跳过非目标行；`scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）
  - Deferred 项分类检查：无 deferred 项；3 个 N/A 条件项均对应未选中裁定分支并显式标注（审计 check #11 PASS）
- Minor（非阻塞，审计记录）：`.rels` EOL 伪差异（既有基线）；`TestNopMetaFqnUniqueKeyBehavior` 用 Exception.class 断言（约束名级断言由 DdlUniqueKeyEmission 承接）；守卫 findAllByQuery+stream 自排除形态（有界集合，风格项）

Follow-up:

- no remaining plan-owned work（P2-05/P2-12 等 ask-first 项从未入本计划 scope，留在 roadmap backlog 等人工触发）
