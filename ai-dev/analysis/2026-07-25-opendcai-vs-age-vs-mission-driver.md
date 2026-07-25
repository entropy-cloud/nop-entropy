# OpenDCAI (DataFlow) vs AGE Template vs Mission Driver — 深度对比分析

> Status: open  
> Date: 2026-07-25  
> Version: 6.0 (pure Mission Driver mechanism, no DataFlow dependency)  
> Scope: 分析 Mission Driver 的 flow 引擎机制与 DataFlow Pipeline DSL 的异同，并讨论**完全不用 DataFlow、只用 Mission Driver 自身机制**实现数据处理 harness 的可行方案。  
> Conclusion: 两者是同构的 flow 引擎（key 拓扑排序 ≡ 子流嵌套展开）。用 Mission Driver 机制实现 harness **可行，不需要修改引擎**——内置 flow 覆盖设计→执行→质量→迭代循环；预置脚本 = operator 注册表，skill = 多步骤编排，子 agent = 任务级并行；文件转接是唯一协作通道。残留差距仅 2 个（编译期 schema 验证、引擎级 streaming），均非阻塞性。

---

## 一、下载概览

OpenDCAI 组织 41 个仓库已全部克隆至 `~/ai/dataflow/`。核心项目清单不变。

---

## 二、Mission Driver 本质：它是一个 Flow DSL 引擎

Mission Driver 不是"一个任务编排工具"，它的完整架构包含**四层 DSL**：

### 2.1 Mission JSON — 声明式流程配置 DSL

```jsonc
{
  "name": "kebab-case-name",
  "extends": "base",              // 继承 missions/base.json（模型/agent/命令默认值）
  "roadmapPath": "path/to/roadmap.md",
  "plansDir": "path/to/plans-dir",
  "flowName": "mission-driver",   // ← 指向一个 flow 定义文件
  "commands": { "test": "...", "build": "...", "lint": "...", "typecheck": "..." },
  "prompts": { "multiAudit": "...", "openAudit": "..." },
  "commitFormat": "..."
}
```

关键特征：
- **纯静态**：无运行时字段，状态在 `_tmp/<runId>/run-state.json`
- **继承机制**：`extends: "base"` 从 `missions/base.json` 继承命令/模型/agent
- **多子流引用**：`flowName` 可指向 `flows/mission-driver.json`（内置）或 `missions/flows/<custom>.json`
- **开放的 commands 集**：缺失 = 跳过对应步骤（`build` 缺失则 BUILD_VERIFY 不运行 build）

### 2.2 Flow 定义 — 状态机 DSL（可组合、可覆盖）

```
加载优先级：
① <missionsDir>/flows/<flowName>.json  ← 项目级覆盖
② tools/mission-driver/flows/<flowName>.json  ← 内置
```

内置 `mission-driver` 流：

```
CHECK → REVIEW_PLANS → EXEC_PLANS → DRAFT_PLANS → [DEEP_AUDIT] → loop
```

子流 `plan-execution`：
```
EXECUTE → CLOSURE_SCRIPT_CHECK → CLOSURE_AUDIT → BUILD_VERIFY
```

子流 `deep-audit-loop`：
```
CHECK_OPEN_AUDITS → MULTI_AUDIT → OPEN_AUDIT → SCAN_NEW_RESULTS
```

状态机特性：
- **顺序步骤** + 循环（顶级：DRAFT_PLANS → REVIEW_PLANS）
- **条件跳过**：`prompts.multiAudit` 缺省 → 对应步骤被 `when` 跳过
- **队列迭代**：EXEC_PLANS 遍历所有 `active` 状态的 plan 文件
- **恢复语义**：从磁盘 `[x]`/`[ ]` 标记恢复，非回放历史
- **终止门控**：`max_cycles` / `max_total_steps` / `ping_pong` / `max_retries`

### 2.3 Plan 文件 — 带状态机的执行切片 DSL

Markdown + 结构化 YAML front-matter + checkboxes `[x]`/`[ ]`。
Plan 的 checkbox **是机器可读的持久化状态**——引擎通过扫描 checkbox 决定从中断点恢复。

### 2.4 Roadmap — 工作项索引 DSL

`todo` / `planned` / `done` 状态派发 + Mermaid 依赖图 + Milestone。

### 2.5 引擎核心进程模型

```
每步 = child opencode run 子进程
日志 = _tmp/<ts>-mission-driver/oc-<STEP>-*.log
状态持久化:
  - mission.json: 只读（不修改）
  - run-state.json: 引擎运行时状态（currentStep, visits, markers）
  - plan 文件: [x]/[ ] 复选框（恢复依据）
  - roadmap: todo/planned/done 工作项状态
```

**结论：Mission Driver 是一个治理流引擎**，其 DSL 四层结构（Mission Config + Flow Definition + Plan + Roadmap）完整描述了一个 AI 驱动开发项目的生命周期管理。它不关心"数据怎么处理"，只关心"任务怎么编排、质量怎么保证"。

---

## 三、DataFlow 本质：它是一个数据流 DSL 引擎

### 3.1 Operator — 基本处理单元

```python
class OperatorABC:
    def run(self, storage: DataFlowStorage, **kwargs): ...
```

每个 operator 声明 `input_keys` 和 `output_keys`（通过 `run()` 签名的参数名约定：`input_`/`output_` 前缀）。

### 3.2 PipelineABC.compile() — 编译期 DAG 构建

```
compile():
  ① AutoOP proxy: 遍历 self 的 OperatorABC 属性，包装为 AutoOP
  ② forward(): 调用 operator chain，每个 AutoOP 记录 OPRuntime
  ③ _build_operator_nodes_graph():
      - 对每个 OPRuntime 构建 OperatorNode
      - 解析 input_keys / output_keys
      - 构建 key-level 依赖图（KeyNode + ptr 双向指针）
      - compile-time 检查：所有 input_key 必须存在于之前 operator 的 output_keys 中
      - 构建 DATASET-INPUT / DATASET-OUTPUT 伪节点
④ forward 替换为 _compiled_forward (顺序执行 operator 节点)
```

关键创新：**key-level 依赖图** vs 常见的 stage-level DAG。

```
原始字段: ["id", "text", "metadata"]
        │
  [PDFParser]        input:  "text"
        │             output: "text", "markdown"
        ▼
  [TextCleaner]      input:  "markdown"
        │             output: "text""text", "clean_text"
        ▼
  [QAGenerator]      input:  "clean_text"
                     output: "question""question", "answer"
        ▼
  [QualityFilter]    input:  "question", "answer"
                     output: "score"
```

### 3.3 三种执行模式

| PipelineABC | _compiled_forward | 单步顺序执行，无批处理 |
| BatchedPipelineABC | 批处理 + checkpoint（`batch_step` + `_last_success_step.txt`） |
| StreamBatchedPipelineABC | 流式批处理（`iter_chunks()` + 内存释放） |

所有模式共享同一个编译结果（`op_nodes_list` + key 依赖图）。

### 3.4 Operator Registry 和插件系统

```python
Registry(name='operators')  # 懒加载 importlib 发现
# operators/ 下按领域组织：
#   code/generate/, code/filter/, code/eval/, code/refine/
#   core_text/generate/, ...
# whitelist 白名单机制控制可用 operator 集合
```

### 3.5 DataFlow-WebUI — NL2Pipeline Harness

DataFlow-WebUI 提供三层桥接：

