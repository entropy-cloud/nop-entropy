# 10 WI10 rename 阶梯第一档——局部变量/参数单文件 rename + symbolIntact 前身形态

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M2 WI10 原文 + Cross-Cutting）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §一.3（四段契约）/§四（symbolIntact 载荷字段）；`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md` §四（单模块符号域裁定）；`ai-dev/design/nop-refactor/00-vision.md` §三 原则 3/6
> Related: `ai-dev/plans/nop-refactor/09-wi9-operation-framework.md`（框架 + SPI + RenameOperation 骨架，completed）；05（RefactorResult 载荷，completed）
> Review: R1(2026-09-26, fresh session agent_44f52c2f): REVISE — 1B+5M+5m all fixed（见文末 Review Record）

## Purpose

执行 roadmap WI10（Item Type: Fix）：rename 阶梯第一档落地——**局部变量 + 参数、单文件内**的语义 rename：RenameOperation.plan 从 fail-closed 骨架转为真实实现（解析、引用收集、名字冲突 fail-closed 进 nonApplied、引用改写编辑计划），经 WI9 框架唯一路径（check → plan → apply → verify）执行；verify 含简化引用计数断言（rename 前后目标符号引用数一致）——symbolIntact 的 WI10 前身形态，经 WI5 assemble 单点组装进载荷。不做字段/方法/类型（WI11）、不做跨文件/import/FQN（WI11）、不做 GraphQL 面（WI12）。

## Current Baseline

（live 已核对，2026-09-26；行号为核对时快照，执行时以 live 为准）

- **WI9 已 completed**（roadmap 已勾选，提交 945592b91c）：core `io.nop.refactor.core.operation`（RefactorOperation SPI + RefactorOperationRunner 框架单点 + RewriteOperation）与 `io.nop.refactor.core.symbol`（SymbolResolverAdapter/SymbolKind/SymbolDeclaration/SymbolReference，Resolution 显式 resolved/unresolved 双形态）已落地；nop-refactor-java 模块 + JavaSymbolResolverAdapter（buildIndex 五类声明索引 + resolveReference 绑定过滤）已落地；RenameOperation 为 fail-closed 骨架（plan 抛 `not yet implemented: rename symbol resolution lands in WI10`，NOT_IMPLEMENTED_MESSAGE 常量 + TestRenameOperationSkeleton 钉住）。
- **RenameRequest 现形态**：`RenameRequest(SymbolTarget target, String newName, RenameScope scope)`——无文件集与适配器入参；SymbolTarget 恰一形态构造器 fail-closed；scope v1 恒 MODULE。
- **框架红线**：apply（EditPlanApplier.apply）与 verify（RefactorVerifier.assemble）在 nop-refactor src/main 内仅 RefactorOperationRunner 一处调用（:60 与 :96-98，R1 实证）；`OperationPlan(files, languageByPath, engine, nonApplies)` 是 plan 段唯一产物；RewriteOperation.java:102 与测试域 TestOperationFrameworkWiring:171（MarkerOperation）按 4 参构造 OperationPlan。
- **WI5 载荷**：`Verification(parseOk, errorNodeCount, residualDiagnostics, symbolIntact)`——assemble 现恒填 symbolIntact=null（design 01 §四 裁定：rename 类操作 WI9–WI12 接管正式计算）；assemble 现为 4 参形态，测试域直调点：TestRefactorVerifier:181、TestRefactorCliEndToEnd:234/:242。
- **ScopeAnalyzer 消费面**（nop-lint-java，public final，R1 逐条实证）：`definitionOf(unit, line, col) → Definition(name, line, col)` 对**变量声明标识符自身位置返回自身**（位置相等通过 `!after` 判定）、对引用出现返回其绑定声明位；`declaredNames(unit, line, col)` = **最内层作用域的直系声明**（非全链）；`collectDeclarations` 只收集 VariableDeclarator/Parameter/FieldDeclaration——**方法与类型声明名位置 definitionOf 返回 null**。定义模型覆盖变量位置。
- **仓内枚举先例的坑**（R1 实证）：resolveReference 用 `findAll(SimpleName.class)`——对局部变量 rename 会误改方法调用名/字段访问尾段/类型名（Java 名字空间分离，这些位置的 definitionOf 会解析到同名局部变量）。rename 面不得复用该先例。
- **适配器 v1 简化面**（WI9 审计记录）：resolveReference 路径的 `definitionAt` 消歧为 v1 简化——rename 面以 ScopeAnalyzer 正式语义接管。
- **byte/char 边界**：SPI 定位契约为 UTF-8 字节偏移；JavaParser 报 line/col——JavaSymbolResolverAdapter.ParsedFile 已实现双向换算（多字节字符经契约测试钉住）。
- **deps**：WI9 completed，可开工。

