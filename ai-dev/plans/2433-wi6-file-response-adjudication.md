# 2433 WI6 编码代理关注点归位裁定（core file/response）

> Plan Status: completed（原编号 2429 与 jdk25 会话计划冲突，改号 2433）
> Last Reviewed: 2026-10-11（round 1 对抗审查 1B+4M 修复 + 4 项 Minor 残留登记（含 DslToolImpl 沙箱语义回归拦截与 cli-core 验证命令重写））
> Source: ai-dev/backlog/nop-ai-structure-refactor-roadmap.md（WI6）；ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md（P2-MA1-012 active 裁定）
> Related: 2414（WI1，ChatExchange 门面）/ 2419（WI2 文件工具归属映射）；deps: WI1（已完成）

## Purpose

裁定 core/file 与 core/response 两个编码代理关注点的归宿：response 按排除法留守 core（补定位说明），file 域执行低风险死面收口并为 IFileOperator→IToolFileSystem 全面收口立显式 successor——与 P2-MA1-012 契约文档的前置条件对齐，避免与 active 裁定正面冲突。

## Current Baseline

（2026-10-11 执行时 HEAD 实测，Explore 子代理普查 + 主会话复核。roadmap 消费面基线有两处误差已修正，见附注。）

- **core/file 9 文件 1,980 行**：IFileOperator 348（**@Deprecated(forRemoval=true)** :31，29 方法——18 抽象 + 11 default，内嵌 GrepResult）、LocalFileOperator 449（唯一实现，**零 NopAiCoreErrors/NopAiCoreException 依赖**——用 commons ERR_FILE_* + 平台 kernel/xlang）、FileContent 200 / FileContents 251（XML bundle，import nop-markdown）、FileDiff 117 / FileDiffGenerator 219 / FileDiffApplier 300（三人组）、FileOperatorHelper 70、FileContentParser 26（**全仓零消费者**）。
- **core/file main 消费者实测（含 roadmap 基线外新发现）**：nop-ai-tools（FileToolBizModel :5-8，9 个 IFileOperator 方法面——GraphQL 契约 IFileToolBiz 全 String 化不泄漏 core 类型；DslToolImpl :3-4 **仅用 getResource 一个方法**且 :82-87 已有 VFS 回退）、nop-runner/nop-cli-core（CliFileCommand :3-5，3 个方法：readFileContentsByAntPath/writeFileContents/getResource；模块实有 16 个测试类但 **CliFileCommand 零测试覆盖**——round 1 审查基线勘误）；**新发现**：coder main 资源 `ai-coder.xlib:264,267`（LocalFileOperator.readFileContents）与 translate main 资源 `ai-translate-dir.task.xml:54`（FileOperatorHelper.normalizedMd5）——两条脚本形态消费者不在 roadmap v2.2 基线内。
- **FileDiff 族消费事实**：main 包外零消费者；IFileOperator.applyDiff（default :259-261 → new FileDiffApplier）**无任何调用方**（FileToolBizModel/CliFileCommand 均不调）；LocalFileOperator 不 override applyDiff。toolkit 的 PatchFileExecutor 已用 nop-diff UnifiedDiff、ApplyDeltaExecutor 已用 xlang DeltaMerger——FileDiff 族是平行重复实现。
- **core/response 5 文件 227 行**：Xml 88 / Markdown 38 / Json 51 / Yaml 26（复用 Code）/ Code 24——singleton、无 @Deprecated、无 coder 域依赖。消费者：**core/prompt/ChatExchange.java:6-10（WI1 门面，全 5 Parser）** + ModelBasedPromptTemplate :217-225（经门面）——**response 解析是 core prompt 求值引擎的内在能力**；nop-dyn-service GptCodeGen.java:10,17（经 dsl-orm→core 传递到达，dyn-service pom **无直接 nop-ai-core 依赖**）；dsl-orm TestGptOrmModelParser:13。
- **roadmap 消费面基线两处误差（实测修正）**：①「main 消费者含 nop-ai-tools」不成立（tools 对 5 Parser 零引用）；②「测试侧另有 translate/deepwiki」不成立（translate 消费的是 core/file 的 FileOperatorHelper；deepwiki 只 import core.prompt）。
- **依赖方向排除性证据**：response→coder = dyn-service 被迫新增 coder 依赖（**roadmap 约束条款直接命中**）+ core 的 ChatExchange/ModelBasedPromptTemplate 跨层；response→toolkit = core→toolkit 分层倒置（ChatExchange/ModelBasedPromptTemplate 是 core main 消费者而 toolkit 不依赖 core）。**保留 core 是唯一可行解**。
- **file→toolkit 全面收口成本事实**：toolkit pom 无 nop-ai-core（file 域 core 专属依赖仅 3 错误码 + NopAiCoreException——技术可迁）但 toolkit 无 nop-markdown（FileContent(s) 需要）；IToolFileSystem 17 方法缺 IFileOperator 的 4 块能力（char offset/limit 切片读、FileContents bundle、mergeFile、AntPath/Filter/Regex/ByName finder——契约文档点名后四者无直接调用方）；P2-MA1-012 契约文档裁定「双抽象并存是最终裁决」+ 迁移前置条件三条 + cli-core **零测试护航**；coder xlib 消费者使 coder 需加 toolkit 边（方向合法）。全面收口 = 中-高工作量 + 18 个 BizModel 操作回归面。
- **相关文档**：`ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md`（active 裁定）；`docs-for-ai/01-repo-map/module-groups.md` :102 附近 core 行未提 file/response 归属；`ai-dev/design/nop-ai-agent/04-tool-invocation.md` :151 文件工具四份归属映射（core file 域随 WI6）。

