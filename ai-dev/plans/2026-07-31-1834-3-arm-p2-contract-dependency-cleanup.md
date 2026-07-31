# 3 P2 契约/依赖清理批次（MA3.5 + MA3.1 + MA1.x + MA2.7 + MA3.4 结构类 P2）

> Plan Status: active
> Last Reviewed: 2026-07-31
> Source: `ai-dev/backlog/audit-remediation-roadmap.md` §P2/P3 Deferred Successors（watch-only residual，按严重度排序另行规划）、`ai-dev/audits/2026-07-31-0423-arm-MA3.5-nop-ai-cross-module-contract.md`、`2026-07-31-0753-arm-MA3.1-nop-ai-cross-module-deps.md`、`2026-07-31-2200-arm-MA1.3-nop-ai-toolkit.md`、`2026-07-31-2201-arm-MA1.4-nop-ai-infra.md`、`2026-07-31-2202-arm-MA1.5-nop-ai-naming.md`、`2026-07-31-0409-arm-MA2.7-nop-ai-ioc.md`、`2026-07-31-0423-arm-MA3.4-nop-ai-error-handling.md`、`2026-07-31-0348-arm-MA2.3-nop-ai-delta.md`
> Related: `ai-dev/plans/2026-07-31-1834-1-arm-p2-security-hardening.md`、`ai-dev/plans/2026-07-31-1834-2-arm-p2-reliability-observability.md`、`ai-dev/plans/2026-07-31-0000-1-arm-mr1-fix.md`

## Purpose

修复 MA3.5（跨模块契约）、MA3.1（跨模块依赖）、MA1.x（结构/命名）、MA2.7（IoC）、MA3.4（错误处理）、MA2.3（Delta）审计遗留的**结构/契约类 P2 finding**（均经 live repo 复核仍成立）：核心模块依赖泄漏、废弃 API 残留、beans.xml 命名空间拼写错误、死代码/存根执行器、硬编码包名、错误处理不规范、i18n 缺口。这是 roadmap 规则 1 下 P2/P3 deferred successor 的第五批，按严重度排序承接 watch-only residual。

## Current Baseline

