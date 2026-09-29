# 02 模块骨架 — nop-bytecode 模块落地与 class 文件采集层 v0（roadmap item 3, Wave 1）

> Plan Status: completed
> Last Reviewed: 2026-09-29
> Source: [nop-bytecode-analysis roadmap](../../backlog/nop-bytecode-analysis-roadmap.md) Wave 1 item 3 + [substrate-adjudication.md](../../design/nop-bytecode/substrate-adjudication.md)（底座 ADR：ASM 自研 + 定名 nop-bytecode）+ [00-overview.md](../../design/nop-bytecode/00-overview.md) §3.1 采集层职责契约
> Related: [01-wave0-gap-ledger-and-substrate-adjudication.md](01-wave0-gap-ledger-and-substrate-adjudication.md)（M0 已达成）

## Purpose

落地 Wave 1 item 3：新建 `nop-bytecode` 底座库模块并实现 class 文件采集层 v0，使 Wave 1 后续两项（item 4 内核 / item 5 发现流通道）有可编译、可测试的模块宿主与确定的采集口径。

## Current Baseline

- M0 已达成（plan 01 completed，commit f2ecee739b）：底座 ADR 裁定 ASM 9.7.1 自研路线、模块定名 `nop-bytecode`；gap-ledger 落 `ai-dev/design/nop-bytecode/gap-ledger.md`（自述"模块骨架落地后迁模块 docs"）。
- 仓库无任何直接 ASM 依赖（plan 01 核实）；本地 .m2 有 asm/asm-tree/asm-analysis 9.5–9.10.1。
- 底座库工程惯例（nop-treesitter 先例）：parent = `io.github.entropy-cloud:nop-entropy`；`<name>` 用递增编号（已用至 35-nop-lint、40-nop-refactor，**36 空闲**）；外部依赖版本在模块 pom 内联声明；root `pom.xml` modules 列表注册（nop-treesitter 在 :545）；JUnit 5 测试。
- 仓库编译目标 Java 17（class file v61）；本机与 CI 的 JDK 需兼容 `--release 17`；`--release 21`（v65）仅在 JDK ≥ 21 可用——v65 测试须按 JDK feature 值做 assume 门控。
- POC-A（plan 01）已实证纯 JDK + ASM 可完成 class 遍历/解析/hash（`_tmp/nop-bytecode-poc/poc-asm/`，gitignore，不入库）。
- 首跑前提：parent pom 经 relativePath 从工作区解析，零 `nop-*` 依赖使 `-am` 不需要；但 parent 的 dependencyManagement import 链（nop-bom → 第三方 BOM，junit 版本来源）须已在本地仓库或可联网——本机已满足，全新环境/CI 首次构建前需 install BOM 链。
- 00-overview §3.1 采集层职责契约：输入 = Maven reactor 输出目录 + 依赖 jar；提供增量采集；不重新编译；对缺失产物响亮失败。§3.2：定名与 nop-lint 依赖关系"随 Wave 0 ADR + Wave 1 骨架 item 裁定"——本 plan 落定该裁定。
- docs-for-ai 惯例：底座库可用后有模块页（nop-treesitter 先例）；本模块在发现流通道（item 5）落地前无用户可消费面。
- git 现状：`ai-dev/backlog/nop-bytecode-analysis-roadmap.md` item 3 = `todo`。

## Goals

- `nop-bytecode` 模块落地：pom（parent + ASM 9.7.1 pin + JUnit5）+ root pom 注册 + 编译通过 + 测试通过。
- 采集层 v0：目录（target/classes 形态）与 jar 两类输入的 class 工件枚举（路径/类名/major version/size/SHA-256），基于 manifest 的增量采集（跳过未变、报告新增/变更/移除），对缺失输入响亮失败。
- 依赖关系裁定落档：与 nop-lint **零依赖并行**（本模块运行时零 `nop-*` 依赖，仅 ASM）；00-overview §3.2 裁定行同步。
- gap-ledger 迁移至 `nop-bytecode/docs/gap-ledger.md`，引用同步。

## Non-Goals

- 不实现解析层类型全解析模型与方法内 CFG（item 4，plan 03）。
- 不实现 CLI / maven goal / 诊断输出（item 5，plan 04）。
- 不写 null-flow / 资源配对分析器（Wave 2/3）。
- 不做 docs-for-ai 模块页（通道尚无用户可消费面；发现流通道落地时随 plan 04 补）。
- 零修改 nop-lint 与现有工具接线（Hard constraint 1；root pom 仅追加 modules 行与 QA profile 无关的模块注册）。

## Scope

### In Scope