## Goals

- **裁定 1（response）**：留守 core，定位说明 =「LLM 输出结构化解析平台能力」（消费画像：core prompt 求值引擎 + dsl-orm→dyn-service 代码生成链；非编码代理专属）。三去向中 coder/toolkit 被依赖方向排除——记录排除性证据。
- **裁定 2（file）**：本 WI 执行低风险死面收口（FileContentParser + FileDiff 族 3 文件 + applyDiff default 方法 + ERR_AI_FILE_INVALID_EDIT_TYPE 错误码 + 对应测试），DslToolImpl 内联 getResource；IFileOperator→IToolFileSystem 全面收口立显式 successor（前置条件：契约文档三条件 + CliFileCommand 补测试 + IToolFileSystem 补 4 块能力面）。
- 消费者对照表（文件级）落 plan 并回写 owner doc；roadmap 基线两处误差在 log 留档供 WI21 对照。

## Non-Goals

- 不在本 WI 执行 IFileOperator→IToolFileSystem 全面收口（successor 承接——前置条件未满足：IToolFileSystem 能力面缺口 + CliFileCommand 零测试覆盖）。
- **roadmap WI6 状态处置预案（round 1 审查 A6）**：③「消费者同步迁移」移出本 plan 后，WI6 done 判定 = 本裁定 + 死面收口落地 + successor 显式登记（依据/前置条件/重启条件齐备，按 Rules :253 纯裁定项精神）；roadmap WI6 状态行随 closure 翻 `done` 并**括注 successor 登记**（状态行括注为本 WI 新增实践——处置显式可见不无声）；successor plan 执行时编号顺延（不在 roadmap 新增编号 WI——按 Rules 避免无确认立项）。
- 不动 FileToolBizModel 的 9 方法面与 GraphQL 契约。
- 不迁 FileOperatorHelper/FileContent/FileContents/IFileOperator/LocalFileOperator（successor 裁定）。
- 不改 response 5 Parser 的任何实现。

## Scope

### In Scope

- nop-ai-core：删 file/{FileContentParser,FileDiff,FileDiffGenerator,FileDiffApplier}.java、IFileOperator 删 applyDiff default 方法、NopAiCoreErrors 删 ERR_AI_FILE_INVALID_EDIT_TYPE、删 TestFileDiffGeneratorErrorCode、TestNopAiCoreErrorsContract javadoc 契约注记同步（FileDiffApplier:139 行删除后 INVALID_STATE 的 throw-site 清单修正）。
- nop-ai-tools：DslToolImpl 删 IFileOperator 依赖、内联 getResource（VFS 回退已有）。
- owner docs：01-file-operator-abstraction-contract.md 补 WI6 裁定注记（收口 successor）、module-groups.md core 行归属说明、04-tool-invocation.md :151 归属映射核对、roadmap 基线误差 log 留档。

### Out Of Scope

- FileToolBizModel/CliFileCommand/ai-coder.xlib 的 IToolFileSystem 迁移（successor）。
- response 5 Parser 的实现与包位置。

## Execution Plan

### Phase 1 - 死面收口与裁定落地