```
NL / Chat (Code Agent)
    │  MCP (list_operator_categories, get_operator_schema, ...)
    ▼
Pipeline Code Generator (skill: generating-dataflow-pipeline)
    │  生成 Python 代码
    ▼
PipelineABC.compile() → run() → DataFlowStorage
    │  pyvis DAG 可视化
    ▼
WebUI Canvas (交互式 DAG 编辑器)
```

**DataFlow-Harness = DataFlow-Skills（流程知识）+ MCP（工具注册表连接）+ WebUI（可视化 DAG）**。

其本质是：用 Code Agent 理解 NL 需求 → 通过 MCP 查询 operator 注册表 → 生成标准的 PipelineABC 子类代码 → DataFlow 引擎编译执行。

---

## 四、关键洞察：执行顺序同构，恢复语义不同

### 4.1 执行顺序层面：key 依赖图与子流嵌套是同构的

> **Scope 声明**：本节分析针对 **SDK 编程路径**（用户手写 PipelineABC 子类并调用 compile()）。在 **WebUI/Agent Harness 路径**中，dataflow_engine.py 实现了独立的线性执行循环（第 777-901 行），不调用 compile()，不构建 key 依赖图——operators JSON 数组顺序即执行顺序。因此本节的同构论证不直接适用于 Harness 场景，但有助于理解两种执行模型的等价性边界。

表面上 DataFlow 的 key-level 依赖图和 Mission Driver 的子流嵌套是两种不同的机制。但**在执行顺序推导上它们是等价的**：

- DataFlow compile()：从 input_keys/output_keys 推导拓扑序 → 线性化为 `op_nodes_list` 顺序执行
- Flow DSL 子流嵌套：每个 step 通过嵌套调用形成调用层次 → 展开的顺序就是执行顺序

两者最终都产生一个算子/步骤的线性执行序列。用 flow DSL 给每个 step 声明 input/output keys，引擎同样能推导出依赖顺序。**依赖解析不是 DataFlow 的独有能力，而是任何 flow DSL 都能做到的**。

### 4.2 真正的差异：恢复语义

执行顺序同构，但**失败后的恢复机制完全不同**。

> **重要修正（Round 1 审查 C2）**：之前版本将 Mission Driver 的恢复描述为"stack unwind + re-push"。这是概念性比喻，与实现不符。SKILL.md C.4 明确说明："recovery is driven entirely **by disk scan**, not by replaying steps"。下文已修正。

#### DataFlow 的线性恢复（SDK 路径，`Pipeline.py:507`）

```python
def _compiled_forward(self, resume_step: int = 0):
    for idx, op_node in enumerate(self.op_nodes_list):
        if idx - 1 < resume_step:   # ← 线性 skip
            continue
        op_node.op_obj.run(...)
```

恢复方式是 **flat skip**：从 `resume_step` 之后的节点继续跑。BatchedPipelineABC 额外支持 step + batch 粒度的 checkpoint 恢复（`_last_success_step.txt`）。

注意：WebUI 的 dataflow_engine.py **没有任何恢复机制**——失败直接返回 failed（第 877-901 行）。

#### Mission Driver 的声明式检查点恢复

Mission Driver 的恢复**不是运行时栈展开**，而是**基于磁盘持久化标记的声明式检查点恢复**：

```
plan 文件: - [x] Phase 1 已完成    → 引擎扫描时跳过
          - [ ] Phase 2 未完成    → 引擎从这里恢复执行
          - [ ] Phase 3 未开始    → 等待

run-state.json: steps[] 是审计日志，不驱动恢复
```

子流嵌套（EXEC_PLANS → plan-execution subflow）提供了**逻辑上的隔离**——一个 plan 的执行失败不影响其他 plan——但这通过 plan 文件状态的独立性保证，不是通过运行时栈帧 pop。

#### 恢复语义对比

| 能力 | DataFlow (SDK 路径) | DataFlow (WebUI 路径) | Mission Driver |
|------|---------------------|---------------------|----------------|
| 恢复机制 | resume_step 线性跳过 | **无恢复**（失败即返回） | checkbox 磁盘扫描 |
| 恢复粒度 | operator 级 / batch 级 | N/A | plan phase 级 |
| 局部重试 | ❌ resume_step 单调递增 | N/A | ✅ 只重跑 `[ ]` 的 phase |
| 多轮循环 | ❌ 一次执行 | ❌ 一次执行 | ✅ DRAFT→EXEC→AUDIT→REDRAFT |
| 子流隔离 | ❌ 扁平数组 | N/A | ✅ 每个 plan 独立文件状态 |

#### Stack 对 loop 的优势（修正后）

Mission Driver 的核心循环是：

```
DRAFT_PLANS → REVIEW_PLANS → EXEC_PLANS → [DEEP_AUDIT] → DRAFT_PLANS ...
```

每次迭代的隔离性来自 **plan 文件的独立状态**，而非栈帧。关键效果：
- DRAFT_PLANS 读 roadmap 的 todo 项 → 起草新 plan 文件
- EXEC_PLANS 扫描所有 `active` plan → 每个 plan 在 plan-execution 子流中执行
- 一个 plan 执行失败 → 标记其 checkbox 状态 → 其他 plan 不受影响
- 所有 plan 完成 → 回到 DRAFT_PLANS 读 roadmap 下一个 todo

**关键**：DataFlow 没有循环——pipeline 是一次性执行。如果要支持多轮优化（draft → run → audit → redraft），DataFlow 必须在外部包一个循环（就像 WebUI 的 DataFlowEngine.run 所做的），但那层循环没有 plan 级别的状态隔离。

### 4.3 核心差异汇总

| 维度 | Mission Driver (治理流) | DataFlow (数据流) |
|------|------------------------|-------------------|
| **DSL 领域** | 开发流程治理 | 数据处理管线 |
| **流类型** | 状态机 + 子流嵌套 | 线性 DAG（compile 拓扑排序） |
| **节点语义** | 治理步骤（CHECK/REVIEW/AUDIT/EXEC） | 数据算子（generate/filter/eval/refine） |
| **节点间通信** | 文件系统（plan 文件 / run-state） | key 列名（input_xxx / output_xxx） |
| **依赖解析** | 子流嵌套天然有序 | key 拓扑排序（与子流嵌套等价，仅 SDK 路径） |
| **恢复原语** | checkbox 磁盘扫描（声明式检查点） | flat skip / 无恢复（WebUI 路径） |
| **循环支持** | 顶层永续循环 + plan 级状态隔离 | 无循环 |
| **恢复机制** | checkbox [x] 扫描 + run-state.json | batch_step + _last_success_step.txt |
| **执行模式** | 顺序（单进程循环） | 顺序/批处理/流式 + Ray 并行 |
| **质量门控** | CLOSURE_AUDIT / BUILD_VERIFY | 无内置门控（靠 operator 自身） |
| **Operator 注册表** | 无（只有 step/prompt 覆盖） | Registry(name='operators') + 白名单 |
| **用户角色** | AI agent 开发者 | 数据处理工程师 |
| **产出** | 代码变更 + 文档 + audit trace | 数据集（JSONL/CSV/Parquet） |

---

## 五、用 Mission Driver 机制实现 DataFlow Harness — 方案讨论

> **Scope 重定义（v6.0）**：本节讨论的是**完全不用 DataFlow SDK**，只用 Mission Driver 自身的机制（mission.json + flow + plan + roadmap + checkbox 恢复 + child opencode run + 文件转接）来实现一个数据处理 harness 系统。DataFlow 仅作为对比参照物，不作为依赖。

### 5.1 Mission Driver 机制与 DataFlow Harness 的映射

