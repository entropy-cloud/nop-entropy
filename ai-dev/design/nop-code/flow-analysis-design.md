# nop-code 流级分析设计

**日期**：2026-05-25
**范围**：`nop-code-flow` 模块
**状态**：**目标架构**（`nop-code-flow` 模块和所有接口均未实现）
**归属模块**：`nop-code-flow`（依赖 `nop-code-core` + `nop-code-graph`）

## 灵感来源

code-review-graph v2.3.3：
- `flows.py` — 入口点检测 + BFS 前向追踪 + 五维关键度评分
- `changes.py` — git diff 行级别映射 + 五维风险评分
- `refactor.py` — 多维度排除的死代码检测

## 模块定位

nop-code-flow 包含三种流级分析能力，它们共享一个特征：**基于执行流的上下游关系进行分析**，而非单纯基于图拓扑。

| 能力 | 核心问题 | 依赖 |
|------|---------|------|
| 执行流追踪 | 代码从入口到出口经过哪些路径？ | core（CallGraph, SymbolTable） |
| 风险评分变更分析 | 这次改动影响有多严重？ | core + graph（社区、影响分析） + 执行流 |
| 死代码检测 | 哪些代码永远不会被调用？ | core（CallGraph） + graph（入口点评分） |

依赖方向：`core ← graph ← flow`，flow 不反向依赖 service 层。

---

## 一、执行流追踪

### 核心语义

执行流 = 从入口点出发，沿 CALLS 边前向 BFS 遍历得到的调用路径。

入口点 = 满足以下任一条件的符号：
1. 无入边 CALLS 的根节点
2. 匹配框架注解模式（@RequestMapping, @Scheduled, @KafkaListener 等）
3. 匹配约定名模式（main, handle*, process*, onEvent* 等）

### 关键度评分

五维加权评分：

```
criticality = file_spread   * 0.30    // 跨越多少个文件（min((n-1)/4, 1.0)）
            + external_score * 0.20    // 调用外部系统比例
            + security_score * 0.25    // 名称含安全关键词比例
            + test_gap       * 0.15    // 1.0 - 测试覆盖率
            + depth_score    * 0.10    // 流深度归一化
```

### 数据模型

```
ExecutionFlow:
  id, name, indexId
  entryPointSymbolId, entryPointQualifiedName
  depth, criticality
  pathNodeIds: List<String>           // 有序节点列表
  stats: { fileCount, symbolCount, maxDepth }

FlowMembership:                       // 多对多
  flowId, symbolId
```

### 持久化

新增 `nop_code_flow` 和 `nop_code_flow_membership` 表。

### 框架模式注册

通过 Nop IoC 注册 `IEntryPointPatternProvider`，每种框架/语言提供一个实现：

| 语言 | 框架 | 模式示例 |
|------|------|---------|
| Java | Spring MVC | @RequestMapping, @GetMapping, @PostMapping |
| Java | Spring Messaging | @MessageMapping, @RabbitListener, @KafkaListener |
| Java | JMX | @ManagedOperation |
| Java | CDI | @Observes |
| Python | Flask | @app.route, @bp.route |
| Python | Django | def view(request) |
| TypeScript | Express | app.get, router.post |

**拒绝了什么**：硬编码模式字典（CRG 的做法）→ Nop IoC 自动发现更符合平台理念，且支持用户通过 Delta 扩展。

### 增量追踪

变更文件 → 受影响流 ID → 删除旧流 → 重新检测相关入口点 → BFS 重追踪。不删除无关节点的流。

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__detectFlows(indexId) → FlowDetectionResult
NopCodeIndex__listFlows(indexId) → [ExecutionFlow]
NopCodeIndex__getFlow(indexId, flowId) → ExecutionFlow
NopCodeIndex__getAffectedFlows(indexId, changedSymbolIds) → [ExecutionFlow]
```

---

## 二、风险评分变更分析

### 核心语义

1. 解析 git diff → `{file: [(startLine, endLine)]}` 行级别映射
2. 行范围映射到受影响符号（行范围重叠检测）
3. 对每个受影响符号计算五维风险评分

### 风险评分公式

```
risk = flow_participation(cap 0.25)      // 参与的执行流数 * 流 criticality
     + community_crossing(cap 0.15)      // 调用者来自不同社区的计数
     + test_coverage_gap(0.30 → 0.05)    // 传递测试覆盖率缺口
     + security_sensitivity(0.20)        // 名称含安全关键词
     + caller_count / 20 (cap 0.10)      // 被调用次数
```

### 数据模型

纯 DTO，无持久化（变更分析是即时计算）：

`ChangeAnalysisResult` 包含：changedFiles / affectedSymbols（含 symbolId, qualifiedName, kind, riskScore, riskBreakdown 五维度, affectedFlows） / riskSummary（high/medium/low 计数） / suggestedActions

### git diff 集成

通过 `ProcessBuilder` 调用 `git diff --unified=0` 解析。不引入 JGit 新依赖。

**拒绝了什么**：
- SVN 支持 → Nop 项目均为 Git
- 持久化变更结果 → 即时计算，无需存储

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__analyzeChanges(indexId, baseCommitish, targetCommitish) → ChangeAnalysisResult
NopCodeIndex__getImpactRadius(indexId, changedSymbolIds, maxDepth) → ImpactRadiusResult
```

---

## 三、死代码检测

### 核心语义

检查符号是否有入边（CALLS / INHERITS / IMPORTS_FROM / REFERENCES / TESTED_BY），无入边的非排除符号标记为死代码。

### 排除规则

以下符号不可标记为死代码：
- 框架入口点（EntryPointScorer 识别的 ENTRY_POINT 类型）
- 测试文件中的符号
- 构造器
- abstract 方法
- ORM 实体基类子类（标记了 @Entity 或继承特定基类）
- dunder 方法（\_\_init\_\_, \_\_str\_\_ 等）
- 被 @property / @abstractmethod / @dataclass 装饰的方法

排除规则通过 Nop 配置 `dead-code-exclude-patterns` 可扩展。

### 导入图验证

对 bare-name（未限定的调用目标名），通过导入图 2-hop 验证可信度：如果调用者和被调用者之间没有直接或间接的 import 关系，则 bare-name 边不可信，不作为"有调用者"的证据。

### 数据模型

纯 DTO：

`DeadCodeReport` 包含：deadSymbols（确定的死代码）/ suspiciousSymbols（低置信度）/ stats（total/dead/suspicious 计数）。每个 `DeadCodeEntry` 含 symbolId, qualifiedName, kind, filePath, reason, confidence。

### GraphQL API

归属 `NopCodeSymbolBizModel`：

```
NopCodeSymbol__detectDeadCode(indexId, kinds) → DeadCodeReport
```
