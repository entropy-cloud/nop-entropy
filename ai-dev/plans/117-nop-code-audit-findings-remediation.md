# 117 nop-code 审计发现修复（2026-05-05/05-10 两轮审计收口）

> Plan Status: in progress
> Last Reviewed: 2026-06-06
> Source: `ai-dev/audits/nop-code-audit-2026-05-05.md`, `ai-dev/audits/nop-code-audit-2026-05-10.md`, live code verification (2026-06-06)
> Related: Plans 88–95（all completed）, Plan 116（completed, data model enhancement）

## Purpose

收口 2026-05-05 和 2026-05-10 两轮审计中经 live code 验证仍 outstanding 的发现。Plans 88–95 已修复绝大多数 P0/P1 项，Plan 116 补齐了数据模型增强。本轮修复剩余可落地的高优先级项，将纯架构演进项（P3）标记为 deferred。

## Current Baseline

### 已修复（Plans 88–95 + 116 覆盖）

- 双存储架构断裂 → 已统一为 DB 持久化 + CodeCacheManager 读缓存
- SOURCE_CODE/IMPORTS 未写入 → saveFileResultInSession 已写入
- InMemoryFingerprintStore → 已默认使用 OrmFingerprintStore
- Java Record 支持 → JavaFileAnalyzer 已支持 JAVA_21 + RecordDeclaration
- instanceof + cast → 已移除，全部通过接口委托
- RuntimeException → 已替换为 NopException + ErrorCode
- Graph 算法溢出 → CommunityDetector 已使用 long 算术
- 依赖图持久化测试 → TestDependencyPersistence 已存在
- 并发安全测试 → TestConcurrentIndexing 已存在
- EdgeProvenance/Metadata → 所有边表已有 PROVENANCE 列（Plan 116）
- Symbol filePath/language 反规范化 → 已落地（Plan 116）
- Spring 路由提取 → 已实现（Plan 116）
- 启发式边合成引擎 → InterfaceImplSynthesizer + SpringEventSynthesizer（Plan 116）

### 仍 Outstanding（经 2026-06-06 live code 验证）

**可落地修复项（本计划 scope）**：

1. **P1-6 (05-05 residual)**：ORM 设计遗漏——`code/call_type` 字典被 ORM 引用但从未定义；NopCodeIndex 实体无 createTime/updateTime 审计字段
2. **P1-4 (05-05 residual)**：CodeCacheManager 使用插入序 LinkedHashMap（非 LRU），无 TTL 驱逐。缓存条目仅在超过 MAX_CACHE_ENTRIES=20 时按插入顺序驱逐
3. **P0-1/P1-5 (narrowed)**：nop-code-api 模块为空壳——迁移纯 DTO（不迁移 ICodeIndexService，因它大量依赖 nop-code-core/nop-code-flow 模型类）
4. **P1-6 (05-10)**：E2E 测试缺口——已有 19+ 测试文件覆盖索引/层级/依赖等，但缺少图分析（社区检测、影响分析、关键节点）和流分析（执行流、变更分析、死代码）的端到端测试

**P3 架构演进项（deferred）**：

5. **P0-7/P0-3 (residual)**：Web 页面使用文本树（input-tree）而非交互式图可视化。功能可用但用户体验差
6. **P0-1/P1-5 (interface migration)**：ICodeIndexService 迁移需先重构接口签名（移除对 core/flow 模型类的直接依赖），属跨模块 API 重设计
7. **P1-5/P2-2**：类型系统仅有字符串表示 + 外部符号引用不可索引——需引入结构化类型模型和外部符号注册表，属长期架构演进
8. **P2-3 (05-10)**：Python/TypeScript import 解析器仅处理基础模式——需 tsconfig paths、node_modules 解析，属多语言支持演进
9. **P1-3 (05-10 residual)**：ProjectAnalyzer 全量累积 fileResults——虽有 BatchQueue 优化符号注册，但文件级结果仍全量驻留内存。需重构为流式持久化回调
10. **callType 字段规范化**：JavaFileAnalyzer 对普通方法写入返回类型描述（如 "void"），对构造函数写入 "CONSTRUCTOR"——语义不一致，需统一为调用类型枚举

