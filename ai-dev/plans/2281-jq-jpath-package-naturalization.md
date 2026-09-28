# 2281 查询模块包名自然化：io.nop.jpath / io.nop.jq 去叠词

> Plan Status: completed
> Last Reviewed: 2026-09-28
> Source: 用户指令（2026-09-28，"包名要改成最直观自然的，不能是 io.nop.jq.jq.xx 这种"）；plan 2280 拆分后的 Deferred 项（包名迁移）由本计划提前收口
> Related: plan 2280（completed，模块拆分）

## Purpose

plan 2280 完成模块拆分时为控制破坏面保留了旧包名（`io.nop.jq.jsonpath` 在 nop-jpath、`io.nop.jq.jq.*` 在 nop-jq）。本计划把两模块包名改为与职责直观对应的最终形态，消除 `jq.jq` 叠词与跨模块包名错位。

## Current Baseline

（live 核对 2026-09-28；以下为改名前状态记录）

- **nop-jpath**（`nop-kernel/nop-jpath`）main 源码现位于两个包：`io.nop.jq`（根辅助类 5 文件：JqJPathInitializer/JsonAccessor/NopJsonAccessor/NopJqErrors/NopJqException）与 `io.nop.jq.jsonpath`（19 文件）；test 位于 `io.nop.jq`（TestJPathBridge/TestNopJsonAccessor）、`io.nop.jq.jsonpath`（7）、`io.nop.jq.benchmark`（3）。`META-INF/services/io.nop.core.initialize.ICoreInitializer` 内容为 `io.nop.jq.JqJPathInitializer`。
- **nop-jq**（顶层 `nop-jq/`）main 源码位于 `io.nop.jq.jq`（7 文件：JqEngine/JqLexer/JqParser/JqToken/JqTokenType/JqDirectQuery/IJsonQuery）、`io.nop.jq.jq.ast`（42）、`io.nop.jq.jq.runtime`（21）；test 位于 `io.nop.jq.jq`（5）与 `io.nop.jq.benchmark`（JqEngineBenchmark）；test 资源 `io/nop/jq/jq/jq-official.test`。
- **外部消费面**：`nop-ai-toolkit`（JqToolExecutor import `io.nop.jq.jq.*`）；nop-core `JPath`/`JPathEvaluator` javadoc 文本提及 `io.nop.jq.jsonpath.NopJsonPath`；design 文档与 module-groups.md 提及旧包名。jayway/jmh 仅 test。
- 测试基线：nop-jpath 95/0、nop-jq 554/0（TestJqOfficial 430/430）、ai-toolkit 243/0、TestWordTemplate 9/0（plan 2280 收口数据）。

## Goals

- nop-jpath 全部源码迁入 `io.nop.jpath`（扁平单包，24 main 类：NopJsonPath/Segment 管道/Filter/解析执行/accessor/桥接器/错误）。
- nop-jq 源码迁入 `io.nop.jq`（门面与解析）+ `io.nop.jq.ast` + `io.nop.jq.runtime`，消除 `jq.jq` 叠词。
- 全仓库 `io.nop.jq.jsonpath` 与 `io.nop.jq.jq.` 引用清零（消费方 import、javadoc、services 文件、测试资源路径字符串、owner docs）。

## Non-Goals

- **不改类名**（JqJPathInitializer、NopJqErrors、NopJsonPath 等保持），不改错误码前缀 `NOP_JQ-xxx`（外部可见，另行立项）。
- 不改任何行为逻辑、不动测试用例内容（仅包路径与 import）。
- 不处理 deepwiki-v3 生成产物（watch-only，plan 363 update 模式）。

## Execution Plan

### Phase 1 - 包迁移与消费方更新

Status: completed
Targets: `nop-kernel/nop-jpath/**`、`nop-jq/**`、`nop-ai/nop-ai-toolkit/**`、`nop-kernel/nop-core/src/main/java/io/nop/core/lang/json/jpath/**`

- Item Types: `Fix`

