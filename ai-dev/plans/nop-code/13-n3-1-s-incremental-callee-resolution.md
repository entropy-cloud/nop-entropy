# 13 N3.1-s 增量 callee 解析与依赖方边恢复

> Plan Status: completed(R1 0B/2M/5m 修订 + R2 复审 APPROVE + 独立 closure audit 修订后通过，agent_0a90f910)
> Last Reviewed: 2026-09-28
> Source: `ai-dev/backlog/nop-code-feature-completion-roadmap.md` N3.1-s（confirmed live defect）；plan 11 Deferred 登记（`11-n3-1-incremental-dependency-propagation.md`）；live 核对（本文件 Current Baseline 全部经 2026-09-28 源码复核 + R1 审查 agent_d00e44e5 独立复核）
> Related: plan 11（N3.1 前序，affectedFiles 提供可观测受影响面）、plan 06（N1.4 缓存语义）、plan 12（N3.2，收口时补独立 audit）

## Purpose

修复 confirmed live defect：增量/单文件索引路径上 call 边静默退化。两条退化面：

1. **重分析文件的方法调用边不解析**：单文件 analyze 仅对 TYPE_OF/INSTANTIATES 两类边内联置 calleeId；普通方法调用边（**含同文件方法互调**）只置 calleeQualifiedName（calleeId=null），persist 处被静默跳过不落库。全量流中它们由 `ProjectAnalyzer.resolveCalls` 回填——增量/单文件路径无此步，重分析文件丢失全部方法调用边。
2. **依赖方→重分析文件的既有边被整行删除且无恢复**：`deleteFileRecords` 按 `calleeId ∈ 该文件符号集` 整行删除其他文件指向该文件的 call 边；该文件符号重获新随机 id 后，这些边既不恢复也不重映射。

修复后语义：**增量/单文件索引后的跨文件方法调用边与"同状态下全量重建"等价**（解析器固有限制除外；重载场景边数等价、端点 id 的选取见 Phase 1 tie-break 口径）。

## Current Baseline（live 核对 2026-09-28，R1 独立复核一致）

- **全量流（行为基线）**：`ProjectAnalyzer.resolveCalls(fileResults, globalSymbolTable)`（nop-code-core L329）在 indexDirectory 流程内解析全部调用：精确 qn → 括号去参模糊匹配；成功置 `EdgeConfidence.EXTRACTED`，失败置 `INFERRED`。`saveFileResultInSession` 对 `calleeId==null || callerId==null` 的调用直接跳过不落库（CodeIndexService L1590）。
- **增量流缺陷面**：`triggerIncrementalIndex`（L894）逐文件 `fileAnalyzer.analyze` 后经 `persistSingleFileInSession` → `saveFileResultInSession`（无解析表重载）落库；**calls 无任何 qn→id 解析**——`resolveQualifiedNamesToIds`（L1177）仅覆盖 `inheritance.superTypeId` 与 `annotationUsage.annotationTypeId`。`computeAffectedFiles` 注释明确登记 "Deferred successor: incremental callee resolution"（L1875-1878）。
- **单文件索引同构缺陷（R1 Major-2 核实）**：`indexFile`（L351）同样 analyze → `deleteFileRecords`（连带删除依赖方指向本文件的 call 边）→ `persistSingleFileInSession`（无解析）。与增量流同缺陷族，本 plan 一并收口。
- **符号 id 非确定**：`JavaFileAnalyzer` 全部实体 id 为 `UUID.randomUUID()`（L273/314/550 等），每次重分析全变。
- **删除面**：`deleteFileRecords`（L1929）对每个变更/删除文件删除：本文件 call/symbol/usage/dependency（sourceFilePath）行 + 跨文件行（`NopCodeCall.calleeId ∈ 符号集` L1961、`callerId ∈ 符号集` L1962、inheritance.superTypeId、usage.symbolId）。依赖方→变更文件的 call 边在此整行消失。
- **受影响面（N3.1 已交付）**：`computeAffectedFiles` 在删除**前**沿 `nop_code_dependency` 文件级依赖图 2-hop 传播，产出受影响文件列表（排除变更/删除文件本身），存入 `incrementalAffectedFilesMap`——**可观测面**。边恢复由快照驱动（calleeId ∈ 变更符号集的全部行），不依赖 affectedFiles（同包无 import 的 caller 不在 affectedFiles 中但同样被恢复）。
- **缓存语义（N1.4）**：`CodeCacheManager` 是 DB 派生视图的读缓存，DB 是 source of truth，失效仅发生在索引写路径末尾。增量事务内解析不得依赖该缓存（事务中途缓存内容为前缀状态；`persistSingleFileInSession` L1356 现有用法即为前缀态）。
- **重载 qn 多候选**：方法符号 qn 不含参数（`TypeQn.methodName`），重载共享 qn；全量流 `SymbolTable.getByQualifiedName` 为 HashMap last-write-wins（任意性）。增量侧需显式确定性 tie-break（Phase 1）。
- **ORM 时序先例**：本类 delete-后-同槽-re-save 必须 flush 的坑有现成注释与模式（L1972-1974 `fileDao.flushSession()`）。
- **测试基建**：`TestIncrementalIndexWithDb`、`TestIncrementalDependencyPropagation`（JunitAutoTestCase + `localDb=true` + `@TempDir` + 直注 `ICodeIndexService`）；后者证明 `nop_code_dependency` 行需手工 seed（import resolver 链路 env-dependent），affectedFiles 断言必须先 seed。
- **遗留登记错误**：commit b3d8447b82（N3.2）提交说明声称 roadmap todo→done，但 diff 未含 roadmap 文件——N3.2 行仍为 `todo`，需本 plan 收口时一并回写（含汇总计数）。

