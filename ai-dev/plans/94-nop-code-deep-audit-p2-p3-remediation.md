# 94 nop-code Deep Audit P2/P3 遗留修复

> Plan Status: in progress
> Last Reviewed: 2026-06-02
> Source: `ai-dev/audits/2026-05-31-deep-audit-nop-code/`（维度 04/07/08/09）+ `ai-dev/audits/2026-06-02-adversarial-review-nop-code/summary.md`（AR-88~AR-93 已由 Plan 93 修复）
> Related: Plans 88–93（all completed）

## Purpose

修复 2026-05-31 深度审计 21 维度中经 live code 验证仍 outstanding 的 P2/P3 findings。Plans 88–93 已修复所有 P0 和 P1 项。本轮收口剩余 P2/P3 项，使 nop-code 审计发现收口。

## Current Baseline

### 已修复（Plans 88–93 覆盖）

所有 P0（3 项）、P1（22 项）和大部分 P2 已在 Plans 88–93 中修复：

- 安全：13-01（@Auth 全覆盖）、13-02（路径白名单 toRealPath）、13-03（Git ref 验证）
- ORM：04-01（refPropName）、04-02（dict 值）、04-03（valueType=string）、04-04（cascadeDelete）
- BizModel：07-01/11-01（forType，同一问题的两个视角）
- IoC：08-01（VFS 索引已含 lang beans）
- 错误处理：09-01/09-02（英文消息）、09-03（IllegalArgumentException→NopException）、09-04（GraphExporter 内联 ErrorCode 已移至 NopCodeCoreErrors）、09-05（.param() 上下文）、09-06（ChangeAnalyzer silent catch→LOG.warn）、09-07（FlowDetector silent catch 已消除）
- 性能：14-01（分页删除）、14-02（per-indexId ReentrantLock 替代全局 synchronized）
- 对抗性审查：AR-63~AR-93 全部修复
- 测试有效性：21-01~21-04（CallGraph 去重/不可变测试、ChangeAnalyzer 测试、GraphDiffer 基础测试）

### 仍 Outstanding（经 live code 验证）

**P2（5 项）**：

- 04-05：`nop-code/model/nop-code.orm.xml:829` NopCodeFlowMembership 使用 `name="createdTime"`，而 NopCodeFlow/NopCodeSemanticEdge 使用 `createTime`，命名不一致（`updateTime` 在 L833 已经正确，只需修 `createdTime`→`createTime`）
- 04-06：`nop-code/model/nop-code.orm.xml:896-897` NopCodeSemanticEdge 有 `delFlag` 列但实体无 `useLogicalDelete="true" deleteFlagProp="delFlag"`
- 04-07：`nop-code/model/nop-code.orm.xml` NopCodeIndex 实体（L93-173）无 `<indexes>` 段，`status`/`rootPath`/`language`/`lastIndexed` 无数据库索引
- 07-03（residual）：`NopCodeIndexBizModel.java:336-343` `evictStatusMap()` 使用 `Iterator.remove()` on `ConcurrentHashMap`，与 AR-91 同模式（已修复的无限循环 bug 的残留实例）
- 08-02/08-04：`CodeIndexService.java:168-171` 手动 `new LanguageAdapterRegistry()` + 3 行 `registerAdapter()` 注册语言适配器，与 IoC beans 文件（`_lang-java.beans.xml`、`_lang-typescript.beans.xml`、`_lang-python.beans.xml`）形成双重注册；`app-service.beans.xml` 未 import lang beans。`LanguageAdapterRegistry` 当前仅有 `registerAdapter(ILanguageAdapter)` 方法，无 `setAdapters(List<ILanguageAdapter>)`

**P3（2 项）**：

- 08-05：`_lang-typescript.beans.xml` 缺少 `xsi:schemaLocation`（其他 lang beans 文件有）
- 08-06：lang 模块（`nop-code-lang-java`/`nop-code-lang-typescript`/`nop-code-lang-python`）在 `_vfs/nop/code/` 下缺少 `_module` 文件（其他 nop-code 子模块均有）

**已验证修复，从 scope 移除**：

- 09-03（GraphExporter IllegalArgumentException→NopException）：已修复，`export()` switch default 抛 NopException
- 09-04（GraphExporter 内联 ErrorCode）：已修复，已引用 `NopCodeCoreErrors.ERR_GRAPH_EXPORT_FAILED`
- 14-02（全局 synchronized→per-indexId Lock）：已修复，`indexLocks` ConcurrentHashMap + ReentrantLock
- 11-01（XMeta forType 缺失）：同 07-01，已修复

## Goals

