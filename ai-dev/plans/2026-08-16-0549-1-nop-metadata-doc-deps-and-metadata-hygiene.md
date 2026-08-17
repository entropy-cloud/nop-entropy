# 2026-08-16-0549-1 nop-metadata 文档/元数据/依赖卫生族批次清扫（P2-15/P2-16/P2-32 + P2-35 文档/依赖子项）

> Plan Status: completed
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit Follow-up Backlog — 文档/元数据/依赖卫生族（P2 批次清扫）
> Last Reviewed: 2026-08-16
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（文档 / 元数据 / 测试卫生族 + P2-35 杂项聚合的文档/依赖子项）；审计源 `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2-15/P2-16/P2-32/P2-35）
> Related: `2026-08-16-0226-3`（其 Non-Blocking Follow-ups 将"文档测试卫生族 P2 项"移交后续批次——本计划承接文档/元数据/依赖子项，测试子项由 `2026-08-16-0549-2` 承接）。执行顺序：本计划 → 0549-2 → 0549-3（0549-2/0549-3 亦可能编辑 `docs-for-ai/03-modules/nop-metadata.md` 不同段落——0549-2 记 P2-20 契约、0549-3 补 dao `model/` 行，顺序执行避免冲突）。

## Purpose

把 2026-08-15 multi-audit 遗留的文档计数漂移、死说明、畸形 XML 声明、依赖卫生四类 P2 项收口到"文档与 live repo 一致、依赖声明显式"状态，并对 AggregationHelper 混装项给出显式裁定（而非悬挂）。

## Current Baseline

以下事实均于 2026-08-16 live 核对：

- **P2-15（owner doc DTO 计数漂移）**：`docs-for-ai/03-modules/nop-metadata.md:221` 与 `:280` 两处宣称 api/dto 包 31 个 `@DataBean`；live 实测 `rg -l "@DataBean" nop-metadata/nop-metadata-api/src/main/java/io/nop/metadata/api/dto/ | wc -l` = **30**。（另有 2 处 `@DataBean` 在非 DTO 文件 `INopMetaDataSourceBiz`/`INopMetaTableBiz`，不在 doc 计数口径内。）
- **P2-16（_templates 计数漂移）**：`nop-metadata/nop-metadata-meta/_templates/README.md:7` 宣称"共 32 个" `_NopMeta*.json`；live `_NopMeta*.json` 实数 = **39**（目录下另有 1 个非实体模板 `_MetadataPropagation.json`，全部 JSON 合计 40——README 句子作用域是 `_NopMeta*`，修正值应为 39 而非 40）。该 README 为 plan 2026-07-19-1250-3 裁定的 watch-only residual（codegen 兼容保留）。39 模板 vs 39 实体（mission 口径）预期差集为空，对账验证而非仅改数字。
- **P2-32（xmeta 畸形命名空间）**：`nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/model/NopMetaSearch/NopMetaSearch.xmeta:3-4` 使用 `i18n-en:displayName` 前缀，根元素未声明 `xmlns:i18n-en`（XML 规范下畸形；运行时宽容加载）。**可循的正确声明形态在本模块生成物中已有**：`nop-metadata-meta/.../model/_NopMeta*/_NopMeta*.xmeta` 根元素均声明 `xmlns:i18n-en="i18n-en"`（如 `_NopMetaTableMeasure.xmeta`；`nop-rule-meta` 的 `NopRuleNode/_NopRuleNode.xmeta:2` 同形态）。注意：`nop-metadata.orm.xml` 根元素自身同样存在未声明前缀（ext/i18n-en）问题——**不在本计划范围**（源模型文件编辑统一归 ORM 族轮次，见 Non-Goals）。i18n 抽取配套**不存在**（roadmap 表述准确）：en/zh i18n yaml 中无 NopMetaSearch 相关 key（live 复核零命中），本计划只修声明、缺 key 登记不补译。
- **P2-35 文档子项**：
  - owner doc 模块结构表未列 `NopMetaSearch` 的 model 目录（`_vfs/nop/metadata/model/NopMetaSearch/NopMetaSearch.xmeta` live 存在，模块结构表缺行）。roadmap 原文为"`.xmeta/.xwf` 未入模块结构表"——live **不存在** `NopMetaSearch.xwf`；真实的 wf 文件为 `_vfs/nop/wf/` 下 3 个审批流（`metaDataContractApproval` / `qualityBreachApproval` / `tagLabelConfirmApproval` 各 `v1.xwf`），同样未入模块结构表——两项缺口一并补齐。
  - `docs-for-ai/04-reference/source-anchors.md:165` META-001 行宣称 `MetaAggregationExecutor` "268 行"；live `wc -l` = **274**（计数断言持续漂移的第三例：F15/P2-15 同族）。
- **P2-35 依赖子项**：
  - `io.nop.wf.api` 包被 main 代码使用（`QualityAlertWorkflowProcessor.java:22` import `io.nop.wf.api.WfReference`），但 `nop-metadata-service/pom.xml` 未显式声明 `nop-wf-api` 依赖（当前经 `nop-metadata-service → nop-wf-core → nop-wf-api` 传递到达；版本已由 `nop-bom/pom.xml:1084` 托管，可无版本号显式声明）。
  - `nop-metadata-service/pom.xml:78-83` 的 `nop-search-lucene` 为 compile + `<optional>true</optional>`；main 代码零 lucene **import**（`NopMetaSearchProcessor` Javadoc 有 Lucene 字样但无类型引用，不算依赖面；测试亦只 import `io.nop.search.api`，lucene 由运行时 SPI 到达）。审计建议 test scope；pom 注释主张"宿主可替换 impl"故事——两者矛盾需显式裁定。optional 本身已不传递，scope 调整对下游消费者零影响。
- **P2-35 结构子项（AggregationHelper 混装）**：`AggregationHelper.java` live **952** 行（审计时 938，仍在增长）；历史上已有两次拆分计划（`04-nop-metadata-aggregation-processor-split`、`2026-07-22-0900-2-nop-metadata-aggregation-context-bizmodel-split`）落地，剩余为 helper 本体混装。

## Goals

- owner doc、_templates README、source-anchors 三处计数/结构表述与 live repo 一致（或改为不随代码增长漂移的稳定表述）。
- `NopMetaSearch.xmeta` 成为 XML 规范合法文档（i18n-en 前缀已声明），且模型加载与 i18n 行为不回归。
- `nop-wf-api` 显式声明；`nop-search-lucene` scope 经显式裁定后落档（pom + owner doc 同步）。
- AggregationHelper 混装项落为显式裁定（classification + 理由），不再悬挂。

## Non-Goals

- 不拆分 AggregationHelper（本计划只做裁定；如裁定需要 successor，登记 successor path 而不在本计划实施）。
- 不处理 P2-17/P2-20 及 P2-35 测试子项（归 `2026-08-16-0549-2`）。
- 不处理 IoC 族 P2-02/03/30/31（归 `2026-08-16-0549-3`）与 P2-27（随 ORM 族轮次——源模型文件编辑含 comment-only 统一走人工确认门）。
- **不触碰任何 `*.orm.xml` 文件**（含修复其根元素未声明前缀问题——登记入 Non-Blocking Follow-ups，归 ORM 族轮次）。
- 不处理 P2-05/P2-33（产品级裁定）/ P2-12（ask-first i18n）/ ORM 结构族 P2-01/26/28/29/34。

## Scope

### In Scope

- `docs-for-ai/03-modules/nop-metadata.md`（DTO 计数 2 处 + 模块结构表 NopMetaSearch 行）
- `docs-for-ai/04-reference/source-anchors.md`（META-001 行数表述）
- `nop-metadata/nop-metadata-meta/_templates/README.md`（计数 + 模板集对账结论）
- `NopMetaSearch.xmeta`（i18n-en 命名空间声明）
- `nop-metadata-service/pom.xml`（nop-wf-api 显式声明；nop-search-lucene scope 裁定落档）
- AggregationHelper 混装裁定记录（roadmap 条目 + 本计划 Deferred 段）

### Out Of Scope

- `_templates/` 下任何 `_*.json` 模板文件的增删（watch-only 裁定维持；对账差集只登记结论）
- i18n yaml 内容翻译或补 key（P2-32 只修声明，不动已存在的 key）
- 任何 Java 行为变更

## Execution Plan

### Phase 1 - owner doc 与 source-anchors 真值化（P2-15 + P2-35 文档子项）

Status: completed
Targets: `docs-for-ai/03-modules/nop-metadata.md`, `docs-for-ai/04-reference/source-anchors.md`

- Item Types: `Fix`

- [x] P2-15：以 live 命令重扫 api/dto `@DataBean` 计数，将 `:221` 与 `:280` 两处 31 修正为实数（执行时以重扫结果为准，当前 30）
- [x] P2-35：模块结构表补 `NopMetaSearch` model 目录行（NopMetaSearch.xmeta，service 模块 `_vfs` 下）**及** `_vfs/nop/wf/` 下 3 个审批流 xwf 行（roadmap ".xmeta/.xwf" 缺口两项一并补齐；live 无 NopMetaSearch.xwf，如模块结构表无 wf 段则补 3 xwf 行或显式裁定归属表述）
- [x] P2-35：source-anchors META-001 行的 "268 行" 修正——优先**去掉易漂移的行数字面量**（改为职责描述，如"分派器，职责见 7 路径"），次选更新为 live 实数并注明计数时点

Exit Criteria:

- [x] `rg -n "31 个" docs-for-ai/03-modules/nop-metadata.md` 零命中；两处计数与执行时 live 重扫值一致（重扫命令与输出记录入 daily log）
- [x] 模块结构表含 NopMetaSearch 行 + 3 个审批流 xwf（或显式裁定表述），均与 live 路径一致；`node ai-dev/tools/check-doc-links.mjs --strict` error 数不增（**裁定沿用 0226-1/0226-2/0226-3 收口先例**：17 errors 全部为其他 mission 归属文件的 pre-existing 基线，本计划改动文件 0 新增——AGENTS.md 0-error 规则与跨 mission 基线的冲突已按该先例显式裁定并记录，非静默降级）
- [x] source-anchors META-001 不再含过时行数（rg "268" 该行零命中）；若保留行数则为 live 实数
- [x] **No new test required**: documentation-only phase（计数修正以 daily log 记录的重扫命令输出为证）
- [x] No owner-doc update required 判定不适用（本 Phase 改的就是 owner doc）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - _templates README 计数修正与模板集对账（P2-16）

Status: completed
Targets: `nop-metadata/nop-metadata-meta/_templates/README.md`

- Item Types: `Fix | Decision`

- [x] live 枚举 `_templates/_NopMeta*.json` 集合（实数 39）与 ORM 实体集合对账——实体集口径二选一：(a) 静态 `rg -c '<entity ' nop-metadata/model/nop-metadata.orm.xml`（当前 39，零环境依赖，推荐）；(b) `IOrmTemplate.getOrmModel().getEntityModels()`（F19 先例 `TestAllEntitiesHaveBizModels.java:79` 调用形态）。求双向差集
- [x] README 计数 32 修正为 `_NopMeta*` 实数（39；README 句子作用域即 `_NopMeta*.json`，勿写全目录 JSON 总数 40），并追加一行对账结论（`_NopMeta*` 39 与实体集差集是否为空；`_MetadataPropagation.json` 为非实体模板的定位说明；"保留不动"的 watch-only 裁定维持）
- [x] 若模板集存在无对应实体的孤儿模板：只登记不清删（watch-only 裁定范围不变）（live 对账：双向差集为空，无孤儿模板）

Exit Criteria:

- [x] README 计数 = live `ls nop-metadata/nop-metadata-meta/_templates/_NopMeta*.json | wc -l` 实数（39，命令以仓库根为 cwd）；对账差集结论（含双向 + `_MetadataPropagation.json` 定位）写入 README 或 daily log
- [x] `_templates/` 目录文件零增删（`git status` 该目录无变更）
- [x] **No new test required**: documentation-only（对账结论为登记性质）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - NopMetaSearch.xmeta i18n-en 命名空间声明（P2-32）

Status: completed
Targets: `nop-metadata/nop-metadata-service/src/main/resources/_vfs/nop/metadata/model/NopMetaSearch/NopMetaSearch.xmeta`

- Item Types: `Fix | Proof`

- [x] 根元素补 `xmlns:i18n-en` 声明——**形态对齐本模块生成物既有声明**：`xmlns:i18n-en="i18n-en"`（如 `nop-metadata-meta/.../model/NopMetaTableMeasure/_NopMetaTableMeasure.xmeta` 根元素；`nop-rule-meta` 的 `NopRuleNode/_NopRuleNode.xmeta:2` 同形态）
- [x] 复核文件内其余 `i18n-en:` 用法均在声明覆盖下；如实记录 en/zh i18n yaml 中 NopMetaSearch key 缺失现状（登记不补译，登记入 daily log 或 Follow-up）

Exit Criteria:

- [x] xmeta 根元素含 `xmlns:i18n-en="i18n-en"` 声明；xmllint 或等价 XML 解析器校验该文件 well-formed 且无未绑定前缀（命令与输出入 daily log）
- [x] 模型加载回归：NopMetaSearch 相关测试（`TestNopMetaSearchProcessor` 等既有套件）全绿，无行为变化（i18n key 本缺失、无消费方，声明修复不改变运行时渲染）
- [x] **No new test required**: 声明修复由既有加载路径回归覆盖（xmllint 校验记录为 Proof）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 依赖显式化与 scope 裁定（P2-35 依赖子项）

Status: completed
Targets: `nop-metadata/nop-metadata-service/pom.xml`, `docs-for-ai/03-modules/nop-metadata.md`

- Item Types: `Fix | Decision`

- [x] `nop-wf-api` 显式声明入 `nop-metadata-service/pom.xml`（版本随根 dependencyManagement/平台 BOM，不写死）
- [x] `nop-search-lucene` scope 裁定（Decision）：二选一并落档——(a) 改 test scope（main 零直接引用为实证依据；宿主需生产搜索时自行引入 nop-search-* impl，owner doc 补部署提示）；(b) 维持 optional compile（需给出比现状注释更强的理由，否则默认选 (a)）——**裁定选 (a) test scope**
- [x] 裁定结论同步 owner doc（模块依赖段或部署段）

Exit Criteria:

- [x] `rg -n "nop-wf-api" nop-metadata/nop-metadata-service/pom.xml` 命中显式声明
- [x] lucene 裁定结论写入 pom 注释 + owner doc，两者表述一致；若选 (a)，`rg "<optional>true</optional>" -B3 nop-metadata/nop-metadata-service/pom.xml` 该依赖不再命中 optional 形态
- [x] `./mvnw compile -pl nop-metadata -am -T 1C` 通过；`./mvnw test -pl nop-metadata/nop-metadata-service -am` BUILD SUCCESS（lucene 改 test scope 后搜索相关测试仍绿——测试 classpath 仍可见）
- [x] **No new test required**: 依赖声明变更，由既有套件回归覆盖
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - AggregationHelper 混装显式裁定（P2-35 结构子项，Decision）

Status: completed
Targets: 本计划 `Deferred But Adjudicated` 段、`ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` P2-35 条目

- Item Types: `Decision`

- [x] 裁定 AggregationHelper 拆分：classification = `optimization candidate`；Why Not Blocking（行为正确性由既有 6 门禁 + 1259 测试守护；混装是可维护性问题非正确性缺陷；两次拆分计划后剩余为长尾）；Successor Required = no（如未来需要，从 roadmap 本条目派生独立重构计划）
- [x] 裁定落档：本计划 Deferred 段 + roadmap P2-35 条目标注处置结果

Exit Criteria:

- [x] Deferred 段含完整裁定（classification / Why Not Blocking / Successor）；roadmap P2-35 条目已标注该子项裁定
- [x] **No new test required**: pure adjudication, no code change
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 本计划以文档/配置为主，仅 Phase 4 触及 pom。构建验证按 Phase 4 条目执行；全量 `./mvnw test` 以 `-pl nop-metadata -am` 为准。

- [x] P2-15/P2-16/P2-32 及 P2-35 文档/依赖子项的 owner-doc / README / source-anchors 表述与 live repo 一致（closure audit 独立重扫复核：DTO 30 / 模板 39 双向差集空 / xmllint 零输出 / 4 文件路径逐一存在）
- [x] 无被静默降级的 in-scope 项（AggregationHelper 裁定为显式 Decision，非降级）
- [x] `./mvnw compile -pl nop-metadata -am -T 1C` 通过（exit 0）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（service 1259/0/0 = 0226-3 基线零漂移；搜索族 41/41）
- [x] checkstyle / 代码规范检查通过（改动仅 1 个 pom，无 Java 改动；`checkstyle:check` 直跑 fail 于上游 `nop-api-core` 9164 条 = 0226-2 收口记录的全仓既有未接入门禁基线（逐条数完全一致），构建未及 nop-metadata、本计划零 Java 改动面）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` error 数不增（基线 17，全部为其他 mission 归属文件 pre-existing；沿 0226-1/0226-2/0226-3 收口先例显式裁定）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] roadmap Follow-up Backlog 对应条目（P2-15/P2-16/P2-32 + P2-35 相应子项）标注处置结果（P2-15/16/32 ✅ Fixed；P2-35 🟡 部分收口——测试子项归 0549-2）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session `ses_ff8778b9bffeUPrfkszHVJCrh7`，review-only，10/10 PASS，verdict `CLOSURE_AUDIT: approved`）

