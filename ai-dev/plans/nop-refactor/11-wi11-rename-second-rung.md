# 11 WI11 rename 阶梯第二档——字段/非虚方法/类型模块域 + import/FQN 同步 + stale-import 检查

> Plan Status: active
> Last Reviewed: 2026-09-26
> Source: `ai-dev/backlog/nop-refactor-roadmap.md`（M2 WI11 原文 + Cross-Cutting）；`ai-dev/design/nop-refactor/01-architecture-baseline.md` §二（WI2 裁定：CORE→CODE 不接线、内嵌索引）；`ai-dev/analysis/2026-09/2026-09-25-wi2-symbol-solver-coverage-spike.md` §四（单模块符号域 + 简名/同包/import 绑定过滤裁定）；`ai-dev/design/nop-refactor/00-vision.md` §三 原则 6
> Related: `ai-dev/plans/nop-refactor/10-wi10-rename-first-rung.md`（第一档 + symbolIntact 组装形态，completed）；09（框架/SPI，completed）
> Review: R1(2026-09-26, fresh session agent_a5045e52): REVISE — 2B+8M+5m all fixed；R2 delta check（同会话）11/11 PASS 放行，新增 N1（import dot 前缀形态）/N2（CONSTRUCTOR kind 载体）两句补丁已落档（详见裁定 1/7/8 与 fixtures）。

## Purpose

执行 roadmap WI11（Item Type: Fix）：rename 阶梯第二档——**字段 + 非虚方法 + 类型**，模块内符号域：跨文件引用按 WI2 裁定的绑定过滤（简单名 + 同包/import/限定名）改写；**import 语句与限定名（FQN）引用同步更新**（类型 rename）；**stale-import 检查**——改名预演后残留旧 FQN import/引用按失败处理（plan 期 CONFLICT 拒绝，不落盘损坏状态），杜绝"parseOk=true 但代码已损坏"的静默破坏；跨模块引用结构性不可见，由潜在影响面 fail-closed 兜底，不静默漏改。经 WI9 框架唯一路径执行，symbolIntact 组装沿 WI10 面。

## Current Baseline

（live 已核对，2026-09-26）

- **WI10 已 completed**（提交 621db35f9a）：`RenameResolution` 四态（RESOLVED 带 declaration + occurrences + symbolIntact / CONFLICT / OUT_OF_SCOPE / UNRESOLVED，fail-closed 构造器——RESOLVED 必带 declaration+symbolIntact，非 RESOLVED 禁带）；`JavaSymbolResolverAdapter.renameResolution` 第一档（索引包含定位 declarationContaining + kind OUT_OF_SCOPE 门 + NameExpr∪声明标识符绑定 + methodBoundaryClash/fieldNames/自改名冲突域 + symbolIntactOnPreview 预演——byte 区间经 charIndexAt 换算）；`RenameOperation.plan` 四态分发（单文件 PlannedFile 组装）；symbolIntact 经 OperationPlan nullable 组件 → runner（rollback→null）→ assemble 5 参 overload 单点组装。
- **R1 实证的两处结构性边界（本 plan 的核心修正对象）**：(a) 第一档出现集枚举（NameExpr）不覆盖类型使用主体——`new Service()`、字段/参数/返回值类型、cast/instanceof、extends/implements、泛型实参等全部在 ClassOrInterfaceType，不在 NameExpr；(b) 编辑载体单文件——`RenameResolution.occurrences` 无文件维度、`RenameOperation.plan` 硬编码单 PlannedFile、verify 经 languageByPath 解析每文件语言（缺条目抛错）——跨文件 rename 需载体与组装面 additive 演化。
- **resolveReference 存量面**：binds() 仅 import name 与 FQN 全等（**通配 type import 不绑定**、**static import 未处理**）；FIELD/METHOD 索引 fqn 恒空串（无 declaring-type FQN）；限定 mention 枚举 findAll(Name.class) 会命中 import/package 子节点且仅对异包文件运行。
- **kind 现状**：ConstructorDeclaration → SymbolKind.METHOD（构造器混淆陷阱）；resolveReference 旧路径的 findAll(SimpleName) 误绑定面（plan 10 follow-up 在案，本 plan 不触碰该旧路径）。
- **deps**：WI10 completed，可开工。