DataFlow Harness 的本质是四个阶段：**设计 → 执行 → 质量检查 → 迭代优化**。Mission Driver 的内置 flow 天然覆盖：

| Harness 阶段 | Mission Driver 机制 | DataFlow-WebUI 的对应物 |
|-------------|---------------------|------------------------|
| **设计** pipeline | DRAFT_PLANS：agent 读 roadmap 任务 → 起草 plan（嵌入数据处理方案） | NL2Pipeline skill：agent 读 NL 需求 → 生成 pipeline config JSON |
| **审查** 设计 | REVIEW_PLANS：sub-agent 审查 plan 的合理性 | 无（WebUI 直接执行，不审查设计） |
| **执行** 处理 | EXECUTE：agent 读 plan → 生成 Python 脚本 → `python script.py` | DataFlowEngine.run()：从 config 实例化 operator → 顺序执行 |
| **质量检查** | CLOSURE_SCRIPT_CHECK + CLOSURE_AUDIT | 无内置门控（靠 operator 自身） |
| **迭代优化** | DEEP_AUDIT → DRAFT_PLANS（下一轮） | 无（一次执行） |
| **任务编排** | roadmap 工作项 + 优先级排序 | 无（用户手动构建 pipeline） |
| **断点恢复** | plan checkbox `[x]`/`[ ]` | BatchedPipelineABC 的 `_last_success_step.txt` |

Mission Driver 的内置 flow **不需要修改**就能驱动数据处理场景。需要做的只是约定 plan 如何描述数据处理方案、EXECUTE prompt 如何指导 agent 生成脚本。

### 5.2 Plan 文件作为数据处理方案的载体

Plan 文件天然适合描述数据处理方案——它有 Phase 切片、Exit Criteria、Closure Gates。只需要约定如何嵌入数据 schema 和处理逻辑：

```markdown
> Plan Status: draft
> Source: roadmap item "为医疗语料生成 QA 训练集"

### Phase 1 - 数据提取与清洗
Status: planned

输入数据: data/raw_medical_papers.jsonl
输入 schema: { "title": str, "abstract": str, "full_text": str }
输出 schema: { "title": str, "clean_text": str }

处理逻辑:
  - 读取 JSONL，提取 full_text 字段
  - 去除 HTML 标签和引用标记
  - 过滤非中英文内容
  - 输出到 data/processed/clean_text.jsonl

Exit Criteria:
- [ ] 输出行数 >= 原始行数 * 0.8
- [ ] clean_text 字段平均长度 >= 100 字符
- [ ] 无空值

### Phase 2 - QA 对生成
Status: planned

输入数据: data/processed/clean_text.jsonl (Phase 1 输出)
输入 schema: { "title": str, "clean_text": str }
输出 schema: { "question": str, "answer": str, "source_title": str }

处理逻辑:
  - 对每条 clean_text，调用 LLM 生成 3-5 个 QA 对
  - 使用 few-shot prompt（示例见 prompts/qa_generation.txt）
  - 输出到 data/processed/qa_pairs.jsonl

Exit Criteria:
- [ ] QA 对数量 >= 1000
- [ ] 平均答案长度 >= 20 字符
- [ ] 抽样检查 20 条，人工可读率 >= 80%

### Phase 3 - 质量过滤
Status: planned

输入数据: data/processed/qa_pairs.jsonl (Phase 2 输出)
输出 schema: { "question": str, "answer": str, "source_title": str, "quality_score": float }

处理逻辑:
  - 用 LLM 对每个 QA 对打分（0-1）
  - 过滤 quality_score < 0.6 的记录
  - 输出到 output/final_dataset.jsonl

Closure Gates:
- [ ] 最终数据集 >= 500 条
- [ ] 平均质量分 >= 0.7
- [ ] 无重复 QA 对
```

**关键约定**：plan 用自然语言 + schema 描述数据处理逻辑，不是用 DataFlow operator 名。EXECUTE agent 的职责是读懂这些描述，生成对应的 Python 脚本（pandas + LLM API），执行它。

### 5.3 文件转接的信息流

所有信息通过文件传递，不需要 API 或同进程通信：

```
roadmap.md
  └─ "todo: 为医疗语料生成 QA 训练集"
        │
        ▼ DRAFT_PLANS agent 读 roadmap
plan-001.md (§5.2 的内容)
        │
        ▼ EXECUTE agent 读 plan
生成脚本并执行:
  Phase 1: python _tmp/gen_clean_text.py → data/processed/clean_text.jsonl
  Phase 2: python _tmp/gen_qa_pairs.py   → data/processed/qa_pairs.jsonl
  Phase 3: python _tmp/gen_filter.py     → output/final_dataset.jsonl
  汇总:   python _tmp/gen_report.py      → output/quality_report.json
        │
        ▼ CLOSURE_SCRIPT_CHECK agent 读 quality_report.json
对照 Exit Criteria 断言:
  - 最终数据集 520 条 ≥ 500 ✓
  - 平均质量分 0.72 ≥ 0.7 ✓
  - 无重复 ✓
        │
        ▼ CLOSURE_AUDIT agent 读 plan + output
审查: Phase 2 的 few-shot prompt 是否合理？抽样质量如何？
        │
        ▼ DEEP_AUDIT agent 读 audit 文件
发现: 10% 的 QA 对答案过于简短
起草 plan-002: Phase 2 增加"答案长度 >= 20 字符"约束
        │
        ▼ 下一轮 DRAFT→EXEC→AUDIT 循环
```

plan 文件之间的数据引用通过路径约定：`Phase N 的输入 = Phase N-1 的输出路径`。每个 plan 的输出路径在 plan 文件中显式声明。

### 5.4 三层执行机制：预置脚本 + Skill + 子 agent 并行

> **v6.1 修正**：之前版本（§5.4-5.5）遗漏了 Mission Driver 生态中三个关键执行机制。本节修正后，Gap-2（operator 库）和 Gap-4（批处理）的评估发生根本变化。

#### 机制 1：预置脚本 = Operator 注册表

EXECUTE prompt 可以直接约定调用**事先编写好的脚本**，不需要 agent 每次从零生成代码。plan 的 Phase 描述中指定脚本名和参数：

```markdown
### Phase 1 - 文本提取
处理脚本: scripts/extract_text.py
参数: --input data/raw_papers.jsonl --output data/processed/text.jsonl --lang zh,en
```

预置的脚本库就是 operator 注册表：

```
scripts/
  extract_text.py        ← 等价于 DataFlow PDFParser
  clean_text.py          ← 等价于 TextCleaner
  generate_qa.py         ← 等价于 QAGenerator
  filter_quality.py      ← 等价于 QualityFilter
  evaluate_dataset.py    ← 等价于 QualityEvaluator
```

**这完全解决了"无预置 operator 库"的问题。** 脚本的参数签名（`--input`/`--output`/`--lang`）等价于 DataFlow operator 的 `input_key`/`output_key`。新增"operator"只需要新增一个脚本。

#### 机制 2：Skill = 多步骤处理流程封装

opencode 的 skill 机制允许在 prompt 中直接引用 skill。skill 内部可以定义多个步骤、条件逻辑、错误处理。可以定义数据处理专用的 skill：

```
.opencode/skills/data-pipeline-exec/SKILL.md
```

这个 skill 封装了"读 plan → 解析 Phase 列表 → 按序调用预置脚本 → 收集结果 → 生成 quality_report"的完整流程。EXECUTE step 的 prompt 只需要说"使用 data-pipeline-exec skill 执行当前 plan"。

