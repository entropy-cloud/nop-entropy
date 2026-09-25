# Java 重构工具调研与本地源码落位

> Status: open
> Date: 2026-09-25
> Scope: Java 生态重构工具全景（IDE 重构引擎 / 自动化迁移框架 / AST 解析转换库 / 重构与坏味道检测 / 静态分析辅助）；本地源码落位 `~/sources/refactor/`
> Conclusion: 建立 14 仓库工具地图并完成源码落位（14/14 成功，约 5.7G）；暂不接入任何工具，后续接入需另立 plan。与 nop-lint 定位互补——lint 负责"检查与报告"，本批工具覆盖"安全执行代码变更与迁移"。

## Context

- 调研动机：为 nop-entropy 平台及 Nop 应用生态后续的代码治理、批量改造（大版本升级迁移）、重构研究提供选型依据。调研最初在 nop-app-mall 工作区完成，开发工作转移至本项目后本文随之迁入 `ai-dev/analysis/`。
- 约束：GitHub 直连不稳定，下载需镜像轮换 + 多轮重试；许可证以本地 LICENSE 文件核对为准，GPL/LGPL 类只借鉴设计、不拷贝代码。
- 相邻文档：`2026-09-19-ast-lint-technology-survey.md` 覆盖 lint/结构化搜索/查询重写（ast-grep、Semgrep、GritQL 等）；本文聚焦重构执行引擎、迁移框架、重构检测，两者互补，均不与自研 nop-lint 的检查规则定位重叠。

## 调研结果

### 1. IDE 内置重构引擎（重构操作的事实标准）

| 工具 | 仓库 | 许可 | 定位 |
|------|------|------|------|
| IntelliJ IDEA 平台 | `JetBrains/intellij-community` | 本地 LICENSE.txt：JetBrains Open-Source Build Terms v1.3（2026-06 起生效；早期历史为 Apache-2.0） | Java 重构引擎的工业标杆（Rename/Extract/Move/Change Signature 等）；通用重构框架在 `platform/`，Java 实现主要在 `java/java-impl`。仓库巨大，仅浅克隆 |
| Eclipse JDT Core | `eclipse-jdt/eclipse.jdt.core` | EPL-2.0 | Java 编译器（ECJ）+ DOM/AST + 绑定模型；几乎所有学术/工程重构工具的 AST 地基（RefactoringMiner 直接依赖） |
| Eclipse JDT UI | `eclipse-jdt/eclipse.jdt.ui` | EPL-2.0 | Eclipse 实际的 Java 重构实现（`org.eclipse.jdt.internal.corext.refactoring`，Extract Method、Move、Rename 等 ~20 种），基于 LTK 重构框架（LTK core 位于 `eclipse-platform/eclipse.platform`，未单独下载） |
| Eclipse JDT LS | （已有 `~/sources/eclipse.jdt.ls`） | EPL-2.0 | 语言服务器形态的同一族重构能力（VSCode Java 的重构入口），此前已下载，不重复落位 |
| Apache NetBeans | `apache/netbeans`（未下载） | Apache-2.0 | 自带 `refactoring.java` 模块；需要时再 clone |

### 2. 自动化重构 / 大规模迁移框架

| 工具 | 仓库 | 许可 | 定位 |
|------|------|------|------|
| OpenRewrite | `openrewrite/rewrite` | Apache-2.0 | AST 级（Lossless Semantic Trees）+ recipe 的大规模重构框架；框架迁移（Spring Boot 升级、JUnit4→5、javax→jakarta）事实标准；各 recipe 集合在 `openrewrite/rewrite-*` 仓库，核心引擎为主下载对象 |
| Error Prone（含 Refaster） | `google/error-prone` | Apache-2.0 | javac 编译期 bug 模式检查 + 建议修复；Refaster 允许"用 Java 写模板 → 自动生成匹配与重写"，是规则化自动重构的轻量路径 |
| AutoRefactor | `JnRouvignac/AutoRefactor` | EPL-1.0 / GPLv3 双许可 | Eclipse 插件形态的规则式自动重构（消除样板代码、替换过时 API），理念与 OpenRewrite 相近但生态早于它 |

### 3. 源码解析与转换库（自建重构工具的地基）

