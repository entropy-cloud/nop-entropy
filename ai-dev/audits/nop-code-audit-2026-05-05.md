# Nop-Code 审计报告 — 2026-05-05

> 首次系统性审计。基于 5 个并行 explore/librarian agent 对 nop-code 全模块的深度分析。
> 审计维度定义见 `ai-dev/skills/nop-code-audit-prompt.md`。

## 审计快照

- **模块规模**：12 个子模块，129 个 Java 文件，约 17,600 行代码
- **审计日期**：2026-05-05

---

## P0 — 架构断裂

### P0-1: 双存储架构断裂

**问题描述**：nop-code 存在两套完全独立的数据存储，互不连通。

```
设计文档预期的架构：
  ProjectAnalyzer → CodeIndexService → DAO(ORM) → Database
                                        ↓
                                  GraphQL/RPC API

实际实现的架构：
  ProjectAnalyzer → CodeIndexService → ConcurrentHashMap（纯内存）
                                        ↓
                                  GraphQL/RPC API

  DAO(ORM) → Database（完全孤立，从未被写入或读取）
```

**具体证据**：
- `CodeIndexService` 用 4 个 `ConcurrentHashMap` 存储所有数据
- 7 个 ORM 实体（`NopCodeIndex`, `NopCodeSymbol`, `NopCodeFile`, `NopCodeUsage`, `NopCodeCall`, `NopCodeInheritance`, `NopCodeAnnotationUsage`）有完整的表结构、xmeta、关系定义
- 但 `CodeIndexService` 中无任何一处引用这些 ORM 实体
- 应用重启后，所有索引数据丢失

**影响**：无法持久化索引结果，重启后需全量重建索引，ORM 实体和 xmeta 成为死代码。

**改进方向**（三选一）：

| 方案 | 描述 | 适用场景 |
|------|------|---------|
| A. 启用持久化 | CodeIndexService 分析完成后写入 ORM，查询走数据库 | 需要长期存储、重启恢复 |
| B. 移除 ORM | 删除 dao/meta/codegen 模块，简化为纯内存架构 | 仅用于临时分析、demo |
| C. 混合模式 | 热数据内存、冷数据持久化，类似缓存层 | 兼顾性能与持久化 |

### P0-2: code-browser 页面不可用

**问题**：queryForm 用了 `editMode="query"`（会加 filter_ 前缀），无 cells，无结果展示。

### P0-3: 类型层级和调用链页面缺少结果可视化

**问题**：表单正常、API 正确，但提交后无结果展示区域。

---

## P1 — 代码质量

### P1-1: 核心模型泄漏到 API 层

**问题**：BizModel 方法直接返回 `nop-code-core` 的内部模型类（`CodeSymbol`, `CodeFileAnalysisResult`, `CodeAnnotationUsage`），而非 DTO。

**影响**：API 消费者与内部实现强耦合，修改 core 层模型会破坏 API 契约。

**建议**：创建 DTO（如 `CodeSymbolDTO`），所有 GraphQL/RPC 返回值使用 DTO，参考 `TypeHierarchyDTO`/`CallHierarchyDTO`/`ImpactResultDTO` 的正确模式。

### P1-2: 接口抽象被绕过

**问题**：`NopCodeIndexBizModel` 中存在 `instanceof` + 强转代码：

```java
private CallGraph getCallGraph(String indexId) {
    if (codeIndexService instanceof CodeIndexService) {
        return ((CodeIndexService) codeIndexService).getCallGraph(indexId);
    }
    return null;
}
```

`ICodeIndexService` 接口缺少 `detectCommunities`、`getGraphAnalysis`、`getImpactAnalysis` 方法。

**建议**：补全接口方法，移除 `instanceof` + 强转。

### P1-3: 错误处理不符合规范

**违规位置**：

| 文件 | 当前写法 | 应改为 |
|------|---------|-------|
| CodeIndexService | `throw new RuntimeException("Failed to index...")` | `NopException(ERR_INDEX_FAILED)` |
| NopCodeIndexBizModel | `throw new RuntimeException("Incremental indexing requires...")` | `NopException(ERR_INCREMENTAL_NOT_SUPPORTED)` |
| NopCodeIndexBizModel | `throw new RuntimeException("Incremental index failed: ...")` | `NopException(ERR_INCREMENTAL_FAILED).cause(e)` |

### P1-4: 内存管理 — 数据冗余与无驱逐

**数据冗余**：`CodeIndexService` 用 4 个 Map 存储同一份数据的不同视图：

