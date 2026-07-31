# 2026-07-31-2248-1 结构类 P2 后续批次（P2-MA1-007/009/012）

> Plan Status: completed
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §P2/P3 Deferred Successors；`ai-dev/plans/2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md` Non-Blocking Follow-ups
> Related: `2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md`（已 closed，本计划为其登记的后继批次）
> Mission: audit-remediation
> Work Item: P2 结构类后续批次（P2-MA1-007/009/012）

## Purpose

将 `2026-07-31-1834-3` Non-Blocking Follow-ups 中明确登记的 3 个结构类 P2 finding（P2-MA1-007 / 009 / 012，原文"按严重度排序进入后续批次"）收口：修复 live 缺陷或完成设计裁定并落盘，确保 arm-index §P2 追踪可追溯至 `fixed` / 裁定记录。

## Current Baseline

- **P2-MA1-007（live）**：`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SkillExecutor.java` 存在三个已确认问题：
  - `discoverSkills()` 的空 catch（:124-125）静默吞掉 VFS 读取异常；
  - VFS 无技能时回退返回 5 个硬编码幻影技能（log-analysis/translator/calculator/code-review/test-generator，:127-133），无对应实现，`load` 后无实际能力；
  - `getContextKey()`（:106-108）硬编码返回 `"default"`，`loadedSkills` 为 static map（:21）——该 map 全仓库**只有写入点（:95）无任何读取点**（write-only 死代码 + 无界增长），应整体删除而非"隔离"。
  - 已有 `SkillExecutorTest.java`（nop-ai-toolkit test），其中 `testLoadSkillSuccess`（:88-97）load 的正是幻影技能 `"log-analysis"`——删除回退后该测试**必然失败**，必须同步改写（见 Phase 1）。
- **P2-MA1-009（live）**：`nop-ai/nop-ai-tools/pom.xml` 仅声明 `nop-ai-core` / `nop-ai-coder` / `nop-biz` / `junit-jupiter`，但 `GraphQLToolProvider.java` 直接 import `io.nop.graphql.core.*`（经 `nop-biz` → `nop-graphql-core` 传递获得）。1834-3 已裁定保留该类（P2-MA3-03 ruling，legacy `nopGraphQLToolSet` 消费者契约），故本批次只收敛依赖声明。
- **P2-MA1-012（待裁定）**：`IFileOperator`（`nop-ai-core/.../file/IFileOperator.java`，已 `@Deprecated` 但无 `forRemoval=true`）与 `IToolFileSystem`（`nop-ai-toolkit/.../fs/IToolFileSystem.java`）双抽象并存。`FileToolBizModel.java` javadoc（:24-34）已记录 P2-MA3-05 ruling：两抽象方法面差异大（grep/globGrep/GrepResult、FileContent offset/limit、findFilesByAntPath/findFilesByFilter 无对应），faithful migration 需先收敛抽象。`FileToolBizModel`（18 个 public/protected 方法）与 `DslToolImpl` 仍使用 `IFileOperator`。
- 1834-3 closure audit 已确认：上述 3 项分类为 `out-of-scope improvement` 且明确登记后继批次，不构成 1834-3 的 closure 阻塞。

## Goals

- P2-MA1-007：SkillExecutor 删除空 catch 与硬编码幻影技能回退；删除 write-only 的 `loadedSkills` map 与硬编码 `getContextKey()`（死代码，无读取点）；行为改为显式（VFS 异常记录日志，无技能时返回空列表或 errorResult）；改写依赖幻影技能的既有测试；补 focused 测试。
- P2-MA1-009：`nop-ai/nop-ai-tools/pom.xml` 显式声明 `nop-graphql-core` 直接依赖（版本由根 pom import 的 `nop-bom` dependencyManagement 管理），消除硬传递依赖。
- P2-MA1-012：完成 `IFileOperator` vs `IToolFileSystem` 抽象收敛裁定（Decision），并按裁定执行最小落地（`@Deprecated(forRemoval=true)` + 边界契约文档化；若裁定迁移则迁移 FileToolBizModel）。
- arm-index §P2 修复追踪新增 3 行（finding → 修复/裁定 → 测试/文档证据；当前 arm-index 无这三行，是新增而非更新）。

