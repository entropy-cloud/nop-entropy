# 2280 拆分 nop-jpath 并将 nop-jq 迁出 nop-kernel

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: 2026-09-28 会话内 live repo 核查 + `ai-dev/design/nop-jq/01-architecture-baseline.md` + `ai-dev/design/nop-jq/03-jpath-bridge-contract.md` + `ai-dev/plans/361-nop-jq-deadcode-and-jpath-fix.md`
> Related: plan 361（JPath SPI 桥接，completed）

## Purpose

把 nop-kernel 收窄为"只包含 jpath 支持"：新增 `nop-jpath` 模块承担 JSONPath 求值与 JPath SPI 桥接（留在 nop-kernel），将纯 jq 引擎（`nop-jq`）迁出 nop-kernel 成为顶层独立模块组。目标是减小 nop-kernel 的模块面与体积，同时保持 JPath 桥接契约（plan 361）与 jq 官方套件 430/430 基线不变。

## Current Baseline

以下均为 2026-09-28 在 live repo 上核查过的事实：

- **nop-jq 当前构成**（拆分前位于 nop-kernel 下，现顶层 `nop-jq/`；main 源码 102 个 java 文件 + test 19 个）是三条零耦合能力线：
  - `io.nop.jq.jq` / `jq.ast` / `jq.runtime`（70 文件 = jq 直属 7 + ast 42 + runtime 21）：完整 jq 1.7.1 引擎，官方套件 430/430（`TestJqOfficial`）。对平台仅依赖 commons `LocalCache` 与 core `JsonTool`（`JqBuiltins` 一处 `JsonTool.parse`）。
  - `io.nop.jq.jsonpath`（19 文件）：fastjson 兼容 JSONPath（`NopJsonPath`）。
  - `io.nop.jq.jsonvalue`（7 文件）：不可变 JsonValue 模型，**全仓库零生产消费者**（唯一引用是测试 `TestJsonValue`；plan 361 裁定为 watch-only residual）。
- **两条线零共享**：jq 引擎不 import jsonpath/jsonvalue/根包类；根包辅助类 `JsonAccessor`（15 处）、`NopJsonAccessor`、`NopJqException`、`NopJqErrors`（jsonpath 仅用 `ERR_JQ_COMPILE_ERROR` + `ARG_EXPR`）全部只被 jsonpath 包使用。jq 引擎的异常体系（`JqRuntimeException` 等）自带于 `jq.runtime`。
- **死代码**：`io.nop.jq.JsonQueryContext` 全仓库（main + test + 全模块）零引用。
- **桥接契约已成立**（plan 361，2026-09-26 收口）：nop-core `io.nop.core.lang.json.jpath.JPath` 为 compile-only 门面，求值经 `JPathEvaluator` SPI 委托；nop-jq 侧 `io.nop.jq.JqJPathInitializer`（`ICoreInitializer`，`META-INF/services/io.nop.core.initialize.ICoreInitializer`，order=`INITIALIZER_PRIORITY_REGISTER_COMPONENT+1`）注册绑定 `NopJsonPath`。契约文档：`ai-dev/design/nop-jq/03-jpath-bridge-contract.md`。
- **消费方清单**：compile 依赖 nop-jq 的仅 `nop-ai/nop-ai-toolkit`（`JqToolExecutor` 用 jq 引擎）；`nop-format/nop-ooxml/nop-ooxml-docx` 为 test-scope（只需 JPath 求值）；nop-xlang 经 JPath 门面在运行时需要已注册的 evaluator。
- **依赖冗余**：`nop-jq/pom.xml` 声明 nop-xlang，但 main 与 test 源码零 `io.nop.xlang` import。
- **测试耦合**：`src/test/java/io/nop/jq/benchmark/` 下 `SimpleBenchmark`/`JqToolBenchmark`/`JsonPathBenchmark` 引用 jsonpath 及根包类（经 `NopJsonPath` 间接触及）；`TestNopJsonAccessor` 直接使用根包 `NopJsonAccessor`；`TestJsonValue` 是 jsonvalue 唯一引用者；jq 专属测试不引用 jsonpath/jsonvalue/根包类。
- **Maven 注册点（拆分前状态，Phase 2 已变更）**：`nop-kernel/pom.xml` `<modules>` 含 `<module>nop-jq</module>`；根 `pom.xml`（artifactId `nop-entropy`）含 `<module>nop-kernel</module>`；`nop-bom/pom.xml` 有 nop-jq 条目。顶层组模式参照 `nop-rg/pom.xml`（parent=`nop-entropy`，packaging=pom）。
- **文档面**：`docs-for-ai/` 无任何 nop-jq 路由引用；nop-jq 侧权威文档在 `ai-dev/design/nop-jq/`；`nop-jq/deepwiki-v3/` 为未入库生成产物（会随目录移动）。
- **plan 编号**：当前最大 2279。

