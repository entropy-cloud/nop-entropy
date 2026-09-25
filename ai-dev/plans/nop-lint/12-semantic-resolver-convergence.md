# 12 nop-lint-java 语义层收敛（缓存对齐/ParsedUnitCache/异常翻译）

> Plan Status: draft
> Last Reviewed: 2026-09-25
> Pre-Review: 2026-09-25 主审计者逐类亲核 baseline（样板 ×4 各项、C9 三入口无 catch、M6/M7、双编译、死字段、minimalAt ×2、constantValue 双遍 build 全部确认）
> R1 对抗审查（agent_006ff517）：2 Blocker + 1 Major 修订——B12-1 **solver 实为两种配置**（Type=仅 Reflection；Semantic=Reflection+ClassLoader(contextClassLoader)）：统一会打破 L2"项目类型须降级"契约或缩水 L4 可答面→缓存组件按消费者参数化三种形态（plain/solver-Reflection/solver-Reflection+ClassLoader）；B12-2 **queryCache 混存两种值**（isAssignableTo 存 "true"/"false"、typeNameAt 存类型名 :115）→拆两个缓存或 Map<String,Object>；M12-3 minimalAt 删除需把 index/byte 位置穿入 ScopeAnalyzer/DataflowQueries 公开签名（TestScopeAnalyzer/TestDataflowQueries 直测签名须适配；行为零变化限定到**方法定位粒度**）；m12-4 观测缝本身是新增件（包内缝可行）；m12-5 其余 baseline 全部属实
> Source: `ai-dev/analysis/2026-09/2026-09-25-nop-lint-quality-optimization-deep-audit.md`（findings C9、D1 全部）
> Related: 11-kernel-correctness-fixes.md（DefUseChain findVar 修复先行）、ai-dev/design/nop-lint/06-pmd-errorprone-alignment.md

## Purpose

收敛 nop-lint-java 语义层四类 resolver 桥接的多轮推进沉积：约 200 行逐行重复的样板（LRU/parse/装配/位置换算 ×4 类）、同一"解析缓存"概念四种做法（三种有界无失效 + 一种无界无同步）、JavaDataflowResolver 的错误缓存键控粒度（同文件 N 个位置 = N 次整读整解析）、以及 L4 路径放行裸 `UnsolvedSymbolException` 违背自身 fail-closed 契约。全部为模块内收敛，不改 SPI 契约与规则行为。

## Current Baseline

（行号引用见分析报告 D1/C9，draft review 时逐条重核 live 代码。）

- **样板重复 ×4**：`JavaScopeResolver`/`JavaMetricsResolver`/`JavaDataflowResolver`/`JavaSemanticResolver`——`CACHE_SIZE=32`、匿名 `LinkedHashMap` LRU（`removeEldestEntry` 一字不差 ×3）、`new JavaParser(ParserConfiguration…JAVA_17)`、"did not parse into a compilation unit" orElseThrow、`LineColBytes`+`JavaNodeIndex` 装配、`line+1/col+1` 位置换算（JavaScopeResolver 与 JavaDataflowResolver 两份）、`isAvailable(){return true;}` ×4；`JavaTypeResolver.parseUnit` 与 `JavaSemanticResolver.unitFor` 是同型装配的第二份。
- **C9（异常翻译缺失）**：`JavaSemanticResolver`（:62-64 `creation.getType().resolve()` 无 catch）与 `SemanticAnalyzer`（:170-175 主动 `throw new UnsolvedSymbolException`）——两者 javadoc 均宣称 solver 失败由 resolver 层翻译为 `NopLintException`，L4 路径无翻译；L2（`JavaTypeResolver.tryResolve`）有完整翻译层，不一致且违背 AGENTS.md 两级异常约定。
- **M6-C（JavaTypeResolver 缓存）**：`filesByPath`/`queryCache` 均 `HashMap`——无界、无同步（兄弟类全 synchronized）、`queryCache` 存 `"true"/"false"` 字符串再 parseBoolean；LSP 长驻场景每新文件/新位置永久累积；`typeSolver` 字段从未使用（:56，`parseUnit` 另建 solver）。
- **M7-C（JavaDataflowResolver 键控粒度）**：`methodsByPath` 键为 `filePath+"@"+line`（:85）——同一文件 50 个查询位置 = 50 次完整 read+parse+index；32 项 LRU 内一个文件占多个槽位；`JavaMetricsResolver` 已示范按 filePath 缓存的正确形态；`queries` 字段未使用（每缓存条目 `new DataflowQueries()`，唯一该共享的无状态对象）。
- **重复查询**：`DataflowQueries.constantValue` 每查询构建两遍 `DefUseChain`（:33-41 与 :125-127）；`minimalAt` 在 `ScopeAnalyzer:332-345` 与 `DataflowQueries:137-153` 各有一份 O(N) 全树扫描，而 `JavaNodeIndex:76-107` 已有 O(depth) 的 `minimalContaining`。
- **死代码**：`JavaTypeResolver.typeSolver`、`JavaSemanticResolver.analyzer`、`JavaDataflowResolver.queries` 三处未用字段。
- 测试防线：nop-lint-java 101 测试绿；TestL2DegradeLadder/TestJavaTypeResolver/TestScopeAnalyzer/TestMetricsEvaluator/TestSemanticAnalyzer/TestDataflowQueries/TestDataFlowAnalyzer 家族。

