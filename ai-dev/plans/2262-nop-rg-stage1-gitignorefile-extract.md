# 2262 nop-rg Stage 1 — GitIgnoreFile 抽取到 nop-core

> Plan Status: completed
> Last Reviewed: 2026-09-19
> Source: `ai-dev/backlog/nop-rg-roadmap.md`（Stage 1，GIT-01..04）、`ai-dev/design/nop-rg/01-architecture-baseline.md` 决策 2
> Related: 后续 nop-rg Wave 1-4 计划（待拟制）

## Purpose

将 `GitIgnoreFile` 从 `nop-ai-code-analyzer` 上移到 `nop-core`（新包 `io.nop.core.git`），使其成为平台可复用的 gitignore 公共库，并补齐单元测试。这是 nop-rg roadmap 的 Stage 1，是 Wave 2 ParallelFileWalker（stage 6）的前置条件。

**Protected area 说明**：本计划改动 `nop-core`（AGENTS.md Protected Area：框架核心引擎，plan-first）。本计划即 plan-first 流程中的计划，需通过独立子 agent draft review 后才进入实施。

## Current Baseline

- `GitIgnoreFile.java`（417 行）位于 `nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/git/GitIgnoreFile.java`，实现 `Predicate<IResource>`，完整支持 `*`、`**`、`?`、`[...]`、`!` 反转、`/` 锚定、目录限定规则、嵌套 `.gitignore`、last-match-wins 语义。
- 依赖：`io.nop.commons.util.StringHelper`（nop-commons）+ `io.nop.core.resource` 的 `IResource`/`ResourceHelper`/`VirtualFileSystem`（nop-core）。nop-core 依赖 nop-commons，上移到 nop-core 无循环依赖。
- 消费者共 2 处：`GitProject`（nop-ai-code-analyzer，import + `getGitIgnoreFile()`）、`CliFileCommand`（nop-runner/nop-cli-core，`nop file path-tree`/`nop file find` 命令）。全仓无其他旧包引用（无 XML/beans/反射字符串引用）。
- `GitIgnoreFile` 当前**没有任何单元测试**（nop-ai-code-analyzer 的 `src/test` 存在但 git 包无对应测试）。
- `nop-core` 当前没有 `io.nop.core.git` 包；`nop-core` 测试基础设施存在（junit-jupiter 已声明），且自带 `_vfs/nop/core` 资源，模块内 `CoreInitialization.initialize()` bootstrap 可行（先例：`TestPathTreeNode`）。
- `docs-for-ai/` 无任何 `GitIgnoreFile`/`code_analyzer.git` 引用；`ai-dev/design/nop-rg/01-architecture-baseline.md` 决策 2 已声明 `io.nop.core.git` 目标落位。

## Goals

- `GitIgnoreFile` 类（含 `Rule` 内部类）落在 `nop-core` 的 `io.nop.core.git` 包，原位置文件删除，公开 API 行为不变（`create`/`isIgnored`/`test`/`reload`/`clear`/`getAllRules`/`isEmpty` 语义不变）。
- 两个消费者改用 `io.nop.core.git.GitIgnoreFile`，无残留旧 import，无行为变化。
- `GitIgnoreFile` 有覆盖核心 gitignore 语义的单元测试（此前 0 测试）。
- roadmap Work Item 1（`Extract GitIgnoreFile to nop-core`）状态收敛为 `done`。

## Non-Goals

- 不实现 nop-rg 的任何搜索能力（stage 2+）。
- 不修改 `GitIgnoreFile` 的 gitignore 语义（包括既有的已知简化，如 `[` 字符类未做 `!`→`^` 转换——按现状保持，语义变更属于后续独立决策）。
- 不动 `nop-utils/nop-git`（JGit 绑定模块）。
- 不改 `VirtualFileSystem`/`ResourceHelper` 本身。

## Scope

### In Scope