- `nop-bytecode/pom.xml`、`nop-bytecode/src/main/java/io/nop/bytecode/**`、`nop-bytecode/src/test/java/**`、`nop-bytecode/README.md`、`nop-bytecode/docs/gap-ledger.md`（迁移）
- root `pom.xml`（仅 modules 列表追加一行）
- `ai-dev/design/nop-bytecode/00-overview.md`（§3.2 裁定行 + 账本迁移落点句同步）
- `ai-dev/design/nop-bytecode/README.md`（账本行改指新位置）
- `ai-dev/design/nop-bytecode/substrate-adjudication.md`（仅头部关联行 gap-ledger 链接改指新位置；其余零改动）
- `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`（item 3 状态动态块）
- `ai-dev/logs/{执行当日}.md`

### Out Of Scope

- 任何 nop-lint / nop-core / 现有工具文件
- `_gen/`、`_*.xml` 生成管线文件（本模块为纯手写底座库，无 codegen 模型）

## Execution Plan

### Phase 1 — 模块骨架与依赖裁定

Status: completed
Targets: `nop-bytecode/pom.xml`、root `pom.xml`、`nop-bytecode/README.md`

- Item Types: `Decision | Fix`

- [x] draft review 通过后：roadmap item 3 状态 `todo`→`planned`（带本 plan 指针）+ `Last updated` 刷新
- [x] 新建 `nop-bytecode/pom.xml`：parent nop-entropy 2.0.0-SNAPSHOT、`<name>36-nop-bytecode</name>`、`java.version=17`、依赖 = org.ow2.asm:asm-tree:9.7.1 + org.ow2.asm:asm:9.7.1（compile，BSD-3 许可注释）+ org.junit.jupiter（test）；**零 `nop-*` 运行时依赖**（裁定：POC-A 已实证纯 JDK+ASM 足够；HC5 依赖最小化；与 nop-lint 零依赖并行）
- [x] root `pom.xml` modules 列表追加 `<module>nop-bytecode</module>`（modules 列表末尾、nop-refactor 行后，贴合编号时间序），不动其他行
- [x] 模块包骨架：`io.nop.bytecode`（模块 package-info 说明分层：collect 采集层；model/analysis/cli 包留待后续 plan 创建，**不建空包**）；模块级异常 `NopBytecodeException extends RuntimeException`（English messages）。**显式裁定**：直继 RuntimeException 是为守住"零 nop-* 运行时依赖"裁定（extends NopException 需引入 nop-api-core）——有意偏离 nop-treesitter 的 `TreeSitterException extends NopException` 先例；若未来需要 ErrorCode 体系，随 plan 04 通道层一并重估
- [x] `nop-bytecode/README.md`：定位（底座库，与 nop-lint 并行不替代）、分层规划、构建方式、指向 ADR 与 gap-ledger
- [x] gap-ledger 迁移 `ai-dev/design/nop-bytecode/gap-ledger.md` → `nop-bytecode/docs/gap-ledger.md`（头部迁移记录 + 状态词表不变）；**迁移后重写 ledger 全部内部相对链接**——统一改为仓库根相对形式（`ai-dev/...`、`nop-lint/...`，check-doc-links 对 `ai-dev/` 前缀按 project-root 解析，位置无关）：`../../../nop-lint/...`→`nop-lint/...`；`../../backlog/...`→`ai-dev/backlog/...`；`../../plans/...`→`ai-dev/plans/...`；`./00-overview.md`→`ai-dev/design/nop-bytecode/00-overview.md`；design/nop-bytecode/README.md 账本行与 substrate-adjudication.md 头部链接改指新位置
- [x] 00-overview §3.2 裁定行落定：定名 `nop-bytecode` + 与 nop-lint 零依赖并行（**共享基础设施 = 无**，超出 JDK+ASM 的需求出现时再裁定）；同节"账本过渡期落本目录……模块骨架落地后迁移"一句同步为迁移后实况

Exit Criteria:

- [x] `./mvnw test -pl nop-bytecode` 退出码 0（骨架即带最小测试，见 Phase 2；若 Phase 1/2 连续执行则以 Phase 2 终态验证）
- [x] root pom 仅 +1 行 modules 注册（`git diff pom.xml` 可验）；In Scope 之外的仓库文件零改动
- [x] 依赖裁定在 00-overview §3.2 可见（grep "共享基础设施 = 无" 命中裁定行——该字样为本次新增，动手前不存在）
- [x] gap-ledger 新位置可达、旧位置无残留文件；**断链核验双轨**：(i) `check-doc-links.mjs --strict` 0 errors；(ii) 人工核验迁移后 ledger 内全部相对引用（`./` 与 `../`）逐条 resolve 到真实文件（doc-links 工具扫描域不含模块 docs 目录，(i) 对该文件不构成覆盖）
- [x] `ai-dev/logs/{执行当日}.md` 对应条目已更新

### Phase 2 — 采集层 v0（ClassArtifactCollector）

