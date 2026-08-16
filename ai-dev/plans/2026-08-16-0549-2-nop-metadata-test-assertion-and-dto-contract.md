# 2026-08-16-0549-2 nop-metadata 测试断言强度与 DTO 契约形态族批次清扫（P2-17/P2-20 + P2-35 测试子项）

> Plan Status: completed
> Mission: nop-metadata-invariant-loop
> Work Item: 2026-08-15 multi-audit Follow-up Backlog — 测试/DTO 卫生族（P2 批次清扫）
> Last Reviewed: 2026-08-16
> Source: `ai-dev/backlog/nop-metadata-invariant-loop-roadmap.md` Follow-up Backlog（文档 / 元数据 / 测试卫生族 + P2-35 杂项聚合的测试子项）；审计源 `ai-dev/audits/2026-08-15-0559-multi-audit-nop-metadata-invariant-loop.md`（P2-17/P2-20/P2-35）
> Related: `2026-08-16-0226-3`（其 Non-Blocking Follow-ups 将 P2-20 显式移交"测试/DTO 卫生族批次"——本计划承接）；`2026-08-14-1448-1`（F19 动态发现先例）；`2026-08-14-1448-1`（F18 DTO round-trip 先例）。执行顺序：在 `2026-08-16-0549-1` 之后、`2026-08-16-0549-3` 之前。

## Purpose

把测试卫生族（守卫可被绕过、虚增计数、弱断言、假阴性正则）与 DTO 冗余契约形态（P2-20）收口到"守卫不可静默逃逸、断言有区分力、DTO 契约与消费面一致"状态。

## Current Baseline

以下事实均于 2026-08-16 live 核对：

- **P2-17（接口完备性守卫弱）**：`TestNopMetaBizInterfaceCompleteness` 覆盖 12 个 I*Biz 接口、断言形态为硬编码方法存在性（`assertDeclaresMethod`）。live 非空 I*Biz 接口 = 14：已覆盖 12（Table/DataSource/Module/LineageEdge/QualityScore/QualityCheckpoint/QualityRule/DataContract/ProfilingRule/QualityResult/DataProduct/TagLabel）+ **未覆盖 2**（`INopMetaReconciliationConfigBiz` 1 方法 / `INopMetaReconciliationResultBiz` 2 方法）。与 F19（硬编码实体清单被改 ORM 注册表动态发现）同族：新增接口/方法可静默逃逸。
- **P2-20（DTO 冗余契约形态）**：`CheckpointExecutionResultDTO`（`nop-metadata-api/.../dto/CheckpointExecutionResultDTO.java:31-32`）含 `executionResults`/`executionErrors` 两个 `List<Map<String,Object>>` 字段。live 事实（2026-08-16 逐点核对，**与"类型化版本已并存、纯冗余"的审计简述有出入**）：
  - 生产者 `NopMetaQualityCheckpointBizModel.executeCheckpoint` 对类型化字段**只调 `setRuleResults`**（:226，`mapRuleResults` :410-423 把 executor 产出的 6 键 Map 投影为 3 键：`qualityRuleId/status/message`）；**从不调 `setErrors`**——类型化 `errors` 字段从未被填充。（`List<Map>` 两字段则由 `setExecutionResults`（:222）/`setExecutionErrors`（:230）填充。）
  - executor 侧 `MetaQualityCheckpointExecutor`（:434-443）每条结果写 6 键（`qualityRuleId/ruleName/status/actualValue/expectedValue/message`）——`ruleResults` 是**有损投影而非等价重复**（`ruleName/actualValue/expectedValue` 无类型化承接字段）。
  - 消费者 `MetaQualityCheckpointScheduler` **只写不读**：唯一交互是 `buildErrorResult`（:246-255）向 `executionErrors` **写入** checkpoint 级错误条目（键 `{source:"scheduler", error}` 等）；main 代码无任何读取点。
  - 其余消费面：测试 2 处（`TestNopMetaQualityCheckpointBizModel`——含 :333/:334/:361 经 `executionErrors` 内容断言缺失规则/缺失表/异常场景，:1097-1101 GraphQL `exec()` 查询该字段；`TestMetaQualityCheckpointSchedulerCronReadFailure`）；xmeta/view/page/e2e/`nop-metadata-web` 全仓零命中（执行时复核）。
  - 该字段暴露于 GraphQL 响应（biz mutation 返回值，schema 运行时生成、无静态 schema 文件），移除属对外契约变更——**决策必须先有字段级等价/损失对照表**（见 Phase 2 步骤重排）。
