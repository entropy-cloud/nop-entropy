# 12 WI12 RenameInput GraphQL 接线——Refactor__previewRename/applyRename + RPC e2e

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M2 WI12 原文 + Cross-Cutting）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §三（四 action 契约与 schema 非破坏增长裁定）；`ai-dev/plans/nop-refactor/06-wi6-graphql-actions.md`（rewrite 面契约先例）；10/11（rename 两档语义与 RenameRequest 前身形态）
> Related: `ai-dev/plans/nop-refactor/06-wi6-graphql-actions.md`、10、11
> Review: R1(2026-09-26, fresh session agent_083cc499): REVISE — 1B+5M+6m all fixed；R2 delta 12/12 PASS 放行（N1 归一化用例入矩阵/N2 design 增注 scope 句——已落档）。

## Purpose

执行 roadmap WI12（Item Type: Fix）：rename 的 GraphQL 契约面——`Refactor__previewRename` / `Refactor__applyRename` 两 action 落地 nop-refactor-graphql（schema 增字段非破坏增长，plan 06 裁定 a）；RenameInput 目标定位（FQN 或 文件+字节偏移，无光标概念）；symbolIntact 语义精确呈现（非 null ⟺ rename RESOLVED 且无回滚；refusal 路径与 rewrite 面恒 null）；GraphQLEngine RPC 端到端真调证明。面只做输入准备与委托——rename 执行走 WI9 框架唯一路径（RenameOperation 经 runner），无第二执行路径。

## Current Baseline

（live 已核对，2026-09-26）

- **WI11 已 completed**：RenameOperation 两档真实可用（RenameRequest 7 参 + files 为 SourceFile(path, content) 集合）；RenameResolution.fileRewrites 跨文件载体；symbolIntact 断言经 assemble 单点组装（**非 null ⟺ RESOLVED 且无回滚**——refusal/rollback/rewrite 面均为 null，结构性行为）；FQN 与 offset 双定位形态；RenameOperation.check 校验 path ∈ files（字符串全等）。
- **rewrite 面先例**（WI6/WI8）：`NopRefactorBizModel` @BizModel("Refactor")——rewrite() 私有链内联"输入校验 → ruleset 加载 → collectTargets（path grammar 三分支 + TargetScanner 扩展名表 java/ts/tsx/xml/xbiz → TargetFile(path, languageId) 真实路径串）→ cap 两键 → 逐文件读取 → RewriteRequest → runner"；`"(skipped targets)"` OUT_OF_SCOPE NonApply 有 RewriteRequest.collectionNonApplies 槽；RPC e2e harness 内嵌于 TestNopRefactorGraphQL（BizModelSchemaLoader 双名 def 模式："RewriteInput" + "g_io_nop_refactor_graphql_RewriteInput"）。
- **IoC 先例**：@Inject 字段包级私有/protected（无 private）；跨模块 bean 以 class 串定义（`<bean class="..." ioc:default="true"/>`）；RenameRequest.resolver null → requireNonNull 抛 NPE（非结构化）。
- **deps**：WI5/WI9/WI11 全部 completed，可开工。

## 执行面裁定记录（R1 审查钉死，逐条编号供 Phase 引用）