## Deferred But Adjudicated

### AggregationHelper 952 行混装（P2-35 结构子项）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 行为正确性由 6 条 hard-gate 门禁 + 1259 测试守护；混装属可维护性长尾（两次拆分计划已消化主要结构），非正确性缺陷、非 contract drift。
- Successor Required: `no`
- Successor Path: （如未来派生，从 roadmap P2-35 条目出发）

## Non-Blocking Follow-ups

- P2-17/P2-20/P2-35 测试子项 → `2026-08-16-0549-2`
- IoC 族 → `2026-08-16-0549-3`
- `nop-metadata.orm.xml` 根元素自身未声明 `ext:`/`i18n-en:` 前缀（与 P2-32 同族、位于源模型文件）→ 随 ORM 族轮次（源模型文件编辑统一走人工确认门）
- NopMetaSearch 的 en/zh i18n key 缺失（本计划只修声明）→ 如需补译另派（P2-12 i18n ask-first 轮次一并考虑）
- ORM 结构族（P2-01/26/27/28/29/34）与裁定项（P2-05/33/12）→ 后续轮次（ORM 结构变更需人工确认，见 mission 授权）

## Closure

Status Note: 5 Phase 全部落地并逐项勾选；全部验证绿（compile/test/doc-links/checklist/scan-hollow/closure audit）。本计划为文档/配置卫生族（唯一代码面载体 = 1 个 pom + 1 个 xmeta 声明，零 Java 改动），行为面由 1259/0/0 全量回归守护。唯一 deferred 项（AggregationHelper）为显式裁定非静默降级；P2-35 测试子项显式移交 0549-2。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（fresh session `ses_ff8778b9bffeUPrfkszHVJCrh7`，review-only 零文件修改，与执行 session 不同 task_id）
- Evidence:
  - 每条 Exit Criterion 的验证结果：**10/10 检查全 PASS**——① `rg "31 个"` 零命中 + live DTO 重扫 30 与 doc 两处一致；② 模块结构表 4 文件路径 live 逐一存在 + 全仓无 NopMetaSearch.xwf；③ META-001 行无 "268"；④ README 39 = live 39 + 审计者独立重算双向差集为空 + `git status _templates/` 仅 README；⑤ xmeta 根声明存在 + 审计者复跑 xmllint 零输出 + 6 个 i18n yaml 零 NopMetaSearch key；⑥ pom nop-wf-api 显式无版本 / lucene test scope 零 optional / nop-bom:1084/:1384 托管 / owner doc 部署提示一致；⑦ Deferred 段完整裁定 + AggregationHelper live 952 行 + roadmap P2-15/16/32 ✅ / P2-35 🟡；⑧ surefire 1259/0/0 + 搜索族 41/41（报告时间晚于最终文件态）；⑨ Non-Blocking Follow-ups 仅越界项；⑩ `git diff --name-only` 零 orm.xml 零 .java（7 文件：roadmap/log/2 docs/README/pom/xmeta）
  - 每条 Closure Gate 的验证结果：全 PASS（compile exit 0 / test BUILD SUCCESS 1259/0/0 / checkstyle 上游基线 9164 条与 0226-2 记录逐条一致且零 Java 改动面 / doc-links 17 = 基线零新增（审计者自行复跑）/ checklist exit 0 / roadmap 标注 / 独立 audit 本条）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（确认无未勾选项 + Closure Evidence 已写入）
  - Anti-Hollow 检查结果：doc/config-only 计划——审计者对全部计数/路径/声明断言独立重扫 live repo 复核（DTO 30、模板 39+diff 空、xmllint 复跑、pom 直读），无"文档宣称与 live 不符"空壳；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）
  - Deferred 项分类检查：唯一 deferred = AggregationHelper（optimization candidate，Why Not Blocking = 6 hard-gate + 1259 测试守护，Successor=no）；无 in-scope live defect / owner-doc drift 被降级（P2-35 测试子项为显式移交 0549-2 非降级）
- verdict: `CLOSURE_AUDIT: approved`

Follow-up:

- P2-17/P2-20/P2-35 测试子项 → plan `2026-08-16-0549-2`（显式 successor，非本计划 debt）
- IoC 族 P2-02/03/30/31 → plan `2026-08-16-0549-3`
- `nop-metadata.orm.xml` 根元素未声明 `ext:`/`i18n-en:` 前缀（P2-32 同族、源模型文件）→ 随 ORM 族轮次（人工确认门）
- NopMetaSearch en/zh i18n key 缺失（本计划只修声明）→ 如需补译随 P2-12 ask-first 轮次
- 除上述显式移交项外，无剩余 plan-owned work