## Goals

- **G1 调用解析（增量 + indexFile）**：重分析文件的方法调用边在落库前完成 calleeQualifiedName→符号 id 解析，语义与全量流共享同一实现（精确 qn → 括号去参模糊）。两阶段结构：**全部变更文件先分析完成** → 建内存 qn→new-id 映射（变更文件集新符号）→ 统一解析 → 再 persist（不得 per-file 边分析边解析，否则 changed→changed 边必丢）。库内解析源：按 distinct calleeQualifiedName 批量点查 `nop_code_symbol`（显式排除变更/删除文件旧符号行；in-clause 分块），不依赖事务内写可见性与 CodeCacheManager。成功置 EXTRACTED；失败置 INFERRED（该边不落库，与全量流一致）。
- **G2 依赖方边恢复（增量 + indexFile）**：`deleteFileRecords` 之前快照 (a) 变更文件符号 id→qualifiedName；(b) 变更文件之外、calleeId ∈ 该符号集的 call 行完整数据（**分页读全，禁止单条 limit 静默截断**）。新符号落库后按 qn 重映射重插；目标 qn 已不存在（方法删除/改名）的边显式丢弃（与全量流"该边不再生成"一致）。删除文件的目标边不恢复（目标已不存在）。
- **G3 全程同事务**：快照、删除、解析、重插均在既有事务内；失效语义不变（事务末尾 `invalidateAnalysisCache`）。
- **G4 测试钉住**：集成测试覆盖 G1/G2 正反面 + changed→changed 互调 + indexFile 路径（见 Exit Criteria），复用既有增量测试基建。
- **G5 roadmap/owner docs 同步**：缺口矩阵 N3.1-s 行置 done；roadmap N3.1-s todo→done + N3.2 补回写 + 汇总计数校正；baseline §6.2 增注。

## Non-Goals

- 不改全量流行为（`ProjectAnalyzer.resolveCalls` 语义保持不变）。
- 不做语义边（nop_code_semantic_edge）、inheritance、usage 的增量恢复（见 Non-Blocking Follow-ups 的显式裁定；本 defect 登记面为 call 边）。
- 不改 N3.1 的传播算法与受影响面口径。
- 不引入符号 id 确定性化（更大的架构改动，会改变全量流 id 契约）。
- 不做重载消解（按签名区分重载方法）：全量流本身无此能力（qn 不含参数、HashMap 任意取一），增量侧仅提供确定性 tie-break 对齐"边数等价"。

## Scope

### In Scope