skill 比 DataFlow operator 更灵活：
- 一个 skill 可以编排多个脚本（等价于一个 mini-pipeline）
- skill 可以包含条件逻辑（如"如果输出行数 < 100，重试一次"）
- skill 可以被 agent 引用，也可以被其他 skill 组合

#### 机制 3：子 agent 并行 = 任务级并行分派

opencode 支持在执行过程中派生子 agent 处理子任务。EXECUTE 的 prompt 可以指示并行分派：

```
prompt 约定:
  "将输入数据按 1000 行分片，每片分派给一个子 agent 调用 generate_qa.py 处理。
   子 agent 1: python generate_qa.py --input chunk_001.jsonl --output qa_001.jsonl
   子 agent 2: python generate_qa.py --input chunk_002.jsonl --output qa_002.jsonl
   ...
   所有子 agent 完成后，合并结果到 qa_pairs.jsonl"
```

这实现了**任务级并行**——不是引擎层面的数据并行（如 DataFlow 的 Ray 加速），而是通过 agent 分派子任务实现的并行处理。对于 LLM 调用密集型任务（如 QA 生成），这种并行非常有用——每个子 agent 独立调用 LLM API，天然并发。

### 5.5 修正后的 Gap 评估

基于上述三个机制，重新评估与 DataFlow 的差距：

#### ~~Gap-2：无预置 operator 库~~ → 已解决

预置脚本（§5.4 机制 1）完全等价于 operator 注册表。脚本参数签名等价于 input/output keys。新增 operator 只需新增脚本。

**残留差异**：DataFlow 有 compile() 时自动从 `run()` 签名推导 input/output keys 的能力。Mission Driver 的脚本参数需要人工在 plan 中声明。但这不是能力缺失——只是自动化程度不同。

#### Gap-1：无编译期数据 schema 验证 → 部分缓解

DataFlow compile() 在执行前检查 key 完整性。Mission Driver 没有等价的自动验证。

**缓解**：
- 预置脚本可以在启动时自检输入文件 schema（如 `extract_text.py --validate-only`）
- REVIEW_PLANS 的 sub-agent 在 plan 审查阶段检查 Phase 间的 schema 一致性
- 但这仍然是运行时/审查时检查，不是编译期自动验证

#### Gap-4：无批处理/streaming 原语 → 部分解决

子 agent 并行分派（§5.4 机制 3）解决了 LLM 密集型任务的并行问题。但对于**单进程内的 streaming generator**（如 DataFlow 的 `StreamBatchedPipelineABC.iter_chunks()`），Mission Driver 没有等价物。

**残留差异**：
- LLM 密集型任务（QA 生成、质量评估）：子 agent 并行**有效**
- IO 密集型大文件处理（GB 级 CSV 流式读取）：预置脚本内部可以用 `pd.read_json(chunksize=N)`，但这依赖脚本实现，不是引擎保证
- 对比 DataFlow 的 `StreamBatchedPipelineABC`：引擎层面的 streaming 抽象，Mission Driver 没有

#### Gap-3：无 DAG 可视化 → 非核心

不变。plan 可嵌入 Mermaid，但无交互式编辑器。这不是 harness 的核心能力。

#### 不算 Gap 的差异

| 差异 | 说明 |
|------|------|
| 多轮迭代 | Mission Driver **优于** DataFlow（DRAFT→EXEC→AUDIT→REDRAFT） |
| 治理审计 | Mission Driver **优于** DataFlow（CLOSURE_AUDIT / DEEP_AUDIT） |
| 任务编排 | Mission Driver **优于** DataFlow（roadmap 驱动） |
| 算子积累 | DataFlow 预置丰富；Mission Driver 通过预置脚本 + AGE 晋升阶梯（prose→plan模板→skill→脚本→MCP 工具）积累 |
| 并行处理 | DataFlow 有 Ray 算子级并行；Mission Driver 有子 agent 任务级并行（对 LLM 密集型任务等效） |

### 5.6 修正后的小结

用 Mission Driver 机制实现数据处理 harness **可行，不需要修改引擎，且能力比之前评估的强得多**。三层执行机制覆盖了 harness 的核心需求：

| 执行机制 | 等价的 DataFlow 能力 | 覆盖度 |
|----------|---------------------|--------|
| 预置脚本 | Operator 注册表 + input/output keys | **完全覆盖** |
| Skill 多步骤 | Pipeline 编排 + 条件逻辑 | **完全覆盖**（且更灵活） |
| 子 agent 并行 | Ray 加速（LLM 密集型） | **等效覆盖**（LLM 场景） |

残留差距仅有 2 个：
1. **编译期 schema 验证**（DataFlow compile() 自动检查，Mission Driver 靠运行时自检 + 审查）
2. **引擎级 streaming**（DataFlow 有 StreamBatchedPipelineABC，Mission Driver 靠脚本内部 chunksize）

这两个差距都不是阻塞性的。Mission Driver 在多轮迭代、治理审计、任务编排方面优于 DataFlow。

---

## 5.7 AGE 哲学在数据 harness 场景的映射

> 之前版本犯了根本性错误——建议 agent "即兴生成临时脚本"。这完全违背了 AGE 的设计思想。本节从 AGE 的核心理论出发，重新审视数据 harness 的架构。

### 吸引子：数据 harness 应收敛到什么稳定结构

AGE 的核心是 `state space → attractor → trajectory → control`。在数据 harness 场景中：

**吸引子 = 项目定义的"数据处理系统应长期维持的结构不变量"**，具体包括：

1. **可用的处理脚本集**（`scripts/` 下的预置脚本）——定义"系统可靠地能做哪些变换"。每个脚本有固定的输入/输出 schema 约定，等价于 DataFlow operator 的 `input_keys`/`output_keys` 契约。这不是临时生成的，而是项目的**结构承诺**。
2. **plan 格式约定**（`00-plan-guide.md`）——定义"数据处理方案必须如何描述"。Phase 模板、schema 声明、Exit Criteria 都是吸引子的一部分。
3. **质量标准**（Exit Criteria 模板 + audit prompt）——定义"什么算好的数据"。
4. **处理边界**（拒绝的方案及原因）——如"禁止用正则提取医学实体，必须用 NER 模型"。

### 轨迹：数据处理的历史记录

| 轨迹载体 | 内容 | 变化速度 |
|----------|------|---------|
| `plans/` | 每次数据处理方案（Phase 切片 + 脚本引用 + 质量标准） | 周级 |
| `audits/` | 每次质量审计的发现（哪些通过、哪些偏离） | 周级 |
| `logs/` | 每日执行日志（哪个 plan 执行了什么、结果如何） | 日级 |
| `bugs/` | 数据质量缺陷的根因分析 | 事件驱动 |

轨迹的可读投影让未来的 session 能恢复"到上次为止做了什么、为什么这样做"。

### 控制：保持轨迹在吸引域内

| 控制机制 | 实现 | 强度 |
|----------|------|------|
| **路由控制** | DRAFT prompt 中的脚本注册表 → agent 只能引用已有脚本或按规格正式实现新脚本 | 引导 |
| **审查控制** | REVIEW_PLANS 的独立 sub-agent 审查 plan 的 schema 一致性和方案合理性 | 半自动 |
| **脚本检查** | CLOSURE_SCRIPT_CHECK 对照 Exit Criteria 做量化断言 | 自动 |
| **独立审计** | CLOSURE_AUDIT 的独立 sub-agent 回到输出数据做质量审查 | 人工级 |
| **构建验证** | BUILD_VERIFY 运行 `commands.test`（质量检查脚本） | 自动 |
| **深度审计** | DEEP_AUDIT 多维度质量审计 + 开放式对抗审查 | 人工级 |

