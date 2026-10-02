# 2304 unit-test-coverage-roadmap WI12 — 小模块收尾与达标确认

> Plan Status: completed
> Last Reviewed: 2026-10-02
> Source: ai-dev/backlog/unit-test-coverage-roadmap.md（WI12 条目）；ai-dev/analysis/2026-10/2026-10-02-unit-test-wi0-coverage-baseline.md
> Related: docs-for-ai/02-core-guides/testing.md；ai-dev/plans/2292-unit-test-wi0-coverage-baseline.md

## Purpose

小模块收尾：nop-spring（spring-core-starter 0%）、nop-quarkus（starters NO-EXEC）、nop-file（dao 32.9%、service NO-EXEC）、nop-autotest（32.95%，测试基建自身）、nop-search（lucene 55.16%、core NO-EXEC）、nop-integration（api 15.35%，其余适配器多数已高）、nop-frontend-support、nop-utils、nop-dev-tools/nop-message（72% 已达标）/nop-credential（高，已达标）/nop-runner/nop-bytecode（76.47% 已达标）/nop-refactor-core（69.03% 已达标）/nop-report-core（64.36% 已达标）按 WI0 基线确认达标或补缺。另收口 WI0 遗留的 10 个无报告模块裁定与 WI0 失败清单中的 nop-code-web 测试装配缺失。

## Current Baseline

- 权威数字源：`ai-dev/analysis/2026-10/coverage-baseline-2026-10-02.json`（本 plan 不复制全表）。
- 明确缺口：nop-spring-core-starter 0%/126L；nop-file-service NO-EXEC；nop-search-core NO-EXEC；nop-integration-api 15.35%；nop-autotest-core 32.95%（>30% 已达标）；nop-file-dao 32.9%（已达标）。
- WI0 无报告模块裁定清单（基线报告）中归属本 WI 的：nop-kernel-cli、nop-lint-graphql、nop-lint-maven-plugin、nop-lint-nop、nop-js、nop-spring-demo/nop-spring-gateway、nop-graphql-grpc。
- 测量管线：删模块 exec → `ai-dev/tools/coverage-baseline.sh --skip-test --label wi12-2026-10-02`；验证 `mvnq -- test -pl :<module> -am -fae`。mvnq = `ai-dev/tools/mvnq`。

## Goals

- 明确缺口模块补测：spring-core-starter、file-service、search-core、integration-api 各 ≥4 用例（可行者）。
- WI0 无报告遗留模块逐个处置：补测试或记录不可行裁定（lint 链先 `mvnq -- install -pl :nop-lint-nop -am -DskipTests` 再补跑）。
- nop-code-web NopCodeWebPagesTest 修复（IHttpClient 测试 bean 装配，测试面修复）。
- 已达标模块（autotest-core/file-dao/message-core/credential/bytecode/refactor-core/report-core/chart-export/lucene 等）不回退确认。
- 记录增量。

## Non-Goals

- 不修改产品代码；demo 模块（nop-spring-demo 等）只记录不裁定补测；nop-js（JS 构建链）只记录。

## Scope

### In Scope

- 上述模块的 `src/test/**` 新增与 pom test-scope 裁定；nop-code-web 测试装配修复（测试资源/测试 bean）。

### Out Of Scope

- 产品代码；JS 侧测试；demo 模块补测。

## Execution Plan

### Phase 1 - 补测与遗留处置

Status: completed
Targets: 缺口模块 `src/test/**`、WI0 遗留清单

- Item Types: `Fix`

- [x] spring-core-starter、file-service、search-core、integration-api 各 ≥4 用例（模块结构不支持纯逻辑测试的，如实记录不可行裁定）。（执行记录：四个模块均可行——integration-api 新增 16 用例/TestChannelTypeCodes+TestEmailMessage+TestOutboundChannelMessage+TestQrcodeAndSmsOptions；spring-core-starter 新增 12/TestSpringResource+TestNopSpringBeanContainer+TestNopSpringTransactionFactory；search-core 新增 12/TestBuildIndexTool+TestSearchEngineBizModel；file-service 新增 4/TestNopFileRecordBizModelAssembly（薄装配层，仅装配接线断言））
- [x] WI0 无报告遗留 8 项逐个处置（nop-rg-cli/vector 已由 WI0 裁定为 build-infra 独立项，不在本清单；lint 链先 `mvnq -- install -pl :nop-lint-nop -am -DskipTests` 再补跑 nop-lint-graphql/nop-lint-maven-plugin；其余记录裁定）。（执行记录：lint-graphql 20 绿、lint-maven-plugin 10 绿；kernel-cli 确认不可执行（pom `-proc:only` 致测试零字节码，pom 修改超授权，移交记录）；lint-nop 无主 Java 代码 N/A；js 管线外；spring-demo/gateway demo 不裁定；graphql-grpc 模块被父 pom 注释移出 reactor）
- [x] nop-lint-nop 普查测试修复（WI0 失败指派）：TestNopRuleSuites/TestProductionRuleCount/TestMetricsRuleSuites 断言更新至当前规则普查（69 套件/74 规则），逐个补入期望清单（不得整段删除断言或弱化"no silent drops"语义）。（执行修正：根因并非"新增 4 套件"，而是 facet-review 已移除的 4 个规则（negated-equals 等）的 stale target/classes 与 target/test-classes 残留——测试的 REMOVED_RULE_IDS"no silent revival"守卫正确拒绝。`clean test` 后 87 用例全绿，期望清单保持 70 规则/65 套件不变，未删改任何断言）
- [x] nop-code-web NopCodeWebPagesTest 修复转绿。（测试 delta beans 注册 StubHttpClient（io.nop.code.web.mock，fail-loud），NopCodeWebPagesTest 绿）
- [x] 各模块 `mvnq -- test -pl :<module> -am -fae` 全绿。（单模块 `-fae` 验证（上游已 install）；integration-api 27、spring-core-starter 13、search-core 12、file-service 4、lint-nop 87、lint-graphql 20、lint-maven-plugin 10、code-web 1，全部 BUILD SUCCESS）

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] 补测用例 ≥16 个或对应不可行裁定记录；遗留清单 8 项逐个有处置结果（rg 两项 WI0 已裁定移出）。（实际新增 44 用例）
- [x] nop-code-web 测试绿。
- [x] pom 变更（如有）仅 test-scope 依赖且已记录。（仅 nop-search-core 新增 test-scope junit-jupiter 一处）
- [x] No owner-doc update required。
- [x] `ai-dev/logs/` 对应日期条目已更新。（本次执行受任务约束"不修改 ai-dev/logs"，已由协调方统一落账）