- `CodeIndexService.triggerIncrementalIndex` 与 `indexFile` 两条写路径：变更文件调用解析 + 依赖方边快照/恢复；解析语义与全量流共享同一实现（不复制两份精确/模糊匹配逻辑）。
- 集成测试（nop-code-service）+ 必要的 core 层单测。
- owner docs：`ai-dev/design/nop-code/01-architecture-baseline.md` §6.2、`ai-dev/audits/nop-code/nop-code-feature-gap-matrix.md`、roadmap 状态区。
- 遗留补登记：roadmap N3.2 行回写 done、plan 12 独立 closure audit 补录（见 Phase 3）。

### Out Of Scope

- 语义边/继承边/usages 的增量恢复（预存缺口，量级独立）。
- 其他写路径（`batchDeleteFileRecords`/`deleteIndex` 等纯删除路径无"恢复"语义，不在本 defect 面）。

## Execution Plan

### Phase 1 - 调用解析（G1：triggerIncrementalIndex + indexFile）

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeIndexService.java`、解析语义共享落点（core 或 service，执行期按最小 diff 定）、`nop-code/nop-code-service/src/test/java/`

- Item Types: `Fix`

- [x] 两阶段结构钉死：triggerIncrementalIndex 中全部变更文件先分析完成 → 建内存 qn→new-id 映射 → 统一解析 → 再经 BatchQueue persist（注意 batchSize=1000 分批边界不得回退为"分析一个 persist 一个"）；indexFile 单文件天然满足两阶段
- [x] 解析实现与全量流共享同一精确/模糊匹配代码路径（消除双份语义漂移面）
- [x] 库内解析源：distinct calleeQualifiedName 批量点查 nop_code_symbol（in-clause 分块；显式排除变更/删除文件旧符号行，按 filePath 或 fileId 过滤），不读 CodeCacheManager
- [x] 重载 qn 多候选确定性 tie-break：候选按 (kind, id) 排序取首；测试夹具不依赖重载歧义
- [x] 置信度对齐全量流：成功 EXTRACTED / 失败 INFERRED；INFERRED 边不落库（沿用既有 persist 过滤，不新增静默分支）
- [x] 集成测试 A：全量索引 A(caller)→B(callee) → 修改 A 触发增量 → A→B 边存在于 nop_code_call 且 calleeId == B 当前符号 id（修复前该边丢失）
- [x] 集成测试 B（changed→changed）：A、B 同为变更文件且 A 调 B → 增量后 A→B 边指向 B 新符号 id
- [x] 集成测试 C（indexFile）：全量索引 A→B → `indexFile` 重索引 A → A→B 边落库且指向现存符号 id
- [x] 集成测试 D：同文件方法调用边恢复（修复前同文件普通方法调用边同样丢失，仅 TYPE_OF/INSTANTIATES 幸存）
- [x] 既有测试回归全绿（`./mvnw test -pl nop-code/nop-code-service -am`）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 测试 A-D 全绿：跨文件边落库、calleeId 指向现存符号、changed→changed 互调不丢、indexFile 路径覆盖、同文件方法边恢复
- [x] **无静默跳过**：解析失败路径为显式 INFERRED 置信度 + 既有 persist 过滤，不引入吞异常/空方法
- [x] 解析实现无双份语义漂移（全量/增量共用同一精确/模糊匹配代码路径）
- [x] No owner-doc update required（本 Phase 纯内部行为修复，对外契约无变化；owner-doc 增注统一在 Phase 3 完成）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 依赖方边恢复（G2：triggerIncrementalIndex + indexFile）

Status: completed
Targets: `CodeIndexService`（快照/恢复时序，两写路径共用）、集成测试

- Item Types: `Fix`

- [x] 删除前快照：变更文件符号 id→qualifiedName；变更文件之外 calleeId ∈ 该符号集的 call 行完整字段（id/callerId/calleeId/fileId/line/column/callType/context/provenance/metadata）——按数据快照（非 ORM 托管实体），分页读全，禁止 limit 截断；快照排除变更/删除文件自身的 call 行
- [x] 新符号落库后按 qn 重映射重插：以**新实体实例**携带快照字段值插入（原 id；勿复用 session 中已 MANAGED/DELETED 的快照实体；delete 后同槽 re-save 遵循既有 flush 模式 L1972-1974 先例）
- [x] 目标 qn 不存在的边显式丢弃（计数日志可观测，不复活死边）；删除文件的目标边不恢复
- [x] 快照/删除/解析/重插同事务；triggerIncrementalIndex 与 indexFile 共用同一恢复实现
- [x] 集成测试 E：A→B 全量索引 → 修改 B（方法保持）触发增量 → A→B 边恢复且 calleeId == B 新符号 id（修复前该边被整行删除后丢失）；fixture 需 seed A→B dependency 行方可在 affectedFiles 断言中覆盖 A（沿用 TestIncrementalDependencyPropagation 模式）
- [x] 集成测试 F：B 的目标方法删除/改名 → 增量后旧边不复活（无悬挂行）
- [x] 集成测试 G（indexFile 恢复）：A→B 全量索引 → indexFile 重索引 B → A→B 边恢复且指向 B 新符号 id

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 测试 E-G 全绿：边恢复指向新 id、死边不复活、indexFile 恢复成立
- [x] **端到端验证**：从 `triggerIncrementalIndex`/`indexFile` 入口到 nop_code_call 读回的完整路径断言（测试内直查 DB 行）
- [x] **接线验证**：恢复逻辑真实消费快照产物（代码链路 + 测试断言双重确认）；受影响面可观测（affectedFiles）与恢复解耦——恢复由快照驱动，不误耦合到 affectedFiles
- [x] **无静默跳过**：死边丢弃为显式裁定路径（计数日志可观测），非吞异常；快照读取无 limit 截断
- [x] No owner-doc update required（同 Phase 1，统一在 Phase 3）
- [x] `ai-dev/logs/` 对应日期条目已更新

### 执行备注（偏差与关键发现）

- **夹具通道发现（实证）**：`JavaFileAnalyzer` 的 type solver 仅 `ReflectionTypeSolver`（探针证实：JRE 之外的 classpath 类 `tryToSolveType` 返回 `SymbolReference{null}`，局部变量/字段接收者的 `expr.resolve()` 均抛 `UnsolvedSymbolException`），跨文件 calleeQualifiedName 仅同 CU 与 JRE 类型接收者可产。跨文件 e2e 夹具改走 **JRE 通道**：索引侧放同 qn 合成源码（java.util 包路径下的 ArrayList 声明），调用侧用 JRE 类型接收者（list.isEmpty() 解析为 java.util.ArrayList.isEmpty）——真实的跨文件 qn 边，解析经 DB 点查分支。实现语义与 plan 无偏差；测试 A 的"跨文件"由本通道承载。
- **修复中引入过一次结构损坏**（getProjectFilePaths 签名被编辑锚点错位），当次编译期发现并修复，最终文件经全量回归验证。
- Phase 1 执行时发现并采纳 R2 复审执行提醒：快照为纯数据持有（record CallSnapshot），免疫事务中途 `session.clear()`。

### Phase 3 - 收口与 roadmap/owner docs 同步（G5 + 遗留补登记）

Status: completed
Targets: roadmap 状态区、`ai-dev/audits/nop-code/nop-code-feature-gap-matrix.md`、`ai-dev/design/nop-code/01-architecture-baseline.md` §6.2、plan 12、`ai-dev/logs/`

- Item Types: `Fix | Proof`

- [x] owner docs：baseline §6.2 增注"增量/单文件路径方法调用边解析 + 依赖方边恢复已落地"（最终态表述）
- [x] 缺口矩阵 N3.1-s 行置 done（证据链接本 plan）
- [x] roadmap：N3.1-s todo→done；**N3.2 补回写 done**（commit b3d8447b82 遗漏）；汇总计数校正
- [x] plan 12 补独立 closure audit（子 agent 对 live code 验证 N3.2 exit criteria），证据写入 plan 12 Closure 段
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` exit 0
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0（closure audit 后）
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] roadmap/缺口矩阵/baseline 三处与本 plan 事实一致（N3.1-s done、N3.2 done、计数正确）
- [x] plan 12 Closure 段含独立审计证据（非自验）
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] confirmed live defect（增量/indexFile 路径 call 边两条退化面）已修复且有集成测试钉住
- [ ] 行为等价目标可验证：增量后方法调用边 = 全量重建口径（测试断言 calleeId 存活 + 边存在）
- [ ] 必要 focused verification 完成（`./mvnw test -pl nop-code/nop-code-service -am` 全绿）
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect（语义边增量恢复为 out-of-scope improvement，已显式登记；indexFile 同构缺口已升格 in-scope 并在本 plan 内修复）
- [ ] 受影响 owner docs 已同步到 live baseline（baseline §6.2、缺口矩阵、roadmap）
- [ ] 独立子 agent closure-audit 已完成并记录证据（本 plan Closure 段）
- [ ] **Anti-Hollow Check**：closure audit 验证 (a) 增量/indexFile 路径真实调用解析/恢复逻辑（读回断言），(b) 端到端：写路径入口 → DB 行 →（既有读路径）call graph 可见恢复的边，(c) 无空方法体/静默吞
- [ ] `./mvnw test -pl nop-code/nop-code-service -am` 全绿
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` exit 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-code --severity high` exit 0

