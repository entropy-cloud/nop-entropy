# 2576 nop-git Wave 1: 骨架与 API 门面

> Plan Status: active
> Last Reviewed: 2026-11-20
> Source: `ai-dev/backlog/nop-git-roadmap.md` Wave 1 (items 1-6)；`ai-dev/design/nop-git/00-vision.md`、`01-architecture-baseline.md`、`04-api-integration-design.md`
> Related: 后续 Wave 2-6 各自立项

## Purpose

落地 nop-git 模块组骨架与 Java API 门面（roadmap items 1–6），使平台内 Java 代码可通过 `IGitRepositoryService` 完成 open/init/status/add/commit/log/branch/resolveRevision 全流程，不接触 JGit 类型；JGit 收敛在 `JGitRepositoryService` 适配层内，并有架构支柱测试钉死「`io.nop.git` 包外禁止 import org.eclipse.jgit」。

## Current Baseline

- `nop-tools/pom.xml` modules 列表无 nop-git；`nop-tools/` 下已有 nop-rg / nop-find / nop-jq / nop-lint / nop-refactor / nop-treesitter / nop-bytecode / nop-ib。
- `nop-tools/nop-rg` 是 multi-module 布局先例：父 pom（packaging=pom，JDK 激活 profile 挂子模块）+ bom + core + cli。
- 根 pom `maven.compiler.release=25`（pom.xml:30）；子 pom 可覆写 `maven.compiler.release` 属性。
- 本地 ~/.m2 已有 `org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r` 及其依赖（javaewah、slf4j）；本机 git 2.54.0 可用于后续 golden 录制（Wave 2 范围）。
- nop-ib 服务（127.0.0.1:8188）健康，编译/测试走 nop-ib。
- `io.nop.git` 包在 `nop-utils/nop-git`（**执行期发现，design 文档未覆盖**）已存在一个 legacy 小模块：「简化版 Git 仓库操作接口，仅支持单分支模型文件管理」（`IGitRepository`/`GitRepositoryImpl`，依赖 nop-core + JGit，artifact `nop-git` jar packaging）。裁决：(a) 新聚合 pom artifact 改名 `nop-git-parent`（目录仍 `nop-tools/nop-git`），避免 GAV 冲突；(b) legacy 模块原地保留不动，其包 `io.nop.git` 天然满足支柱规则（包内可 import JGit），Wave 6 item 30 评估时再决定是否迁移到新 core；(c) 新 API 类名 `IGitRepositoryService` 与 legacy `IGitRepository` 不同名，同包共存无冲突。
- JGit 版本在仓库内此前无任何 dependencyManagement 声明（legacy 模块裸依赖，版本来自根 BOM 传递/未钉死）；`nop-git-bom` 的声明点地位不受影响。

## Goals

- `nop-tools/nop-git` 四模块拓扑成立：`nop-git`（父 pom，packaging=pom）+ `nop-git-bom`（JGit 版本唯一声明点）+ `nop-git-core`（API 门面 + JGit 适配层）+ `nop-git-cli`（占位模块，main 空 shell 到 Wave 2 实现）。接入 `nop-tools/pom.xml` modules。
- 运行基线 JDK 21：nop-git 父 pom 覆写 `maven.compiler.source/target=21`（根 pom compiler plugin 消费的是 source/target property，无 `<release>` 标签）；core 内启动校验 `Runtime.version().feature() >= 21`，不满足抛 `NopGitException`（英文消息）。**裁决：nop-git-core 不依赖任何仓库内部模块（含 nop-core/nop-api-core，均为 class v69 / Java 25 编译，会隐式抬高基线）**，错误面用模块级 `NopGitException`；JDK 21 基线因此真实可达，CLI 可在 JDK 21 上独立部署。design 04 的 NopException 契约相应修订（owner doc 随本 Wave 更新）。
- 自有结果模型落地：`NopGitStatus` / `NopGitCommit` / `NopGitDiffEntry` / `NopGitRef` + `DiffOptions` / `LogOptions`（`io.nop.git.api` 包）。
- `IGitRepositoryService`（`io.nop.git.api`）+ `JGitRepositoryService`（`io.nop.git.core`）实现 open/init/status/add/commit/log/branchList/resolveRevision/currentBranch；`AutoCloseable`。**裁定：本 Wave 接口是 design 04 契约的真子集（无 policy 参数版本，Wave 3 接入引擎时扩签；checkout/reset/clean/worktree/push 由 Wave 4+ 增量扩签），不与 owner doc 冲突。**
- 空仓库语义：无 HEAD 时 `log` 返回空、`currentBranch` 返回 null、`resolveRevision("HEAD")` 返回 null；`init` 默认分支跟随配置（默认 `master`）。
- 架构支柱测试：全模块源码扫描，`io.nop.git` 包以外（含 cli 模块）禁止 import `org.eclipse.jgit`。
- 每个公开门面方法内部经 `IGitPolicyEngine` 裁决的**接口形态**在本 Wave 仅预留（Wave 3 落地引擎）。落地裁决（closure audit Minor-1 修正）：过渡性可选 policy 参数省略未实现——构造器无 policy 入口，Wave 3 直接增签 `open(File, IGitPolicyEngine)`，避免引入又移除的临时参数面。

