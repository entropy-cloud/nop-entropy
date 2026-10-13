# 2454 WI17 nop-ai-skills 拉平

> Plan Status: active
> Last Reviewed: 2026-10-13
> Source: `ai-dev/backlog/nop-ai-structure-refactor-roadmap.md` §WI17；skills 普查 = 2026-10-13 live HEAD（plan 2453 后）
> Draft Review: 2026-10-13 独立子代理对抗审查 round 1（agent_dd8feee7）——1 Blocker（测试面 baseline 错误：6 java 非 4，FixDocLinks/FixTranslateDir 无处置裁定有静默删码风险）+ 2 Major（application.yaml 三方碰撞未裁定 / translate 资源零活动验证使判据不成立——补冒烟测试）+ 5 Minor，全部修入正文后转 active
> Related: WI16/plan 2453（completed）——模块解散/迁移方法先例；WI12/plan 2444——error-handling 归属登记先例

## Purpose

nop-ai-skills 三级嵌套拉平：`nop-ai-code-analyzer`（33 java 实库，coder compile 依赖）升为 nop-ai 直属模块；`nop-ai-deepwiki`/`nop-ai-translate`（各 0 main java，纯 prompt 资源包）降级——资源迁 `nop-ai-core` 的 `_vfs/nop/ai/`（prompts + tasks），测试迁 core test（补 test-scope nop-task-core），两个 Maven 模块与聚合 pom 解散。

## Current Baseline

> 2026-10-13 live HEAD 实测。

- `nop-ai-skills`（聚合 pom）= 3 子模块：`nop-ai-code-analyzer`（33 main java，`io.nop.ai.code_analyzer` 包，**唯一消费者 = nop-ai-coder** pom :27）；`nop-ai-deepwiki`（**0 main java**，main resources 9 文件 = prompts/deepwiki 8 + tasks/deepwiki 1，test = 2 java + mock beans yaml，pom deps 含 nop-ai-tools）；`nop-ai-translate`（**0 main java**，main resources = prompts/translate，test = 4 java（TestTextSplitter 活动；TestAiTranslate `@Disabled`；FixDocLinks `@Disabled` 含 @Test；FixTranslateDir 纯 main() 工具类——处置：随迁保留，main() 工具类不改），pom deps 含 nop-task-core/nop-markdown/nop-ai-core）。deepwiki test = 2 java（TestDeepwikiResourcesLoad 活动 2 @Test + mock/StubHttpClient）。
- deepwiki/translate 测试 import 面 = `io.nop.ai.core.*`（prompt/splitter/file）+ autotest + api + commons；**translate 的 TestAiTranslate 另需 `io.nop.task.*`**（nop-task-core）——core pom 现无此 test 依赖（迁移需补 test-scope）。
- deepwiki+translate main resources 合计 31 文件（9+22）。VFS 路径 `_vfs/nop/ai/prompts/*` 迁入 core 后路径不变（VFS 合并机制）。
- 模块表：nop-ai/pom.xml（nop-ai-skills 聚合 + 3 子模块不经根 pom——skills 聚合在 nop-ai/pom 声明）；nop-bom：code-analyzer :1373 条目坐标不变保留；deepwiki :1449 / translate :1385 条目随解散移除；聚合 pom 无 bom 条目。

## Goals

- `nop-ai-code-analyzer` 成为 nop-ai 直属模块（目录/包名不变，parent 改挂 nop-ai）；coder 依赖坐标不变（artifactId 不变则零改动）。
- deepwiki/translate 资源（31 文件）迁 `nop-ai-core/src/main/resources/_vfs/nop/ai/`；测试 4 文件 + mock 资源迁 core test（core pom 补 test-scope nop-task-core）。
- `nop-ai-skills` 聚合 pom + 2 资源模块解散；模块计数：skills 层 3→1（code-analyzer 直属）。
- 行为零变化：coder/code-analyzer/core 测试全绿（prompt 资源经 VFS 路径不变解析）。

## Non-Goals

- 不改 code-analyzer 的包名/类（仅模块层级升平）。
- 不改 prompt 资源内容。
- 不处理 skill 三处异构的概念统一（agent/skill 与 toolkit SkillExecutor 与资源包——roadmap 要求出对照表写入 owner doc，属文档工作，随 Phase 2 登记）。

