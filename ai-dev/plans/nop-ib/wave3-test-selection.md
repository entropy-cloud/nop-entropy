# nop-ib Wave 3 — 测试选择（roadmap items 19–24）

> Plan Status: active
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

Status: in progress
Targets: `nop-tools/nop-ib/src/main/java/io/nop/ib/cli/TestCommand.java`

- Item Types: `Feature`

- [x] runSurefire 委托：per-module `-pl :<module> surefire:test [-Dtest=...] -DfailIfNoSpecifiedTests=false`，BuildLock 内执行
- [x] test 命令：--affected（默认 git 变更集）/ --class [module:]Class / -pl 模块全量
- [x] check 命令：compile 后重判选择集（回退编译后重新判定，设计 01 §13.3）
- [x] 汇总解析（Tests run/Failures/Errors/Skipped）+ 退出码
- [ ] 真仓 E2E：body 探针 check 全绿且只跑选择集；耗时对比 mvn test 全量

Exit Criteria:

- [ ] E2E 实测数字在档（选择集规模、端到端耗时）
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] `ai-dev/logs/2026/11-14.md` 已更新

### Phase 2 - 漏测 oracle 与契约对齐（items 23–24）

Status: planned
Targets: `nop-tools/nop-ib/tools/`, plan evidence

- Item Types: `Proof`

- [ ] oracle 语料：构造/回放真实失败场景（改行为→test 选择集必须含会失败的测试类）
- [ ] 对照"全量测试失败集"与"选择集失败集"，漏选=0 或逐条裁定
- [ ] 02-test-execution 契约逐条核对表（cwd=委托形态由 surefire 自身保证；classpath=不合并、由 Maven 解释；快照禁用=不注入录制属性；超时=批次上限+进程看门狗）
- [ ] 差异即修

Exit Criteria:

- [ ] oracle 结果在档：漏选为零（或逐条裁定）
- [ ] 契约核对表写入本 plan
- [ ] `ai-dev/logs/2026/11-14.md` 已更新

## Closure Gates

- [ ] items 19–22 真仓实测达标
- [ ] item 23 oracle 在档
- [ ] item 24 契约核对表在档
- [ ] `./mvnw test -pl nop-tools/nop-ib` 全绿
- [ ] roadmap Wave 3 状态更新（M3 达成）
- [ ] 独立 closure audit（随 roadmap 收口批次）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 通过

## Deferred But Adjudicated

（无）

## Closure

Status Note: （待收口）
Completed: （待填）

Closure Audit Evidence: （待填）
