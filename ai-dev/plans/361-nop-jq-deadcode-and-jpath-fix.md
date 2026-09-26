# 361 nop-jq 死代码清理与 JPath 墓碑缺陷修复

> Plan Status: completed
> Last Reviewed: 2026-09-26
> Source: deepwiki skill 实测产出的事实清单（`ai-dev/logs/2026/09-26.md`）、git `40d64b4114`（nop-jq 落地提交）
> Related: `ai-dev/plans/01-nop-jq-complete-jq-implementation.md`（历史，已完成）、`ai-dev/plans/362-nop-code-index-column-truncation-fix.md`（共用实测背景）
> Draft Review: 两轮独立子 agent 对抗性审查（2026-09-26，agent_6835c657）：R1 发现 1 Blocker（docx classpath 无 nop-jq 致 e2e 不可达）+2 Major，全部修复；R2 复审判定无 Blocker 可执行，3 Minor（N361-1/N361-2/N362-1）已随修

## Purpose

把 deepwiki 生成过程中核实出的 nop-jq 死代码清除，并修复 nop-jq 落地时引入的 JPath 墓碑缺陷（docx 测试在真实源码构建下失败）。收口状态：全仓库无对已删类型的引用，`./mvnw install` 后 TestWordTemplate 全绿，nop-jq 模块测试全绿。

## Current Baseline（均于 2026-09-26 live 验证）

- 工作区 `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/jpath/JPath.java` 是抛异常墓碑（commit `40d64b4114` 用它替换了原 jayway json-path 包装实现）；compile/jpath/compileWithCache/getPathString 仍可用，get/getOne/set/delete 全部抛 `UnsupportedOperationException`。
- 修复前，本地仓库安装的 nop-core jar 是旧 jayway 版（早于墓碑源码的安装产物），`TestWordTemplate` 曾因此假绿；2026-09-26 从工作区源码重新 install nop-core 后，`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -Dtest=TestWordTemplate` 实测失败：`testParse:100 » UnsupportedOperation JPath.get() is deprecated`。
- nop-xlang 有 4 处对 JPath 的 compile 面使用（`TemplateMacroImpls.java:158` 的 jpath 宏、`GlobalFunctions.java:126-127` 的 `@Macro(resultType=JPath.class)`、`SimpleStdDomainHandlers.java:1732-1757` 的 JPathType std domain、`XPathHelper.java:22` 仅注释）；这些产物对象一旦被求值即抛异常。nop-xlang 无法依赖 nop-jq（nop-jq → nop-xlang → nop-core 的依赖方向），墓碑无法通过"改调用方"修复。
- 死代码（全仓库含测试仅自引用，grep 验证）：`nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqLegacyParser.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqCompiledQuery.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/InFilter.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NullFilter.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/SetPropertySegment.java`。
- 死 AST 节点：`SelectNode`/`MapNode`/`LimitNode` 无任何构造点（jq 的 select/map/limit 在 parser 中降为 FuncCallNode + JqBuiltins 函数），但被 `JqAstNode` permits、`JqAstVisitor`、`JqExecutor`（visitor 分支）、`JqPathEval`（仅 SelectNode）引用。
- `JqTokenType.FORMAT`：仅枚举声明，无词法产出方与消费方。
- `NopJsonPath.set` 的 javadoc 声称"自动创建中间容器"，实现（`NopCompiledJsonPath.set`）在父节点缺失时返回 false——文档与实现不符。

## Goals

- nop-jq 与 nop-core 中 6 个零引用文件、3 个死 AST 节点、1 个死枚举常量全部删除，无残留引用。
- JPath 恢复求值能力：nop-core 提供 SPI 入口，nop-jq 注册基于 NopJsonPath 的实现；docx 模板等既有消费方在新构建下恢复工作。
- 未注册 evaluator 时 JPath 求值方法快速失败（NopException），不再是无语义的 UnsupportedOperationException。
- `NopJsonPath.set` javadoc 与实现对齐。

## Non-Goals

- 不重构 NopJsonPath/NopCompiledJsonPath 的任何求值语义。
- 不迁移 xlang 的 jpath 宏/JPathType domain 到 nop-jq 类型（依赖方向不允许，且既有 API 面保持兼容）。
- 不处理 `io.nop.jq.jsonvalue` 包"无生产消费者"的事实（它是对外值模型，保留观察）。
- 不修改 `ai-dev/plans/01-nop-jq-complete-jq-implementation.md`（历史计划）与 docs-for-ai 中对 JPath 的历史表述（如有）。