## Non-Goals

- 不迁移 `GraphQLToolProvider` 到新 tool 契约（P2-MA3-03 已裁定保留 legacy 契约，属 future major 版本工作）。
- 不做 `IFileOperator` 的全量行为重写；如裁定不迁移，保留 FileToolBizModel/DslToolImpl 现状并文档化边界契约。
- 不处理 AskOracleExecutor 真实 oracle 客户端调用（1834-3 已裁定快速失败为最终契约，tool.xml 已文档化）。
- 不拆分 ReActAgentExecutor/DefaultAgentEngine 超大文件（MA4.2-05，既有 optimization candidate 裁定不重开）。

## Scope

### In Scope

- SkillExecutor 行为修复 + 测试
- nop-ai-tools pom 依赖声明
- IFileOperator/IToolFileSystem 抽象收敛裁定 + 最小落地
- arm-index / design 文档同步

### Out Of Scope

- GraphQLToolProvider 契约迁移
- FileToolBizModel 全量功能重写
- 其他 P2/P3 结构项（P2-MA1-013/014/015/016、P3-MA1-038 等，见 1834-3 follow-ups）

## Execution Plan

### Phase 1 - SkillExecutor 硬编码回退与空 catch 修复（P2-MA1-007）

Status: completed
Targets: `nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SkillExecutor.java`、`nop-ai/nop-ai-toolkit/src/test/java/io/nop/ai/toolkit/tools/SkillExecutorTest.java`

- Item Types: `Fix | Proof`

- [x] `discoverSkills()` 空 catch 改为显式处理：VFS 异常记录 SLF4J WARN 日志并返回空列表（不静默吞异常、不伪造数据）；删除 :127-133 硬编码幻影技能回退
- [x] 删除 write-only 死代码 `loadedSkills`（:21 static map，仅 :95 写入无任何读取）与 `getContextKey()`（:106-108）；`handleLoad` 成功后不再记录到 map（该 map 无消费者，删除后行为等价）
- [x] 改写 `SkillExecutorTest` 中依赖幻影技能的用例：`testLoadSkillSuccess`（:88-97，load `"log-analysis"`）改为断言 load 不存在技能时返回 errorResult；`testListSkills` 断言语义改为"VFS 无技能时返回空 `<skills/>`（或空列表）"，不再隐含幻影技能
- [x] 新增 focused 用例：VFS 读取异常路径返回空列表且不产生幻影技能（可构造不可读/不存在的 VFS 路径触发 catch 分支，或在测试中模拟 IResource 异常）；`load` 不存在技能 → errorResult
- [x] 真实加载路径验证：在 `nop-ai-toolkit/src/test/resources/_vfs/nop/skills/<skill-name>/` 放置静态测试资源目录；测试类通过 `CoreInitialization.initializeTo(CoreConstants.INITIALIZER_PRIORITY_REGISTER_COMPONENT)` 初始化 VFS（仓库既有模式，见 `TestDefaultAiChatFunctionTool:39`、`TestCompactionDataPathway:51`）+ `@AfterAll CoreInitialization.destroy()` 清理（避免 MA5.6-AR-1 生命周期串扰）；断言 `list` 输出含该技能名、`load` 该技能成功。若 CoreInitialization 与模块内其他测试类产生并行生命周期冲突，将真实路径验证改为独立测试类运行（`-Dtest=...` 隔离）或记录裁定降级（降级时同步放宽对应 Exit Criteria 与 Closure Gate，需在 plan 内显式记录）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] grep 确认 SkillExecutor 无 `catch (Exception e) {}` 空块、无硬编码技能名回退（文件级 grep `log-analysis|translator|calculator|code-review|test-generator` 在 SkillExecutor.java 0 命中；全仓层面这些字符串在 nop-ai-agent skill 测试中存在但为无关用途，不纳入本计划核验范围）
- [x] `loadedSkills` / `getContextKey` 全仓库 0 引用残留（grep 核验，含测试）
- [x] `SkillExecutorTest` 改写完成且全部通过：空 VFS 列表、异常路径、不存在技能 load 失败；无任何用例依赖幻影技能
- [x] **无静默跳过**：VFS 异常路径有 WARN 日志输出且返回显式空结果（非幻影数据）
- [x] **接线验证**：真实 VFS 资源路径测试（CoreInitialization 初始化 + `_vfs/nop/skills` 静态资源）验证 `list` 含该技能、`load` 成功——证明 handleList/handleLoad 与 discoverSkills 的调用连通且数据来自 VFS；若按裁定降级为错误路径-only，须在 plan 执行记录中写明降级理由与 gate 调整
- [x] No owner-doc update required（行为为缺陷收敛；`ai-dev/design/nop-ai-agent/04-tool-invocation.md` 核验 0 处 skill 描述，核验结果记录于 daily log）
- [x] `ai-dev/logs/2026/07-31.md` 对应条目已更新

