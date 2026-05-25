# nop-code 流级分析设计

**日期**：2026-05-25
**范围**：`nop-code-flow` 模块
**状态**：**已实现**
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
| 风险评分变更分析 | 这次改动影响有多严重？ | core（CallGraph, SymbolTable） |
| 死代码检测 | 哪些代码永远不会被调用？ | core（CallGraph, SymbolTable） |

依赖方向：`core ← graph ← flow`，flow 不反向依赖 service 层。

---

## 一、执行流追踪

### 核心语义

执行流 = 从入口点出发，沿 CALLS 边前向 BFS 遍历得到的调用路径。

**BFS 前向追踪**使用深度限制（默认 15），并在遇到外部包符号时终止遍历（不进入 `java.*`, `javax.*`, `jakarta.*`, `org.springframework.*`, `org.apache.*` 等前缀的符号）。

入口点检测三路合并：
1. **结构分析**：通过 `EntryPointScorer` 识别无入边 CALLS 的根节点
2. **框架注解**：通过 `IEntryPointPatternProvider` IoC 接口匹配框架注解模式
3. **命名约定**：匹配正则 `^(main|handle.*|process.*|onEvent.*|run|execute|...)$`

### 框架模式注册

通过 Nop IoC 注册 `IEntryPointPatternProvider`，每种框架/语言提供一个实现。实现按 `priority()` 降序排列，高优先级先匹配。

内置默认实现 `DefaultSpringEntryPointPatternProvider` 覆盖 Spring MVC / Messaging / Scheduling / JMX 注解。

**拒绝了什么**：硬编码模式字典（CRG 的做法）→ Nop IoC 自动发现更符合平台理念，且支持用户通过 Delta 扩展。

### 关键度评分

五维加权评分：

```
criticality = file_spread   * 0.30    // 跨越多少个文件（min((n-1)/4, 1.0)）
            + external_score * 0.20    // 调用外部系统比例
            + security_score * 0.25    // 名称含安全关键词比例
            + test_gap       * 0.15    // 当前固定 1.0（待接入测试覆盖率数据）
            + depth_score    * 0.10    // 流深度归一化（depth / maxDepth）
```

安全关键词检测使用正则匹配 `qualifiedName` 和 `name` 字段，覆盖 auth/login/encrypt/password/token/permission 等常见安全词汇。

### 增量追踪

变更文件 → 受影响流 ID → 删除旧流 → 重新检测相关入口点 → BFS 重追踪。不删除无关节点的流。

`getAffectedFlows` 通过将 `changedFilePaths` 与流中每个节点的文件路径交叉比对来确定受影响的流。

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__detectFlows(indexId) → [ExecutionFlow]
NopCodeIndex__listFlows(indexId) → [ExecutionFlow]
NopCodeIndex__getFlow(indexId, flowId) → ExecutionFlow
NopCodeIndex__getAffectedFlows(indexId, changedFilePaths) → [ExecutionFlow]
```

---

## 二、风险评分变更分析

### 核心语义

1. 通过 `ProcessBuilder` 调用 `git diff --unified=0` 解析为 `{file: [(startLine, endLine)]}` 行级别映射
2. 行范围映射到受影响符号（行范围重叠检测）
3. 对每个受影响符号计算五维风险评分
4. 按 riskScore 降序排序，附带风险摘要和建议操作

### 风险评分公式

五个独立维度直接加总（不使用权重系数，各维度自带上限 cap）：

```
risk = flow_participation(cap 0.25)      // 有调用关系则 0.15，否则 0
     + community_crossing(cap 0.15)      // 调用者来自 >1 个不同包
     + test_coverage_gap(0.30 → 0.05)    // 测试文件/构造器 0.05，其他 0.30
     + security_sensitivity(0.20)        // 名称含安全关键词
     + caller_count / 20 (cap 0.10)      // 被调用次数归一化
```

风险分级：high ≥ 0.50，medium ≥ 0.25，low < 0.25。

### 数据模型

纯 DTO，无持久化（变更分析是即时计算）。

### git diff 集成

通过 `ProcessBuilder` 调用 `git diff baseline..target --unified=0`。解析器处理 rename、binary file 跳过、hunk header 提取。不引入 JGit 新依赖。

**拒绝了什么**：
- SVN 支持 → Nop 项目均为 Git
- 持久化变更结果 → 即时计算，无需存储

### GraphQL API

归属 `NopCodeIndexBizModel`：

```
NopCodeIndex__analyzeChanges(indexId, baselineCommitish, targetCommitish) → ChangeAnalysisResult
```

---

## 三、死代码检测

### 核心语义

检查符号是否有入边 CALLS（调用者），无调用者的非排除符号标记为死代码。

### 排除规则

以下符号不可标记为死代码：
- 构造器、参数、局部变量、类型参数、导入、命名空间
- abstract 方法
- 框架入口点（通过注解匹配 RequestMapping/Scheduled/KafkaListener/BizModel/BizQuery 等约 20 种）
- 测试文件中的符号（通过正则匹配 test 路径模式）
- ORM 实体基类子类（@Entity 或继承 Entity/BaseEntity/MappedSuperclass）
- dunder 方法（`__init__`, `__str__` 等）
- 被 @property / @abstractmethod / @dataclass 等装饰的方法

### 可配置排除模式

构造时传入 `excludePatterns`（正则表达式列表），匹配 `qualifiedName` 的符号被排除。这允许用户通过配置扩展排除规则，无需修改代码。

### 置信度评分

非排除的无调用者符号按置信度分级：
- **确定死代码**（confidence ≥ 0.9）：private 方法/字段，无动态调用指标
- **可疑死代码**（0.5 ≤ confidence < 0.9）：public/protected 方法或字段，或含 IoC 注解/框架组件签名等动态调用指标
- **忽略**（confidence < 0.5）：不输出

动态调用指标检测：符号签名含 Bean/Component/Service/Repository/Controller/Inject 等关键词，或名称含 listener/handler/callback/hook/observer/subscriber。

### 数据模型

纯 DTO。

### GraphQL API

归属 `NopCodeSymbolBizModel`：

```
NopCodeSymbol__detectDeadCode(indexId) → DeadCodeReport
```