## Non-Goals

- CLI 子命令实现与输出格式化（Wave 2，nop-git-cli 仅占位 + 空可构建模块）。
- 策略引擎实现、YAML 配置、审计日志（Wave 3）。
- worktree / checkout / reset / clean / push 等 Wave 1 未列门面方法（后续 Wave 按需加）。
- golden 对照测试框架（Wave 2 item 10）。
- beans.xml / Nop IoC 声明（Wave 6 item 29；本 Wave 为纯 Java 库，测试直接 new）。

## Scope

### In Scope

- `nop-tools/pom.xml` 增加 `<module>nop-git</module>`（JDK ≥21 激活 profile 包裹，同 nop-rg 模式）。
- `nop-tools/nop-git/` 下新建 `pom.xml`、`nop-git-bom/`、`nop-git-core/`、`nop-git-cli/`。
- core 源码：`io.nop.git.api`（接口 + 模型 + Options + NopGitErrors/ErrorCode）与 `io.nop.git.core`（JGitRepositoryService + JDK 版本校验）。
- 测试：门面行为测试（temp dir 真实仓库）、空仓库语义测试、架构支柱扫描测试、JDK 校验测试。

### Out Of Scope

- `nop-git-server`（Wave 5）；`docs-for-ai/` 使用文档（Wave 6 item 31 统一收口，本 Wave 只按需更新 source-anchors）。
- 对 `nop-api-core` 或任何既有公共 API 的修改（Protected Areas 不涉及——全新模块）。

## Execution Plan

### Phase 1 - 模块骨架与依赖锁定

Status: completed
Targets: `nop-tools/pom.xml`、`nop-tools/nop-git/**`

- Item Types: `Feature`

- [x] `nop-tools/nop-git/pom.xml`（parent=nop-tools，packaging=pom，name=`43-nop-git`，properties 覆写 `maven.compiler.source/target=21`，JDK `[21,)` 激活 profile 挂全部四个子模块，同 nop-rg 布局）
- [x] `nop-git-bom`（standalone 坐标 pom，与 nop-rg-bom 同模式：无 parent，但被 nop-git 父 pom profile 的 `<modules>` 显式挂入 reactor；dependencyManagement 声明 core；JGit `org.eclipse.jgit` 版本 7.7.1.202607240634-r 在此唯一声明）
- [x] `nop-git-core`（parent=nop-git；**零仓库内部依赖**，仅 org.eclipse.jgit + junit-jupiter test）
- [x] `nop-git-cli` 占位（parent=nop-git，仅依赖 core + junit；本 Wave 仅 `package-info.java`，保证可构建）
- [x] `nop-tools/pom.xml` modules 增加 nop-git
- [x] nop-ib `model refresh`（pom 变更后刷新构建事实）

Exit Criteria:

- [x] `ib check --paths nop-tools/pom.xml nop-tools/nop-git/pom.xml`（或全量 compile）退出码 0，四模块进入 reactor
- [x] bom 中 JGit 版本为唯一声明点（grep 无其他 JGit 版本字面量）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - API 模型与门面接口