Status: completed
Targets: `nop-bytecode/src/main/java/io/nop/bytecode/collect/**`、`nop-bytecode/src/test/java/io/nop/bytecode/collect/**`

- Item Types: `Fix`

- [x] `ClassArtifact` 值类型：relativePath、className、majorVersion、size、sha256（hex）
- [x] `CollectInput`：目录输入（递归枚举后缀 .class 文件；**module-info.class 采集**，className 记 `module-info`；`META-INF/versions/` 多版本条目按独立工件枚举）与 jar 输入（JarFile 遍历 .class 条目）
- [x] `MissingInputException extends NopBytecodeException`：输入目录/jar 不存在时抛出，消息含缺失路径清单（**响亮失败，不静默跳过**）
- [x] `CollectManifest`：properties 形态（path → size,mtimeMillis,sha256），load/save；`ClassArtifactCollector.collect(inputs, manifestOrNull)` → `CollectResult`（artifacts 全集 + added/changed/removed 相对 manifest 的三类增量清单 + unchanged 计数）；同输入二次采集 manifest 回写后 added/changed/removed 均空
- [x] 增量判定口径：path + size + mtime 未变且 manifest 有 sha256 → 直接沿用（不重读文件；collector 注释声明 mtime 粒度假设——1s 粒度文件系统上同秒修改且同 size 可能漏报 changed，消费方需要强保证时以 manifest 清空重采兜底）；否则重算 sha256 并归类 added/changed；manifest 有而本轮无的 path → removed
- [x] 测试（JDK `ToolProvider.getSystemJavaCompiler()` 在 `@TempDir` 现场编译 fixture 源码）：(a) v61 目录采集（--release 17）类名/版本/hash 正确；(b) jar 输入采集；(c) 增量：首采 added=全量 → 二采 unchanged=全量 → 修改一个源文件重编后 changed=1（重编后显式 `Files.setLastModifiedTime` 拨一个保证不同的时间戳，规避 1s 粒度文件系统）→ **直接从输入目录删除一个 .class 文件**后 removed=1（javac 不清理陈旧产物，禁止用"删源重编"配方）；(d) 缺失输入抛 MissingInputException 且消息含路径；(e) v65（--release 21）`Assumptions.assumeTrue(JDK≥21)` 门控采集 major=65
- [x] 无静默跳过自查：collector 对无法解析的 .class（损坏文件）**抛 NopBytecodeException**（消息含文件路径），不吞异常

Exit Criteria:

- [x] `./mvnw test -pl nop-bytecode` 退出码 0，新增测试类 ≥ 5 个测试方法覆盖上述 (a)–(e)
- [x] **端到端验证**：从"目录+jar 输入"到"CollectResult 增量三类清单"完整跑通（测试 (c) 即端到端链路）
- [x] **无静默跳过**：损坏 class 文件与缺失输入均为显式异常（测试断言异常类型与消息含路径）
- [x] **接线验证**：ASM `ClassReader` 确实被采集链路调用（className/majorVersion 来自 ClassReader 解析而非文件名猜测——测试 (a) 断言 className 与编译源码的 package/class 一致）
- [x] owner-doc 裁定：00-overview §3.1 采集层契约按 overview 实现，实现中若发现偏差须在本 Phase 内修正并同步 overview（预期无偏差）
- [x] `ai-dev/logs/{执行当日}.md` 对应条目已更新

### Phase 3 — 收口

Status: completed
Targets: `ai-dev/backlog/nop-bytecode-analysis-roadmap.md`、本 plan

- Item Types: `Proof | Follow-up`

- [x] roadmap item 3 状态 `planned`→`done`（plan 指针 + audit agent id）+ `Last updated` 刷新
- [x] 文本一致性核对：Plan Status / Phase Status / Exit Criteria / Closure Gates / daily log
- [x] 终门禁：`node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0；`node ai-dev/tools/check-doc-links.mjs --strict` 0 errors；`node ai-dev/tools/scan-hollow-implementations.mjs --module nop-bytecode --severity high` 退出码 0
- [x] 独立子代理 closure audit（fresh session）+ evidence 写入本 plan `## Closure`
- [x] Commit（单提交，`git revert` 可回退；root pom +1 行随本提交）

Exit Criteria:

- [x] roadmap item 3 = `done` 带指针与 audit id
- [x] check-plan-checklist --strict 0、doc-links --strict 0、hollow-scan --module nop-bytecode 0
- [x] `./mvnw test -pl nop-bytecode` 全绿（收口复跑）
- [x] 独立 closure audit 完成，evidence 写入
- [x] `ai-dev/logs/{执行当日}.md` 收口条目已更新

## Closure Gates