- MR1 已修复 P1-MA1-003/010/017/018/019/031/032/033、P1-MA2-014/018 等；MR2 已修复 P1-MA3-01/02（core 内部包依赖泄漏的 P1 部分）。
- **P2-MA3-001（live 确认）**：`nop-ai-core/pom.xml:27` 声明 `nop-dao` compile 依赖，`nop-ai-core/src/main/java/` 零 `io.nop.dao` import（Rule 3 层违例）。**连带面**：`nop-ai-coder/.../orm/AiOrmModelNormalizer.java:7` import `io.nop.dao.dialect.SQLDataType`，而 nop-ai-coder 未声明 nop-dao——nop-dao 经 nop-ai-core 传递提供；移除 nop-ai-core 的 nop-dao 会打破 nop-ai-coder 编译，必须同步给 nop-ai-coder 补直接依赖（或移除该 import）。
- **P2-MA3-03（live 确认）**：`nop-ai-tools/.../graphql/GraphQLToolProvider.java:3-6` 使用废弃 `IAiChatFunctionTool`/`IAiChatToolSet`/`DefaultAiChatFunctionTool`/`DefaultAiChatToolSet`（core 内废弃 API）；`GraphQLToolSetFactoryBean` 同。
- **P2-MA3-04（live 确认）**：`core/api/chat/IAiChatService.java:15` `@Deprecated(forRemoval=true)` 但仍是 core 主干实现（`DefaultAiChatService` 实现、`AiCommand` 使用）；`IAiChatSession`/`IAiChatFunctionTool` 同——deprecation 误导。
- **P2-MA3-05（live 确认）**：`nop-ai-tools/.../file/FileToolBizModel.java:6-7`、`nop-ai-coder/.../xdsl/DslToolImpl.java:3-4` 依赖 core 内部 `io.nop.ai.core.file.IFileOperator`/`LocalFileOperator`。
- **P2-MA3-06（live 确认）**：`api/chat/ChatOptions.java`（516 行，非废弃）与 `core/api/chat/AiChatOptions.java`（442 行，@Deprecated）字段不对称重复。
- **P2-MA3-08（live 确认）**：`nop-ai-coder/.../convert/AiXdefDocumentConverter.java` import core 内部 xdef 包 `AiXDefHelper`。
- **P2-MA1-006 / P2-MA2-029（live 确认）**：`nop-ai-tools/src/main/resources/_vfs/nop/ai/beans/ai-tools-defaults.beans.xml:1` `xmlns:x="/nop/schema/xdsl.xef"` 命名空间拼写错误（`xdsl.xef` → `xdsl.xdef`）。
- **P2-MA1-008（live 确认）**：`SearchEngineExecutor` 类存在（`nop-ai-toolkit/.../tools/SearchEngineExecutor.java`）但 bean 在 `ai-tools-defaults.beans.xml:26` 被注释——死代码 + 未接线类。
- **P2-MA1-011（live 确认）**：`AskOracleExecutor` 为存根：无 `ORACLE_ENDPOINT` 时静默返回第一个 option 作为成功结果，无真实 oracle 调用。
- **P2-MA1-020（live 确认）**：`nop-ai-dsl-orm/.../GptOrmModelParser.java:49` `entityModel.setClassName("app.demo." + StringHelper.simpleClassName(name))` 硬编码包名 `app.demo.`。
- **P2-MA1-021/022（live 确认）**：`nop-ai-maven`/`nop-ai-codegen` pom 死依赖/重型依赖（见 MA1.4 报告明细）。**注意**：`nop-ai-maven/pom.xml:37-42` 声明 `nop-api-core`/`nop-core`（main 代码零 import，编译期死依赖成立），但 Phase 4 的 NopException 修复依赖 `nop-api-core`——两个 phase 存在顺序冲突，需预先裁定（见 Phase 1 item）。
- **P2-MA3-2（live 确认）**：`nop-ai-maven` 3 文件 10+ IAE + 1 RTE（`DeltaVirtualFileSystem.java:46-56,73,146,173,199-200` + `:68` RTE、`DeltaWorkspaceReader.java:44,47,158,161`、`ArtifactInfo.java:30,33,36`），未用 NopException/错误码。
- **P2-MA3-4（live 确认）**：`nop-ai-skills/nop-ai-code-analyzer/.../stats/FileLanguageStats.java:313` `throw new RuntimeException("Error walking file tree: ...")` 包装 IOException 未用 NopException。
- **P2-MA3-3（live 部分确认）**：`VfsMavenCli` 已由 MA4.2-11 修复（`printCommand` → SLF4J `LOG.info`，live `VfsMavenCli.java:115`），本计划仅复验，不重复修复。
- **P2-D06-019（live 确认）**：dict 双源——ORM `<dicts>`（`_app.orm.xml` 生成物）+ 独立 `nop-ai-meta/src/main/resources/_vfs/dict/ai/*.dict.yaml`（24 个文件：15 个 active + **9 个废弃 snake_case 文件**：`config_type`、`file_format`、`message_type`、`model_provider`、`module_type`、`project_language`、`requirement_type`、`rule_type`、`status_type`）。**注意**：arm-index 声称 P1-MA2-018（9 个 snake_case dict 清理）MR1 已 `fixed`，但 live 文件仍全部存在——与 P1-MA2-005 同类的 overclaim；9 个 snake_case 文件删除为**无条件 Fix**（不放入 Decision），仅 15 个 active dict 的双源问题进入 Decision。
- **P2-D06-020（live 确认）**：`nop-ai-web/src/main/resources/_vfs/i18n/` 仅 `en/`，无 `zh-CN/`（对比 `nop-ai-meta/src/main/resources/_vfs/i18n/` 有 en + zh-CN）。
- **P2-MA1-034/035/036/037（live 部分确认）**：错误码前缀不一致（`AiCoreErrors` vs 模块包名）、`NopAiChatResponse` 冗余 `ai_` 列前缀、状态模型 enum vs dict 不匹配——需逐项复核后裁定。
- 全量基线：`./mvnw test -pl nop-ai -am -T 1C` 绿（3444 tests / 0 failures，2026-07-31 记录）。

## Goals