## Goals

- nop-kernel 新增 `nop-jpath` 模块（jsonpath 包 + 根包辅助类 + `JqJPathInitializer` 桥接），nop-kernel 对外只保留 JPath/JSONPath 求值支持面。
- `nop-jq` 迁出 nop-kernel，成为顶层模块组 `nop-jq/`（纯 jq 引擎），artifactId 与坐标不变。
- 桥接契约行为零变化：`TestJPathBridge` 全绿；nop-ai-toolkit、nop-ooxml-docx 模板测试全绿；jq 官方套件 430/430 保持。
- 顺带清理两项已核实的死代码（`jsonvalue` 包、`JsonQueryContext`）与一项依赖冗余（nop-jq 的 nop-xlang 声明）。

## Non-Goals

- **不改包名**：nop-jpath 模块内保留 `io.nop.jq.jsonpath` / `io.nop.jq` 包名（Decision，见 Deferred；改名是外部 API 破坏，另行立项）。
- **不改错误码**：`NOP_JQ-xxx` 前缀与 `NopJqErrors` 常量随 nop-jpath 整体迁移，不重命名。
- **不改桥接契约语义**：`03-jpath-bridge-contract.md` 描述的行为面（eval/evalOne/set/remove 语义、快速失败、注册时机）不动。
- **不动 jq 引擎实现**与官方测试套件内容。
- 不重新生成 deepwiki（按 plan 363 的 update 增量模式另行处理）。

## Scope

### In Scope

- 新建 `nop-kernel/nop-jpath` 模块并完成文件迁移、测试拆分、pom 重整。
- `nop-jq` 迁出 nop-kernel（git mv + pom parent/modules 调整 + bom 条目）。
- 消费方依赖切换（docx test-scope 改 nop-jpath）与 owner-doc 同步。

### Out Of Scope

- jq 引擎功能演进、性能优化。
- nop-core `JPath` 门面 API 形态调整（含历史命名 `get(bean,value)`）。
- jsonvalue 的替代实现或新值模型（选择删除，见 Phase 1）。

## Execution Plan

### Phase 1 - 新建 nop-jpath 并拆分 nop-jq

Status: completed
Targets: `nop-kernel/nop-jpath/**`、`nop-kernel/nop-jq/**`、`nop-kernel/pom.xml`、`nop-bom/pom.xml`

- Item Types: `Fix | Decision`