- **P2-35 测试子项**（live 文件确认存在，断言/正则缺陷执行时逐项复核）：
  1. `TestAggregationHelper.java` 无 `@Test` 方法——**命名与行为不符**（surefire 按 `Test*` 发现该类但运行 0 例，"虚增计数"实为命名误导 + helper 被误放入测试类命名空间；被 3 个测试类实例化使用），非计数膨胀
  2. `nop-metadata/nop-metadata-service/src/test/java/io/nop/metadata/service/invariant/TestLimitTargetSetCompleteness.java` 计数正则单一形态（字面 `@Name("limit")`，漏匹配 `@Name(value="limit")`/空白变体/`@RequestBean` 内 limit 形态 → 守卫盲区；live 4 处命中全为规范形态，洞为假阴性型）
  3. `TestCoreMetricsUsage.java` 注释剥离正则（`//[^\n]*` + 无 DOTALL 的 `/\*.*?\*/`）假阴性洞：多行注释剥离不全、字符串字面量内 `//` 误剥 → 用量统计失真
  4. `TestNopMetadataErrorsCentralized.java` 镜像断言（`assertEquals(字面量, Constant.getErrorCode())` 与被测物同源）；且 `testAllErrorsUseNopErrPrefix` 枚举**硬编码 10 个 `*Errors` 接口清单**——新增 `*Errors` 接口静默逃逸（F19 同族盲区）
  5. `TestCrossDbInMemoryAggregationProcessor.testCrossDbAliasOf` 仅 `assertNotNull`（被测 `crossDbAliasOf` 返回 alias-or-`"right"`，值断言可直接构造期望）
  6. `TestNopMetaDtoResults.java` 首方法 `testDtoJsonRoundTrip`（:31-41）无 parse-back（F18 只修复了 `testDtoJsonRoundTripAllTypes`，同文件其余 3 方法均有 parse-back）

## Goals

- `TestNopMetaBizInterfaceCompleteness` 覆盖全部非空 I*Biz（14/14，执行时重扫定稿），且具备程序化全集守卫（新接口/新自定义方法不可静默逃逸——F19 动态发现形态）。
- P2-20 经消费面清单裁决后收敛：默认移除冗余 `List<Map>` 字段并同步生产者/消费者/owner doc；若发现计划外消费面则改为显式保留 + 落档理由。
- P2-35 六个测试子项逐一修复，且每个子项能说明"修复前断言强度 vs 修复后断言强度"（有区分力实证或说明）。

## Non-Goals

- 不重构被测生产代码（六项测试修复只改测试与其守卫正则，若发现真实生产缺陷按 mission 授权升级为 Fix 并记录）。
- 不处理文档/依赖子项（归 0549-1）、IoC/注释族（归 0549-3）。
- 不改 `TestLimitTargetSetCompleteness` 背后的 INV-LIMIT 门禁本体（只修测试内计数正则）。

## Scope

### In Scope

- `nop-metadata-service/src/test/.../TestNopMetaBizInterfaceCompleteness.java`（P2-17）
- `nop-metadata-api/.../dto/CheckpointExecutionResultDTO.java` + 生产者 `NopMetaQualityCheckpointBizModel` + 消费者 `MetaQualityCheckpointScheduler`（P2-20，按裁决结果）
- P2-35 六项测试文件（上列 1-6）
- `docs-for-ai/03-modules/nop-metadata.md`（P2-20 契约变更记录，如裁决为移除）

### Out Of Scope

- GraphQL schema 文件 / 前端消费方适配（如 live 复核发现 xmeta/web 消费面，只登记不迁移前端）
- ORM 结构族、裁定项（P2-05/33/12）

## Execution Plan

### Phase 1 - 接口完备性守卫强化（P2-17）

Status: completed
Targets: `TestNopMetaBizInterfaceCompleteness.java`

- Item Types: `Fix | Proof`