- 新建 `nop-kernel/nop-core/src/main/java/io/nop/core/git/GitIgnoreFile.java`（包名改为 `io.nop.core.git`，内容与现实现等价）。
- 更新 `GitProject`、`CliFileCommand` 的 import 与引用，删除原文件。
- 在 `nop-core` 新增 `GitIgnoreFile` 单元测试（gitignore 语义 + 嵌套目录 + 反转 + 边界）。
- `docs-for-ai/` owner doc 与 `docs-for-ai/04-reference/source-anchors.md` 的落位同步（若存在引用点；已预核无引用，需 closure 时复核）。
- roadmap `ai-dev/backlog/nop-rg-roadmap.md` Work Item 1 状态更新。

### Out Of Scope

- nop-rg 模块组（stage 2-15）。
- 任何 `IResource`/VFS API 变更。
- gitignore 语义增强或修复（除非测试暴露现实现确实违背其声明的语义，此时按 bug 记录、单独裁定，不在本计划内顺手改语义）。

## Execution Plan

### Phase 1 - 原子迁移：抽取 + 消费者切换 + 删旧

Status: completed
Targets: `nop-kernel/nop-core/src/main/java/io/nop/core/git/`、`nop-ai/nop-ai-skills/nop-ai-code-analyzer/.../project/GitProject.java`、`nop-runner/nop-cli-core/src/main/java/io/nop/cli/commands/CliFileCommand.java`

- Item Types: `Fix`（落位迁移 + import 迁移，无行为变化）、`Decision`（新公共包落位 `io.nop.core.git`，与 design 决策 2 一致）

- [x] 在 `nop-core` 创建 `io.nop.core.git.GitIgnoreFile`（含 `Rule`），仅调整 package 与必要 import，类行为与原实现等价
- [x] `GitProject`、`CliFileCommand` 改用 `io.nop.core.git.GitIgnoreFile`
- [x] 删除 `nop-ai-code-analyzer` 下原 `git/GitIgnoreFile.java`（连同仅因它存在的空目录）
- [x] 全仓 grep 确认旧包路径 `code_analyzer.git` 零残留（含 java/xml/反射字符串）

Exit Criteria:

- [x] `io.nop.core.git.GitIgnoreFile` 存在于 nop-core，原文件已删除，`grep -r "code_analyzer.git"`（排除 target/.git/ai-dev）零命中
- [x] `./mvnw compile -pl nop-kernel/nop-core,nop-ai/nop-ai-skills/nop-ai-code-analyzer,nop-runner/nop-cli-core -am` 通过（原子迁移后无 broken 中间态）
- [x] 行为无变化：`CliFileCommand` 的 path-tree/find 命令与 `GitProject` 的过滤逻辑调用路径不变（仅 import 与包名变化）
- [x] **接线验证**：消费者运行时调用的确实是 nop-core 新类——由旧类删除 + 消费者编译通过共同证明
- [x] No new test required in this phase：迁移 phase 无行为变化，测试覆盖由 Phase 2 显式交付
- [x] `ai-dev/logs/2026/09-19.md` 已更新

### Phase 2 - GitIgnoreFile 单元测试

Status: completed
Targets: `nop-kernel/nop-core/src/test/java/io/nop/core/git/`

- Item Types: `Proof`（语义验证，此前 0 测试）

- [x] 新建 `GitIgnoreFileTest`（JUnit 5）。**前置约束**：`GitIgnoreFile` 的规则加载路径（`ResourceHelper.resolveChildResource` 与 `VirtualFileSystem.instance().getChildren`）都依赖已初始化的 VFS，裸 JUnit 会抛 `ERR_RESOURCE_VIRTUAL_FILE_SYSTEM_NOT_INITIALIZED`；测试须继承 `io.nop.core.unittest.BaseTestCase`（或等效方式执行 `CoreInitialization.initialize()`/`destroy()`），仓库先例 `nop-kernel/nop-core/src/test/java/io/nop/core/resource/path/TestPathTreeNode.java`
- [x] 用真实文件系统目录（`@TempDir` 或 `BaseTestCase` 支持的机制）+ `.gitignore` 文件构造目录树，覆盖：
  - 基础字面量匹配；`*`（单段）；`**`（跨段）；`?`；`[...]` 字符类
  - `!` 反转（后规则覆盖前规则，last-match-wins）
  - `/` 锚定 vs 非锚定（`**/` 前缀展开）
  - 目录限定规则（`dir/` 只匹配目录及其子项）
  - 嵌套子目录 `.gitignore` 的作用域
  - 根目录自身不被忽略；root 外资源被忽略