## Goals

1. 补全 ORM 缺失的 `code/call_type` 字典定义（匹配实际代码写入的值）
2. 为 NopCodeIndex 实体补充 createTime/updateTime 审计字段
3. 将 CodeCacheManager 改为 LRU 驱逐 + TTL 支持
4. 为 nop-code-api 模块补充核心 DTO（不迁移 ICodeIndexService——见 Non-Goals）
5. 补充现有测试未覆盖的关键流程测试

## Non-Goals

- **不迁移 ICodeIndexService 到 nop-code-api**：该接口 import `nop-code-core`（CodeFileAnalysisResult、CodeSymbol 等 6 个类）和 `nop-code-flow`（ExecutionFlow、ChangeAnalysisResult、DeadCodeReport）。迁移它将迫使 nop-code-api 依赖 nop-code-core + nop-code-flow + 传递依赖 nop-code-graph，使 api 模块失去轻量性。正确做法需先重构接口方法签名（改用 DTO 替代 core 模型），这属于跨模块 API 重设计，需独立 design doc → deferred
- 不引入图可视化库（vis.js/Cytoscape.js）——前端功能独立规划
- 不实现结构化类型系统——P3 架构演进，需独立 design doc
- 不实现外部符号引用注册表——P3 架构演进
- 不重构 Python/TS import 解析器——P2 中期
- 不重构 ProjectAnalyzer 为流式持久化——需改公共 API 签名
- 不为 nop-code-dao 添加测试——DAO 层由 ORM + service 测试间接覆盖
- 不规范化 callType 字段——当前 `callType` 对普通方法存储返回类型描述（如 "void"），对构造函数存储 "CONSTRUCTOR"，语义不一致。规范化需改 JavaFileAnalyzer 写入逻辑和所有读取方，属 P2 数据质量专项

## Scope

### In Scope

- ORM 补全（dict 定义、审计字段）
- CodeCacheManager LRU + TTL
- nop-code-api 模块补充纯 DTO（不迁移接口）
- 关键流程测试补充

### Out Of Scope

- ICodeIndexService 迁移 → 需先重构接口签名，独立 successor plan
- callType 字段规范化 → P2 数据质量专项
- 图可视化前端 → 独立前端计划
- 结构化类型系统 → P3 successor plan
- 外部符号引用 → P3 successor plan
- Python/TS import 增强 → P2 successor plan
- ProjectAnalyzer 流式持久化 → P2 successor plan

## Execution Plan