Status: completed
Targets: `nop-git-core/src/main/java/io/nop/git/api/`

- Item Types: `Feature`

- [x] 结果模型：`NopGitStatus`（分支、clean 标志、added/changed/removed/untracked/staged 路径集合）、`NopGitCommit`（id、abbrevId、authorName/email、commitTime、shortMessage、fullMessage、parentIds）、`NopGitDiffEntry`（changeType、oldPath、newPath、insertions/deletions）、`NopGitRef`（name、shortName、objectId、isBranch）
- [x] Options：`DiffOptions`（cached/againstRev/stat 语义所需）、`LogOptions`（maxCount、rev、oneline 所需字段）
- [x] `IGitRepositoryService` 接口（open/init static 工厂 + status/diff/log/currentBranch/resolveRevision/branchList/add/commit + AutoCloseable），签名全自有类型
- [x] `NopGitException`（模块级异常，英文消息；Wave 1 无 nop-core 依赖，两层错误策略中的 NopException+ErrorCode 面推迟到 Wave 6 IoC 集成时以适配方式引入——届时若引入 nop-core 依赖需重新裁定 JDK 基线并在 plan 中显式记录）

Exit Criteria:

- [x] 接口与模型编译通过；无任何 JGit 类型出现在 `io.nop.git.api` 签名中（源码 grep 证明）
- [x] 新类型各有 Javadoc 说明字段语义（英文）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - JGit 适配层与生命周期语义

Status: completed
Targets: `nop-git-core/src/main/java/io/nop/git/core/`

- Item Types: `Feature`

- [x] `JGitRepositoryService`：open（workDir 发现 .git）、init（defaultBranch 可配，默认 master）、status/add/commit（身份从 git config 链读取，缺失报英文错误 NopGitException）/log/branchList/resolveRevision/currentBranch/close
- [x] JDK 21 基线校验类（`Runtime.version().feature() >= 21`，失败 NopGitException 英文消息），在 open/init 入口调用
- [x] 空仓库语义：无 HEAD 时 log 空列表、currentBranch null、resolveRevision null、branchList 仅含 unborn HEAD 指向分支（或空，按实现取真实行为并在测试钉死）
- [x] Repository 生命周期：try-with-resources 可关闭，close 后再调用抛 IllegalStateException

Exit Criteria:

- [x] 门面行为测试（JUnit 5 + temp dir 真仓库）：init→add→commit→log→branchList→resolveRevision 全链路断言（端到端验证：从 open 入口到 commit 结果出口）
- [x] 空仓库语义测试逐条断言
- [x] close 后调用测试
- [x] commit 身份缺失场景报 NopGitException（非静默跳过验证）
- [x] JDK 校验测试：以可注入的最小 feature 值驱动（当前 JDK 26 下无法真实触发 21 门槛，校验类暴露包内可见的 `requireFeature(int min)`，测试以 min=99 断言抛异常路径真实可达）
- [x] `ib check` 相关路径退出码 0
- [x] No owner-doc update required（新模块，docs-for-ai 收口在 Wave 6；source-anchors 若路由变化则在 Phase 4 一并处理）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 架构支柱测试与收口

Status: planned
Targets: `nop-git-core/src/test/java/io/nop/git/`、roadmap、logs

- Item Types: `Proof`

- [x] 架构支柱测试：测试内按相对路径遍历 `${project.basedir}/../nop-git-{core,cli}/src` 全部 `.java` 源文件，正则扫描断言 `io.nop.git` 包之外无 `org.eclipse.jgit` import（覆盖 cli 占位；core 自身在 `io.nop.git` 包内天然合规）
- [x] roadmap `Work Item Status`：items 1-6 置 done、Milestone「Java API 集成可用」置 done
- [x] owner doc 同步（reconcile-on-touch）：`ai-dev/design/nop-git/00-vision.md` §五 补「core 零仓库内部依赖以保 JDK 21 基线」裁定；`04-api-integration-design.md` 错误面修订为 NopGitException（NopException 适配推迟 Wave 6）
- [x] `docs-for-ai/04-reference/source-anchors.md` 若需新增模块锚点则更新（否则记录 no update required）
- [x] 独立子 agent closure audit