- [x] 测试验证的是查询结果（`isIgnored` 返回值），不是仅"不抛异常"

Exit Criteria:

- [x] `GitIgnoreFileTest` 存在且断言具体忽略/不忽略结果
- [x] `./mvnw test -pl nop-kernel/nop-core -am -Dtest=GitIgnoreFileTest -Dsurefire.failIfNoSpecifiedTests=false` 通过（`-am` 拉入的上游模块不含该测试，须禁用 failIfNoSpecifiedTests，否则必败）
- [x] `./mvnw test -pl nop-kernel/nop-core -am` 全量通过（无回归）
- [x] **无静默跳过**：测试为真实断言，非空壳
- [x] 若测试暴露现实现违背其声明语义的 bug：记录到 `ai-dev/bugs/` 或本 plan Deferred，不静默、不顺手改语义（结果：17 用例全部按 gitignore 语义一次通过，无语义 bug）
- [x] No owner-doc update required（gitignore 语义与 API 未变，仅落位变化；closure 时 grep 复核 `docs-for-ai` 无 `code_analyzer.git` 引用）
- [x] `ai-dev/logs/2026/09-19.md` 已更新

## Closure Gates

- [x] roadmap `ai-dev/backlog/nop-rg-roadmap.md` Work Items 块中 Work Item 1 已改为 `done`（状态只在 Work Items 块维护，Stage details 不改）
- [x] 所有 in-scope confirmed live defects 已修复（Phase 2 测试未暴露语义 bug：17 用例一次全过；nop-ai-coder 既有 broken test 非本计划 in-scope，见下方偏差裁决）
- [x] 行为/契约结果已达成：nop-core 提供 `io.nop.core.git.GitIgnoreFile`，消费者无旧引用
- [x] 必要 focused verification 已完成（Phase 1-2 Exit Criteria 全勾）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift（checkstyle WARN 已显式裁定）
- [x] 受影响的 owner docs 已同步（预期 No owner-doc update required，需 grep 复核 `docs-for-ai` 中无 `code_analyzer.git` 引用）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 验证消费者运行时确实调用新类（旧类已删 + 全量编译 + 测试通过）
- [x] `./mvnw test -pl nop-kernel/nop-core -am` 通过（实测：294 tests，0 failures，含 GitIgnoreFileTest 17）
- [x] `./mvnw test -pl nop-runner/nop-cli-core -am` 通过——**偏差裁决**：该命令在本机永远失败，因上游 `nop-ai-coder` 的 `AiConverterTest.testConvertOrm` 依赖从未入库的 `test.ai-orm.xml`（git stash 干净 master 实测复现，既有 broken test，与本计划改动无关，nop-ai-coder 非本计划模块）。替代验证已执行并通过：`./mvnw test -pl nop-runner/nop-cli-core -am -Dtest='!AiConverterTest' -Dsurefire.failIfNoSpecifiedTests=false` BUILD SUCCESS，cli-core 自身 8 个测试类 fresh pass（详见 daily log）
- [x] 代码规范检查（可观察判据）：运行 `./mvnw -Pqa checkstyle:check -pl nop-kernel/nop-core` 并核对 console 输出，`io/nop/core/git/**` 无新增 [WARN]（基线以实测为准：nop-core 既有 WARN 共 137 条，判定方法只对比 `io/nop/core/git/**` 新增项；checkstyle severity=warning + failOnViolation=false 导致该命令恒绿，故以 WARN 输出对比为准）。若新增 WARN 无法消除，逐条记录并裁定，不静默通过。另核对 imports 分组等仓库惯例——实测：新增文件 1 条 WARN（globToRegexBody 圈复杂度 18），已裁定记入 Deferred But Adjudicated
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-core --severity high` 退出码 0（实测 EXIT=0，零发现）

## Deferred But Adjudicated

### checkstyle 圈复杂度 WARN：`io.nop.core.git.GitIgnoreFile#globToRegexBody`（18 > 15）