- 跨模块契约收敛：移除 `nop-ai-core` 对 `nop-dao` 的死依赖；废弃 API 使用面（GraphQLToolProvider）迁移或显式裁定；core 内部类泄漏（IFileOperator/AiXDefHelper）裁定归属。
- IoC 与结构卫生：beans.xml 命名空间拼写修复；`SearchEngineExecutor` 死代码移除或重新接线裁定；`AskOracleExecutor` 存根行为裁定（快速失败或真实实现）。
- 错误处理规范：`nop-ai-maven`/code-analyzer 的 IAE/RTE 收敛为模块异常/NopException（按模块内部约定，英文消息）。
- 配置与 i18n：dsl-orm 硬编码包名修复；dict 双源一致性校验或单源裁定；nop-ai-web zh-CN i18n 补齐。
- 命名一致性 P2 项逐项裁定落盘。

## Non-Goals

- 不迁移 legacy `IAiChatService`/`IAiChatSession`/`AiChatOptions` 全量（P2-MA3-04 仅裁定 deprecation 语义，不做 API 面重写——API 面迁移属未来 major 版本）。
- 不做 `nop-ai-rag` 空模块填充（P3-MA3-003）。
- 不重排全量 import（MA4.2-14 已裁定 optimization candidate）。
- 不拆分超大文件（MA4.2-05 已裁定 optimization candidate）。

## Scope

### In Scope

- `nop-ai-core/pom.xml`：移除 `nop-dao` 死依赖。
- `nop-ai-tools`：`GraphQLToolProvider`/`GraphQLToolSetFactoryBean` 废弃 API 迁移或裁定；`ai-tools-defaults.beans.xml` 命名空间修复。
- `nop-ai-toolkit`：`SearchEngineExecutor` 死代码裁定；`AskOracleExecutor` 存根裁定。
- `nop-ai-coder`：`AiXdefDocumentConverter` 内部包依赖裁定。
- `nop-ai-dsl-orm`：`GptOrmModelParser` 硬编码包名修复。
- `nop-ai-maven`/`nop-ai-codegen`：pom 死依赖清理；IAE → 规范异常。
- `nop-ai-skills/nop-ai-code-analyzer`：`FileLanguageStats` RTE → 规范异常。
- `nop-ai-meta`/`nop-ai-web`：dict 双源一致性校验；zh-CN i18n。
- 命名 P2 项（P2-MA1-034/035/036/037）：复核 + 裁定。

### Out Of Scope

- legacy API 全量迁移（P2-MA3-03 之外的部分、P2-MA3-04 API 面）。
- P3 项（P2-MA1-023~030、P2-MA3-1 等）。
- 超大文件拆分（MA4.2-05）。
- 全量 import 重排（MA4.2-14）。

## Execution Plan

### Phase 1 — 依赖清理（P2-MA3-001 + P2-MA1-021/022）

Status: planned
Targets: `nop-ai/nop-ai-core/pom.xml`、`nop-ai/nop-ai-coder/pom.xml`、`nop-ai/nop-ai-maven/pom.xml`、`nop-ai/nop-ai-codegen/pom.xml`

- Item Types: `Fix | Decision | Proof`

- [ ] （Proof）复核 `nop-ai-core` 零 `io.nop.dao` import（grep 确认后）→ 移除 `nop-dao` 依赖。
- [ ] （Fix）**连带修复**：`nop-ai-coder/.../AiOrmModelNormalizer.java:7` 使用 `io.nop.dao.dialect.SQLDataType`——给 `nop-ai-coder/pom.xml` 补 `nop-dao` 直接依赖（或评估移除该 import 改用替代类型），保证移除 nop-ai-core 的传递依赖后编译不破。
- [ ] （Decision）**预先裁定 nop-ai-maven 的 `nop-api-core`/`nop-core` 依赖去留**：Phase 4 需要 `NopException`（nop-api-core 提供）。两选项：(a) 保留 `nop-api-core`（注释说明供 Phase 4 异常类型使用，`nop-core` 仍移除）；(b) 全部移除，Phase 4 改用模块级异常类（extends RuntimeException，记录与 audit 建议的偏离）。裁定落盘后 Phase 4 按裁定执行。
- [ ] （Proof）`nop-ai-codegen` pom 重型依赖逐项确认：**注意 postcompile 运行时需求**——`postcompile/gen-orm.xgen` 渲染 `nop-ai.orm.xml` 需 ORM 模板运行时；正确修法是 `nop-orm` → `nop-orm-model` 替换（OrmModelLoader + `orm.register-model.xml` 在 nop-orm-model，已实测）而非直接删除。**另注意传递链**：`nop-codegen`（`XCodeGenerator` 所在）当前经 `nop-graphql-core` 传递提供（nop-graphql-core/pom.xml:47），清理重型依赖后需**显式补 `nop-codegen` 直接依赖**。**退出判据不能只看"无 NoClassDefFoundError"**——`XCodeGenerator.renderModel` 在模型 loader 缺失时只 LOG.warn 并继续（`CODE_GEN_MODEL` 为 null），会静默空生成；必须以可观察产物验证：postcompile 重跑后 `nop-ai-dao/.../_app.orm.xml` 与 `_gen/*.java` 存在且非空（或 `git diff` 无意外变更）。
- [ ] 全量 build + test 验证（**验证命令必须含 nop-ai-coder 与 nop-ai-codegen**，因为它们是连带面）。

