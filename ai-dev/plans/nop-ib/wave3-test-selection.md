# nop-ib Wave 3 — 测试选择（roadmap items 19–24）

> Plan Status: completed
> Last Reviewed: 2026-11-14
> Source: ai-dev/backlog/nop-ib-roadmap.md（Wave 3）; ai-dev/design/nop-incremental-build/05 §6.4/§7.2; 02-test-execution.md
> Related: wave1-readonly-kernel.md（选择引擎在 ScopePlanner）; wave2-compile-fastpath.md（补录）

## Purpose

测试选择集可执行：委托 Surefire 保真执行、`test`/`check` 命令面、漏测 oracle 与既有测试契约对齐。

## Current Baseline

- Wave 1 已落地选择引擎（行为闭包含方法体变化、放宽矩阵、full-test/selected 分轨）。
- Wave 2 已落地编译快路径与对拍零差异、锁互通。
- IB-G3 裁定：首版委托 `mvn -pl :<module> surefire:test -Dtest=<classes>`，cwd/argLine/系统属性由 Maven 自身按模块 pom 解释（委托形态天然保真，无需注入）。

## Goals

- item 19/20：测试索引与选择引擎（单元索引 testClass refs + ScopePlanner 闭包，已具）在 `test` 命令路径接线。
- item 21：`mvn surefire:test` 委托执行器（per-module 精确选择、锁内执行、超时看门狗）。
- item 22：`test`（--affected 默认/--class/-pl）与 `check`（compile+affected）命令，统一 JSON 与退出码。
- item 23：漏测 oracle——比较选择集与全量测试失败集，漏选必须为零或逐条裁定。
- item 24：对照 02-test-execution.md 逐条验证（cwd/classpath 定序/快照禁用/超时）在委托形态下的落实点。

## Non-Goals

- 自研 JUnit Platform 启动器（须同语料对照后另立项切换）。
- 动态覆盖收窄。

## Execution Plan

### Phase 1 - 委托执行与命令面（items 21–22）

Status: completed
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/cli/TestCommand.java`

- Item Types: `Feature`

- [x] runSurefire 委托：per-module `-pl :<module> surefire:test [-Dtest=...] -DfailIfNoSpecifiedTests=false`，BuildLock 内执行
- [x] test 命令：--affected（默认 git 变更集）/ --class [module:]Class / -pl 模块全量
- [x] check 命令：compile 后重判选择集（回退编译后重新判定，设计 01 §13.3）
- [x] 汇总解析（Tests run/Failures/Errors/Skipped）+ 退出码
- [x] 真仓 E2E：body 探针 check 4.5s（编译+选择集 24 测试绿）；oracle 探针失败退出 1（选择集含失败类）；mvn 全模块 test 4.26s 对照

Exit Criteria:

- [x] E2E 实测数字在档（选择集 1 类/24 测试,4.5s）
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿（17 tests）
- [x] `ai-dev/logs/2026/11-14.md` 已更新

### Phase 2 - 漏测 oracle 与契约对齐（items 23–24）

Status: completed
Targets: `nop-tools/nop-ib/tools/`, plan evidence

- Item Types: `Proof`

- [x] oracle 语料：formatJavaSource 行为探针（全量失败集 {TestJavaCompileTool}）
- [x] 对照: 全量失败集 {TestJavaCompileTool} ⊆ 选择集失败集（24 run/2 failed,同类）→ 漏选=0
- [x] 契约核对表: cwd=模块 basedir（surefire 自身保证,委托形态）; classpath=由 Maven 按 -pl 模块解释,无跨模块合并; 快照禁用=nop-ib 不注入任何录制/禁用属性; 超时=MavenFallback 30-60min 上限+destroyForcibly 看门狗+锁超时 75
- [x] 差异即修（修复自死锁:外层 BuildLock×mvnq 同锁嵌套）

Exit Criteria:

- [x] oracle 结果在档：漏选为零
- [x] 契约核对表写入本 plan
- [x] `ai-dev/logs/2026/11-14.md` 已更新

## Closure Gates

- [x] items 19–22 真仓实测达标
- [x] item 23 oracle 在档
- [x] item 24 契约核对表在档
- [x] `./mvnw test -pl nop-tools/nop-ib` 全绿（17 tests）
- [x] roadmap Wave 3 状态更新（M3 达成）
- [x] 独立 closure audit（随 roadmap 收口批次）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 通过

## Deferred But Adjudicated

（无）

## Closure

Status Note: 语义与实测已收口;独立 closure audit 已完成。
Completed: 2026-11-14

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent closure audit（session agent_ff78ed3b-bbac-4675-8cdc-099bd662fc18,2026-11-14）
- Evidence: `./mvnw test -pl nop-tools/nop-ib` 17/0;CLI plan/status/check 实测;调用链 Anti-Hollow 追踪连通;check-doc-links 0 errors;check-plan-checklist 通过（含 Reviewer/Evidence 字段）。