- Classification: `watch-only residual`
- Why Not Blocking Closure: 该方法是从 nop-ai-code-analyzer 原样搬迁的 417 行实现的一部分，复杂度在原位置即已存在（原模块树不在 nop-kernel checkstyle 范围内所以此前不可见）；Non-Goals 明确不改实现语义，AGENTS.md 要求最小 diff、避免噪声重构。重构降复杂度属后续优化候选，不影响本计划的行为等价目标。
- Successor Required: `no`（可由 nop-rg Wave 3+ 性能计划顺带评估，无硬性要求）
- Successor Path: —

## Non-Blocking Follow-ups

- nop-rg Wave 1+ 计划（stage 2-5）另行拟制，不在本计划内。

## Closure

Status Note: Stage 1 全部交付物落地：`GitIgnoreFile` 上移 `nop-core` `io.nop.core.git`（与原实现 diff 零差异），两个消费者切换且无旧引用残留，新增 17 用例单元测试（此前 0 覆盖），roadmap Work Item 1 → `done`。两处偏差均有裁决与独立验证（nop-ai-coder 既有 broken test 与本计划无关；checkstyle 复杂度 WARN 为搬迁携带既有属性，记入 Deferred）。
Completed: 2026-09-19

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure auditor（fresh session，agent_957e7a26-b7e0-43ad-a3ab-a1359cd9faa8，未参与实现）
- Audit Session: agent_957e7a26-b7e0-43ad-a3ab-a1359cd9faa8
- Evidence:
  - Phase 1 Exit Criteria：全部 PASS——`git show HEAD:旧文件` 与新文件剔除 package/import 后 diff 零差异；全仓 grep `code_analyzer.git` 仅剩 ai-dev 文本记述；三模块原子编译 BUILD SUCCESS；消费者 diff 仅 import 行变化
  - Phase 2 Exit Criteria：全部 PASS——审计者独立复跑定向测试（17/17）与全量测试（294/0/0）；测试断言与实现语义逐项吻合，无凑通过断言；场景覆盖对照 plan 列表逐项通过
  - Closure Gates：PASS——roadmap Work Item 1 为 `done` 且其余条目未动（git diff 仅 1 行）；Anti-Hollow 调用链实证（CliFileCommand `handlePathTree`/`handleFind` → `getGitIgnoreFile()` → `GitIgnoreFile.create`，过滤器实际消费 `ignoreFile.test(res)`；GitProject 同）
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（审计者实测）
  - `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-kernel/nop-core --severity high` 退出码 0
  - Anti-Hollow 检查结果：端到端调用链追踪通过（消费两条命令路径真实调用 nop-core 新类）；hollow 扫描零发现
  - Deferred 项分类检查：checkstyle 复杂度 WARN 实测存在且经 `git show` 对比确认为搬迁携带既有属性，watch-only residual 分类诚实；cli-core 偏差裁决经 `git ls-files`（`test.ai-orm.xml` 从未入库）独立验证属实
  - 审计 Minor 事项处置：测试副作用文件 `nop-cli-errors.i18n.yaml` 已 `git checkout` 还原，不混入本计划提交；closure gate 基线表述已修正（137 条既有 WARN，判定方法不变）

Follow-up:

- `nop-ai-coder` 的 `AiConverterTest.testConvertOrm` 缺失 `test.ai-orm.xml` 测试资源（master 既有缺陷，阻断 `./mvnw test -pl nop-runner/nop-cli-core -am`），由 nop-ai-coder owner 修复，不属本计划 scope
- nop-rg Wave 1+ 计划（plan 2263）另行执行