### Phase 1 — ORM 补全（dict + 审计字段）

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`

- Item Types: `Fix`

- [x] **补全 `code/call_type` 字典定义**。在 ORM 的 `<dicts>` 段添加 `code/call_type` 字典。**注意**：当前 `JavaFileAnalyzer` 对 `callType` 写入两类值：(1) 构造函数调用写入 `"CONSTRUCTOR"`；(2) 普通方法调用写入返回类型描述（如 `"void"`、`"String"`）。字典定义需覆盖实际使用的值。首期字典包含 `CONSTRUCTOR`（构造调用），其余值为自由文本（返回类型描述），字典仅用于 xmeta 显示和未来规范化参考
- [x] **为 NopCodeIndex 添加审计字段**。在 NopCodeIndex `<columns>` 段增加 `CREATED_TIME`（domain=createTime）和 `UPDATE_TIME`（domain=updateTime）。**已知 residual**：其他实体（NopCodeFile、NopCodeSymbol、NopCodeCall 等）同样缺少审计字段，但本计划仅修复 NopCodeIndex（主表、最常查询），其余留作 follow-up

Exit Criteria:

- [x] `code/call_type` 字典在 `nop-code.orm.xml` 的 `<dicts>` 段中有定义，至少包含 `CONSTRUCTOR` 值
- [x] NopCodeIndex 实体有 `createTime` 和 `updateTime` 列
- [x] `./mvnw install -pl nop-code -am -DskipTests` 通过（触发代码生成）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No new test required: ORM model changes verified by codegen + compile + existing tests
- [x] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — CodeCacheManager LRU + TTL

Status: completed
Targets: `nop-code/nop-code-service/src/main/java/io/nop/code/service/impl/CodeCacheManager.java`

- Item Types: `Fix`

- [x] **将 `analysisCacheMap`（`LinkedHashMap`，插入序）替换为 access-order LRU**。使用 `LinkedHashMap(initialCapacity, loadFactor, true)` 构造 access-order 模式，或引入 Caffeine（需先确认 nop-code 依赖树中已有 Caffeine；若无，使用 access-order LinkedHashMap + 手动 TTL 包装，避免引入新依赖）
- [x] **添加 TTL 支持**。为每个缓存条目包装 `CacheEntry<T>`（含 `value` + `lastAccessTime`），在 `getOrRebuildXxx` 方法入口检查 TTL。添加 `CACHE_TTL_MS` 常量（默认 3600000 = 60 分钟），超时条目在访问时惰性驱逐并重建
- [x] **添加单元测试**：验证 LRU 驱逐顺序（访问最久未用的条目被先驱逐）、TTL 过期行为（超时条目被重建）、MAX_CACHE_ENTRIES 上限

Exit Criteria:

- [x] `CodeCacheManager` 的 `analysisCacheMap` 使用 access-order 迭代（最久未访问的条目排在前面，先被驱逐）
- [x] 缓存条目有 TTL 过期机制（基于 `lastAccessTime` + `CACHE_TTL_MS`）
- [x] 新增测试验证 LRU 和 TTL 行为
- [x] `./mvnw test -pl nop-code/nop-code-service -am` 通过
- [x] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — nop-code-api 模块补充 DTO

Status: planned
Targets: `nop-code/nop-code-api/`, `nop-code/nop-code-service/`

- Item Types: `Fix`

**设计决策**：`ICodeIndexService` 暂不迁移（它依赖 `nop-code-core` 6 个模型类 + `nop-code-flow` 3 个模型类，迁移会迫使 api 模块变成重型依赖）。仅迁移与 `ICodeIndexService` 方法签名无直接依赖的纯 DTO——这些 DTO 仅依赖 `nop-api-core`（已通过 pom 依赖链可用）。

- [ ] **识别可迁移的 DTO**。从 `nop-code-service/api/dto/` 中筛选仅依赖 `nop-api-core` 的 DTO（如 `SymbolDTO`、`DepGraphDTO`、`IndexStatsDTO` 等）。跳过依赖 core/flow 模型的 DTO
- [ ] **迁移筛选出的 DTO 到 nop-code-api**。移动到 `nop-code-api/src/main/java/io/nop/code/api/dto/`
- [ ] **更新 nop-code-api 的 pom.xml**。添加对 `nop-api-core` 的依赖（如尚未有）
- [ ] **更新 nop-code-service 的 import**。全局替换被迁移 DTO 的 import 路径

Exit Criteria:

- [ ] `nop-code-api/src/main/java/` 目录存在，包含至少 3 个迁移的 DTO
- [ ] `nop-code-api/pom.xml` 不依赖 nop-code-service、nop-code-dao、nop-code-core、nop-code-flow
- [ ] `./mvnw compile -pl nop-code -am` 通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No owner-doc update required（内部模块结构调整，不改公共 API 契约）
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 补充测试缺口

Status: planned
Targets: `nop-code/nop-code-service/src/test/java/io/nop/code/service/`, `nop-code/nop-code-graph/src/test/java/io/nop/code/graph/`

- Item Types: `Proof`

**已知测试覆盖（不重复）**：
- 索引→搜索→层级→调用链→依赖图：`TestNopCodeIndexBizModel`（GraphQL 端到端）、`TestNopCodeHierarchyQueries`（层级查询）、`TestDependencyPersistence`（依赖持久化）、`TestConcurrentIndexing`（并发）
- 图算法：`TestCommunityDetector`（社区检测算法）、`TestImpactAnalyzer`（影响分析算法）、`TestCriticalNodeAnalyzer`（关键节点算法）

**缺失的端到端测试**：
- [ ] **图分析端到端测试**（在 `nop-code-service` 中）：索引一个小型项目 → 调用 `detectCommunities()` → 验证返回社区列表非空。同理覆盖 `getCriticalNodes()` 和 `getKnowledgeGaps()`
- [ ] **流分析端到端测试**（在 `nop-code-service` 中）：索引一个小型项目 → 调用 `detectFlows()` → 验证返回执行流列表非空。同理覆盖 `analyzeChanges()` 和 `detectDeadCode()`

使用 AutoTest 内存数据库，复用现有 test-resources 中的 Java 文件。

Exit Criteria:

- [ ] 新增测试类覆盖图分析端到端（社区检测 + 关键节点 + 知识缺口）
- [ ] 新增测试类覆盖流分析端到端（执行流检测 + 变更分析 + 死代码检测）
- [ ] 测试可在 CI 中运行（无 @Disabled，无外部依赖）
- [ ] `./mvnw test -pl nop-code/nop-code-service -am` 通过
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] `code/call_type` 字典已定义（至少包含 CONSTRUCTOR）
- [ ] NopCodeIndex 有 createTime/updateTime
- [ ] CodeCacheManager 使用 access-order LRU + TTL
- [ ] nop-code-api 有至少 3 个迁移的 DTO（不依赖 core/flow/graph）
- [ ] 图分析和流分析端到端测试存在且在 CI 中通过
- [ ] 无 in-scope live defect 被降级到 deferred/follow-up
- [ ] 无空壳实现或静默跳过的新增代码
- [ ] `./mvnw compile -pl nop-code -am` 通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### ICodeIndexService 迁移到 nop-code-api

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: ICodeIndexService import nop-code-core（6 个模型类）+ nop-code-flow（3 个模型类）。迁移需先重构接口签名改用 DTO，属跨模块 API 重设计。当前 DTO 迁移是安全的首步
- Successor Required: `yes`
- Successor Path: 待创建（需先有 design doc 定义 DTO-first 接口设计）

### callType 字段规范化

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: callType 对普通方法存储返回类型描述（非调用类型），语义不一致但不影响现有功能。规范化需改写入逻辑和所有读取方
- Successor Required: `no`

### P1-5/P2-2 结构化类型系统 + 外部符号引用

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需独立 design doc 定义类型模型架构。当前字符串表示对已有功能（搜索、层级、调用链）无影响，仅限制未来类型感知分析能力
- Successor Required: `yes`
- Successor Path: 待创建

### P2-3 Python/TS import 解析器增强

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 多语言支持演进项。当前基础 import 解析已覆盖主流程，tsconfig paths/node_modules 解析属功能扩展
- Successor Required: `no`

### P1-3 ProjectAnalyzer 流式持久化

- Classification: `optimization candidate`
- Why Not Blocking Closure: BatchQueue 已优化符号注册批次，persistInSession 已有 flush+evict。全量 fileResults 累积仅在超大项目（1000+ 文件）时有 OOM 风险，当前用户场景不触及
- Successor Required: `no`

### P0-7/P0-3 图可视化前端

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 需引入前端库（vis.js/Cytoscape.js），属独立前端功能开发。当前 input-tree 文本展示功能可用，仅用户体验待改进
- Successor Required: `yes`
- Successor Path: 待创建（前端专项计划）

### NopCodeFile/NopCodeSymbol 等实体审计字段

- Classification: `optimization candidate`
- Why Not Blocking Closure: ORM root 有 useStdFields="true"，但只有 Flow/FlowMembership/SemanticEdge/Index 有显式审计字段。其他实体缺少 createTime/updateTime 但不影响功能正确性
- Successor Required: `no`

## Non-Blocking Follow-ups

- nop-code-dao 测试覆盖（DAO 层由 ORM + service 测试间接覆盖）
- TestIndexNopEntropyProject 移除 @Disabled 或改为 CI profile
- CodeIndexService God Object 进一步拆分（Plan 95 Deferred successor）

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<独立审阅者或独立子 agent>>
- Evidence: <<task id / daily log link / findings 摘要>>

Follow-up:

- <<只记录 non-blocking follow-up；confirmed live defect 不得出现在这里>>