- [x] live 重扫全部 `INopMeta*Biz` 接口非空集（执行时定稿；当前 14），补未覆盖 2 接口的方法断言（ReconciliationConfig 1 方法 / ReconciliationResult 2 方法，以重扫为准）
- [x] 增加程序化全集守卫——**发现机制钉死为文件系统扫描**：扫描 `nop-metadata-dao/src/main/java/io/nop/metadata/biz` 目录全部 `INopMeta*Biz.java` 源文件（沿 `TestLimitTargetSetCompleteness` 目录行走 + 路径回退先例；Node 侧 `ai-dev/tools/check-ibiz-interfaces.mjs` 为源码扫描可行性先例），解析"自定义方法"=接口声明的方法集；断言测试覆盖清单与扫描结果一致 + ≥N sanity 断言（N=当前实数）——新增接口或新增自定义方法若未登记即红。**盲区契约**：本守卫只覆盖驻留在该包的 I*Biz（包约定 + owner doc F15 IBiz 表背书）；接口移包即视为结构性变更需同步守卫
- [x] 保留既有逐方法签名断言（参数个数级）作为精度层

Exit Criteria:

- [x] 测试覆盖数 = live 重扫非空接口数（重扫命令与输出入 daily log）；Reconciliation 两接口的方法断言可验证（方法名/参数个数与 live 接口一致）
- [x] **接线验证（守卫区分力）**：临时向任一 I*Biz 接口加一个假想方法名（或注释掉覆盖清单中一项）→ 测试红；恢复 → 绿（变异验证记录入 daily log）
- [x] `TestNopMetaBizInterfaceCompleteness` 全绿；不引入对 `_gen` 代码的结构耦合
- [x] owner doc 测试段如提及该守卫的覆盖数则同步；否则 `No owner-doc update required`
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - CheckpointExecutionResultDTO 冗余字段处置（P2-20）

Status: completed
Targets: `CheckpointExecutionResultDTO.java`, `NopMetaQualityCheckpointBizModel`, `MetaQualityCheckpointScheduler`, 相关测试, owner doc

- Item Types: `Decision | Fix`

- [x] 步骤 1（消费面终审清单，执行时 live 复核）：Java main/test 全量（含注释提及点：`MetaQualityCheckpointScheduler.java:201/:246` javadoc）、xmeta、view/page、e2e、`nop-metadata-web`、docs——`executionResults`/`executionErrors` 逐面命中/不命中记录入 daily log
- [x] 步骤 2（**字段级等价/损失对照表，先于决策**）：逐键对照——`executionResults` 条目的 6 键（`qualityRuleId/ruleName/status/actualValue/expectedValue/message`）在 `QualityRuleResultDTO` 中何者有承接字段；`executionErrors` 的错误条目键（BizModel 侧与 Scheduler `buildErrorResult` 侧两族）在 `ErrorDTO` 中何者有承接字段；输出"等价 / 可类型化承接 / 无承接（信息损失）"三态表入 daily log
- [x] 步骤 3（裁决，**以对照表为据**）：仅当每个键都有类型化承接（或损失经显式裁定可接受）才允许移除；否则在 in-scope 变体中先补承接再移除：(a) 生产者填充类型化 `errors`（现状从不填充）；(b) `QualityRuleResultDTO` 增补 `ruleName/actualValue/expectedValue` 承接字段；(c) `ErrorDTO` 增补错误条目键承接字段（现仅 `code/message/detail`，对 `source/refType/refValue` 等键）。禁止在信息损失未裁定的情况下删除字段
- [x] 步骤 4（实施）：按裁决终态同步 DTO / 生产者 / Scheduler 写入点 / 测试断言（`TestNopMetaQualityCheckpointBizModel` :333/:334/:361 经 `executionErrors` 的场景断言与 :1097-1101 GraphQL 查询列，重写为类型化字段断言且断言强度不降——逐场景保留原期望值语义）
- [x] 步骤 5（落档）：裁决 + 字段清单 + 契约变更写入 owner doc（API 契约段：executeCheckpoint 返回值字段清单）；**迁移面结论显式记录**："全仓消费面清点 = 零外部消费（web/e2e/xmeta 零命中），无可迁移面"（AGENTS.md 跨模块 API plan-first 的 migration 要求以此为显式结论）；GraphQL schema 运行时生成，无静态 schema 文件需同步
- [x] 交叉引用：本 Phase 重写 `TestNopMetaQualityCheckpointBizModel` 部分断言——`2026-08-16-0549-3` Phase 1 基线以执行时点 live 实数为准（见其 Related）