- [x] 模块落地：`./mvnw test -pl nop-bytecode` 全绿；root pom 仅 +1 行；零 `nop-*` 运行时依赖（pom grep 可验）
- [x] 采集层 v0 端到端：目录+jar → 增量三类清单完整跑通（测试在档）
- [x] 依赖裁定与 gap-ledger 迁移落档：00-overview §3.2 裁定行 + ledger 新位置 + 引用无断链
- [x] 无静默跳过：缺失输入与损坏 class 均显式异常（测试断言在档）
- [x] roadmap 动态块与本 plan 状态一致（item 3 done 带指针与 audit id）
- [x] 独立子代理 closure audit 完成并记录证据
- [x] 三门禁：check-plan-checklist --strict / check-doc-links --strict / scan-hollow-implementations --module nop-bytecode 均 0

## Deferred But Adjudicated

（无——item 3 范围内不接受延期。）

## Non-Blocking Follow-ups

- **plan 03 立项时补 asm-analysis 9.7.1 依赖**（`org.objectweb.asm.tree.analysis` oracle 纪律所在构件；本 plan 采集层只用 asm core，asm-tree 为 Wave 1 内核即时消费）——Why Not Blocking Closure: 本 plan 无内核面，遗漏只影响 plan 03 首次编译
- docs-for-ai 模块页（`03-modules/nop-bytecode.md` + INDEX 路由行）——发现流通道（item 5）落地后随首个用户可消费面补建（Why Not Blocking Closure: docs-for-ai 只描述已实现的使用能力，骨架阶段无消费面）
- 采集层多版本 jar（`META-INF/versions`）的 baseline 版本去重口径——出现真实多版本语料需求时随 plan 04 通道口径一并裁定（Why Not Blocking Closure: v0 按独立工件枚举已语义正确，去重属消费侧优化）

## Closure

Status Note: item 3 收口：nop-bytecode 模块落地（零 nop-* 运行时依赖，root pom 仅 +1 行）+ 采集层 v0（目录/jar 双输入、manifest 增量三类清单、缺失/损坏响亮失败）+ 依赖裁定与 gap-ledger 迁移落档。6/6 测试全绿（含收口时按 audit M1 补充的损坏 class 测试）；执行期修复 manifest 污染 bug（入口快照 baseline）并有回归覆盖。
Completed: 2026-09-29

Closure Audit Evidence:

- Reviewer / Agent: agent_ad5221af-22b8-4eff-b349-57b67dce73af（独立 fresh-session 子代理）
- Audit Session: sess_49c9956b-0ccc-48c1-ba6b-d6a1bd2eb130 / agent_ad5221af
- Evidence:
  - Phase 1 Exit Criteria 5/5 PASS（pom 零 nop-* 依赖 grep 可验；root pom diff 恰 1 insertion；00-overview §3.2 "共享基础设施 = 无"裁定行；ledger 迁移 + 旧位置删除；双轨断链核验含 auditor 逐条 -f 实测 6 引用）
  - Phase 2 Exit Criteria 6/6 PASS（auditor 实跑 5/5→收口时 6/6 测试；端到端 (c) 链路；无静默跳过——M1 修复后损坏 class 亦有测试断言；接线验证 ClassReader :178 真实解析；owner-doc 裁定；log 条目）
  - Phase 3 + Closure Gates 全勾（audit id 回填 roadmap；三门禁 EXIT=0 实测；测试收口复跑 6/6）
  - 首轮审计 APPROVE 附 1 Major（M1 损坏 class 无测试断言——EC 文本超 claim）+ 3 Minor；M1 以推荐选项 (a) 处置：补 `corruptClassFileFailsLoudlyWithPathInMessage` 测试（6/6 绿），Minors 记录不阻塞
  - Anti-Hollow：auditor 通读 collector 全文——无空壳/吞异常；ClassReader 真实调用；manifest 污染修复的快照逻辑核验正确且测试 (c) 第三腿构成回归覆盖；MissingInputException 运行时探针实证；`scan-hollow-implementations.mjs --module nop-bytecode --severity high` EXIT=0
  - `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0（evidence 写入后复跑）；`check-doc-links.mjs --strict` 0 errors
  - 纯增量约束：auditor git 全量核对——nop-lint 零改动、root pom 仅 +1 行、足迹与 In Scope 精确吻合；并发其他流脏文件已排除出本 plan 足迹（收口提交选择性 add）
  - Deferred 分类：3 条 Follow-up（asm-analysis → plan 03、docs-for-ai 页 → plan 04、多版本 jar 去重 → plan 04）均真 non-blocking；Deferred But Adjudicated = 无，无 in-scope defect 降级

Follow-up:

- plan 03 立项时补 asm-analysis 9.7.1（tree.analysis oracle 构件）
- docs-for-ai 模块页随 plan 04 首个用户可消费面补建；多版本 jar 去重口径随 plan 04 裁定