## 执行面裁定记录（R1 审查钉死的契约面，逐条编号供 Phase 引用）

1. **适配器注入路径（R1 Blocker 裁定）**：`RenameRequest` 携带适配器引用——`RenameRequest(SymbolTarget target, String newName, RenameScope scope, List<SourceFile> files, SymbolResolverAdapter resolver)`。core 只依赖 SPI 接口（依赖方向红线不破：core 零 java 适配依赖）；运行时注入语义沿 WI9 裁定 3；operation 保持无状态单例。此形态即 WI12 GraphQL RenameInput 契约的前身（face 装配适配器 bean 后入参）。check 校验 files 非空、target 为 offset 形态、target.path ∈ files（fail-closed 精度，R1 m3）。
2. **rename 解析 SPI 面（R1 Major-1 裁定）**：`SymbolResolverAdapter` 新增四态 rename 解析操作——输入 = 已构建 index + target(offset 形态) + newName；输出显式四态（机器可判读，detail 非空，与 Resolution 同型 fail-closed 构造器）：可解析（目标声明 + 绑定出现集，字节区间）/ CONFLICT / OUT_OF_SCOPE / UNRESOLVED。**分工钉死**：目标定位走 buildIndex 索引（五类 kind 齐备、字节区间包含判定）——METHOD/TYPE/FIELD 目标由索引 kind 判 OUT_OF_SCOPE（definitionOf 对方法/类型名位置返回 null，不能承担定位）；definitionOf 只负责"出现绑定"（出现位置 → 返回定义位与目标声明位相等，比较基准 = line/col，R1 m1——避免字节换算误差）。
3. **冲突域 = 方法边界 + 字段面（R1 Major-2 裁定）**：沿作用域链收集目标声明外封方法边界内**全部**局部/参数名（经 scopeKind + declaredNames 逐层上行，declaredNames 仅最内层不够）——方法边界内任一作用域已声明新名 → CONFLICT（覆盖外层块/参数/内层块漏报）。新名撞本文件已声明**字段**名 → CONFLICT（同方法内未限定字段引用会被改名后的局部截获——可编译但语义静默破坏，保守拒绝）。newName 等于旧名（自改名）→ CONFLICT（no-op 拒绝，保持编辑列表非退化）。以上三类走 CONFLICT nonApply，宁可误拒不静默破坏（vision 原则 6）。
4. **出现集枚举（R1 Major-3 裁定）**：改名出现集 = **NameExpr 标识符**（未限定变量引用的唯一形态）∪ **目标声明标识符自身**（单独加入，NameExpr 不含声明名）；**禁止 SimpleName 全量枚举**（会误改方法调用名/字段访问尾段/类型名——仓内 resolveReference 先例对 rename 面是错的）。绑定判定：NameExpr 位置经 definitionOf 返回定义位 == 目标声明位（line/col 相等）才收集；遮蔽作用域中的同名 NameExpr 返回别的声明位 → 结构性排除。编辑列表 = 声明标识符 + 全部绑定 NameExpr，replacement = 新名。
5. **symbolIntact 前身形态数据流（R1 Major-4 裁定）**：
   - **计算点 = plan 期预演**：编辑拼接预演（不落盘）的改名后内容上重解析——改名后声明标识符 startByte 不变（编辑从声明位起替换），重解析用新名 NameExpr 绑定计数 M（声明位 line/col 在改名后不变，比较免位移簿记）；N = 旧名绑定出现数（声明标识符不计入 N/M 两侧，对称）；symbolIntact = (N == M)，plan 期算得 Boolean（确定性等价于落盘后重解析——同一字节序列落盘）。
   - **载体 = OperationPlan 新增 nullable 组件 `Boolean symbolIntact`**（record 保持纯数据；rewrite 面恒 null）。**additive 形态**：保留旧 4 参便捷构造器（委托新 canonical，symbolIntact=null）——RewriteOperation.java:102 与 TestOperationFrameworkWiring:171 零修改不受影响；RefactorVerifier.assemble 新增 5 参 overload（4 参原形态保留、委托新形态）——三个测试域直调点零修改。
   - **runner 传递**：assemble 调用仍单点，传入 plan.symbolIntact()。**rollback 语义**：文件被守卫回滚（ROLLED_BACK）→ symbolIntact 置 null（断言输入不存在 = 诚实"未验证"形态）。**false 值语义**：仅载荷报告（N≠M 为实现缺陷信号，可判读非异常——预演与落盘同字节，恒等应成立）。