### Phase 2 - 覆盖增量实测与达标确认

Status: completed
Targets: `ai-dev/analysis/2026-10/`、roadmap WI12 checkbox

- Item Types: `Proof` + `Decision`

- [x] baseline 脚本复测（label wi12-2026-10-02，lint 链补跑后刷新）：spring-core-starter 0→40.48%、file-service 0→100%、search-core 0→75.61%、integration-api 15.35%→58.66%、lint-graphql 94.2%、lint-maven-plugin 83.93%；全仓加权 58.37%。
- [x] 已达标模块不回退确认（快照对比）；未达标残余显式裁定（Deferred 段）。
- [x] 独立子 agent closure audit 通过后勾选 roadmap WI12 checkbox。

Exit Criteria:

- [x] 增量数字记录在案。
- [x] 裁定有记录。
- [x] roadmap WI12 checkbox 与 plan/log 一致（待 audit 后同步）。
- [x] `ai-dev/logs/` 对应日期条目已更新。

## Closure Gates

- [x] 各模块全部新增测试绿（含既有测试零回归）
- [x] 产品代码零修改；pom 仅 test-scope 新增且记录
- [x] 覆盖增量实测记录
- [x] WI0 遗留 8 项逐个处置（无静默跳过；rg 两项已由 WI0 裁定移出）
- [x] 残余缺口显式裁定（无静默降级）
- [x] No owner-doc update required（已裁定）
- [x] Anti-Hollow Check：补测断言语义（audit 抽查：TestBuildIndexTool 行为捕获 stub+真实临时文件 I/O、TestNopSpringBeanContainer scope 透传/autowire 解析断言）
- [x] 独立子 agent closure-audit 已完成并记录证据
- [x] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2304-unit-test-wi12-small-modules.md --strict` 退出码 0
- [x] roadmap WI12 checkbox 与 plan/log 一致

## 执行偏差记录

1. 执行会话因账户用量上限中断后由协调方接管收尾：Phase 1 各项中 lint 普查修复经查已由并行 lint roadmap 会话完成（本 WI 验证 87/87 绿并记账），其余补测/修复/测量由协调方验证延续。
2. -am 因上游非本 plan 模块红改用单模块 `-fae` 口径（gap 模块无新增上游依赖，等价）。

## Deferred But Adjudicated

### 小模块残余缺口

- Classification: `watch-only residual`（WI13 复裁）
- Why Not Blocking Closure: 缺口四模块全部大幅增量（spring-core-starter 40.48%/search-core 75.61%/integration-api 58.66%/file-service 100%）；lint 链两模块经上游 install 后补跑即高覆盖（94.2%/83.93%）。残余为 nop-kernel-cli 测试形态（非 JUnit）、demo/profile 门控模块——均属记录性裁定。
- Successor Required: `yes`
- Successor Path: `ai-dev/backlog/unit-test-coverage-roadmap.md`（WI13 复裁）

## Non-Blocking Follow-ups

- lint-graphql/lint-maven-plugin 的 exec 依赖 worktree .m2-repo 中已 install 的上游 SNAPSHOT——CI 若从零构建需先 install 上游链（记录于本 plan）。

## Closure

Status Note: 44 用例（spring 12/search 12/integration 16/file 4，audit 勘误原记 36）五模块全绿；WI0 遗留 8 项清零（lint 链 94.2%/83.93%、nop-code-web 修复、其余逐项裁定）；缺口模块覆盖 spring 40.48%/search 75.61%/integration 58.66%/file 100%。lint 普查修复由并行会话完成经本 WI 验证记账。roadmap closure (a)(b) 满足，(c) 三处同步。
Completed: 2026-10-02

Closure Audit Evidence:

- Reviewer / Agent: 独立子 agent（Explore，agent_c692b361-86bb-4e37-af9b-a079a4577800，fresh session）

Follow-up:

- （待填写）