## Goals

- 包私有 `ParsedUnitCache`（或等名组件）收敛四处样板：线程安全有界 LRU + parse + index 装配 + 位置换算，四个 resolver 各瘦身约 40 行；行为零变化。
- 缓存语义统一为"32 项 LRU + synchronized"（对齐兄弟类既有形态）：JavaTypeResolver 双缓存并入；JavaDataflowResolver 键控改 filePath（每文件一次解析，方法定位走 index/父链）。
- L4 异常翻译层：`JavaSemanticResolver` 三入口 catch RuntimeException 翻译为 `NopLintException`（对齐 `tryResolve` 模式）；`erasedQualifiedName` 改抛 `NopLintException`。
- `constantValue` 单次构建 DefUseChain 两处共享；两份 `minimalAt` 删除改用 `JavaNodeIndex.minimalContaining`。
- 三处死字段删除。

## Non-Goals

- 不改 `TypeResolver`/`MetricsResolver`/`ScopeResolver`/`SemanticResolver`/`DataflowResolver` SPI 接口签名与 ServiceLoader 发现面。
- 不改 L2-L4 门控/降级语义（永不伪造红线）。
- 不做项目级 classpath 模型（`initProject` 语义维持 v1 单文件模型）。
- 不动 DefUseChain 遮蔽判定（plan 11 已收口）。

## Scope

### In Scope

- `nop-lint-java/src/main/java/io/nop/lint/java/semantic/`：新增 ParsedUnitCache（包私有）；JavaTypeResolver/JavaScopeResolver/JavaMetricsResolver/JavaDataflowResolver/JavaSemanticResolver/SemanticAnalyzer/DataflowQueries/ScopeAnalyzer/ConstantPropagation 的收敛改动。
- 焦点测试：翻译层三态、LRU 容量行为、dataflow 同文件多位置单解析。

### Out Of Scope

- nop-lint-core 全部；规则 YAML；bench。

## Execution Plan

### Phase 1 - 异常翻译与缓存对齐（Fix）

Status: planned
Targets: `nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/semantic/JavaSemanticResolver.java`、`SemanticAnalyzer.java`、`JavaTypeResolver.java`

- Item Types: `Fix`

- [ ] `JavaSemanticResolver` 入口统一 catch 翻译（对齐 `JavaTypeResolver.tryResolve` 模式）：`UnsolvedSymbolException`/`NoSuchElementException` 等运行时失败 → `NopLintException`（英文消息含文件与位置）；`SemanticAnalyzer.erasedQualifiedName` 改抛 `NopLintException`
- [ ] `JavaTypeResolver` 缓存对齐兄弟形态：`synchronized` + 32 项 LRU（`filesByPath`/`queryCache`）；**queryCache 拆两个（审查 B12-2）**：assignability 缓存（Boolean 值）与 typeNameAt 缓存（String 类型名）——现混存于同一 Map 的两种值类型不可合并；未用 `typeSolver` 字段删除
- [ ] 焦点测试：solver 不可解符号 → `NopLintException`（非裸 UnsolvedSymbolException 穿透）；LRU 超 32 项淘汰最老（可构造 33 文件/位置场景断言）；既有 TestL2DegradeLadder/TestJavaTypeResolver 零回归

Exit Criteria:

- [ ] L4 路径无裸 `UnsolvedSymbolException` 穿透（翻译测试红转绿）；javadoc 契约与实现一致
- [ ] JavaTypeResolver 缓存有界且同步（代码可观察）；全模块测试零回归
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - ParsedUnitCache 收敛 + DataflowResolver 键控修正（Fix）

Status: planned
Targets: `nop-lint/nop-lint-java/src/main/java/io/nop/lint/java/semantic/`（新 ParsedUnitCache + 五 resolver）

- Item Types: `Fix`

