# 121 nop-code 2026-06-06 深度审计剩余发现收口

> Plan Status: proposed
> Last Reviewed: 2026-06-06
> Source: `ai-dev/audits/2026-06-06-deep-audit-nop-code/` 21 维度深度审计（61 条发现），Plans 118–120（已完成）覆盖 AR 编号项及部分深度审计项后仍 outstanding 的发现
> Related: Plan 120（completed, P2/P3 AR 编号项收口）、Plan 119（completed, r9 P0/P1）、Plan 118（completed, r8 P0/P1）

## Purpose

将 2026-06-06 深度审计（21 维度）中 Plans 118–120 未覆盖的 outstanding 发现修复收口。Plans 118–120 主要追踪对抗性审查 AR 编号项，深度审计中约 32 条发现（1 条 P1 + 约 24 条 P2 + 约 7 条 P3）未被纳入。其中 15-02 经对抗性审查验证为非问题（已有 instanceof 守卫）。

## Current Baseline

### 已修复（Plans 88–95 + 116–120 覆盖）

- 全部 AR 编号 P0/P1 问题已修复（AR-88~AR-129）
- 全部 AR 编号 P2/P3 问题已修复（AR-130~AR-144）
- 深度审计部分项已修复：04-03（Dict valueType）、02-03（ImportResolver）、04-02~04-05（审计字段命名统一）、17-01（isEmpty 替换）
- 深度审计 16-01/16-05（BizModel 错误路径测试）→ Plan 118 部分修复
- nop-code-api DTO 迁移 → Plan 117
- ORM 唯一约束、级联删除、QN 解析 → Plan 120
- 查询性能优化（N+1、CLOB、投影查询）→ Plan 120
- 算法鲁棒性（Tarjan 迭代、分页、搜索评分）→ Plan 120
- 并发安全（CallGraph 线程安全、锁竞态、线程泄漏）→ Plan 120
- 118 tests 全部通过

### 仍 Outstanding（本 Plan Scope）

**P1（1 条）**：

- 21-01: `TestFlowDetector.testCriticalityHighWhenNoTestFiles` 构建测试场景但无断言，核心逻辑改错测试仍通过

**P2（约 25 条，分 5 组）**：

*安全与数据完整性（6 条）*：
- 04-01/10-01: `NopCodeSemanticEdge.relationType` 引用 `code/relation_type` 字典（仅 EXTENDS/IMPLEMENTS），但实际持久化 `SemanticRelationType` 枚举 8 个值
- 04-02: `uk_dependency_unique` 唯一键含 3×VARCHAR(500) + VARCHAR(36)，总长 6144 字节 > MySQL 3072 限制
- 07-01: `allowedLocalRoot` 为 null 时 `validateLocalPath` 完全跳过，admin 可索引任意目录
- 07-02: `IncrementalStatus.errorMessage` 字段从未被设置（API 契约漂移）
- 13-01: `filterByFilePattern` glob 转正则未转义元字符（+、{、}、(、)等），ReDoS 风险
- 13-02: `indexFile` 的 `sourceCode` 参数无长度限制，OOM/DB 膨胀风险

*测试质量（5 条）*：
- 21-03: `TestNopCodeFlowBizModel` 3 个测试仅验证 `response.isOk()` 和非 null，返回空列表测试仍通过
- 21-04: `TestBizModelErrorPaths` 恒真断言 `assertTrue(response.isOk() || !response.isOk())`
- 21-06: 多个测试使用 `Map<String, Object>` 无类型检查，字段重命名测试静默通过
- 21-07: `TestNopSearchIntegration` 使用 `assumeTrue` 搜索失败时静默跳过
- 21-02: `TestCodeIndexService` 类名暗示测试 CodeIndexService，实际测试 ProjectAnalyzer