## 执行面裁定记录（R1 审查钉死，逐条编号供 Phase 引用）

1. **TYPE rename 出现集——完整节点面（R1 Blocker-1 裁定）**：类型使用的改写集 = 声明标识符 ∪ 该类型构造器声明名（构造器名=类型名，随类改名）∪ **绑定文件内 ClassOrInterfaceType 简名**（`new X()`、字段/参数/返回值类型、cast/instanceof、extends/implements、泛型实参、数组、`X.class`、`X.this` 的类型面）∪ **绑定文件内表达式位置 NameExpr 简名**（如 `X.staticMember()` 的 scope）∪ **import 语句**（ImportDeclaration name 与旧 FQN **全等或 dot 边界前缀**——`import a.Service;` 全等、`import a.Service.Foo;` 前缀改前缀段为 `a.NewName.Foo`；通配 `a.*` 无需改写）∪ **限定名 mention**（Name/FieldAccessExpr 链拼接与旧 FQN **全等 + dot 边界**——`a.Service` 命中、`a.ServiceHelper` 不命中；`a.Service.Foo` 嵌套类型前缀命中并改前缀段）。**遮蔽排除**：绑定文件内声明了同名局部变量/字段的，其同名类型使用在该文件结构性不可判 → 该文件整体 fail-closed CONFLICT（不猜绑定）。**去重**：mention 枚举显式排除 ImportDeclaration/PackageDeclaration 子树（import 面唯一归 import 规则）。
2. **跨文件编辑载体（R1 Blocker-2 裁定）**：`RenameResolution` additive 新组件 `List<FileRewrite> fileRewrites`（record `FileRewrite(String path, List<RenameSpan> spans)`，`RenameSpan(range, replacement)`——TYPE 面 import/限定 mention 的替换文本为新 FQN 而非新简名，per-span 文本使跨文件载体诚实）——跨文件改写集的权威载体；`occurrences` 保留 = 目标文件条目的区间（第一档兼容面）。`RenameOperation.plan` 演化为**多文件组装**：遍历 fileRewrites 逐文件取 original 构建 PlannedFile（每文件语言 = request.language——解析仅收集同语言绑定），languageByPath 全覆盖改写文件（verify 逐文件解析必需）；"RenameOperation 无需面变化"作废，Targets 补 core 路径。
3. **FQN 定位归属（R1 Major-1 裁定）**：WI11 接受 FQN form 目标——check 门放行 FQN form（删除 WI10 的 FQN 一律拒绝）。实现演化（closure audit M2 裁定补录，与 design 01 补记一致）：FQN 定位泛化为全声明查找（declarationByFqn——TYPE 经 byFqn 表、FIELD/METHOD 经 declaringType.member 索引 FQN），member FQN 可直接定位并按 kind 分发进对应第二档面（档位由 kind 分发决定，与定位形态正交，staticImportFieldRenameCrossesFiles 端到端钉住）；未知 FQN → UNRESOLVED。文件+offset 形态两档通用。测试演化：Skeleton 的 FQN 拒绝用例 → FQN 接受用例（check 层）。
4. **FIELD/METHOD 保守面 + 潜在影响面精确界定（R1 Major-2 裁定）**：改写集 = 声明标识符 ∪ 声明类内绑定引用（NameExpr 经 definitionOf 绑定 / `this.f` FieldAccessExpr 尾段 / 直接调用名——方法调用名 definitionOf 无定义模型，按同类内结构性收集）∪ **static import 精确绑定文件**的简名使用（裁定 5）。**潜在影响面（fail-closed CONFLICT，"不静默漏改"兜底）**：(a) 声明类内同名声明（重载/同名字段）→ CONFLICT；(b) **绑定文件**（同包文件 / 精确 import 声明类 FQN 的文件 / 精确 static import 文件）内存在 receiver 型同名 token（MethodCallExpr 名 / FieldAccessExpr 尾段——结构性不可判是否为目标）→ CONFLICT；(c) 非绑定文件的同名 token 结构性不可达 → 不触发（它类私有方法等无关同名不拒）。**通配 static import**（`import static a.B.*;` 且 a.B == 目标成员 declaringFqn）：该文件为歧义文件，内含同名简名 token → CONFLICT。
5. **static import 绑定（R1 Major-3 裁定）**：索引为 FIELD/METHOD additive 补 **declaring-type FQN**（成员 fqn = declaringTypeFqn + "." + name）；static import 绑定 = `isStatic() && import name 全等 成员 fqn`；尾段匹配废除（防 `import static x.Y.m;` 误绑改坏）。
6. **通配 type import（R1 Major-4 裁定）**：`import a.*;` = 绑定（收集该文件简名使用，无 import 改写需求）；通配域内出现模块内其它同名 TYPE 声明 → 歧义 fail-closed CONFLICT。
7. **stale-import 检查（R1 Major-5 裁定）**：执行位置 = **plan 期预演**（各改写文件预演内容重新解析，扫旧 FQN import 残留（含 dot 边界前缀形态）/ 旧 FQN 限定 mention 残留 / 绑定文件内旧简名类型使用残留）；**处置 = plan 期 CONFLICT 拒绝**（零编辑不落盘——比落盘后报告更强的 fail-closed，此为 roadmap"verify 含 stale-import 检查、按失败处理"的第二档落地面，design 01 增注记录与原文措辞的落点差异）；**symbolIntact 保持 WI10 原义**（预演/落盘同字节下的引用计数对称断言——实现缺陷信号），不与 stale 复合。**N/M 构成**：N = 改写集区间总数（跨文件聚合，声明标识符计入）；M = 预演内容按同一枚举规则对新名/新 FQN 的命中总数（跨文件聚合）；N==M 逐文件等价聚合。
8. **kind 路由顺序 + 构造器（R1 Major-7 + m3 裁定）**：renameResolution 分支顺序 = 自改名检查（kind 无关）→ kind 路由（LOCAL_VARIABLE/PARAMETER → 第一档路径不变；CONSTRUCTOR 索引态 → **OUT_OF_SCOPE**（"构造器改名 = 类改名，归 TYPE 面"——TYPE 面出现集已含构造器声明名；载体 = core `SymbolKind` additive 枚举值 `CONSTRUCTOR`，索引收集构造器改标该 kind，不再混入 METHOD）→ FIELD/METHOD/TYPE → 第二档路径）→ 各第二档分支的冲突域检测先于出现集收集。methodBoundaryClash 仅在第一档路径内调用（FIELD/TYPE 目标无外封方法，路由先行避免 fail-closed 异常）。
9. **definitionOf 异常可观测（R1 m4 裁定）**：排除出现计数并入 RESOLVED detail（如 "resolved: N rewrites across F files; K occurrences excluded by unresolvable positions"）——RESOLVED detail 允许非空，由测试钉住。
10. **测试演化清单（R1 Major-6 裁定）**：`JavaSymbolResolverRenameContractTest` 两处 method/field OUT_OF_SCOPE 用例 → 第二档语义用例；`TestRenameOperationFirstRung.outOfScopeKindSurfacesAsNonApply` → 构造器 OUT_OF_SCOPE 用例；`TestRenameOperationSkeleton.checkRejectsFqnTargetingAsSecondRung` → FQN-TYPE 接受 + FQN-非 TYPE 拒绝。**Closure Gate 措辞收窄**："rewrite 面与第一档局部变量/参数语义用例零修改"。