| 工具 | 仓库 | 许可 | 定位 |
|------|------|------|------|
| Spoon | `INRIA/spoon` | CeCILL-C / MIT 双许可 | 分析与转换 Java 源码的元编程库：自建语义模型（CtModel），支持模板与 processor 扩展；研究界做"程序修复/重构推荐"的常用底座 |
| JavaParser | `javaparser/javaparser` | LGPL / Apache-2.0 双许可（仓库 LICENSE 确认） | 纯 Java 实现的解析器（覆盖新版本语法）+ SymbolSolver 语义解析；上手成本最低的自建 AST 工具 |

对比要点：JDT AST 能力最全但绑定 Eclipse 运行时；Spoon/JavaParser 更适合独立工具与实验；OpenRewrite 的 LST 额外保留格式/位置信息，专为大改动小 diff 设计。本项目自建工具链已有 tree-sitter 路线（nop-lint / nop-treesitter），Java 语义级重写若 tree-sitter 不够用时可选上表地基。

### 4. 重构检测与坏味道识别（研究/评审辅助）

| 工具 | 仓库 | 许可 | 定位 |
|------|------|------|------|
| RefactoringMiner | `tsantalis/RefactoringMiner` | MIT | 从 git 历史/版本对中自动检测重构实例（100+ 种），学术基准事实标准，已发布 Maven Central；研究"重构如何发生"的首选 |
| JDeodorant | `tsantalis/JDeodorant` | MIT | Eclipse 插件：检测 Feature Envy / Type Check / State Check / Long Method / Blob 并推荐 Move Method、Replace Conditional with Polymorphism、Extract Class 等重构；与 RefactoringMiner 同作者（N. Tsantalis） |
| RefactorFirst | `refactorfirst/RefactorFirst`（原 `jimbethancourt/RefactorFirst` 已迁移） | Apache-2.0 | Gradle 插件：扫描 God Class 等坏味道并输出"优先重构顺序"HTML 报告，适合接入构建做治理看板 |
| GumTree | `GumTreeDiff/gumtree` | LGPL-3.0 | 通用 AST diff 框架（树编辑距离近似算法）；重构检测、语义补丁类研究的常用底层件 |

### 5. 静态分析辅助决策（重构靶点发现）

| 工具 | 仓库 | 许可 | 定位 |
|------|------|------|------|
| SpotBugs | `spotbugs/spotbugs` | LGPL-2.1 | FindBugs 继承者；字节码级缺陷模式，提示值得重构的高风险点 |
| PMD | `pmd/pmd` | BSD-style | 源码级多语言静态分析；设计类规则（GodClass、圈复杂度、过长类/方法）+ CPD 重复代码检测，是"该重构哪里"的常用信号源 |

选型原则：覆盖**重构动作的实现**（IDE 引擎）、**重构的自动化执行**（迁移框架）、**重构的地基**（AST 库）、**重构的发现与评估**（检测/坏味道/静态分析）四个环节，便于按需组合。

## 本地源码清单与下载状态

目录：`~/sources/refactor/`；克隆方式均为 shallow clone（`--depth 1 --single-branch --no-tags`），失败时回退 codeload/镜像 tarball（无 git 历史）。下载脚本：`~/sources/download-java-refactor.sh`（直连优先 + 镜像轮换 + tarball 兜底 + 40 轮重试）。

| 仓库 | 本地路径 | 下载状态 | 约体积 | 许可（本地核对） |
|------|----------|----------|--------|------------------|
| tsantalis/RefactoringMiner | `~/sources/refactor/RefactoringMiner` | ✅ tarball 兜底（无 .git 历史） | 2.9G（含大量 src/test 检测数据集） | MIT |
| tsantalis/JDeodorant | `~/sources/refactor/JDeodorant` | ✅ 直连 shallow clone | 5.4M | MIT |
| JnRouvignac/AutoRefactor | `~/sources/refactor/AutoRefactor` | ✅ 镜像 shallow clone | 7.0M | EPL-1.0 / GPLv3 |
| refactorfirst/RefactorFirst | `~/sources/refactor/RefactorFirst` | ✅ 直连 shallow clone | 6.5M | Apache-2.0 |
| GumTreeDiff/gumtree | `~/sources/refactor/gumtree` | ✅ 直连 shallow clone | 24M | LGPL-3.0 |
| openrewrite/rewrite | `~/sources/refactor/rewrite` | ✅ 直连 shallow clone | 107M | Apache-2.0 |
| INRIA/spoon | `~/sources/refactor/spoon` | ✅ 直连 shallow clone | 62M | CeCILL-C / MIT |
| javaparser/javaparser | `~/sources/refactor/javaparser` | ✅ 直连 shallow clone | 58M | LGPL / Apache-2.0 |
| google/error-prone | `~/sources/refactor/error-prone` | ✅ 直连 shallow clone | 25M | Apache-2.0 |
| spotbugs/spotbugs | `~/sources/refactor/spotbugs` | ✅ 直连 shallow clone | 37M | LGPL-2.1 |
| pmd/pmd | `~/sources/refactor/pmd` | ✅ 直连 shallow clone | 67M | BSD-style |
| eclipse-jdt/eclipse.jdt.core | `~/sources/refactor/eclipse.jdt.core` | ✅ 直连 shallow clone | 135M | EPL-2.0 |
| eclipse-jdt/eclipse.jdt.ui | `~/sources/refactor/eclipse.jdt.ui` | ✅ 镜像 shallow clone | 139M | EPL-2.0 |
| JetBrains/intellij-community | `~/sources/refactor/intellij-community` | ✅ 直连 shallow clone | 2.1G | JetBrains Open-Source Build Terms v1.3 |