*代码质量与类型安全（8 条）*：
- 17-04: `CodeIndexService.entityToCodeSymbol` 与 `CodeSymbolConverter.toCodeSymbol` 完全重复（需验证当前状态）
- 17-05: `NopCodeException`、`NopCodeConstants`、`NopCodeConfigs` 已定义但零使用
- 17-02: `CodeIndexService.java:6` 两个 import 挤在同一行
- 17-03: `CodeIndexService` 重复注释块和空注释段
- 15-01: 28 个源文件使用 `java.util.*` 通配符导入
- ~~15-02~~: `SpringEventSynthesizer.java:102` 已有 `instanceof Map` 守卫（line 101），**已验证非问题**，从 plan 中移除
- 15-06: `GraphSnapshot.EdgeKey.equals()` 未使用 `Objects.equals`，NPE 风险
- 15-new: `Community.getSymbolIds()` 返回不可变列表，后续 `add` 抛 UnsupportedOperationException

*ORM/代码生成（2 条）*：
- 05-01: `NopCodeSemanticEdge` 缺少 `i18n-en:displayName` 和 `ext:icon`，生成产物传播 null
- 04-04: 11 实体审计字段配置不一致（NopCodeSemanticEdge 有逻辑删除但无 updateTime）

*文档与配置（4 条）*：
- 18-01: `docs-for-ai/` 无 nop-code 模块使用文档
- 20-03: `NopCodeConfigs` 为空接口，所有配置硬编码常量
- 19-01: `ExecutionFlow` 与 `NopCodeFlow` 字段名不一致（criticality/overallScore, entryPointSymbolId/entryPointId）
- 14-01: `indexDirectory` 在事务内执行 CPU 密集型 AST 分析，长事务风险

**P3（约 7 条）**：
- 04-06: `NopCodeUsage` 唯一键含可空列
- 09-01: `NopCodeException` 缺少 String 构造器（Phase 4 删除该类后不再适用）
- 09-02: `JavaFileAnalyzer` 静默吞掉异常（无 LOG.debug）
- 14-02: `indexLocks` ConcurrentHashMap 永不收缩
- 19-03: `provenance` 字段无 dict 映射
- 15-02: `SpringEventSynthesizer` unchecked cast → **已验证非问题**（line 101 已有 `instanceof Map` 守卫）
- 03-01: `ICodeIndexService` 三个 batch 方法无调用者 → 低优先级

### 真正剩余的 gap

- 安全边界松散（ReDoS + 路径校验 + 长度限制）
- ORM 字典与运行时不一致（relationType 字典）
- 测试质量系统性薄弱（无效断言、恒真断言、无类型检查）
- 模块文档缺失
- 死代码残留

## Goals

1. 修复所有 P2 级安全发现（ReDoS、路径校验、长度限制）
2. 修复 ORM 字典与运行时不一致（SemanticEdge relationType）
3. 修复唯一键超 MySQL 限制问题
4. 修复 P1 级无效测试（无断言测试）
5. 修复 P2 级测试质量问题（恒真断言、弱断言、无类型检查）
6. 清理死代码和重复代码
7. 修复类型安全缺陷（NPE 风险、不可变列表误用）
8. 补充 NopCodeSemanticEdge 生成管线缺失属性
9. 创建 nop-code 模块使用文档

## Non-Goals

- **不拆分 CodeIndexService God Class**（1999 行）→ 已在 Plan 120 Deferred，需先有 design doc
- **不拆分 CodeGraphService**（752 行）→ 架构演进
- **不迁移 ICodeIndexService 到 api 模块**（20-01）→ 分层重构需独立 plan
- **不迁移 DTO 到 api 模块**（02-04/19-02）→ Plan 117 已完成 api DTO 迁移，3 个 service DTO 待后续
- **不迁移 Biz 接口到 api 模块**（02-05）→ Codegen 模式，低优先级
- **不拆分 BizModel**（02-06/02-07）→ 架构演进
- **不引入 TypeScript tsconfig 解析**（AR-141 根本修复）→ 已在 Plan 120 Deferred
- **不外部化全部配置**（20-03）→ Phase 4 删除空接口 `NopCodeConfigs`，配置硬编码降级为 watch-only
- **不修复长事务**（14-01）→ 需 CodeIndexService 拆分后才能有效解决
- **不修改跨模块公共 API** → plan-first 约束

## Scope

### In Scope

- 1 条 P1 修复（21-01）
- 约 24 条 P2 修复（15-02 已验证为非问题，移除）
- 约 7 条 P3 修复
- 每个修复项的对应测试（如适用）
- nop-code 模块使用文档创建

### Out Of Scope