- [ ] 新增包私有 `ParsedUnitCache`：**三解析形态（R1 B12-1 修正）**——plain（JAVA_17，Scope/Metrics/Dataflow）、solver-Reflection-only（Type，保持"项目类型不可解→降级"的 L2 契约）、solver-Reflection+ClassLoader（Semantic，L4 可答面）——共享 LRU(32) 机制与装配/换算，**parser 配置按消费者参数化且互不混用**（统一为任一 solver 形态都会静默改变 L2 降级或 L4 可答面）；parse-orElseThrow 统一英文消息按用途后缀参数化；`JavaSemanticResolver.unitFor`/`JavaTypeResolver.parseUnit` 并入或委托（parseUnit 被 3 个测试类直接调用，委托形态必须保留）
- [ ] `JavaDataflowResolver` 缓存键改 `filePath`（缓存解析产物 + index），方法定位改经 `JavaNodeIndex.minimalContaining` + 父链上行（删除手写 greedy descent 与 `covers`——注意现 covers 有 0-based/1-based 半格偏差，只因方法粒度大而无害；重写后行为零变化**限定到方法定位粒度**）；`DataflowQueries` 实例随缓存条目共享
- [ ] `minimalAt` 删除改调 `JavaNodeIndex.minimalContaining`：**需把 index/byte 位置穿入 ScopeAnalyzer/DataflowQueries 公开签名（审查 M12-3）**——两 resolver 缓存条目补 index 字段，TestScopeAnalyzer/TestDataflowQueries 直测签名随之适配（声明于本 Phase 测试项）
- [ ] `DataflowQueries.constantValue` 两遍 DefUseChain 构建合并为一次共享；`ScopeAnalyzer`/`DataflowQueries` 两份 `minimalAt` 删除改调 `JavaNodeIndex.minimalContaining`
- [ ] 三处死字段（typeSolver/analyzer/queries）删除（typeSolver 随 Phase 1；此处核剩余）
- [ ] **自 plan 14 移入（M14-3，同模块同批文件）**：DataflowQueries 4 处 IAE、ScopeAnalyzer:320、LineColBytes:52 → `NopLintException` 统一；DefUseChain:85 newSetFromMap 内联 FQN、JavaSemanticResolver:109-113 内联 symbolsolver FQN 归 import 区
- [ ] 焦点测试（**观测缝本身是新增件**——包内计数缝可行）：同一文件 3 个不同位置查询只触发一次 parse（计数断言）；LRU 32 项淘汰断言（第 33 文件挤掉最老）；四 resolver 行为零变化
- [ ] TestScopeAnalyzer/TestDataflowQueries 签名适配落地（index/byte 位置穿入后的直测形态）

Exit Criteria:

- [ ] 样板收敛后四 resolver 无重复 LRU/parse/换算定义（代码结构可观察）；单文件多位置单解析测试落地
- [ ] java 模块异常类型统一与 FQN 清理落地（移入项）；nop-lint-java 全量测试零回归；`./mvnw test -pl nop-lint/nop-lint-java` 绿
- [ ] `No owner-doc update required`
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

> 只有本 section 所有条目以及每个 Phase 的 Exit Criteria 全部勾选为 `[x]` 后，才能将 `Plan Status` 改为 `completed`。

- [ ] C9 与 D1 全部子项修复/收敛落地（异常翻译、缓存对齐、键控修正、样板收敛、重复查询消除、死字段）
- [ ] 行为零漂移（SPI 契约、门控语义、全量既有测试背书）
- [ ] owner docs：`No owner-doc update required`（逐 Phase 裁定）
- [ ] 独立子 agent closure-audit 已完成并记录证据
- [ ] Anti-Hollow Check：ParsedUnitCache 被四 resolver 真实消费（非平行新类）；翻译层在 L4 求值链上真实生效（xscript semantic 绑定 → resolver → 翻译）
- [ ] `./mvnw test -pl nop-lint/nop-lint-java -am` 全绿
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 退出 0
- [ ] `node ai-dev/tools/scan-hollow-implementations.mjs --module nop-lint-java --severity high` 退出 0
- [ ] `node ai-dev/tools/check-plan-checklist.mjs <本文件> --strict` 退出 0

## Non-Blocking Follow-ups

- 缓存文件 mtime 失效策略（文件变更后同实例内陈旧答案）——LSP 接线时随宿主生命周期裁定。
- findVar/ScopeAnalyzer.resolve 实现合并（plan 11 follow-up 承接）。

## Closure

Status Note: （关闭时填写）
Completed:

Closure Audit Evidence:

- Reviewer / Agent:
- Evidence:

Follow-up:

- （关闭时填写或写 no remaining plan-owned work）