## Goals

- **TYPE rename 完整面**：声明+构造器名+跨文件绑定类型使用（ClassOrInterfaceType/NameExpr/限定链）+ import 同步 + dot 边界限定名 + 遮蔽 fail-closed + stale-import 预演拒绝。
- **FIELD/METHOD 保守面**：声明文件 + static import 精确绑定 + 潜在影响面三界定 fail-closed；declaring-type FQN 索引 additive。
- **跨文件载体**：RenameResolution.fileRewrites + RenameOperation.plan 多文件组装（runner 四段不变，单点红线不破）。
- **FQN 定位（TYPE）+ kind 路由 + 构造器排除 + follow-up 收紧（最内层方法、异常可观测）**。

## Non-Goals

- 不做 receiver 型字段/方法访问（`obj.f`/`obj.m()`）的跨文件改写（类型求解超出结构面——以裁定 4 影响面 fail-closed 替代，升级归 design 层）。
- 不做继承链/接口 override 传播（非虚方法边界 = 绑定面）。
- 不做 Refactor__previewRename / applyRename（WI12）；CLI rename 面不入 v1。
- 不做工程级 rename（WI2 出预算）；不改 nop-lint/nop-treesitter/nop-code 任何行为。
- 旧 resolveReference 路径的 SimpleName 枚举面不触碰（plan 10 follow-up 保留）。