合计约 5.7G。下载证据：`~/sources/refactor/download-java-refactor.{log,ok,fail}`；单仓库失败细节见同名 `*.clone.log`。

## 不稳定网络下载教训（本次实测）

- GitHub 仓库路径与 codeload archive 路径**大小写敏感**：`tsantalis/refactoring-miner` 404，正确为 `tsantalis/RefactoringMiner`；镜像的"404"也可能是真实 404，需与网络故障区分。
- 镜像可用性时效极短：6 月可用的列表中 kkgithub.com 已域名过期，ghfast.top/gh-proxy.com 拒绝 git 协议（要求认证），gitclone.com 对小众仓库返回空仓库镜像。
- 最可靠的兜底是 **codeload tarball**（`codeload.github.com/<repo>/tar.gz/HEAD`），网络好窗口期直连秒下；git clone 与 tarball 双通道轮换的脚本模式有效。
- 直连时好时坏（好窗口约数分钟）：脚本直连优先、镜像兜底、40 轮重试的设计在两种窗口下都能收敛。

## 与当前项目的关系

可借鉴的点：

- 本项目已有 PMD/SpotBugs QA 实践（见 `ai-dev/logs/index.md` 2026-05-03、2026-05-05 条目），本次调研补齐其余层次的选型地图；SpotBugs/PMD 源码已在本地可对照。
- OpenRewrite：未来 Nop 平台大版本升级时，可为下游 Nop 应用批量升级提供 recipe 路线（AST 级、保格式）。
- RefactoringMiner / GumTree：研究型工具，可用于分析本仓库历史提交中的重构模式、做 AST diff 类验证。
- RefactorFirst：若要做仓库级"重构优先级"治理看板，其坏味道→优先级排序思路可参考。
- JDT AST / Spoon / JavaParser：Java 语义级重写的备选地基；与 nop-lint 的 tree-sitter 路线互补（tree-sitter 强在语言无关的语法层，弱在类型/语义绑定）。

不可借鉴/注意的点：

- GPL/LGPL 许可项目（AutoRefactor 的 GPLv3 选项、GumTree、SpotBugs）只借鉴设计与思路，不拷贝代码进本项目。
- intellij-community 许可条款已变更（JetBrains Open-Source Build Terms v1.3），引用其代码前须重新评估条款兼容性。
- 本调研不向平台引入任何运行时依赖；`~/sources/refactor/` 仅为本地研读材料。

## Open Questions

- [ ] 是否/何时将静态分析类工具（PMD 新规则、RefactorFirst 式坏味道看板）接入 CI —— 需另立 plan
- [ ] OpenRewrite recipe 是否作为 Nop 大版本升级时下游应用的迁移标准路径 —— 需 design 评估
- [ ] RefactoringMiner 挖掘本仓库 git 历史是否有研究价值（本地为 shallow clone 无历史，需全量 clone）

## References

- 本地源码：`~/sources/refactor/`（清单见上文表格）；下载脚本 `~/sources/download-java-refactor.sh`
- 相邻分析：`ai-dev/analysis/2026-09/2026-09-19-ast-lint-technology-survey.md`、`ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`
- 项目既有 QA 基础：`ai-dev/logs/index.md`（2026-05-03 SpotBugs 排除规则扩展 / PMD 升级 3.28.0；2026-05-05 nop-kernel SpotBugs findings 源码级验证）
- 上游仓库：见上文各表（GitHub）