### Phase 2 - nop-ai-tools 显式声明 nop-graphql-core 依赖（P2-MA1-009）

Status: completed
Targets: `nop-ai/nop-ai-tools/pom.xml`

- Item Types: `Fix | Proof`

- [x] `nop-ai/nop-ai-tools/pom.xml` 增加 `<dependency>nop-graphql-core</dependency>`（无版本号，由根 pom import 的 `nop-bom` dependencyManagement 管理，核验 `nop-bom` 中 `nop-graphql-core` 版本条目存在），保持 `nop-biz` 依赖不变
- [x] 核验 `GraphQLToolProvider` 及 `GraphQLToolSetFactoryBean` 的 `io.nop.graphql.core` import 全部由新声明的直接依赖满足（`./mvnw dependency:tree -pl nop-ai/nop-ai-tools` 确认 direct scope）
- [x] 编译验证 `./mvnw compile -pl nop-ai/nop-ai-tools -am` 通过

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] pom.xml diff 仅含新增依赖行（无其他依赖变更）
- [x] `./mvnw compile -pl nop-ai/nop-ai-tools -am` 通过
- [x] dependency:tree 显示 `nop-graphql-core` 为 direct dependency（非 transitive-only）
- [x] No owner-doc update required（依赖声明修正，不改契约）
- [x] `ai-dev/logs/2026/07-31.md` 对应条目已更新