## Scope

### In Scope

- nop-refactor-java：renameResolution 第二档（裁定 1/3/4/5/6/7/8/9）+ 契约测试（含全部 fixtures）。
- core：RenameResolution.fileRewrites additive（FileRewrite record）+ RenameOperation.plan 多文件组装 + check FQN 门演化。
- 测试演化清单（裁定 10 三处）。
- design 01 增注；ai-dev/logs 条目。

### Out Of Scope

- WI12/WI13 交付面；receiver 型跨文件改写；继承链传播；nop-lint/nop-treesitter/nop-code 行为面；旧 resolveReference 路径收紧。

## Execution Plan

前置依赖：WI10 completed。Phase 开工前 git status 自查 nop-refactor 干净。

### Phase 1 - 第二档解析 + 跨文件载体 + 契约测试（Decision + Fix）

Status: completed
Targets: `nop-refactor/nop-refactor-java/src/main/java/io/nop/refactor/java/`、`nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/symbol/`、`nop-refactor/nop-refactor-core/src/main/java/io/nop/refactor/core/operation/RenameOperation.java`、两模块 test

- Item Types: `Decision + Fix`

- [x] core additive：RenameResolution.fileRewrites（FileRewrite record）+ RenameOperation.plan 多文件组装（PlannedFile 逐文件 + languageByPath 全覆盖）+ check FQN 门演化（裁定 2/3）
- [x] 索引 additive：FIELD/METHOD declaring-type FQN（裁定 5）；TYPE 的 ClassOrInterfaceType/构造器收集
- [x] TYPE rename 完整面实现（裁定 1）：四类出现面 + import 改写 + dot 边界限定名 + 同名局部/字段文件 fail-closed CONFLICT + mention 去重
- [x] FIELD/METHOD 保守面实现（裁定 4/5）：声明类绑定引用 + static import 精确绑定 + 潜在影响面三界定 + 通配 static import 歧义
- [x] 通配 type import 绑定 + 歧义 CONFLICT（裁定 6）
- [x] stale-import 预演检查 + CONFLICT 处置（裁定 7）
- [x] kind 路由顺序 + 构造器 OUT_OF_SCOPE + methodBoundaryClash 最内层收紧 + definitionOf 异常计数入 detail（裁定 8/9 + WI10 audit Minor-1/2 收紧）
- [x] 契约测试（fixtures 落地面——closure audit M1 后诚实化）：已落地 = TYPE 跨文件 5 文件逐字节（同包/精确 import/通配/非绑定 FQN mention + new/字段类型/泛型 + import 同步）+ 遮蔽整文件 CONFLICT + static import 单成员命中落盘 + 嵌套类型 import 前缀（a.Service.Foo→a.Service.Bar）+ `a.ServiceHelper` dot 边界不动（import/mention 去重由 e2e 无伪 CONFLICT 证明）+ cast/instanceof/声明类型面 + FIELD 影响面双向（绑定 receiver 拒绝/非绑定不触发）+ FQN-TYPE 定位 + member FQN 定位落盘（staticImportFieldRenameCrossesFiles）+ 未知 FQN → UNRESOLVED（declarationByFqn 未命中） + detail 计数摘要 + 演化三处（构造器 OUT_OF_SCOPE/method 第二档落盘/FQN 门接受）。黑盒不可构造项移入 Deferred（见下）
- [x] 测试演化（裁定 10 三处）
- [x] design 01 增注（第二档语义全集 + stale 处置与 roadmap 措辞落点差异 + N/M 声明标识符计入与第一档不计入的跨档差异说明）
- [x] `ai-dev/logs/` 对应日期条目已更新

