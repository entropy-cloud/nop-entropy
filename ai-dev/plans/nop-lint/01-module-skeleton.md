# 01 nop-lint 模块骨架（roadmap item 1）

> Plan Status: completed
> Last Reviewed: 2026-09-21
> Source: ai-dev/backlog/nop-lint-roadmap.md Wave 1 item 1；ai-dev/design/nop-lint/01-pattern-dsl.md §1（模块划分）
> Related: nop-lint-roadmap.md items 2–7（后续 plan）

## Purpose

把 roadmap Wave 1 item 1 收口：`nop-lint/` 模块组（nop-lint-core/-java/-nop）以最小骨架进入根 pom reactor，每个模块有可运行的 bootstrap 测试证明依赖链与语言资源加载真实可用，为 item 2–7（LintNode、pattern kernel）提供可构建的落点。

## Current Baseline

- `nop-lint/` 目录不存在，根 `pom.xml` `<modules>` 未注册任何 nop-lint 模块（2026-09-21 核对）。
- nop-treesitter（33-）与 nop-rg（34-）已存在；仓库最大模块编号为 34，nop-lint 取 `35-nop-lint`。
- nop-treesitter 提供：`io.nop.treesitter.TSParser.parse(Language, String)` → `TSTree`；`io.nop.treesitter.TSNode`（record：tree/id/aliasSymbol）带 `type()/named()/child(i)/childCount()/startByte()/endByte()`；`TSTree.rootNode()` 返回根 `TSNode`；Java grammar blob 位于 `nop-treesitter` jar 内 `/grammars/java/tree-sitter-java-blob.bin`（`src/main/resources/grammars/java/` 下实文件），测试中已用 `Language.fromClasspath(...)` 加载（`nop-treesitter/src/test/java/io/nop/treesitter/bench/JavaParseBenchmark.java:24`）。
- `DefaultTreeSitterLanguageProvider`（nop-treesitter 主 jar，公开类无参构造）提供 `getLanguage(String)`/`languageNames()`；built-in 6 语言：json/java/javascript/python/typescript/tsx。注意：nop-treesitter 自己的测试断言 `languageNames()` 数量为 7，因其 test classpath 有经 `META-INF/services` 注册的 custom provider——nop-lint 模块没有该 test 资源，断言必须用 contains 而非数量相等。
- **依赖版本管理事实（closure audit 已核）**：nop-bom 未管理 `nop-treesitter`；因此 nop-lint-core 依赖 nop-treesitter、java/nop 依赖 nop-lint-core 均须显式 `<version>${project.version}</version>`（仓库先例：`nop-graph/nop-graph-core/pom.xml` 消费兄弟模块）。nop-xlang 由 nop-bom 管理，无需版本。junit-jupiter 由根 BOM 链管理，无需版本。
- **Java 版本事实（closure audit 已核）**：根 pom 统一 `maven.compiler.source/target/release=17`；根 pom 的 `java.version=17` 属性在根编译链无消费者（个别模块如 nop-demo/nop-quarkus 使用的 `${java.version}` 均为各自本地自定义属性，非继承根值）。nop-treesitter 实际按 release 17 编译。nop-lint 骨架遵循同一口径（继承 17，不覆写 compiler 属性）；未来若需 21 语法按 nop-utils/nop-rg 的 profile 门控模式另立决策。
- `ai-dev/plans/nop-lint/` 目录仅有 README.md（无编号 plan），本 plan 编号为 `01`。
- mission 配置 `missions/nop-lint.json` 已定义 test 命令 `./mvnw -pl nop-lint/nop-lint-core -am test -T 1C` 与 commit 格式 `feat(nop-lint): <description>`。
- 设计偏差声明：design 01 §1 中 nop-lint-java 对 nop-java-parser / nop-ai-code-analyzer solver 的依赖属 L2 类型推导（roadmap item 26），本骨架 plan 不引入，属有意延后而非漂移。

## Goals

- `nop-lint` 聚合 pom + 三个子模块（nop-lint-core / nop-lint-java / nop-lint-nop）注册进根 pom，`./mvnw -pl nop-lint/nop-lint-core -am clean test -T 1C` 可绿。
- 每个模块有一条真实 bootstrap 测试：core 证明 "语言 blob 加载 → Java 源码解析 → 树遍历" 全链路；java / nop 证明各自 classpath 上依赖链（经 nop-lint-core 传递到 nop-treesitter）真实可用。
- 依赖方向落地：core 依赖 nop-treesitter（`${project.version}`）+ nop-xlang；java、nop 以 `${project.version}` 依赖 core。