Status: completed
Targets: `nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/file/`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/NopAiCoreErrors.java`、`nop-ai/nop-ai-core/src/test/`、`nop-ai/nop-ai-tools/src/main/java/io/nop/ai/tools/xdsl/DslToolImpl.java`

- Item Types: `Decision` + `Fix`

- [x] 删除 file 包死面：FileContentParser.java（全仓零消费）、FileDiff.java、FileDiffGenerator.java、FileDiffApplier.java（main 包外零消费；applyDiff 无调用方；能力已被 toolkit nop-diff/xlang DeltaMerger 覆盖）
- [x] IFileOperator 删 applyDiff default 方法（:259-261）+ 同步 :20-21 javadoc 的 applyDiff 提及（round 1 审查 m5：FileDiff 为同包类无 import 语句）；LocalFileOperator 核对无 override（已实证）；NopAiCoreErrors :179-184 孤儿 javadoc 块（INVALID_EDIT_TYPE 头注）随删
- [x] NopAiCoreErrors 删 ERR_AI_FILE_INVALID_EDIT_TYPE（唯一消费者 FileDiffGenerator 随删）；删 TestFileDiffGeneratorErrorCode.java
- [x] TestNopAiCoreErrorsContract javadoc 契约注记同步（round 1 审查 m3 收窄：仅删 INVALID_STATE 清单中「file path: file/FileDiffApplier.java:139」一个 `<li>`——该文件从不提 INVALID_EDIT_TYPE；历史记录 arm-index/旧 plan 不回改）
- [x] DslToolImpl 去废弃依赖（round 1 审查 A2 重写：**禁止纯 VFS 直读**——LocalFileOperator.getResource :64-93 的 base-dir 相对解析（含 "/..." 拼接 baseDir）与 `..`/`:` 拒绝 + canonical 包含性检查是 GraphQL 写路径（saveDslFile @Auth FileTool:write）的沙箱语义，直读即安全回归）：保留 baseDir 字段，内联**等价路径解析 + 路径穿越拒绝**（ERR_FILE_INVALID_PATH 语义平移）；FileToolBizModel:254 构造改 `new DslToolImpl(getProjectDir(projectName))`（getProjectDir 已有 fail-closed 校验）；**TestDslToolImpl 为等价钉必须原样通过**（其 loadDslFile("/model/test.ai-orm.xml") 用例恰好吃 base-dir 解析）；DslToolImpl/IDslTool 自身 javadoc（"backed by legacy IFileOperator"/"do not fix by inlining"）同步重写
- [x] 编译 + 测试（round 1 审查 A5 命令重写：compile-only `-o` 形态会撞 stale jar 且属空验证）：`./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-runner/nop-cli-core -am`；roadmap 验证面要求的 nop-dyn-service（消费面零触碰——response 未动）以 `./mvnw compile -pl nop-dyn/nop-dyn-service -o` 补充核验，若撞并行会话在途改动则按 2424 先例登记偏差

Exit Criteria:

- [x] 4 个删除文件不存在；core/file 剩 5 文件（IFileOperator/LocalFileOperator/FileContent/FileContents/FileOperatorHelper）
- [x] IFileOperator 无 applyDiff；NopAiCoreErrors 无 ERR_AI_FILE_INVALID_EDIT_TYPE
- [x] core/tools 测试全绿 + cli-core 编译通过
- [x] owner-doc 更新于本 plan Phase 2 内完成；`ai-dev/logs/` 对应日期条目已更新

### Phase 2 - 裁定记录与 owner doc 回写

Status: completed

> 顺序约定（round 1 审查）：契约文档裁定注记与 Phase 1 删除**同 commit 落地**（避免中间态 live code 与 active 契约文档 §四相悖）。
Targets: `ai-dev/design/nop-ai/01-file-operator-abstraction-contract.md`、`docs-for-ai/01-repo-map/module-groups.md`、`ai-dev/design/nop-ai-agent/04-tool-invocation.md`

- Item Types: `Decision` + `Proof`

- [x] 01-file-operator-abstraction-contract.md 补 WI6 裁定节：FileDiff 族/FileContentParser 已删除（能力覆盖方 = toolkit nop-diff / xlang DeltaMerger）；IFileOperator→IToolFileSystem 收口立 successor（前置条件三条逐条列出：IToolFileSystem 补 offset 切片读/FileContents bundle/mergeFile/GrepResult 等价面、cli-core 补测试、FileToolBizModel 18 操作回归方案）；response 留守 core 裁定与排除性证据
- [x] module-groups.md core 行补 file/response 归属说明（file = 契约文档裁定的收口中域，response = LLM 输出解析平台能力）；04-tool-invocation.md :151 归属映射行核对更新（core file 域 = 收口中 + successor 指针）
- [x] 消费者对照表（文件级）随裁定节落 plan（本 plan Current Baseline 已含，回写时引用）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

- [x] 三处 doc 更新在位且与 live code 一致
- [x] check-doc-links --strict 0 error

## Closure Gates

- [x] 死面 4 文件删除 + applyDiff/错误码清理完成，DslToolImpl 零 IFileOperator 依赖
- [x] response 留守裁定 + file successor 裁定写入 owner doc（含排除性证据与前置条件）
- [x] `./mvnw test -pl nop-ai/nop-ai-core,nop-ai/nop-ai-tools,nop-runner/nop-cli-core -am` 全绿 + nop-dyn-service 编译核验（或偏差登记）
- [x] 不存在被静默降级的 in-scope live defect；IFileOperator 全面收口为显式 successor（非静默降级——前置条件未满足的显式裁定）
- [x] owner docs 三处已同步
- [x] 独立子 agent closure-audit 已完成并记录证据（APPROVE + punch list 落地）
- [x] Anti-Hollow Check：FileToolBizModel/CliFileCommand 消费面零回归（消费的方法面未触碰）；DslToolImpl getResource 内联后 VFS 回退语义保持
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai/nop-ai-core --severity high` 无本次新增 high 发现（唯一 high = NoOpProviderFailoverQueue:34 占位注释，2026-08-15 既存、WI12 审计范围，本 plan 未触碰该文件）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2433-wi6-file-response-adjudication.md --strict` 退出码 0（自指验证）

## Deferred But Adjudicated

### IFileOperator→IToolFileSystem 全面收口

- Classification: `moved to explicit successor ownership`
- Why Not Blocking Closure: 前置条件未满足（IToolFileSystem 缺 4 块能力面、CliFileCommand 零测试覆盖、18 个 BizModel 操作回归面）——契约文档 P2-MA1-012 的三条前置条件即 successor 的验收清单；本 plan 完成其第 0 步（死面删除缩小收口面）。Classification 取 guide :89 合法终态 `moved to explicit successor ownership`（模板枚举未列，guide 内部不一致，有据）。
- Successor Required: `yes`
- Successor Path: 新立 plan（建议编号顺延），验收 = 契约文档三条件 + cli-core 新增测试 + core/file 目录清空 + FileToolBizModel/CliFileCommand/xlib 消费者迁移

### response 改名/上移（定位为 LLM 输出解析平台能力后的包名评估）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 留守裁定下包名 io.nop.ai.core.response 与定位一致，无变更收益。
- Successor Required: `no`
- Successor Path: 无

## Non-Blocking Follow-ups

- roadmap WI6 消费面基线两处误差（nop-ai-tools 非 response 消费者；translate/deepwiki 非 response 测试消费者）——log 留档，WI21 roadmap 收口对照。
- CliFileCommand 无测试——successor plan 的前置条件之一，本 plan 不补（其消费面未触碰）。

## Closure

Status Note: WI6 两 Phase 完成：response 留守 core（三去向中唯一可行解，排除性证据登记）；file 死面收口落地（FileContentParser + FileDiff 族 3 文件 + applyDiff + ERR_AI_FILE_INVALID_EDIT_TYPE 删除，core/file 剩 5 文件）；DslToolImpl 去废弃依赖且 base-dir 沙箱语义等价内联（TestDslToolImpl 等价钉原样通过）；IFileOperator→IToolFileSystem 全面收口立显式 successor（契约文档三前置条件为验收清单）。closure audit 初判 APPROVE + punch list 全部落地。
Completed: 2026-10-11

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（closure audit，2026-10-11）
- Evidence:
  - A 死面收口 PASS：4 文件 ABSENT、core/file 剩 5 文件、applyDiff/INVALID_EDIT_TYPE/孤儿 javadoc 全清、cli-core i18n 条目同步删除（审计者发现的本 plan 外同步点已确认）
  - B 保留面 PASS：5 保留文件未动、response 5 Parser 未动、nop-lint 同名 record 未误动
  - C 测试 PASS：TestDslToolImpl 等价钉原样在位（:32 base-dir 解析钉）；core 399 / tools 29 绿留档
  - D owner docs PASS（D11 Minor 已修：契约文档补 response 留守指针行）；check-doc-links --strict exit 0
  - E 文本一致性 PASS（Closure Gates 勾选与 Evidence 回填为 closure 收尾动作，本签核完成）
  - scan-hollow（nop-ai-core, severity high）exit 0 留档（签核时实跑）
  - plan 编号冲突登记：`2429-wi4-delete-legacy-workflow-docs-jdk25`（jdk25 会话）与本 plan 并存占用 2429——跨 mission 并发占用，本 plan 改号 2433（沿 2415→2419 先例）

Follow-up:

- IFileOperator→IToolFileSystem 全面收口 successor（前置条件见 Deferred）
- CliFileCommand 补测试（successor 前置条件）
- （承袭）roadmap 基线两处误差 log 留档供 WI21 对照