6. **编辑载体取值（R1 m2 裁定）**：rename 编辑的 `Fix.ruleId = "rename"`、`description = "rename <oldName> to <newName>"`（FileEdit.summary 经 description 面呈现，WI12 渲染消费）。
7. **骨架交接**：NOT_IMPLEMENTED 骨架时代随本 plan 结束——NOT_IMPLEMENTED_MESSAGE 常量移除；TestRenameOperationSkeleton 的 plan-fail-closed 用例替换为第一档真实语义用例（check 拒绝面用例保留、构造点适配新签名）。WI9 骨架的"not yet implemented"契约由本 plan 的实现契约接替，属 WI10 owned 演化（非静默改动）。

## Goals

- **rename 解析面（Java 适配）**：四态 rename 解析操作（裁定 2）——目标声明定位（索引）、绑定引用收集（NameExpr + definitionOf 绑定，裁定 4）、方法边界 + 字段面冲突检测（裁定 3）。
- **RenameOperation.plan 落地**：第一档 kind 边界 fail-closed（FQN 定位与字段/方法/类型目标 → nonApplied(OUT_OF_SCOPE)；UNRESOLVED 同型）；名字冲突 → nonApplied(CONFLICT)；正常路径产出编辑列表经框架 apply/verify；symbolIntact 前身形态经 WI5 assemble 单点组装（裁定 5），rewrite 面恒 null 不变。
- **骨架交接**：裁定 7。

## Non-Goals

- 不做字段/非虚方法/类型 rename（roadmap WI11）；不做跨文件引用、import 语句、限定名（FQN）更新（WI11 + stale-import 检查）。
- 不做 Refactor__previewRename / Refactor__applyRename GraphQL 面（WI12）；CLI rename 子命令不入 v1（roadmap 未列，cli 面维持 rewrite-only）。
- 不做工程级 rename / classpath 装配器（WI2 裁定出预算）。
- 不改 nop-lint / nop-treesitter / nop-code 任何行为（零修改红线）；不改 WI5 载荷既有字段语义（symbolIntact 语义扩展见裁定 5，rewrite 面恒 null 不变）。
- 不做 LTK 式 Undo/脚本/participants（roadmap 明文）。

## Scope

### In Scope

- core：SymbolResolverAdapter SPI 四态 rename 解析操作；RenameRequest 签名扩展（files + resolver，裁定 1）；OperationPlan additive symbolIntact 组件 + 便捷构造器；RefactorVerifier additive 5 参 assemble overload；RenameOperation.plan 第一档实现；runner 传递（调用点仍单点）。
- nop-refactor-java：rename 解析实现（裁定 2/3/4）+ 契约测试。
- 测试演化（WI10 owned，裁定 7 + R1 M5 清单）：TestRenameOperationSkeleton 4 处构造点 + plan 用例替换；TestOperationFrameworkWiring / TestRefactorVerifier / TestRefactorCliEndToEnd / RewriteOperation.java:102 **零修改**（additive 形态保护，裁定 5）。
- design 01 增注；ai-dev/logs 条目。

### Out Of Scope

- WI11/WI12/WI13 全部交付面；nop-lint/nop-treesitter/nop-code 行为面；docs-for-ai 模块文档新建（WI13）。

## Execution Plan

前置依赖：WI9 completed。Phase 1 开工前 git status 自查 nop-refactor 干净。

### Phase 1 - SPI rename 面 + Java 适配实现 + 契约测试（Decision + Fix）

Status: planned
Targets: `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/symbol/`、`nop-refactor/nop-refactor-java/src/main/java/io/nop/refactor/java/`、两模块 test

- Item Types: `Decision + Fix`

