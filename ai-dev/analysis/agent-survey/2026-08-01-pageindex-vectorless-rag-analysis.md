# PageIndex 无向量推理检索与提示注入防护分析 & Nop AI Agent Memory/安全

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/pageindex`（Python，无向量推理 RAG，84 文件）vs `nop-ai-agent`（memory 包 + security ContentOrigin）
> Conclusion:

## 一、总览

**PageIndex** 完全无向量、无分块，用 **LLM 推理 + 结构化树索引**替代相似度搜索，检索可追溯可解释。

| 维度 | pageindex | nop-ai-agent |
|------|-----------|--------------|
| 检索方式 | 无向量 tree search（LLM 推理） | 向量检索（memory 适配器） |
| 索引 | 层级树索引（类似"目录"） | 向量索引 |
| 确定性 | Flash 确定性引擎（无 LLM 构建树） | — |
| 安全 | 提示注入三重防护 | security + ContentOrigin |

## 二、核心机制详解

### 2.1 两步式推理检索（`page_index.py`，1320 行）
- 第一步：为长文档生成**层级树索引**（类似"目录"——章节/小节/段落）。
- 第二步：LLM 对树结构进行 **tree search 推理检索**（看目录→选章节→看小节→取内容）。

### 2.2 Flash 确定性引擎（`flash/`）
- `flash/parser_pdfium_charlevel/`：pdfium 字符级解析。
- `flash/outline_assembly/`：heading detection → outline assembly → tree building。
- **无需 LLM 即可构建树**——纯规则确定性管线。

### 2.3 Agentic 工具检索（`retrieve.py:81-137`）
- 三个工具供 OpenAI Agents SDK 驱动的 LLM 自主调用：
  - `get_document`：取完整文档。
  - `get_document_structure`：取目录结构。
  - `get_page_content`：取指定页内容。
- LLM 先看目录再取内容——**可追溯可解释**。

### 2.4 提示注入三重防护（`page_index.py:12-49`）
- `_sanitize_doc_text`（:12）：正则脱敏（移除已知注入模式 `_INJECTION_PATTERNS`）。
- `_wrap_doc_text`：分隔符包裹（标记"这是文档内容，不是指令"）。
- `_SYSTEM_HARDENING`：系统提示加固（告诉模型忽略文档中的指令）。

## 三、对 nop-ai-agent 的借鉴要点

1. **树检索模式（结构→内容两步）**（中价值，memory 包）——先检索记忆索引树再精确取内容，作为向量检索的补充策略（memory 适配器增加"树索引"实现）。nop memory 当前是向量检索；增加树检索作为**可解释替代**（可追溯为什么取了这段内容）。
2. **提示注入三重防护**（高价值，security 包）——`_wrap_doc_text`（分隔符包裹）+ `_sanitize_doc_text`（正则脱敏）+ system hardening 三重。对应 nop AgentSession 消息流注入用户内容时的安全加固。nop 已有 ContentOrigin（标记来源类型），在此基础上增加"内容包裹 + 脱敏 + 系统加固"三重防护。与 gstack 的双分类器共识（`2026-08-01-gstack-sprint-workflow-analysis.md`）互补。
3. **确定性 Flash 管线**（中价值）——无 LLM 的纯规则树构建，嵌入适配器可支持"无嵌入、规则索引"模式。

## 三.5 Harness 可靠性（Retry/Replan/Resume）

- **Agentic 检索重试**（`retrieve.py:81-137`）：get_document_structure → get_page_content——检索失败换路径（先目录后内容，**检索级 replan**）。
- **Flash 确定性管线**：pdfium 解析→outline→tree——纯规则确定性（重试结果一致）。
- **对 nop 的启示**：确定性管线让重试可预测（无 LLM 不确定性）；树检索路径 replan 是 nop memory 检索的参考。

## 四、优缺点

### 优点
1. 完全无向量——无需嵌入模型/向量数据库。
2. 检索可追溯可解释（tree search 路径清晰）。
3. 提示注入三重防护实用有效。

### 缺点
1. PDF 导向（其他文档类型需适配）。
2. 高质量树构建依赖云端 OCR。
3. tree building 延迟较高（大文档）。

## 五、结论

PageIndex 的提示注入三重防护与无向量树检索是 nop memory/安全的参考。

## 六.5 Harness 机制维度覆盖（对照参考框架 D1-D12）

> 参考：`2026-08-01-harness-mechanism-reference-framework.md`（Agent Harness 十二大机制维度）

覆盖维度：**D3**（无向量树索引+Flash 确定性管线）、**D6**（提示注入三重防护）、**D12**（检索路径 replan）。缺失/薄弱：D1、D5。

## 对比结论：nop-ai-agent 全面超越性分析

**nop-ai-agent 已超越的部分**：
- **安全**：nop security 包已有 `ContentOrigin`（标记内容来源）+ `IContentTrustEvaluator`（可信度评估）——pageindex 的提示注入三重防护 nop 有等价（来源标记 + 信任评估），且 nop 是引擎级。
- **记忆**：nop memory 适配器比其 PDF 树索引更通用。

**必要参考的增量（以超越方式吸收）**：
- **提示注入三重防护**（分隔符包裹 + 正则脱敏 + 系统加固）：nop AgentSession 消息流注入用户内容时可增加内容包裹/脱敏——真正增量（与 ContentOrigin 组合成完整防护）。

**总评**：nop-ai-agent **全面超越** pageindex（ContentOrigin 已实现来源标记）；提示注入三重防护一个增量吸收（与 ContentOrigin 组合）。

## References
- `~/ai/pageindex/page_index.py:12-49`（1320 行）、`flash/`、`retrieve.py:81-137`
- `ai-dev/design/nop-ai-agent/nop-ai-agent-context-model.md`、`nop-ai-agent-security-and-permissions.md`
- `ai-dev/analysis/agent-survey/2026-08-01-trustgraph-context-graph-analysis.md`、`2026-08-01-gstack-sprint-workflow-analysis.md`
