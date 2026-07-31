# MA4 P2 批量修复（第二批：测试质量 — 测试覆盖 + 测试有效性）

> Plan Status: active
> Mission: audit-remediation
> Work Item: MA4 P2 test-quality batch（MA4.3 + MA4.4）
> Last Reviewed: 2026-07-31
> Source: `ai-dev/audits/2026-07-31-XXXX-arm-MA4.3-nop-ai-test-coverage.md`、`2026-07-31-arm-MA4.4-nop-ai-test-effectiveness.md`、roadmap 规则 1（P2/P3 deferred successor）
> Related: `2026-07-31-1446-1-arm-roadmap-convergence.md`、`2026-07-31-1446-2-arm-ma4-p2-code-quality.md`
> Draft Review: 3 轮独立子 agent 对抗性审查通过（含想象性分析），无 Blocker/Major（final round: ses_048f7f8b3ffeVZoE9IYClAED5w）

## Purpose

修复 MA4 审计产出的第二批 P2 测试质量 finding（MA4.3 测试覆盖 + MA4.4 测试有效性），为已确认的覆盖缺口与异常路径缺失补充回归测试。本计划是 roadmap 规则 1 中 P2/P3 deferred successor 的第二批实现。

## Current Baseline

- MA4.3 P2（live 已核验）：
  - MA4.3-06：nop-ai-coder 核心包（service/convert/xdsl/xdef/orm/code/config/meta/utils）无聚焦测试；ApiModelToJava、AiApiModel、convert 确认零覆盖；3 个测试文件断言不足（AiConverterTest 2 个零断言 + 1 个值级 assertTrue、AiGenCodeTask/TestAiTask 无断言）
  - MA4.3-08：nop-ai-shell adapter/ + checker/ 无**独立**测试类（注意：ShellCommandExecutorTest 已覆盖 checker 接线、DefaultCommandChecker 契约、LsCommand、adapter 异常路径；CdCommand/EchoCommand 有独立测试类）——真实缺口为 checker 独立类、ExternalCommandAdapter 正向路径
  - MA4.3-12：8 个测试类零断言（TestAiChatService、TestGptOrmModelParser、AiGenCodeTask、TestAiTask、NopAiWebPagesTest、NopAiWebCodeGen、NopAiCodeGen、VfsMavenUsageExample）——其中 6 个为 codegen runner/示例类，需裁定加断言或重命名 Runner/Manual
  - MA4.3-14：nop-ai-core `service/` 8 个服务类仅 3 个测试文件，`DefaultAiChatService`、`DefaultAiChatSession`、`AbstractAiChatSession`、`DefaultChatLogger`、`ChatLogHelper`、`LlmConfigHelper`、`MockAiChatService` 未覆盖
- **既有覆盖（避免重复造轮子）**：`nop-ai-shell/src/test/java/io/nop/ai/shell/executor/ShellCommandExecutorTest.java` 已含 `testPreCheckRejectionReturns126`（checker 拒绝 → exit 126 + 拒绝消息）、`testPreCheckAllowsNonBlockedCommands`、`testDefaultCommandCheckerAllowsAll`、`testExternalCommandAdapterThrowsException`、`testLsCommand`/`testLsLongFormat`；`CdCommandTest`/`EchoCommandTest`/`ShellCommandRegistryTest` 已存在——Phase 3 的接线验证引用此既有覆盖，不重复编写
- MA4.4 P2（live 已核验）：
  - MA4.4-04：BashSyntaxParserTest 30+ 正常语法用例，仅 1 个错误用例（testParseError）；无 null/空串/超长/畸形 redirect
  - MA4.4-05：nop-ai-toolkit 22 个测试文件 0 个 assertThrows
  - MA4.4-06：nop-ai-core dialect 测试（TestOpenAiDialect 等 5 个文件）0 个 assertThrows，全 happy-path
  - MA4.4-08：全模块异常路径覆盖 31.6%（112/354 nop-ai-agent 文件用 assertThrows）