- [ ] core SPI 四态 rename 解析操作（裁定 2）：显式结果形态 + detail 非空 fail-closed 构造器；定位走索引 kind、绑定走 definitionOf（line/col 基准）
- [ ] RenameRequest 签名扩展（裁定 1）：files + resolver 入参 + check 校验（files 非空/offset 形态/path ∈ files）
- [ ] nop-refactor-java rename 解析实现（裁定 3/4）：NameExpr ∪ 声明标识符出现集、definitionOf 绑定判定（line/col 相等）、方法边界链式冲突域 + 字段面冲突 + 自改名拒绝
- [ ] 契约测试（fixtures 覆盖）：参数/局部变量 rename 出现集正确；**遮蔽场景**（内层重声明同名，内层 NameExpr 不绑定目标）；**负向枚举 fixture**（同名方法调用 `this.x()`/同名字段访问 `obj.x`/同名类型 `new X()` 不被收集——R1 M3 负向面）；新名冲突三型（同方法其他块/字段撞名/自改名）→ CONFLICT；字段/方法/类型目标 → OUT_OF_SCOPE；定位不可达 → UNRESOLVED；零引用目标（仅声明标识符，N=0 对称）；多字节文件 byte 偏移
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw test -pl nop-refactor/nop-refactor-java -am` 全绿（-am 带上游 core 测试，RenameRequest 构造点演化同轮被覆盖）
- [ ] **无静默跳过**（Minimum Rules #24）：四态全部显式形态 + detail 非空；不可解析/冲突/越档不产任何编辑
- [ ] **新功能测试清单**（Minimum Rules #25）：四态 + 遮蔽 + 负向枚举 + 零引用 + 多字节，逐项列出并落为测试
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - RenameOperation.plan 落地 + symbolIntact 前身形态 + 框架端到端（Decision + Fix）

Status: planned
Targets: `nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/operation/`、`nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/RefactorVerifier.java`、`nop-refactor/nop-refactor-core/src/test/java/`、`ai-dev/design/nop-refactor/01-architecture-baseline.md`

- Item Types: `Decision + Fix`

- [ ] RenameOperation.plan 第一档实现（裁定 2/4/5/6）：四态分发——可解析 → 编辑列表（ruleId="rename"，description="rename <old> to <new>"）+ symbolIntact 预演断言；CONFLICT/OUT_OF_SCOPE/UNRESOLVED → 零编辑 + 对应 NonApply；NOT_IMPLEMENTED 骨架面移除（裁定 7）
- [ ] symbolIntact 前身形态接线（裁定 5）：OperationPlan additive nullable 组件 + 4 参便捷构造器保留；RefactorVerifier.assemble additive 5 参 overload（4 参保留）；runner 传参——apply/verify 调用点仍各单点；rollback → symbolIntact=null
- [ ] 框架端到端测试：runner.run(RenameOperation, request, dryRun=false) 真实落盘（声明 + 绑定引用全部改名、其余内容逐字节不变）+ 载荷断言（applied=true、verification.symbolIntact=true、stats）；preview dryRun 不落盘；CONFLICT 场景（零编辑 + nonApplied(CONFLICT) + symbolIntact=null）；越档场景（OUT_OF_SCOPE）
- [ ] WI9 骨架测试演化（裁定 7）：TestRenameOperationSkeleton 4 处构造点适配 + plan 用例替换为第一档语义
- [ ] 零修改保护核对：RewriteOperation.java、TestOperationFrameworkWiring、TestRefactorVerifier、TestRefactorCliEndToEnd 零修改（additive 形态承载演化——git diff 实证）
- [ ] design 01 增注：rename 第一档语义 + symbolIntact 前身形态组装形态 + 骨架交接记录
- [ ] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [ ] `./mvnw test -pl nop-refactor/nop-refactor-core,nop-refactor/nop-refactor-java -am` 全绿
- [ ] **端到端验证**（Minimum Rules #22）：rename 从 RenameRequest 输入经 runner 四段到 RefactorResult 载荷与磁盘落盘完整贯通——组件级单测不替代
- [ ] **接线验证**（Minimum Rules #23）：rename 与 codemod 走同一 plan/apply/verify 机制（apply/verify 调用点 grep 仍单点；symbolIntact 经 RefactorVerifier.assemble 组装，无第二载荷组装路径）
- [ ] **无静默跳过**（Minimum Rules #24）：冲突/越档/不可解析零编辑 + 结构化 nonApplied；symbolIntact 断言 false 与 rollback-null 形态机器可判读（非静默）
- [ ] **新功能测试清单**（Minimum Rules #25）：plan 四态 + symbolIntact 断言 + 端到端落盘 + preview/apply 双态，逐项列出并落为测试
- [ ] 零行为红线自查（scoped git diff）：nop-lint / nop-treesitter / nop-code 零修改；rewrite 面既有测试零修改全绿（TestOperationFrameworkWiring/TestRefactorCliEndToEnd/TestNopRefactorBizModel/TestNopRefactorGraphQL——additive 形态保护下成立）
- [ ] design 01 增注已落档且与 landed 实现互洽
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] 全部 in-scope 项完成，无残留未勾选 checklist
- [ ] rename 第一档成立：局部变量/参数单文件 rename 经 WI9 框架四段真实可用（runner 端到端落盘 + 载荷）；名字冲突 fail-closed 进 nonApplied（CONFLICT 三型全覆盖）
- [ ] symbolIntact 前身形态成立：引用计数断言经 WI5 assemble 单点组装；rewrite 面恒 null 不变（WI5 载荷契约零缩水）
- [ ] 零行为红线（scoped diff）：nop-lint / nop-treesitter / nop-code 零修改；rewrite 面行为零变化（既有测试零修改全绿，additive 形态承载演化）
- [ ] owner docs 已同步：design 01 增注（rename 第一档 + symbolIntact 组装形态 + 骨架交接）
- [ ] **Anti-Hollow Check**：closure audit 已验证 (a) rename 端到端从输入到落盘与载荷运行时连通（遮蔽/冲突/负向枚举语义真实生效，非仅类型存在），(b) 无空方法体/静默跳过/no-op 作为正常实现
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-core --severity high` 退出 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-java --severity high` 退出 0
- [ ] `./mvnw test -pl nop-refactor/nop-refactor-core,nop-refactor/nop-refactor-java,nop-refactor/nop-refactor-graphql -am` 全绿
- [ ] 代码规范：`check-import-order.mjs` 本 plan 新增/变更文件零违规（范围口径）
- [ ] vision 原则 1–9 回扣核对（closure audit 执行）：原则 3（载荷不缩水——symbolIntact 首次非 null 组装）、原则 6（fail-closed——冲突三型/越档/不可解析显式形态）、原则 9（预算——第一档最小实现，解析复用 WI9 SPI）为重点
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（范围口径：本 plan 变更文件零错误；全仓 gate 因并发文档 churn 波动时，提交前全局复跑）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/10-wi10-rename-first-rung.md --strict` 退出 0