### 工具（脚本）在 AGE 中的正确定位

**预置脚本不是临时工具，而是吸引子的实现。** 它们定义了"系统可靠地能做哪些数据变换"——正如 nop-entropy 的 AGENTS.md 定义了"AI 可以做什么、不可以做什么"。

当遇到没有现成脚本的处理需求时，AGE 规定的流程是：

```
Level 0: 在 plan 中以"脚本规格"描述需求
         （输入 schema、输出 schema、处理规则、验收标准）
  ↓ EXECUTE agent 根据规格正式实现脚本
Level 1: 脚本作为 plan 的交付物写入 scripts/，成为永久 operator
  ↓ 同一处理模式在多个 plan 中反复使用
Level 2: 提取为 skill（封装调用约定 + 参数验证 + 错误处理）
  ↓ 需要跨项目复用
Level 3: 提取为 MCP 工具或独立 Python 包
```

**关键区别**：这不是"临时脚本 → 正式脚本"的事后提升，而是从一开始就以正式规格驱动实现。每个新脚本都是吸引子的**增量扩展**——系统可靠能做的事变多了。同时，新脚本的产生会反向更新 DRAFT prompt 中的脚本注册表，让后续 session 知道它存在。

---

## 六、具体实例：医疗 QA 数据集生成 — 完整文件清单与内容

> 以"从医疗论文 JSONL 生成 QA 训练集"为例，展示用 Mission Driver 实现 harness 需要的全部文件。**不需要修改引擎**，所有定制通过 mission config + prompt 覆盖 + 预置脚本实现。

### 6.1 文件总览

```
项目根目录/
├── missions/
│   ├── medical-qa.json                    # ① mission 配置
│   ├── prompts/
│   │   ├── execute.md                     # ② EXECUTE prompt（薄：指向 plan + scripts/index）
│   │   ├── draft.md                       # ③ DRAFT prompt（薄：指向 roadmap + scripts/index）
│   │   └── closure-script-check.md        # ④ 质量检查 prompt（薄：指向 plan Exit Criteria）
│   └── flows/                             # （不需要自定义 flow）
├── docs/
│   ├── backlog/
│   │   └── medical-qa-roadmap.md          # ⑤ 任务清单
│   └── plans/
│       └── medical-qa/                    # plan 目录
│           └── 00-plan-guide.md           # ⑥ plan 格式约定（薄）
├── scripts/
│   └── medical-qa/
│       ├── index.md                       # ⑦ 脚本注册表（唯一真值源）
│       ├── extract_text.py
│       ├── generate_qa.py
│       ├── filter_quality.py
│       └── check_quality.py
└── data/
    └── raw_medical_papers.jsonl
```

共 11 个文件。核心设计原则：**prompt 是薄的，只指向信息源；信息真值源唯一（scripts/index.md 是注册表，roadmap 是任务源，plan 是执行方案）。**

### 6.2 ① mission 配置：`missions/medical-qa.json`

```json
{
  "extends": "base",
  "name": "medical-qa",
  "description": "从医疗论文生成高质量 QA 训练数据集（数据处理 mission，非代码开发）",
  "roadmapPath": "docs/backlog/medical-qa-roadmap.md",
  "plansDir": "docs/plans/medical-qa",
  "planGuide": "docs/plans/medical-qa/00-plan-guide.md",
  "auditsDir": "docs/audits/medical-qa",
  "contextDir": "docs/context",
  "moduleDir": ".",
  "prompts": {
    "multiAudit": "docs/skills/data-quality-audit-prompt.md",
    "openAudit": "docs/skills/open-ended-data-review-prompt.md"
  },
  "commands": {
    "test": "python scripts/medical-qa/check_quality.py --report output/quality_report.json --min-records 500",
    "build": "echo 'medical-qa: no build step (data processing mission)'",
    "typecheck": "python -c \"import json; [json.loads(l) for l in open('data/raw_medical_papers.jsonl')]\" && echo 'input data schema OK'",
    "lint": "echo 'medical-qa: no lint step'"
  },
  "commitFormat": "data(medical-qa): <description>"
}
```

关键设计：
- **commands.test** 是质量检查脚本——BUILD_VERIFY 和 CHECK 会运行它，如果数据集行数 < 500 就失败
- **commands.typecheck** 是输入数据 schema 验证——CHECK 阶段先确保输入文件可解析
- **不需要 commands.build**（无编译步骤），但保留占位 echo
- **prompts.multiAudit / openAudit** 指向数据质量审计 prompt（不是代码审计）

### 6.3 ② EXECUTE prompt：`missions/prompts/execute.md`

薄 prompt——不硬编码脚本列表，只指向信息源：

```markdown
---
name: execute
description: Execute data processing plan
---

# Execution

读取当前 plan 文件，执行其中描述的数据处理步骤。

## 流程

1. 读 plan，理解每个步骤要做什么、调用什么脚本、参数是什么。
2. 如果 plan 引用了 skill，使用该 skill 执行（skill 内部会调用脚本）。
3. 如果 plan 中有可并行的步骤，派生子 agent 并行处理。
4. 每步完成后更新 plan 的 checkbox。
5. 全部完成后运行 `scripts/medical-qa/check_quality.py` 生成质量报告。

## 遇到 plan 引用了不存在的脚本？

按 AGE 工具晋升阶梯处理：根据 plan 中的脚本规格正式实现脚本，
写入 `scripts/medical-qa/`，并更新 `scripts/medical-qa/index.md`。
禁止生成临时脚本。
```

### 6.4 ③ DRAFT prompt：`missions/prompts/draft.md`

薄 prompt——不硬编码脚本列表，让 AI 自己读 index 动态组织：

```markdown
---
name: draft
description: Draft data processing plans from roadmap
---

# Pipeline Design

读取 roadmap 中第一个 `todo` 工作项，为它起草数据处理 plan。

## 信息源

1. **可用工具**：读 `scripts/medical-qa/index.md` 了解现有脚本的名称、功能、参数约定。
2. **任务目标**：读 roadmap 工作项了解要做什么、成功标准是什么。
3. **历史方案**：读 `docs/plans/medical-qa/` 下已有 plan 了解之前怎么做的。

## 设计原则

- 根据 index 中列出的工具和任务需求，动态决定使用哪些脚本、如何组织步骤。
- 如果有固化的多步模式已提取为 skill，直接在 plan 中引用 skill。
- 如果现有工具不够，在 plan 中写明新脚本的规格（输入/输出 schema + 处理规则），
  EXECUTE agent 会正式实现。
- Exit Criteria 必须包含量化指标（行数/质量分/schema 完整性）。
```

**关键**：prompt 中不列脚本清单。脚本清单在 `scripts/medical-qa/index.md` 中维护。新增脚本只需更新 index，DRAFT agent 下次自动发现。prompt 永远不需要改。

### 6.5 ④ 自定义质量检查 prompt：`missions/prompts/closure-script-check.md`

```markdown
---
name: closure-script-check
description: Validate data quality against exit criteria
---

# Data Quality Closure Check

读取当前 plan 的 Exit Criteria 和 Closure Gates，
对照 `output/quality_report.json` 逐项断言。

## 检查流程

1. 读取 `output/quality_report.json`（由 EXECUTE 阶段的汇总脚本生成）
2. 读取 plan 中每个 Phase 的 Exit Criteria
3. 逐项对照：
   - 行数 >= 阈值？
   - 平均质量分 >= 阈值？
   - 空值率 <= 阈值？
   - schema 字段齐全？
4. 如果全部通过 → 标记 Closure Gates checkbox 为 [x]，返回 pass
5. 如果有未通过项 → 记录具体失败原因，返回 fail

## 输出

将检查结果写入 plan 的 Closure Gates 部分，更新 checkbox。
```