- 项目零 Mockito 策略（MA4.3-11，P3 信息项）：测试用真实实现/H2/手写 test double，本计划沿用
- P2 findings 均未被 MR1-MR4 修复（MR2/MR4 只修了 MA4.3 的 P1：nop-ai-api/dao/tools/service/core-api 等零测试模块已补测试）
- 既有测试基准：`TestNopAiChatResponseSummarizeByModel`（7 个 @Test、20 assertEquals + 6 assertNotNull + 3 assertThrows；live 实测计数）为 BizModel 测试标杆

## Goals

- MA4.3-12：零断言测试文件逐一处置——能加断言的加断言（TestAiChatService 改用 MockAiChatService 或裁定 rename），codegen runner 类裁定重命名（Runner/Manual 后缀）或加输出断言
- MA4.3-06：nop-ai-coder 关键转换/生成逻辑（ApiModelToJava、AiApiModel、xdsl/xdef、convert）补充聚焦测试
- MA4.3-08：nop-ai-shell 补齐真实测试缺口（checker 独立测试类、adapter 正向路径），接线验证引用既有 ShellCommandExecutorTest 覆盖
- MA4.3-14：nop-ai-core `service/` 8 个服务类补充聚焦测试（至少 6 个）
- MA4.4-04/05/06/08：为 BashSyntaxParser、toolkit executors（error-result 语义断言）、core dialects（含新建 Ollama/Gemini 测试类）补充异常路径测试
- 不改变项目零 Mockito 策略

## Non-Goals

- 不修复 MA4.1/MA4.2/MA4.5 P2（代码质量批次，由 `2026-07-31-1446-2` 承接）
- 不追求全模块行覆盖率达标（测试覆盖是逐模块聚焦，不是覆盖率工程）
- 不重写既有绿色测试，不改既有断言强度（保留既有覆盖）
- 不改生产代码行为（除非测试暴露 live defect——暴露时升级为 Fix 并记入本 plan scope）

## Scope

### In Scope

- MA4.3-06、MA4.3-08、MA4.3-12、MA4.3-14
- MA4.4-04、MA4.4-05、MA4.4-06、MA4.4-08
- 测试暴露的 live defect（如有，就地修复 + 回归测试）

### Out Of Scope

- MA4.3-09/13 assertTrue-only 升级（P3，NoOp 测试可接受）
- MA4.3-11 Mockito 策略变更（P3 信息项，维持现状）
- MA4.4-01/02/03（P3，低价值测试优化）
- 其他里程碑 P2/P3

## Execution Plan

> **验证命令说明**：nop-ai-* 子模块仅在 `nop-ai/pom.xml` 聚合，根 POM reactor 只认 `nop-ai` 聚合入口；因此 `-pl` 选择器在本仓库应统一用 `-pl nop-ai -am`（全量，daily log 惯例）。

### Phase 1 — MA4.3-12 零断言测试处置 + MA4.3-14 nop-ai-core service 覆盖

Status: planned
Targets: `nop-ai/nop-ai-core/src/test/java/io/nop/ai/core/service/TestAiChatService.java`、`nop-ai/nop-ai-dsl-orm/src/test/java/io/nop/ai/dsl/orm/TestGptOrmModelParser.java`、`nop-ai/nop-ai-coder/src/test/java/io/nop/ai/coder/AiGenCodeTask.java`、`nop-ai/nop-ai-codegen/src/test/java/io/nop/ai/codegen/NopAiCodeGen.java`、`nop-ai/nop-ai-maven/src/test/java/io/nop/ai/maven/examples/VfsMavenUsageExample.java`、`nop-ai/nop-ai-web/src/test/**/NopAiWebPagesTest.java`、`NopAiWebCodeGen.java`、`nop-ai/nop-ai-coder/**/TestAiTask.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/service/`

- Item Types: `Fix | Decision`