Exit Criteria:

- [ ] `nop-ai-core/pom.xml` 无 `nop-dao`（grep pom 验证）
- [ ] `nop-ai-coder` 编译通过（nop-dao 直接依赖已补或 import 已替换）
- [ ] `nop-ai-maven` 依赖裁定落盘（保留 nop-api-core 或改模块异常，二选一有记录）
- [ ] `nop-ai-codegen` 清理后 postcompile 实际执行成功且产物可观察（`nop-ai-dao/.../_app.orm.xml` 与 `_gen/*.java` 存在且非空，或 git diff 无意外变更——不依赖"NoClassDefFoundError"这类可能被 warn-and-continue 吞掉的判据）
- [ ] `./mvnw test -pl nop-ai-core,nop-ai-coder,nop-ai-maven,nop-ai-codegen -am` 绿
- [ ] `docs-for-ai/01-repo-map/module-groups.md` 依赖矩阵同步（如受影响）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — 废弃 API 使用面裁定与迁移（P2-MA3-03/04/05/06/08）

Status: planned
Targets: `nop-ai/nop-ai-tools/`、`nop-ai/nop-ai-coder/`、`nop-ai/nop-ai-api/src/main/java/io/nop/ai/api/chat/ChatOptions.java`、`nop-ai/nop-ai-core/src/main/java/io/nop/ai/core/api/chat/`

- Item Types: `Fix | Decision | Proof`

- [ ] （Decision）P2-MA3-04：裁定 `IAiChatService`/`IAiChatSession` deprecation 语义——保留 `@Deprecated(forRemoval=true)` 并明确迁移路径（MR4 已裁定 legacy 由废弃路径覆盖），或在 javadoc 记录"core 内部主干，deprecated 标注误导"的修正文案。
- [ ] （Fix）P2-MA3-03：`GraphQLToolProvider`/`GraphQLToolSetFactoryBean` 迁移到 `nop-ai-api`/`nop-ai-toolkit` 新接口（`IToolDefinition`/`IToolExecutor`/`IToolManager`），或裁定保留 + 记录（需给出理由：如新接口无法表达 GraphQL tool 场景）。
- [ ] （Decision）P2-MA3-05：裁定 `IFileOperator`/`LocalFileOperator` 归属——提升到 `nop-ai-api` 公开契约，或在 toolkit 定义接口并迁移 2 个使用方。
- [ ] （Decision）P2-MA3-06：裁定 `ChatOptions` vs `AiChatOptions`——合并方向（api 为准，core 废弃面收敛）或记录字段差异为历史残留。
- [ ] （Decision）P2-MA3-08：裁定 `AiXdefDocumentConverter` 的 `AiXDefHelper` 依赖——在 core 内公开该工具类（从内部包提升）或 coder 内自实现。
- [ ] 按裁定落地最小变更 + 测试（迁移路径必须有编译/测试证据）。

Exit Criteria:

- [ ] 5 个 P2-MA3 契约项逐项有裁定记录（`arm-index.md` 或 owner doc），live 状态与裁定一致
- [ ] 凡裁定为"迁移"的项：编译通过 + 对应测试绿（**接线验证**：新接口被实际调用）
- [ ] 凡裁定为"保留 + 记录"的项：javadoc/设计文档说明理由，无"已废弃却无说明"残留
- [ ] **无静默跳过**：无"迁移了一半"的中间态（要么完成迁移，要么完整记录）
- [ ] 相关 `docs-for-ai/`（如 nop-ai 模块文档）已同步
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — IoC 与结构卫生（P2-MA1-006/029 + P2-MA1-008 + P2-MA1-011）

Status: planned
Targets: `nop-ai/nop-ai-tools/src/main/resources/_vfs/nop/ai/beans/ai-tools-defaults.beans.xml`、`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/SearchEngineExecutor.java`、`nop-ai/nop-ai-toolkit/src/main/java/io/nop/ai/toolkit/tools/AskOracleExecutor.java`、`nop-ai/nop-ai-toolkit/src/main/resources/_vfs/nop/ai/beans/ai-tools-defaults.beans.xml`

- Item Types: `Fix | Decision | Proof`

- [ ] （Fix）`ai-tools-defaults.beans.xml` 命名空间 `xdsl.xef` → `xdsl.xdef`（两个 beans.xml 均检查：nop-ai-tools 与 nop-ai-toolkit）。
- [ ] （Decision）`SearchEngineExecutor`：裁定删除（死代码 + 注释 bean 清理）或重新接线（补 bean 定义 + 实现校验）。**若选删除，连带面**：`SearchEngineExecutorTest.java`（含 MockSearchEngine，删类后编译失败）、`search-engine.tool.xml`、`nop-search-api` 依赖——需一并清理。
- [ ] （Fix）`AskOracleExecutor` 存根行为（**两个分支都要处理，live 现状**：无 `ORACLE_ENDPOINT` 时 :44-47 静默返回第一个 option；**有 endpoint 时 :49 也静默返回第一个 option，endpoint 从未被使用**；`timeoutMs` 解析后未用）：裁定快速失败（无 endpoint/未实现时返回错误结果，符合 Anti-Silent-NoOp 规则）或实现真实 oracle 调用；**快速失败为默认倾向**。
- [ ] （Fix）**改写 `AskOracleExecutorTest` 现有成功断言**（`testExecuteWithQuestionAndOptions` 断言静默成功行为）为快速失败断言；新增有 endpoint 分支的测试。
- [ ] 全量 build + test 验证。

Exit Criteria:

- [ ] 两个 beans.xml 命名空间正确（xdef），加载无校验告警
- [ ] `SearchEngineExecutor` 裁定落盘：删除（文件 + 注释 bean + 测试 + tool.xml + 依赖均无残留）或接线（bean 定义存在 + 测试）
- [ ] `AskOracleExecutor` 两个分支行为明确：无 endpoint 快速失败（测试断言）、有 endpoint 不再静默返回第一个 option（实现真实调用或同样快速失败）；**不存在静默 stub 成功**
- [ ] `AskOracleExecutorTest` 已改写（旧"成功"断言不再存在）
- [ ] **无静默跳过**：stub 语义不再以"成功返回第一个 option"形式存在
- [ ] `No owner-doc update required` 或 design 文档同步（如工具清单变化）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 错误处理与硬编码修复（P2-MA1-020 + P2-MA3-2 + P2-MA3-4）

Status: planned
Targets: `nop-ai/nop-ai-dsl-orm/src/main/java/io/nop/ai/dsl/orm/GptOrmModelParser.java`、`nop-ai/nop-ai-maven/src/main/java/io/nop/ai/maven/vfs/DeltaVirtualFileSystem.java`、`DeltaWorkspaceReader.java`、`ArtifactInfo.java`、`nop-ai/nop-ai-skills/nop-ai-code-analyzer/src/main/java/io/nop/ai/code_analyzer/stats/FileLanguageStats.java`

- Item Types: `Fix | Decision | Proof`