Exit Criteria:

- [x] 消费面清单 + 字段级等价/损失对照表落档（daily log）；裁决及理由（含所选变体）写入 plan 完成记录
- [x] 若移除：`rg -ln "executionResults|executionErrors" nop-metadata -g '!**/target/**' -g '*.java'` main/test 代码零命中（含 javadoc/注释提及点一并如实化）；DTO 字段清单与 owner doc 一致
- [x] Scheduler 行为等价：其 `buildErrorResult` 错误条目在终态下的去向逐项对照（对照表入 daily log）；`TestMetaQualityCheckpointScheduler*` + `TestNopMetaQualityCheckpointBizModel` 全绿，断言重写后逐场景期望值语义保留（断言强度不降的逐条说明入 daily log）
- [x] `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS
- [x] owner doc API 契约段已更新（字段清单/裁决记录/迁移面结论）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 测试卫生六项修复（P2-35 测试子项）

Status: completed
Targets: 上列六个测试文件

- Item Types: `Fix`

- [x] 项 1（TestAggregationHelper 无 @Test）：live 复核——若确无 `@Test`：要么补充真实断言用例使其名副其实，要么更名/合并为普通 helper（消除 `Test*` 命名误导——注意实为命名与行为不符而非计数膨胀，登记时用准确表述）；二选一裁定记录入 daily log
- [x] 项 2（TestLimitTargetSetCompleteness 正则单一形态）：live 复核正则漏匹配形态，扩展为覆盖全部合法调用形态（或改语义解析）；**证据形态（假阴性洞）**：新增样例单测钉死正则形态——样例单测修复前红/修复后绿；守卫对样例从漏计变计入（构造 `@Name(value="limit")` 等漏匹配样例证明）
- [x] 项 3（TestCoreMetricsUsage 注释剥离假阴性）：复核剥离正则，构造多行注释/字符串内 `//` 样例单测钉死剥离行为（样例单测修复前红/修复后绿同项 2 形态）
- [x] 项 4（TestNopMetadataErrorsCentralized 镜像断言 + 硬编码接口清单）：(a) 镜像断言改独立真值断言（抽样与 `NopMetadataErrors` 常量逐一核对 + 死码面既有门禁衔接，不与被测物互为镜像）；(b) `testAllErrorsUseNopErrPrefix` 硬编码 10 接口清单改源码目录动态枚举（或 ≥N sanity + 新增即红），封 F19 族盲区
- [x] 项 5（testCrossDbAliasOf 仅 assertNotNull）：补值断言（`crossDbAliasOf` alias-or-`"right"` 语义的两态期望值）
- [x] 项 6（TestNopMetaDtoResults 首方法无 parse-back）：`testDtoJsonRoundTrip` 补 parse-back 循环断言（F18 形态：stringify → parse → 关键字段相等）

Exit Criteria:

- [x] 六项逐一完成且每项在 daily log 记录"修复前断言强度 → 修复后断言强度"（含项 2/3 的样例单测红→绿证明、项 5/6 的具体新断言）
- [x] 若任一项 live 复核发现审计描述与实际不符（已是强断言/正则已覆盖）：如实登记为 false positive 并标注证据，不强行改写
- [x] `./mvnw test -pl nop-metadata/nop-metadata-service -am` BUILD SUCCESS；不因断言增强引入非确定性
- [x] default surefire 全绿 + 门禁脚本复跑不红（注：项 2/3/4 所属测试**不在** 6-guard 聚合链内，真实信号以 surefire 全绿为准；门禁复跑为防意外回归）
- [x] `No owner-doc update required`（纯测试变更；如项 1 选择更名则同步 owner doc 测试段提及处）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] P2-17 守卫 14/14（或重扫实数）全覆盖 + 动态全集守卫含变异验证证据
- [x] P2-20 消费面清单 + 裁决落档；移除路径下 main/test 零残留
- [x] P2-35 六项测试子项修复（或如实 FP 登记）且每项有强度对照记录
- [x] `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（0 failures）
- [x] checkstyle / 代码规范检查通过（改动均为测试/DTO，无 main 行为变更——P2-20 生产者/消费者同步除外，其等价性有对照表）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0
- [x] roadmap Follow-up Backlog 对应条目（P2-17/P2-20 + P2-35 测试子项）标注处置结果
- [x] 独立子 agent closure-audit 已完成并记录证据

## Deferred But Adjudicated

（无。Phase 2 如发现计划外消费面选择保留，属裁决路径而非 deferred，在 Phase 内落档。）

## Non-Blocking Follow-ups

- ORM 结构族（P2-01/26/28/29/34）与裁定项（P2-05/33/12）→ 后续轮次（ORM 结构变更需人工确认，见 mission 授权）
- IoC 族 + P2-27 → `2026-08-16-0549-3`

## Closure

Status Note: 三个 Phase 全部落地并经独立 closure audit（20/20 PASS）——P2-17 接口完备性守卫扩至 14/14 全集 + 文件系统扫描程序化守卫（变异验证红→绿双向实证）；P2-20 经消费面清单 + 字段级等价/损失对照表逐键裁定后以变体 (a)+(b)+(c) 全键类型化承接并移除两个 List<Map> 冗余字段（零未裁定信息损失，迁移面 = 零外部消费）；P2-35 六项测试子项逐一修复且各有"修复前→修复后断言强度"对照（项 2/3 含样例单测红→绿证明）。无 in-scope live defect 被降级 deferred。
Completed: 2026-08-16

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（fresh session `ses_ff8284916ffekAvK9RMIPU1Fi6`，review-only 零文件修改）
- Evidence:
  - 逐条 Exit Criterion / 检查点 20/20 全 PASS（Phase 1：:133-135 新接口断言 + :149-185 全集守卫 + 独立重扫 14 非空接口与 COVERAGE 键集逐一相符 + owner doc :192 守卫表述；Phase 2：DTO :21-33 字段清单精确 13 字 + `rg executionResults|executionErrors` 零命中（exit 1）+ QualityRuleResultDTO/ErrorDTO 增补字段与访问器 + mapRuleResults :414-437 六键 / mapErrorEntries :449-470 四族全键 + Scheduler :253-261 source=scheduler + 测试类型化断言（resolution :337-344 / execution :375-378 / scheduler CronReadFailure :105-112）+ owner doc §4 :130-134 裁决与迁移面结论；Phase 3：更名清理 / 正则与样例单测 / 状态机剥离器 / 字面量真值 + 动态枚举 / 三态值断言 / parse-back 全键）；verdict `CLOSURE_AUDIT: approved`
  - 变异验证（执行者，Phase 1 守卫区分力）：新增未登记接口 → 红（`Scan only: [INopMetaMutationProbeBiz]`）；COVERAGE 删方法 → 红（`Scan only: [batchConfirmMatches]`）；恢复 → 绿（daily log 2026/08-16）
  - 样例单测红→绿（执行者，Phase 3 项 2/3）：limit 正则旧 1/4 → 新 4/4；注释剥离多行块注释旧残留 → 新完整剥离（daily log 同条目）
  - `./mvnw test -pl nop-metadata -am -T 1C` BUILD SUCCESS（service **1262/0/0** = 0549-1 收口基线 1259 + 净 3 新测试）；门禁聚合链 `run-nop-metadata-invariants.sh` 全绿 exit 0
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-metadata --severity high` 退出码 0（0 findings）；`node ai-dev/tools/check-doc-links.mjs --strict` 17 errors = 既有跨 mission 基线（本计划文件自身 1 处断裂链接已顺手修复；改动文件 0 新增）；checkstyle 直跑 9164 条 = 既有全仓未接入门禁基线（与本计划改动文件交集 = 0，逐文件名比对）；import-order 检查改动文件 0 命中
  - Deferred 项分类检查：Non-Blocking Follow-ups 仅含 ORM 结构族（人工确认门授权外）与 IoC 族（显式移交 0549-3），无 in-scope live defect 降级
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（收口时复跑确认）

Follow-up:

- 无 plan-owned 剩余工作（IoC 族 + P2-27 已显式移交 `2026-08-16-0549-3`；ORM 结构族按 mission 授权人工确认门另行轮次）