1. **rename 符号域 = 模块的 java 文件集（R1 Blocker 裁定）**：`collectTargets` 参数化语言过滤（单一实现：rewrite 调用传全扩展名表、rename 传 java-only）——非 java 文件是**定义性排除**（不属于 rename 搜索域，WI2"适配器拥有语言语义"的推论），不产 NonApply（与 rewrite 面"目标不可改写 → OUT_OF_SCOPE"的呈现差异由 design 01 增注 owns）；offset 目标文件非 java → face 预检结构化 `NopRefactorException`（"…is not a java file; v1 rename is java-only"）。零 core 改动。
2. **path 归一化等价（R1 Major-1 裁定）**：face 对 `RenameInput.path` 施加与目标收集同一条 grammar（containment + 存在性 + `toRealPath`），`SymbolTarget` 携带归一化后字符串——与 `SourceFile.path`（收集产出同串）全等可比较；归一化后不命中收集集 → 结构化错误。服务级钉"raw 相对路径入参 → rename 成功"用例。
3. **适配器注入四点（R1 Major-2 裁定）**：(a) 字段类型 = **SPI 接口** `SymbolResolverAdapter`（非具体类），pom 依赖 `<scope>runtime</scope>`（test classpath 可见，e2e 可用）；(b) bean 落 graphql 模块 main beans.xml（class 串引用 JavaSymbolResolverAdapter，CoreInitialization 加载 → BeanContainer 自动装配）；(c) face 对 `resolver == null` 预检抛结构化 `NopRefactorException`（消息点名 bean/依赖），**禁止 fallback `new JavaSymbolResolverAdapter()`**（假装配 = Anti-Hollow 反例）；(d) `nop-lint-java` 经 nop-refactor-java 传递可达，graphql 不直接声明。装配生效证明 = rename RPC 真调返回 RESOLVED 载荷（注入未生效则预检必然结构化报错——正反两面断言）。
4. **symbolIntact 精确语义（R1 Major-3 裁定）**：非 null ⟺ rename RESOLVED 且无回滚；refusal 路径（CONFLICT/OUT_OF_SCOPE/UNRESOLVED）与 rewrite 面恒 null。e2e CONFLICT 用例显式断言 symbolIntact=null + edits 空 + nonApplied[0].reason=CONFLICT。
5. **两层测试呈现（R1 Major-4 裁定）**：**check/构造层违规 → 结构化响应错误（isOk=false）**——恰一校验（both/neither）、newName 非法、paths 空、适配器缺失、非 java offset 目标、path∉files；**plan 层三拒绝 → nonApplied 载荷**——CONFLICT/OUT_OF_SCOPE/UNRESOLVED。服务级边界矩阵（TestNopRefactorBizModel 增补）+ RPC e2e 各落断言；cap 两键 rename 语义（max-target-files=搜索域规模门、max-source-size=文件读取门）；paths 重复条目按 real path 去重（服务级用例）。
6. **共享执行链抽取纪律（R1 Major-5 裁定）**：rewrite() 的收集/读/cap 段抽成共享 helper = 纯等价重构——rewrite 两 action 行为零变化、既有测试零修改全绿；cap/grammar 错误消息措辞中性化（去 "rewrite" 前缀——既有 e2e 断言配置键名非整句，安全）；previewRename/applyRename 共用同一 `rename(input, dryRun)` 私有函数（无状态重执行，与 rewrite 同构）。
7. **RenameInput 形态（R1 m1/m2 裁定）**：沿 RewriteInput bean 形态 Decision（非 record）；schema defs 双名模式增补（`RenameInput` + `g_io_nop_refactor_graphql_RenameInput`，byteOffset 声明 Int）；**Int→Long setter 绑定是 harness 未验证路径**——执行时先最小探针验证、以 live 为准回写。
8. **CONFLICT e2e 诱导形态（R1 m6 裁定）**：用 WI11 裁定 4a 最易诱导形态——**声明类内同名重载方法**（避免误搭成 UNRESOLVED/OUT_OF_SCOPE 场景）。
9. **BizModel javadoc 过时句更新（R1 m4）**：随本 plan 更新（javadoc 非行为，不破"rewrite 面零变化"红线）。

## Goals

- **RenameInput 契约**：`RenameInput { paths, fqn?, path?, byteOffset?, newName }` 恰一定位 + 面级校验（裁定 2/5）。
- **两 action + 共享链**：previewRename/applyRename 经裁定 6 的共享收集链与 `rename(input, dryRun)` → RenameOperation 经 runner；拒绝 → nonApplied 载荷。
- **适配器注入**（裁定 3）：SPI 接口字段 + runtime 依赖 + main beans.xml + 预检结构化错误。
- **symbolIntact 精确呈现**（裁定 4）+ 两层测试矩阵（裁定 5）。

## Non-Goals

- 不做 rename 面 cap 新键；不做 glob；不做 CLI rename。
- 不改 nop-lint / nop-treesitter / nop-code 任何行为；不改 rewrite 面既有 action 语义（共享链抽取为纯等价重构）。
- 不做 core 改动（RenameRequest 无 collectionNonApplies 槽——java-only 域为定义性排除，无需 NonApply 通道，裁定 1）。

## Scope

### In Scope

- nop-refactor-graphql：RenameInput bean + previewRename/applyRename + collectTargets 语言过滤参数化（B1 修法 a）+ 共享链抽取（裁定 6）+ 适配器注入（裁定 3）+ 服务级边界矩阵测试 + RPC e2e（schema defs 增补）。
- nop-refactor-graphql pom：additive nop-refactor-java runtime 依赖。
- design 01 增注（裁定 1/3/4/5/7 语义全集）；ai-dev/logs 条目。

### Out Of Scope

- WI13；core 改动；nop-lint/nop-treesitter/nop-code 行为面；rewrite 面行为变更。

## Execution Plan

### Phase 1 - 契约面 + 共享链抽取 + 两层测试（Fix）

Status: planned
Targets: `nop-refactor/nop-refactor-graphql/src/main/`（java + beans.xml + pom）、`src/test/java/`、design 01

- Item Types: `Fix`

