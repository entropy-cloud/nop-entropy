# Design 文档编写指南

> Status: active guide
> Created: 2026-05-17

## 定位

`ai-dev/design/` 是 AI 编辑的架构决策和使用契约文档，不是代码文档。

本目录记录：
- 架构决策：选了什么方案、为什么选、拒绝了什么替代方案
- 使用契约：使用者可感知的命名规范（Meter 名称、API 命名、GraphQL schema）、模块边界、数据流方向
- 需求规格：本项目没有独立的需求文档，需求层面的内容也落实到 design 文档中

本目录不记录：
- 具体代码：类签名、方法列表、字段定义、伪代码——源码是代码层面的唯一事实
- 实现过程：执行历史、迁移日记、方案对比的演进叙事——这些放在 `ai-dev/analysis/` 或 `ai-dev/logs/`

## 写什么

### 必须有

1. **决策 + 理由**：每个关键设计点都要回答"选了什么、为什么"
2. **拒绝了什么**：说明为什么没选其他方案，防止后续重复讨论
3. **约束和边界**：模块职责边界、数据流向、不可违反的约束

### 可以有

4. **使用层面的命名契约**：Meter 名称、API 端点命名、GraphQL 字段命名等使用者在 Grafana/代码/Prometheus 中直接看到的东西
5. **与外部系统的关系**：接口协议、数据格式约定

### 不要有

6. **实现代码片段**（告诉开发者怎么写代码）——看源码
7. **"Proposed vs Current" 对比**——design 只描述当前最终状态
8. **方案选型的多轮演进叙事**——放 `ai-dev/analysis/`

### 接口名称与代码片段的判断标准

**核心接口名和方法名可以出现**在 design 中——它们是架构决策的表达工具，不是代码翻译。但仅限于**定义契约**，不展开实现。

| 场景 | 属于 design？ | 原因 |
|------|-------------|------|
| 写出核心接口名及其职责（如 `IToolExecutor` 的 `executeAsync`） | ✅ | 接口名是设计决策，定义对象间的契约 |
| 用几行伪代码描述算法的期望行为（如"阻塞策略的判断顺序"） | ✅ | 这是需求规格，定义**应该发生什么** |
| 用伪代码描述数据流的转换规则 | ✅ | 这是语义契约 |
| 写出完整的类实现（字段、私有方法、内部逻辑） | ❌ | 这是实现细节，看源码 |
| 写出"先创建 X 再调用 Y 然后返回 Z"的实现步骤 | ❌ | 这是实现方案，不是需求 |

**判断标准**：这段内容是在回答**"系统如何组成、对象间如何协作"**还是**"代码怎么写"**？前者属于 design，后者属于源码。

**实践经验**：如果去掉所有接口名后，设计文档仍然能清楚表达架构决策，说明接口名是装饰性的，可以去掉。如果去掉后无法表达清楚，说明接口名是设计的一部分，应该保留。

## 组织方式

按子系统建子目录（如 `nop-job/`、`nop-ai-agent/`）。

层级和 precedence model 定义在 `ai-dev/design/README.md`（本目录的 attractor index）。

### 子系统目录结构

每个子系统目录必须有一个 `README.md`，说明：
1. 本目录的设计文档结构和内部层级
2. 阅读顺序（必读路径 + 按需深入 + 扩展方向）
3. 各文档的职责边界
4. 声明本目录遵循 AGE（Attractor-Guided Engineering）owner-doc 模式组织

### 必备层级

不论模块如何命名，每个子系统必须包含以下两层：

| 层级 | 职责 | 必须回答的问题 |
|------|------|---------------|
| **Vision**（愿景/原则层） | 产品定位、成功标准、不可违反的约束、显式 non-goals、设计收敛路径 | 做什么、不做什么、凭什么判断成功、哪些决策必须由人做出 |
| **Architecture Baseline**（架构基线层） | 系统分层、核心对象职责契约、模块边界、关键设计决策 | 怎么分层、核心对象各自的职责是什么、模块间依赖方向是什么 |

其余层级（执行模型、DSL、引擎、策略等）按子系统实际需要自行组织，命名不强求统一。不同模块复杂度不同，层数和名称可以不同，但 Vision 和 Architecture Baseline 是底线。

### 编号命名（推荐）

编号前缀（`00-`、`01-`、`02-`…）用于标识阅读顺序和层级归属，不是强制要求。当子系统文档较多（>5 篇）时推荐使用，便于新读者快速定位入口。

未使用编号的子系统，至少要在 README.md 中明确标注哪些文档是 Vision 级别、哪些是 Architecture Baseline 级别。

### 命名规范

| 场景 | 文件名格式 | 示例 |
|------|-----------|------|
| Vision 层 | `00-vision.md` 或 `{topic}-vision.md` | `00-vision.md` |
| Architecture Baseline 层 | `01-architecture-baseline.md` 或 `{topic}-architecture.md` | `01-architecture-baseline.md`、`architecture.md` |
| 子系统专题设计 | `{topic}-design.md` | `core-design.md`、`invoker-design.md` |
| 子系统索引 | `README.md` | 每个子系统目录一个 |
| 跨子系统专题 | `{topic}-design.md`（放在 `ai-dev/design/` 根目录） | `semantic-edge-design.md` |
| 目录名 | 模块名（与 Maven 模块一致） | `nop-job/`、`nop-stream/` |

### 接口命名规则

接口名应足够自描述，避免与 Java 生态中常见的同名接口碰撞。规则：