### 6.6 ⑤ Roadmap：`docs/backlog/medical-qa-roadmap.md`

```markdown
# Medical QA Dataset Roadmap

## Work Items

| Status | Item | Target |
|--------|------|--------|
| todo | 医疗论文 QA 数据集 v1（基础流水线） | ≥ 500 条 QA 对，平均质量 ≥ 0.6 |
| todo | 数据集质量提升（加入清洗+过滤） | ≥ 1000 条，平均质量 ≥ 0.7 |
| planned | 多领域扩展（心血管+内分泌） | ≥ 3000 条，覆盖 2 个子领域 |

## Milestones

- [ ] M1: v1 数据集产出（≥ 500 条）
- [ ] M2: 质量提升版（≥ 1000 条，质量 ≥ 0.7）
- [ ] M3: 多领域版（≥ 3000 条）
```

引擎的 DRAFT_PLANS 读第一个 `todo` 项起草 plan。EXECUTE 执行。AUDIT 检查。完成后 roadmap 项变为 `done`，引擎进入下一个 `todo`。

### 6.7 ⑥ Plan 格式约定：`docs/plans/medical-qa/00-plan-guide.md`

```markdown
# Medical QA Plan 格式约定

## Phase 模板

每个 Phase 必须包含以下字段：

### Phase N - <名称>
Status: planned
处理脚本: scripts/medical-qa/<script>.py
参数: --input <path> --output <path> [--model <model>]

输入 schema: {字段名: 类型}
输出 schema: {字段名: 类型}

Exit Criteria:
- [ ] 可量化的检查项

## Phase 间数据流

Phase N 的 `--output` 路径 = Phase N+1 的 `--input` 路径。
所有中间文件放在 `data/processed/` 目录。
最终输出放在 `output/` 目录。

## Closure Gates 模板

- [ ] 最终数据集行数 >= <阈值>
- [ ] 平均质量分 >= <阈值>
- [ ] 无空值
- [ ] output/quality_report.json 存在
```

### 6.8 ⑦ 脚本注册表：`scripts/medical-qa/index.md`

**这是唯一真值源**——prompt 不硬编码脚本列表，AI 读这个文件了解可用工具：

```markdown
# Medical QA Scripts

## 可用脚本

### extract_text.py
功能: 从原始 JSONL 提取并清洗文本内容
用法: python extract_text.py --input <in.jsonl> --output <out.jsonl> [--lang zh,en]
输入字段: raw_content (str)
输出字段: clean_text (str), title (str)
依赖: 无

### generate_qa.py
功能: 用 LLM 从文本生成 QA 对
用法: python generate_qa.py --input <in.jsonl> --output <out.jsonl> --model <model> --api-key-env <ENV>
输入字段: clean_text (str)
输出字段: question (str), answer (str), source_title (str)
依赖: OpenAI API

### filter_quality.py
功能: 用 LLM 对 QA 对质量打分并过滤
用法: python filter_quality.py --input <in.jsonl> --output <out.jsonl> --threshold 0.6 --model <model>
输入字段: question (str), answer (str)
输出字段: quality_score (float) + 原有字段
依赖: OpenAI API

### check_quality.py
功能: 统计数据集质量指标，生成 quality_report.json
用法: python check_quality.py --output-dir <dir> --report <report.json> [--min-records N]
输出: quality_report.json（record_count, avg_quality, schema_check 等）
```

**自维护机制**：EXECUTE agent 实现新脚本时，同步更新此文件。DRAFT agent 下次读到此文件就自动知道新工具存在。prompt 永远不需要改。

### 6.9 预置脚本示例（`scripts/medical-qa/`）

预置脚本 = operator。每个脚本遵循统一接口：`--input`/`--output`/`--model`/`--api-key-env`。

以 `generate_qa.py` 为例（伪代码，展示接口约定）：

```python
#!/usr/bin/env python3
"""QA pair generator — equivalent to DataFlow QAGenerator operator."""
import argparse, json, os, sys

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, help="Input JSONL file")
    parser.add_argument("--output", required=True, help="Output JSONL file")
    parser.add_argument("--model", default="gpt-4o", help="LLM model")
    parser.add_argument("--api-key-env", default="OPENAI_API_KEY")
    parser.add_argument("--qa-per-text", type=int, default=3)
    args = parser.parse_args()

    api_key = os.environ.get(args.api_key_env, "")
    if not api_key:
        print(f"ERROR: {args.api_key_env} not set", file=sys.stderr)
        sys.exit(2)

    # Read input
    with open(args.input) as f:
        records = [json.loads(line) for line in f]

    results = []
    for rec in records:
        text = rec.get("clean_text", "")
        # Call LLM to generate QA pairs (omitted)
        qa_pairs = call_llm_for_qa(text, args.model, api_key, args.qa_per_text)
        for qa in qa_pairs:
            results.append({"question": qa["q"], "answer": qa["a"], "source_title": rec.get("title", "")})

    # Write output
    with open(args.output, "w") as f:
        for rec in results:
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")

    print(f"Generated {len(results)} QA pairs → {args.output}")

if __name__ == "__main__":
    main()
```

### 6.9 引擎运行全过程（端到端追踪）

以"医疗论文 QA 数据集 v1"为例：

```
1. 用户启动: ./tools/mission-driver.sh run medical-qa

2. CHECK (运行 commands.typecheck + commands.test)
   → python -c "import json; ..." 验证输入 JSONL 可解析 ✓
   → python check_quality.py 检查现有输出（首次运行为空，SKIP）

3. REVIEW_PLANS (扫描 plansDir 有无 draft/active plan)
   → 首次运行为空 → passthrough

4. DRAFT_PLANS (agent 读 roadmap 第一个 todo + draft.md prompt)
   → 读 roadmap: "医疗论文 QA 数据集 v1（基础流水线）"
   → 读 draft.md: 了解可用脚本（extract_text / generate_qa / filter_quality）
   → 生成 plan-001.md:
       Phase 1: extract_text.py (raw → clean_text)
       Phase 2: generate_qa.py (clean_text → QA 对)
       Phase 3: filter_quality.py (QA 对 → 过滤后数据集)
       Exit Criteria: ≥ 500 条，质量 ≥ 0.6
   → sub-agent 审查 plan → 提升 Status 为 active

5. EXEC_PLANS (对 plan-001 启动 plan-execution 子流)

   5a. EXECUTE (agent 读 plan-001 + execute.md prompt)
       → Phase 1: python scripts/medical-qa/extract_text.py
           --input data/raw_medical_papers.jsonl
           --output data/processed/clean_text.jsonl
           → [x] 成功，1200 条
       → Phase 2: python scripts/medical-qa/generate_qa.py
           --input data/processed/clean_text.jsonl
           --output data/processed/qa_pairs.jsonl
           --model gpt-4o
           → 可选并行：用子 agent 分片处理（每片 200 条）
           → [x] 成功，3600 条
       → Phase 3: python scripts/medical-qa/filter_quality.py
           --input data/processed/qa_pairs.jsonl
           --output output/final_dataset.jsonl
           → [x] 成功，1850 条（过滤掉 1750 条低质量）
       → 汇总: python check_quality.py → output/quality_report.json
           {"record_count": 1850, "avg_quality": 0.68, ...}

   5b. CLOSURE_SCRIPT_CHECK (agent 读 closure-script-check.md prompt)
       → 读 quality_report.json
       → 对照 Exit Criteria: 1850 >= 500 ✓, 0.68 >= 0.6 ✓
       → 标记 Closure Gates [x] → pass

   5c. CLOSURE_AUDIT (独立 sub-agent)
       → 读 plan-001 + output/quality_report.json
       → 审查: QA 对的多样性如何？是否有重复模板？
       → 发现: 15% 的 QA 对问题模式重复（"什么是X？"）
       → 写 audit-001.md (Audit Status: open)

   5d. BUILD_VERIFY (运行 commands.test)
       → python check_quality.py --report output/quality_report.json --min-records 500
       → pass ✓

6. DRAFT_PLANS (检查 roadmap 下一个 todo)
   → "数据集质量提升（加入清洗+过滤）"
   → 但 DEEP_AUDIT 发现了 audit-001.md...

7. DEEP_AUDIT (扫描 open audit)
   → 读 audit-001.md: "15% QA 对问题模式重复"
   → 起草 plan-002: 增加 prompt 多样性约束 + 重复检测
   → plan-002 Status: draft

8. 回到 REVIEW_PLANS → 审查 plan-002 → active
   → EXEC_PLANS → 执行 plan-002（在 plan-001 输出基础上改进）
   → ...
   → 直到 roadmap 所有 todo 完成 + maxAuditRounds 耗尽
```