- [ ] 逐文件裁定：`TestAiChatService`（当前 `@Disabled` 且依赖真实 LLM API，加断言需改用 main 中已存在的 `MockAiChatService` 做 round-trip，或裁定 rename Manual）、`TestGptOrmModelParser`（补正常解析输出断言）等可自动化测试的类补充断言（最低验证服务初始化 + 基本 round-trip / parser 正常解析输出）
- [ ] codegen runner/示例类（AiGenCodeTask、NopAiCodeGen、NopAiWebCodeGen、NopAiWebPagesTest、VfsMavenUsageExample、TestAiTask）裁定：加输出断言（生成文件存在/内容包含关键标记）或重命名为 `*Runner`/`*Manual` 后缀 + 类 javadoc 标注 manual；**注意**：NopAiCodeGen/NopAiWebCodeGen 是 main()-only 非 JUnit 类（加断言需转 @Test）；VfsMavenUsageExample 被 `nop-ai-maven/README.md` 引用（改名会断链，需同步改 README）；AiGenCodeTask/TestAiTask 为 `@Disabled` 类（改名不影响 surefire 执行，但若改为启用需先补断言）
- [ ] MA4.3-14：为 nop-ai-core `service/` 未覆盖类补聚焦测试——`DefaultAiChatService`、`DefaultAiChatSession`、`AbstractAiChatSession`（session 生命周期）、`LlmConfigHelper`（配置加载）、`DefaultChatLogger`/`ChatLogHelper`（日志路径）、`MockAiChatService`（round-trip）；`TestAiChatService` 若改为 rename 则 `MockAiChatService` 的 round-trip 测试在此补充，确保 service 包覆盖不因 rename 丢失
- [ ] 裁定结果与理由记录到本 plan（每文件一行：add-asserts | rename-runner | keep+@Disabled+doc）

Exit Criteria:

- [ ] 8 个零断言文件全部有明确处置（grep 复核：原路径不再存在零断言测试类；**例外**：裁定为 keep+@Disabled+doc 的类必须在每文件裁定清单中记录，且该类须为已 @Disabled 状态——未经裁定不得保留零断言类）
- [ ] 新加断言验证正确结果（非仅不抛异常）
- [ ] 改名类同步更新 README/引用，无断链
- [ ] nop-ai-core `service/` 8 个服务类中至少 6 个有聚焦测试（MA4.3-14 覆盖）
- [ ] `./mvnw test -pl nop-ai -am` 通过
- [ ] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 2 — MA4.3-06 nop-ai-coder 关键逻辑测试

Status: planned
Targets: `nop-ai-coder/src/main/java/io/nop/ai/coder/service/ApiModelToJava.java`、`AiApiModel.java`、`xdsl/`、`xdef/`、`convert/`、`orm/`

- Item Types: `Fix`

- [ ] 为 ApiModelToJava 代码生成逻辑补测试（方法签名/类型转换/输出结构断言）
- [ ] 为 AiApiModel 服务模型构建补测试（输入模型 → 输出 Java 结构）
- [ ] 为 xdsl/xdef 转换逻辑补测试（至少 1 个典型转换场景 + 1 个畸形输入断言）
- [ ] 为既有 AiConverterTest 升级断言（实测 3 个测试中 2 个零断言、1 个 assertTrue 为值级 contains 检查——补齐零断言用例；经 MA4.3-06 的 suggestion 纳入本计划，不属 MA4.3-09/13 的 assertTrue-only 升级范畴）

Exit Criteria:

- [ ] coder 关键类（ApiModelToJava、AiApiModel、xdsl/xdef 转换）每个至少 1 个聚焦测试且含值级断言
- [ ] 畸形输入路径有 assertThrows 或显式失败断言
- [ ] `./mvnw test -pl nop-ai -am` 通过（全量；含 nop-ai-coder）
- [ ] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 3 — MA4.3-08 nop-ai-shell adapter/checker/commands 测试

Status: planned
Targets: `nop-ai-shell/src/main/java/io/nop/ai/shell/adapter/`、`checker/`、`commands/`

- Item Types: `Fix | Proof`