1. **用接口操作的对象限定，不用子系统前缀**：`IAgentXxx` 是懒前缀——所有东西都是 Agent 的。应该用操作对象来限定
2. **概念标准且不易混淆的名称可以保持简短**：`ICircuitBreaker`、`IRetryPolicy` 等概念在 Java 生态中已固定，上下文足够清晰
3. **过于通用的名称必须限定**：如果接口名脱离包名后在 import 层可能与其他库碰撞，就必须加限定词

| 反模式 | 正确 | 理由 |
|--------|------|------|
| `IHook` | `IAgentLifecycleHook` | "Hook" 太通用（Reactor、JUnit 都有 Hook） |
| `IRouter` | `IModelRouter` | "Router" 是 Java 生态中最拥挤的名字之一（HTTP、消息、Faces） |
| `ICompactor` | `IContextCompactor` | 概念模糊——压缩什么？上下文？内存？日志？ |
| `IGuardrail` | `IContentGuardrail` | 操作对象是 LLM 输入/输出内容 |
| `ICircuitBreaker` | 保持 | 概念标准，上下文清晰 |
| `IRetryPolicy` | 保持 | 概念标准，上下文清晰 |
| `ITalent` | 保持 | 足够独特 |
| `ISustainer` | 保持 | 足够独特 |
| `IGoalTracker` | 保持 | 足够具体 |
| `ICheckpointManager` | 保持 | 足够具体 |

实现类名跟随接口限定：`NoOpContextCompactor`（不是 `NoOpCompactor`）、`SmartModelRouter`（不是 `SmartRouter`）。

### 完整范例

`nop-ai-agent/` 是当前最完整的范例，8 层结构：

```
nop-ai-agent/
├── README.md                           # 索引 + 阅读顺序 + AGE 声明
├── 00-vision.md                        # Vision 层
├── 01-architecture-baseline.md         # Architecture Baseline 层
├── 02-execution-model.md               # 执行模型
├── 04-tool-invocation.md               # 工具调用
├── nop-ai-agent-dsl.md                 # DSL 层（多篇）
├── nop-ai-agent-react-engine.md        # 引擎层（多篇）
├── nop-ai-agent-runtime-semantics.md   # 语义映射层
├── nop-ai-agent-session-and-storage.md # 策略层（多篇）
└── nop-ai-agent-roadmap.md             # 愿景演进
```

README 中明确声明："本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织"。

### 被取代文档的处理

当一篇设计文档的内容已被新文档完全覆盖时：

1. **逐节核对**新文档是否覆盖了旧文档的全部内容
2. 确认覆盖后**直接删除**旧文档，不保留作"历史参照"
3. 更新所有引用旧文档的链接（包括其他设计文档、README、`docs-for-ai/`）
4. 如果旧文档中有个别内容未被覆盖，先补充到新文档中，再删除旧文档

**不保留 superseded 文档的理由**：设计文档是当前基线，不是版本历史。保留过时文档会造成读者无法判断哪篇是权威来源，且容易产生不一致。历史追溯使用 git。

### 推荐模板

```markdown
# {子系统/专题} 设计

**日期**：YYYY-MM-DD（更新于 YYYY-MM-DD）
**范围**：涉及的模块和文件
**状态**：active / resolved / superseded / 草案
**灵感来源**：（可选）

---

## 一、设计结论
（核心决策，1-5 条）

## 二、背景与动机
（为什么需要这个设计，当前痛点）

## 三、核心设计
（架构决策 + 使用契约 + 约束边界）

## 四、拒绝了什么
（替代方案及拒绝理由）

## 五、与已有设计的关系
（上下游依赖，相关设计文档链接）
```

## 与 docs-for-ai 的关系

| | `docs-for-ai/` | `ai-dev/design/` |
|---|---|---|
| **定位** | 已实现的平台使用规范 | 设计决策和架构演进 |
| **时态** | 描述当前已实现的能力 | 可包含未实现的目标架构 |
| **读者** | 使用 Nop 构建应用的开发者 | 开发 Nop 框架本身的开发者 |
| **权威性** | 必须与代码一致 | 先于代码，驱动实现 |

设计实现后，关键结论应同步到 `docs-for-ai/`。设计文档保留决策上下文（"为什么"），`docs-for-ai/` 保留使用规范（"怎么做"）。

## 设计文档的引用约束

设计文档是**架构决策的最终记录**，不应引用具有时效性的过程文档：

- **禁止引用** `ai-dev/discussions/` — 讨论是需求澄清的过程记录，设计应独立表达最终结论，而非依赖讨论的推导过程。讨论中有价值的洞察应直接融入设计文档。
- **禁止引用** `ai-dev/analysis/` — 分析是调研和对比的过程记录，设计应呈现最终决策和理由，而非引用某次分析的结果。分析中的关键发现应作为设计文档的独立章节。
- **可以引用** `ai-dev/lessons/`、`ai-dev/design/` 内的其他设计文档、以及 `docs-for-ai/` — 这些是规范性和持久性的文档。
- **可以引用** 源码文件 — 用于锚定实现位置。

简言之：设计文档引用的应是**持久的**文档（规范、设计、经验教训、源码），而非**时效性的**过程记录（讨论、分析、日志）。

## 参考

- `ai-dev/design/README.md` — 本目录的层级索引和 precedence model
- chaos-flux 项目的 docs/architecture/README.md 是同类索引的成熟范例
- `ai-dev/lessons/02-metrics-design-convention.md` 是子系统级规范的好例子