### 6.11 关键设计原则

**prompt 是薄的，信息真值源唯一**：

| 信息 | 真值源 | prompt 怎么用 |
|------|--------|-------------|
| 可用工具（operator 注册表） | `scripts/medical-qa/index.md` | prompt 只说"读 index" |
| 任务目标 | `docs/backlog/medical-qa-roadmap.md` | prompt 只说"读 roadmap 第一个 todo" |
| 执行方案 | `docs/plans/medical-qa/*.md` | prompt 只说"读 plan 执行" |
| 质量标准 | plan 的 Exit Criteria | prompt 只说"对照 Exit Criteria 断言" |

新增脚本时只更新 `scripts/medical-qa/index.md`——DRAFT agent 下次自动发现。**prompt 永远不需要改**。

| 设计点 | 机制 | AGE 角色 |
|--------|------|---------|
| **工具注册表** | `scripts/medical-qa/index.md`（自维护） | 吸引子：定义系统能做什么 |
| **plan 动态组织** | AI 读 index + roadmap 后自行决定步骤 | 轨迹：AI 智能组织，非硬编码 |
| **skill 固化** | 多步模式稳定后提取为 skill | 控制层：封装可靠调用序列 |
| **子 agent 并行** | EXECUTE prompt 中指示分片 | 控制层：任务级并行 |
| **质量门控** | commands.test = check_quality.py | 控制层：自动断言 |
| **独立审计** | CLOSURE_AUDIT + DEEP_AUDIT | 控制层：人工级验证 |
| **多轮迭代** | roadmap todo → plan → audit → 改进 | 轨迹：闭环优化 |
| **断点恢复** | plan checkbox | 控制：声明式检查点 |

**不需要自定义 flow**。内置 flow 完全适配。所有定制通过 3 个薄 prompt + mission config + scripts/index.md 完成。

---

## 七、总结

### 7.1 AGE 理论框架下的 harness 实现

用 Mission Driver 实现 dataflow harness 的本质是构建一个 AGE 系统：

```
吸引子 (Attractor):
  scripts/ 下的预置脚本集 = 系统可靠能做的数据变换
  plan 格式约定 = 数据处理方案必须如何描述
  质量标准 = 什么算好的数据
  → 这些定义了"系统应收敛到什么稳定结构"

轨迹 (Trajectory):
  plans/ = 数据处理方案的历史
  audits/ = 质量审计的发现
  logs/ = 执行记录
  → 这些记录了"系统怎么走到现在的"

控制 (Control):
  DRAFT prompt 中的脚本注册表 = 路由控制（引导 agent 使用已有工具）
  REVIEW_PLANS 独立审查 = 审查控制
  CLOSURE_SCRIPT_CHECK = 自动断言
  CLOSURE_AUDIT 独立子代理 = 人工级验证
  DEEP_AUDIT = 深度质量审计
  → 这些保持"轨迹在吸引域内"
```

### 7.2 核心结论

- **吸引子先于线束**：预置脚本集（吸引子的实现）定义系统能力边界，plan 引用已有脚本，新需求以规格驱动正式实现（不是即兴生成临时脚本）
- **三层执行机制**：预置脚本 = operator 注册表，skill = 多步骤编排，子 agent = 任务级并行
- **不需要自定义 flow**：内置 CHECK→REVIEW→EXEC→DRAFT→AUDIT 完美映射 state space→trajectory→control
- **残留差距仅 2 个**（均非阻塞）：编译期 schema 验证、引擎级 streaming
- **3 个增量优势**：多轮迭代、治理审计、任务编排

---

## 八、Mission Driver 作为通用目标驱动引擎 — 架构演进方向

> 上述分析聚焦"数据处理 harness"场景。但实际上 Mission Driver 的 flow 是**完全通用的**——CHECK→REVIEW→EXEC→DRAFT→AUDIT 映射到任何目标驱动任务。本节讨论一个架构增强方向：通过 `promptsDir` 配置 + goal-driven wrapper，让 Mission Driver 成为所有类型工作的统一执行引擎。

### 8.1 flow 的通用性

内置 flow 的五个步骤不是代码开发专用的——它们是任何"设计→执行→检查→迭代"循环的通用骨架：

| Step | 代码开发 | 数据处理 | 文档对比分析 | 调研 |
|------|---------|---------|------------|------|
| **CHECK** | mvn test 绿 | 数据文件+脚本可用 | 待分析文档存在 | 参考资料可达 |
| **DRAFT** | 代码变更方案 | 数据处理 pipeline 方案 | 分析维度+对比方案 | 调研提纲+信息源 |
| **EXEC** | 改代码 | 调脚本处理数据 | 调脚本对比/生成表格 | 读资料+总结 |
| **CLOSURE** | 测试通过 | 数据质量断言 | 分析完整性检查 | 覆盖度检查 |
| **AUDIT** | 多维度代码审计 | 数据质量趋势 review | 分析深度审查 | 结论可靠性审查 |

**flow 不需要改。变的只是 prompt 告诉 agent "你在做什么类型的事"。**

### 8.2 `promptsDir` 配置增强

#### 当前 prompt 加载链

```
① <missionsDir>/prompts/<name>.md     ← 项目级覆盖
② tools/mission-driver/prompts/<name>.md  ← 内置默认
```

#### 增强后的加载链

```
① <promptsDir>/<name>.md              ← 新增：任务类型级 prompt 集
② <missionsDir>/prompts/<name>.md     ← 项目级覆盖
③ tools/mission-driver/prompts/<name>.md  ← 内置默认
```

mission.json 新增可选字段：

```json
{
  "name": "dataflow-vs-nop-stream-analysis",
  "promptsDir": "missions/prompts/analysis",
  "flowName": "mission-driver"
}
```

**向后兼容**：不设 `promptsDir` 时，行为与当前完全一致。设了就多一层高优先搜索路径。

#### 任务类型 → prompt 集映射