1. 修复 ORM 模型不一致（审计字段命名、logical delete 配置、数据库索引）
2. 修复 residual 并发模式（evictStatusMap）
3. 统一 IoC 注册方式（消除手动注册与 IoC beans 的双重性）
4. 修复静默异常处理
5. 修复 IoC 配置一致性

## Non-Goals

- 不拆分 CodeIndexService（Plan 93 Deferred，独立 successor plan）
- 不做 entityToCodeSymbol DRY 治理（Plan 91 Deferred 07-03）
- 不做 Tarjan 递归→迭代（AR-69 P3 watch-only residual）
- 不做 i18n 英文翻译（ORM displayName/label，不影响运行时行为）
- 不做 @RequestBean 重构（07-07 P3）
- 不做 pass-through @BizLoader 清理（07-06 P3）
- 不做 ORM P3 琐项：04-08（SemanticEdge 缺 comment）、04-10（callType/relationType 缺 dict）、04-11（resolved 字段类型）、07-02（IncrementalStatus 缺 @DataBean）、09-08（SymbolBizModel 静默枚举过滤）、09-09（CommunityDetector 空 catch 已有注释）
- 不做 ProjectAnalyzer IOException→NopException（09-10 P2，需改公共 API 签名，影响调用方）

## Scope

### In Scope

- 5 个 P2 修复（ORM 3 + 并发 1 + IoC 1）
- 3 个 P3 修复（IoC 配置 2 + 异常日志 1）

### Out Of Scope

- CodeIndexService 拆分 → successor plan
- i18n / DRY / 模块结构优化
- Tarjan 递归→迭代
- 07-04 手动级联删除冗余（deferred，见 Deferred But Adjudicated）

## Execution Plan

### Phase 1 — ORM 模型修复

Status: completed
Targets: `nop-code/model/nop-code.orm.xml`

- Item Types: `Fix`

- [x] **04-05：统一审计字段命名**。将 NopCodeFlowMembership 的 `name="createdTime"` 改为 `name="createTime"`，与 NopCodeFlow/NopCodeSemanticEdge 一致。**注意**：ORM 模型修改后需 `./mvnw install` 以触发代码生成管线，更新实体 Java 类的 getter/setter 名和 `_app.orm.xml`
- [x] **04-06：添加 useLogicalDelete**。为 NopCodeSemanticEdge 实体添加 `useLogicalDelete="true" deleteFlagProp="delFlag"`
- [x] **04-07：添加 NopCodeIndex 数据库索引**。为常用查询字段添加索引：`idx_nop_code_index_status`（status）、`idx_nop_code_index_root_path`（rootPath）、`idx_nop_code_index_language`（language）、`idx_nop_code_index_last_indexed`（lastIndexed）

Exit Criteria:

> Phase 1 completed: commit a68a49eb7

- [x] NopCodeFlowMembership 审计字段 `createdTime` 改为 `createTime`（与 NopCodeFlow、NopCodeSemanticEdge 一致。`updateTime` 已经正确无需修改）
- [x] NopCodeSemanticEdge 实体声明 `useLogicalDelete="true" deleteFlagProp="delFlag"`
- [x] NopCodeIndex 实体有 `<indexes>` 段，覆盖 `status`、`rootPath`、`language`、`lastIndexed`
- [x] `./mvnw install -pl nop-code -am -DskipTests` 通过（触发代码生成）
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] 无 Java 代码引用旧属性名 `createdTime`（grep 确认，NopCodeFlowMembership 实体已由代码生成更新）
- [x] No new test required: ORM model changes verified by codegen + compile + existing tests
- [x] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 2 — Residual 并发 + IoC 统一

Status: completed
Targets: `NopCodeIndexBizModel.java`, `CodeIndexService.java`, `LanguageAdapterRegistry.java`, `app-service.beans.xml`, lang beans files

- Item Types: `Fix`

- [x] **07-03：修复 evictStatusMap**。`NopCodeIndexBizModel.java` 的 `evictStatusMap()` 替换为 CHM-safe 模式
- [x] **08-02/08-04：统一 IoC 注册**。在 `app-service.beans.xml` 中 import 三个 lang beans；`LanguageAdapterRegistry` 注册为 IoC bean；`CodeIndexService` 改用 `@Inject` setter 注入；通过 `BeanContainer.getBeansOfType()` 发现适配器
- [x] **ExtDataHelper silent catch**。3 处静默 JSON 解析 catch 添加 `LOG.debug`。注：09-06（ChangeAnalyzer）和 09-07（FlowDetector）已在前序计划中修复；ExtDataHelper 是同类残留

Exit Criteria:

> Phase 2 completed: commit 508ea3c05