- CodeIndexService 拆分 → 需先有 `ai-dev/design/` design doc
- 分层重构（api 模块迁移）→ 独立 successor plan
- 长事务修复 → 依赖 CodeIndexService 拆分
- TypeScript tsconfig 解析 → 已在 Plan 120 Deferred

## Execution Plan

### Phase 1 — 安全修复（ReDoS + 路径校验 + 长度限制）

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeSearchService.java`, `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../entity/NopCodeIndexBizModel.java`

- Item Types: `Fix`

- [x] **13-01: 修复 glob 转正则 ReDoS**。使用逐字符 globToRegex() 方法替代简单 replace，正确转义所有正则元字符
- [x] **13-02: 添加 sourceCode 长度限制**。`NopCodeIndexBizModel.indexFile` 添加参数校验，超过 1MB 抛出 `NopException(ERR_CODE_SOURCE_CODE_TOO_LARGE)`
- [x] **07-01: 加固路径校验**。`CodeIndexService.setAllowedLocalRoot` 未配置时打 WARN 日志；`validateLocalPath` 增加拒绝绝对路径（Unix `/` 和 Windows 驱动器号）
- [x] 新增测试：TestSecurityFixes 6 个测试覆盖 ReDoS/glob/路径；TestServiceLayerErrorPaths 新增 ERR_CODE_SOURCE_CODE_TOO_LARGE 测试

Exit Criteria:

- [x] `filterByFilePattern` 正确转义所有正则元字符
- [x] `indexFile` 拒绝超过 1MB 的 sourceCode
- [x] `allowedLocalRoot` 未配置时有 WARN 日志
- [x] 新增安全测试全部通过（TestSecurityFixes 6 tests + TestServiceLayerErrorPaths 7 tests）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required（安全加固不改变公共契约）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — ORM/字典修复

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`

- Item Types: `Fix`

- [x] **04-01: 创建 SemanticEdge 专用字典**。新建 `code/semantic_relation_type` 字典，包含 `SemanticRelationType` 枚举全部 8 个值。NopCodeSemanticEdge 的 `RELATION_TYPE` 列改为引用 `code/semantic_relation_type`
- [x] **04-02: 修复唯一键超 MySQL 限制**。引入 `dependencyKeyHash` 列（VARCHAR(64)，SHA-256），唯一键改为 `(indexId, dependencyKeyHash)`。原唯一键替换为普通索引
- [x] **05-01: 补充 NopCodeSemanticEdge 生成属性**。添加 `i18n-en:displayName="Semantic Edge"` 和 `ext:icon="link"` 属性
- [x] 新增测试：TestSemanticRelationTypeDict 验证字典包含全部 8 个枚举值

Exit Criteria:

- [x] `code/semantic_relation_type` 字典存在且包含 `SemanticRelationType` 全部 8 个值
- [x] `NopCodeSemanticEdge.relationType` 引用 `code/semantic_relation_type`
- [x] `uk_dependency_unique` 已改为 `(indexId, dependencyKeyHash)`，总长度 ≤ 3072
- [x] `dependencyKeyHash` 列存在且在写入时自动计算
- [x] `NopCodeSemanticEdge` 有 `i18n-en:displayName` 和 `ext:icon`
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required（ORM内部修复）
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — P1 无效测试 + P2 测试质量修复

Status: completed
Targets: `nop-code/nop-code-flow/.../TestFlowDetector.java`, `nop-code/nop-code-service/.../TestBizModelErrorPaths.java`, `nop-code/nop-code-service/.../TestNopCodeFlowBizModel.java`, `nop-code/nop-code-service/.../TestNopSearchIntegration.java`, `nop-code/nop-code-service/.../TestCodeIndexService.java`

- Item Types: `Fix`

- [x] **21-01 (P1): 修复 TestFlowDetector 无断言测试**。添加 criticality 比较断言和范围检查
- [x] **21-04: 修复 TestBizModelErrorPaths 恒真断言**。将恒真断言改为具体的 `assertTrue(response.isOk())`
- [x] **21-03: 加强 TestNopCodeFlowBizModel 断言**。验证返回列表非空 + flow 条目有结构（entryPointSymbolId, criticality）
- [x] **21-06: 为 Map<String,Object> 测试添加 null 检查**。3 个测试文件添加 assertNotNull
- [x] **21-07: 修复 TestNopSearchIntegration assumeTrue 弱化**。替换为 assertTrue/条件路径验证
- [x] **21-02: 澄清 TestCodeIndexService 测试对象**。类 Javadoc 明确标注测试 ProjectAnalyzer