> **既有覆盖引用**：`ShellCommandExecutorTest` 已含 checker 接线拒绝路径（exit 126）、放行路径、DefaultCommandChecker 契约、ExternalCommandAdapter 异常路径、LsCommand（testLsCommand/testLsLongFormat）；CdCommand/EchoCommand 有独立测试类——本 Phase 不重复编写，补齐真实缺口并引用既有覆盖作为接线验证证据。

- [ ] 核验并引用既有 `ShellCommandExecutorTest` 的接线与命令覆盖（testPreCheckRejectionReturns126 等）作为 MA4.3-08 接线验证证据（Proof）
- [ ] 为 checker 补独立测试类（ICommandChecker 契约：通过返回 null、拒绝返回非 null 消息；含 DefaultCommandChecker 显式测试类）
- [ ] 为 ExternalCommandAdapter 补正向路径测试（既有仅异常路径）
- [ ] 扫描 commands/impl 补齐其余无测试命令（若有除 Cd/Echo/Ls 外未覆盖者）
- [ ] adapter 其余实现（如有）补核心转换路径测试

Exit Criteria:

- [ ] **接线验证**：ShellCommandExecutor → ICommandChecker 运行时调用链有测试覆盖（引用既有 ShellCommandExecutorTest 证据 + 新增 checker 独立测试类）
- [ ] checker 独立测试类存在（DefaultCommandChecker 契约显式验证）
- [ ] ExternalCommandAdapter 有正向路径测试
- [ ] `./mvnw test -pl nop-ai -am` 通过（全量；含 nop-ai-shell）
- [ ] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 4 — MA4.4 异常路径测试

Status: planned
Targets: `nop-ai-shell/src/test/java/io/nop/ai/shell/parser/BashSyntaxParserTest.java`、`nop-ai-toolkit/src/test/java/**`、`nop-ai-core/src/test/java/io/nop/ai/core/dialect/Test*Dialect.java`

- Item Types: `Fix`

> **toolkit 实现事实**：全部 executor 主类 0 个 `throw` 语句，错误通过 `AiToolCallResult.errorResult(...)`（status/error message）返回而非抛异常。因此 toolkit 的异常路径验证断言 **error-result 语义**（status/error message 非 null 且含预期信息），不强行 assertThrows；仅当执行器确实抛出（如 NPE 防御）时用 assertThrows。

- [ ] MA4.4-04：BashSyntaxParserTest 补 null 输入、空串、超长输入、畸形 redirect/管道的 assertThrows（或明确失败断言）
- [ ] MA4.4-05：toolkit executor 测试补错误路径——无效参数、缺失文件系统、权限拒绝的 **error-result 断言**（断言 status/error message），覆盖已有测试的 executor 补路径；若某 executor 确实抛出异常则用 assertThrows
- [ ] MA4.4-06：dialect 测试补畸形 JSON、null response、错误码解析的 assertThrows；**注意**：测试目录现有 TestOpenAiDialect/TestAnthropicDialect 两个文件，Ollama/Gemini 需新建测试类（TestOllamaDialect/TestGeminiDialect）或并入 TestLlmDialectFactory——执行时按模块结构裁定，**新建测试类在本 plan 授权范围内**
- [ ] MA4.4-08：以上补充后异常路径覆盖比例提升，记录修正后计数

Exit Criteria:

- [ ] BashSyntaxParser 至少 4 个边界/畸形输入用例
- [ ] toolkit 至少 3 个 executor 有错误路径用例（error-result 断言或 assertThrows，按 live 行为选择）
- [ ] 4 个 dialect（OpenAi/Anthropic/Ollama/Gemini）各有至少 1 个异常路径用例（新建测试类授权）
- [ ] `./mvnw test -pl nop-ai -am` 通过（全量；含 shell/toolkit/core）
- [ ] 覆盖比例修正值记录到 arm-index MA4.4 行或 daily log
- [ ] `ai-dev/logs/2026/07-31.md` 已更新

### Phase 5 — 全量验证 + arm-index 更新 + closure