Exit Criteria:

- [x] 支柱测试红笔验证（临时加一个违规 import 能让测试变红，恢复后绿）
- [x] roadmap 状态块已更新且与 plan 一致
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan> --strict` 与 `node ai-dev/tools/scan-hollow-implementations.mjs --severity high nop-tools/nop-git/nop-git-core nop-tools/nop-git/nop-git-cli`（位置参数路径）退出码 0
- [x] 独立 closure audit 证据写入本 plan Closure 段
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [x] items 1-6 全部落地且各有测试覆盖（Feature 必有测试，Minimum Rules #25）
- [x] 端到端：open→init→add→commit→log→branchList→resolveRevision 门面全链路测试绿
- [x] 架构支柱测试绿 + 红笔验证记录
- [x] JGit import 收敛在 `io.nop.git.core`（grep 全模块证明，api/cli 无 JGit）
- [x] 无空方法体 / 静默跳过 / TODO 充当实现（新增公共方法未支持路径显式抛异常）
- [x] No owner-doc update required（Wave 6 统一收口，roadmap 状态块除外）
- [x] 独立子 agent closure-audit 完成并写入证据
- [x] nop-ib check（全 nop-git 模块 + nop-tools 父 pom）退出码 0
- [x] checkstyle / 代码规范：imports 分组（io.nop.* → third-party → java.*）、4 空格缩进、英文错误消息

## Deferred But Adjudicated

（无 —— Wave 1 无 deferred 项；CLI/策略/worktree 均为后续 Wave 的独立 plan 范围，不属于本 plan deferred。）

## Non-Blocking Follow-ups

- porcelain / CLI 格式化所需的字段若 Wave 2 发现缺失，在 Wave 2 plan 中补充模型字段（本 Wave 字段集以 design 04 契约方向为准）。

## Closure

Status Note: Wave 1（roadmap items 1-6）全部落地：四模块拓扑成立（聚合 artifact 定名 nop-git-parent 规避 legacy nop-utils/nop-git GAV 冲突）、JDK 21 基线真实可达（core 零仓库内部依赖 + maven.compiler.release=21 覆写，实测编译日志 release 21）、自有模型与门面适配层全实现、端到端全链路测试断言内容、架构支柱测试红笔独立复验通过。nop-ib test run=7 failed=0；hollow 扫描 0 findings；roadmap/daily log/live code 三方一致。无 in-scope live defect，无 deferred 项；rename 检测归 Wave 2 item 8 successor ownership。
Completed: 2026-11-20

Closure Audit Evidence:

- Reviewer / Agent: 独立 closure audit 子 agent（fresh session，agent_704a95c0-9662-4f05-8b28-0a65336170f8）
- Audit Session: agent_704a95c0-9662-4f05-8b28-0a65336170f8
- Evidence:
  - Phase 1-4 全部 Exit Criteria：PASS（file:line 与 test name 证据见 audit 报告；关键项：`nop-tools/pom.xml:29` module 声明、bom 7.7.1.202607240634-r 唯一版本字面量、api/cli 包 grep JGit 零命中、`JGitRepositoryServiceLifecycleTest.fullLifecycle` 全链路断言内容）
  - Anti-Hollow：(a) static 工厂→JGitRepositoryService 调用链真实，diff/log/commit 均真 JGit 操作无 no-op；(b) fullLifecycle 端到端断言内容非仅不抛错；(c) scan-hollow-implementations exit 0 / 0 findings（12 文件）
  - 支柱测试红笔独立复验：core test 下临时违规文件 → 精确点名变红，删除后 7 run / 0 failed（--force fresh 非 cache）
  - `check-plan-checklist.mjs --strict` 退出码 0
  - Deferred 项分类检查：无 deferred；rename 检测属 Wave 2 successor ownership（roadmap item 8），非降级
  - Minor 修正已落地：Goals policy 参数行补落地裁决；scan-hollow 命令改位置参数写法