## Deferred But Adjudicated

（无——本 plan 无 deferred 项）

## Non-Blocking Follow-ups

- 字段/非虚方法/类型 rename 与跨文件引用/import/FQN 更新/stale-import 检查：roadmap WI11 承接——Why Not Blocking Closure：第一档 scope 为单文件局部变量/参数，跨文件面本 plan 未声明。
- Refactor__previewRename / Refactor__applyRename GraphQL 接线：roadmap WI12 承接。
- resolveReference（非 rename 路径）的 SimpleName 全量枚举歧义面（WI9 先例，R1 M3 证实对变量面有误绑定风险）：WI11 统一收紧——Why Not Blocking Closure：该路径 v1 消费面为索引查询演示，rename 面已用正确枚举。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）

## Review Record

- **R1(2026-09-26, fresh session agent_44f52c2f): REVISE** — 1 Blocker + 5 Major + 5 Minor, all fixed in text:
  - Blocker（B1→裁定 1）：适配器注入路径钉死——RenameRequest 携带 SPI 接口引用（core 零 java 依赖、运行时注入、operation 保持单例），Phase 2 Item Types 补 Decision
  - Major-1（→裁定 2）：四态 rename op 输入（index/target/newName）与分工钉死——定位走索引 kind（METHOD/TYPE 正确分类 OUT_OF_SCOPE，消除 fixture 矛盾）、definitionOf 只管绑定
  - Major-2（→裁定 3）：冲突域扩为方法边界链式收集 + 字段撞名 + 自改名三类 CONFLICT（c/d 类静默破坏保守拒绝）
  - Major-3（→裁定 4）：出现集 = NameExpr ∪ 声明标识符，禁止 SimpleName 全量枚举；负向 fixture 钉死误改写面
  - Major-4（→裁定 5）：symbolIntact 数据流四处钉死——plan 期预演计算（line/col 基准免位移簿记）、OperationPlan nullable 组件 + additive 便捷构造器、assemble 5 参 overload（4 参保留）、rollback→null / false 仅报告
  - Major-5（→裁定 5/M5 清单）：构造点清单补全（Skeleton×4 / RewriteOperation:102 / Wiring:171 / Verifier:181 / CliE2E:234,242）+"TestOperationFrameworkWiring 零修改"矛盾以 additive 形态调和
  - Minor（m1–m5）：line/col 比较基准、ruleId="rename" 取值、path ∈ files 校验、doc-links 范围口径豁免语、Phase 1 -am 覆盖说明——全部随修订落档