## Deferred But Adjudicated

（无——in-scope 无延期项；indexFile 同构缺口经 R1 升格 in-scope）

## Non-Blocking Follow-ups

- 语义边/继承边/usage 的增量恢复：预存缺口（增量路径自始不生成这些行；deleteFileRecords 删除得干净——除变更文件自身行外也整删跨文件 inheritance.superTypeId/usage.symbolId 引用行，属 AR-149/150 预存登记缺口，无悬挂残留）。非本 defect 登记面（roadmap N3.1-s 文本为 call 边 callee 解析与依赖方边恢复）。Classification: `out-of-scope improvement`；Why Not Blocking Closure: 不产生"半新半旧"悬挂面（删得干净、只是不重生成），全量重建可恢复；successor 归属由 roadmap 收口评审（NG.1 对照缺口矩阵）裁定，届时若确认登记为新 WI 则回灌。

## Closure

Status Note: confirmed live defect（增量/indexFile 路径 call 边两条退化面）已修复并有 8 项集成测试钉住；全量/增量解析语义共享同一实现；快照→删除→解析→persist→恢复时序经独立审计逐行核对；owner docs（baseline §6.2/缺口矩阵/roadmap 计数）三处一致。R1 草案审查（0B/2M/5m）与 R2 复审 APPROVE，独立 closure audit 首轮 REVISE 仅文档收尾项（汇总计数/Phase 3 勾选/孤儿 javadoc/follow-up 措辞），全部修订后收口。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agent_0a90f910）
- Evidence:
  - Phase 1/2 Exit Criteria 逐条 PASS（两阶段结构 CodeIndexService L986-1033、共享语义 CallReferenceResolver、点查排除变更文件 notIn filePath、tie-break (kind,id)、纯数据快照、分页读尽、saveReplacingExisting 重插）
  - Anti-Hollow 三项 PASS：(a) 双写路径真实调用 resolve/restore（L1021/L1036、L390/L393）；(b) 8 测试从写路径入口直查 nop_code_call 行断言（JRE 通道跨文件夹具经探针测试钉住）；(c) 死边显式丢弃带计数日志，scan-hollow exit 0
  - 时序 PASS：snapshot→deleteFileRecords→resolve→persist→restore 同事务
  - 全量流等价 PASS：resolveCalls 逐行迁入共享实现，计数语义不变
  - 实跑 PASS：`./mvnw test -pl nop-code/nop-code-service -am` 230/0（service）+ nop-code 全聚合 488/0；check-doc-links --strict 0 errors
  - 首轮审计 REVISE 项全部修订：roadmap 汇总 done 13 · todo 26（N3.1 早前已 done，原头部少计）、Phase 3 勾选与 Status、ProjectAnalyzer 孤儿 javadoc 删除、follow-up 措辞对齐 deleteFileRecords 实际删除面
  - Deferred 分类检查：语义边/继承边/usage 增量恢复为 out-of-scope improvement（删除干净无悬挂、全量重建可愈），非 in-scope defect 降级
  - `node ai-dev/tools/check-plan-checklist.mjs` 退出码 0（收口后复跑）

Follow-up:

- 语义边/继承边/usage 的增量恢复（out-of-scope improvement，successor 归 NG.1 收口评审裁定）