Status: planned
Targets: `ai-dev/audits/arm-index.md`、全 nop-ai 模块

- Item Types: `Proof | Follow-up`

- [ ] 更新 arm-index：MA4.3/MA4.4 行 P2 修复状态标注（或新增 P2 修复追踪段）
- [ ] 全量 `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [ ] 全量 `./mvnw test -pl nop-ai -am -T 1C`
- [ ] 独立子 agent closure audit

Exit Criteria:

- [ ] arm-index P2 修复状态可追溯（finding → 测试文件 → 断言）
- [ ] 全量 build + test 绿
- [ ] 独立 closure audit 证据写入本 plan Closure 段
- [ ] `ai-dev/logs/2026/07-31.md` 已更新

## Closure Gates

- [ ] MA4.3/MA4.4 全部 in-scope P2 finding（含 MA4.3-14）已修复或裁定（无静默降级）
- [ ] 零断言文件处置清单完整（每文件明确 add-asserts 或 rename-runner）
- [ ] 异常路径测试覆盖新增可复核（grep error-result/assertThrows 计数对比）
- [ ] 不存在被静默降级到 deferred/follow-up 的 in-scope live defect（测试暴露的 defect 已就地修复或显式记录）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] **Anti-Hollow Check**：closure audit 验证（a）新测试断言验证正确结果而非仅不抛异常，（b）toolkit error-result 断言与 live 实现语义一致（非强行 assertThrows），（c）无空测试方法/吞断言作为"覆盖"
- [ ] `./mvnw clean install -DskipTests -pl nop-ai -am -T 1C`
- [ ] `./mvnw test -pl nop-ai -am -T 1C`
- [ ] checkstyle / 代码规范检查通过（实际门禁为 compile 级，见 MV plan 记录）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1446-3-arm-ma4-p2-test-quality.md --strict` 退出码 0（closure 时）

## Deferred But Adjudicated

### MA4.3-09/13 assertTrue-only NoOp 测试升级（P3）

- Classification: `watch-only residual`
- Why Not Blocking Closure: NoOp/contract 测试中 `assertTrue(result)` 对平凡布尔返回是正确断言（MA4.3-09 审计原文认可）；TestDefaultPermissionMatrix/TestDbSessionTakeoverLockDualInstanceE2E 的升级属 P3 优化。
- Successor Required: `no`

### MA4.4-01/02/03（P3 低价值测试）

- Classification: `watch-only residual`
- Why Not Blocking Closure: getter/setter round-trip 与编译器保证断言提供文档价值但缺陷检测价值低；审计自身标注 Low。
- Successor Required: `no`

### 全模块行覆盖率达标

- Classification: `optimization candidate`
- Why Not Blocking Closure: 本计划聚焦 MA4.3/MA4.4 P2 的已确认缺口；行覆盖率工程化是持续治理项，非 closure 必需。
- Successor Required: `no`

## Non-Blocking Follow-ups

- MA4.1/MA4.2/MA4.5 P2（代码质量）由 `2026-07-31-1446-2-arm-ma4-p2-code-quality.md` 承接
- MA1-MA3/MA5-MA6 P2 批量修复按严重度排序规划后续 successor

## Closure

Status Note: 待执行后填写
Completed: （待填写）

Closure Audit Evidence:

- Reviewer / Agent: （待填写）
- Evidence: （待填写）

Follow-up:

- （待填写）

## Optional Sections

## Risks And Rollback

- 测试补充不改生产行为；若测试暴露 live defect，修复范围限制在最小集合并记录。
- 重命名 runner 类可能破坏既有引用——先 grep 引用（含 README 等非 Java 文件）再改，或保留原类名加 @Disabled + javadoc 标注（Decision 选项）；main()-only 类转 @Test 需确认运行环境。
- TestAiChatService 当前 @Disabled + 真实 LLM API 依赖，加断言必须改用 MockAiChatService（main 中已存在），避免 key 依赖的 flaky 测试。