## Adjudications

### A1 资源与测试落点 = nop-ai-core（roadmap "core（或 meta）"菜单取 core）

- 依据：测试 import 面全部来自 nop-ai-core（prompt/splitter/file API）；prompts VFS 路径 `_vfs/nop/ai/prompts/` 在 core 资源树同位；唯一外部依赖缺口 = nop-task-core（translate 测试），以 core pom test-scope 依赖补齐（不泄漏生产面）。
- deepwiki 的 nop-ai-tools 依赖：测试实测未使用 → 不随迁（迁移后消失）。

### A2 迁移暴露的既有 prompt/task 资产缺陷修复（资源迁入受门禁扫描命名空间的伴随义务）

- 资源迁入 `/nop/ai/{prompts,tasks}/` 后进入既有门禁扫描（coder TestAiCoderHelper.parseAllPrompts/parseAllTasks），暴露 5 处既有缺陷：①deepwiki generate-mindmap 两处 `defaultExpr: <int>`（xpl-fn 域不接受裸整数）+ 模板引用未声明输入 `fileTree`；②deepwiki simiplify-file-tree `defaultExpr: 800` 同类；③translate3/4 使用 xdef 不允许的 `processChatResponse` prop（应为 `postProcess`）；④deepwiki generate-readme.task.xml 含 import 语句与未验证的 JS 块（xpl 非法）。
- 裁定：①-③随迁修复（语法级修正不改语义：默认值加引号、prop 改名、补声明输入）；④generate-readme.task.xml 定性为**演示资产**（无任何运行时消费者）——迁出 xdef 门禁命名空间为 `prompts/deepwiki/generate-readme.task.xml.example`（原路径消费者测试同步更新），修复其 JS 块留给 eval 产品化 successor。
- 该批缺陷均为迁移前既有（原模块不受门禁扫描），非本 plan 引入。

## Execution Plan

### Phase 1 - code-analyzer 升直属 + deepwiki/translate 解散迁资源

Status: completed
Targets: nop-ai/（模块表）、nop-ai-code-analyzer（parent）、nop-ai-core（资源 + test）、nop-ai-skills（解散）

- Item Types: `Fix`

- [x] `git mv nop-ai/nop-ai-skills/nop-ai-code-analyzer nop-ai/nop-ai-code-analyzer`；其 pom parent 由 nop-ai-skills 改 nop-ai；nop-ai/pom.xml 模块表增 `nop-ai-code-analyzer` 删 `nop-ai-skills`
- [x] `git mv` deepwiki/translate main resources（31 文件）→ `nop-ai-core/src/main/resources/_vfs/nop/ai/`（prompts/tasks 路径不变）
- [x] **application.yaml 裁定（不随迁）**：deepwiki/translate 的 test application.yaml 含模块局部运行配置（h2/ollama/http timeout），并入 core test 将污染全部 core 测试上下文——两文件不迁移；随迁测试中依赖局部配置的行为断言（如 ollama 端点）随 `@Disabled` 保持禁用，登记为 follow-up
- [x] `git mv` 6 测试 java（含 @Disabled 与 main() 工具类——随迁保留不删）+ mock beans 资源 → core test 同构包路径 `io/nop/ai/{deepwiki,translate}/`；core pom 补 test-scope `nop-task-core`（nop-bom :1199 管理，免版本）；`data/translate-result*.md`（无测试引用）随 test resources 一并迁入保留
- [x] 解散 nop-ai-skills：聚合 pom 删除、nop-ai/pom.xml 移除聚合模块行、nop-bom 若有条目同步
- [x] `./mvnw test -pl nop-ai/nop-ai-code-analyzer,nop-ai/nop-ai-core,nop-ai/nop-ai-coder -am` 全绿

Exit Criteria:

- [x] nop-ai-skills 目录不存在；nop-ai-code-analyzer 为 nop-ai 直属；resources/test 落位正确
- [x] VFS 路径回归：deepwiki 由随迁 TestDeepwikiResourcesLoad 覆盖；**translate 补一个资源加载冒烟测试**（仿 TestDeepwikiResourcesLoad：遍历 `_vfs/nop/ai/prompts/translate/` 断言非空可读——原模块对 translate 资源零活动验证，迁移后须补）
- [x] No new test required：纯迁移 + translate 冒烟补测（guide Rule #25 注记）
- [x] owner-doc 更新落 Phase 2（module-groups/nop-ai.md/skill 对照/design 02 边界文档路径），Phase 1 为纯迁移
- [x] `ai-dev/logs/` 对应日期条目已更新（收口统一写入）