- [ ] （Decision）`GptOrmModelParser` 包名来源裁定：配置注入（默认值可配置）或按 ORM 模型命名推导；修复 `"app.demo." +` 硬编码（`GptOrmModelParser.java:49`）。
- [ ] （Fix）`nop-ai-maven` 3 文件 IAE → 按 Phase 1 依赖裁定选择异常类型：若保留 `nop-api-core` 用 `NopException`；若移除则用模块级异常类（英文消息；nop-ai-maven 无独立 ErrorCode 类则按模块内部约定，见 AGENTS.md 错误处理规则）。覆盖 `DeltaVirtualFileSystem.java:46-56,73,146,173,199-200,:68 RTE`、`DeltaWorkspaceReader.java:44,47,158,161`、`ArtifactInfo.java:30,33,36` 全部实例。
- [ ] （Fix）`FileLanguageStats.java:313` RTE → 规范异常（保留 cause）。
- [ ] （Proof）复验 P2-MA3-3：grep `System.out` 于 `nop-ai-maven/src/main/java/` 应 0 命中（MA4.2-11 已修，此处仅留证据）。
- [ ] （Fix）测试：异常类型变更后行为断言（构造期参数校验抛错 + 原因链保留）。
- [ ] 全量 build + test 验证。

Exit Criteria:

- [ ] `GptOrmModelParser` 无硬编码包名（代码 + 测试验证）
- [ ] `nop-ai-maven`/code-analyzer 无裸 `IllegalArgumentException`/`RuntimeException`（grep 验证；允许参数校验用 `NopException` + ErrorCode 或模块异常）
- [ ] P2-MA3-3 复验证据：`System.out` 在 nop-ai-maven main 0 命中（记录于本 Phase）
- [ ] **无静默跳过**：异常仍抛（快速失败），仅类型/规范变化
- [ ] `No owner-doc update required`（内部错误处理规范，无公开 API 变化）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — dict 一致性 + zh-CN i18n + 命名裁定（P2-D06-019/020 + P2-MA1-034/035/036/037）

Status: planned
Targets: `nop-ai/nop-ai-meta/src/main/resources/_vfs/dict/ai/`、`nop-ai/model/nop-ai.orm.xml`、`nop-ai/nop-ai-web/src/main/resources/_vfs/i18n/`、命名相关文件

- Item Types: `Fix | Decision | Proof`

- [ ] （Fix）**删除 9 个废弃 snake_case dict 文件**（`config_type`、`file_format`、`message_type`、`model_provider`、`module_type`、`project_language`、`requirement_type`、`rule_type`、`status_type` `.dict.yaml`）——无条件 Fix（P1-MA2-018 声称 fixed 但 live 仍在，属 overclaim 纠正；删除前 grep 确认无引用），并在 arm-index 补记 P1-MA2-018 实际落地。
- [ ] （Decision）15 个 active dict 的双源问题（ORM `<dicts>` vs 独立 `.dict.yaml`）裁定：单源化（ORM 为准）或加 build-time 一致性校验脚本（复用 `ai-dev/tools/` 模式）。
- [ ] （Fix）按裁定落地：一致性校验脚本（运行 0 错误）或文件收敛。
- [ ] （Fix）`nop-ai-web` zh-CN i18n 补齐：**镜像 `en/` 的 extends 链**（`en/` 含 `_nop-ai-web.i18n.yaml` base + `nop-ai-web.i18n.yaml` 外层，外层 `x:extends` 内层）——zh-CN 需同时建 `zh-CN/_nop-ai-web.i18n.yaml` 与 `zh-CN/nop-ai-web.i18n.yaml`（外层 extends 内层），翻译键对齐 en 文件。
- [ ] （Decision）P2-MA1-034/035/036/037 逐项复核 live 状态并裁定（错误码前缀、`ai_` 列前缀、enum vs dict、类名前缀）：可低成本修复则修复，否则记录裁定理由（落盘于 arm-index 新 §P2 追踪）。
- [ ] 全量 build + test + i18n 加载验证。

Exit Criteria:

- [ ] 9 个 snake_case dict 文件已删除（grep/ls 验证）且 arm-index P1-MA2-018 补记实际落地
- [ ] 15 个 active dict 一致性：单源或校验脚本存在（运行 0 错误）
- [ ] `nop-ai-web` 有 `zh-CN` i18n 文件且加载验证通过
- [ ] 命名 4 项逐项有裁定记录（修复证据或理由，落盘于 arm-index §P2 追踪）
- [ ] **无静默跳过**：无"清理声明但文件仍在"的残留状态
- [ ] `No owner-doc update required`（i18n/dict 为资源补充）或 `docs-for-ai/` 同步（如有约定变化）
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> **关闭条件**：只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 所有 in-scope 结构 P2 finding（P2-MA3-001/03/04/05/06/08、P2-MA1-006/008/011/020/021/022/034/035/036/037、P2-MA2-029、P2-MA3-2/3 复验/4、P2-D06-019/020、P1-MA2-018 overclaim 纠正）已修复或裁定落盘
- [ ] 无 in-scope live defect 被静默降级到 deferred / follow-up（snake_case dict 为无条件 Fix）
- [ ] 关键行为（迁移后接线、AskOracle 快速失败、异常类型、dict 校验）均有 focused 测试
- [ ] 不存在空方法体/静默跳过/no-op 作为正常实现（Anti-Hollow）
- [ ] 受影响 owner docs（`docs-for-ai/01-repo-map/module-groups.md`、`docs-for-ai/03-modules/nop-ai.md`、`arm-index.md` 新 §P2 追踪）已同步
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `./mvnw compile -pl nop-ai -am`
- [ ] `./mvnw test -pl nop-ai -am -T 1C`
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-ai --severity high` 退出码 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出码 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/2026-07-31-1834-3-arm-p2-contract-dependency-cleanup.md --strict` 退出码 0

## Deferred But Adjudicated

### P2-MA3-04 legacy API 全量迁移（IAiChatService 等）

- Classification: `watch-only residual`
- Why Not Blocking Closure: MR4 已裁定 legacy `IAiChatSession` caller-identity 绑定由 `@Deprecated(forRemoval=true)` 废弃路径覆盖；全量 API 面迁移属未来 major 版本工作，本计划只收敛 deprecation 语义说明。
- Successor Required: `no`

### P2-MA1-004/005 超大文件拆分（CodeFileInfo/FileLanguageStats 行数）

- Classification: `optimization candidate`
- Why Not Blocking Closure: 与 MA4.2-05 同类裁定（SRP 重构，纯结构性变更，回归风险高）；audit 自标 size-isn't-a-defect。
- Successor Required: `no`

### P2-MA1-007/009/012（SkillExecutor 回退 / GraphQL 传递依赖 / 重复文件系统抽象）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 本批次按严重度只承接契约/依赖清理主体；这三项与既有架构决策（toolkit 分层、nop-ai-api 边界）耦合较深，需单独设计裁定，已登记 Non-Blocking Follow-ups 进入后续批次。
- Successor Required: `no`

## Non-Blocking Follow-ups

- P2-MA1-007（SkillExecutor 硬编码回退 + 空 catch，live 仍存在 :119-128）、P2-MA1-009（GraphQLToolProvider 硬传递依赖 nop-graphql-core）、P2-MA1-012（IToolFileSystem vs IFileOperator 重复抽象）：结构类 P2，本批次未入 scope，按严重度排序进入后续批次。
- P2-MA1-013/014/015/016 等 P3 结构项：低优先，后续批次。
- P3-MA3-003（nop-ai-rag 空模块）：P3，观察项。
- MA4.2-05/MA4.2-14 既有 deferred（optimization candidate）：不因本批次重开。

## Closure

Status Note: （完成时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

-

## Optional Sections

## Risks And Rollback

- 依赖移除：删除前逐项 grep 引用证据，删除后模块编译/测试验证（**验证命令必须含连带模块 nop-ai-coder/nop-ai-codegen**）；回滚单 commit。
- **nop-ai-maven 依赖与异常类型冲突**：Phase 1 预先裁定（保留 nop-api-core 或改模块异常），Phase 4 严格按裁定执行，不现场裁决。
- 废弃 API 迁移（P2-MA3-03）：若新接口无法表达 GraphQL tool 场景则保留 + 记录，不做半迁移。
- `AskOracleExecutor` 快速失败：改变工具行为（错误结果替代静默成功），对调用方透明（errorResult 已有契约）；回滚单 commit。
- dict 单源化：若选单源需评估 codegen 再生成影响；校验脚本方案风险最低（默认倾向）。snake_case 删除前 grep 确认无引用。
- i18n zh-CN：纯资源补充，无行为影响。