```
missions/prompts/
  code-dev/              ← 代码开发 prompt 集
    execute.md             "改代码，跑测试"
    draft.md               "从 roadmap 起草代码变更 plan"
    closure-script-check.md "跑 test + lint"
  data-processing/       ← 数据处理 prompt 集
    execute.md             "读 plan，调 scripts/ 下的脚本"
    draft.md               "读 scripts/index.md，拟制处理 pipeline"
    closure-script-check.md "对照 Exit Criteria 检查数据质量"
  analysis/              ← 对比分析 prompt 集
    execute.md             "读 plan，执行分析步骤，生成对比表格"
    draft.md               "拟制分析维度和对比方案"
    closure-script-check.md "检查分析覆盖度和深度"
  research/              ← 调研 prompt 集
    execute.md             "读 plan，搜索+阅读+总结"
    draft.md               "拟制调研提纲和信息源"
    closure-script-check.md "检查调研覆盖度和结论可靠性"
```

每个 mission 只需设 `promptsDir` 指向对应的 prompt 集。新增任务类型只需新增一个 prompt 目录——**不需要改 flow，不需要改引擎**。

如果某个 prompt 在 `promptsDir` 中不存在（比如 analysis 场景不需要自定义 closure-audit prompt），引擎自动回退到上级目录的 prompt，再回退到内置默认。**部分覆盖、整体回退**。

### 8.3 Goal-Driven Wrapper 架构

在 Mission Driver 之上包装一层，实现"用户说目标 → 自动澄清 → 自动生成 roadmap + mission → 自动运行"：

```
用户: "分析 DataFlow 和 nop-stream 的区别"
    │
    ▼
┌─────────────────────────────────────┐
│  Grill Me (deep-interview skill)     │
│  澄清需求：                           │
│  - 分析哪些维度？（架构/性能/场景）    │
│  - 产出什么格式？（表格/报告/PPT）     │
│  - 深度还是概要？                     │
│  - 有什么约束？（字数/时间/参考）      │
│  → 输出: clarified spec              │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Task Type Detection                  │
│  从 clarified spec 判断任务类型：      │
│  - "分析/对比" → analysis             │
│  - "处理/生成数据" → data-processing  │
│  - "开发/修改代码" → code-dev         │
│  - "调研/总结" → research             │
│  → 输出: task_type + promptsDir       │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Roadmap + Mission Generation         │
│  从 clarified spec 生成：             │
│  - roadmap.md（工作项 + 成功标准）     │
│  - mission.json（含 promptsDir）      │
│  → 输出: 可执行的 mission 配置         │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  Mission Driver Auto-Run              │
│  ./tools/mission-driver.sh run <name> │
│  CHECK → REVIEW → EXEC → DRAFT → AUDIT│
│  → 直到 roadmap 完成                  │
└─────────────────────────────────────┘
```

#### 实现路径

1. **Grill Me**：复用已有的 `deep-interview` skill，增强为面向 mission 的需求澄清——输出不是自由文本，而是结构化的 mission spec（目标/成功标准/约束/预估工作量）
2. **Task Type Detection**：简单的分类逻辑（keyword 匹配或 LLM 分类），输出 `promptsDir` 路径
3. **Roadmap + Mission Generation**：复用已有的 `draft` 命令，输入从"用户直接给"变为"grill me 的输出"
4. **Auto-Run**：直接调用 `./tools/mission-driver.sh run <name>`

这四步中，只有第 1 步（grill me → mission spec）和第 2 步（task type detection）是新增逻辑。第 3、4 步完全复用现有机制。

### 8.4 这个方向的 AGE 解读

Goal-driven wrapper 本质上是 AGE 理论的延伸：

```
用户目标 (模糊)
  → Grill Me 澄清 (收敛需求空间)
  → 生成 roadmap (创建吸引子)
  → 选择 promptsDir (匹配领域特化)
  → 运行 mission (轨迹向吸引子收敛)
  → 审计 (控制层校正)
```

Grill Me 是**需求空间的吸引子**——它把模糊的用户意图收敛到足够精确的 mission spec，使得后续的 roadmap 生成有明确的输入。这正是 deep-interview skill 的设计初衷。

`promptsDir` 是**领域特化的控制面**——不同领域有不同的"好 plan 是什么样的"、"执行怎么做"、"质量怎么检查"标准。通过 prompt 集切换，而非 flow 切换，保持了引擎的通用性。

### 8.5 小结

- **flow 是通用的**：CHECK→REVIEW→EXEC→DRAFT→AUDIT 适用于任何目标驱动任务
- **`promptsDir` 是关键增强**：向后兼容，使不同任务类型共享引擎但各有专用 prompt 集
- **Goal-driven wrapper 是自然延伸**：grill me → 检测类型 → 生成 mission → 自动运行
- **所有工作都可以通过 mission driver 驱动**：代码开发、数据处理、文档分析、调研——区别只在 prompt 集，不在引擎


## References

- OpenDCAI/DataFlow Pipeline 源码: `~/ai/dataflow/dataflow/pipeline/Pipeline.py`
- DataFlow-WebUI Harness 架构: `~/ai/dataflow/DataFlow-WebUI/AGENTS.md`
- DataFlow-WebUI 执行引擎: `~/ai/dataflow/DataFlow-WebUI/backend/app/services/dataflow_engine.py`
- DataFlow Storage 实现: `~/ai/dataflow/dataflow/utils/storage.py`
- Mission Driver SKILL: `.opencode/skills/mission-driver/SKILL.md`
- Mission Config Schema: `.opencode/skills/mission-driver/references/mission-config-schema.md`
- DataFlow-Harness Paper: https://huggingface.co/papers/2607.16617
- AGE 分析: `ai-dev/analysis/2026-06-07b-nop-ai-agent-age-support-gap-supplement.md`

---

## 附录：审查过程与教训

### v3.0-v4.0 两轮审查的局限性

v3.0-v4.0 版本进行了两轮独立子 Agent 审查（Round 1 发现 5 P1 + 6 P2，Round 2 确认收敛并订正 3 P2）。两轮审查的源码分析是严谨的（dataflow_engine.py 逐行核实、mission-config-schema 全量阅读、storage.py/Pipeline.py 交叉验证），但**两轮审查都建立在一个错误的前提假设上**：以为 Mission Driver 需要通过 HTTP API 或同进程协作来驱动 DataFlow。

这个错误假设导致：
- 分析出"7 个子系统缺失"（Operator Registry / LLM Serving / Storage / 动态代码 / Prompt 模板 / 参数强制 / Database Manager）
- 分析出"进程模型根本冲突"（serving 跨进程共享不可行、batch/stream generator 不可跨进程）
- 提出双路径架构（路径 A 黑盒委托 via HTTP API / 路径 B 算子级编排）

**实际上这些全是伪命题**。Mission Driver 的协作媒介是文件（plan/roadmap/audit），不是 API。EXECUTE step 的 agent 读 plan 中的 pipeline config → 生成 Python 脚本 → `python script.py` 执行，整个 pipeline 在一个子进程内跑完。serving/storage/batch 全在该子进程内，不存在跨进程共享问题。

### 教训

1. **先理解协作机制，再分析能力差距**。v3/v4 版本跳过了对 Mission Driver 文件转接机制的理解，直接假设 API 协作，导致全部分析偏离方向。
2. **审查者也会犯同样的错误**。两轮独立审查都没有质疑"API 协作"的前提假设——因为 dataflow_engine.py 和 WebUI 的架构确实暗示了 API 模式。但 Mission Driver 的设计哲学是文件驱动的，与 API 驱动完全不同。
3. **源码严谨性不等于结论正确性**。v4.0 的源码引用全部经核实准确，但基于错误前提的准确引用仍然导向错误结论。