## Non-Goals

- 不实现 LintNode、SourcePatternCompiler、matcher、规则 DSL（roadmap items 2–8，后续 plan）。
- 不创建 nop-lint-js（roadmap item 19 / Wave 4 才需要）。
- 不引入 JMH/JFR：性能基线是 roadmap item 13 的职责，骨架无可度量的性能面。
- 不写 `docs-for-ai/` 使用教程（模块尚无使用面 API）；仅按 Minimum Rules #17 更新模块地图 owner doc。
- 不调整编译 release（继承根 pom 17）；不把 nop-treesitter 登记进 nop-bom（超出 item 1 最小骨架，若后续模块增多可另立决策）。

## Scope

### In Scope

- `nop-lint/pom.xml`（聚合：parent=nop-entropy，packaging=pom，`<name>35-nop-lint`，modules：core/java/nop）
- `nop-lint/nop-lint-core/pom.xml`（parent=nop-lint）：依赖 nop-treesitter（`${project.version}`）、nop-xlang、junit-jupiter(test)
- `nop-lint/nop-lint-java/pom.xml`（parent=nop-lint）：依赖 nop-lint-core（`${project.version}`）、junit-jupiter(test)
- `nop-lint/nop-lint-nop/pom.xml`（parent=nop-lint）：依赖 nop-lint-core（`${project.version}`）、junit-jupiter(test)
- 每个子模块一个 `package-info.java`（`io.nop.lint.core` / `io.nop.lint.java` / `io.nop.lint.nop`），作为模块包锚点
- bootstrap 测试 ×3（见 Phase 2）
- 根 `pom.xml`：`<modules>` 增加 `<module>nop-lint</module>`（置于 `<module>nop-rg</module>` 之后）
- `docs-for-ai/01-repo-map/module-groups.md`：新增 nop-lint 分组行
- `ai-dev/logs/2026/09-21.md`：执行日志（当天新建）

### Out Of Scope

- 任何生产 API 类（LintNode 等）——避免无消费者的空壳实现（Minimum Rules #24）
- checkstyle 配置、JMH 基准、规则 YAML
- `.github`/CI 变更

## Execution Plan

### Phase 1 - 模块骨架与 reactor 注册

Status: completed
Targets: `nop-lint/**/pom.xml`、根 `pom.xml`

- Item Types: `Fix`
- 本 Phase 无测试产出（测试在 Phase 2）；`clean test` 此刻验证的是编译与 reactor 接线，属预期空跑。

- [x] 创建 `nop-lint/pom.xml`：parent=io.github.entropy-cloud:nop-entropy:2.0.0-SNAPSHOT，packaging=pom，`<name>35-nop-lint`，modules 声明 core/java/nop
- [x] 创建 `nop-lint/nop-lint-core/pom.xml`：parent=nop-lint；依赖 nop-treesitter + nop-xlang（compile）、junit-jupiter（test）；不覆写 compiler 属性（继承 release 17）
- [x] 创建 `nop-lint/nop-lint-java/pom.xml`：parent=nop-lint；依赖 nop-lint-core（`${project.version}`）、junit-jupiter（test）
- [x] 创建 `nop-lint/nop-lint-nop/pom.xml`：parent=nop-lint；依赖 nop-lint-core（`${project.version}`）、junit-jupiter（test）
- [x] 三个子模块各建 `src/main/java/io/nop/lint/{core,java,nop}/package-info.java`
- [x] 根 `pom.xml` `<modules>` 注册 `<module>nop-lint</module>`

Exit Criteria:

- [x] `./mvnw -pl nop-lint/nop-lint-core -am clean test -T 1C` 退出码 0
- [x] `./mvnw -pl nop-lint/nop-lint-java -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0（证明两个下游模块依赖链可解析）
- [x] **接线验证**：`./mvnw -pl nop-lint/nop-lint-core -am dependency:list` 输出（不用 `-q`，否则 INFO 清单被抑制）中可 grep 到 `io.github.entropy-cloud:nop-treesitter` 与 `nop-xlang`，且为 compile scope
- [x] `No new test required: 本 Phase 仅 pom/package-info 配置类产出，测试由 Phase 2 统一交付`
- [x] Owner doc 裁定：本 Phase 将 nop-lint 注册进根 reactor（改变 live baseline），对应 `module-groups.md` 更新集中在 Phase 3 执行，不在本 Phase 静默跳过（Phase 3 已落地）
- [x] `ai-dev/logs/2026/09-21.md` 已更新本 phase 记录

### Phase 2 - Bootstrap 测试（每个模块 1 条）

Status: completed
Targets: `nop-lint/*/src/test/java/io/nop/lint/**`

- Item Types: `Proof`

- [x] nop-lint-core：`CoreBootstrapTest` — `Language.fromClasspath("/grammars/java/tree-sitter-java-blob.bin")` → `TSParser.parse(language, snippet)` → `TSTree.rootNode()` → 经 `TSNode.child(i)/type()` 深度优先收集，断言存在 `method_declaration` 与 `throw_statement` 节点
- [x] nop-lint-java：`JavaModuleBootstrapTest` — 经本模块 classpath 解析 nop-lint-core 传递的 nop-treesitter，解析同款 Java 片段并断言存在 `type_identifier` kind（证明传递依赖在 java 模块可解析、Java 语法 kind 可访问）
- [x] nop-lint-nop：`NopModuleBootstrapTest` — 直接实例化 `DefaultTreeSitterLanguageProvider`，断言 `getLanguage("java")` 非空且 `languageNames()` **contains**（非数量相等）json/java/javascript/python/typescript/tsx 六项（证明 nop 平台规则模块运行时可拿到语言）
- [x] 三条测试均为 `Proof` 类型：验证骨架接线，不含 placeholder 断言（禁止 `assertTrue(true)` 式空断言）

Exit Criteria:

- [x] 三个模块 `./mvnw test` 全绿，且每条测试至少包含 1 个会因依赖缺失/资源缺失而失败的真实断言
- [x] **端到端验证**：core 测试覆盖 "blob 资源 → Language → parse → TSNode 树遍历" 完整路径
- [x] 无静默跳过：测试不含假设性 try/catch 吞错；blob 路径错误时 `Language.fromClasspath` 抛错使测试失败
- [x] `No owner-doc update required: 本 Phase 仅新增测试代码，不改变任何 owner doc 所述行为`
- [x] `ai-dev/logs/2026/09-21.md` 已更新

### Phase 3 - Owner doc 同步与收口

Status: completed
Targets: `docs-for-ai/01-repo-map/module-groups.md`、`ai-dev/backlog/nop-lint-roadmap.md`

- Item Types: `Follow-up`

- [x] `module-groups.md` 根分组表新增 nop-lint 行（路径、定位、Wave 进度指针指向 roadmap）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [x] 顺序约束：先完成独立 closure audit（见 Closure Gates），audit 通过后再把 roadmap item 1 状态 `todo` → `done`（附 plan 编号引用），最后在本 plan 的 Closure 段记录 evidence
- [x] `ai-dev/logs/2026/09-21.md` 收口记录已更新

Exit Criteria:

- [x] `module-groups.md` 含 nop-lint 行且链接检查通过
- [x] roadmap Work Items 块中 item 1 标记为 `done`（附 plan 编号引用；仅在 closure audit 通过后勾选本项）
- [x] `ai-dev/logs/2026/09-21.md` 收口记录已更新

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（执行中无发现 live defect）
- [x] 所有 in-scope confirmed contract drifts 已收敛（不适用：无行为契约）
- [x] 行为/契约结果已达成：三个模块可构建、bootstrap 测试全绿
- [x] 必要 focused verification 已完成：Phase 1/2 Exit Criteria 全勾
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响 owner docs 已同步：`module-groups.md` 已更新；`INDEX.md`/`source-anchors.md` 无需更新（无新增路由/锚点，理由记录于 Closure）
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现会话）
- [x] Anti-Hollow Check：本 plan 验证方式为"测试即产物"——三条 bootstrap 测试从模块 classpath 真实调用 nop-treesitter 解析链；closure audit 复核生产产物面：三个模块目录下仅 `pom.xml` + `src/main/java/**/package-info.java`，无其他 main 源文件、无 `src/main/resources` 杂项；audit evidence 须记录 scan-hollow-implementations 实际扫描的文件数（防对空目录空转通过）
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-lint/01-module-skeleton.md --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint --severity high` 退出码 0（模块新引入，预期无发现；若工具不支持多模块目录则对三个子模块分别执行并记录）
- [x] `./mvnw -pl nop-lint/nop-lint-core -am clean test -T 1C` 退出码 0
- [x] `./mvnw -pl nop-lint/nop-lint-java -am test -T 1C` 与 `./mvnw -pl nop-lint/nop-lint-nop -am test -T 1C` 退出码 0
- [x] 代码规范检查：仓库 checkstyle 为非门禁（root qa profile `failOnViolation=false`；mission lint 命令自带 `|| echo` 恒为 0）——本 gate 以人工复核导入分组/命名约定 + 记录 mission lint 命令实际输出代替，不冒充门禁证据