### Phase 3 - IFileOperator/IToolFileSystem 抽象收敛裁定与落地（P2-MA1-012）

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/file/IFileOperator.java`、`ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md`（新建目录/文档）、`ai-dev/audits/arm-index.md`

- Item Types: `Decision | Fix`

- [x] 裁定（Decision）：基于 FileToolBizModel javadoc 已记录的方法面差异（grep/globGrep/GrepResult、offset/limit、findFilesByAntPath/findFilesByFilter，实测 18 个 public/protected 方法），裁定迁移 vs 保持双抽象。默认倾向：标记 `@Deprecated(forRemoval=true)` + 设计文档记录边界契约（IFileOperator = legacy core 资源操作；IToolFileSystem = toolkit 沙箱化 FS），FileToolBizModel/DslToolImpl 保留现状并在 javadoc 记录迁移前置条件；若裁定迁移，则将 FileToolBizModel 迁移至 IToolFileSystem 并删除 IFileOperator（工作量更大，需在 plan 内重估）
- [x] 按裁定落地：`IFileOperator` 标注 `@Deprecated(forRemoval=true)`（如裁定迁移则执行迁移并删除接口）
- [x] 设计文档落盘裁定结论（新建 `ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md`，并配套新建 `ai-dev/design/nop-ai/README.md` + 在 `ai-dev/design/README.md` 注册该子目录，遵循 `00-design-writing-guide.md` 目录规范）：接口边界契约、迁移前置条件、consumer 清单（FileToolBizModel/DslToolImpl/FileDiffApplier/LocalFileOperator）
- [x] arm-index §P2 修复追踪新增 P2-MA1-012 行（裁定结论 + 证据链接）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 裁定结论写入 design 文档（明确"迁移"或"保持+forRemoval"二选一，含理由与拒绝项）
- [x] 如裁定保持：`IFileOperator` 类注解含 `forRemoval=true`，编译通过（`@SuppressWarnings("deprecation")` 已存在于 FileToolBizModel）
- [x] 如裁定迁移：FileToolBizModel 编译/测试通过且 `IFileOperator` 引用清零（grep 0 命中）
- [x] arm-index P2-MA1-012 行状态可追溯（`fixed` 或 `fixed（裁定）` + 证据）
- [x] **端到端验证**（如适用，仅迁移裁定）：FileToolBizModel 的 BizModel 操作（file-tool 工具）从入口到文件操作完整路径走通（相关 executor 测试或 FileToolBizModel 测试）
- [x] 相关 owner docs 已同步：`docs-for-ai/` 中涉及 file tool 或 FS 抽象的章节核验并同步；否则明确写 `No owner-doc update required`
- [x] `ai-dev/logs/2026/07-31.md` 对应条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。关闭流程详见本 guide 的 `When Closing The Plan` 和 `Closure Audit Rule`。

- [x] P2-MA1-007 已修复（无空 catch、无幻影技能回退、死代码已删除、有 focused 测试）
- [x] P2-MA1-009 已修复（直接依赖显式声明、编译通过）
- [x] P2-MA1-012 已裁定并落地（design 文档 + forRemoval 或迁移完成）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] arm-index §P2 修复追踪 3 行全部新增并可追溯
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）skill 工具从入口到 VFS 发现/加载的调用链运行时连通（非仅类型存在），（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-ai -am`
- [x] `./mvnw test -pl nop-ai/nop-ai-toolkit,nop-ai/nop-ai-tools -am`（或 `-pl nop-ai -am` 全组）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <本plan文件> --strict` 退出码 0
- [x] checkstyle / 代码规范检查通过

## Deferred But Adjudicated

### P2-MA1-012 全量迁移（若裁定为保持+forRemoval）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 双抽象方法面差异大（FileToolBizModel javadoc 已记录），faithful migration 需先收敛抽象；本计划完成裁定 + 契约文档化，迁移作为 successor 候选由裁定结论决定。
- Successor Required: `no`（裁定结论若为"保持"，无需后继）

## Non-Blocking Follow-ups

- P2-MA1-013/014/015/016 等 P3 结构项：低优先，后续批次（1834-3 已登记）
- P3-MA1-038（GptOrm* 类命名 gpt 命名空间）：P3，后续批次

## Closure

Status Note: 三个 in-scope finding 全部收口（P2-MA1-007 修复、P2-MA1-009 修复、P2-MA1-012 裁定+落盘），无 in-scope live defect 降级；独立 closure audit APPROVE，本 plan 可关闭。
Completed: 2026-07-31

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent `ses_04721a35dffew5CoGNQ6zpqD6H`（fresh session，非实现 session）
- Evidence:
  - Phase 1 Exit Criteria 全部 PASS：SkillExecutor.java 无空 catch（`:117-120` LOG.warn + 返回空列表；外层 `:49-51` errorResult）、幻影技能名 grep 0 命中、`loadedSkills`/`getContextKey` 全仓 0 引用；`SkillExecutorTest` 7 例 + `SkillExecutorVfsTest` 3 例全绿（toolkit 全模块 112 tests 0 failures）；`_vfs/nop/skills/sample-analysis/` 静态资源接线验证（list 含 `sample-analysis`、load 成功，数据来自真实 VFS）；`skill.tool.xml` 示例幻影名已替换；owner-doc 核验 0 处 skill 描述 → No owner-doc update required
  - Phase 2 Exit Criteria 全部 PASS：pom diff 仅含新增 `nop-graphql-core` 依赖块（nop-bom:751 管理版本）；`dependency:tree` 确认 direct scope（模块根级 `+-`）；`./mvnw compile -pl nop-ai/nop-ai-tools -am` PASS
  - Phase 3 Exit Criteria 全部 PASS：裁定 = 保持双抽象 + `@Deprecated(forRemoval=true)`（`IFileOperator.java:31`）；design 文档 `ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md`（裁定/理由/拒绝项/迁移前置条件/consumer 清单）+ `nop-ai/README.md` + `ai-dev/design/README.md:36` 注册；`FileToolBizModel`/`DslToolImpl` javadoc 收口指向 P2-MA1-012 + design 文档；arm-index §P2 三行（:387-389）可追溯；docs-for-ai grep `IFileOperator|IToolFileSystem` 0 命中 → No owner-doc update required；端到端验证 N/A（保持裁定，非迁移）
  - Closure Gates 全部 PASS：compile `-pl nop-ai -am` exit 0；test `-pl nop-ai -am -T 1C` BUILD SUCCESS（0 failures，audit 复核 toolkit 112/tools 3 全绿）；checkstyle 触及文件 0 新违规（IFileOperator 77→77、DslToolImpl 32→31、FileToolBizModel 114→113、toolkit 0；sun_checks 与仓库 checkstyle.xml 双配置一致）；`scan-hollow-implementations.mjs --module nop-ai --severity high` 24 项既有基线不变（SkillExecutor 0 命中）；`check-doc-links.mjs --strict` exit 0（0 errors，1 条 warning 为兄弟计划 2248-3 既有 broken link，非本 plan 引入）
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-2248-1-arm-p2-structural-successor.md --strict` 退出码 0（全项勾选 + Closure Evidence 已写入）
  - Anti-Hollow：SkillExecutorVfsTest 以真实 VFS 断言证明 handleList/handleLoad → discoverSkills 运行时调用链连通（非仅类型存在）；无空方法体/静默跳过/no-op 作为正常实现（WARN + 显式空列表/errorResult）
  - Deferred 项分类检查：仅 P2-MA1-012 全量迁移为 `watch-only residual`（方法面收敛前置 + 非阻塞理由完整）；Non-Blocking Follow-ups 均为 1834-3 登记的 out-of-scope 项，无 in-scope live defect 降级