- [x] （Decision）新建 `nop-kernel/nop-jpath`：artifactId `nop-jpath`，parent=`nop-kernel`；注册进 `nop-kernel/pom.xml` `<modules>`；`nop-bom/pom.xml` 增加 nop-jpath dependencyManagement 条目。
- [x] （Fix）迁移到 nop-jpath（包名不变）：`io.nop.jq.jsonpath` 全部 19 文件；根包 `JsonAccessor`/`NopJsonAccessor`/`NopJqErrors`/`NopJqException`；`JqJPathInitializer`；`src/main/resources/META-INF/services/io.nop.core.initialize.ICoreInitializer`。
- [x] （Decision）删除 `io.nop.jq.JsonQueryContext`（全仓库零引用；删除前做词边界 grep 复核并记录结果）。
- [x] （Decision）删除 `io.nop.jq.jsonvalue` 7 文件 + `TestJsonValue`（零生产消费者，plan 361 watch-only residual 在此做最终裁定为移除；删除前 grep 复核）。
- [x] （Fix）测试拆分：`TestJPathBridge`、`TestNopJsonAccessor`、`src/test/java/io/nop/jq/jsonpath/**`、`JsonPathBenchmark` 移入 nop-jpath；jq 专属测试（含 `JqEngineBenchmark`）留 nop-jq；`SimpleBenchmark`/`JqToolBenchmark` 若同时依赖两线则留 nop-jq 并声明 test-scope 依赖 nop-jpath（执行时按实际 import 裁定并记录）。
- [x] （Fix）pom 重整：nop-jpath 依赖 nop-api-core/nop-commons/nop-core，test 依赖 junit + jmh-core/jmh-generator-annprocess（含 compiler `annotationProcessorPaths`）+ jayway json-path(test)（`JsonPathBenchmark` 对比基准需要）；nop-jq 移除未使用的 nop-xlang 依赖，main 依赖收敛为 nop-core/nop-commons，test 依赖 junit + jmh + jayway 按留驻测试实际需要保留。本计划为纯迁移重构：No new test required（测试随代码移动，覆盖面不变）。
- [x] （Decision）nop-jq（引擎）不依赖 nop-jpath：jq 引擎与根包类零耦合已核实；若执行中发现隐藏引用，回炉记录后重新裁定，不允许静默加依赖绕过。

Exit Criteria:

- [x] `./mvnw test -pl nop-kernel/nop-jpath -am` 全绿；`./mvnw test -pl nop-kernel/nop-jq -am` 全绿。
- [x] `TestJqOfficial` 官方套件 430/430 仍在 nop-jq 通过（拆分后复跑确认）。
- [x] `TestJPathBridge` 在 nop-jpath 坐标下全绿——**接线验证（直接调用链）**：该测试证明 `JqJPathInitializer.initialize()` → `JPath.registerEvaluator` → `NopJsonPath` 的求值委托链连通。注意：`META-INF/services` 的 ServiceLoader **发现链**端到端证明归属 Phase 3 的 `TestWordTemplate`（`TestJPathBridge` javadoc 已声明该分工），Phase 1 不以此勾选发现链验证。
- [x] nop-jq main 源码中 `io.nop.jq.jsonpath`/`jsonvalue`/根包辅助类残留为 0（grep 退出码验证）。
- [x] 删除 `JsonQueryContext`/`jsonvalue` 前的 grep 复核结果已记录（执行日志）。
- [x] 本 Phase 改变 live baseline：`ai-dev/design/nop-jq/01-architecture-baseline.md` §1.1 模块树与 §1.2 依赖图已更新；`03-jpath-bridge-contract.md` 注册方所在 jar 已改为 nop-jpath。
- [x] `ai-dev/logs/2026/09-28.md`（或实际执行日）条目已更新。

### Phase 2 - 迁移 nop-jq 出 nop-kernel

Status: completed
Targets: `pom.xml`、`nop-kernel/pom.xml`、`nop-jq/**`

- Item Types: `Fix`

- [x] （Fix）`git mv nop-kernel/nop-jq nop-jq`（deepwiki-v3 未跟踪产物随目录移动，不丢失）。
- [x] （Fix）`nop-jq/pom.xml` parent 由 `nop-kernel` 改为 `nop-entropy`（与 `nop-rg` 顶层组模式一致）。
- [x] （Fix）根 `pom.xml` `<modules>` 增加 `<module>nop-jq</module>`；`nop-kernel/pom.xml` `<modules>` 移除 `<module>nop-jq</module>`。
- [x] （Fix）全仓库 grep `<artifactId>nop-jq</artifactId>` 复核消费方 pom 的 relativePath/依赖声明无需其他变更（nop-ai-toolkit 依赖坐标不变）。
- [x] （Fix）移除 `nop-kernel/pom.xml` `<dependencyManagement>` 中过期的 nop-jq 版本管理条目（迁出后消费方版本解析走 nop-bom，该条目成为残留）。

Exit Criteria:

- [x] `./mvnw compile -pl nop-jq -am` 与 `./mvnw compile -pl nop-kernel/nop-jpath -am` 均通过（reactor 覆盖新旧两组）。
- [x] `./mvnw test -pl nop-jq` 全绿（迁移后复跑，官方套件 430/430）。
- [x] `git status` 确认迁移为 rename 语义、无文件意外丢失（deepwiki-v3 在新位置存在）。
- [x] 本 Phase 仅移动目录与 pom，不改行为：owner-doc 更新并入 Phase 1/3 已列条目，此处显式记录 `No owner-doc update required`（位置性变更由 Phase 3 统一收口）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