## Deferred But Adjudicated

### Java 21 release 升级

- Classification: `optimization candidate`
- Why Not Blocking Closure: 根 pom 全局 release 17，nop-treesitter 同口径；骨架代码无 21 语法需求。未来升级按 nop-utils/nop-rg 的 JDK 门控 profile 模式另立决策，不影响 item 1 骨架成立
- Successor Required: `no`
- Successor Path: 无（如未来需要，在 roadmap 层面立项）

## Non-Blocking Follow-ups

- JMH 基准与 perf doc 由 roadmap item 13 承接（依赖 M1，非本 plan 范围）
- nop-lint-js 模块由 roadmap item 19 承接
- nop-lint-java 的 solver 依赖（nop-java-parser 等）由 roadmap item 26 / design 06 §6 承接

## Closure

Status Note: roadmap Wave 1 item 1 完成。nop-lint 模块组（35-nop-lint：core/-java/-nop）进入根 reactor，三条 bootstrap 测试证明 "语言 blob → parse → 树遍历" 链路与各模块依赖真实可用；两轮对抗审查（1B+2M+8m → 0B/0M）与独立 closure audit 全部通过。
Completed: 2026-09-21

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent agent_4a9d9e71-ce6e-4c53-981d-b22d91a43197（fresh session，两轮 draft review 为 agent_4c0ab23e-f5dd-4cfc-8e4d-0671cc23f0cb / agent_68513349-17cc-4b61-887c-b600f9145163）
- Evidence:
  - Phase 1 Exit Criteria：全部 PASS——4 个 pom 内容逐项核对一致；根 pom `pom.xml:580` 含 `<module>nop-lint</module>`；3 个 package-info 存在；`dependency:list` grep 确认 nop-treesitter/nop-xlang compile scope
  - Phase 2 Exit Criteria：全部 PASS——三条测试断言真实（blob 缺失抛错、依赖缺失不编译），auditor 亲自重跑 `-pl nop-lint-nop-lint-java/-nop -am test` 均 BUILD SUCCESS 退出码 0；surefire 各 1 test / 0 failure
  - Phase 3 Exit Criteria：全部 PASS——module-groups.md nop-lint 行落地；`check-doc-links.mjs --strict` 退出码 0（0 errors，遗留 warning 均在历史文件）；roadmap item 1 已翻 `done`（plan 01 引用）
  - Closure Gates：全部 PASS——`check-plan-checklist.mjs --strict` 退出码 0（Passed 1/Failed 0）；`scan-hollow-implementations.mjs --module nop-lint --severity high` 退出码 0，全级别 0 findings，实际扫描 3 个 src/main 文件（auditor 用 `nop-treesitter --severity low` 报出 93 发现反证工具非空转）；Anti-Hollow：`find nop-lint -name "*.java" | wc -l` = 6（3 package-info + 3 测试），生产面仅 pom + package-info
  - mission lint 命令输出已记录：checkstyle 告警全部来自上游历史模块，nop-lint 源文件 0 命中
  - Deferred 项分类检查：唯一 deferred（Java 21 升级，optimization candidate）合法，无 in-scope live defect 被降级
  - 文本一致性：Plan Status/Phase Status/Exit Criteria/Closure Gates/logs 六处一致；`ai-dev/logs/2026/09-21.md` 已记录执行与收口

Follow-up:

- no remaining plan-owned work（JMH 基准 → roadmap item 13；nop-lint-js → item 19；solver 依赖 → item 26，均已由 roadmap 承接）