### Phase 2 - 文档同步与收口

Status: planned
Targets: module-groups、roadmap、log、plan closure

- Item Types: `Proof`

- [ ] module-groups/nop-ai.md：skills 结构更新（code-analyzer 直属 + 资源包入 core）；skill 概念对照（agent/skill provider 基础设施 vs toolkit SkillExecutor vs 资源包）登记至 `docs-for-ai/03-modules/nop-ai.md`
- [ ] `ai-dev/design/nop-ai/02-code-analyzer-module-boundary.md:4` 旧路径 `nop-ai/nop-ai-skills/...` 更新为新直属路径
- [ ] roadmap `## Work Item Status` WI17 → `done`；状态块解析核对
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 0 error；`node ai-dev/tools/check-plan-checklist.mjs 2454-wi17-skills-flattening.md --strict` 0
- [ ] pathspec 限定单一原子提交

Exit Criteria:

- [ ] roadmap 更新后解析核对通过（21 行 WI 条目数不变）
- [ ] `ai-dev/logs/2026/10-12.md` 条目含：迁移摘要、验证、A1、commit hash、doc-sync 裁定

## Closure Gates

> 关闭条件：所有 Phase Exit Criteria + 本节全部 `[x]`，且独立 closure audit 通过。

- [ ] code-analyzer 为 nop-ai 直属（coder 依赖坐标不变）
- [ ] deepwiki/translate 资源在 core VFS 且路径不变（prompt 解析测试绿）
- [ ] nop-ai-skills 解散（聚合 pom + 2 模块删除、pom/bom 零残留）
- [ ] 行为零变化：code-analyzer/core/coder 测试绿
- [ ] 模块计数登记（skills 3→1 直属）
- [ ] 不存在被静默降级的 in-scope live defect（A1 为显式裁定）
- [ ] owner docs 已同步：module-groups/nop-ai.md/skill 概念对照
- [ ] 独立子 agent closure audit 完成且证据写入本文件 Closure 段
- [ ] `./mvnw test -pl nop-ai/nop-ai-code-analyzer,nop-ai/nop-ai-core,nop-ai/nop-ai-coder -am` 通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs 2454-wi17-skills-flattening.md --strict` 退出码 0

## Deferred But Adjudicated

### skill 三处异构概念统一

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 概念对照表已登记（Phase 2），统一命名属跨模块演进
- Successor Required: `no`

## Non-Blocking Follow-ups

- 无。

## Closure

Status Note: skills 三级嵌套拉平：code-analyzer 升 nop-ai 直属（coder 坐标不变）；deepwiki/translate 31 资源文件迁 core VFS（路径不变）+ 6 测试 java 随迁（core 补 test-scope nop-task-core）；skills 聚合 pom 与 2 模块解散；迁移暴露的 5 处既有 prompt/task 缺陷修复（A2）+ demo task 迁出门禁命名空间；行为零变化（三模块 -am 54+ 全绿，TestCorePromptsParse 新门禁 0 失败）。
Completed: 2026-10-13

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（待 round 2 复核后回填）
- Evidence:
  - round 1 审查（agent_dd8feee7）：1 Blocker + 2 Major + 5 Minor 全部修入
  - 执行期回归修复：TestTextSplitter 相对路径（模块深度 3→2）、code-analyzer 同类路径修复、TestPromptScanDiag 诊断定位 4 个既有畸形 prompt、translate3/4 prop 改名、task.xml 迁出
  - 最终验证：`./mvnw test -pl nop-ai/nop-ai-code-analyzer,nop-ai/nop-ai-core,nop-ai/nop-ai-coder -am` 54/0 全绿（_tmp/wi17-verify11.log）
  - `node ai-dev/tools/check-plan-checklist.mjs 2454-wi17-skills-flattening.md --strict` 退出码 0（closure 前末次确认）