- [x] `evictStatusMap()` 无 `Iterator.remove()` 调用，使用 CHM-safe 驱逐模式
- [x] `CodeIndexService` 构造函数无手动 `new LanguageAdapterRegistry()` 和 `registerAdapter()` 调用
- [x] `app-service.beans.xml` import `_lang-java.beans.xml`、`_lang-typescript.beans.xml`、`_lang-python.beans.xml`
- [x] `LanguageAdapterRegistry` 在 beans.xml 中注册为 IoC bean，通过 `BeanContainer.getBeansOfType()` 收集适配器
- [x] `ExtDataHelper` 有 `Logger` 字段，所有 catch 块有 DEBUG 级别日志输出
- [x] `./mvnw compile -pl nop-code -am` 通过
- [x] `./mvnw test -pl nop-code -am` 通过
- [x] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

### Phase 3 — P3 IoC 配置一致性

Status: planned
Targets: `_lang-typescript.beans.xml`, lang 模块 `_module` 文件

- Item Types: `Fix`

- [ ] **08-05：补充 xsi:schemaLocation**。为 `_lang-typescript.beans.xml`（路径：`nop-code-lang-typescript/src/main/resources/_vfs/nop/code/beans/_lang-typescript.beans.xml`）添加 `xmlns:xsi` 和 `xsi:schemaLocation`，与 `_lang-java.beans.xml` 和 `_lang-python.beans.xml` 一致
- [ ] **08-06：添加 _module 文件**。为 3 个 lang 模块在各自 `_vfs/nop/code/` 下添加空 `_module` 文件：`nop-code-lang-java/src/main/resources/_vfs/nop/code/_module`、`nop-code-lang-typescript/src/main/resources/_vfs/nop/code/_module`、`nop-code-lang-python/src/main/resources/_vfs/nop/code/_module`

Exit Criteria:

- [ ] `_lang-typescript.beans.xml` 有 `xsi:schemaLocation` 属性（与 `_lang-java.beans.xml` 结构一致）
- [ ] 3 个 lang 模块各自 VFS 目录下有 `_module` 文件
- [ ] `./mvnw compile -pl nop-code -am` 通过
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] No new test required: configuration file consistency changes
- [ ] No owner-doc update required
- [ ] `ai-dev/logs/` 对应日期条目已更新

## Closure Gates

- [ ] 5 个 P2 全部修复（ORM 3 + 并发 1 + IoC 1）
- [ ] 3 个 P3 全部修复（IoC 配置 2 + 异常日志 1，已合入 Phase 2/3）
- [ ] 无 in-scope live defect 被降级到 deferred/follow-up
- [ ] 无空壳实现或静默跳过的新增代码
- [ ] `./mvnw install -pl nop-code -am -DskipTests` 通过（含代码生成）
- [ ] `./mvnw test -pl nop-code -am` 通过
- [ ] checkstyle / 代码规范检查通过
- [ ] 独立子 agent closure audit 已完成并记录证据
- [ ] `ai-dev/logs/` 收口记录已更新

## Deferred But Adjudicated

### 07-04 Manual cascade delete duplicating ORM cascadeDelete

- Classification: `watch-only residual`
- Why Not Blocking Closure: ORM cascade 已正确配置（`cascadeDelete="true"`），手动删除在 cascade 之前执行起到防御性作用。虽然冗余，但不影响数据完整性。移除手动删除需验证所有 cascadeDelete 路径在 Nop ORM 引擎中的行为，属于重构范畴
- Successor Required: no

### 07-06 Pass-through @BizLoader

- Classification: `optimization candidate`
- Why Not Blocking Closure: 运行时功能正确，仅有微量冗余开销
- Successor Required: no

### 07-07 @RequestBean refactoring

- Classification: `optimization candidate`
- Why Not Blocking Closure: 6 参数方法可维护但不够优雅，非功能性问题
- Successor Required: no

### CodeIndexService God Object

- Classification: `optimization candidate`
- Why Not Blocking Closure: Plan 92/93 已拆分至 ~1631 行（46% 缩减），功能正确。进一步拆分需独立评估
- Successor Required: yes
- Successor Path: 独立 successor plan（编号待定）

## Non-Blocking Follow-ups

- entityToCodeSymbol DRY 治理（Plan 91 Deferred 07-03）
- nop-code-api 模块结构重组（Plan 92 Deferred F-29/F-30）
- Tarjan 递归→迭代（AR-69 P3 watch-only residual）
- i18n ORM displayName 英文翻译（P3，不影响运行时）

## Closure

Status Note: <<完成或关闭时填写>>

Closure Audit Evidence:

- Reviewer / Agent: <<待填写>>
- Evidence: <<待填写>>

Follow-up:

- <<待填写>>