- [x] （Fix）nop-jpath：`git mv` 将 `io/nop/jq/jsonpath/**` 与根包 5 类迁入 `src/main/java/io/nop/jpath/`，包声明与全部 import 更新；test 同步（`io.nop.jpath` + `io.nop.jpath.benchmark`）；services 文件内容改为 `io.nop.jpath.JqJPathInitializer`。
- [x] （Fix）nop-jq：`git mv` 将 `io/nop/jq/jq/*`（7）迁入 `io/nop/jq/`，`jq/ast`→`io/nop/jq/ast`，`jq/runtime`→`io/nop/jq/runtime`；test 与资源 `jq-official.test` 同步（`io/nop/jq/jq-official.test`），更新测试内的资源路径字符串。
- [x] （Fix）消费方：nop-ai-toolkit import 更新为 `io.nop.jq.*`；nop-core JPath/JPathEvaluator javadoc 文本改为 `io.nop.jpath.NopJsonPath`。
- [x] （Fix）全仓库 grep 复核：`io.nop.jq.jsonpath`、`io.nop.jq.jq.`（含 test/resources/docs，deepwiki-v3 除外）零残留。
Exit Criteria:

- [x] （Proof）`./mvnw test -pl nop-kernel/nop-jpath -am` 与 `./mvnw test -pl nop-jq -am` 全绿（430/430 保持）；`./mvnw test -pl nop-ai/nop-ai-toolkit` 全绿；`./mvnw test -pl nop-format/nop-ooxml/nop-ooxml-docx -Dtest=TestWordTemplate` 全绿（ServiceLoader 发现链在新包名下连通——接线验证）。
- [x] 本 Phase 改变 public contract（包名）：`ai-dev/design/nop-jq/01-architecture-baseline.md`、`03-jpath-bridge-contract.md`、`docs-for-ai/01-repo-map/module-groups.md` 中的包名引用已同步。
- [x] `ai-dev/logs/` 当日条目已更新。

## Closure Gates

- [x] 所有 in-scope items 完成且全仓库旧包名 grep 清零（deepwiki-v3 除外）。
- [x] 四组验证全绿（两模块 + ai-toolkit + TestWordTemplate）。
- [x] owner docs 同步；`check-doc-links.mjs --strict` 0 errors。
- [x] 独立子 agent closure audit 完成并记录证据。
- [x] `check-plan-checklist.mjs --strict` 退出码 0；`scan-hollow-implementations.mjs` 双模块 exit 0。

## Closure

Status Note: 包名自然化完成——nop-jpath 全量 `io.nop.jpath` 扁平包、nop-jq 去 `jq.jq` 叠词（io.nop.jq / .ast / .runtime），类名与 NOP_JQ 错误码未动，四组 fresh 验证全绿且与改名前基线逐位一致，全仓库旧包名残留清零（历史文档除外）。
Completed: 2026-09-28

Closure Audit Evidence:

- Reviewer / Agent: 独立 fresh-session 子代理（agentId: agent_0a15c749-4b98-43f4-a63e-bb3a9b3127e0）
- Evidence:
  - 结构：nop-jpath main 24 类全在 io/nop/jpath（无 io/nop/jq 目录残留）；nop-jq main = io/nop/jq（7）+ ast（42）+ runtime（21），资源 io/nop/jq/jq-official.test；git status 全为 RM rename 条目
  - 残留 grep：代码/xml/资源/docs-for-ai/design 清零；仅历史 plans（01/361/2280/2281）、backlog 路线图与日志过程记录按裁定保留
  - fresh 复跑：nop-jpath 95/0、nop-jq 554/0（官方 430）、ai-toolkit 243/0（2 skip 为环境依赖沙箱测试，与基线一致）、TestWordTemplate 9/0（ServiceLoader 发现链新包名连通）
  - 契约面：services=io.nop.jpath.JqJPathInitializer；本地 m2 nop-jq jar 无 services 残留（clean install 修复旧 jar 污染）
  - `check-doc-links.mjs --strict` 0 errors；`scan-hollow-implementations.mjs` 双模块 exit 0；`check-plan-checklist.mjs --strict` 退出码 0（completed 后复跑）