```
fileResultsMap    ──┐
symbolTableMap   ──┤── 实际都来自同一个 ProjectAnalysisResult
callGraphMap     ──┤
analysisResultsMap ──┘ （已包含前三者的全部数据）
```

**无驱逐机制**：索引数据只增不减，无 LRU/TTL/大小限制，多个大项目索引可导致 OOM。

**源码常驻内存**：`CodeFileAnalysisResult.sourceCode` 存储完整文件内容，分析完成后未释放。

**建议**：移除冗余 Map，使用 Caffeine 缓存，分析完成后清理 `sourceCode`。

### P1-5: api 模块为空

**问题**：`nop-code-api` 模块存在但完全为空。`ICodeIndexService` 和所有 DTO 放在 `nop-code-service/api/` 下。

**建议**：将接口和 DTO 迁移到 `nop-code-api`，或删除空模块。

### P1-6: ORM 实体设计问题

- 配置了 `ext:useStdFields="true"` 但实体均无 createTime/updateTime 等审计字段
- 设计文档预期多表（Class/Interface/Enum/Method/Field/Constructor 各自独立），实际合并为单一 `NopCodeSymbol` 表
- `NopCodeCall.callType` 和 `NopCodeInheritance.relationType` 缺少 dict 定义

---

## P2 — 能力缺口

### P2-1: Java 解析缺失

| 缺失特性 | 说明 |
|----------|------|
| Record 类 | `JavaFileAnalyzer` 无 `RecordDeclaration` 处理器 |
| module-info.java | 无模块声明处理 |
| sealed class / pattern matching | 未检查 |

### P2-2: 类型系统缺失

所有类型信息以字符串存储（`returnType="List<String>"`），无法程序化解析泛型参数、追踪类型继承关系。

**建议**：创建 `CodeTypeReference` 模型。

### P2-3: 图算法潜在问题

- `CommunityDetector` (Leiden)：边去重哈希可能溢出（`minIdx * maxIdx`）
- `ImpactAnalyzer`：BFS 用 `String[]` 存深度，效率低

### P2-4: 缺失的前端页面

| 缺失页面 | 后端 API 已有 |
|----------|-------------|
| 图分析仪表盘 | detectCommunities, getGraphAnalysis |
| 影响分析页面 | getImpactAnalysis |
| 代码浏览器 | getByPath, fileTree |
| 层级可视化 | getTypeHierarchy, getCallHierarchy |

### P2-5: 空壳页面

NopCodeFile、NopCodeUsage、NopCodeCall、NopCodeInheritance、NopCodeAnnotationUsage — 仅继承生成页面，无自定义。

### P2-6: E2E 测试缺口

已覆盖：符号搜索（5 个）、类型层级（5 个）。
未覆盖：仪表盘、代码浏览、调用链、索引管理、图分析。

---

## P3 — 架构演进

### P3-1: 行业对标差距

| 维度 | nop-code | 成熟工具 | 差距程度 |
|------|---------|---------|---------|
| 持久化存储 | 纯内存 | PostgreSQL/SQLite | 最大差距 |
| 查询优化 | 全量遍历 | 索引+缓存 | 100k+ 符号时性能堪忧 |
| 语言无关 IR | 每语言独立 | SCIP/LSIF | 跨语言分析受限 |
| 外部符号引用 | 完全缺失 | JDK+三方库可索引 | 调用链在库边界中断 |

### P3-2: 多语言支持局限

- Python/TypeScript 解析器功能远弱于 Java（无符号解析、无类型推断）
- 无跨语言分析能力
- 无统一的语言无关中间表示

### P3-3: 测试质量

| 测试类型 | 覆盖 | 缺口 |
|----------|------|------|
| 单元测试（core） | 60-70% | Record/lambda 测试 |
| 单元测试（lang-java） | ~30% | 复杂场景 |
| 集成测试（service） | 基础 | 错误场景 |
| 性能测试 | 无 | 需要基准测试 |
| 并发测试 | 无 | 需验证线程安全 |

---

## 改进路线图

### P0（立即）

1. 决定持久化策略并执行
2. 修复 code-browser 页面
3. 添加层级/调用链结果可视化

### P1（短期）

4. DTO 隔离
5. 补全 ICodeIndexService 接口
6. 统一错误处理
7. 内存优化

### P2（中期）

8. Record 类支持
9. 图分析仪表盘页面
10. 循环依赖检测
11. E2E 测试覆盖

### P3（长期）

12. 结构化类型系统
13. 外部符号引用注册表
14. api 模块重构
15. 性能基准测试
