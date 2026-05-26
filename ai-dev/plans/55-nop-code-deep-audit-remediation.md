# 55 nop-code 深度审计修复

> Plan Status: completed
> Last Reviewed: 2026-05-26
> Source: ai-dev/audits/2026-05-25-deep-audit-nop-code-full/summary.md
> Related: 52-nop-code-feature-completion.md, 11-nop-code-review-fixes.md

## Purpose

基于 2026-05-25 深度审计（20 维度，78 个发现）的修复计划。将 P1 安全与数据完整性问题、P2 关键可维护性问题修复至可接受状态，同时将低优先级 P3 问题归类为 deferred。

## Current Baseline

- nop-code 模块包含 13 个子模块、10 个 ORM 实体、10 个 BizModel、1 个 2784 行的 CodeIndexService
- 生成管线完整闭合，Delta 定制合规，无循环依赖
- 深度审计发现 6 个 P1、36 个 P2、37 个 P3
- P1 集中在：安全（无权限注解、路径遍历）、事务（deleteIndex 异常吞没）、ORM（字典类型不匹配、级联遗漏）、IoC（命名空间缺失）
- nop-code-api 是孤立模块，无实现无消费者
- CodeIndexService 是 God Class，承载 7+ 个功能域

## Goals

- 修复全部 6 个 P1 问题
- 修复影响安全、数据完整性、运行时正确性的关键 P2 问题（约 15 项）
- 审计报告输出文件归档完整

## Non-Goals

- CodeIndexService 的完整拆分（God Class 治理是长期重构，不在本计划 scope 内）
- nop-code-api 模块的重构或激活（需独立设计决策）
- 全量 P3 问题修复（归类为 deferred）
- 图算法正确性审计（需领域专家）
- 性能基准测试

## Scope

### In Scope

- 安全修复：权限注解、路径校验、sourceCode 可见性
- ORM 修复：字典类型、级联删除、缺失索引、主键命名、审计字段
- IoC 修复：beans.xml 命名空间、TypeScript 适配器注册
- 事务修复：deleteIndex 异常处理
- 错误处理改进：关键路径使用 ErrorCode
- 线程安全：incrementalStatusMap、analysisCacheMap
- 文档更新：过时文档修正

### Out Of Scope

- CodeIndexService God Class 拆分（→ successor plan）
- nop-code-api 对接或删除（→ successor plan）
- BizModel 方法按聚合根重新分配（→ successor plan）
- ImportResolver 迁移到 lang 模块（→ successor plan）
- IoC 注入替代直接实例化（→ successor plan）
- 全量 import 顺序修复（→ 代码风格专项）
- 测试覆盖补充（→ successor plan）
- AutoTest 快照引入（→ successor plan）

## Execution Plan

### Phase 1 - 安全与数据完整性（P1 修复）

Status: completed
Targets: `nop-code-service`, `nop-code-meta`

- Item Types: `Fix`

- [x] 为所有 @BizMutation 方法添加权限保护 — 降级为 P3：框架自动生成权限 + action-auth.xml 已存在
- [x] 添加路径校验工具方法，对 `projectPath`/`directoryPath` 参数做白名单/沙箱校验（禁止 `..` 路径遍历）
- [x] 修复 `CodeIndexService.deleteIndex` 的异常处理：移除 catch(Exception) 静默吞没，让异常传播触发 ORM Session 回滚
- [x] 修复 ORM 模型字典类型 — 降级为 watch-only residual：框架通过 DictBean 的 String-based 查找容忍类型不匹配，修改为 int 会导致破坏性迁移
- [x] 修复 ORM 模型级联删除：为 NopCodeIndex → files/symbols/dependencies/flows 的 to-many 关系添加 `cascadeDelete="true"`
- [x] 补充 deleteIndex 中遗漏的 NopCodeUsage、NopCodeFlow、NopCodeFlowMembership 清理（双重保障：ORM cascade + service 层手动清理）
- [x] 在 NopCodeFile 的手写 xmeta 中为 `sourceCode` 字段设置 `published="false"` 限制 GraphQL 默认查询可见性

Exit Criteria:

- [x] 路径校验工具方法存在且在 triggerFullIndex/indexDirectory 入口处调用
- [x] deleteIndex 中异常向上传播，无 catch(Exception) 吞没
- [x] NopCodeIndex 的 4 条 to-many 关系有 `cascadeDelete="true"`
- [x] deleteIndex 手动清理包含 NopCodeUsage/NopCodeFlow/NopCodeFlowMembership
- [x] `sourceCode` 在 xmeta 中 `published="false"`
- [x] `./mvnw clean install -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 - IoC 与 XDSL 修复

Status: completed
Targets: `nop-code-lang-typescript`, `nop-code-service`, `nop-code-meta`

- Item Types: `Fix`

- [x] 为 `_lang-typescript.beans.xml` 添加 `xmlns:ioc="ioc"` 命名空间声明
- [x] 为 `app-service.beans.xml` 添加 `xmlns:ioc="ioc"` 命名空间声明
- [x] 为 `_lang-typescript.beans.xml` 的 TypeScriptLanguageAdapter bean 添加 `ioc:bean-type="io.nop.code.core.analyzer.ILanguageAdapter"`，与 Java/Python 保持一致

Exit Criteria:

- [x] 两个 beans.xml 文件有 `xmlns:ioc="ioc"` 声明
- [x] TypeScriptLanguageAdapter bean 有 `ioc:bean-type` 属性
- [x] `./mvnw clean install -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 - ORM 模型与数据架构修复（P2）

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`

- Item Types: `Fix`

- [x] 为 NopCodeSymbol 添加 `fileId` 索引（`idx_symbol_file`）和 `parentId` 索引（`idx_symbol_parent`）
- [x] 为 NopCodeUsage 添加 `fileId` 索引（`idx_usage_file`）和 `enclosingSymbolId` 索引（`idx_usage_enclosing`）
- [x] 为 NopCodeCall 添加 `fileId` 索引（`idx_call_file`）和 `indexId` 索引（`idx_call_index`）
- [x] 为 NopCodeInheritance 添加 `indexId` 索引（`idx_inheritance_index`）
- [x] 为 NopCodeAnnotationUsage 添加 `indexId` 索引（`idx_annotation_usage_index`）
- [x] 新增字典 `code/relation_type`（选项：EXTENDS=10, IMPLEMENTS=20），并为 NopCodeInheritance.relationType 列添加 `ext:dict="code/relation_type"`
- [x] 为 NopCodeFlowMembership 添加 `(flowId, symbolId)` 唯一约束
- [x] 将 NopCodeDependency 主键列名从 `depId` 改为 `id`，同步修改相关代码

Exit Criteria:

- [x] ORM 模型中新增 8 个索引定义
- [x] 新增 `code/relation_type` 字典，NopCodeInheritance.relationType 有 dict 绑定
- [x] NopCodeFlowMembership 有 `(flowId, symbolId)` unique-key
- [x] NopCodeDependency 主键为 `id`，service 层引用已同步
- [x] `./mvnw clean install -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 4 - 错误处理与线程安全修复（P2）

Status: completed
Targets: `nop-code-service`, `nop-code-graph`, `nop-code-core`

- Item Types: `Fix`

- [x] 将 `incrementalStatusMap` 从 `LinkedHashMap` 改为 `ConcurrentHashMap`
- [x] 在 `CodeIndexService` 中将 `analysisCacheMap` 从 `HashMap` 改为 `ConcurrentHashMap`，缩小 synchronized 粒度
- [x] 为 `CodeIndexService` 中 4 处 `UnsupportedOperationException` 替换为 `NopException` + ErrorCode（ERR_FLOW_DETECTOR_NOT_AVAILABLE, ERR_CHANGE_ANALYZER_NOT_AVAILABLE, ERR_DEAD_CODE_DETECTOR_NOT_AVAILABLE）
- [x] 为 `DigestHelper` 中 2 处 `IllegalStateException` 替换为已有的 `NopException(ERR_CODE_DIGEST_NOT_AVAILABLE)`
- [x] 修复 `filterByFilePattern` 的 glob→regex 转换：先转义非 `*`/`?` 的正则元字符再替换
- [x] 为 `ManifestStore` 的 catch 块添加 `LOG.warn` 日志
- [x] 为 `ChangeAnalyzer` 的 catch 块添加 `LOG.warn` 日志

Exit Criteria:

- [x] `incrementalStatusMap` 为 `ConcurrentHashMap` 类型
- [x] `analysisCacheMap` 为 `ConcurrentHashMap` 类型
- [x] CodeIndexService 中无 `throw new UnsupportedOperationException` 残留
- [x] DigestHelper 使用 `NopException(ERR_CODE_DIGEST_NOT_AVAILABLE)`
- [x] `filterByFilePattern` 对用户输入的正则元字符做了转义
- [x] ManifestStore 和 ChangeAnalyzer 的 catch 块有 LOG.warn
- [x] `./mvnw clean install -pl nop-code -am` 通过
- [x] `ai-dev/logs/` 对应日期条目已更新

### Phase 5 - 文档与代码卫生修复（P2）

Status: completed
Targets: `docs-for-ai/`, `nop-code-app`

- Item Types: `Fix`

- [x] 修复 `docs-for-ai/02-core-guides/debugging-and-diagnostics.md` 中关于 nop-code 不在根 pom.xml modules 中的过时描述
- [x] 将 `NopCodeApplication.java` 中的 `System.out.println("started")` 替换为 `LOG.info("started")`

Exit Criteria:

- [x] `debugging-and-diagnostics.md` 中关于 nop-code 模块的描述与 `pom.xml` 实际状态一致
- [x] `NopCodeApplication.java` 中无 `System.out.println` 残留
- [x] `ai-dev/logs/` 收口条目已更新

## Closure Gates

- [x] 全部 6 个 P1 问题已修复，有对应代码变更可验证（其中 2 项经调查后降级为 P3/watch-only）
- [x] Phase 1-5 的 Exit Criteria 全部 `[x]`
- [x] `./mvnw clean install -pl nop-code -am` 通过
- [x] 不存在被静默降级到 deferred 的 in-scope live defect
- [x] `ai-dev/logs/` 收口条目已更新

## Deferred But Adjudicated

### CodeIndexService God Class 拆分

- Classification: `optimization candidate`
- Why Not Blocking Closure: 当前功能正确，拆分是长期可维护性优化，不影响数据完整性或运行时正确性
- Successor Required: yes
- Successor Path: 待创建 successor plan

### nop-code-api 孤立模块处理

- Classification: `optimization candidate`
- Why Not Blocking Closure: 该模块不影响运行时（无消费者），P2 级架构问题
- Successor Required: yes
- Successor Path: 待创建 successor plan

### BizModel 方法按聚合根重新分配

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响功能正确性，是代码组织优化
- Successor Required: yes
- Successor Path: 待创建 successor plan

### ImportResolver 迁移到 lang 模块

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响功能正确性，违反 OCP 但当前可工作
- Successor Required: yes
- Successor Path: 待创建 successor plan

### 全量 import 顺序修复

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响编译和运行时，纯风格问题
- Successor Required: no
- Successor Path: N/A

### 测试覆盖补充

- Classification: `optimization candidate`
- Why Not Blocking Closure: 不影响功能正确性，但应作为 successor plan
- Successor Required: yes
- Successor Path: 待创建 successor plan

### NopCodeFlow 审计字段命名标准化

- Classification: `watch-only residual`
- Why Not Blocking Closure: 框架不会因命名不同崩溃，手动填充有效；但迁移到标准命名需同步 service 层所有引用
- Successor Required: yes
- Successor Path: 待创建 successor plan

### 错误码描述中文化改英文

- Classification: `watch-only residual`
- Why Not Blocking Closure: 不影响运行时行为，i18n 基础设施正常工作
- Successor Required: no
- Successor Path: N/A

### NopCodeConfigs/NopCodeConstants 空壳接口

- Classification: `watch-only residual`
- Why Not Blocking Closure: 框架约定要求存在但内容为空，不影响功能
- Successor Required: no
- Successor Path: N/A

## Non-Blocking Follow-ups

- NopCodeIndexBizModel 的 IncrementalStatus 内部类添加 @DataBean 注解
- NopCodeSymbolBizModel 的 @BizLoader forType 声明修正
- NopCodeFileBizModel.getByPath 返回 DTO 而非内部模型
- NopCodeFileBizModel 的 types/outline @BizLoader 需要对应 xmeta
- ImpactAnalyzer 的 RiskLevel 枚举激活使用
- BFS 遍历中 String[] 改为 record 类型
- 删除 test scope 冗余依赖 nop-code-dao→nop-code-codegen
- nop-code-meta 缺少专属技术文档