Follow-up:

- no remaining plan-owned work（P2-MA1-013/014/015/016、P3-MA1-038 等 P3 结构项继续由 1834-3 follow-ups 登记，见 plan `Deferred But Adjudicated` / `Non-Blocking Follow-ups` 段）

## Optional Sections

## Risks And Rollback

- SkillExecutor 行为变更（幻影技能移除）：文件级核验（SkillExecutor.java 与 SkillExecutorTest.java 内 `log-analysis|translator|calculator|code-review|test-generator`）确认无残留后删除；全仓其他文件中这些字符串为无关用途（nop-ai-agent skill 测试），不属本计划引用面。
- `loadedSkills`/`getContextKey` 删除：write-only 死代码（无读取点），删除后行为等价；如有调用方依赖（grep 核验 0 命中），先改调用方再删。
- SkillExecutorTest 引入 CoreInitialization：有 MA5.6-AR-1 已知生命周期串扰风险——测试类 `@AfterAll destroy()` + 必要时独立测试类运行隔离；若裁定降级真实路径验证，需同步放宽 Exit Criteria 与 Closure Gate（plan 内记录）。
- P2-MA1-012 裁定若选迁移：回归风险高（18 个 BizModel 方法 + DslToolImpl），需 `./mvnw test -pl nop-ai/nop-ai-tools -am` 全绿；单 commit 可回滚。