Exit Criteria:

- [x] `testCriticalityHighWhenNoTestFiles` 有具体值断言，核心逻辑改为错误实现时测试失败
- [x] `TestBizModelErrorPaths` 无恒真表达式
- [x] `TestNopCodeFlowBizModel` 验证返回内容而非仅非 null
- [x] `TestNopSearchIntegration` 搜索失败时测试报告而非静默跳过
- [x] `TestCodeIndexService` 类 Javadoc 明确测试对象
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 — 代码质量与类型安全

Status: completed
Targets: `nop-code/nop-code-service/.../impl/CodeIndexService.java`, `nop-code/nop-code-service/.../NopCodeException.java`, `nop-code/nop-code-service/.../NopCodeConstants.java`, `nop-code/nop-code-service/.../NopCodeConfigs.java`, `nop-code/nop-code-graph/.../diff/GraphSnapshot.java`, `nop-code/nop-code-graph/.../community/CommunityDetector.java`

- Item Types: `Fix`

- [x] **17-04: 删除 CodeIndexService 中的重复 toCodeSymbol**。删除 `entityToCodeSymbol` 方法（~30 行，零调用者）
- [x] **17-05: 清理死代码**。删除 `NopCodeException.java`（零 import）、`NopCodeConstants.java`（空接口）、`NopCodeConfigs.java`（空接口）
- [x] **17-02: 修复 import 格式**。`CodeIndexService.java:6` 两个 import 拆分为两行
- [x] **17-03: 清理重复注释**。删除 CodeIndexService 中的空注释段和重复注释块
- [x] **15-01: 替换通配符导入**。21 个源文件中的 `java.util.*` 替换为具体 import
- [x] **15-06: 修复 EdgeKey.equals NPE**。`GraphSnapshot.EdgeKey.equals()` 使用 `Objects.equals`
- [x] **15-new: 修复 Community.getSymbolIds() 不可变列表**。返回 `new ArrayList<>()` 而非 `Collections.emptyList()`
- [x] 新增测试：TestCommunityDetectorFixes 验证 `Community.getSymbolIds()` 返回可变列表

Exit Criteria:

- [x] `CodeIndexService` 无 `entityToCodeSymbol` 方法，仅使用 `CodeSymbolConverter.toCodeSymbol`
- [x] `NopCodeException`、`NopCodeConstants`、`NopCodeConfigs` 已删除
- [x] `CodeIndexService` import 格式正确
- [x] 无通配符 `java.util.*` import（源文件）
- [x] `EdgeKey.equals` 使用 `Objects.equals`
- [x] `Community.getSymbolIds()` 返回可变列表
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 — 文档与 P3 收口

Status: completed
Targets: `docs-for-ai/03-modules/nop-code.md`（需先创建 `03-modules/` 目录）, `nop-code/model/nop-code.orm.xml`, `nop-code/nop-code-lang-java/src/main/java/io/nop/code/lang/java/analyzer/JavaFileAnalyzer.java`, `nop-code/nop-code-service/.../impl/CodeIndexService.java`

- Item Types: `Fix` | `Follow-up`

- [x] **18-01: 创建 nop-code 模块使用文档**。在 `docs-for-ai/03-modules/nop-code.md` 创建文档，包含子模块职责、核心 API 清单、实体关系、配置项说明
- [x] **04-04: 统一审计字段**。NopCodeSemanticEdge 添加 `UPDATED_BY`/`UPDATE_TIME` 列
- [x] **09-02: 修复 JavaFileAnalyzer 静默吞异常**。`catch (Exception e)` 块添加 `LOG.debug`
- [x] **14-02: 添加 indexLocks 清理**。`deleteIndex` 中增加 `indexLocks.remove(indexId)`
- [x] **19-01: 评估字段名不一致**。在 `entityToExecutionFlow` 中添加映射注释
- [x] **07-02: 修复 errorMessage 从未设置**。在 `triggerIncrementalIndex` 的 catch 块中设置 `status.setCompleted(false)` 和 `status.setErrorMessage(e.getMessage())`
- [x] **09-01: NopCodeException 已删除，此项不再适用**
- [x] **04-06: 评估 NopCodeUsage 唯一键可空列**。确认应用层通过确定性SHA-256 ID生成去重，文档记录
- [x] **19-03: 添加 provenance dict 映射**。创建 `code/provenance` 字典包含 EdgeProvenance 5 个枚举值