### Phase 3 - 消费方切换与文档收口

Status: completed
Targets: `nop-format/nop-ooxml/nop-ooxml-docx/pom.xml`、`nop-ai/nop-ai-toolkit/**`、`ai-dev/design/nop-jq/**`、`docs-for-ai/01-repo-map/module-groups.md`

- Item Types: `Fix | Proof`

- [x] （Fix）`nop-ooxml-docx` test-scope 依赖由 nop-jq 改为 nop-jpath（模板测试只需 JPath 求值，不再拖 jq 引擎）；pom 注释同步改写。
- [x] （Proof）`./mvnw test -pl nop-ai/nop-ai-toolkit` 全绿——**端到端验证**：AI agent → `JqToolExecutor` → `JqEngine.compile(expr).apply(root)` 完整路径在迁移后的 nop-jq 坐标上可用。
- [x] （Proof）`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -Dtest=TestWordTemplate` 全绿——**端到端验证（含 ServiceLoader 发现链）**：docx 模板 → xlang JPath 门面 → `META-INF/services` ServiceLoader 发现 nop-jpath 的 `JqJPathInitializer` → `NopJsonPath` 完整求值路径在切换后可用（这是发现链的唯一端到端证明，见 plan 361 的测试分工）。
- [x] （Fix）`ai-dev/design/nop-jq/01-architecture-baseline.md`：模块定位章节改写为最终状态（nop-jpath 在 nop-kernel、nop-jq 顶层组、依赖方向图）。
- [x] （Fix）`ai-dev/design/nop-jq/03-jpath-bridge-contract.md`：消费方规则中"显式添加 nop-jq 依赖"改为 nop-jpath；注册方描述对齐新模块坐标。
- [x] （Fix）`docs-for-ai/01-repo-map/module-groups.md`：基础内核行提及 nop-jpath；新增顶层模块组行 nop-jq（纯 jq 引擎，位置、定位一句话）。
- [x] （Proof）`node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0；确认 `docs-for-ai/INDEX.md` 与 `04-reference/source-anchors.md` 无既有 nop-jq 路由需要更新（当前零引用，若核查后仍为零则记录"无需更新"）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 所有 in-scope confirmed live defects 已修复（死代码 jsonvalue/JsonQueryContext、依赖冗余 xlang 均已清除）。
- [x] 桥接契约 drift 已收敛：plan 361 契约行为面在模块拆分/迁移后零变化（TestJPathBridge + TestWordTemplate 证据）。
- [x] 结构目标达成：nop-kernel 不再包含 jq 引擎；nop-jpath 在 nop-kernel 内承担 JPath 求值；nop-jq 为顶层组。
- [x] 必要 focused verification 已完成：三模块测试 + 官方套件 430/430 + 两条例子级端到端 Proof。
- [x] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect 或 contract drift。
- [x] 受影响 owner docs 已同步（01-architecture-baseline、03-jpath-bridge-contract、module-groups.md）。
- [x] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现会话）。
- [x] **Anti-Hollow Check**：closure audit 已验证（a）JPath 桥接链与 jq 工具链在运行时确实连通（不只 import 存在），（b）无空方法体/静默跳过/no-op 作为正常实现。
- [x] `./mvnw compile -pl nop-jq,nop-kernel/nop-jpath -am` 通过。
- [x] `./mvnw test -pl nop-jq,nop-kernel/nop-jpath -am` 全绿。
- [x] 仓库现行静态门禁通过：`check-doc-links.mjs --strict` 0 errors + `scan-hollow-implementations.mjs` 双模块 exit 0（checkstyle 插件在根 pom 中未绑定构建，非现行门禁）。
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2280-nop-jpath-split-and-jq-move.md --strict` 退出码 0。
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-jpath --severity high` 与 `--module nop-jq --severity high` 退出码 0。

## Risks And Rollback

- 目录迁移风险：Maven relativePath/parent 断链导致 reactor 解析失败——Phase 2 Exit Criteria 以 compile 复跑兜底；回滚 = `git mv` 还原 + pom 还原（纯移动，无行为变更）。
- 测试类拆分遗漏 import 导致编译失败：以两模块分别编译为硬门禁，不允许 `-Dmaven.test.skip` 绕过。
- 删除 jsonvalue 属于对外可见 API 收缩（虽零消费者）：提交信息与日志中显式记录裁定依据（plan 361 watch-only + 本计划 grep 复核），出现未知下游需求时按 git 历史恢复。

## Deferred But Adjudicated

### nop-jpath 包名与 artifactId 不一致（io.nop.jq.jsonpath 保留在 nop-jpath 模块）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 包名是外部 API，改名需要 deprecation shim 与全平台 import 迁移；模块拆分本身不依赖改名，桥接契约文本已按"模块坐标 vs 包名"区分表述。
- Successor Required: `no`
- Successor Path: 如未来需要，另立 plan 做包名迁移（含 `NopJsonPath` → 新坐标的 deprecation 周期）。

### deepwiki-v3 文档内容与新模块位置的同步

- Classification: `watch-only residual`
- Why Not Blocking Closure: deepwiki-v3 随 `git mv` 整体迁移，页内相对链接（`../src/...`）仍然有效；仅 wiki-state 指纹与提交锚点过期，属 plan 363 update 增量模式的既定处理范围。
- Successor Required: `yes`
- Successor Path: plan 363（nop-deepwiki structure and consumption upgrade）的 update 增量模式


## Closure

Status Note: 三个 Phase 全部落地且经独立 fresh-session closure audit 复核：nop-kernel 仅保留 nop-jpath 查询支持面，nop-jq 为顶层纯 jq 引擎组；桥接契约零漂移（TestJPathBridge 7/0 + TestWordTemplate 9/0），官方套件 430/430，死代码与依赖冗余清除，owner docs 与 live 一致，doc-links 0 errors。无 in-scope 剩余工作。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agentId: agent_31a123b3-5a44-4daf-8759-60dd7d285517）
- Evidence:
  - Phase 1：fresh 复跑 `./mvnw test -pl nop-kernel/nop-jpath` / `-pl nop-jq` 均 exit 0；surefire 95/0 与 554/0，TestJqOfficial 430/430；nop-jq main/test 零 jsonpath/jsonvalue/根包残留（grep exit 1）；services 文件在 nop-jpath 且内容为 `io.nop.jq.JqJPathInitializer`
  - Phase 2：nop-jq/pom.xml parent=nop-entropy（pom:7-11）；根 pom module（pom.xml:580）；kernel pom 无 nop-jq module/DM、nop-jpath 条目在位（:121/:474）；nop-bom :105/:111；git status 114 R + 1 RM rename 语义；deepwiki-v3 随迁
  - Phase 3：docx pom:24 test-scope nop-jpath；ai-toolkit 30 报告零失败；TestWordTemplate 9/0；design 01/03、README、module-groups.md 与 live 一致；check-doc-links --strict 0 errors
  - Anti-Hollow：(a) JqJPathInitializer 四方法真实委托 NopJsonPath，TestJPathBridge 断言求值结果/写穿/快速失败；(b) 无空方法体/TODO 静默跳过；(c) `scan-hollow-implementations.mjs --module nop-jpath / nop-jq` 双双 exit 0
  - `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2280-nop-jpath-split-and-jq-move.md --strict` 退出码 0（completed 后复跑）
  - Deferred 分类检查：包名不改（out-of-scope improvement）与 deepwiki 同步（watch-only residual，successor=plan 363 实际存在）均非 live defect 降级
  - 审计条件（2 Major 均为收口动作）已完成：当日日志补写（含删除前 grep 复核记录）+ 全部变更提交

## Non-Blocking Follow-ups

- nop-jq 顶层组如后续新增子模块（如 CLI、benchmark 独立模块），再引入 packaging=pom 聚合层，当前保持单模块目录最小结构。
- `NopJqErrors` 中仅被 jsonpath 使用的错误码前缀若将来需要按模块区分（NOP_JPATH-xxx），随包名迁移一并处理，不在本计划范围。