## Scope

### In Scope

- 删除 Phase 1 列出的 6 文件 + 3 AST 节点 + FORMAT 常量及其全部引用点。
- JPath SPI 桥接（core 接口 + jq 注册 + 消费方 test-scope 依赖注入）及其测试；桥接契约落入 `ai-dev/design/`。
- `NopJsonPath.set` javadoc 修正。
- untracked 测试产物 `deepwiki/` 的处置（本计划改变其描述的 baseline）。

### Out Of Scope

- `JqBuiltins` 中"BUILTIN_NAMES 与实际 dispatch 差异"（如 nl/unutf8/weights 无实现）——独立治理项，见 Non-Blocking Follow-ups。
- `JqDestructurer`/`JqFormatStrings` 未成文文档化。
- nop-code 索引服务问题（见 plan 362）。

## Execution Plan

### Phase 1 - 死代码删除

Status: completed
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/**`

- Item Types: `Fix | Proof`

- [x] Fix: 删除 `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqLegacyParser.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/JqCompiledQuery.java`
- [x] Fix: 删除 `nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/InFilter.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NullFilter.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/SetPropertySegment.java`
- [x] Fix: 删除 `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/SelectNode.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/MapNode.java`、`nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/LimitNode.java`，并同步移除 `JqAstNode` permits 条目、`JqAstVisitor` 对应 visit 方法、`JqExecutor` 匿名 visitor 对应分支、`JqPathEval` 中 SelectNode 引用（审查核实引用点恰为：`JqAstNode.java:14`、`JqAstVisitor.java:32-36`、`JqExecutor.java:332/339/384`、`JqPathEval.java:16,191`；注意 `nop-kernel/nop-jq/src/main/java/io/nop/jq/jq/ast/FormatNode.java` 是活的——lexer 产出 AT token、`JqParser.java:690` 经 parseFormat 构造——不得误删）
- [x] Fix: 删除 `JqTokenType.FORMAT` 常量（仅声明处，无词法 case 分支）
- [x] Proof: 全仓库 grep 确认上述类型名零残留引用（用词边界模式 `\bFORMAT\b` 等排除 FormatNode/visitFormat 等同名误报；排除 `ai-dev/`、`deepwiki/`、docs 历史记录）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `grep -rnE "\b(JqLegacyParser|JqCompiledQuery|InFilter|NullFilter|SetPropertySegment|SelectNode|MapNode|LimitNode)\b" nop-kernel/nop-jq/src` 类型级零命中（`\bFORMAT\b` 于 `JqTokenType.java` 零命中；FormatNode 不受影响）
- [x] `./mvnw test -pl nop-kernel/nop-jq` 全绿（基线 652 用例，删除死代码不得改变通过数——若有用例专测死类型，按实际记录删减并说明）
- [x] No new test required: 纯死代码删除，既有测试守护行为
- [x] No owner-doc update required（deepwiki 产物处置单列于 Phase 3；docs-for-ai 未记载这些内部类型）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - JPath SPI 桥接修复

Status: completed
Targets: `nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/jpath/**`、`nop-kernel/nop-jq/src/main/**`（含 `src/main/resources/META-INF/services/`）、`nop-format/nop-ooxml/nop-ooxml-docx/pom.xml`

- Item Types: `Fix | Decision | Proof`

- [x] Decision：采用"core 定义 evaluator 接口 + 静态注册入口 + jq 模块经 ICoreInitializer 注册"方案（被拒替代方案：①恢复 core 内 jayway 实现——与去第三方依赖方向冲突；②xlang 改调 nop-jq——nop-core 不依赖 nop-jq，依赖成环；③core 直接依赖 nop-jq 类型——同前）。机制锚定仓库既有模式：core 侧 `JPath` 新增静态 evaluator 注册入口（静态 volatile 字段 + 显式 register/unregister，destroy 时清理）；jq 侧新增 `JqJPathInitializer implements io.nop.core.initialize.ICoreInitializer`，经 `nop-jq/src/main/resources/META-INF/services/io.nop.core.initialize.ICoreInitializer` 注册（ServiceLoader 发现，order 晚于 core init）——与 nop-xlang/nop-dao 等模块同一模式
- [x] Fix: core `JPath` 求值方法委托已注册 evaluator；未注册时抛 `NopException`（新错误码 `nop.err.core.jpath.no-evaluator`）。执行时偏差裁定：**静态便捷方法（get(bean,path)/get(bean,path,value)/delete(bean,path)）已移除**而非保留委托——实测发现其与实例重载存在二义性（经实例引用调用时更具体的静态 `get(Object,String)` 恒胜出，set 值被当 path 编译，TestJPathBridge 首跑复现），且全仓库零调用者；类级 `@Deprecated` 已移除；`NopJqException extends NopException` 无需包装，已写入 design doc 03-jpath-bridge-contract.md
- [x] Fix: jq 侧 evaluator 实现（eval/getOne/set/remove 语义对齐 `NopJsonPath`）+ `JqJPathInitializer` + SPI 文件
- [x] Fix: `nop-format/nop-ooxml/nop-ooxml-docx/pom.xml` 增加 **test-scope** 的 `nop-jq` 依赖——审查证实的 Blocker：docx 测试 classpath 上原本没有 nop-jq（nop-format 树零引用），ServiceLoader 无法发现 jq 注册器，JPath 求值必然失败；测试注入依赖后 SPI 生效
- [x] Decision：SPI 桥接的模块边界契约（core 承接注册、jq 实现注册、消费方以依赖注入获得能力）落入 `ai-dev/design/`（新建或追加既有子系统目录下小节，含被拒方案与理由）
- [x] Fix: JPath 类 javadoc 替换 "will be removed in a future version" 表述为实际契约说明（compile 面始终可用；求值面需 classpath 存在 nop-jq）

Exit Criteria:

- [x] 新增测试（jq 模块）：初始化后 `JPath.jpath("$.entities[0].a").get(...)` 返回正确值、`getOne`/`get(bean,value)`（set 语义）/`delete` 路径各一条断言——证明 core 静态入口与 jq evaluator 的运行时接线（Anti-Hollow Rule #23）
- [x] 新增测试：未注册 evaluator 时（显式 unregister 后调用）求值方法抛 `NopException` 而非静默返回（Rule #24）
- [x] **端到端验证**（Rule #22）：`./mvnw install -DskipTests -T 1C` 后 `./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -Dtest=TestWordTemplate` 全绿（`testParse:100` 的 `JPath...get(model)` 走通真实 DynamicObject 模型；该测试的 classpath 依赖 test-scope nop-jq）
- [x] `./mvnw test -pl nop-kernel/nop-jq,nop-kernel/nop-core,nop-kernel/nop-xlang` 全绿
- [x] `ai-dev/design/` 契约记录已落档
- [x] No docs-for-ai update required（`docs-for-ai/` 未记载 JPath 语义；执行时如发现记载则改判并更新本项）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - 文档对齐与产物处置

Status: completed
Targets: `nop-kernel/nop-jq/src/main/java/io/nop/jq/jsonpath/NopJsonPath.java`、`deepwiki/`

- Item Types: `Fix | Decision | Proof`

- [x] `NopJsonPath.set` javadoc 改为与 `NopCompiledJsonPath.set` 实际语义一致（父节点缺失返回 false，不创建中间容器）
- [x] Decision + 执行：删除 untracked 测试产物 `deepwiki/`（理由：Phase 1/2 改变其描述的 baseline，保留即 stale；内容可经 nop-deepwiki skill 一键重建），处置记录进 daily log。其 wiki 页中记载的 JPath "全抛异常"表述随删除一并处置
- [x] Proof: 核对 `.opencode/skills/nop-deepwiki/references/` 下文件无 JPath 表述需更新（2026-09-26 审查实测：skill reference 中 grep "JPath" 零命中，nop-code-api.md §5 记载的是 nop-code 索引问题与 JPath 无关）；如执行时发现新增表述则同步更新

Exit Criteria:

- [x] `NopJsonPath.set` javadoc 与实现逐句一致（人工核对）
- [x] `deepwiki/` 目录已删除，`git status` 无该目录
- [x] skill reference 无 JPath 表述需更新的核对结论已记录（或已同步更新）
- [x] No new test required: 纯文档与产物处置
- [x] No owner-doc update required（skill reference 经核对无 JPath 表述需更新，见本 Phase Proof 项；nop-code-api.md §5 属 plan 362 范围）
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 所有 in-scope confirmed live defects 已修复（JPath 墓碑、javadoc drift）
- [x] 所有 in-scope confirmed contract drifts 已收敛（死代码零残留）
- [x] 行为/契约结果已达成（docx e2e 全绿 + jq/core/xlang 测试全绿）
- [x] 必要 focused verification 已完成（SPI 接线测试、fast-fail 测试）
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift
- [x] 受影响的 owner docs 已同步到 live baseline，或明确写明 No owner-doc update required
- [x] 独立子 agent / 独立审阅者 closure-audit 已完成并记录证据
- [x] **Anti-Hollow Check**：closure audit 已验证（a）JPath 静态入口 → jq evaluator 的运行时接线被测试证明，（b）无空方法体/静默跳过/no-op 作为正常实现
- [x] `./mvnw compile -pl nop-kernel/nop-jq -am`
- [x] `./mvnw test -pl nop-kernel/nop-jq -am`（含 core/xlang 联动）
- [x] checkstyle / 代码规范检查通过（imports 分组、无格式噪声）
- [x] `node ai-dev/tools/check-plan-checklist.mjs <plan-file> --strict` 退出码 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-jq --severity high` 退出码 0

## Deferred But Adjudicated

（无——in-scope 项全部为 Fix/Decision，不允许延期）

## Non-Blocking Follow-ups

- `JqBuiltins` BUILTIN_NAMES 与实际 dispatch 的差异治理（nl/unutf8/weights 报告无实现、round/三角族/IN/INDEX/JOIN 实现无报告）——optimization candidate，不影响 JPath 契约与本计划 closure。
- `io.nop.jq.jsonvalue` 包无生产消费者的长期定位——watch-only residual，保留为对外值模型。

## Closure

Status Note: 全部 3 个 Phase 完成，独立 closure audit（agent_c38cb3b4，2026-09-26）判定可关闭：每条 Exit Criterion 现场验证 PASS（测试独立重跑：nop-jq 659/0、core 299/0、xlang 726/0、TestJPathBridge 7/7、TestWordTemplate 9/9 e2e）；Anti-Hollow 三重证实（调用链静态核对 + 接线测试 + docx e2e）。执行偏差（静态便捷方法移除）已记录于 Phase 2 与 design doc。Closure Gates 中 `-am` 字面命令以等价组合覆盖（jq/core/xlang 三模块分别从源码 test 全绿，成功构建含全量编译）、checkstyle 以新增文件 imports 分组人工核对替代（仓库无项目级 checkstyle 绑定）——替代口径已在 evidence 注明。
Completed: 2026-09-26

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（task agent_c38cb3b4-af01-4ddb-be83-19f3cf40a702，与实现会话隔离）
- Audit Session: agent_c38cb3b4-af01-4ddb-be83-19f3cf40a702
- Evidence:
  - Phase 1 Exit Criteria：死类型词边界 grep 零命中 PASS；8 文件删除确认 PASS（注：删除经 `git rm` 暂存后被并行会话提交 c061f953d5 带入 HEAD，live 状态满足实质判据）；nop-jq 659/0 PASS（独立重跑）
  - Phase 2 Exit Criteria：TestJPathBridge 7/7 PASS（独立重跑）；`unsetEvaluatorFailsFast` 断言错误码 `nop.err.core.jpath.no-evaluator` PASS；**e2e（Rule #22）TestWordTemplate 9/9 PASS**（独立重跑，前置核实 m2 nop-jq jar 含 JqJPathInitializer + SPI 文件且晚于源码创建）；jq/core/xlang 三模块全绿 PASS；design doc 03-jpath-bridge-contract.md 落档且无未定稿内容 PASS
  - Phase 3 Exit Criteria：NopJsonPath.set javadoc 与 NopCompiledJsonPath.set 实现逐句一致 PASS；deepwiki/ 已删除 PASS；skill reference 零命中核对已记录 PASS
  - Closure Gates：全部 PASS——Anti-Hollow 调用链现场核对（JPath.java:93-98 → 静态 volatile evaluator → JPathEvaluatorImpl → NopJsonPath，全链无 no-op）；`check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs --module nop-jq --severity high` 退出码 0
  - Deferred 项分类检查：Deferred But Adjudicated 为空，无 in-scope live defect 被降级
  - check-doc-links --strict 退出码 0

Follow-up:

- `io.nop.jq.jsonvalue` 无生产消费者（watch-only residual，见 Non-Blocking Follow-ups）
- BUILTIN_NAMES 与 dispatch 差异治理（optimization candidate，见 Non-Blocking Follow-ups）