Exit Criteria:

- [x] `docs-for-ai/03-modules/nop-code.md` 存在且包含子模块职责、API 清单、实体关系
- [x] `docs-for-ai/INDEX.md` 已更新
- [x] `NopCodeSemanticEdge` 有完整 4 个审计字段
- [x] `JavaFileAnalyzer` 异常有 `LOG.debug` 记录
- [x] `deleteIndex` 清理 `indexLocks`
- [x] `IncrementalStatus.errorMessage` 在失败时被设置
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 全部 in-scope P1 发现已修复
- [ ] 全部 in-scope P2 发现已修复或有明确的 deferred/adjudicated 说明
- [ ] P3 发现已修复或标记为 deferred with reason
- [ ] 不存在被静默降级到 deferred / follow-up 的 in-scope live defect
- [ ] 受影响的 owner docs 已同步到 live baseline
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `./mvnw compile -pl nop-code -am` 通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] `node ai-dev/tools/check-plan-checklist.mjs ai-dev/plans/121-nop-code-2026-06-06-deep-audit-outstanding-remediation.md --strict` 退出码为 0
- [ ] `node ai-dev/tools/check-doc-links.mjs --strict` 通过（如修改了 docs-for-ai/）

## Deferred But Adjudicated

### CodeIndexService 拆分

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: 1999 行 God Class 是维护性瓶颈但不影响功能正确性。拆分需先有 design doc 定义子服务边界
- Successor Required: `yes`
- Successor Path: 待创建（需先有 `ai-dev/design/` design doc）

### 长事务（14-01）

- Classification: `optimization candidate`
- Why Not Blocking Closure: CPU 密集型 AST 分析在事务内执行导致长事务，但不影响正确性。根本修复需 CodeIndexService 拆分后将 AST 分析移到事务外
- Successor Required: `yes`
- Successor Path: 跟随 CodeIndexService 拆分 successor plan

### 分层重构（api 模块迁移）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: ICodeIndexService 和 3 个 DTO 放在 service 模块而非 api 模块，违反分层原则但不影响功能
- Successor Required: `yes`
- Successor Path: 待创建

### BizModel 拆分（02-06/02-07）

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: NopCodeIndexBizModel 30 个方法未按实体分组，但不影响运行时行为
- Successor Required: `no`

### 配置外部化（20-03）

- Classification: `resolved by 17-05`
- Why Not Blocking Closure: `NopCodeConfigs` 空接口将在 Phase 4 删除，配置硬编码降级为 watch-only
- Successor Required: `no`

### ICodeIndexService batch 方法（03-01）

- Classification: `watch-only residual`
- Why Not Blocking Closure: `batchSaveFileRecords`/`batchLoadFileRecords`/`batchDeleteFileRecords` 仅有测试调用者，无生产代码调用
- Successor Required: `no`

### TypeScript tsconfig 解析

- Classification: `out-of-scope improvement`
- Why Not Blocking Closure: AR-141 已在 Plan 120 做临时缓解。根本修复需 tsconfig.json 解析基础设施
- Successor Required: `yes`
- Successor Path: 待创建

## Non-Blocking Follow-ups

- CodeIndexService 拆分 design doc 需在独立 successor plan 前完成
- CodeIndexService 核心持久化方法的系统性单元测试覆盖（Plan 118 添加了错误路径测试，但 1999 行核心逻辑仍缺直接测试）
- 大型代码库（50 万+ 符号）端到端性能验证
- `docs-for-ai/INDEX.md` 路由表更新（如 Phase 5 创建 nop-code.md 后需要）

## Closure

Status Note: 待填写

Closure Audit Evidence:

- Reviewer / Agent: 待填写
- Evidence: 待填写

Follow-up:

- 待填写