- [ ] collectTargets 语言过滤参数化（裁定 1：rewrite 全表 / rename java-only）+ 共享收集/读/cap 链抽取（裁定 6 纯等价重构——rewrite 既有测试零修改全绿 + 错误消息中性化）
- [ ] RenameInput bean（裁定 7：恰一定位校验 + newName 标识符）+ previewRename/applyRename → 共享 rename(input, dryRun) → RenameRequest 组装（path 归一化裁定 2 + java-only 预检 + resolver null 预检裁定 3c + 去重 m2）+ RenameOperation 经 runner
- [ ] 适配器注入（裁定 3）：pom runtime 依赖 + main beans.xml class 串装配 + SPI 接口 @Inject 非 private 字段
- [ ] 服务级边界矩阵（裁定 5）：恰一（both/neither）/newName 非法/paths 空/非 java offset 目标/path∉files/适配器缺失 → 结构化错误；CONFLICT（重载方法诱导，裁定 8）→ nonApplied 载荷；cap 两键语义；paths 去重；确定性（同输入同载荷）；归一化成功（raw 相对路径入参 → rename 成功，裁定 2）
- [ ] RPC e2e：BizModelSchemaLoader 双名 defs 增补 RenameInput（byteOffset Int——Int→Long 绑定先探针验证裁定 7）；previewRename（applied=false + symbolIntact=true + 文件不变）+ applyRename（跨文件落盘 + applied=true + symbolIntact=true）+ CONFLICT 载荷（裁定 8 诱导 + symbolIntact=null 断言，裁定 4）+ FQN 定位形态
- [ ] BizModel javadoc 过时句更新（裁定 9）
- [ ] design 01 增注（裁定 1/3/4/5/7 + m3 cap 语义 + m5 paths 语义差 + RenameInput 无 scope 字段——v1 恒 MODULE，design §三注释'符号域范围'不入 v1 input）
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw test -pl nop-refactor/nop-refactor-graphql -am` 全绿
- [ ] **端到端验证**（Minimum Rules #22）：RenameInput 经 GraphQLEngine RPC 真调 → RenameOperation 四段 → 跨文件落盘 + 载荷（含 symbolIntact）完整贯通
- [ ] **接线验证**（Minimum Rules #23）：apply/verify 调用点 grep 仍单点；**装配生效证明 = rename RPC 真调返回 RESOLVED 载荷**（注入未生效则预检结构化报错——正反两面断言，裁定 3）
- [ ] **无静默跳过**（Minimum Rules #24）：check 层违规 → isOk=false 结构化错误；plan 层拒绝 → nonApplied 载荷；适配器缺失预检点名 bean
- [ ] **新功能测试清单**（Minimum Rules #25）：服务级矩阵 + e2e 四断言逐项列出并落为测试
- [ ] 零行为红线自查（scoped git diff）：nop-lint / nop-treesitter / nop-code 零修改；rewrite 面既有测试零修改全绿（共享链抽取纯等价）
- [ ] design 01 增注已落档且与 landed 实现互洽
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 项完成，无残留未勾选 checklist
- [ ] rename GraphQL 面成立：previewRename/applyRename 经 GraphQLEngine RPC 真调走通（跨文件落盘 + symbolIntact 精确呈现 + 拒绝路径结构化）+ 服务级边界矩阵全绿
- [ ] 零行为红线（scoped diff）：nop-lint / nop-treesitter / nop-code 零修改；rewrite 面零变化（共享链抽取纯等价实证）
- [ ] owner docs 已同步：design 01 增注
- [ ] **Anti-Hollow Check**：closure audit 已验证 RPC 入口到落盘与载荷运行时连通（beans 装配真实生效——装配证明正反两面断言）
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-graphql --severity high` 退出 0
- [ ] `./mvnw test -pl nop-refactor/nop-refactor-core,nop-refactor/nop-refactor-java,nop-refactor/nop-refactor-graphql -am` 全绿
- [ ] 代码规范：`check-import-order.mjs` 本 plan 新增/变更文件零违规（范围口径）
- [ ] vision 原则 1–9 回扣核对（closure audit 执行）：原则 1（四 action 齐全）、原则 2（无状态重执行）、原则 3（symbolIntact 精确呈现）为重点
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（范围口径）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/12-wi12-rename-graphql.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- rename 面 glob 目标展开：rewrite 面同型 Non-Goal——Why Not Blocking Closure：WI6 裁定不入 v1。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）

## Review Record

- **R1(2026-09-26, fresh session agent_083cc499): REVISE** — 1 Blocker + 5 Major + 6 Minor, all fixed in text:
  - Blocker（→裁定 1）：rename 符号域 = 模块 java 文件集（collectTargets 语言过滤参数化；非 java = 定义性排除不产 NonApply；offset 非 java 预检结构化错误）——消除"复用收集撞墙 + NonApply 无通道"双重缺口
  - Major-1（→裁定 2）：path 归一化等价（SymbolTarget 携带 toRealPath 串与 SourceFile.path 全等可比）+ raw 相对路径服务级用例
  - Major-2（→裁定 3）：适配器注入四点闭合（SPI 接口字段/runtime scope/main beans.xml/预检结构化错误禁 fallback）+ 装配生效正反两面证明
  - Major-3（→裁定 4）：symbolIntact 收窄为"RESOLVED 且无回滚时非 null"；CONFLICT e2e 显式断言 null
  - Major-4（→裁定 5）：两层测试呈现总原则 + 服务级边界矩阵入 plan
  - Major-5（→裁定 6）：共享链抽取纪律（纯等价重构/零修改全绿/消息中性化/rename(input,dryRun) 同构）
  - Minor m1–m6：bean 形态与双名 defs 与 Int→Long 探针、paths 去重、cap 语义增注、javadoc 过时句、design 增注清单、CONFLICT 诱导形态（重载方法）——全部落档