Exit Criteria:

> 每个 Phase 完成后，必须逐条勾选本节。所有 `[x]` 后才能将 Phase Status 改为 `completed`。

- [x] `./mvnw test -pl nop-refactor/nop-refactor-java,nop-refactor/nop-refactor-core -am` 全绿（含全部新增契约测试与演化用例）
- [x] **端到端验证**（Minimum Rules #22）：TYPE 跨文件 rename 经 runner 四段到多文件落盘与载荷贯通（多 PlannedFile 组装真实生效）——组件级单测不替代
- [x] **接线验证**（Minimum Rules #23）：apply（EditPlanApplier）/verify（assemble）调用点仍各单点（grep src/main），rename 多文件走同一框架组装路径、无第二组装实现
- [x] **无静默跳过**（Minimum Rules #24）：潜在影响面/遮蔽/通配歧义/stale 全部 CONFLICT 零编辑 + detail 非空；RESOLVED detail 计数摘要可判读
- [x] **新功能测试清单**（Minimum Rules #25）：上述 fixtures 逐项列出并落为测试
- [x] 零行为红线自查（scoped git diff）：nop-lint / nop-treesitter / nop-code 零修改；rewrite 面与第一档局部变量/参数语义用例零修改全绿
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [x] 全部 in-scope 项完成，无残留未勾选 checklist
- [x] 第二档成立：TYPE 完整面（声明+构造器名+跨文件绑定类型使用+import+限定名 dot 边界）与 FIELD/METHOD 保守面（声明文件+static import 精确绑定+潜在影响面三界定）经 runner 四段真实可用
- [x] stale-import 检查成立：残留旧 FQN/旧简名类型使用 → plan 期 CONFLICT 拒绝（不落盘损坏状态）
- [x] 跨文件载体成立：fileRewrites 多文件组装经单点 apply/verify 落盘（无第二组装路径）
- [x] 零行为红线（scoped diff）：nop-lint / nop-treesitter / nop-code 零修改；rewrite 面与第一档局部变量/参数语义用例零修改全绿
- [x] owner docs 已同步：design 01 增注
- [ ] **Anti-Hollow Check**：closure audit 已验证 (a) TYPE 跨文件 rename 端到端（import/限定名同步真实落盘、多 PlannedFile 组装运行时连通）、(b) 潜在影响面/遮蔽/stale 拒绝真实拦截、(c) 无空方法体/静默跳过
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-java --severity high` 退出 0
- [x] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-refactor-core --severity high` 退出 0
- [x] `./mvnw test -pl nop-refactor/nop-refactor-core,nop-refactor/nop-refactor-java,nop-refactor/nop-refactor-graphql -am` 全绿
- [x] 代码规范：`check-import-order.mjs` 本 plan 新增/变更文件零违规（范围口径）
- [x] vision 原则 1–9 回扣核对（closure audit 执行）：原则 6（fail-closed——stale/遮蔽/影响面/通配歧义（含 FIELD NameExpr 面）全部显式拒绝）与原则 9（预算——复用 WI10 面与索引）为重点
- [ ] 独立子 agent closure-audit 已完成并记录证据（fresh session，不复用实现 session）
- [x] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0（范围口径）
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/nop-refactor/11-wi11-rename-second-rung.md --strict` 退出 0

## Deferred But Adjudicated

### stale 预演拒绝与通配歧义的黑盒 fixture 构造（closure audit M1 诚实化）

- Classification: `watch-only residual`
- Why Not Blocking Closure: stale 扫描机制 live 且经代码审读核实（typeResidueScan：旧 FQN import/mention dot 前缀 → CONFLICT 零落盘），但当前枚举完备——黑盒构造残留需先 inducing 一个收集缺口，等价于先写一个缺陷；通配歧义 CONFLICT 同理（通配绑定/歧义检查分支已实现并经代码路径核实）。两者由 N==M 计数对称断言 + 机制审读共同覆盖。
- Successor Required: no

### import/mention 去重的独立伪 CONFLICT 断言

- Classification: `watch-only residual`
- Why Not Blocking Closure: 去重已由 TYPE 跨文件 e2e 证明——b/Importer.java 同时含 import 改写与简单类型使用（若 import 面与 mention 面重叠生成双 span，applier 必报 CONFLICT 或跳过，e2e 断言 applied=true + 逐字节落盘已排除该形态）。
- Successor Required: no

## Non-Blocking Follow-ups

- receiver 型跨文件字段/方法访问改写（需类型求解）：WI2 结构面裁定外的升级，design 层另议——Why Not Blocking Closure：裁定 4 影响面 fail-closed 已兜住静默漏改。
- 继承链/接口 override 传播：非虚方法边界外的语义面——Why Not Blocking Closure：roadmap 未列，入预算须过 design。
- Refactor__previewRename / applyRename GraphQL 接线：WI12 承接。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）

## Review Record

- **R1(2026-09-26, fresh session agent_a5045e52): REVISE** — 2 Blocker + 8 Major + 5 Minor, all fixed in text:
  - Blocker-1（→裁定 1）：TYPE 出现集重写为完整节点面（ClassOrInterfaceType 主体 + NameExpr + 构造器名 + import + 限定链 dot 边界 + 遮蔽文件 fail-closed + import/mention 去重）+ fixtures 全集
  - Blocker-2（→裁定 2）：跨文件载体裁定——RenameResolution.fileRewrites（FileRewrite record）+ RenameOperation.plan 多文件组装 + languageByPath 全覆盖；"无需面变化"表述作废；Targets 补 core
  - Major-1（→裁定 3）：FQN 定位 WI11 接受、仅限 TYPE；check/adapter 门演化 + 测试演化
  - Major-2（→裁定 4）：潜在影响面三界定（声明类内/绑定文件 receiver 型/非绑定不触发）+ 双向 fixtures
  - Major-3（→裁定 5）：static import = declaring-type FQN 全等（索引 additive 补 FQN）；尾段匹配废除；通配 static 歧义
  - Major-4（→裁定 6）：通配 type import 绑定 + 同名类型歧义 CONFLICT
  - Major-5（→裁定 7）：stale 三裁定点——plan 期预演 + CONFLICT 拒绝（不落盘）+ symbolIntact 保持 WI10 原义不复用 + N/M 构成钉死
  - Major-6（→裁定 10）：测试演化清单三处 + Closure Gate"零修改"措辞收窄
  - Major-7（→裁定 8）：构造器 OUT_OF_SCOPE（改名=类改名，TYPE 面含构造器声明名）
  - Major-8（→Phase 1 Exit）：补 Minimum Rules #22/#23 两条
  - Minor m1–m5：dot 边界、mention 去重、路由顺序、detail 计数载体、Targets 补 core——全部随裁定落档
- **R2(2026-09-26, 同会话 delta): 放行执行** — 11/11 PASS；快扫新增 N1/N2（import dot 前缀形态、CONSTRUCTOR kind 载体）两句补丁已落档（裁定 1/7/8）；N/M 跨档差异（第二档计入声明标识符、第一档不计入——各自内部对称）记 design 01 增注义务。
